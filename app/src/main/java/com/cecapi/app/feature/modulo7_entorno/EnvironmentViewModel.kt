package com.cecapi.app.feature.modulo7_entorno

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.navigation.CecapiDestinations
import com.cecapi.app.core.ui.GALLERY_WORDS
import com.cecapi.app.core.util.ConnectivityObserver
import com.cecapi.app.core.util.StorageReport
import com.cecapi.app.core.voice.IntentFallback
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceState
import com.cecapi.app.core.voice.VoiceText
import com.cecapi.app.feature.modulo1_aplicacionprincipal.CommandCatalog
import com.cecapi.app.feature.modulo1_aplicacionprincipal.ServicioDeCuentas
import com.cecapi.app.feature.modulo3_asistenteinteligente.AiAssistantApi
import com.cecapi.app.feature.modulo3_asistenteinteligente.AiMessages
import com.cecapi.app.feature.modulo3_asistenteinteligente.FotoParaIa
import com.cecapi.app.feature.modulo3_asistenteinteligente.ModoVision
import kotlinx.coroutines.Dispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val sessionRepository: ServicioDeCuentas,
    private val repository: EnvironmentRepository,
    private val cues: FeedbackCues,
    private val storageReport: StorageReport,
    // Módulo 3: describir la última foto con la IA del servidor (/ver), solo si la persona lo pide.
    private val aiApi: AiAssistantApi,
    private val intentFallback: IntentFallback,
    connectivityObserver: ConnectivityObserver,
    // Visión local: describe las fotos generales sin internet. Nunca se usa para billetes ni monedas.
    private val iaLocal: SmolVlmLocal,
) : ViewModel() {

    private val isOnline: StateFlow<Boolean> = connectivityObserver.observe().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), true,
    )

    /** La última foto que se describió en el teléfono, y si era de un billete o moneda, para la segunda opinión. */
    private var ultimaFoto: String? = null
    private var ultimaFueBillete = false
    private var ultimaFueMoneda = false

    // Frase exacta que ya se dijo para la moneda. Se conserva para repetirla tal cual, sin volver
    // a clasificar ni arriesgar una denominación distinta. En una foto general mantiene la regla
    // de no nombrar la denominación cuando la lectura vino de un rescate.
    private var ultimaMonedaFrase: String? = null

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
        // Deja listos el modelo de visión y el traductor en segundo plano para la primera foto.
        iaLocal.prepararTraductor()
        iaLocal.precargar()
    }

    override fun onCleared() {
        iaLocal.liberar()
        super.onCleared()
    }

    /** Los comandos que esta pantalla responde, del más específico al más general. */
    private fun onSpeech(spoken: String) {
        val text = VoiceText.normalize(spoken)
        fun has(vararg words: String) = VoiceText.hasAny(text, *words)
        when {
            CommandCatalog.isRequest(spoken) -> voiceEngine.speak(CommandCatalog.ENVIRONMENT, listenAfter = true)
            has("atras", "volver", "vuelve", "regresa", "regresar", "salir", "menu", "inicio", "pantalla anterior") -> _back.tryEmit(Unit)
            // Antes que "describe" y "billete": "describe con la IA" no es tomar otra foto.
            has("con inteligencia artificial", "con la ia", "con ia", "mas detalle", "mas detalles",
                "describelo mejor", "describe mejor", "explicame la foto") -> describirConIa()
            has("cuanto llevo", "cuanto dinero llevo", "que llevo", "cuanto suman", "total de billetes", "dime el total") -> decirTotalBilletes()
            has("borra los billetes", "borrar billetes", "limpia los billetes", "empieza de nuevo", "reinicia los billetes", "borrar total") -> reiniciarBilletes()
            has(
                "agrega esta moneda", "agregar moneda", "agrega moneda", "suma esta moneda", "sumar moneda",
                "suma la moneda", "sumar la moneda", "anota esta moneda", "anotar moneda", "anota la moneda",
                "anotar la moneda", "guarda la moneda", "guardar la moneda", "guarda moneda", "guardar moneda",
                "cuenta la moneda", "contar la moneda",
            ) -> pedirSumarMoneda()
            has("borra las monedas", "borrar monedas", "limpia las monedas", "reinicia las monedas") -> reiniciarMonedas()
            has(
                "que moneda es", "que moneda", "cuanto vale la moneda", "cuanto es la moneda",
                "reconoce la moneda", "reconocer la moneda", "moneda", "monedas",
            ) -> pedirCapturaMoneda()
            has("agrega este billete", "agregar billete", "agrega billete", "suma este billete", "sumar billete", "anota este billete", "anotar billete") -> pedirSumarBillete()
            has("billete", "billetes", "dinero", "cuanto vale", "cuanto dinero") -> pedirCapturaBillete()
            has("leer texto", "lee", "leer", "texto", "documento") -> cambiarALectura()
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

    // true cuando la próxima foto es para reconocer una moneda (dijo "qué moneda es").
    private var buscandoMonedaActual = false

    // true cuando esa moneda se va a sumar a la cuenta de "una en una" (dijo "agrega esta moneda").
    private var sumandoMonedaActual = false

    // Cuenta de las monedas que se han mostrado una por una, para sumar el total junto con los billetes.
    private val monedasContadas = mutableMapOf<DenominacionMoneda, Int>()

    /**
     * Botón grande "Tomar foto": vibra, dice la cuenta regresiva y captura al terminar. Es el
     * mismo botón de siempre; solo se le agregó la cuenta para que la persona tenga tiempo de
     * apuntar. Los comandos de voz siguen tomando la foto de inmediato.
     */
    fun pedirFotoConCuenta() {
        if (_uiState.value.isProcessing || !hayEspacio()) return
        limpiarModo()
        _uiState.value = EnvironmentUiState(isProcessing = true)
        cues.play(FeedbackCues.Cue.TAP, withSound = false)
        voiceEngine.speak("Tomando foto en 3, 2, 1")
        viewModelScope.launch {
            delay(3_200)
            _captureRequests.tryEmit(Unit)
        }
    }

    /** También es lo que llaman el chip "qué hay enfrente" y la voz. */
    fun pedirCaptura() {
        if (_uiState.value.isProcessing || !hayEspacio()) return
        limpiarModo()
        voiceEngine.speak("Tomando foto.")
        _captureRequests.tryEmit(Unit)
    }

    private fun limpiarModo() {
        buscandoBilleteActual = false
        sumandoBilleteActual = false
        buscandoMonedaActual = false
        sumandoMonedaActual = false
    }

    /** Lo que dice "qué billete es" y el botón del mismo nombre: toma la foto y dice de cuánto es el billete. */
    fun pedirCapturaBillete() {
        if (_uiState.value.isProcessing || !hayEspacio()) return
        limpiarModo()
        buscandoBilleteActual = true
        voiceEngine.speak("Pon el billete plano frente a la cámara. Tomando foto.")
        _captureRequests.tryEmit(Unit)
    }

    /** Vuelve a la cámara para tomar otra foto: quita la foto y la descripción anteriores. */
    /** "Agrega este billete": toma la foto de un billete y, si se lee con claridad, lo suma a la cuenta. */
    fun pedirSumarBillete() {
        if (_uiState.value.isProcessing || !hayEspacio()) return
        limpiarModo()
        buscandoBilleteActual = true
        sumandoBilleteActual = true
        voiceEngine.speak("Muéstrame un solo billete, plano frente a la cámara. Tomando foto.")
        _captureRequests.tryEmit(Unit)
    }

    /** "Qué moneda es": toma la foto de una moneda y dice de cuánto es con el modelo del teléfono. */
    fun pedirCapturaMoneda() {
        if (_uiState.value.isProcessing || !hayEspacio()) return
        limpiarModo()
        buscandoMonedaActual = true
        voiceEngine.speak("Pon una sola moneda sobre una superficie lisa, frente a la cámara. Tomando foto.")
        _captureRequests.tryEmit(Unit)
    }

    /** "Agrega esta moneda": toma la foto de una moneda y, si se reconoce con claridad, la suma a la cuenta. */
    fun pedirSumarMoneda() {
        if (_uiState.value.isProcessing || !hayEspacio()) return
        limpiarModo()
        buscandoMonedaActual = true
        sumandoMonedaActual = true
        voiceEngine.speak("Muéstrame una sola moneda, sola y frente a la cámara. Tomando foto.")
        _captureRequests.tryEmit(Unit)
    }

    /** "Cuánto llevo": dice lo que se ha sumado (billetes y monedas) y el total. */
    fun decirTotalBilletes() {
        voiceEngine.speak(fraseTotalDinero(), listenAfter = true)
    }

    /** "Borra los billetes": empieza la cuenta de billetes de cero (la de monedas no se toca). */
    fun reiniciarBilletes() {
        billetesContados.clear()
        voiceEngine.speak("Listo. Empecé la cuenta de billetes desde cero.", listenAfter = true)
    }

    /** "Borra las monedas": empieza la cuenta de monedas de cero (la de billetes no se toca). */
    fun reiniciarMonedas() {
        monedasContadas.clear()
        voiceEngine.speak("Listo. Empecé la cuenta de monedas desde cero.", listenAfter = true)
    }

    /** Billetes y monedas sumados. Si no hay monedas, queda igual que la cuenta de solo billetes. */
    private fun fraseTotalDinero(): String {
        if (monedasContadas.isEmpty()) return fraseTotalBilletes()
        val partesBilletes = billetesContados.entries.sortedByDescending { it.key.valor }.map { (d, n) ->
            if (n == 1) "un billete de ${d.nombreHablado}" else "$n billetes de ${d.nombreHablado}"
        }
        val partesMonedas = monedasContadas.entries.sortedByDescending { it.key.valor }.map { (d, n) ->
            if (n == 1) "una moneda de ${d.nombreHablado}" else "$n monedas de ${d.nombreHablado}"
        }
        val partes = partesBilletes + partesMonedas
        val total = billetesContados.entries.sumOf { it.key.valor * it.value } +
            monedasContadas.entries.sumOf { it.key.valor * it.value }
        val unidos = if (partes.size == 1) partes[0] else partes.dropLast(1).joinToString(", ") + " y " + partes.last()
        return "Llevas $unidos. Son $total pesos en total."
    }

    private fun fraseTotalBilletes(): String {
        if (billetesContados.isEmpty()) {
            return if (monedasContadas.isEmpty()) {
                "Todavía no has sumado ningún billete ni moneda. Di agrega este billete o agrega esta moneda."
            } else {
                "No has sumado billetes. " + fraseSoloMonedas()
            }
        }
        val partes = billetesContados.entries.sortedByDescending { it.key.valor }.map { (d, n) ->
            if (n == 1) "un billete de ${d.nombreHablado}" else "$n billetes de ${d.nombreHablado}"
        }
        val total = billetesContados.entries.sumOf { it.key.valor * it.value }
        val cuantos = billetesContados.values.sum()
        val unidos = if (partes.size == 1) partes[0] else partes.dropLast(1).joinToString(", ") + " y " + partes.last()
        return "Llevas $unidos. Son $cuantos ${if (cuantos == 1) "billete" else "billetes"}, $total pesos en total."
    }

    private fun fraseSoloMonedas(): String {
        val partes = monedasContadas.entries.sortedByDescending { it.key.valor }.map { (d, n) ->
            if (n == 1) "una moneda de ${d.nombreHablado}" else "$n monedas de ${d.nombreHablado}"
        }
        val total = monedasContadas.entries.sumOf { it.key.valor * it.value }
        val unidos = if (partes.size == 1) partes[0] else partes.dropLast(1).joinToString(", ") + " y " + partes.last()
        return "Llevas $unidos. Son $total pesos en total."
    }

    /** Suma el billete de la foto a la cuenta solo si es uno solo y se leyó con claridad; si no, dice por qué. */
    private fun sumarALaCuenta(resultado: ResultadoBillete?): String {
        val d = resultado?.denominacion
        if (resultado == null || d == null || resultado.hayVarios) {
            return "No sumé nada. Para llevar la cuenta, muéstrame un solo billete a la vez, plano y con buena luz."
        }
        billetesContados[d] = (billetesContados[d] ?: 0) + 1
        return "Anotado, un billete de ${d.nombreHablado}. " + fraseTotalDinero()
    }

    /** Suma la moneda de la foto a la cuenta solo si se reconoció con claridad; si no, dice por qué. */
    private fun sumarMonedaALaCuenta(resultado: ResultadoMoneda?): String {
        val d = resultado?.denominacion
        if (resultado == null || d == null || !resultado.esSegura) {
            return "No sumé nada. Para llevar la cuenta, muéstrame una sola moneda a la vez, sola y con buena luz."
        }
        monedasContadas[d] = (monedasContadas[d] ?: 0) + 1
        return "Anotada, una moneda de ${d.nombreHablado}. " + fraseTotalDinero()
    }

    fun tomarOtraFoto() {
        if (_uiState.value.isProcessing) return
        limpiarModo()
        _uiState.value = EnvironmentUiState()
        voiceEngine.speak("Listo. Apunta la cámara y di qué hay enfrente.")
    }

    /**
     * Manda la última foto a la IA del servidor (`/ver`): en modo billete si la foto era de un billete, y si no,
     * en modo escena (hasta 3 objetos con su posición). El análisis del teléfono no cambia; esto es una segunda
     * opinión que la persona pide con la voz, y solo funciona con internet y con la IA activada.
     */
    fun describirConIa() {
        if (_uiState.value.isProcessing) return
        val foto = ultimaFoto
        if (foto == null) {
            voiceEngine.speak("Primero toma una foto. Di qué hay enfrente.", listenAfter = true)
            return
        }
        // Las monedas siempre las decide el modelo especializado del teléfono. Se repite la frase
        // local antes de revisar internet o la IA del servidor: reconocer una moneda no depende de
        // la conexión, y la denominación nunca la pone la descripción.
        if (ultimaFueMoneda) {
            val frase = ultimaMonedaFrase
                ?: "No logré reconocer la moneda. Ponla sola sobre una superficie lisa, con buena luz, y pregunta otra vez."
            voiceEngine.speak(frase, listenAfter = true)
            return
        }
        if (!intentFallback.disponible) {
            voiceEngine.speak("No puedo darte más detalle ahora. Revisa la configuración.", listenAfter = true)
            return
        }
        if (!isOnline.value) {
            voiceEngine.speak("No hay conexión a internet. No puedo darte más detalle ahora.", listenAfter = true)
            return
        }
        val modo = if (ultimaFueBillete) ModoVision.BILLETE else ModoVision.ESCENA
        _uiState.value = _uiState.value.copy(isProcessing = true, errorMessage = null)
        viewModelScope.launch {
            val jpeg = withContext(Dispatchers.Default) { runCatching { FotoParaIa.desdeArchivo(foto!!) }.getOrNull() }
            if (jpeg == null) {
                _uiState.value = _uiState.value.copy(isProcessing = false)
                voiceEngine.speak("No pude abrir la última foto. Toma otra.", listenAfter = true)
                return@launch
            }
            val resultado = voiceEngine.speakWhile { aiApi.describe(jpeg, modo) }
            resultado
                .onSuccess { texto ->
                    _uiState.value = _uiState.value.copy(isProcessing = false, descripcion = texto)
                    voiceEngine.speak(texto, listenAfter = true)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(isProcessing = false)
                    cues.play(FeedbackCues.Cue.ERROR)
                    voiceEngine.speak(AiMessages.forFailure(error), listenAfter = true)
                }
        }
    }

    fun repetir() {
        val descripcion = _uiState.value.descripcion
        if (descripcion == null) {
            voiceEngine.speak("Todavía no he descrito nada. Di qué hay enfrente.", listenAfter = true)
        } else {
            voiceEngine.speak(descripcion)
        }
    }

    /** Botón "Leer texto" del selector de modo de la cámara (y la voz). */
    fun cambiarALectura() {
        cues.play(FeedbackCues.Cue.NAVIGATE)
        voiceEngine.speak("Cambiando al lector de texto.")
        _routes.tryEmit(CecapiDestinations.DOCUMENT_READER)
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
        limpiarModo()
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
        val buscarMoneda = buscandoMonedaActual
        val sumarMoneda = sumandoMonedaActual
        limpiarModo()
        // Las fotos generales usan la descripción local de forma automática e invisible. Billetes y
        // monedas tienen su propio camino y nunca pasan por el modelo de descripción.
        val usarIa = !buscarBillete && !buscarMoneda
        voiceEngine.speak(
            when {
                buscarBillete -> "Leyendo el billete."
                buscarMoneda -> "Reconociendo la moneda."
                else -> "Analizando el entorno."
            },
        )
        ultimaFoto = rutaImagen
        ultimaFueBillete = buscarBillete
        ultimaFueMoneda = buscarMoneda
        ultimaMonedaFrase = null
        viewModelScope.launch {
            repository.processCapturedPhoto(
                usuarioId = usuarioId,
                imageUri = imageUri,
                rutaImagen = rutaImagen,
                buscarBillete = buscarBillete,
                buscarMoneda = buscarMoneda,
                usarIa = usarIa,
            )
                .onSuccess { result ->
                    // Una foto general puede haber terminado en billete por la revisión automática.
                    // Para la segunda opinión se conserva ese modo y no se describe como escena.
                    ultimaFueBillete = buscarBillete || result.billetes != null
                    ultimaFueMoneda = buscarMoneda || result.monedas != null
                    ultimaMonedaFrase = if (result.monedas != null) result.descripcion else null
                    // Si era para llevar la cuenta, lo que se dice es lo anotado y el total, no la descripción suelta.
                    val dicho = when {
                        sumarBillete -> sumarALaCuenta(result.billetes)
                        sumarMoneda -> sumarMonedaALaCuenta(result.monedas)
                        else -> result.descripcion
                    }
                    _uiState.value = EnvironmentUiState(descripcion = dicho, photoPath = rutaImagen)
                    voiceEngine.speak(dicho)
                }
                .onFailure {
                    val message = "No lo logré describir. Intenta con otra foto."
                    _uiState.value = EnvironmentUiState(errorMessage = message)
                    cues.play(FeedbackCues.Cue.ERROR)
                    voiceEngine.speak(message, listenAfter = true)
                }
        }
    }
}
