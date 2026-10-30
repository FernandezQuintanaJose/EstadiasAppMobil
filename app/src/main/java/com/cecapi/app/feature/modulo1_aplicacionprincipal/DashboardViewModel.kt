package com.cecapi.app.feature.modulo1_aplicacionprincipal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.model.ModuloCecapi
import com.cecapi.app.core.ui.MenuItem
import com.cecapi.app.core.navigation.CecapiDestinations
import com.cecapi.app.core.util.ConnectivityObserver
import com.cecapi.app.core.util.PhoneStatusReader
import com.cecapi.app.core.util.VolumeControl
import com.cecapi.app.core.util.WikidataFact
import com.cecapi.app.core.util.WikidataLookup
import com.cecapi.app.core.util.WikipediaLookup
import com.cecapi.app.core.voice.AssistantPreferences
import com.cecapi.app.core.voice.DeviceSettings
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.IntentFallback
import com.cecapi.app.core.voice.VoiceMessages
import com.cecapi.app.notifications.NotificationReader
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceMemory
import com.cecapi.app.core.voice.VoiceState
import com.cecapi.app.core.voice.VoiceText
import com.cecapi.app.core.voice.WakeWordController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val sessionRepository: SessionRepository,
    private val permisosModuloDao: PermisosModuloDao,
    private val phoneStatusReader: PhoneStatusReader,
    private val wakeWordController: WakeWordController,
    private val voiceMemory: VoiceMemory,
    private val volumeControl: VolumeControl,
    private val cues: FeedbackCues,
    private val notificationReader: NotificationReader,
    private val intentFallback: IntentFallback,
    private val deviceSettings: DeviceSettings,
    private val connectivityObserver: ConnectivityObserver,
    private val wikipediaLookup: WikipediaLookup,
    private val wikidataLookup: WikidataLookup,
    assistantPreferences: AssistantPreferences,
) : ViewModel() {

    private var holdingWakeWord = false

    init {
        viewModelScope.launch {
            sessionRepository.consumirAvisoPendiente()?.let { voiceEngine.speak(it) }
        }
    }

    val currentUser: StateFlow<UsuarioEntity?> = sessionRepository.currentUser

    val assistantName: StateFlow<String> = assistantPreferences.assistantName.stateIn(
        viewModelScope, SharingStarted.Eagerly, "",
    )

    val isOnline: StateFlow<Boolean> = connectivityObserver.observe().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), true,
    )

    private val _exitEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val exitEvents: SharedFlow<Unit> = _exitEvents

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    val enabledModules: StateFlow<List<ModuloCecapi>> = sessionRepository.currentUser
        .filterNotNull()
        .flatMapLatest { usuario -> permisosModuloDao.observeByUser(usuario.id) }
        .map { permisos ->
            permisos.filter { it.habilitado }
                .mapNotNull { ModuloCecapi.fromStorageCode(it.moduloCodigo) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModuloCecapi.entries.toList())

    /** El menú principal para esta cuenta: Cámara, Documentos, Personalización y Configuración. */
    val menu: StateFlow<List<MenuItem>> = enabledModules
        .map { ModuleVoice.menu(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModuleVoice.menu(ModuloCecapi.entries))

    private val _navEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navEvents: SharedFlow<String> = _navEvents

    // Este ViewModel sobrevive a su pantalla mientras un módulo está abierto encima de él, así que
    // debe ignorar el habla que pertenece a ese módulo.
    private var screenActive = false

    init {
        viewModelScope.launch {
            // El estado de la conexión solo se menciona cuando hay un problema.
            val online = connectivityObserver.observe().first()
            voiceEngine.speak(welcomeMessage() + if (online) "" else " $OFFLINE_NOTICE")
        }
        viewModelScope.launch {
            var wasOnline: Boolean? = null
            connectivityObserver.observe().collect { online ->
                if (wasOnline == true && !online && screenActive) { cues.play(FeedbackCues.Cue.WARNING); voiceEngine.speak(OFFLINE_NOTICE) }
                wasOnline = online
            }
        }
        viewModelScope.launch {
            voiceEngine.speechFor(CecapiDestinations.DASHBOARD).collect { speech ->
                if (screenActive) interpretCommand(speech.text.lowercase())
            }
        }
    }

    private enum class PendingConfirm { EXIT, LOGOUT }

    private var pendingConfirm: PendingConfirm? = null
    private var pendingConfirmAskedAt = 0L

    /**
     * Responde a la pregunta "¿Cierro...? Di sí o no." Solo durante un rato corto, y solo con una palabra
     * deliberada: esto le puede pasar a cualquiera, incluyendo un niño, así que una palabra que también
     * signifique otra cosa en una conversación casual ("va", "sale") no debe contar.
     */
    private fun handlePendingConfirm(text: String): Boolean {
        val action = pendingConfirm ?: return false
        pendingConfirm = null
        if (System.currentTimeMillis() - pendingConfirmAskedAt > EXIT_CONFIRM_WINDOW_MS) return false
        return when {
            VoiceText.isNo(text) -> {
                voiceEngine.speak("De acuerdo, no cierro nada.")
                true
            }
            VoiceText.normalize(text).split(" ").any { it in EXIT_CONFIRM_WORDS } -> {
                if (action == PendingConfirm.EXIT) exitApp() else onLogout()
                true
            }
            else -> false
        }
    }

    private fun interpretCommand(text: String) {
        if (handlePendingConfirm(text)) return
        when (voiceMemory.repeatKind(text)) {
            VoiceMemory.Repeat.RESPONSE ->
                voiceEngine.speak(voiceEngine.lastSpoken ?: "Todavía no he dicho nada.")
            VoiceMemory.Repeat.REQUEST -> {
                val previous = voiceMemory.lastRequest
                if (previous == null) voiceEngine.speak("Todavía no me has pedido nada.") else runCommand(previous)
            }
            null -> {
                voiceMemory.remember(text)
                runCommand(text)
            }
        }
    }

    private fun runCommand(text: String, allowFallback: Boolean = true) {
        // "dime más": profundiza en lo último que respondió la IA, o lee el resto de un resumen de Wikipedia.
        intentFallback.deepQuestionFor(text)?.let { question ->
            askAi(question, deep = true)
            return
        }
        pendingWikiRest?.let { rest ->
            if (intentFallback.wantsMore(text)) {
                pendingWikiRest = null
                voiceEngine.speak(rest, listenAfter = true)
                return
            }
        }
        phoneStatusReader.answer(text)?.let { status ->
            voiceEngine.speak(status)
            return
        }
        volumeControl.handle(text)?.let { message ->
            voiceEngine.speak(message)
            return
        }
        if (CommandCatalog.isRequest(text)) {
            voiceEngine.speak(CommandCatalog.DASHBOARD)
            return
        }
        notificationReader.handle(text)?.let { message ->
            voiceEngine.speak(message)
            return
        }
        // Salir es difícil de deshacer para alguien que no puede ver la pantalla, y una palabra mal
        // escuchada no debe hacerlo.
        if (SessionCommands.isExitApp(text)) {
            pendingConfirm = PendingConfirm.EXIT
            pendingConfirmAskedAt = System.currentTimeMillis()
            voiceEngine.speak("¿Cierro la aplicación? Di sí, cerrar, o no.", listenAfter = true)
            return
        }
        if (SessionCommands.isLogout(text)) {
            pendingConfirm = PendingConfirm.LOGOUT
            pendingConfirmAskedAt = System.currentTimeMillis()
            voiceEngine.speak("¿Cierro tu sesión? Di sí, cerrar, o no.", listenAfter = true)
            return
        }
        if (puedeGestionar() && VoiceText.hasAny(VoiceText.normalize(text), "gestion", "administracion", "panel")) {
            onGestionSelected()
            return
        }
        if (esAlumnoOUsuario() &&
            VoiceText.hasAny(VoiceText.normalize(text), "ayuda y soporte", "soporte", "mi educador", "reportar un problema")
        ) {
            onSoporteSelected()
            return
        }
        if (VoiceText.hasAny(VoiceText.normalize(text), "clasificacion", "tabla de clasificacion", "ranking")) {
            onRankingSelected()
            return
        }
        if (ModuleVoice.isListRequest(text)) {
            voiceEngine.speak(ModuleVoice.spokenList(menu.value))
            return
        }
        ModuleVoice.directCameraRoute(text)?.let { route ->
            openRoute(
                route,
                if (route == CecapiDestinations.ENVIRONMENT) "Abriendo el asistente del entorno." else "Abriendo la cámara para leer texto.",
            )
            return
        }
        val key = ModuleVoice.matchKey(text, menu.value)
        if (key != null) {
            onMenuKey(key)
        } else {
            notUnderstood(text, allowFallback)
        }
    }

    /**
     * No es uno de nuestros comandos. Si hay un resolver registrado (el "traductor de frases" del equipo de
     * IA) y hay internet, se le da una oportunidad de convertir la frase en un comando que conocemos; si no,
     * se dice que no se entendió.
     */
    private fun notUnderstood(text: String, allowFallback: Boolean) {
        val fact = wikidataLookup.matchFact(text)
        val wikiQuery = wikiQuery(text)
        when {
            // Un dato específico y estructurado ("cuándo nació", "capital de", "población de"...) va primero:
            // Wikidata lo responde con más precisión que buscar el número dentro del texto de Wikipedia.
            allowFallback && fact != null && isOnline.value -> askWikidataFact(fact)
            allowFallback && intentFallback.resolver != null && isOnline.value -> askAi(text, deep = false)
            // La IA está apagada (sus proveedores no permiten menores de edad) o aún no hay conexión: Wikipedia
            // sigue ayudando con un simple "qué es / quién fue / busca..." cuando hay internet, sin necesitar cuenta.
            allowFallback && wikiQuery != null && isOnline.value -> askWikipedia(wikiQuery)
            // Último recurso antes de rendirse: la frase no dijo "qué es" ni "busca", pero podría ser un tema
            // por sí mismo ("Miguel Hidalgo", "pregúntale por el día de la independencia"...). Es seguro probar
            // con Wikipedia a ciegas: es contenido de referencia curado, no una respuesta abierta de cualquier lado.
            allowFallback && isOnline.value && text.trim().length >= 3 -> askWikipedia(text.trim())
            else -> sayNotUnderstood()
        }
    }

    /** Un dato estructurado de Wikidata; recurre a la IA o a una búsqueda simple en Wikipedia si no tiene ninguno. */
    private fun askWikidataFact(fact: WikidataFact) {
        voiceEngine.speak("Buscando el dato.")
        viewModelScope.launch {
            val answer = runCatching { wikidataLookup.answer(fact) }.getOrNull()
            when {
                answer != null -> voiceEngine.speak(answer, listenAfter = true)
                intentFallback.resolver != null && isOnline.value -> askAi(fact.subject, deep = false)
                else -> askWikipedia(fact.subject)
            }
        }
    }

    /** La parte después de "qué es", "quién fue", "busca"... en las palabras que la persona realmente dijo (con acentos). */
    private fun wikiQuery(text: String): String? {
        val match = WIKI_TRIGGER.find(VoiceText.fold(text)) ?: return null
        return text.substring(match.groups[1]!!.range.first).trim().takeIf { it.length >= 2 }
    }

    /** Qué decir después del resumen actual de Wikipedia si la persona dice "dime más". */
    private var pendingWikiRest: String? = null

    private fun askWikipedia(query: String) {
        voiceEngine.speak("Buscando en Wikipedia.")
        viewModelScope.launch {
            val result = runCatching { wikipediaLookup.search(query) }.getOrNull()
            if (result == null) {
                voiceEngine.speak("No encontré nada en Wikipedia sobre eso. Di lista de comandos para escuchar lo que puedo hacer.")
            } else {
                pendingWikiRest = result.rest
                voiceEngine.speak(
                    result.short + if (result.rest != null) " Si quieres saber más, di dime más." else "",
                    listenAfter = true,
                )
            }
        }
    }

    /** Consulta al backend de IA. Responde con un comando para ejecutar, o con una respuesta corta para leer en voz alta. */
    private fun askAi(question: String, deep: Boolean) {
        val resolver = intentFallback.resolver ?: return sayNotUnderstood()
        viewModelScope.launch {
            val reply = runCatching { voiceEngine.speakWhile { resolver(question, deep) } }.getOrNull()
            val command = reply?.command
            val answer = reply?.answer
            when {
                command != null -> runCommand(command, allowFallback = false)
                answer != null -> {
                    intentFallback.lastQuestion = question
                    voiceEngine.speak(answer + if (reply.hasMore) " Si quieres saber más, di dime más." else "")
                }
                // La IA no pudo responder (servidor inalcanzable, sin respuesta...): una pregunta de tipo
                // "qué es / quién fue / busca" todavía podría funcionar a través de Wikipedia en lugar de
                // rendirse en ese momento.
                else -> askWikipedia(wikiQuery(question) ?: question.trim())
            }
        }
    }

    // Nunca quedarse en silencio: el usuario no puede ver si la frase fue entendida.
    private fun sayNotUnderstood() {
        cues.play(FeedbackCues.Cue.NOT_UNDERSTOOD)
        voiceEngine.speak("No entendí ese comando. Di lista de comandos para escuchar lo que puedo hacer.")
    }

    fun setScreenActive(active: Boolean) {
        screenActive = active
    }

    /** "Hola" (o el nombre del asistente) abre el micrófono; necesita el permiso del micrófono, que la pantalla verifica. */
    fun setWakeWordEnabled(enabled: Boolean) {
        if (enabled == holdingWakeWord) return
        holdingWakeWord = enabled
        if (enabled) wakeWordController.acquire() else wakeWordController.release()
    }

    override fun onCleared() {
        setWakeWordEnabled(false)
        super.onCleared()
    }

    /**
     * Bienvenida neutral para todos: nombre, rol (y de dónde vienen, si se sabe), y qué es distinto
     * dentro. El texto por rol se puede personalizar más adelante sin cambiar esta estructura.
     */
    private suspend fun welcomeMessage(): String {
        val style = deviceSettings.addressStyle.first()
        val usuario = sessionRepository.currentUser.value
        // El nombre que la persona eligió para que la llamen tiene prioridad sobre el de la cuenta.
        val nombre = deviceSettings.preferredName.first().ifBlank { usuario?.nombreCompleto.orEmpty() }
        val rol = usuario?.rol ?: RolUsuario.USUARIO.codigo
        val origen = usuario?.origen.orEmpty()
        val identidad = buildString {
            append(style.pick("Tu rol es $rol", "Su rol es $rol"))
            if (origen.isNotBlank() && rol != RolUsuario.ADMINISTRADOR.codigo) append(", de $origen")
            append(".")
        }
        return "Bienvenido $nombre. $identidad " +
            style.pick("Aquí tienes tu menú. Di menú para escucharlo.", "Aquí tiene su menú. Diga menú para escucharlo.")
    }

    /** Dos toques rápidos en el micrófono silencian al asistente, para alguien que usa el tacto con las manos en lugar de la voz. */
    fun onMicDoubleTap() = voiceEngine.mute()

    fun onMicTapped() {
        voiceEngine.startListening()
    }

    /** El usuario rechazó el permiso del micrófono: dice por qué no pasará nada en lugar de quedarse en silencio. */
    fun onMicPermissionDenied() {
        voiceEngine.speak(VoiceMessages.MIC_DENIED)
    }

    /** Es lo mismo que decir [command] en voz alta; lo usan los chips de sugerencias en pantalla. */
    fun onSuggestionTapped(command: String) {
        interpretCommand(command.lowercase())
    }

    fun onModuleSelected(modulo: ModuloCecapi) {
        cues.play(FeedbackCues.Cue.NAVIGATE)
        voiceEngine.speak(modulo.voicePrompt)
        _navEvents.tryEmit(modulo.route)
    }

    fun onSettingsSelected() {
        cues.play(FeedbackCues.Cue.NAVIGATE)
        voiceEngine.speak("Abriendo la configuración.")
        _navEvents.tryEmit(CecapiDestinations.SETTINGS)
    }

    /** Solo administrador, directivo y educador llegan a ver el botón que llama esto. */
    fun onGestionSelected() {
        cues.play(FeedbackCues.Cue.NAVIGATE)
        voiceEngine.speak("Abriendo gestión.")
        _navEvents.tryEmit(CecapiDestinations.GESTION)
    }

    private fun puedeGestionar(): Boolean {
        val rol = currentUser.value?.rol?.let(RolUsuario::fromCodigo) ?: return false
        return rol == RolUsuario.ADMINISTRADOR || rol == RolUsuario.DIRECTIVO || rol == RolUsuario.EDUCADOR
    }

    fun onSoporteSelected() {
        cues.play(FeedbackCues.Cue.NAVIGATE)
        voiceEngine.speak("Abriendo ayuda.")
        _navEvents.tryEmit(CecapiDestinations.SOPORTE)
    }

    private fun esAlumnoOUsuario(): Boolean {
        val rol = currentUser.value?.rol?.let(RolUsuario::fromCodigo) ?: return false
        return rol == RolUsuario.ALUMNO || rol == RolUsuario.USUARIO
    }

    /** Abierto para cualquiera con sesión iniciada, cualquier rol — a diferencia de Gestión y Soporte, que dependen del rol. */
    fun onRankingSelected() {
        cues.play(FeedbackCues.Cue.NAVIGATE)
        voiceEngine.speak("Abriendo clasificación.")
        _navEvents.tryEmit(CecapiDestinations.RANKING)
    }

    /** Abierto para cualquiera con sesión iniciada, cualquier rol. */
    fun onEditarCuentaSelected() {
        cues.play(FeedbackCues.Cue.NAVIGATE)
        voiceEngine.speak("Abriendo editar mi cuenta.")
        _navEvents.tryEmit(CecapiDestinations.EDIT_ACCOUNT)
    }

    /** Se tocó una tarjeta del menú principal (o se nombró en voz alta). */
    fun onMenuItemSelected(item: MenuItem) = onMenuKey(item.key)

    private fun onMenuKey(key: String) {
        when (key) {
            MenuItem.SETTINGS_KEY -> onSettingsSelected()
            MenuItem.CAMERA_KEY -> openRoute(CecapiDestinations.DOCUMENT_READER, "Abriendo la cámara. Para saber qué hay enfrente, toca el botón de arriba o dilo.")
            MenuItem.PERSONALIZATION_KEY -> openRoute(CecapiDestinations.PERSONALIZATION, "Abriendo la personalización.")
            MenuItem.CHATS_KEY -> openRoute(CecapiDestinations.CHATS, "Abriendo los chats.")
            else -> ModuloCecapi.fromStorageCode(key)?.let(::onModuleSelected)
        }
    }

    private fun openRoute(route: String, speech: String) {
        cues.play(FeedbackCues.Cue.NAVIGATE)
        voiceEngine.speak(speech)
        _navEvents.tryEmit(route)
    }

    /** Sin voz aquí: la pantalla de inicio se abre a continuación y dice "Sesión cerrada" por sí misma. */
    fun onLogout() {
        cues.play(FeedbackCues.Cue.NAVIGATE)
        sessionRepository.logout()
    }

    /** Se despide, espera a que termine, y luego cierra la app para que el teléfono quede libre de nuevo. */
    private fun exitApp() {
        voiceEngine.speak("Cerrando la aplicación. Hasta luego.")
        viewModelScope.launch {
            delay(300)
            withTimeoutOrNull(6_000) { voiceEngine.state.first { it !is VoiceState.Speaking } }
            _exitEvents.emit(Unit)
        }
    }

    private companion object {
        const val OFFLINE_NOTICE =
            "Ahora no tienes conexión a internet, así que mis respuestas pueden ser menos precisas."

        const val EXIT_CONFIRM_WINDOW_MS = 30_000L
        val EXIT_CONFIRM_WORDS = setOf("si", "cerrar", "cierra", "confirmo", "hazlo", "correcto")

        // Se compara contra VoiceText.fold(text) — sin acentos, con la misma longitud que el original — para que
        // el rango del grupo capturado coincida con las palabras que la persona realmente dijo, con acentos y todo.
        // Las preguntas clave en español: qué, quién, cómo, cuándo, dónde, por qué, cuál, cuánto.
        val WIKI_TRIGGER = Regex(
            "(?:busca(?:r)? en wikipedia|busca(?:r)?|informacion sobre|dime sobre|wikipedia|" +
                "que es|que son|que fue|que fueron|que significa|" +
                "quien es|quien fue|quien son|quien era|" +
                "como es|como funciona|como se hace|como surgio|" +
                "cuando fue|cuando es|cuando ocurrio|cuando paso|cuando nacio|cuando murio|" +
                "donde esta|donde queda|donde se encuentra|donde nacio|" +
                "por que|porque|" +
                "cual es|cuales son|" +
                "cuanto es|cuanto vale|cuanto mide|cuanto pesa|cuanto cuesta)\\s+(.+)",
        )
    }
}
