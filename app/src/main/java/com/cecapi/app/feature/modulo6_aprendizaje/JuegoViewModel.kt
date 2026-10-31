package com.cecapi.app.feature.modulo6_aprendizaje

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.navigation.CecapiDestinations
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceState
import com.cecapi.app.core.voice.VoiceText
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CazaFase { PREPARADO, CUENTA_REGRESIVA, SONANDO, ENCONTRADO }

data class JuegoUiState(
    val tipo: JuegoTipo,
    // Caza el sonido
    val cazaFase: CazaFase = CazaFase.PREPARADO,
    val cuentaRegresiva: Int = 0,
    val segundos: Int = 0,
    val mejorTiempo: Int? = null,
    // Memoria de sonidos
    val preguntas: List<PreguntaMemoria> = emptyList(),
    val indice: Int = 0,
    val aciertos: Int = 0,
    val elegida: SonidoCotidiano? = null,
    val terminada: Boolean = false,
    // Marcador, turnos y estaciones: el reloj que corre hacia atrás
    val marcador: Marcador = Marcador(),
    val duracion: Int = 600,
    val restante: Int = 600,
    val corriendo: Boolean = false,
    val participantes: Int = 2,
    val actual: Int = 1,
    // Ritmo
    val bpm: Int = 60,
    val ritmoSonando: Boolean = false,
    // Guía por voz
    val ultimaIndicacion: String? = null,
) {
    val pregunta: PreguntaMemoria? get() = preguntas.getOrNull(indice)
}

/**
 * Un solo modelo para los juegos que acompaña el teléfono; el [JuegoTipo] llega en la ruta. Cada juego usa
 * su parte del estado. Los relojes y los sonidos se detienen al salir de la pantalla.
 */
@HiltViewModel
class JuegoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val voiceEngine: VoiceEngine,
    private val cues: FeedbackCues,
) : ViewModel() {

    private val tipo = JuegoTipo.desdeId(savedStateHandle.get<String>("tipo"))

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    private val _estado = MutableStateFlow(
        JuegoUiState(
            tipo = tipo,
            duracion = if (tipo == JuegoTipo.MARCADOR) 600 else 60,
            restante = if (tipo == JuegoTipo.MARCADOR) 600 else 60,
            participantes = if (tipo == JuegoTipo.ESTACIONES) 4 else 2,
        ),
    )
    val estado: StateFlow<JuegoUiState> = _estado.asStateFlow()

    private val _back = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val back: SharedFlow<Unit> = _back

    private var reloj: Job? = null
    private var ritmo: Job? = null
    private var reproductor: MediaPlayer? = null
    private var tono: ToneGenerator? = null

    init {
        if (tipo == JuegoTipo.MEMORIA_SONIDOS) _estado.value = _estado.value.copy(preguntas = MemoriaSonidos.ronda())
        viewModelScope.launch {
            voiceEngine.speechFor(CecapiDestinations.JUEGO).collect { onSpeech(it.text) }
        }
        val inicio = when (tipo) {
            JuegoTipo.CAZA_SONIDO -> "Toca Empezar, entrégale el teléfono a alguien y que lo esconda. En 10 segundos empieza a sonar."
            JuegoTipo.MEMORIA_SONIDOS -> "Escucha y dime qué sonido es."
            JuegoTipo.MARCADOR -> "Toca el equipo que anota, o di punto para el uno, o punto para el dos. Di inicia el tiempo para el reloj."
            JuegoTipo.TURNOS -> "Elige cuántos juegan y cuánto dura cada turno, y toca Empezar. Di siguiente para pasar el turno."
            JuegoTipo.ESTACIONES -> "Elige cuántas estaciones y cuánto dura cada una, y toca Empezar. Te aviso cuando haya que cambiar."
            JuegoTipo.RITMO -> "Toca Empezar para marcar el ritmo. Di más rápido o más lento para cambiarlo."
            JuegoTipo.GUIA -> "Para el compañero que guía: toca una dirección y la digo en voz alta."
        }
        voiceEngine.speak("${tipo.titulo}. ${tipo.comoSeJuega} $inicio", listenAfter = tipo != JuegoTipo.GUIA)
        if (tipo == JuegoTipo.MEMORIA_SONIDOS) {
            viewModelScope.launch {
                delay(9_000)
                reproducirPregunta()
            }
        }
    }

    private fun onSpeech(hablado: String) {
        val texto = VoiceText.normalize(hablado)
        fun has(vararg palabras: String) = VoiceText.hasAny(texto, *palabras)
        if (has("atras", "volver", "salir", "menu", "regresa", "regresar")) {
            _back.tryEmit(Unit)
            return
        }
        if (has("comandos", "ayuda", "que puedo decir")) {
            voiceEngine.speak("${tipo.comoSeJuega} Di atrás para salir.", listenAfter = true)
            return
        }
        when (tipo) {
            JuegoTipo.CAZA_SONIDO -> when {
                has("lo encontre", "encontrado", "aqui esta", "ya lo tengo", "encontre") -> encontrado()
                has("empieza", "empezar", "esconde", "comienza", "otra vez") -> empezarCaza()
                else -> noEntendi("Di empieza, o lo encontré.")
            }
            JuegoTipo.MEMORIA_SONIDOS -> {
                val pregunta = _estado.value.pregunta
                val dicho = pregunta?.let { MemoriaSonidos.reconocer(hablado, it.opciones) }
                when {
                    dicho != null -> responderMemoria(dicho)
                    has("repite", "otra vez", "de nuevo", "escuchar") -> reproducirPregunta()
                    has("siguiente", "sigue") -> siguientePregunta()
                    has("otra ronda", "nueva ronda", "jugar otra vez") -> nuevaRonda()
                    else -> noEntendi("Di el nombre del sonido, o repite.")
                }
            }
            JuegoTipo.MARCADOR -> when {
                has("quita", "resta", "corrige") && has("uno", "1") -> restarPunto(1)
                has("quita", "resta", "corrige") && has("dos", "2") -> restarPunto(2)
                has("punto") && has("uno", "1", "primero") -> sumarPunto(1)
                has("punto") && has("dos", "2", "segundo") -> sumarPunto(2)
                has("marcador", "como vamos", "cuanto van") -> voiceEngine.speak(_estado.value.marcador.enPalabras(), listenAfter = true)
                has("pausa", "deten", "para el tiempo") -> pausar()
                has("reinicia", "de nuevo", "nuevo partido") -> reiniciarPartido()
                has("inicia", "empieza", "arranca", "tiempo", "sigue") -> iniciarReloj()
                has("cuanto queda", "cuanto falta") -> decirRestante()
                else -> noEntendi("Di punto para el uno, punto para el dos, marcador, o inicia el tiempo.")
            }
            JuegoTipo.TURNOS, JuegoTipo.ESTACIONES -> when {
                has("siguiente", "cambio", "cambia", "paso", "ya") -> siguienteTurno(manual = true)
                has("pausa", "deten", "para") -> pausar()
                has("cuanto queda", "cuanto falta", "tiempo") -> decirRestante()
                has("empieza", "inicia", "comienza", "arranca", "sigue") -> iniciarReloj()
                else -> noEntendi("Di empieza, siguiente, pausa, o cuánto queda.")
            }
            JuegoTipo.RITMO -> when {
                has("mas rapido", "acelera", "sube") -> cambiarRitmo(+10)
                has("mas lento", "despacio", "baja") -> cambiarRitmo(-10)
                has("para", "deten", "alto", "pausa") -> detenerRitmo()
                has("empieza", "inicia", "comienza", "sigue") -> empezarRitmo()
                else -> noEntendi("Di empieza, para, más rápido o más lento.")
            }
            JuegoTipo.GUIA -> when {
                has("adelante", "enfrente", "derecho") -> guiar("Adelante")
                has("izquierda") -> guiar("Izquierda")
                has("derecha") -> guiar("Derecha")
                has("alto", "para", "detente") -> guiar("Alto")
                has("atras", "regresa") -> guiar("Atrás")
                else -> Unit
            }
        }
    }

    private fun noEntendi(pista: String) {
        cues.play(FeedbackCues.Cue.NOT_UNDERSTOOD)
        voiceEngine.speak("No entendí. $pista", listenAfter = true)
    }

    fun onMicTapped() = voiceEngine.startListening()

    fun onMicDoubleTap() = voiceEngine.mute()

    fun onCommandsRequested() = onSpeech("comandos")

    // ---- Caza el sonido ----------------------------------------------------------------------------------

    fun empezarCaza() {
        detenerTodo()
        _estado.value = _estado.value.copy(cazaFase = CazaFase.CUENTA_REGRESIVA, cuentaRegresiva = 10, segundos = 0)
        voiceEngine.speak("Escóndelo ya. Empieza a sonar en 10 segundos.")
        reloj = viewModelScope.launch {
            while (isActive && _estado.value.cuentaRegresiva > 0) {
                delay(1_000)
                _estado.value = _estado.value.copy(cuentaRegresiva = _estado.value.cuentaRegresiva - 1)
            }
            _estado.value = _estado.value.copy(cazaFase = CazaFase.SONANDO)
            reproducir("campana", enBucle = true)
            while (isActive) {
                delay(1_000)
                _estado.value = _estado.value.copy(segundos = _estado.value.segundos + 1)
            }
        }
    }

    fun encontrado() {
        if (_estado.value.cazaFase != CazaFase.SONANDO) return
        detenerTodo()
        val segundos = _estado.value.segundos
        val mejor = _estado.value.mejorTiempo
        val esRecord = mejor == null || segundos < mejor
        _estado.value = _estado.value.copy(
            cazaFase = CazaFase.ENCONTRADO,
            mejorTiempo = if (esRecord) segundos else mejor,
        )
        cues.play(FeedbackCues.Cue.SUCCESS)
        voiceEngine.speak(
            "¡Lo encontraste en ${tiempoEnPalabras(segundos)}!" + if (esRecord && mejor != null) " Es tu mejor tiempo." else "",
            listenAfter = true,
        )
    }

    // ---- Memoria de sonidos -------------------------------------------------------------------------------

    fun reproducirPregunta() {
        val pregunta = _estado.value.pregunta ?: return
        reproducir(pregunta.sonido.archivo, enBucle = false)
    }

    fun responderMemoria(opcion: SonidoCotidiano) {
        val s = _estado.value
        val pregunta = s.pregunta ?: return
        if (s.elegida != null) return
        val bien = opcion == pregunta.sonido
        _estado.value = s.copy(elegida = opcion, aciertos = s.aciertos + if (bien) 1 else 0)
        detenerSonido()
        cues.play(if (bien) FeedbackCues.Cue.SUCCESS else FeedbackCues.Cue.ERROR)
        voiceEngine.speak(
            (if (bien) "¡Correcto! Era ${pregunta.sonido.nombre.lowercase()}." else "No, era ${pregunta.sonido.nombre.lowercase()}.") +
                " Di siguiente.",
            listenAfter = true,
        )
    }

    fun siguientePregunta() {
        val s = _estado.value
        if (s.indice + 1 >= s.preguntas.size) {
            _estado.value = s.copy(terminada = true, elegida = null)
            voiceEngine.speak("Terminaste. Acertaste ${s.aciertos} de ${s.preguntas.size}. Di otra ronda para jugar otra vez.", listenAfter = true)
            return
        }
        _estado.value = s.copy(indice = s.indice + 1, elegida = null)
        reproducirPregunta()
    }

    fun nuevaRonda() {
        _estado.value = _estado.value.copy(preguntas = MemoriaSonidos.ronda(), indice = 0, aciertos = 0, elegida = null, terminada = false)
        reproducirPregunta()
    }

    // ---- Marcador ----------------------------------------------------------------------------------------

    fun sumarPunto(equipo: Int) {
        val marcador = _estado.value.marcador.sumar(equipo)
        _estado.value = _estado.value.copy(marcador = marcador)
        cues.play(FeedbackCues.Cue.SUCCESS)
        voiceEngine.speak("Punto para el equipo ${if (equipo == 1) "uno" else "dos"}. ${marcador.enPalabras()}")
    }

    fun restarPunto(equipo: Int) {
        val marcador = _estado.value.marcador.restar(equipo)
        _estado.value = _estado.value.copy(marcador = marcador)
        voiceEngine.speak("Quité un punto al equipo ${if (equipo == 1) "uno" else "dos"}. ${marcador.enPalabras()}")
    }

    fun reiniciarPartido() {
        detenerTodo()
        _estado.value = _estado.value.copy(marcador = Marcador(), restante = _estado.value.duracion, corriendo = false)
        voiceEngine.speak("Partido nuevo. Cero a cero.")
    }

    // ---- Reloj (marcador, turnos y estaciones) -----------------------------------------------------------

    fun elegirDuracion(segundos: Int) {
        pausarSilencioso()
        _estado.value = _estado.value.copy(duracion = segundos, restante = segundos)
        voiceEngine.speak("Cada ${if (tipo == JuegoTipo.MARCADOR) "partido" else if (tipo == JuegoTipo.TURNOS) "turno" else "estación"} dura ${tiempoEnPalabras(segundos)}.")
    }

    fun elegirParticipantes(cuantos: Int) {
        _estado.value = _estado.value.copy(participantes = cuantos, actual = 1)
        voiceEngine.speak(if (tipo == JuegoTipo.ESTACIONES) "$cuantos estaciones." else "Juegan $cuantos.")
    }

    fun iniciarReloj() {
        if (_estado.value.corriendo) return
        if (_estado.value.restante <= 0) _estado.value = _estado.value.copy(restante = _estado.value.duracion)
        _estado.value = _estado.value.copy(corriendo = true)
        voiceEngine.speak(
            when (tipo) {
                JuegoTipo.TURNOS -> "Turno del jugador ${_estado.value.actual}."
                JuegoTipo.ESTACIONES -> "Estación ${_estado.value.actual}. ¡A jugar!"
                else -> "Corre el tiempo."
            },
        )
        reloj?.cancel()
        reloj = viewModelScope.launch {
            while (isActive && _estado.value.restante > 0) {
                delay(1_000)
                val restante = _estado.value.restante - 1
                _estado.value = _estado.value.copy(restante = restante)
                if (restante == 10) voiceEngine.speak("Quedan 10 segundos.")
            }
            if (!isActive) return@launch
            if (tipo == JuegoTipo.MARCADOR) {
                pitido()
                _estado.value = _estado.value.copy(corriendo = false)
                voiceEngine.speak("¡Se acabó el tiempo! ${_estado.value.marcador.enPalabras()}", listenAfter = true)
            } else {
                siguienteTurno(manual = false)
            }
        }
    }

    /** Pasa al siguiente jugador o estación y vuelve a correr el reloj. */
    fun siguienteTurno(manual: Boolean) {
        val s = _estado.value
        val siguiente = if (s.actual >= s.participantes) 1 else s.actual + 1
        _estado.value = s.copy(actual = siguiente, restante = s.duracion, corriendo = false)
        reloj?.cancel()
        if (!manual) pitido()
        val etiqueta = if (tipo == JuegoTipo.ESTACIONES) "¡Cambio! Pasen a la estación $siguiente" else "Turno del jugador $siguiente"
        voiceEngine.speak("$etiqueta.")
        iniciarRelojSinAviso()
    }

    private fun iniciarRelojSinAviso() {
        _estado.value = _estado.value.copy(corriendo = true)
        reloj = viewModelScope.launch {
            while (isActive && _estado.value.restante > 0) {
                delay(1_000)
                val restante = _estado.value.restante - 1
                _estado.value = _estado.value.copy(restante = restante)
                if (restante == 10) voiceEngine.speak("Quedan 10 segundos.")
            }
            if (isActive) siguienteTurno(manual = false)
        }
    }

    fun pausar() {
        pausarSilencioso()
        voiceEngine.speak("En pausa. Quedan ${tiempoEnPalabras(_estado.value.restante)}.", listenAfter = true)
    }

    private fun pausarSilencioso() {
        reloj?.cancel()
        _estado.value = _estado.value.copy(corriendo = false)
    }

    fun decirRestante() = voiceEngine.speak("Quedan ${tiempoEnPalabras(_estado.value.restante)}.", listenAfter = true)

    // ---- Ritmo -------------------------------------------------------------------------------------------

    fun empezarRitmo() {
        if (_estado.value.ritmoSonando) return
        _estado.value = _estado.value.copy(ritmoSonando = true)
        voiceEngine.speak("Ritmo de ${_estado.value.bpm} por minuto.")
        ritmo = viewModelScope.launch {
            delay(1_500)
            while (isActive) {
                pitidoCorto()
                delay(60_000L / _estado.value.bpm)
            }
        }
    }

    fun detenerRitmo() {
        ritmo?.cancel()
        _estado.value = _estado.value.copy(ritmoSonando = false)
        voiceEngine.speak("Ritmo detenido.", listenAfter = true)
    }

    fun cambiarRitmo(delta: Int) {
        val bpm = (_estado.value.bpm + delta).coerceIn(30, 180)
        _estado.value = _estado.value.copy(bpm = bpm)
        if (!_estado.value.ritmoSonando) voiceEngine.speak("$bpm por minuto.")
    }

    // ---- Guía por voz ------------------------------------------------------------------------------------

    fun guiar(indicacion: String) {
        _estado.value = _estado.value.copy(ultimaIndicacion = indicacion)
        voiceEngine.speak(indicacion)
    }

    // ---- Sonido ------------------------------------------------------------------------------------------

    private fun reproducir(archivo: String, enBucle: Boolean) {
        detenerSonido()
        val id = context.resources.getIdentifier(archivo, "raw", context.packageName)
        if (id == 0) return
        reproductor = try {
            MediaPlayer.create(context, id)?.apply {
                isLooping = enBucle
                setVolume(1f, 1f)
                start()
            }
        } catch (e: Exception) {
            Log.w("CecapiJuegos", "No se pudo reproducir $archivo", e)
            null
        }
    }

    private fun detenerSonido() {
        reproductor?.let {
            try {
                it.stop()
            } catch (_: IllegalStateException) {
            }
            it.release()
        }
        reproductor = null
    }

    private fun generadorDeTonos(): ToneGenerator? = tono ?: try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 55).also { tono = it }
    } catch (e: RuntimeException) {
        null
    }

    private fun pitido() {
        generadorDeTonos()?.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
    }

    private fun pitidoCorto() {
        generadorDeTonos()?.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
    }

    private fun detenerTodo() {
        reloj?.cancel()
        ritmo?.cancel()
        detenerSonido()
        _estado.value = _estado.value.copy(corriendo = false, ritmoSonando = false)
    }

    /** Al salir de la app o de la pantalla todo se calla. */
    fun onAppStopped() = detenerTodo()

    override fun onCleared() {
        detenerTodo()
        tono?.release()
        tono = null
        super.onCleared()
    }
}
