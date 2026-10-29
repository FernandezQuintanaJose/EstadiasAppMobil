package com.cecapi.app.core.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread
import kotlin.math.sqrt

/**
 * Envoltura única y compartida alrededor de [SpeechRecognizer] y [TextToSpeech] de Android.
 *
 * Cada módulo (asistente de voz, OCR, solicitudes, aprendizaje, entorno...) lee
 * [state] y llama a [speak]/[startListening] en lugar de crear su propia instancia
 * del motor, porque solo una sesión de SpeechRecognizer/TextToSpeech es confiable
 * por proceso. Esto es también lo que le permite a la app "narrar" automáticamente
 * cada transición de pantalla, que es el requisito de accesibilidad principal para
 * los usuarios de CECAPI.
 */
@Singleton
class VoiceEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cues: FeedbackCues,
) {
    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val _recognizedSpeech = MutableSharedFlow<RecognizedSpeech>(extraBufferCapacity = 4)
    val recognizedSpeech: SharedFlow<RecognizedSpeech> = _recognizedSpeech.asSharedFlow()

    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private val pendingSpeech = ArrayDeque<Pair<String, Boolean>>()

    private val _mode = MutableStateFlow(AssistantMode.ACTIVE)
    val mode: StateFlow<AssistantMode> = _mode.asStateFlow()

    @Volatile var addressStyle: AddressStyle = AddressStyle.TU
    private var currentPitch = 1.0f
    private var currentVoiceName = ""

    // Comandos de voz globales (volumen, estado del teléfono...) que funcionan en toda pantalla, registrados por GlobalVoiceCommands.
    private val speechInterceptors = mutableListOf<(String) -> Boolean>()

    /**
     * Interceptores que se ejecutan incluso mientras una pantalla está capturando texto en bruto (un usuario,
     * una contraseña, una respuesta en Documentos): hoy solo la salida de pantalla negra y la de brillo, porque
     * la persona siempre debe tener una manera de volver a una pantalla normal, en toda pantalla, sin que nada
     * se la trague.
     */
    private val criticalInterceptors = mutableListOf<(String) -> Boolean>()

    /** Lo último que dijo el asistente en voz alta, para que "repite lo que dijiste" pueda repetirlo. */
    @Volatile
    var lastSpoken: String? = null
        private set

    // Establecido por speak(listenAfter = true): abre el micrófono en cuanto termina lo que se está
    // diciendo, para que una instrucción hablada ("Dime tu usuario") fluya directo hacia la respuesta del usuario.
    @Volatile private var listenAfterSpeech = false

    // Respaldado por la tabla `configuracion_usuario` del Módulo 1 (ver SessionRepository.aplicarConfiguracion).
    private var currentRate = 1.0f
    private var currentVolume = 1.0f

    private var speechRecognizer: SpeechRecognizer? = null

    // Palabra de activación: mientras está habilitada, un segundo reconocedor sigue escuchando alguna
    // de [wakeWords] ("hola" más el nombre que el usuario le dio al asistente). Nunca se ejecuta mientras
    // el asistente está hablando ni mientras se está capturando un comando, para que no se active con su propia voz.
    private val mainHandler = Handler(Looper.getMainLooper())
    private var wakeWords: Set<String> = emptySet()
        set(value) {
            field = value
            wakePatterns = value.map { word ->
                val pattern = word.split(' ').joinToString("\\s+") { Regex.escape(it) }
                Regex("(?<![\\p{L}\\p{N}])$pattern(?![\\p{L}\\p{N}])")
            }
        }
    private var wakePatterns: List<Regex> = emptyList()
    private var openMic = false
    private var wakeRecognizer: SpeechRecognizer? = null
    private val wakeRestart = Runnable { listenForWakeWord() }

    /** Cuántos reinicios consecutivos de la palabra de activación fallaron por error de red; controla la espera creciente en [listenForWakeWord]. */
    @Volatile private var consecutiveWakeNetworkErrors = 0

    // Interrupción por voz (barge-in): mientras el asistente habla, un monitor ligero del micrófono (fuente
    // VOICE_COMMUNICATION, para que el cancelador de eco del teléfono elimine casi toda nuestra propia voz)
    // corta el habla si el usuario empieza a hablar.
    @Volatile private var bargeInToken: AtomicBoolean? = null

    init {
        textToSpeech = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (!ttsReady) {
                // No se puede decir nada en absoluto: avisarlo en pantalla y con un doble zumbido distintivo.
                Log.e(TAG, "TextToSpeech failed to start (status=$status)")
                _state.value = VoiceState.Error(
                    "La voz del teléfono no está disponible. Es posible que el teléfono no tenga elegido " +
                        "un motor de voz, aunque esté instalado. Toca el botón de abajo para elegirlo.",
                    openTtsSettings = true,
                )
                cues.play(FeedbackCues.Cue.ERROR)
            }
            if (ttsReady) {
                selectSpanishVoice()
                applyProfile()
                textToSpeech?.setSpeechRate(currentRate)
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        // el estado ya fue puesto en Speaking por speak()
                    }

                    override fun onDone(utteranceId: String?) {
                        // Un texto largo se dice en partes; solo la última termina el habla.
                        if (chunksLeft.decrementAndGet() > 0) return
                        stopBargeInDetection()
                        if (_state.value is VoiceState.Speaking) {
                            _state.value = VoiceState.Idle
                        }
                        val listen = listenAfterSpeech
                        listenAfterSpeech = false
                        mainHandler.post {
                            if (listen) mainHandler.postDelayed({ startListening() }, MIC_HANDOFF_MS) else resumeWakeWord()
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        chunksLeft.set(0)
                        stopBargeInDetection()
                        listenAfterSpeech = false
                        _state.value = VoiceState.Idle
                        mainHandler.post { resumeWakeWord() }
                    }
                })
                while (pendingSpeech.isNotEmpty()) {
                    val (text, listenAfter) = pendingSpeech.removeFirst()
                    speakInternal(text, listenAfter)
                }
            }
        }
    }

    private val chunksLeft = AtomicInteger(0)

    private fun splitForSpeech(text: String, maxLength: Int = 3500): List<String> {
        if (text.length <= maxLength) return listOf(text)
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        for (sentence in text.split(SENTENCE_END)) {
            if (current.isNotEmpty() && current.length + sentence.length + 1 > maxLength) {
                parts.add(current.toString())
                current.clear()
            }
            // Una sola oración más larga que el límite se corta de tajo en vez de descartarse.
            sentence.chunked(maxLength).forEach { piece ->
                if (current.isNotEmpty()) current.append(' ')
                current.append(piece)
            }
        }
        if (current.isNotEmpty()) parts.add(current.toString())
        return parts
    }

    /** Español mexicano si el teléfono lo tiene, si no cualquier español, si no el idioma propio del teléfono. */
    private fun selectSpanishVoice() {
        val tts = textToSpeech ?: return
        val unusable = setOf(TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED)
        if (tts.setLanguage(Locale("es", "MX")) in unusable && tts.setLanguage(Locale("es")) in unusable) {
            Log.w(TAG, "No Spanish voice installed; using the phone's default language")
            tts.setLanguage(Locale.getDefault())
        }
    }

    /**
     * Lee [text] en voz alta. Interrumpe lo que se esté diciendo en ese momento.
     * Con [listenAfter] el micrófono se abre en cuanto termina la lectura, para capturar la respuesta del usuario.
     */
    fun speak(text: String, listenAfter: Boolean = false, force: Boolean = false) {
        // Silenciado o detenido: permanecer callado. Solo la despedida tras "para" pasa ([force]).
        if (!force && _mode.value != AssistantMode.ACTIVE) return
        if (!ttsReady) {
            pendingSpeech.addLast(text to listenAfter)
            return
        }
        speakInternal(text, listenAfter)
    }

    /** Ejecuta [block], una solicitud que puede ser lenta, y le habla a la persona durante la espera para que sepa que sigue funcionando. */
    suspend fun <T> speakWhile(block: suspend () -> T): T = coroutineScope {
        val progress = launch {
            delay(2_000)
            speak("Déjame pensarlo un momento.")
            delay(4_000)
            speak("Estoy consultando la información. La conexión está algo lenta, pero sigo aquí.")
            delay(6_000)
            speak("El servidor estaba dormido y está despertando. Puede tardar hasta un minuto. Sigo aquí.")
            delay(45_000)
            speak("Casi… si no llega pronto, busco en Wikipedia.")
        }
        try {
            block()
        } finally {
            progress.cancel()
        }
    }

    private fun speakInternal(rawText: String, listenAfter: Boolean) {
        // El texto de una IA o de un documento escaneado puede traer markdown o emojis que no se deben leer en voz alta.
        val text = VoiceText.forSpeech(rawText).ifBlank { return }
        pauseWakeWord()
        lastSpoken = text
        listenAfterSpeech = listenAfter
        _state.value = VoiceState.Speaking(text)
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, currentVolume) }
        // El motor de voz de Android rechaza más de unos 4000 caracteres de una vez (respuestas largas de la IA,
        // documentos escaneados), así que el texto largo se envía en partes, dividido al final de cada oración.
        val parts = splitForSpeech(text)
        chunksLeft.set(parts.size)
        parts.forEachIndexed { index, part ->
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            textToSpeech?.speak(part, mode, params, UUID.randomUUID().toString())
        }
        startBargeInDetection()
    }

    /**
     * Aplica las preferencias guardadas del usuario desde el Módulo 1 (`configuracion_usuario`).
     * [velocidadVoz] es un multiplicador de la velocidad del TTS (0.5x–2.0x); [volumen] es 0.0–1.0.
     */
    fun applyVoiceSettings(velocidadVoz: Float, volumen: Float) {
        currentRate = velocidadVoz.coerceIn(0.5f, 2.0f)
        currentVolume = volumen.coerceIn(0.0f, 1.0f)
        if (ttsReady) {
            textToSpeech?.setSpeechRate(currentRate)
        }
    }

    fun stopSpeaking() {
        silenceSpeech()
        mainHandler.post { resumeWakeWord() }
    }

    // ---- Modo del asistente: "silencio" y "para" ----------------------------------------------------

    /** La persona abrió la app o tocó el micrófono: el asistente vuelve a funcionar normalmente. */
    fun activate() {
        if (_mode.value == AssistantMode.ACTIVE) return
        _mode.value = AssistantMode.ACTIVE
        mainHandler.post { resumeWakeWord() }
    }

    /** Permite que otras clases manejen una frase en cualquier pantalla antes de que llegue al ViewModel propio de la pantalla. */
    fun addSpeechInterceptor(interceptor: (String) -> Boolean) {
        speechInterceptors.add(interceptor)
    }

    /** Igual que [addSpeechInterceptor], pero también se ejecuta mientras una pantalla captura texto en bruto. Ver [criticalInterceptors]. */
    fun addCriticalSpeechInterceptor(interceptor: (String) -> Boolean) {
        criticalInterceptors.add(interceptor)
    }

    /** "¿En qué te puedo ayudar?" con el trato que la persona eligió (tú o usted). */
    fun wakePrompt(): String = addressStyle.pick(VoiceMessages.WAKE_PROMPT, VoiceMessages.WAKE_PROMPT_USTED)

    /** Toda frase reconocida pasa por aquí: primero las palabras de control, luego los comandos globales, luego las pantallas. */
    private fun dispatchSpeech(text: String) {
        if (handleControl(text)) return
        if (criticalInterceptors.any { it(text) }) return
        // Una pantalla que está capturando texto en bruto (un usuario, una contraseña) debe recibirlo sin alterar.
        if (!rawInput && speechInterceptors.any { it(text) }) return
        if (!appVisible) {
            // Escuchado fuera de la app (escucha en segundo plano): solo se pueden responder los comandos globales.
            speak(VoiceMessages.OPEN_APP)
            return
        }
        _recognizedSpeech.tryEmit(RecognizedSpeech(text, System.currentTimeMillis()))
    }

    /** Verdadero mientras una pantalla quiere la siguiente frase exactamente como se dijo, sin comandos globales de por medio. */
    @Volatile var rawInput = false

    /** Cuándo fue la última vez que la persona habló encima del asistente. Las pantallas que leen por sí mismas (el lector de texto) no deben avanzar a la siguiente parte. */
    @Volatile var lastBargeInAt = 0L

    /** Falso mientras la app no está en pantalla (puede seguir escuchando a través del servicio en segundo plano). */
    @Volatile var appVisible = true

    /** Ruta de la pantalla que está al frente. Solo esa pantalla recibe lo que se dice. */
    @Volatile var activeRoute: String? = null

    /** Lo que se dice, solo mientras la pantalla de [route] está al frente. */
    fun speechFor(route: String): Flow<RecognizedSpeech> = recognizedSpeech.filter { activeRoute == route }

    /** Llamado por el botón "Detener" del servicio en segundo plano: igual que decir "para". */
    fun stopByUser() = stopAssistant()

    /**
     * "silencio" / "espera" solo callan al asistente; "para" / "detente" lo detienen por completo.
     * Deben ser la frase completa: "para" también es una palabra de uso cotidiano ("para qué sirve").
     */
    private fun handleControl(text: String): Boolean {
        when (VoiceText.normalize(text)) {
            in SILENCE_PHRASES -> mute()
            in STOP_PHRASES -> stopAssistant()
            else -> return false
        }
        return true
    }

    /** Igual que decir "silencio": se queda callado hasta "hola". También se llama desde un doble toque en el micrófono, para uso solo con las manos. */
    fun mute() {
        _mode.value = AssistantMode.MUTED
        silenceSpeech()
        cues.play(FeedbackCues.Cue.NAVIGATE)
        mainHandler.post { resumeWakeWord() }
    }

    private fun stopAssistant() {
        silenceSpeech()
        speak("Hasta luego.", force = true)
        _mode.value = AssistantMode.STOPPED
        stopListening()
        pauseWakeWord()
    }

    // ---- Perfil de voz (Personalización) ------------------------------------------------------------

    /** Las voces en español del teléfono, para que la persona pueda elegir una. Vacío hasta que el motor de voz esté listo. */
    fun spanishVoices(): List<VoiceOption> {
        val tts = textToSpeech ?: return emptyList()
        if (!ttsReady) return emptyList()
        return runCatching {
            tts.voices.orEmpty()
                .filter { it.locale.language == "es" }
                .sortedBy { it.name }
                .mapIndexed { index, voice ->
                    val country = voice.locale.getDisplayCountry(Locale("es")).ifBlank { "genérica" }
                    VoiceOption(voice.name, "Voz ${index + 1} · $country", voice.isNetworkConnectionRequired)
                }
        }.getOrDefault(emptyList())
    }

    /** [voiceName] vacío significa "automático". [pitch] va de 0.5 (grave) a 2.0 (agudo). */
    fun applyVoiceProfile(voiceName: String, pitch: Float) {
        currentVoiceName = voiceName
        currentPitch = pitch.coerceIn(0.5f, 2.0f)
        if (ttsReady) applyProfile()
    }

    private fun applyProfile() {
        val tts = textToSpeech ?: return
        tts.setPitch(currentPitch)
        if (currentVoiceName.isEmpty()) {
            selectSpanishVoice()
        } else {
            tts.voices.orEmpty().firstOrNull { it.name == currentVoiceName }?.let { tts.voice = it }
        }
    }

    private fun silenceSpeech() {
        chunksLeft.set(0)
        stopBargeInDetection()
        listenAfterSpeech = false
        pendingSpeech.clear()
        textToSpeech?.stop()
        if (_state.value is VoiceState.Speaking) {
            _state.value = VoiceState.Idle
        }
    }

    private fun startBargeInDetection() {
        stopBargeInDetection()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val minBuffer = AudioRecord.getMinBufferSize(BARGE_IN_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) return
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                BARGE_IN_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer * 2,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Barge-in unavailable: ${e.message}")
            return
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return
        }
        val effects = listOfNotNull(
            if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(record.audioSessionId) else null,
            if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(record.audioSessionId) else null,
        )
        effects.forEach { it.enabled = true }

        val token = AtomicBoolean(true)
        bargeInToken = token
        thread(name = "cecapi-barge-in", isDaemon = true) {
            val frame = ShortArray(BARGE_IN_SAMPLE_RATE * BARGE_IN_FRAME_MS / 1000)
            var loudMs = 0
            var peak = 0.0
            var sinceLogMs = 0
            var settleSum = 0.0
            var settleFrames = 0
            var threshold = BARGE_IN_RMS_THRESHOLD.toDouble()
            val startedAt = SystemClock.elapsedRealtime()
            try {
                record.startRecording()
                while (token.get()) {
                    val read = record.read(frame, 0, frame.size)
                    if (read <= 0) break
                    var sum = 0.0
                    for (i in 0 until read) sum += frame[i].toDouble() * frame[i]
                    val rms = sqrt(sum / read)
                    // Los primeros instantes miden el ruido del cuarto (un salón de clases nunca está en silencio).
                    // El habla solo interrumpe cuando es claramente más fuerte que eso, para que la plática de los niños no corte al asistente.
                    if (SystemClock.elapsedRealtime() - startedAt < BARGE_IN_SETTLE_MS) {
                        settleSum += rms
                        settleFrames++
                        continue
                    }
                    if (settleFrames > 0) {
                        threshold = maxOf(BARGE_IN_RMS_THRESHOLD.toDouble(), settleSum / settleFrames * BARGE_IN_NOISE_MARGIN)
                        settleFrames = 0
                    }
                    peak = maxOf(peak, rms)
                    sinceLogMs += BARGE_IN_FRAME_MS
                    if (sinceLogMs >= 1_000) {
                        Log.d(TAG, "rms peak=${peak.toInt()} threshold=${threshold.toInt()}")
                        peak = 0.0
                        sinceLogMs = 0
                    }
                    loudMs = if (rms > threshold) loudMs + BARGE_IN_FRAME_MS else maxOf(0, loudMs - BARGE_IN_FRAME_MS / 2)
                    if (loudMs >= BARGE_IN_HOLD_MS) {
                        if (token.getAndSet(false)) mainHandler.post { onBargeIn(token) }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Barge-in stopped: ${e.message}")
            } finally {
                runCatching { record.stop() }
                record.release()
                effects.forEach { it.release() }
            }
        }
    }


    private fun stopBargeInDetection() {
        bargeInToken?.set(false)
        bargeInToken = null
    }

    /** El usuario habló encima del asistente: callarlo y escuchar lo que está diciendo. */
    private fun onBargeIn(@Suppress("UNUSED_PARAMETER") token: AtomicBoolean) {
        Log.d(TAG, "Barge-in: user spoke over the assistant")
        lastBargeInAt = System.currentTimeMillis()
        silenceSpeech()
        mainHandler.postDelayed({ startListening() }, MIC_HANDOFF_MS)
    }

    /**
     * Empieza a escuchar un solo enunciado. Quien llama debe haber solicitado
     * ya RECORD_AUDIO en la capa de Activity/Compose; esta clase no maneja los
     * diálogos de permisos porque eso corresponde a la UI, no al motor.
     */
    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            val message = "El reconocimiento de voz no está disponible en este teléfono."
            _state.value = VoiceState.Error(message)
            cues.play(FeedbackCues.Cue.ERROR)
            speak(message)
            return
        }
        activate() // tocar el micrófono siempre trae de vuelta al asistente después de "silencio" o "para"
        pauseWakeWord()
        silenceSpeech()
        stopListening()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = VoiceState.Listening
                // El usuario no puede ver la pantalla: un pitido y un zumbido dicen "estoy escuchando, habla ahora".
                cues.play(FeedbackCues.Cue.LISTENING)
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit

            override fun onError(error: Int) {
                Log.w(TAG, "Recognition error $error")
                _state.value = VoiceState.Idle
                // Un zumbido largo: el micrófono se cerró sin una respuesta utilizable.
                cues.play(FeedbackCues.Cue.NO_AUDIO)
                // Nunca fallar en silencio: el usuario no puede ver la pantalla, así que hay que decir qué salió mal.
                recognitionErrorMessage(error)?.let { speak(it) } ?: resumeWakeWord()
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                val score = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.firstOrNull()
                _state.value = VoiceState.Idle
                if (!text.isNullOrBlank() && (score == null || score >= COMMAND_MIN_CONFIDENCE)) {
                    // Dos pulsos rápidos: "te escuché".
                    cues.play(FeedbackCues.Cue.HEARD)
                    dispatchSpeech(text)
                    resumeWakeWord()
                } else if (score != null && score < COMMAND_MIN_CONFIDENCE) {
                    // Probablemente es el ambiente, no la persona: mejor preguntar otra vez que ejecutar un comando equivocado.
                    speak("Hay mucho ruido. Repite, por favor, un poco más cerca del teléfono.")
                } else {
                    speak(recognitionErrorMessage(SpeechRecognizer.ERROR_NO_MATCH).orEmpty())
                }
            }

            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        recognizer.startListening(intent)
    }

    /** Qué decirle al usuario para un código de error del reconocedor de voz, o null si debe quedarse en silencio. */
    private fun recognitionErrorMessage(error: Int): String? = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
            "No te escuché. Toca el micrófono para intentarlo otra vez."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
            "No pude reconocer tu voz porque la conexión a internet falló. Intenta de nuevo."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "Necesito permiso para usar el micrófono. Actívalo en los ajustes de la aplicación."
        SpeechRecognizer.ERROR_AUDIO -> "Hubo un problema con el micrófono. Intenta de nuevo."
        // CLIENT y BUSY ocurren cuando nosotros mismos cancelamos o reiniciamos el reconocedor: no es problema del usuario.
        SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> null
        else -> "No pude escucharte. Intenta de nuevo."
    }

    fun stopListening() {
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        if (_state.value is VoiceState.Listening) {
            _state.value = VoiceState.Idle
        }
    }

    /**
     * Activa la escucha de la palabra de activación con [words] (p. ej. "hola" y el nombre del asistente).
     * Quien llama ya debe tener RECORD_AUDIO. Volver a llamar para cambiar las palabras.
     */
    fun startWakeWord(words: Collection<String>, openMic: Boolean = false) {
        wakeWords = words.map(VoiceText::normalize)
            .filter { it.isNotBlank() }
            .toSet()
        this.openMic = openMic
        if (wakeWords.isEmpty()) {
            stopWakeWord()
        } else {
            resumeWakeWord()
        }
    }

    fun stopWakeWord() {
        wakeWords = emptySet()
        openMic = false
        pauseWakeWord()
    }

    private fun resumeWakeWord(delayMs: Long = 400) {
        mainHandler.removeCallbacks(wakeRestart)
        if (wakeWords.isNotEmpty() && _mode.value != AssistantMode.STOPPED) mainHandler.postDelayed(wakeRestart, delayMs)
    }

    private fun pauseWakeWord() {
        mainHandler.removeCallbacks(wakeRestart)
        if (Looper.myLooper() == Looper.getMainLooper()) releaseWakeRecognizer() else mainHandler.post { releaseWakeRecognizer() }
    }

    private fun releaseWakeRecognizer() {
        wakeRecognizer?.cancel()
        wakeRecognizer?.destroy()
        wakeRecognizer = null
    }

    private fun listenForWakeWord() {
        if (wakeWords.isEmpty() || _mode.value == AssistantMode.STOPPED) return
        val busy = _state.value is VoiceState.Speaking || _state.value is VoiceState.Listening
        if (busy || phoneIsInCall() || !SpeechRecognizer.isRecognitionAvailable(context)) {
            resumeWakeWord(if (busy) 800 else 3_000)
            return
        }
        releaseWakeRecognizer()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        wakeRecognizer = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onError(error: Int) {
                if (wakeRecognizer !== recognizer) return
                val isNetworkError = error == SpeechRecognizer.ERROR_NETWORK ||
                    error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
                    error == SpeechRecognizer.ERROR_SERVER
                // Sin internet el reconocedor en línea falla de inmediato, siempre: reiniciar a los 300ms de
                // siempre sondearía así para siempre y agotaría la batería. Esperar cada vez más, duplicando
                // en cada falla, solo para problemas de red; un "nadie dijo nada" normal (NO_MATCH/timeout)
                // sigue reiniciando rápido para que "hola" se siga sintiendo responsivo.
                if (isNetworkError) {
                    consecutiveWakeNetworkErrors++
                } else {
                    consecutiveWakeNetworkErrors = 0
                }
                val delay = if (isNetworkError) {
                    (300L shl (consecutiveWakeNetworkErrors - 1).coerceAtMost(6)).coerceAtMost(WAKE_NETWORK_BACKOFF_MAX_MS)
                } else {
                    when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> 5_000L
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1_500L
                        else -> 300L
                    }
                }
                resumeWakeWord(delay)
            }

            override fun onResults(results: Bundle?) {
                if (wakeRecognizer !== recognizer) return
                consecutiveWakeNetworkErrors = 0
                val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                // "silencio" y "para" funcionan sin decir "hola" primero.
                if (heard.any { handleControl(it) }) return
                val scores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                for ((index, text) in heard.withIndex()) {
                    val end = wakeWordEnd(text) ?: continue
                    // El ruido de fondo y las voces de la televisión a menudo producen un "hola" que parece seguro pero con puntaje bajo.
                    if (scores != null && scores.getOrElse(index) { 1f } < WAKE_MIN_CONFIDENCE) continue
                    onWakeWordHeard(text.substring(end).trim { !it.isLetterOrDigit() })
                    return
                }
                // Micrófono abierto (p. ej. la pantalla de inicio de sesión): el usuario está en medio de una tarea, así que todo lo que diga es un comando.
                val spoken = if (openMic && _mode.value == AssistantMode.ACTIVE) heard.firstOrNull { it.isNotBlank() } else null
                if (spoken != null) {
                    releaseWakeRecognizer()
                    cues.play(FeedbackCues.Cue.HEARD)
                    dispatchSpeech(spoken.trim())
                    resumeWakeWord(1_500)
                    return
                }
                resumeWakeWord()
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        recognizer.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            },
        )
    }

    /** "hola" solo responde "¿En qué te puedo ayudar?"; "hola iniciar sesión" ejecuta el comando de inmediato. */
    private fun onWakeWordHeard(remainder: String) {
        releaseWakeRecognizer()
        cues.play(FeedbackCues.Cue.HEARD, withSound = false)
        activate() // "hola" o el nombre del asistente termina un "silencio"
        if (remainder.isBlank()) {
            // Solo la palabra de activación: responder, luego abrir el micrófono para lo que el usuario quiera.
            speak(wakePrompt(), listenAfter = true)
        } else {
            dispatchSpeech(remainder)
            resumeWakeWord(1_500)
        }
    }

    /** Índice en [raw] justo después de la primera palabra de activación encontrada, o null. [fold] mantiene los índices 1:1 con [raw]. */
    private fun wakeWordEnd(raw: String): Int? {
        val folded = VoiceText.fold(raw)
        for (regex in wakePatterns) {
            val match = regex.find(folded)
            if (match != null) return match.range.last + 1
        }
        return null
    }

    /** Durante una llamada o un teléfono que está timbrando, el micrófono le pertenece a la llamada: no escuchar "hola". */
    private fun phoneIsInCall(): Boolean {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return false
        return audio.mode == android.media.AudioManager.MODE_IN_CALL ||
            audio.mode == android.media.AudioManager.MODE_IN_COMMUNICATION ||
            audio.mode == android.media.AudioManager.MODE_RINGTONE
    }

    private companion object {
        const val TAG = "CecapiVoice"
        const val WAKE_MIN_CONFIDENCE = 0.6f
        const val COMMAND_MIN_CONFIDENCE = 0.4f
        val SENTENCE_END = Regex("(?<=[.!?…])\\s+")

        // Solo frases completas ("para" también aparece dentro de oraciones cotidianas).
        val SILENCE_PHRASES = setOf("silencio", "espera", "esperame", "callate", "un momento", "un segundo")
        val STOP_PHRASES = setOf("para", "detente", "alto", "basta", "para ya", "ya para", "hasta luego", "adios", "apagate")

        // Ajuste de la interrupción por voz (barge-in). El RMS está en PCM de 16 bits (0..32767); el habla
        // normal cerca del teléfono está en los miles bajos. Observa "rms peak" en logcat (tag CecapiVoice) para reajustar por dispositivo.
        const val BARGE_IN_SAMPLE_RATE = 16_000
        const val BARGE_IN_FRAME_MS = 50
        const val BARGE_IN_SETTLE_MS = 500L
        const val BARGE_IN_HOLD_MS = 300
        const val BARGE_IN_RMS_THRESHOLD = 1_800
        const val BARGE_IN_NOISE_MARGIN = 2.5
        const val MIC_HANDOFF_MS = 300L

        /** La espera más larga entre reinicios de la palabra de activación mientras el reconocedor en línea sigue fallando por la red. */
        const val WAKE_NETWORK_BACKOFF_MAX_MS = 30_000L
    }

    fun release() {
        stopWakeWord()
        stopListening()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}
