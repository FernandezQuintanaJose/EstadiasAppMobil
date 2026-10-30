package com.cecapi.app.feature.modulo4_lectordocumentos

import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.navigation.CecapiDestinations
import com.cecapi.app.core.ui.GALLERY_WORDS
import com.cecapi.app.core.util.StorageReport
import com.cecapi.app.core.util.saveImageToGallery
import com.cecapi.app.core.voice.AssistantMode
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceState
import com.cecapi.app.core.voice.VoiceText
import com.cecapi.app.feature.modulo1_aplicacionprincipal.CommandCatalog
import com.cecapi.app.feature.modulo1_aplicacionprincipal.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DocumentReaderUiState(
    val isProcessing: Boolean = false,
    val documentoId: Long? = null,
    val recognizedText: String? = null,
    val errorMessage: String? = null,
    val parrafos: List<String> = emptyList(),
    val parrafoActual: Int = 0,
    val estaLeyendo: Boolean = false,
    val estaPausado: Boolean = false,
    val capturaArmada: Boolean = false,
    /** Botón que ya se tocó una vez y explicó lo que hace; el siguiente toque sobre él lo ejecuta. */
    val botonArmado: BotonLector? = null,
    /** La foto que se está leyendo, mostrada en su propia área en lugar de la vista de cámara en vivo mientras existe. */
    val photoPath: String? = null,
)

/** Botones del lector que piden dos toques: el primero dice qué hacen y el segundo ejecutan la acción. */
enum class BotonLector(val ayuda: String) {
    PAUSAR("Botón pausar lectura. Detiene la lectura en este párrafo. Toca otra vez para pausar."),
    REANUDAR("Botón reanudar lectura. Sigue leyendo desde donde te quedaste. Toca otra vez para continuar."),
    REPETIR("Botón repetir lectura. Lee el documento desde el inicio. Toca otra vez para repetir."),
    ANTERIOR("Botón párrafo anterior. Regresa al párrafo anterior. Toca otra vez para ir atrás."),
    SIGUIENTE("Botón párrafo siguiente. Avanza al siguiente párrafo. Toca otra vez para avanzar."),
    GUARDAR("Botón guardar foto. Guarda la foto del documento en tu galería, en la carpeta Nauren. Toca otra vez para guardarla."),
    OTRA_FOTO("Botón tomar otra foto. Regresa a la cámara para leer un documento nuevo. Toca otra vez para abrir la cámara."),
    VOLVER("Botón volver al menú principal. Sales del lector y regresas al menú. Toca otra vez para volver."),
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DocumentReaderViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val sessionRepository: SessionRepository,
    private val repository: DocumentReaderRepository,
    private val cues: FeedbackCues,
    private val storageReport: StorageReport,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    private val _uiState = MutableStateFlow(DocumentReaderUiState())
    val uiState: StateFlow<DocumentReaderUiState> = _uiState.asStateFlow()

    val history: StateFlow<List<DocumentoEscaneadoEntity>> = sessionRepository.currentUser
        .filterNotNull()
        .flatMapLatest { usuario -> repository.observeRecent(usuario.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // La pantalla es dueña de la cámara, así que los comandos de voz la alcanzan a través de estos.
    private val _captureRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val captureRequests: SharedFlow<Unit> = _captureRequests

    private val _back = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val back: SharedFlow<Unit> = _back

    // Le pide a la pantalla que abra el selector de fotos del sistema (ella es dueña del activity result launcher).
    private val _pickRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val pickRequests: SharedFlow<Unit> = _pickRequests

    // Le pide a la pantalla que guarde la foto (ella revisa el permiso de almacenamiento en Android 9 o anterior).
    private val _saveRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saveRequests: SharedFlow<Unit> = _saveRequests

    private val _routes = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val routes: SharedFlow<String> = _routes

    // Guía de encuadre: solo se habla cuando la misma indicación se repite (evita ruido de un cuadro suelto).
    private var ultimaPista: FramingHint? = null
    private var pistaRepetida = 0
    private var pistaDicha: FramingHint? = null
    private var momentoPistaDicha = 0L

    // Captura automática: se toma la foto sola cuando el encuadre queda bien unos segundos seguidos.
    private var capturaPendiente = false
    private var fallosAutomaticos = 0
    private val capturaAutomaticaActiva: Boolean get() = fallosAutomaticos < MAX_FALLOS_AUTOMATICOS
    private var ultimaCapturaFueAutomatica = false

    init {
        observarFinDeParrafo()
        viewModelScope.launch {
            voiceEngine.speechFor(CecapiDestinations.DOCUMENT_READER).collect { speech -> onSpeech(speech.text) }
        }
        voiceEngine.speak(
            "Lector de texto. Apunta la cámara a un documento y no te muevas: tomaré la foto sola cuando el texto se vea bien. " +
                "También puedes tocar el botón azul o decir toma la foto. " + CommandCatalog.hint("cámara"),
            listenAfter = true,
        )
    }

    /** Los comandos que el lector responde, del más específico al más general. */
    private fun onSpeech(spoken: String) {
        val text = VoiceText.normalize(spoken)
        fun has(vararg words: String) = VoiceText.hasAny(text, *words)
        val hayDocumento = _uiState.value.parrafos.isNotEmpty()
        // Un comando de voz cancela el botón que quedó a la espera del segundo toque.
        _uiState.value = _uiState.value.copy(botonArmado = null)
        when {
            CommandCatalog.isRequest(spoken) -> voiceEngine.speak(CommandCatalog.READER, listenAfter = true)
            has("parrafo anterior", "anterior") -> anteriorParrafo()
            has("atras", "volver", "vuelve", "regresa", "regresar", "salir", "menu", "inicio", "pantalla anterior") -> {
                detenerLectura()
                _back.tryEmit(Unit)
            }
            has("guarda", "guardar", "guardala") -> pedirGuardado()
            has("enfrente", "entorno", "describe", "alrededor") -> cambiarA(
                CecapiDestinations.ENVIRONMENT,
                "Cambiando a describir lo que hay enfrente.",
            )
            has(*GALLERY_WORDS) -> pedirGaleria()
            has("otra foto", "nueva foto", "otro documento", "nuevo documento", "otro papel") -> nuevaFoto(
                tomarYa = has("toma", "captura", "saca"),
            )
            has("otra vez", "repite", "repetir", "de nuevo", "desde el principio", "empieza") -> repetirLectura()
            has("siguiente", "adelante") -> siguienteParrafo()
            has("pausa", "pausar") -> pausarLectura()
            has("continua", "continuar", "sigue", "reanuda") -> continuarLectura()
            has("foto", "fotografia", "captura", "toma", "escanea") -> pedirCaptura()
            has("lee", "leer", "leelo") -> if (hayDocumento) repetirLectura() else pedirCaptura()
            else -> {
                cues.play(FeedbackCues.Cue.NOT_UNDERSTOOD)
                voiceEngine.speak(
                    "No entendí. Di toma la foto, repite, siguiente párrafo o atrás. " + CommandCatalog.hint("cámara"),
                    listenAfter = true,
                )
            }
        }
    }

    /** Cada foto se guarda en el teléfono, así que con casi nada de espacio es mejor avisarlo que llenarlo. */
    private fun hayEspacio(): Boolean {
        if (!storageReport.criticallyLow()) return true
        cues.play(FeedbackCues.Cue.WARNING)
        voiceEngine.speak(
            "Queda muy poco espacio en el teléfono y no puedo guardar más fotos. " +
                "Ve a configuración y di libera espacio, o limpia la caché.",
        )
        return false
    }

    fun onBotonCapturaPresionado(): Boolean {
        if (_uiState.value.capturaArmada) {
            _uiState.value = _uiState.value.copy(capturaArmada = false)
            return true
        }
        if (!hayEspacio()) return false
        _uiState.value = _uiState.value.copy(capturaArmada = true, botonArmado = null)
        voiceEngine.speak("Vas a tomar una foto del documento. Toca otra vez para capturarla.")
        return false
    }

    /**
     * El primer toque explica qué hace el botón; el segundo ejecuta la acción.
     * Explicarlo interrumpe la lectura, así que se deja en pausa para poder seguir después.
     */
    fun onBotonPresionado(boton: BotonLector) {
        val state = _uiState.value
        if (state.botonArmado != boton) {
            if (state.estaLeyendo) {
                detenerLectura()
                _uiState.value = _uiState.value.copy(estaPausado = true)
            }
            _uiState.value = _uiState.value.copy(botonArmado = boton, capturaArmada = false)
            voiceEngine.speak(boton.ayuda)
            return
        }
        _uiState.value = state.copy(botonArmado = null)
        when (boton) {
            // La explicación ya detuvo la lectura; solo se confirma la pausa.
            BotonLector.PAUSAR -> voiceEngine.speak("En pausa. Di continúa para seguir.", listenAfter = true)
            BotonLector.REANUDAR -> continuarLectura()
            BotonLector.REPETIR -> repetirLectura()
            BotonLector.ANTERIOR -> anteriorParrafo()
            BotonLector.SIGUIENTE -> siguienteParrafo()
            BotonLector.GUARDAR -> pedirGuardado()
            BotonLector.OTRA_FOTO -> nuevaFoto(tomarYa = false)
            BotonLector.VOLVER -> {
                detenerLectura()
                _back.tryEmit(Unit)
            }
        }
    }

    fun pedirCaptura() = capturar(automatica = false)

    private fun capturar(automatica: Boolean) {
        if (capturaPendiente || _uiState.value.isProcessing || !hayEspacio()) return
        detenerLectura()
        capturaPendiente = true
        ultimaCapturaFueAutomatica = automatica
        voiceEngine.speak("Tomando foto.")
        _captureRequests.tryEmit(Unit)
    }

    /**
     * Recibe lo que ve la cámara en vivo y guía por voz ("mueve el teléfono a la izquierda", "acércalo un poco").
     * No interrumpe otra frase ni el micrófono abierto, y no repite la misma indicación muy seguido.
     */
    fun onFramingHint(pista: FramingHint) {
        val state = _uiState.value
        if (capturaPendiente || state.photoPath != null || state.isProcessing || state.parrafos.isNotEmpty()) return

        pistaRepetida = if (pista == ultimaPista) pistaRepetida + 1 else 1
        ultimaPista = pista

        // Con el texto bien encuadrado y el teléfono quieto, toma la foto sin esperar a que se hable la indicación.
        if (pista == FramingHint.LISTO && capturaAutomaticaActiva && pistaRepetida >= CUADROS_AUTOCAPTURA &&
            voiceEngine.state.value !is VoiceState.Speaking
        ) {
            cues.play(FeedbackCues.Cue.TAP)
            capturar(automatica = true)
            return
        }

        val necesarias = if (pista == FramingHint.SIN_TEXTO) CUADROS_SIN_TEXTO else CUADROS_PISTA
        if (pistaRepetida < necesarias) return
        if (voiceEngine.state.value != VoiceState.Idle) return
        // Con el asistente en silencio o la app fuera de pantalla no se dice nada.
        if (voiceEngine.mode.value != AssistantMode.ACTIVE || !voiceEngine.appVisible) return
        if (pista == FramingHint.LISTO && pistaDicha == FramingHint.LISTO) return

        val ahora = SystemClock.elapsedRealtime()
        val espera = when {
            pista != pistaDicha -> ESPERA_CAMBIO_MS
            pista == FramingHint.SIN_TEXTO -> ESPERA_SIN_TEXTO_MS
            else -> ESPERA_REPETIR_MS
        }
        if (ahora - momentoPistaDicha < espera) return

        pistaDicha = pista
        momentoPistaDicha = ahora
        voiceEngine.speak(mensajeDe(pista))
    }

    private fun mensajeDe(pista: FramingHint): String = when {
        pista != FramingHint.LISTO -> pista.mensaje
        capturaAutomaticaActiva -> pista.mensaje + " No te muevas, voy a tomar la foto."
        else -> pista.mensaje + " Toca el botón o di toma la foto."
    }

    fun onCameraPermissionDenied() {
        voiceEngine.speak("Sin el permiso de la cámara no puedo leer. Actívalo en los ajustes de la aplicación.")
    }

    private fun reiniciarGuia() {
        ultimaPista = null
        pistaRepetida = 0
        pistaDicha = null
        momentoPistaDicha = 0L
    }

    /**
     * "Elige una foto": abre el selector de fotos del sistema. La persona elige una foto y la aplicación
     * solo ve esa; no hay permiso sobre toda la galería. Se dice en voz alta primero para que siempre sea
     * su decisión.
     */
    fun pedirGaleria() {
        if (_uiState.value.isProcessing || !hayEspacio()) return
        detenerLectura()
        voiceEngine.speak("Voy a abrir tus fotos. Elige la imagen que quieres que lea; solo veré esa.")
        _pickRequests.tryEmit(Unit)
    }

    fun onPickCancelled() {
        voiceEngine.speak("No elegiste ninguna foto. Di toma la foto, o elige una foto.", listenAfter = true)
    }

    fun onPickFailed() {
        cues.play(FeedbackCues.Cue.ERROR)
        voiceEngine.speak("No pude abrir esa imagen. Prueba con otra.", listenAfter = true)
    }

    fun onCaptureFailed() {
        capturaPendiente = false
        val message = "No pude tomar la foto. Revisa que la cámara esté libre e intenta de nuevo."
        _uiState.value = DocumentReaderUiState(errorMessage = message)
        cues.play(FeedbackCues.Cue.ERROR)
        voiceEngine.speak(message, listenAfter = true)
    }

    /** "Guarda la foto" por voz o el segundo toque del botón: la pantalla revisa el permiso y luego llama a [onSavePhoto]. */
    private fun pedirGuardado() {
        if (_uiState.value.photoPath == null) return voiceEngine.speak("Primero toma una foto.")
        detenerLectura()
        _saveRequests.tryEmit(Unit)
    }

    fun onSavePhoto() {
        val path = _uiState.value.photoPath ?: return voiceEngine.speak("Primero toma una foto.")
        viewModelScope.launch {
            val saved = saveImageToGallery(appContext, path)
            voiceEngine.speak(
                if (saved) {
                    "Listo, la foto quedó guardada en tu galería, en la carpeta Nauren."
                } else {
                    "No pude guardar la foto en tu galería."
                },
            )
        }
    }

    fun onSavePhotoDenied() {
        voiceEngine.speak("Necesito permiso para guardar fotos en tu teléfono. Toca Guardar foto otra vez y acéptalo.")
    }

    fun onPhotoCaptured(imageUri: Uri, rutaImagen: String, desdeCamara: Boolean = false) {
        // La cámara es libre para cualquiera: sin nadie con sesión iniciada, el texto igual se lee en voz
        // alta, solo que no se guarda en un historial (processCapturedPhoto omite guardar cuando usuarioId es null).
        val usuarioId = sessionRepository.currentUser.value?.id
        capturaPendiente = false
        _uiState.value = _uiState.value.copy(
            isProcessing = true,
            errorMessage = null,
            estaLeyendo = false,
            estaPausado = false,
            capturaArmada = false,
            photoPath = rutaImagen, // se muestra de inmediato, en lugar de la vista en vivo, mientras procesa
        )
        // Confirma la captura solo con la cámara; una foto de la galería no se "toma".
        voiceEngine.speak(if (desdeCamara) "Foto tomada. Procesando la imagen." else "Procesando la imagen.")
        viewModelScope.launch {
            when (val outcome = repository.processCapturedPhoto(usuarioId, imageUri, rutaImagen)) {
                is OcrOutcome.Exito -> {
                    fallosAutomaticos = 0
                    _uiState.value = DocumentReaderUiState(
                        isProcessing = false,
                        documentoId = outcome.documentoId,
                        recognizedText = outcome.textoCompleto,
                        parrafos = outcome.parrafos,
                        photoPath = rutaImagen,
                    )
                    if (outcome.borrosa) {
                        voiceEngine.speak("La foto salió un poco borrosa. Si algo no se entiende, limpia la cámara e intenta de nuevo.")
                    }
                    leerParrafo(0)
                    repository.logLectura(outcome.documentoId)
                }
                OcrOutcome.PocaLuz -> falloDeLectura("La imagen está muy oscura. Busca mejor iluminación e intenta de nuevo.")
                OcrOutcome.SinTexto -> falloDeLectura("No se detectó texto en la imagen. " + consejoSinTexto())
                is OcrOutcome.Error -> falloDeLectura("No se pudo leer el texto de la imagen. Intenta con mejor iluminación.")
            }
        }
    }

    /** Usa lo último que vio la cámara en vivo para decir cómo corregir la foto. */
    private fun consejoSinTexto(): String = when (val pista = ultimaPista) {
        null, FramingHint.LISTO, FramingHint.SIN_TEXTO -> FramingHint.SIN_TEXTO.mensaje
        else -> pista.mensaje
    }

    private fun falloDeLectura(message: String) {
        reiniciarGuia()
        _uiState.value = DocumentReaderUiState(errorMessage = message)
        cues.play(FeedbackCues.Cue.ERROR)
        // Si la foto automática falla seguido (poca luz, papel sin texto), se apaga para no repetir fotos en bucle.
        if (ultimaCapturaFueAutomatica) fallosAutomaticos++
        val siguientePaso = when {
            !ultimaCapturaFueAutomatica -> "Di toma la foto para intentarlo otra vez."
            capturaAutomaticaActiva -> "Vuelve a apuntar al papel y tomaré otra foto sola."
            else -> "Ya no tomaré fotos solas. Toca el botón o di toma la foto cuando estés listo."
        }
        voiceEngine.speak("$message $siguientePaso", listenAfter = true)
    }

    fun onRepeatFromStartRequested() = repetirLectura()

    fun onPauseResumeToggle() {
        val state = _uiState.value
        if (state.estaPausado) {
            continuarLectura()
        } else if (state.estaLeyendo) {
            pausarLectura()
        } else if (state.parrafos.isNotEmpty()) {
            continuarLectura()
        }
    }

    fun onResetToCameraRequested() = nuevaFoto(tomarYa = false)

    fun onNavigateToMenuRequested(onNavigateToMenu: () -> Unit) {
        detenerLectura()
        voiceEngine.speak("Volviendo al menú principal.")
        _back.tryEmit(Unit)
        onNavigateToMenu()
    }

    private fun leerParrafo(indice: Int) {
        val lista = _uiState.value.parrafos
        // Un párrafo sin nada que pronunciar (solo símbolos) nunca arrancaría, así que se omite.
        var i = indice
        while (i < lista.size && VoiceText.forSpeech(lista[i]).isBlank()) i++
        val parrafo = lista.getOrNull(i)
        if (parrafo == null || voiceEngine.mode.value != AssistantMode.ACTIVE || !voiceEngine.appVisible) {
            _uiState.value = _uiState.value.copy(estaLeyendo = false, estaPausado = false)
            return
        }
        _uiState.value = _uiState.value.copy(parrafoActual = i, estaLeyendo = true, estaPausado = false)
        voiceEngine.speak(parrafo)
    }

    private fun sinDocumento() {
        voiceEngine.speak("Todavía no he leído nada. Di toma la foto.", listenAfter = true)
    }

    /** Silencia la lectura sin dejar que el observador de párrafos avance al siguiente. */
    private fun detenerLectura() {
        if (!_uiState.value.estaLeyendo) return
        _uiState.value = _uiState.value.copy(estaLeyendo = false)
        voiceEngine.stopSpeaking()
    }

    fun repetirLectura() {
        if (_uiState.value.parrafos.isEmpty()) return sinDocumento()
        voiceEngine.speak("Repetiendo lectura desde el inicio.")
        leerParrafo(0)
    }

    fun siguienteParrafo() {
        val state = _uiState.value
        if (state.parrafos.isEmpty()) return sinDocumento()
        val siguiente = state.parrafoActual + 1
        if (siguiente >= state.parrafos.size) {
            _uiState.value = state.copy(estaLeyendo = false)
            voiceEngine.speak("Ese era el último párrafo. Di repite para leer desde el principio.", listenAfter = true)
        } else {
            leerParrafo(siguiente)
        }
    }

    fun anteriorParrafo() {
        val state = _uiState.value
        if (state.parrafos.isEmpty()) return sinDocumento()
        if (state.parrafoActual <= 0) {
            _uiState.value = state.copy(estaLeyendo = false)
            voiceEngine.speak("Estás en el primer párrafo. Di repite para leerlo otra vez.", listenAfter = true)
        } else {
            leerParrafo(state.parrafoActual - 1)
        }
    }

    fun pausarLectura() {
        if (!_uiState.value.estaLeyendo) {
            voiceEngine.speak("No estoy leyendo en este momento.", listenAfter = true)
            return
        }
        detenerLectura()
        _uiState.value = _uiState.value.copy(estaPausado = true)
        voiceEngine.speak("En pausa. Di continúa para seguir.", listenAfter = true)
    }

    fun continuarLectura() {
        if (_uiState.value.parrafos.isEmpty()) return sinDocumento()
        _uiState.value = _uiState.value.copy(estaPausado = false)
        voiceEngine.speak("Reanudando lectura.")
        leerParrafo(_uiState.value.parrafoActual)
    }

    /** Botón "Tomar otra foto" en la pantalla gigante de controles de lectura. */
    fun onRetakePhoto() = nuevaFoto(tomarYa = false)

    /** Borra el último documento para que la siguiente foto empiece desde cero. */
    private fun nuevaFoto(tomarYa: Boolean) {
        detenerLectura()
        reiniciarGuia()
        fallosAutomaticos = 0
        _uiState.value = DocumentReaderUiState()
        if (tomarYa) {
            pedirCaptura()
        } else {
            voiceEngine.speak("Cámara lista. Apunta al nuevo papel y quédate quieto, tomaré la foto sola.", listenAfter = true)
        }
    }

    /** Botón "Qué hay enfrente" del selector de modo de la cámara. */
    fun cambiarAEntorno() = cambiarA(CecapiDestinations.ENVIRONMENT, "Cambiando a describir lo que hay enfrente.")

    private fun cambiarA(route: String, speech: String) {
        detenerLectura()
        cues.play(FeedbackCues.Cue.NAVIGATE)
        voiceEngine.speak(speech)
        _routes.tryEmit(route)
    }

    /** Lee en voz alta los comandos de esta pantalla; el botón del encabezado lo invoca. */
    fun onCommandsRequested() {
        voiceEngine.speak(CommandCatalog.READER, listenAfter = true)
    }

    fun onMicTapped() = voiceEngine.startListening()

    /** Dos toques rápidos en el micrófono silencian al asistente, para quien usa el tacto en vez de la voz. */
    fun onMicDoubleTap() = voiceEngine.mute()

    /**
     * TextToSpeech no avisa directamente cuándo termina: VoiceEngine pasa de
     * Speaking a Idle. Solo avanzamos si lo que terminó fue el párrafo actual;
     * stopSpeaking() también deja el estado en Idle, por eso se revisa estaLeyendo.
     * VoiceEngine limpia el texto antes de hablarlo, así que se compara contra el texto ya limpio.
     */
    private fun observarFinDeParrafo() {
        viewModelScope.launch {
            var anterior: VoiceState = VoiceState.Idle
            voiceEngine.state.collect { actual ->
                val state = _uiState.value
                val parrafo = state.parrafos.getOrNull(state.parrafoActual)
                if (state.estaLeyendo && !state.estaPausado && parrafo != null) {
                    val hablado = VoiceText.forSpeech(parrafo)
                    if (actual is VoiceState.Speaking && actual.text != hablado) {
                        if (!actual.text.startsWith("Lectura pausada") &&
                            !actual.text.startsWith("En pausa") &&
                            !actual.text.startsWith("Reanudando") &&
                            !actual.text.startsWith("Repetiendo") &&
                            !actual.text.startsWith("Volviendo")
                        ) {
                            _uiState.value = state.copy(estaLeyendo = false)
                        }
                    } else if (actual is VoiceState.Idle && anterior == VoiceState.Speaking(hablado)) {
                        val interrumpida = System.currentTimeMillis() - voiceEngine.lastBargeInAt < BARGE_IN_WINDOW_MS
                        if (voiceEngine.mode.value != AssistantMode.ACTIVE || !voiceEngine.appVisible || interrumpida) {
                            // "Silencio" o "para", salir de la app, o hablar encima de la lectura: no seguir leyendo.
                            _uiState.value = state.copy(estaLeyendo = false)
                        } else {
                            siguienteAutomatico(state)
                        }
                    }
                }
                anterior = actual
            }
        }
    }

    private fun siguienteAutomatico(state: DocumentReaderUiState) {
        val siguiente = state.parrafoActual + 1
        if (siguiente < state.parrafos.size) {
            leerParrafo(siguiente)
        } else {
            _uiState.value = state.copy(estaLeyendo = false, estaPausado = false)
        }
    }

    private companion object {
        /** Cuadros seguidos (uno cada ~0.7 s) con la misma indicación antes de decirla. */
        const val CUADROS_PISTA = 2
        const val CUADROS_SIN_TEXTO = 4

        /** Cuadros seguidos con el encuadre correcto (~2 s quieto) antes de tomar la foto sola. */
        const val CUADROS_AUTOCAPTURA = 3

        /** Fotos automáticas fallidas seguidas antes de dejar solo el botón y la voz. */
        const val MAX_FALLOS_AUTOMATICOS = 2

        const val ESPERA_CAMBIO_MS = 2_500L
        const val ESPERA_REPETIR_MS = 5_000L
        const val ESPERA_SIN_TEXTO_MS = 10_000L

        /** Si la persona habló encima de la lectura hace tan poco, la pausa en la voz fue suya: no seguir leyendo. */
        const val BARGE_IN_WINDOW_MS = 1_500L
    }

    override fun onCleared() {
        if (_uiState.value.estaLeyendo) voiceEngine.stopSpeaking()
        super.onCleared()
    }
}
