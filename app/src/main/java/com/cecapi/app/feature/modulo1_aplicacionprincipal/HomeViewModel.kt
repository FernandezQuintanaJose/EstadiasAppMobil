package com.cecapi.app.feature.modulo1_aplicacionprincipal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.model.ModuloCecapi
import com.cecapi.app.core.navigation.CecapiDestinations
import com.cecapi.app.core.ui.MenuItem
import com.cecapi.app.core.util.ConnectivityObserver
import com.cecapi.app.core.util.PhoneStatusReader
import com.cecapi.app.core.util.VolumeControl
import com.cecapi.app.core.util.WikidataFact
import com.cecapi.app.core.util.WikidataLookup
import com.cecapi.app.core.util.WikipediaLookup
import com.cecapi.app.core.voice.VoiceMessages
import com.cecapi.app.core.voice.VoiceText
import com.cecapi.app.core.voice.AssistantPreferences
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.DeviceSettings
import com.cecapi.app.core.voice.IntentFallback
import com.cecapi.app.core.voice.LaunchRequests
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.notifications.NotificationReader
import com.cecapi.app.core.voice.VoiceMemory
import com.cecapi.app.core.voice.VoiceState
import com.cecapi.app.core.voice.WakeWordController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

sealed interface HomeNavEvent {
    data object GoToLogin : HomeNavEvent
    data object GoToRegister : HomeNavEvent
    data object GoToSettings : HomeNavEvent
    data object ExitApp : HomeNavEvent
    data class GoToModule(val route: String) : HomeNavEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val assistantPreferences: AssistantPreferences,
    private val wakeWordController: WakeWordController,
    private val phoneStatusReader: PhoneStatusReader,
    private val voiceMemory: VoiceMemory,
    private val volumeControl: VolumeControl,
    private val cues: FeedbackCues,
    private val launchRequests: LaunchRequests,
    private val notificationReader: NotificationReader,
    private val intentFallback: IntentFallback,
    private val deviceSettings: DeviceSettings,
    private val sessionRepository: SessionRepository,
    private val connectivityObserver: ConnectivityObserver,
    private val wikipediaLookup: WikipediaLookup,
    private val wikidataLookup: WikidataLookup,
) : ViewModel() {

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    val assistantName: StateFlow<String> = assistantPreferences.assistantName.stateIn(
        viewModelScope, SharingStarted.Eagerly, "",
    )

    private var holdingWakeWord = false

    /** El menú principal. Es igual para todos aquí; el dashboard lo filtra según lo que la cuenta puede usar. */
    val menu: List<MenuItem> = ModuleVoice.menu(ModuloCecapi.entries)

    // Este ViewModel sobrevive a su pantalla mientras Login/Register están abiertos encima de él,
    // así que debe ignorar el habla que pertenece a esas pantallas.
    private var screenActive = false

    val isOnline: StateFlow<Boolean> = connectivityObserver.observe().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), true,
    )

    private val _navEvents = MutableSharedFlow<HomeNavEvent>(extraBufferCapacity = 1)
    val navEvents: SharedFlow<HomeNavEvent> = _navEvents

    init {
        viewModelScope.launch {
            // Abierto desde el botón "Hablar" del widget: responde y escucha en lugar del saludo largo.
            if (launchRequests.consumeListen()) {
                voiceEngine.speak(voiceEngine.wakePrompt(), listenAfter = true)
                return@launch
            }
            val name = assistantPreferences.assistantName.first()
            val online = connectivityObserver.observe().first()
            val loggedOut = if (sessionRepository.consumeLogoutNotice()) "Sesión cerrada. " else ""
            val style = deviceSettings.addressStyle.first()
            val intro = loggedOut + if (name.isBlank()) {
                style.pick(
                    "Hola, soy tu asistente. Di hola o toca el micrófono para hablar conmigo. " +
                        "Si quieres, ponme un nombre: di por ejemplo, llámate Alice. ",
                    "Hola, soy su asistente. Diga hola o toque el micrófono para hablar conmigo. " +
                        "Si desea, póngame un nombre: diga por ejemplo, llámate Alice. ",
                )
            } else {
                style.pick(
                    "Hola, soy $name. Di hola o $name para hablar conmigo. ",
                    "Hola, soy $name. Diga hola o $name para hablar conmigo. ",
                )
            }
            // El estado de la conexión solo se menciona cuando hay un problema.
            val offlineNotice = if (online) "" else "$OFFLINE_NOTICE "
            voiceEngine.speak(
                intro + offlineNotice + style.pick("Di menú para saber qué puedo hacer.", "Diga menú para saber qué puedo hacer."),
            )
        }
        viewModelScope.launch {
            var wasOnline: Boolean? = null
            connectivityObserver.observe().collect { online ->
                if (wasOnline == true && !online && screenActive) { cues.play(FeedbackCues.Cue.WARNING); voiceEngine.speak(OFFLINE_NOTICE) }
                wasOnline = online
            }
        }
        viewModelScope.launch {
            voiceEngine.speechFor(CecapiDestinations.HOME).collect { speech ->
                if (screenActive) interpretCommand(speech.text.lowercase())
            }
        }
    }

    fun onModuleSelected(modulo: ModuloCecapi) {
        cues.play(FeedbackCues.Cue.NAVIGATE)
        voiceEngine.speak(modulo.voicePrompt)
        _navEvents.tryEmit(HomeNavEvent.GoToModule(modulo.route))
    }

    fun onAyudaSelected() {
        _navEvents.tryEmit(HomeNavEvent.GoToModule(CecapiDestinations.SOPORTE))
    }

    fun onSettingsSelected() {
        cues.play(FeedbackCues.Cue.NAVIGATE)
        voiceEngine.speak("Abriendo la configuración.")
        _navEvents.tryEmit(HomeNavEvent.GoToSettings)
    }

    fun onCrearCuentaSelected() {
        cues.play(FeedbackCues.Cue.NAVIGATE)
        voiceEngine.speak("Abriendo crear cuenta.")
        _navEvents.tryEmit(HomeNavEvent.GoToRegister)
    }

    /** Se tocó una tarjeta del menú principal (o se nombró en voz alta). */
    fun onMenuItemSelected(item: MenuItem) = onMenuKey(item.key)

    private fun onMenuKey(key: String) {
        when (key) {
            MenuItem.SETTINGS_KEY -> onSettingsSelected()
            MenuItem.CAMERA_KEY -> openRoute(CecapiDestinations.CAMERA_HUB, "Abriendo la cámara.")
            MenuItem.PERSONALIZATION_KEY -> openRoute(CecapiDestinations.PERSONALIZATION, "Abriendo la personalización.")
            MenuItem.CHATS_KEY -> openRoute(CecapiDestinations.CHATS, "Abriendo los chats.")
            else -> ModuloCecapi.fromStorageCode(key)?.let(::onModuleSelected)
        }
    }

    private fun openRoute(route: String, speech: String) {
        cues.play(FeedbackCues.Cue.NAVIGATE)
        voiceEngine.speak(speech)
        _navEvents.tryEmit(HomeNavEvent.GoToModule(route))
    }

    private fun openCameraMode(route: String) = openRoute(
        route,
        if (route == CecapiDestinations.ENVIRONMENT) "Abriendo el asistente del entorno." else "Abriendo la cámara para leer texto.",
    )

    fun setScreenActive(active: Boolean) {
        screenActive = active
    }

    /** Escuchar la palabra de activación necesita el permiso del micrófono, así que la pantalla lo activa solo una vez que lo tiene. */
    fun setWakeWordEnabled(enabled: Boolean) {
        if (enabled == holdingWakeWord) return
        holdingWakeWord = enabled
        if (enabled) wakeWordController.acquire() else wakeWordController.release()
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

    fun onSuggestionTapped(command: String) {
        interpretCommand(command.lowercase())
    }

    private var pendingExit = false
    private var pendingExitAskedAt = 0L

    /**
     * Responde a "¿Cierro la aplicación? Di sí o no." Solo durante un rato corto, y solo con una palabra
     * deliberada: cerrar la app le puede pasar a cualquiera, incluyendo un niño, así que una palabra que
     * también signifique otra cosa en una conversación casual ("va", "sale") no debe contar.
     */
    private fun handlePendingConfirm(text: String): Boolean {
        if (!pendingExit || System.currentTimeMillis() - pendingExitAskedAt > EXIT_CONFIRM_WINDOW_MS) {
            pendingExit = false
            return false
        }
        pendingExit = false
        return when {
            VoiceText.isNo(text) -> {
                voiceEngine.speak("De acuerdo, no cierro nada.")
                true
            }
            VoiceText.normalize(text).split(" ").any { it in EXIT_CONFIRM_WORDS } -> {
                exitApp()
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
        val newName = NAME_COMMAND.find(text)?.groupValues?.get(1)?.trim { !it.isLetterOrDigit() }
        val phoneStatus = phoneStatusReader.answer(text)
        // Cambiar el volumen es una acción, así que solo se intenta cuando nada de lo anterior ya reclamó la frase.
        val volume = if (newName == null && phoneStatus == null) volumeControl.handle(text) else null
        val notification = notificationReader.handle(text)
        val cameraRoute = ModuleVoice.directCameraRoute(text)
        val menuKey = ModuleVoice.matchKey(text, menu)
        when {
            newName != null -> saveAssistantName(newName)
            phoneStatus != null -> voiceEngine.speak(phoneStatus)
            volume != null -> voiceEngine.speak(volume)
            notification != null -> voiceEngine.speak(notification)
            SessionCommands.isExitApp(text) -> {
                pendingExit = true
                pendingExitAskedAt = System.currentTimeMillis()
                voiceEngine.speak("¿Cierro la aplicación? Di sí, cerrar, o no.", listenAfter = true)
            }
            VoiceText.normalize(text).let { "configuracion" in it || "ajustes" in it } -> onSettingsSelected()
            SessionCommands.isLogout(text) -> voiceEngine.speak("No tienes una sesión abierta.")
            CommandCatalog.isRequest(text) -> voiceEngine.speak(CommandCatalog.HOME)
            ModuleVoice.isListRequest(text) -> voiceEngine.speak(ModuleVoice.spokenList(menu))
            cameraRoute != null -> openCameraMode(cameraRoute)
            menuKey != null -> onMenuKey(menuKey)
            "iniciar sesión" in text || "iniciar sesion" in text -> {
                voiceEngine.speak("Abriendo el inicio de sesión.")
                _navEvents.tryEmit(HomeNavEvent.GoToLogin)
            }
            "ayuda" in text -> voiceEngine.speak(
                "Soy un asistente para personas invidentes. Puedo abrir la cámara para leer texto o describir lo que hay enfrente, " +
                    "y decirte cómo está tu teléfono. Di menú, estado del teléfono, " +
                    "batería, wifi, hora, fecha, o iniciar sesión. Si quieres que repita algo, " +
                    "di repite la solicitud anterior. Di lista de comandos para escucharlos todos.",
            )
            else -> notUnderstood(text, allowFallback)
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

    private fun sayNotUnderstood() {
        cues.play(FeedbackCues.Cue.NOT_UNDERSTOOD)
        voiceEngine.speak("No entendí ese comando. Di lista de comandos para escuchar lo que puedo hacer.")
    }

    /** Se despide, espera a que termine, y luego cierra la app para que el teléfono quede libre de nuevo. */
    private fun exitApp() {
        voiceEngine.speak("Cerrando la aplicación. Hasta luego.")
        viewModelScope.launch {
            delay(300)
            withTimeoutOrNull(6_000) { voiceEngine.state.first { it !is VoiceState.Speaking } }
            _navEvents.emit(HomeNavEvent.ExitApp)
        }
    }

    private fun saveAssistantName(name: String) {
        if (name.isBlank() || name.length > MAX_NAME_LENGTH || name.equals(DEFAULT_WAKE_WORD, ignoreCase = true)) {
            voiceEngine.speak("No pude usar ese nombre. Di por ejemplo: llámate Alice.")
            return
        }
        val display = name.replaceFirstChar { it.uppercase() }
        viewModelScope.launch {
            assistantPreferences.setAssistantName(display)
            voiceEngine.speak("Listo, ahora me llamo $display. Di $display para hablar conmigo.")
        }
    }

    override fun onCleared() {
        setWakeWordEnabled(false)
        voiceEngine.stopListening()
        super.onCleared()
    }

    private companion object {
        const val DEFAULT_WAKE_WORD = "hola"
        const val MAX_NAME_LENGTH = 30
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

        // "llámate Luna", "quisiera llamarte Luna", "quiero que te llames Luna", "te llamas Luna",
        // "te voy a llamar Luna", "tu nombre será Luna", "te pongo Luna", "ponte de nombre Luna"
        val NAME_COMMAND = Regex(
            "(?:l+[aá]ma(?:rte|te)|l+[aá]mar|te llam(?:as|es)|te llamar[eé]|" +
                "tu nombre (?:es|ser[aá]|va a ser|sea)|ponerte(?: de)? nombre|ponte(?: de)? nombre|" +
                "te pongo|te pondr[eé]|te voy a poner)" +
                "\\s+(?:como\\s+|de\\s+|el nombre\\s+(?:de\\s+)?)?(.+)",
        )
    }
}
