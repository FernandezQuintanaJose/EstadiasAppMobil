package com.cecapi.app.feature.modulo1_aplicacionprincipal

import com.cecapi.app.core.navigation.CecapiDestinations
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.util.PasswordHasher
import com.cecapi.app.core.util.StorageReport
import com.cecapi.app.core.util.StorageUsage
import com.cecapi.app.core.util.VolumeControl
import com.cecapi.app.core.voice.DeviceSettings
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceState
import com.cecapi.app.core.voice.VoiceText
import com.cecapi.app.notifications.NotificationAccess
import com.cecapi.app.service.BackgroundListening
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Configuración: el lado técnico (volumen, acceso a notificaciones, escuchar fuera de la aplicación, cuenta).
 * Cómo suena el asistente y cómo llama a la persona vive en Personalización.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val deviceSettings: DeviceSettings,
    private val volumeControl: VolumeControl,
    private val cues: FeedbackCues,
    private val notificationAccess: NotificationAccess,
    private val backgroundListening: BackgroundListening,
    private val sessionRepository: SessionRepository,
    private val storageReport: StorageReport,
) : ViewModel() {

    val simpleMode: StateFlow<Boolean> = deviceSettings.simpleMode.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val announceNotifications: StateFlow<Boolean> = deviceSettings.announceNotifications.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val listenOutsideApp: StateFlow<Boolean> = deviceSettings.backgroundListening.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val aiEnabled: StateFlow<Boolean> = deviceSettings.aiEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val holaEnabled: StateFlow<Boolean> = deviceSettings.holaEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** El usuario con sesión iniciada, o null cuando Configuración se abrió desde la pantalla de inicio. */
    val currentUser: StateFlow<UsuarioEntity?> = sessionRepository.currentUser

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    private val _loggedOut = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val loggedOut: SharedFlow<Unit> = _loggedOut

    private val _back = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val back: SharedFlow<Unit> = _back

    // Activar escuchar fuera de la aplicación necesita un permiso que la pantalla debe pedir, así que la pantalla hace el cambio.
    private val _listenOutsideRequests = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val listenOutsideRequests: SharedFlow<Boolean> = _listenOutsideRequests

    private val _storage = MutableStateFlow<StorageUsage?>(null)
    val storage: StateFlow<StorageUsage?> = _storage.asStateFlow()

    /** True mientras se le preguntó a la persona si borrar las fotos viejas y todavía no ha respondido. */
    private val _pendingClean = MutableStateFlow(false)

    /** True mientras se le pidió a la persona decir su contraseña para confirmar que borra su cuenta. */
    private val _awaitingDeletePassword = MutableStateFlow(false)
    val awaitingDeletePassword: StateFlow<Boolean> = _awaitingDeletePassword.asStateFlow()

    /** True mientras, ya confirmada la contraseña, se le preguntó a la persona el "¿borro...?" final, solo por un tiempo corto. */
    private val _pendingDeleteAccount = MutableStateFlow(false)
    val pendingDeleteAccount: StateFlow<Boolean> = _pendingDeleteAccount.asStateFlow()
    private var deleteAskedAt = 0L
    val pendingClean: StateFlow<Boolean> = _pendingClean.asStateFlow()


    init {
        refreshStorage()
        viewModelScope.launch {
            voiceEngine.speechFor(CecapiDestinations.SETTINGS).collect { speech -> onSpeech(speech.text) }
        }
        voiceEngine.speak(
            "Configuración. Aquí están el volumen, las notificaciones, escuchar fuera de la aplicación y tu cuenta. " +
                "Mantén presionado cualquier control para que te explique para qué sirve. " +
                CommandCatalog.hint("configuración"),
            listenAfter = true,
        )
    }

    /**
     * Los comandos que esta pantalla responde. El volumen y cualquier cosa que nombre las notificaciones son
     * comandos globales (nunca llegan aquí), por eso el interruptor de notificaciones se llama "avisos".
     */
    private fun onSpeech(spoken: String) {
        val text = VoiceText.normalize(spoken)
        fun has(vararg words: String) = VoiceText.hasAny(text, *words)
        val enable = when {
            listOf("desactiv*", "apaga", "quita", "sin", "no quiero").let { phrases -> VoiceText.hasAny(text, phrases) } -> false
            listOf("activ*", "enciende", "prende", "pon", "quiero").let { phrases -> VoiceText.hasAny(text, phrases) } -> true
            else -> null
        }
        // "si" y "no" deben ser palabras completas: "siguiente" contiene "si".
        val spokenWords = text.split(" ")
        fun hasWord(vararg words: String) = words.any { it in spokenWords }
        val cleaning = _pendingClean.value
        val expired = System.currentTimeMillis() - deleteAskedAt >= CONFIRM_WINDOW_MS
        val awaitingPassword = _awaitingDeletePassword.value && !expired
        val deletingAccount = _pendingDeleteAccount.value && !expired
        // El paso de la contraseña abre rawInput para que una contraseña hablada nunca sea absorbida por otro comando; si la
        // persona se quedó callada en vez de responder, regresa la pantalla a lo normal en vez de dejarla atascada.
        if (_awaitingDeletePassword.value && expired) {
            _awaitingDeletePassword.value = false
            voiceEngine.rawInput = false
        }
        when {
            // El aviso del micrófono espera "acepto" o "no acepto" antes que cualquier otro comando.
            _pendingListenConsent.value -> when {
                hasWord("no", "nunca", "cancela", "cancelar", "rechazo") -> declineListenConsent()
                hasWord("acepto", "si", "claro", "dale", "adelante", "ok", "okey") || has("de acuerdo") -> acceptListenConsent()
                else -> voiceEngine.speak("Di acepto para activarlo, o no acepto.", listenAfter = true)
            }
            // Antes de tratar lo dicho como el intento de contraseña: si la persona quiere salir de ahí
            // ("regresar", "salir"...), eso debe cancelar el borrado, no fallar como si fuera una contraseña mala.
            awaitingPassword && has("atras", "volver", "vuelve", "regresa", "regresar", "salir", "cancelar", "cancela", "menu", "inicio") ->
                cancelDeleteAccount()
            // Borrar la cuenta pide, además del "sí", la contraseña: mientras se espera, cualquier otra frase es el
            // intento, no un comando (igual que el usuario y la contraseña al iniciar sesión).
            awaitingPassword -> checkDeletePassword(spoken)
            CommandCatalog.isRequest(spoken) -> voiceEngine.speak(CommandCatalog.SETTINGS, listenAfter = true)
            deletingAccount && !VoiceText.isNo(spoken) && spokenWords.any { it in DELETE_CONFIRM_WORDS } -> confirmDeleteAccount()
            deletingAccount && VoiceText.isNo(spoken) -> cancelDeleteAccount()
            has("borra mi cuenta", "borrar mi cuenta", "elimina mi cuenta", "eliminar mi cuenta", "borra mis datos", "borrar mis datos", "elimina mis datos", "eliminar mis datos", "darme de baja") -> askDeleteAccount()
            has("inteligencia artificial", "la ia") && enable != null -> onAiEnabledChanged(enable)
            cleaning && hasWord("si", "claro", "dale", "ok", "okey", "supuesto", "afirmativo", "correcto", "seguro", "hazlo", "confirmo", "borralas", "adelante") -> confirmClean()
            cleaning && hasWord("no", "nunca", "negativo", "olvidalo", "dejalo", "cancela", "cancelar") -> cancelClean()
            has("cache", "memoria temporal", "archivos temporales", "temporales") &&
                has("limpia", "limpiar", "borra", "borrar", "vacia", "vaciar", "libera", "liberar", "elimina") -> clearCache()
            has("cuanto espacio", "espacio libre", "almacenamiento", "cuanto ocupa") -> speakStorage()
            has("libera espacio", "liberar espacio", "libera memoria", "limpia el espacio", "limpiar espacio", "borra las fotos", "borrar las fotos") ->
                askClean()
            has("cerrar sesion", "cierra sesion", "cierra mi sesion", "salir de la cuenta") ->
                if (currentUser.value != null) onLogout() else voiceEngine.speak("No hay una sesión iniciada.", listenAfter = true)
            has("fuera de la app", "fuera de la aplicacion", "segundo plano") && enable != null ->
                _listenOutsideRequests.tryEmit(enable)
            has("modo simple") && enable != null -> onSimpleModeChanged(enable)
            has("aviso") && enable != null -> onAnnounceNotificationsChanged(enable)
            has("atras", "volver", "vuelve", "regresa", "regresar", "salir", "menu", "inicio", "pantalla anterior") -> _back.tryEmit(Unit)
            else -> {
                cues.play(FeedbackCues.Cue.NOT_UNDERSTOOD)
                voiceEngine.speak("No entendí. " + CommandCatalog.hint("configuración"), listenAfter = true)
            }
        }
    }

    /** Llamado desde el botón del encabezado: lee en voz alta los comandos de esta pantalla. */
    fun onCommandsRequested() {
        voiceEngine.speak(CommandCatalog.SETTINGS, listenAfter = true)
    }

    fun onMicTapped() = voiceEngine.startListening()

    /** Dos toques rápidos en el micrófono silencian al asistente, para alguien que usa el tacto en vez de la voz. */
    fun onMicDoubleTap() = voiceEngine.mute()

    fun refreshStorage() {
        viewModelScope.launch { _storage.value = storageReport.read() }
    }

    fun speakStorage() {
        viewModelScope.launch {
            val usage = storageReport.read()
            _storage.value = usage
            voiceEngine.speak(storageReport.describe(usage), listenAfter = true)
        }
    }

    /** Ofrece borrar las fotos de más de un mes. No se borra nada hasta que la persona diga que sí. */
    fun askClean() {
        viewModelScope.launch {
            val usage = storageReport.read()
            _storage.value = usage
            if (usage.oldPhotoCount == 0) {
                voiceEngine.speak(
                    "No hay fotos de más de ${StorageReport.OLD_PHOTO_DAYS} días que borrar.",
                    listenAfter = true,
                )
            } else {
                _pendingClean.value = true
                voiceEngine.speak(
                    "Puedo borrar ${usage.oldPhotoCount} fotos de más de ${StorageReport.OLD_PHOTO_DAYS} días y liberar " +
                        "${storageReport.format(usage.oldPhotoBytes)}. Solo se borran las fotos; el texto que leí sigue guardado. " +
                        "¿Las borro? Di sí o no.",
                    listenAfter = true,
                )
            }
        }
    }

    fun confirmClean() {
        _pendingClean.value = false
        viewModelScope.launch {
            val (count, bytes) = storageReport.deleteOldPhotos()
            _storage.value = storageReport.read()
            cues.play(FeedbackCues.Cue.SUCCESS)
            voiceEngine.speak("Listo, borré $count fotos y liberé ${storageReport.format(bytes)}.", listenAfter = true)
        }
    }

    /** Los archivos temporales no contienen nada que la persona haya hecho, así que no se pregunta nada; la respuesta dice cuánto se recuperó. */
    fun clearCache() {
        viewModelScope.launch {
            val freed = storageReport.clearCache()
            _storage.value = storageReport.read()
            cues.play(FeedbackCues.Cue.SUCCESS)
            voiceEngine.speak(
                if (freed > 0) "Listo, vacié la memoria temporal y liberé ${storageReport.format(freed)}."
                else "La memoria temporal ya estaba vacía.",
                listenAfter = true,
            )
        }
    }

    fun cancelClean() {
        _pendingClean.value = false
        voiceEngine.speak("De acuerdo, no borré nada.", listenAfter = true)
    }

    fun describeStorage(usage: StorageUsage): String = storageReport.describe(usage)

    // ---- Privacidad: borrar mi cuenta, y el interruptor de la IA ------------------------------------------------------

    /** Pregunta primero, con palabras claras sobre lo que se pierde: esto no se puede deshacer. Necesita una persona con sesión iniciada. */
    /**
     * Borrar una cuenta no se puede deshacer, por eso pide algo más que un simple "sí": la contraseña de la
     * propia persona. Esto también evita que un "sí, borrar" suelto de otra persona en la habitación borre una cuenta.
     */
    fun askDeleteAccount() {
        if (currentUser.value == null) {
            voiceEngine.speak("No hay una sesión iniciada, así que no hay una cuenta que borrar. Ve a iniciar sesión primero.", listenAfter = true)
            return
        }
        _awaitingDeletePassword.value = true
        deleteAskedAt = System.currentTimeMillis()
        voiceEngine.rawInput = true // una contraseña hablada debe llegar aquí sin tocar, igual que al iniciar sesión
        voiceEngine.speak("Para borrar tu cuenta, primero dime tu contraseña.", listenAfter = true)
    }

    /** Para el campo de contraseña en pantalla, cuando la persona la escribe en vez de decirla. */
    fun onDeletePasswordEntered(password: String) {
        if (password.isBlank()) return
        checkDeletePassword(password)
    }

    private fun checkDeletePassword(spoken: String) {
        val user = currentUser.value
        _awaitingDeletePassword.value = false
        if (user == null || !sessionRepository.passwordMatches(user.contrasenaHash, spoken)) {
            voiceEngine.rawInput = false
            cues.play(FeedbackCues.Cue.ERROR)
            voiceEngine.speak("Esa no es tu contraseña. No borré nada.", listenAfter = true)
            return
        }
        _pendingDeleteAccount.value = true
        deleteAskedAt = System.currentTimeMillis()
        voiceEngine.speak(
            "Contraseña correcta. ¿Borro tu cuenta y todo lo que guardaste: chats, resultados, documentos y fotos? " +
                "No se puede deshacer. Di sí, borrar, para confirmar, o no.",
            listenAfter = true,
        )
    }

    fun confirmDeleteAccount() {
        _pendingDeleteAccount.value = false
        voiceEngine.rawInput = false
        viewModelScope.launch {
            if (sessionRepository.deleteCurrentAccount()) {
                cues.play(FeedbackCues.Cue.SUCCESS)
                voiceEngine.speak(
                    "Listo. Borré los datos de tu cuenta: tus chats, resultados, documentos y fotos. " +
                        "Algunas preferencias de este teléfono, como el nombre con el que te saludo y cómo hablo, se quedan, " +
                        "porque son de este teléfono y no de tu cuenta.",
                )
                _loggedOut.tryEmit(Unit)
            } else {
                voiceEngine.speak("No pude borrar la cuenta porque no hay una sesión iniciada.")
            }
        }
    }

    fun cancelDeleteAccount() {
        _pendingDeleteAccount.value = false
        _awaitingDeletePassword.value = false
        voiceEngine.rawInput = false
        voiceEngine.speak("De acuerdo, no borré nada.", listenAfter = true)
    }

    /** La IA es opcional y está apagada por defecto: lo que el asistente no entiende nunca se envía a ningún lado a menos que esto esté activado. */
    fun onHolaChanged(enabled: Boolean) {
        viewModelScope.launch { deviceSettings.setHolaEnabled(enabled) }
        voiceEngine.speak(
            if (enabled) {
                "Listo. Ahora también respondo cuando digas hola."
            } else {
                "Listo. Ya no respondo a hola. Di mi nombre para hablar conmigo."
            },
        )
    }

    fun onAiEnabledChanged(enabled: Boolean) {
        viewModelScope.launch { deviceSettings.setAiEnabled(enabled) }
        voiceEngine.speak(
            if (enabled) {
                "Inteligencia artificial activada. Cuando no entienda una frase y haya internet, podré enviarla a un servidor para interpretarla. " +
                    "Por ahora todavía no está conectada. No la actives si quien usa la aplicación es menor de edad."
            } else {
                "Inteligencia artificial desactivada. Ninguna de tus preguntas se envía a un servidor para interpretarla. " +
                    "El reconocimiento de voz del teléfono sigue funcionando como siempre."
            },
        )
    }

    private companion object {
        /** Una confirmación para borrar una cuenta solo es válida por un rato corto. */
        const val CONFIRM_WINDOW_MS = 30_000L

        /** Se dice antes de activar la escucha fuera de la app; la aceptación se guarda con su fecha en el teléfono. */
        const val LISTEN_CONSENT_NOTICE =
            "Antes de activar esto: voy a mantener el micrófono encendido mientras tengas la sesión iniciada, " +
                "aunque la aplicación no esté en pantalla. Solo escucho mi nombre y tus comandos; no grabo ni guardo lo que oigo. " +
                "Verás un aviso fijo y puedes detenerme diciendo para. Gasta más batería. ¿Aceptas? Di acepto, o no acepto."

        /** Palabras completas que confirman el borrado; un simple "ok" o "dale" no basta para algo tan definitivo. */
        val DELETE_CONFIRM_WORDS = setOf("si", "confirmo", "borrar", "borrala", "borralo", "elimina", "eliminala")
    }

    fun currentVolumePercent(): Int = volumeControl.percent()

    fun onVolumeChanged(percent: Int) {
        volumeControl.setPercent(percent)
        voiceEngine.speak(volumeControl.levelText())
    }

    fun isNotificationAccessEnabled(): Boolean = notificationAccess.isEnabled()

    fun onOpenNotificationSettings() {
        voiceEngine.speak("Voy a abrir los ajustes. Busca CECAPI en la lista y actívalo. Después regresa a la aplicación.")
        notificationAccess.openSettings()
    }

    fun onAnnounceNotificationsChanged(enabled: Boolean) {
        viewModelScope.launch { deviceSettings.setAnnounceNotifications(enabled) }
        voiceEngine.speak(
            if (enabled) "Te avisaré cuando llegue una notificación." else "Ya no te avisaré cuando llegue una notificación.",
        )
    }

    /** Inicia o detiene el servicio que sigue escuchando "hola" después de cerrar la aplicación. */
    fun onListenOutsideAppChanged(enabled: Boolean) {
        if (!enabled) {
            applyListenOutside(false)
            return
        }
        if (currentUser.value == null) {
            voiceEngine.speak("Para escuchar fuera de la aplicación primero inicia sesión.")
            return
        }
        viewModelScope.launch {
            when {
                deviceSettings.listenConsentAt.first() != null -> applyListenOutside(true)
                // Tocar el interruptor otra vez mientras se espera la respuesta cuenta como aceptar.
                _pendingListenConsent.value -> acceptListenConsent()
                else -> {
                    _pendingListenConsent.value = true
                    voiceEngine.speak(LISTEN_CONSENT_NOTICE, listenAfter = true)
                }
            }
        }
    }

    /** True mientras se leyó el aviso del micrófono y la persona todavía no acepta ni rechaza. */
    private val _pendingListenConsent = MutableStateFlow(false)

    private fun acceptListenConsent() {
        _pendingListenConsent.value = false
        viewModelScope.launch {
            deviceSettings.setListenConsent(System.currentTimeMillis())
            applyListenOutside(true)
        }
    }

    private fun declineListenConsent() {
        _pendingListenConsent.value = false
        voiceEngine.speak("De acuerdo, no activé la escucha fuera de la aplicación.")
    }

    private fun applyListenOutside(enabled: Boolean) {
        viewModelScope.launch {
            deviceSettings.setBackgroundListening(enabled)
            if (enabled) backgroundListening.start() else backgroundListening.stop()
        }
        voiceEngine.speak(
            if (enabled) {
                "Listo. Seguiré escuchando aunque cierres la aplicación. Verás un aviso fijo. Di para, para detenerme."
            } else {
                "Listo. Ya no escucharé cuando la aplicación esté cerrada."
            },
        )
    }

    fun onSimpleModeChanged(enabled: Boolean) {
        viewModelScope.launch { deviceSettings.setSimpleMode(enabled) }
        voiceEngine.speak(if (enabled) "Modo simple activado." else "Modo simple desactivado.")
    }

    fun onLogout() {
        cues.play(FeedbackCues.Cue.NAVIGATE)
        sessionRepository.logout()
        _loggedOut.tryEmit(Unit)
    }

    override fun onCleared() {
        voiceEngine.rawInput = false
        super.onCleared()
    }
}
