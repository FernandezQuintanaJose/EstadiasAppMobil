package com.cecapi.app.feature.modulo1_aplicacionprincipal

import com.cecapi.app.core.navigation.CecapiDestinations
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceMessages
import com.cecapi.app.core.voice.VoiceState
import com.cecapi.app.core.voice.VoiceText
import com.cecapi.app.core.voice.WakeWordController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Qué significa lo próximo que diga el usuario: un comando, o el valor de un campo. */
private enum class LoginVoiceStep { COMMAND, USERNAME, PASSWORD }

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val errorMessage: String? = null,
    val isSubmitting: Boolean = false,
)

/**
 * Flujo de voz para usuarios ciegos: decir "usuario", y luego decir el nombre de usuario; decir "contraseña", y luego decir
 * la contraseña (inicia sesión por su cuenta). Decirlo de otra forma ("ingresa el usuario") también funciona,
 * pero el asistente le dice al usuario la forma corta para que la aprenda.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val sessionRepository: SessionRepository,
    private val wakeWordController: WakeWordController,
    private val attemptsStore: LoginAttemptsStore,
    private val cues: FeedbackCues,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    private val _loginSucceeded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val loginSucceeded: SharedFlow<Unit> = _loginSucceeded

    private val _navigateToRegister = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToRegister: SharedFlow<Unit> = _navigateToRegister

    private val _back = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val back: SharedFlow<Unit> = _back

    private var step = LoginVoiceStep.COMMAND
        set(value) {
            field = value
            // Mientras se dicta un nombre de usuario o una contraseña, debe llegarnos sin que lo toquen los comandos globales.
            voiceEngine.rawInput = value != LoginVoiceStep.COMMAND
        }
    private var holdingWakeWord = false

    // Después de un intento fallido guiamos al usuario por el nombre de usuario y luego la contraseña otra vez, sin
    // hacer que diga las palabras clave "usuario" / "contraseña".
    private var guided = false

    // Este ViewModel sigue vivo debajo de la pantalla de Registro; ignora la voz dirigida a esa pantalla.
    private var screenActive = false

    init {
        viewModelScope.launch {
            val lockMs = attemptsStore.lockRemainingMs()
            if (lockMs > 0) {
                voiceEngine.speak(lockMessage(lockMs))
            } else {
                voiceEngine.speak(
                    "Abriendo el inicio de sesión. Di usuario para decir tu usuario, contraseña para decir " +
                        "tu contraseña, o crear cuenta si aún no tienes una.",
                    listenAfter = true,
                )
            }
        }
        viewModelScope.launch {
            voiceEngine.speechFor(CecapiDestinations.LOGIN).collect { speech ->
                if (screenActive) onSpeech(speech.text.trim())
            }
        }
    }

    fun setScreenActive(active: Boolean) {
        screenActive = active
    }

    /** Escuchar la palabra de activación necesita el permiso del micrófono, así que la pantalla lo activa solo cuando ya lo tiene. */
    fun setWakeWordEnabled(enabled: Boolean) {
        if (enabled == holdingWakeWord) return
        holdingWakeWord = enabled
        // Micrófono abierto: en esta pantalla el usuario está en medio de una tarea, así que no se necesita "hola".
        if (enabled) wakeWordController.acquire(openMic = true) else wakeWordController.release(openMic = true)
    }

    private fun onSpeech(text: String) {
        // Una palabra clave de campo o "crear cuenta" siempre gana, incluso mientras se espera un valor. De lo contrario, si
        // una solicitud expiró, el siguiente "contraseña" sería absorbido como si fuera el nombre de usuario.
        if (step != LoginVoiceStep.COMMAND &&
            (parseFields(text).isNotEmpty() || wantsRegister(VoiceText.normalize(text)))
        ) {
            step = LoginVoiceStep.COMMAND
        }
        when (step) {
            LoginVoiceStep.USERNAME -> captureUsername(text)
            LoginVoiceStep.PASSWORD -> capturePassword(text)
            LoginVoiceStep.COMMAND -> onCommand(text)
        }
    }

    private fun captureUsername(text: String) {
        step = LoginVoiceStep.COMMAND
        if (wantsToLeaveWhileCapturing(text)) return
        applyUsername(text)
    }

    private fun capturePassword(text: String) {
        step = LoginVoiceStep.COMMAND
        if (wantsToLeaveWhileCapturing(text)) return
        applyPasswordAndSubmit(text)
    }

    /**
     * Mientras se dicta el nombre de usuario o la contraseña, "regresar"/"salir" debe salir de la pantalla de inicio de sesión
     * como lo hace en todas partes — no escribirse como si fueran el nombre de usuario o la contraseña. Un
     * "cancelar"/"no" más suave solo cancela este campo y se queda, preguntando otra vez.
     */
    private fun wantsToLeaveWhileCapturing(text: String): Boolean {
        val words = VoiceText.normalize(text)
        if (VoiceText.hasAny(words, EXIT_PHRASES)) {
            voiceEngine.speak("Volviendo al inicio.")
            _back.tryEmit(Unit)
            return true
        }
        if (words in CANCEL_WORDS) {
            voiceEngine.speak("De acuerdo. Di usuario, contraseña o ingresar.", listenAfter = true)
            return true
        }
        return false
    }

    /** Los nombres de usuario se guardan en MAYÚSCULAS sin espacios, para que uno hablado coincida sin importar cómo se escuchó. */
    private fun applyUsername(spoken: String) {
        val username = spoken.replace(" ", "").uppercase()
        onUsernameChange(username)
        if (guided) {
            step = LoginVoiceStep.PASSWORD
            voiceEngine.speak("Usuario: $username. Ahora dime tu contraseña.", listenAfter = true)
        } else {
            voiceEngine.speak(
                "Usuario: $username. Di contraseña para continuar, o usuario si quieres corregirlo.",
                listenAfter = true,
            )
        }
    }

    private fun applyPasswordAndSubmit(spoken: String) {
        onPasswordChange(spoken.replace(" ", ""))
        submit()
    }

    private class SpokenField(val field: LoginVoiceStep, val value: String)

    /**
     * Encuentra "usuario" / "contraseña" en [raw] y el valor dicho justo después de cada uno, así una sola
     * frase puede llevar todo: "usuario pepe contraseña 1234". Una palabra clave sin nada después
     * da un valor vacío. El valor conserva la forma en que se escribió (las contraseñas distinguen mayúsculas y minúsculas).
     */
    private fun parseFields(raw: String): List<SpokenField> {
        val folded = VoiceText.fold(raw)
        val matches = FIELD_KEYWORD.findAll(folded).toList()
        return matches.mapIndexed { i, match ->
            val field = if (match.groupValues[1].isNotEmpty()) LoginVoiceStep.USERNAME else LoginVoiceStep.PASSWORD
            val start = match.range.last + 1
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else raw.length
            // Omite relleno como "es", "el", "mi" entre la palabra clave y el valor ("mi usuario es pepe").
            val skip = FILLER_PREFIX.find(folded.substring(start, end))?.value?.length ?: 0
            SpokenField(field, raw.substring(start + skip, end).trim { !it.isLetterOrDigit() })
        }
    }

    private fun wantsRegister(words: String): Boolean =
        words.contains("crear cuenta") || words.contains("registr") || words.contains("nueva cuenta")

    private fun handleFields(words: String, fields: List<SpokenField>) {
        val username = fields.lastOrNull { it.field == LoginVoiceStep.USERNAME && it.value.isNotBlank() }?.value
        val password = fields.lastOrNull { it.field == LoginVoiceStep.PASSWORD && it.value.isNotBlank() }?.value
        when {
            username != null && password != null -> {
                onUsernameChange(username.replace(" ", ""))
                applyPasswordAndSubmit(password)
            }
            username != null -> applyUsername(username)
            password != null -> {
                if (_uiState.value.username.isBlank()) {
                    onPasswordChange(password.replace(" ", ""))
                    voiceEngine.speak(
                        "Contraseña recibida. Falta el usuario. Di usuario, y luego tu usuario.",
                        listenAfter = true,
                    )
                } else {
                    applyPasswordAndSubmit(password)
                }
            }
            else -> {
                val field = fields.first().field
                askForField(field, coach = !isBareFieldCommand(words, field))
            }
        }
    }

    private fun onCommand(text: String) {
        val words = VoiceText.normalize(text)
        val fields = parseFields(text)
        when {
            // Antes del analizador de campos: "olvidé mi contraseña" termina en la palabra "contraseña" sin nada
            // después, lo cual el analizador de otro modo leería como "empieza a dictar la contraseña" — y entonces
            // lo que la persona dijera después se intentaría como la contraseña misma y fallaría.
            VoiceText.hasAny(words, FORGOT_PASSWORD_PHRASES) -> voiceEngine.speak(
                "Todavía no hay una forma automática de recuperar tu contraseña. Pide a un administrador o " +
                    "a quien te dio de alta que te ayude a restablecerla.",
                listenAfter = true,
            )
            VoiceText.hasAny(words, EXIT_PHRASES) -> {
                voiceEngine.speak("Volviendo al inicio.")
                _back.tryEmit(Unit)
            }
            wantsRegister(words) -> {
                voiceEngine.speak("Vamos a crear tu cuenta.")
                _navigateToRegister.tryEmit(Unit)
            }
            fields.isNotEmpty() -> handleFields(words, fields)
            SUBMIT_WORDS.any { it in words } -> submit()
            CommandCatalog.isRequest(words) -> voiceEngine.speak(CommandCatalog.LOGIN, listenAfter = true)
            words.contains("ayuda") || words.contains("repite") -> voiceEngine.speak(
                "Di usuario, y luego tu usuario. Di contraseña, y luego tu contraseña. " +
                    "Cuando tengas los dos, di ingresar.",
                listenAfter = true,
            )
            else -> {
                cues.play(FeedbackCues.Cue.NOT_UNDERSTOOD)
                voiceEngine.speak(
                    "No entendí. Para tu usuario di: usuario. Para tu contraseña di: contraseña. " +
                        "Para entrar di: ingresar. O di lista de comandos.",
                )
            }
        }
    }

    private fun askForField(field: LoginVoiceStep, coach: Boolean) {
        step = field
        val name = if (field == LoginVoiceStep.USERNAME) "usuario" else "contraseña"
        val tip = if (coach) "Entendido. La próxima vez basta con decir solo: $name. " else ""
        voiceEngine.speak("${tip}Dime tu $name.", listenAfter = true)
    }

    /** "usuario", "el usuario", "mi contraseña"... a diferencia de "ingresa el usuario". */
    private fun isBareFieldCommand(words: String, field: LoginVoiceStep): Boolean {
        val keywords = if (field == LoginVoiceStep.USERNAME) USER_WORDS else PASSWORD_WORDS
        val withoutArticle = words.removePrefix("el ").removePrefix("la ").removePrefix("mi ").removePrefix("tu ")
        return withoutArticle in keywords
    }

    fun onUsernameChange(value: String) {
        _uiState.value = _uiState.value.copy(username = value.uppercase(), errorMessage = null)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, errorMessage = null)
    }

    fun onMicTapped() {
        voiceEngine.startListening()
    }

    fun onMicPermissionDenied() {
        voiceEngine.speak(VoiceMessages.MIC_DENIED)
    }

    /** Dos toques rápidos en el micrófono silencian al asistente, para alguien que usa el tacto en vez de la voz. */
    fun onMicDoubleTap() = voiceEngine.mute()

    fun onCommandsRequested() {
        voiceEngine.speak(
            "Di usuario para dictar tu usuario, o contraseña para dictar tu contraseña, o escríbelos abajo. " +
                "Di ingresar cuando tengas los dos.",
            listenAfter = true,
        )
    }

    fun submit() {
        val state = _uiState.value
        if (state.isSubmitting) return
        // Una sugerencia del teclado puede dejar un espacio suelto en cualquier extremo; nunca es parte de una credencial.
        val username = state.username.trim()
        val password = state.password.trim()
        if (username.isEmpty() || password.isEmpty()) {
            val message = when {
                username.isEmpty() && password.isEmpty() ->
                    "Falta tu usuario y tu contraseña. Di usuario para empezar."
                username.isEmpty() -> "Falta tu usuario. Di usuario."
                else -> "Falta tu contraseña. Di contraseña."
            }
            _uiState.update { it.copy(errorMessage = "Falta el usuario o la contraseña.") }
            voiceEngine.speak(message, listenAfter = true)
            return
        }
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            val lockMs = attemptsStore.lockRemainingMs()
            if (lockMs > 0) {
                _uiState.update { it.copy(isSubmitting = false, errorMessage = "Inicio de sesión bloqueado por ahora.") }
                voiceEngine.speak(lockMessage(lockMs))
                return@launch
            }
            val result = try {
                sessionRepository.login(username, password)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Un problema de la base de datos no es culpa del usuario: no lo cuentes como un intento fallido.
                Log.e(TAG, "login crashed", e)
                _uiState.update { it.copy(isSubmitting = false, errorMessage = "No se pudo verificar tus datos. Intenta de nuevo.") }
                voiceEngine.speak("Ocurrió un problema al verificar tus datos. Intenta de nuevo en unos segundos.")
                return@launch
            }
            Log.d(TAG, "login attempt success=${result is LoginResult.Success}")
            when (result) {
                is LoginResult.Success -> {
                    attemptsStore.reset()
                    cues.play(FeedbackCues.Cue.SUCCESS)
                    guided = false
                    step = LoginVoiceStep.COMMAND
                    // Sin voz aquí: a continuación se abre el Dashboard, que da la bienvenida personalizada.
                    _uiState.update { it.copy(isSubmitting = false) }
                    _loginSucceeded.tryEmit(Unit)
                }
                LoginResult.InvalidCredentials -> onFailedLogin()
            }
        }
    }

    /**
     * 1er fallo: intenta otra vez. 2do y siguientes: dice claramente que un campo está mal y pide ambos otra vez.
     * 5to y 8vo: también sugiere pedir ayuda a alguien. 10mo: bloqueado.
     */
    private suspend fun onFailedLogin() {
        val failures = attemptsStore.recordFailure()
        cues.play(if (failures >= LoginAttemptsStore.MAX_FAILED_ATTEMPTS) FeedbackCues.Cue.LOCKED else FeedbackCues.Cue.ERROR)
        Log.d(TAG, "failed attempt #$failures")
        val remaining = LoginAttemptsStore.MAX_FAILED_ATTEMPTS - failures

        if (failures >= LoginAttemptsStore.MAX_FAILED_ATTEMPTS) {
            guided = false
            step = LoginVoiceStep.COMMAND
            _uiState.update { it.copy(isSubmitting = false, password = "", errorMessage = "Inicio de sesión bloqueado.") }
            voiceEngine.speak(lockMessage(LoginAttemptsStore.LOCK_MS))
            return
        }

        _uiState.update {
            it.copy(
                isSubmitting = false,
                password = "",
                errorMessage = "Usuario o contraseña incorrectos. Intento $failures de ${LoginAttemptsStore.MAX_FAILED_ATTEMPTS}.",
            )
        }
        if (failures == 1) {
            guided = false
            step = LoginVoiceStep.COMMAND
            voiceEngine.speak("Usuario o contraseña incorrectos. Di usuario para intentarlo de nuevo.", listenAfter = true)
            return
        }

        guided = true
        step = LoginVoiceStep.USERNAME
        val message = buildString {
            append("Van $failures intentos fallidos. Alguno de los dos datos está mal. ")
            if (failures in SUPPORT_AT_FAILURES) {
                append("Te recomiendo pedir apoyo a otra persona para iniciar sesión. ")
            }
            if (remaining <= 2) {
                append(if (remaining == 1) "Te queda un intento antes del bloqueo. " else "Te quedan $remaining intentos antes del bloqueo. ")
            }
            append("Vamos a corregirlos. Dime tu usuario otra vez.")
        }
        voiceEngine.speak(message, listenAfter = true)
    }

    private fun lockMessage(remainingMs: Long): String {
        val minutes = ((remainingMs + 59_999) / 60_000).coerceAtLeast(1)
        return "El inicio de sesión está bloqueado por demasiados intentos fallidos. " +
            "Pide apoyo a otra persona. Podrás intentarlo de nuevo en $minutes " +
            if (minutes == 1L) "minuto." else "minutos."
    }

    override fun onCleared() {
        voiceEngine.rawInput = false
        setWakeWordEnabled(false)
        super.onCleared()
    }

    private companion object {
        const val TAG = "CecapiLogin"
        val SUPPORT_AT_FAILURES = setOf(5, 8)
        val USER_WORDS = listOf("usuario", "nombre de usuario", "user")
        val PASSWORD_WORDS = listOf("contrasena", "clave", "password", "pasword")

        // Grupo 1 = palabra clave de usuario, grupo 2 = palabra clave de contraseña (coincide en texto en minúsculas sin acentos).
        val FIELD_KEYWORD = Regex(
            "(?<![\\p{L}\\p{N}])(?:(nombre de usuario|usuario)|(contrasena|clave|password|pasword))(?![\\p{L}\\p{N}])",
        )
        val FILLER_PREFIX = Regex("^\\s*(?:(?:es|son|sera|seria|va a ser|de|el|la|mi|tu)\\s+)*")
        val SUBMIT_WORDS = listOf("ingresa", "ingresar", "entrar", "entra", "iniciar", "inicia", "listo", "aceptar", "enviar")
        val CANCEL_WORDS = setOf("cancelar", "cancela", "atras", "no", "borrar")
        val FORGOT_PASSWORD_PHRASES = listOf(
            "olvide mi contrasena", "olvide la contrasena", "se me olvido la contrasena", "se me olvido mi contrasena",
            "no recuerdo mi contrasena", "no me acuerdo de mi contrasena", "perdi mi contrasena",
        )
        val EXIT_PHRASES = listOf("atras", "volver", "vuelve", "regresa", "regresar", "salir", "menu", "inicio")
    }
}
