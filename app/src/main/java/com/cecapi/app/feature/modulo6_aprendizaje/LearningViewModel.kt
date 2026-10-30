package com.cecapi.app.feature.modulo6_aprendizaje

import com.cecapi.app.core.navigation.CecapiDestinations
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceState
import com.cecapi.app.core.voice.VoiceText
import com.cecapi.app.feature.modulo1_aplicacionprincipal.CommandCatalog
import com.cecapi.app.feature.modulo1_aplicacionprincipal.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

enum class ActivityMode { AUDIO, VIBRATION }

/** Un ejercicio, sea un sonido o un patrón de vibración. */
data class ActivityItem(
    val id: Long,
    val title: String,
    val instruction: String,
    val answer: String,
    val sound: EjercicioEntity? = null,
    val vibration: EjercicioVibracionEntity? = null,
)

data class ActivitiesUiState(
    val mode: ActivityMode = ActivityMode.AUDIO,
    val level: Int = 1,
    val items: List<ActivityItem> = emptyList(),
    val index: Int = 0,
    val feedback: String? = null,
    val correct: Boolean? = null,
    /** Se está reproduciendo la instrucción, el sonido o la vibración: todavía no se toman respuestas. */
    val busy: Boolean = false,
    /** El índice ya calificado en esta ronda, para que repetir la misma respuesta no sume puntos otra vez. */
    val answeredIndex: Int = -1,
    /** Los ejercicios de esta ronda que ya se calificaron: regresar con Anterior no deja sumar puntos dos veces. */
    val respondidos: Set<Int> = emptySet(),
    /** Respuestas correctas hasta ahora en esta ronda (sonidos o vibraciones, según lo que esté seleccionado). */
    val correctCount: Int = 0,
    /** Se terminaron todos los ejercicios: se muestra el puntaje y la persona puede repetir o salir. */
    val finished: Boolean = false,
    /** Practice mode: the answers are not saved and the level does not change. */
    val practice: Boolean = false,
    val finalMessage: String? = null,
) {
    val current: ActivityItem? get() = items.getOrNull(index)
}

/**
 * Actividades: entrenamiento auditivo. Se reproduce un sonido (o un patrón de vibración) y la persona dice
 * de dónde vino o cómo se sintió. Las respuestas se revisan, se guardan para el usuario con sesión iniciada,
 * y el nivel sube con los puntos. También funciona sin iniciar sesión; los resultados simplemente no se guardan.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LearningViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val cues: FeedbackCues,
    private val sessionRepository: SessionRepository,
    private val repository: LearningRepository,
    private val vibrationRepository: VibrationRepository,
    private val audioPlayer: AudioSpatialPlayer,
    private val vibrationEngine: VibrationEngine,
) : ViewModel() {

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    private val _state = MutableStateFlow(ActivitiesUiState())
    val state: StateFlow<ActivitiesUiState> = _state.asStateFlow()

    private val _back = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val back: SharedFlow<Unit> = _back

    private val mode = MutableStateFlow(ActivityMode.AUDIO)
    private val level = MutableStateFlow(1)
    private var exerciseJob: Job? = null

    // El nivel más alto que la persona ha alcanzado (progreso guardado), para anunciar cuando sube.
    private var savedLevel = 1

    private val itemsFlow: Flow<List<ActivityItem>> = combine(mode, level) { m, l -> m to l }
        .flatMapLatest { (m, l) ->
            if (m == ActivityMode.AUDIO) {
                repository.observeEjerciciosPorNivel(l).map { list ->
                    list.map { ActivityItem(it.id, it.titulo, it.instruccion, it.respuestaCorrecta, sound = it) }
                }
            } else {
                vibrationRepository.observeEjerciciosDelNivel(1).map { list ->
                    list.map { ActivityItem(it.id, it.titulo, it.instruccion, it.respuestaCorrecta, vibration = it) }
                }
            }
        }

    init {
        viewModelScope.launch {
            repository.seedEjerciciosSiVacio()
            vibrationRepository.seedEjerciciosSiVacio()
            sessionRepository.currentUser.value?.let { user ->
                savedLevel = repository.observeNivel(user.id).first().nivelActual
                level.value = savedLevel
            }
            _state.value = _state.value.copy(level = level.value)
            voiceEngine.speak(
                "Actividades para entrenar el oído. Escucharás un sonido y dirás de dónde viene. " +
                    "Para notar de qué lado viene el sonido, usa audífonos. " +
                    "Di vibración para practicar con vibraciones. " + CommandCatalog.hint("actividades"),
            )
            itemsFlow.collect { list -> onItemsLoaded(list) }
        }
        viewModelScope.launch {
            voiceEngine.speechFor(CecapiDestinations.LEARNING).collect { speech -> onSpeech(speech.text) }
        }
    }

    private suspend fun onItemsLoaded(list: List<ActivityItem>) {
        val previous = _state.value
        if (list.map { it.id } == previous.items.map { it.id } && previous.mode == mode.value) return
        _state.value = previous.copy(
            mode = mode.value,
            level = level.value,
            items = list,
            index = 0,
            feedback = null,
            correct = null,
            answeredIndex = -1,
            respondidos = emptySet(),
            correctCount = 0,
            finished = false,
            finalMessage = null,
        )
        if (list.isEmpty()) return
        // Deja que termine el mensaje de bienvenida antes del primer ejercicio.
        speakAndWait("")
        startExercise(intro = "${where()} ")
    }

    private fun where(): String {
        val s = _state.value
        val place = if (s.mode == ActivityMode.AUDIO) "Nivel ${s.level}" else "Vibración"
        val prefix = if (s.practice) "Práctica. " else ""
        return "$prefix$place, ejercicio ${s.index + 1} de ${s.items.size}."
    }

    /**
     * Los comandos que responde esta pantalla. Una frase que contiene una respuesta ("el sonido vino de la
     * izquierda", "terminó en la derecha") es una respuesta, aunque también tenga una palabra que en otro
     * lugar es un comando; por eso la revisión de respuesta va antes que repetir, siguiente y atrás.
     * "Atrás" y "adelante" son respuestas en el nivel 3, así que ahí no son comandos.
     */
    private fun onSpeech(spoken: String) {
        val text = VoiceText.normalize(spoken)
        fun has(vararg words: String) = VoiceText.hasAny(text, *words)
        val chosenLevel = LEVEL_COMMAND.find(text)?.groupValues?.let { it[1].ifEmpty { it[2] } }
        val awaitingAnswer = _state.value.current != null && !_state.value.busy
        when {
            CommandCatalog.isRequest(spoken) -> {
                stopStimulus()
                voiceEngine.speak(CommandCatalog.ACTIVITIES, listenAfter = true)
            }
            has("salir de practica", "modo normal", "con puntos") -> setPractice(false)
            has("practica", "sin puntos") -> setPractice(true)
            chosenLevel != null -> setLevel(levelNumber(chosenLevel))
            text in VIBRATION_ONLY || has("modo vibracion", "cambia a vibracion", "cambiar a vibracion", "pasa a vibracion", "ejercicios de vibracion") ->
                setMode(ActivityMode.VIBRATION)
            text in SOUNDS_ONLY || has("modo sonidos", "cambia a sonidos", "cambiar a sonidos", "pasa a sonidos", "ejercicios de sonidos") ->
                setMode(ActivityMode.AUDIO)
            _state.value.finished -> onFinalCommand(text)
            awaitingAnswer && categoriesIn(text).isNotEmpty() -> answer(spoken)
            has("repite", "repetir", "repiteme", "otra vez", "de nuevo", "escuchalo", "ponlo otra vez", "vuelve a poner", "vuelve a sonar", "no alcance a oir", "no escuche") -> repeat()
            has("siguiente", "proximo", "continua", "continuar", "sigue", "el que sigue", "otro ejercicio", "otro", "pasa al siguiente") -> next()
            has("volver", "vuelve", "salir", "menu", "regresar", "regresa", "terminar", "termina", "ya no quiero", "inicio") -> {
                stopStimulus()
                _back.tryEmit(Unit)
            }
            _state.value.busy -> Unit
            else -> answer(spoken)
        }
    }

    private fun levelNumber(word: String): Int = when (word) {
        "uno", "1", "primer" -> 1
        "dos", "2", "segundo" -> 2
        else -> 3
    }

    fun setMode(newMode: ActivityMode) {
        if (newMode == mode.value) return
        stopStimulus()
        mode.value = newMode
    }

    fun setPractice(enabled: Boolean) {
        if (_state.value.practice == enabled) return
        _state.value = _state.value.copy(practice = enabled)
        restartActivity()
    }

    fun setLevel(newLevel: Int) {
        if (mode.value != ActivityMode.AUDIO) mode.value = ActivityMode.AUDIO
        if (newLevel == level.value) return
        stopStimulus()
        level.value = newLevel
    }

    /** Dos toques rápidos en el micrófono silencian al asistente, para quien usa las manos en vez de la voz. */
    fun onMicDoubleTap() {
        stopStimulus()
        voiceEngine.mute()
    }

    fun onMicTapped() {
        stopStimulus()
        voiceEngine.startListening()
    }

    fun onCommandsRequested() {
        stopStimulus()
        voiceEngine.speak(CommandCatalog.ACTIVITIES, listenAfter = true)
    }

    /** Reproduce otra vez el sonido o la vibración, sin repetir la instrucción. */
    fun repeat() {
        if (_state.value.current == null || _state.value.finished) return
        startExercise(intro = "")
    }

    /** Vuelve al ejercicio anterior para escucharlo otra vez; si ya se calificó, no vuelve a sumar. */
    fun previous() {
        val s = _state.value
        if (s.items.isEmpty() || s.finished) return
        if (s.index == 0) {
            voiceEngine.speak("Estás en el primer ejercicio.", listenAfter = true)
            return
        }
        _state.value = s.copy(index = s.index - 1, feedback = null, correct = null)
        startExercise(intro = "${where()} ")
    }

    /** Responder tocando un botón (para quien prefiere no hablar): es lo mismo que decir la respuesta. */
    fun responder(respuesta: String) = answer(respuesta)

    fun next() {
        val s = _state.value
        if (s.items.isEmpty() || s.finished) return
        // Después del último ejercicio termina la ronda: se dice el puntaje en vez de empezar de nuevo.
        if (s.index + 1 >= s.items.size) {
            finish()
            return
        }
        _state.value = s.copy(index = s.index + 1, feedback = null, correct = null)
        startExercise(intro = "${where()} ")
    }

    /** La ronda terminó: dice el puntaje en voz alta y escucha "repetir" o "salir". */
    private fun finish() {
        stopStimulus()
        val s = _state.value
        val message = "Terminaste. Acertaste ${s.correctCount} de ${s.items.size}. " +
            "Di repetir para intentarlo otra vez, o salir para volver al menú."
        _state.value = s.copy(finished = true, feedback = null, correct = null, finalMessage = message)
        voiceEngine.speak(message, listenAfter = true)
    }

    /** Empieza de nuevo el mismo nivel (o las vibraciones) desde el primer ejercicio, con el puntaje en cero. */
    fun restartActivity() {
        val s = _state.value
        if (s.items.isEmpty()) return
        stopStimulus()
        _state.value = s.copy(
            index = 0,
            feedback = null,
            correct = null,
            answeredIndex = -1,
            respondidos = emptySet(),
            correctCount = 0,
            finished = false,
            finalMessage = null,
        )
        startExercise(intro = "${where()} ")
    }

    fun leave() {
        stopStimulus()
        _back.tryEmit(Unit)
    }

    /** Al final, lo que la persona dice es "repetir" o "salir"; cualquier otra cosa recibe las opciones otra vez. */
    private fun onFinalCommand(text: String) {
        when {
            VoiceText.hasAny(text, "repite", "repetir", "repiteme", "otra vez", "de nuevo", "volver a intentar", "reiniciar", "empezar") ->
                restartActivity()
            VoiceText.hasAny(text, "salir", "volver", "vuelve", "menu", "regresar", "regresa", "terminar", "termina", "ya no quiero", "inicio") ->
                leave()
            else -> {
                cues.play(FeedbackCues.Cue.NOT_UNDERSTOOD)
                voiceEngine.speak("Di repetir para intentarlo otra vez, o salir para volver.", listenAfter = true)
            }
        }
    }

    private fun startExercise(intro: String) {
        val item = _state.value.current ?: return
        stopStimulus()
        exerciseJob = viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val talk = (intro + if (intro.isBlank()) "" else item.instruction).trim()
            if (talk.isNotEmpty()) speakAndWait(talk)
            // La persona salió de la aplicación mientras hablaba: nada debería reproducirse o escuchar a sus espaldas.
            if (!voiceEngine.appVisible) return@launch stopStimulus()
            delay(300)
            item.sound?.let { audioPlayer.reproducir(it) }
            item.vibration?.let { vibrationEngine.reproducirPatron(it.patronMs.aPatronMs(), it.intensidad) }
            delay(500)
            _state.value = _state.value.copy(busy = false)
            if (voiceEngine.appVisible) voiceEngine.startListening()
        }
    }

    /** La aplicación pasó a segundo plano (botón de inicio, bloqueo de pantalla): detener el sonido, la vibración y el ejercicio. */
    fun onAppStopped() = stopStimulus()

    private fun stopStimulus() {
        exerciseJob?.cancel()
        audioPlayer.detener()
        vibrationEngine.detener()
        _state.value = _state.value.copy(busy = false)
    }

    private suspend fun speakAndWait(text: String) {
        if (text.isNotBlank()) voiceEngine.speak(text)
        delay(250)
        withTimeoutOrNull(20_000) { voiceEngine.state.first { it !is VoiceState.Speaking } }
    }

    private fun answer(spoken: String) {
        val item = _state.value.current ?: return
        // Ya calificado: repetir la misma respuesta no debe sumar puntos otra vez. "Repite" solo reproduce el sonido.
        if (_state.value.index in _state.value.respondidos) {
            voiceEngine.speak("Ya respondiste este ejercicio. Di siguiente para continuar, o repite para escucharlo otra vez.", listenAfter = true)
            return
        }
        val text = VoiceText.normalize(spoken)
        val categories = categoriesIn(text)
        val movement = movement(text)
        val expectsMovement = VoiceText.normalize(item.answer).startsWith("de ")
        val expectsMixed = VoiceText.normalize(item.answer) == "mixto"
        // Nada que suene a respuesta: no calificarlo, puede que la persona simplemente haya dicho otra cosa.
        if (categories.isEmpty() && movement == 0) {
            cues.play(FeedbackCues.Cue.NOT_UNDERSTOOD)
            voiceEngine.speak("No entendí tu respuesta. Dila con una sola palabra, o di repite, o siguiente.", listenAfter = true)
            return
        }
        // Varias respuestas a la vez ("cerca, lejos, centro") siempre acertarían por suerte: pedir una sola.
        if (!expectsMovement && !expectsMixed && categories.size > 1) {
            cues.play(FeedbackCues.Cue.NOT_UNDERSTOOD)
            voiceEngine.speak("Dime una sola respuesta.", listenAfter = true)
            return
        }
        val correct = matches(spoken, item.answer)
        val message = (if (correct) "Correcto" else "Incorrecto") + ", la respuesta era ${spokenAnswer(item.answer)}."
        cues.play(if (correct) FeedbackCues.Cue.SUCCESS else FeedbackCues.Cue.ERROR)
        // answeredIndex (revisado arriba) garantiza que cada ejercicio se cuente solo una vez por ronda.
        _state.value = _state.value.copy(
            feedback = message,
            correct = correct,
            answeredIndex = _state.value.index,
            respondidos = _state.value.respondidos + _state.value.index,
            correctCount = _state.value.correctCount + if (correct) 1 else 0,
        )
        val isLast = _state.value.index == _state.value.items.size - 1
        val nextHint = if (isLast) "Di siguiente para ver tu puntaje" else "Di siguiente para continuar"
        viewModelScope.launch {
            val user = if (_state.value.practice) null else sessionRepository.currentUser.value
            var levelUp = ""
            if (user != null) {
                if (item.sound != null) {
                    repository.registrarResultado(user.id, item.id, correct)
                    val nowLevel = repository.observeNivel(user.id).first().nivelActual
                    if (nowLevel > savedLevel) {
                        savedLevel = nowLevel
                        levelUp = " Subiste al nivel $nowLevel."
                    }
                } else {
                    vibrationRepository.registrarResultado(user.id, item.id, correct)
                }
            }
            voiceEngine.speak(
                "$message$levelUp $nextHint, o repite para escucharlo otra vez.",
                listenAfter = true,
            )
        }
    }

    /** Cómo se dice la respuesta en voz alta: el código guarda "centro", las grabaciones dicen "ambos lados". */
    private fun spokenAnswer(answer: String): String = if (VoiceText.normalize(answer) == "centro") "ambos lados" else answer

    /** Qué tipos de respuesta contiene la frase. Un solo tipo es una respuesta clara; varios no lo son. */
    private fun categoriesIn(text: String): Set<String> =
        ANSWER_WORDS.filterValues { words -> VoiceText.hasAny(text, words) }.keys

    /** +1 cuando el sonido fue de izquierda a derecha, -1 de derecha a izquierda, 0 cuando la frase no lo indica. */
    private fun movement(text: String): Int {
        val left = text.indexOf("izquierd")
        val right = text.indexOf("derech")
        return when {
            left >= 0 && right >= 0 -> if (left < right) 1 else -1
            right >= 0 && VoiceText.hasAny(text, "hacia la derecha", "a la derecha", "hacia el lado derecho", "se fue a la derecha", "va a la derecha") -> 1
            left >= 0 && VoiceText.hasAny(text, "hacia la izquierda", "a la izquierda", "hacia el lado izquierdo", "se fue a la izquierda", "va a la izquierda") -> -1
            else -> 0
        }
    }

    /**
     * Cada respuesta acepta las distintas formas en que la gente la dice. "Centro" y "ambos lados" son la
     * misma respuesta. El movimiento ("de izquierda a derecha") se juzga por el orden de los dos lados o por
     * hacia dónde fue el sonido ("hacia la derecha"). Cualquier otra respuesta es correcta solo cuando es el
     * único tipo de respuesta dicho.
     */
    private fun matches(spoken: String, expected: String): Boolean {
        val text = VoiceText.normalize(spoken)
        val want = VoiceText.normalize(expected)
        val categories = categoriesIn(text)
        return when (want) {
            "de izquierda a derecha" -> movement(text) > 0
            "de derecha a izquierda" -> movement(text) < 0
            "mixto" -> "mixto" in categories || categories == setOf("corto", "largo")
            else -> categories == setOf(want)
        }
    }

    override fun onCleared() {
        exerciseJob?.cancel()
        audioPlayer.detener()
        vibrationEngine.detener()
        super.onCleared()
    }

    private companion object {
        val LEVEL_COMMAND = Regex("(?:nivel (uno|dos|tres|1|2|3)|(primer|segundo|tercer) nivel)")

        // Cambiar la actividad necesita la palabra sola o una frase explícita: "vibró corto" es una respuesta.
        val VIBRATION_ONLY = setOf("vibracion", "vibraciones", "vibrar", "la vibracion", "con vibracion", "vibrador")
        val SOUNDS_ONLY = setOf("sonidos", "sonido", "audio", "audios", "los sonidos", "con sonidos", "oido")

        /** Todas las formas de decir cada respuesta. Solo palabras completas, para que "acerca" no sea "cerca". */
        val ANSWER_WORDS: Map<String, List<String>> = mapOf(
            "izquierda" to listOf("izquierda", "izquierdo", "lado izquierdo", "oido izquierdo"),
            "derecha" to listOf("derecha", "derecho", "lado derecho", "oido derecho"),
            "centro" to listOf(
                "centro", "ambos", "los dos", "medio", "en medio", "al centro", "de los dos lados", "por los dos lados",
                "los dos lados", "ambos lados", "ambos oidos", "los dos oidos", "de ambos lados", "parejo",
            ),
            "cerca" to listOf("cerca", "cercano", "cerquita", "pegado", "muy cerca", "fuerte", "de cerca"),
            "lejos" to listOf("lejos", "lejano", "distante", "alejado", "a lo lejos", "muy lejos", "bajito", "de lejos", "debil"),
            "enfrente" to listOf("enfrente", "frente", "adelante", "al frente", "de frente", "delante", "por delante"),
            "atras" to listOf("atras", "detras", "espalda", "de atras", "por atras", "por detras", "a mi espalda", "atrasito"),
            "corto" to listOf("corto", "cortos", "cortito", "breve", "breves", "rapido", "rapidos"),
            "largo" to listOf("largo", "largos", "prolongado", "extenso", "lento", "sostenido"),
            "mixto" to listOf("mixto", "mezcla", "mezclado", "combinado", "alternado", "variado", "corto y largo", "largo y corto"),
        )
    }
}
