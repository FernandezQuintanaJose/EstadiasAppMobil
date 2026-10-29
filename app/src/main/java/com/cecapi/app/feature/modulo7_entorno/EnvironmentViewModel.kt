package com.cecapi.app.feature.modulo7_entorno

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.navigation.CecapiDestinations
import com.cecapi.app.core.ui.GALLERY_WORDS
import com.cecapi.app.core.util.StorageReport
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceState
import com.cecapi.app.core.voice.VoiceText
import com.cecapi.app.feature.modulo1_aplicacionprincipal.CommandCatalog
import com.cecapi.app.feature.modulo1_aplicacionprincipal.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EnvironmentUiState(
    val isProcessing: Boolean = false,
    val descripcion: String? = null,
    val errorMessage: String? = null,
    /** La foto que se está describiendo, mostrada en su propia área en lugar de la vista de cámara en vivo mientras existe. */
    val photoPath: String? = null,
)

@HiltViewModel
class EnvironmentViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val sessionRepository: SessionRepository,
    private val repository: EnvironmentRepository,
    private val cues: FeedbackCues,
    private val storageReport: StorageReport,
) : ViewModel() {

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    private val _uiState = MutableStateFlow(EnvironmentUiState())
    val uiState: StateFlow<EnvironmentUiState> = _uiState.asStateFlow()

    // La pantalla es dueña de la cámara, así que los comandos de voz la alcanzan a través de estos.
    private val _captureRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val captureRequests: SharedFlow<Unit> = _captureRequests

    private val _back = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val back: SharedFlow<Unit> = _back

    // Le pide a la pantalla que abra el selector de fotos del sistema (ella es dueña del activity result launcher).
    private val _pickRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val pickRequests: SharedFlow<Unit> = _pickRequests

    private val _routes = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val routes: SharedFlow<String> = _routes

    init {
        viewModelScope.launch {
            voiceEngine.speechFor(CecapiDestinations.ENVIRONMENT).collect { speech -> onSpeech(speech.text) }
        }
        voiceEngine.speak(
            "Descripción del entorno. Apunta la cámara al frente y di qué hay enfrente. " +
                CommandCatalog.hint("cámara"),
            listenAfter = true,
        )
    }

    /** Los comandos que esta pantalla responde, del más específico al más general. */
    private fun onSpeech(spoken: String) {
        val text = VoiceText.normalize(spoken)
        fun has(vararg words: String) = VoiceText.hasAny(text, *words)
        when {
            CommandCatalog.isRequest(spoken) -> voiceEngine.speak(CommandCatalog.ENVIRONMENT, listenAfter = true)
            has("atras", "volver", "vuelve", "regresa", "regresar", "salir", "menu", "inicio", "pantalla anterior") -> _back.tryEmit(Unit)
            has("cuanto llevo", "cuanto dinero llevo", "que llevo", "cuanto suman", "total de billetes", "dime el total") -> decirTotalBilletes()
            has("borra los billetes", "borrar billetes", "limpia los billetes", "empieza de nuevo", "reinicia los billetes", "borrar total") -> reiniciarBilletes()
            has("agrega este billete", "agregar billete", "agrega billete", "suma este billete", "sumar billete", "anota este billete", "anotar billete") -> pedirSumarBillete()
            has("billete", "billetes", "dinero", "cuanto vale", "cuanto dinero") -> pedirCapturaBillete()
            has("leer texto", "lee", "leer", "texto", "documento") -> {
                cues.play(FeedbackCues.Cue.NAVIGATE)
                voiceEngine.speak("Cambiando al lector de texto.")
                _routes.tryEmit(CecapiDestinations.DOCUMENT_READER)
            }
            has(*GALLERY_WORDS) -> pedirGaleria()
            has("otra vez", "repite", "repetir", "de nuevo", "dilo") -> repetir()
            has("enfrente", "describe", "descripcion", "foto", "fotografia", "captura", "toma", "analiza",
                "que hay", "que ves", "entorno") -> pedirCaptura()
            else -> {
                cues.play(FeedbackCues.Cue.NOT_UNDERSTOOD)
                voiceEngine.speak(
                    "No entendí. Di qué hay enfrente, repite, o atrás. " + CommandCatalog.hint("cámara"),
                    listenAfter = true,
                )
            }
        }
    }

    /** Cada foto se guarda en el teléfono, así que con casi nada de espacio es mejor avisarlo que llenarlo. */
    private fun hayEspacio(): Boolean {
        if (storageReport.criticallyLow()) {
            cues.play(FeedbackCues.Cue.WARNING)
            voiceEngine.speak(
                "Queda muy poco espacio en el teléfono y no puedo guardar más fotos. " +
                    "Ve a configuración y di libera espacio, o limpia la caché.",
            )
            return false
        }
        return true
    }

    // true cuando la próxima foto es para leer un billete (dijo "qué billete es") y no para describir el entorno.
    private var buscandoBilleteActual = false

    // true cuando ese billete se va a sumar a la cuenta de "uno en uno" (dijo "agrega este billete").
    private var sumandoBilleteActual = false

    // Cuenta exacta de los billetes que se han mostrado uno por uno, para sumar el total.
    private val billetesContados = mutableMapOf<DenominacionBillete, Int>()

    /** También es lo que llaman el botón en pantalla y el chip "qué hay enfrente". */
    fun pedirCaptura() {
        if (_uiState.value.isProcessing || !hayEspacio()) return
        buscandoBilleteActual = false
        sumandoBilleteActual = false
        voiceEngine.speak("Tomando foto.")
        _captureRequests.tryEmit(Unit)
    }

    /** Lo que dice "qué billete es" y el botón del mismo nombre: toma la foto y dice de cuánto es el billete. */
    fun pedirCapturaBillete() {
        if (_uiState.value.isProcessing || !hayEspacio()) return
        buscandoBilleteActual = true
        sumandoBilleteActual = false
        voiceEngine.speak("Pon el billete plano frente a la cámara. Tomando foto.")
        _captureRequests.tryEmit(Unit)
    }

    /** Vuelve a la cámara para tomar otra foto: quita la foto y la descripción anteriores. */
    /** "Agrega este billete": toma la foto de un billete y, si se lee con claridad, lo suma a la cuenta. */
    fun pedirSumarBillete() {
        if (_uiState.value.isProcessing || !hayEspacio()) return
        buscandoBilleteActual = true
        sumandoBilleteActual = true
        voiceEngine.speak("Muéstrame un solo billete, plano frente a la cámara. Tomando foto.")
        _captureRequests.tryEmit(Unit)
    }

    /** "Cuánto llevo": dice cuántos billetes se han sumado y el total. */
    fun decirTotalBilletes() {
        voiceEngine.speak(fraseTotalBilletes(), listenAfter = true)
    }

    /** "Borra los billetes": empieza la cuenta de cero. */
    fun reiniciarBilletes() {
        billetesContados.clear()
        voiceEngine.speak("Listo. Empecé la cuenta de billetes desde cero.", listenAfter = true)
    }

    private fun fraseTotalBilletes(): String {
        if (billetesContados.isEmpty()) return "Todavía no has sumado ningún billete. Di agrega este billete."
        val partes = billetesContados.entries.sortedByDescending { it.key.valor }.map { (d, n) ->
            if (n == 1) "un billete de ${d.nombreHablado}" else "$n billetes de ${d.nombreHablado}"
        }
        val total = billetesContados.entries.sumOf { it.key.valor * it.value }
        val cuantos = billetesContados.values.sum()
        val unidos = if (partes.size == 1) partes[0] else partes.dropLast(1).joinToString(", ") + " y " + partes.last()
        return "Llevas $unidos. Son $cuantos ${if (cuantos == 1) "billete" else "billetes"}, $total pesos en total."
    }

    /** Suma el billete de la foto a la cuenta solo si es uno solo y se leyó con claridad; si no, dice por qué. */
    private fun sumarALaCuenta(resultado: ResultadoBillete?): String {
        val d = resultado?.denominacion
        if (resultado == null || d == null || resultado.hayVarios) {
            return "No sumé nada. Para llevar la cuenta, muéstrame un solo billete a la vez, plano y con buena luz."
        }
        billetesContados[d] = (billetesContados[d] ?: 0) + 1
        return "Anotado, un billete de ${d.nombreHablado}. " + fraseTotalBilletes()
    }

    fun tomarOtraFoto() {
        if (_uiState.value.isProcessing) return
        buscandoBilleteActual = false
        sumandoBilleteActual = false
        _uiState.value = EnvironmentUiState()
        voiceEngine.speak("Listo. Apunta la cámara y di qué hay enfrente.")
    }

    fun repetir() {
        val descripcion = _uiState.value.descripcion
        if (descripcion == null) {
            voiceEngine.speak("Todavía no he descrito nada. Di qué hay enfrente.", listenAfter = true)
        } else {
            voiceEngine.speak(descripcion)
        }
    }

    fun onCommandsRequested() {
        voiceEngine.speak(CommandCatalog.ENVIRONMENT, listenAfter = true)
    }

    fun onMicTapped() = voiceEngine.startListening()

    /** Dos toques rápidos en el micrófono silencian al asistente, para quien usa el tacto en vez de la voz. */
    fun onMicDoubleTap() = voiceEngine.mute()

    fun onCameraPermissionDenied() {
        voiceEngine.speak("Sin el permiso de la cámara no puedo describir lo que hay enfrente. Actívalo en los ajustes de la aplicación.")
    }

    /**
     * "Elige una foto": abre el selector de fotos del sistema. La persona elige una foto y la aplicación
     * solo ve esa; no hay permiso sobre toda la galería. Se dice en voz alta primero para que siempre sea
     * su decisión.
     */
    fun pedirGaleria() {
        if (_uiState.value.isProcessing || !hayEspacio()) return
        buscandoBilleteActual = false
        sumandoBilleteActual = false
        voiceEngine.speak("Voy a abrir tus fotos. Elige la imagen que quieres que describa; solo veré esa.")
        _pickRequests.tryEmit(Unit)
    }

    fun onPickCancelled() {
        voiceEngine.speak("No elegiste ninguna foto. Di qué hay enfrente, o elige una foto.", listenAfter = true)
    }

    fun onPickFailed() {
        cues.play(FeedbackCues.Cue.ERROR)
        voiceEngine.speak("No pude abrir esa imagen. Prueba con otra.", listenAfter = true)
    }

    fun onCaptureFailed() {
        val message = "No pude tomar la foto. Revisa que la cámara esté libre e intenta de nuevo."
        _uiState.value = EnvironmentUiState(errorMessage = message)
        cues.play(FeedbackCues.Cue.ERROR)
        voiceEngine.speak(message, listenAfter = true)
    }

    fun onPhotoCaptured(imageUri: Uri, rutaImagen: String) {
        // La cámara es libre para cualquiera: sin nadie con sesión iniciada, la descripción igual se dice en
        // voz alta, solo que no se guarda en un historial (processCapturedPhoto omite guardar cuando usuarioId es null).
        val usuarioId = sessionRepository.currentUser.value?.id
        _uiState.value = EnvironmentUiState(isProcessing = true, photoPath = rutaImagen)
        val buscarBillete = buscandoBilleteActual
        val sumarBillete = sumandoBilleteActual
        buscandoBilleteActual = false
        sumandoBilleteActual = false
        voiceEngine.speak(if (buscarBillete) "Leyendo el billete." else "Analizando el entorno.")
        viewModelScope.launch {
            repository.processCapturedPhoto(usuarioId, imageUri, rutaImagen, buscarBillete)
                .onSuccess { result ->
                    // Si era para llevar la cuenta, lo que se dice es lo anotado y el total, no la descripción suelta.
                    val dicho = if (sumarBillete) sumarALaCuenta(result.billetes) else result.descripcion
                    _uiState.value = EnvironmentUiState(descripcion = dicho, photoPath = rutaImagen)
                    voiceEngine.speak(dicho)
                }
                .onFailure {
                    val message = "No pude analizar la imagen. Intenta de nuevo."
                    _uiState.value = EnvironmentUiState(errorMessage = message)
                    cues.play(FeedbackCues.Cue.ERROR)
                    voiceEngine.speak(message, listenAfter = true)
                }
        }
    }
}
