package com.cecapi.app.feature.modulo5_solicitudes

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.navigation.CecapiDestinations
import com.cecapi.app.core.util.ConnectivityObserver
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceMessages
import com.cecapi.app.core.voice.VoiceState
import com.cecapi.app.core.voice.VoiceText
import com.cecapi.app.feature.modulo1_aplicacionprincipal.CommandCatalog
import com.cecapi.app.feature.modulo1_aplicacionprincipal.SessionRepository
import com.cecapi.app.feature.modulo3_asistenteinteligente.AiAssistantRepository
import com.cecapi.app.feature.modulo3_asistenteinteligente.AiMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** En qué paso va la consulta: esperando que se pida algo, generando, o ya con el documento a la vista. */
enum class ConsultaPaso { ESPERANDO, GENERANDO, LISTO }

data class ConsultasUiState(
    val paso: ConsultaPaso = ConsultaPaso.ESPERANDO,
    /** Lo que la persona pidió, tal cual lo dijo o lo escribió. */
    val consulta: String = "",
    /** El documento que devolvió la IA, ya sin marcas de formato. Null mientras no hay. */
    val documento: String? = null,
    val error: String? = null,
)

/**
 * Consultas: se pide algo (por voz o escrito) y la IA genera un documento, como en cualquier asistente de IA.
 * El resultado se muestra en su propio panel, aparte de lo que se pidió.
 *
 * Más adelante cada institución podrá tener una plantilla propia: el texto que genera la IA se colocaría dentro
 * del formato de la institución. Todavía no existe; por ahora el documento se muestra sin formato de institución.
 */
@HiltViewModel
class ConsultasViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val sessionRepository: SessionRepository,
    private val repository: AiAssistantRepository,
    private val cues: FeedbackCues,
    connectivityObserver: ConnectivityObserver,
) : ViewModel() {

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    private val isOnline: StateFlow<Boolean> = connectivityObserver.observe().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), true,
    )

    private val _uiState = MutableStateFlow(ConsultasUiState())
    val uiState: StateFlow<ConsultasUiState> = _uiState.asStateFlow()

    private val _back = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val back: SharedFlow<Unit> = _back

    init {
        // Lo que se dice aquí es el pedido de un documento, no un comando del teléfono: "la hora de entrada" o
        // "el volumen de ventas" no deben tomarse como "qué hora es" o "sube el volumen".
        voiceEngine.rawInput = true
        voiceEngine.speak(
            "Consultas. Dime qué documento necesitas, por ejemplo: una carta para pedir un justificante. " +
                CommandCatalog.hint("consultas"),
            listenAfter = true,
        )
        viewModelScope.launch {
            voiceEngine.speechFor(CecapiDestinations.REQUESTS).collect { speech -> onSpeech(speech.text) }
        }
    }

    /** Solo las frases completas de control cuentan como comando: un pedido puede contener "atrás" o "repite". */
    private fun onSpeech(spoken: String) {
        val texto = VoiceText.normalize(spoken)
        val estado = _uiState.value
        when {
            estado.paso == ConsultaPaso.GENERANDO -> Unit
            CommandCatalog.isRequest(spoken) && texto.split(" ").size <= 4 ->
                voiceEngine.speak(CommandCatalog.CONSULTAS, listenAfter = true)
            texto in ATRAS -> _back.tryEmit(Unit)
            estado.paso == ConsultaPaso.LISTO && texto in LEER -> leerDocumento()
            estado.paso == ConsultaPaso.LISTO && texto in NUEVA -> nuevaConsulta()
            else -> generar(spoken)
        }
    }

    /** Lee en voz alta los comandos de esta pantalla; el botón del encabezado lo invoca. */
    fun onCommandsRequested() {
        voiceEngine.speak(CommandCatalog.CONSULTAS, listenAfter = true)
    }

    fun onMicTapped() = voiceEngine.startListening()

    /** Dos toques rápidos en el micrófono silencian al asistente, para quien usa el tacto en vez de la voz. */
    fun onMicDoubleTap() = voiceEngine.mute()

    /** Pide el documento. Llega por voz o desde el cuadro de texto. */
    fun generar(consulta: String) {
        val pedido = consulta.trim()
        if (pedido.isEmpty() || _uiState.value.paso == ConsultaPaso.GENERANDO) return
        val usuario = sessionRepository.currentUser.value
        if (usuario == null) {
            voiceEngine.speak(VoiceMessages.NEEDS_LOGIN)
            return
        }
        if (!isOnline.value) {
            fallo(pedido, "No hay conexión a internet. Las consultas necesitan internet para generar el documento.")
            return
        }
        _uiState.value = ConsultasUiState(paso = ConsultaPaso.GENERANDO, consulta = pedido)
        viewModelScope.launch {
            try {
                val resultado = voiceEngine.speakWhile { repository.ask(usuario.id, "$INSTRUCCION $pedido") }
                resultado.onSuccess { texto ->
                    val documento = limpiarFormato(texto)
                    if (documento.isBlank()) {
                        fallo(pedido, "No recibí ningún documento. Intenta pedirlo de otra forma.")
                    } else {
                        _uiState.value = ConsultasUiState(paso = ConsultaPaso.LISTO, consulta = pedido, documento = documento)
                        cues.play(FeedbackCues.Cue.SUCCESS)
                        voiceEngine.speak("Listo, el documento está en pantalla. Di lee el documento para escucharlo, o nueva consulta.", listenAfter = true)
                    }
                }.onFailure { error -> fallo(pedido, AiMessages.forFailure(error)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Una falla inesperada no debe cerrar la aplicación: se avisa y se puede intentar de nuevo.
                Log.e("CecapiConsultas", "generar falló", e)
                fallo(pedido, "No pude generar el documento por un problema inesperado. Intenta de nuevo.")
            }
        }
    }

    private fun fallo(pedido: String, mensaje: String) {
        _uiState.value = ConsultasUiState(paso = ConsultaPaso.ESPERANDO, consulta = pedido, error = mensaje)
        cues.play(FeedbackCues.Cue.ERROR)
        voiceEngine.speak("$mensaje Puedes volver a pedirlo.", listenAfter = true)
    }

    fun leerDocumento() {
        val documento = _uiState.value.documento ?: return voiceEngine.speak("Todavía no hay un documento. Dime qué necesitas.", listenAfter = true)
        voiceEngine.speak(documento)
    }

    fun nuevaConsulta() {
        voiceEngine.stopSpeaking()
        _uiState.value = ConsultasUiState()
        voiceEngine.speak("Dime qué documento necesitas.", listenAfter = true)
    }

    override fun onCleared() {
        voiceEngine.rawInput = false
        super.onCleared()
    }

    internal companion object {
        /** El pedido se manda con esta instrucción para que la respuesta sea el documento y nada más. */
        const val INSTRUCCION =
            "Redacta en español el documento que se pide a continuación. Entrega solo el documento listo para " +
                "usar, con título, sin explicaciones antes ni después y sin formato especial. Pedido:"

        private val ATRAS = setOf("atras", "volver", "vuelve", "regresa", "regresar", "salir", "menu", "inicio")
        private val LEER = setOf("lee", "leer", "lee el documento", "leelo", "repite", "repetir")
        private val NUEVA = setOf("nueva consulta", "otra consulta", "otro documento", "nuevo documento")

        private val MARCAS_LINEA = Regex("""^\s{0,3}#{1,6}\s*""", RegexOption.MULTILINE)
        private val NEGRITA = Regex("""\*\*|__""")
        private val VINETA = Regex("""^\s*[*•]\s+""", RegexOption.MULTILINE)

        /** Quita las marcas de Markdown (#, **, viñetas con asterisco) para que el documento se vea y se lea limpio. */
        fun limpiarFormato(texto: String): String = texto
            .replace(MARCAS_LINEA, "")
            .replace(NEGRITA, "")
            .replace(VINETA, "- ")
            .trim()
    }
}
