package com.cecapi.app.feature.modulo1_aplicacionprincipal

import com.cecapi.app.core.navigation.CecapiDestinations
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.util.PasswordRules
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceMessages
import com.cecapi.app.core.voice.VoiceState
import com.cecapi.app.core.voice.VoiceText
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

private enum class RegisterVoiceTarget { NOMBRE_COMPLETO, USUARIO, CONTRASENA, NONE }

/** Por ahora solo hay una institución real; el resto entra como independiente. */
enum class InstitucionRegistro(val origen: String, val etiqueta: String) {
    NINGUNA("", "No estoy afiliado con ninguna"),
    CECAPI("CECAPI", "CECAPI"),
}

data class RegisterUiState(
    val nombreCompleto: String = "",
    val nombreUsuario: String = "",
    val contrasena: String = "",
    val confirmarContrasena: String = "",
    /** Opcional: sirve para recuperar la cuenta y, cuando el servidor lo permita, para verificarla con un código. */
    val email: String = "",
    val institucion: InstitucionRegistro = InstitucionRegistro.NINGUNA,
    val errorMessage: String? = null,
    val isSubmitting: Boolean = false,
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val cues: FeedbackCues,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    private val _registerSucceeded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val registerSucceeded: SharedFlow<Unit> = _registerSucceeded

    private val _back = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val back: SharedFlow<Unit> = _back

    private var voiceTarget = RegisterVoiceTarget.NOMBRE_COMPLETO

    init {
        // Lo que se dice aquí es un nombre o una contraseña, así que los comandos globales no deben tomarlo. Los controles
        // de frase completa (cancelar, repite, lista de comandos) se manejan más abajo.
        voiceEngine.rawInput = true
        voiceEngine.speak(
            "Vamos a crear tu cuenta. Di tu nombre completo, o escríbelo abajo. Di cancelar para volver. " +
                CommandCatalog.hint("crear cuenta"),
        )
        viewModelScope.launch {
            voiceEngine.speechFor(CecapiDestinations.REGISTER).collect { speech -> onVoiceInput(speech.text.trim()) }
        }
    }

    private fun onVoiceInput(texto: String) {
        val phrase = VoiceText.normalize(texto)
        if (phrase in CANCEL_PHRASES) {
            _back.tryEmit(Unit)
            return
        }
        if (CommandCatalog.isRequest(phrase)) {
            voiceEngine.speak(CommandCatalog.REGISTER, listenAfter = true)
            return
        }
        if (phrase in REPEAT_PHRASES) {
            repeatPrompt()
            return
        }
        when (voiceTarget) {
            RegisterVoiceTarget.NOMBRE_COMPLETO -> {
                onNombreCompletoChange(texto)
                voiceTarget = RegisterVoiceTarget.USUARIO
                voiceEngine.speak("Ahora di el nombre de usuario que quieres usar.")
            }
            RegisterVoiceTarget.USUARIO -> {
                onNombreUsuarioChange(texto.replace(" ", ""))
                voiceTarget = RegisterVoiceTarget.CONTRASENA
                voiceEngine.speak("Ahora di tu contraseña. Debe tener al menos quince letras; puede ser una frase, sin números ni símbolos.")
            }
            RegisterVoiceTarget.CONTRASENA -> {
                // Dictada: se normaliza igual que al iniciar sesión, y se toma también como su confirmación.
                val dictada = PasswordRules.normalize(texto)
                onContrasenaChange(dictada)
                onConfirmarContrasenaChange(dictada)
                voiceTarget = RegisterVoiceTarget.NONE
                submit()
            }
            RegisterVoiceTarget.NONE -> voiceEngine.speak(
                "Ya tengo tus datos. Si algo está mal, corrígelo escribiendo en la pantalla.",
            )
        }
    }

    /** Dos toques rápidos en el micrófono silencian al asistente, para alguien que usa el tacto con las manos en vez de la voz. */
    fun onMicDoubleTap() = voiceEngine.mute()

    fun onMicTapped() {
        voiceEngine.startListening()
    }

    fun onMicPermissionDenied() {
        voiceEngine.speak(VoiceMessages.MIC_DENIED)
    }

    private fun repeatPrompt() {
        voiceEngine.speak(
            when (voiceTarget) {
                RegisterVoiceTarget.NOMBRE_COMPLETO -> "Di tu nombre completo."
                RegisterVoiceTarget.USUARIO -> "Di el nombre de usuario que quieres usar."
                RegisterVoiceTarget.CONTRASENA -> "Di tu contraseña, una frase de al menos quince letras."
                RegisterVoiceTarget.NONE -> "Ya tengo tus datos. Si algo está mal, corrígelo escribiendo en la pantalla."
            },
            listenAfter = voiceTarget != RegisterVoiceTarget.NONE,
        )
    }

    override fun onCleared() {
        voiceEngine.rawInput = false
        super.onCleared()
    }

    private companion object {
        // Solo frases completas: el nombre o la contraseña de una persona podría contener estas palabras.
        val CANCEL_PHRASES = setOf("cancelar", "cancela", "volver", "vuelve", "atras", "regresar", "regresa", "salir")
        val EMAIL_REGEX = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")
        val REPEAT_PHRASES = setOf("repite", "repetir", "otra vez", "de nuevo", "repite la pregunta", "ayuda")
    }

    fun onNombreCompletoChange(value: String) {
        _uiState.value = _uiState.value.copy(nombreCompleto = value, errorMessage = null)
    }

    fun onNombreUsuarioChange(value: String) {
        _uiState.value = _uiState.value.copy(nombreUsuario = value.uppercase(), errorMessage = null)
    }

    fun onContrasenaChange(value: String) {
        _uiState.value = _uiState.value.copy(contrasena = value, errorMessage = null)
    }

    fun onEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(email = value.trim(), errorMessage = null)
    }

    fun onConfirmarContrasenaChange(value: String) {
        _uiState.value = _uiState.value.copy(confirmarContrasena = value, errorMessage = null)
    }

    fun onInstitucionChange(value: InstitucionRegistro) {
        _uiState.value = _uiState.value.copy(institucion = value)
    }

    fun submit() {
        val state = _uiState.value
        if (state.nombreCompleto.isBlank() || state.nombreUsuario.isBlank() || state.contrasena.isBlank()) {
            val message = "Falta llenar tu nombre, usuario o contraseña."
            _uiState.value = state.copy(errorMessage = message)
            voiceEngine.speak(message)
            return
        }
        val contrasenaNormalizada = PasswordRules.normalize(state.contrasena)
        val problema = PasswordRules.problem(contrasenaNormalizada)
        if (problema != null) {
            val message = "$problema Dime otra contraseña."
            _uiState.value = state.copy(errorMessage = message)
            voiceTarget = RegisterVoiceTarget.CONTRASENA
            voiceEngine.speak(message, listenAfter = true)
            return
        }
        if (PasswordRules.normalize(state.confirmarContrasena) != contrasenaNormalizada) {
            val message = "Las contraseñas no coinciden. Escribe la confirmación otra vez."
            _uiState.value = state.copy(errorMessage = message, confirmarContrasena = "")
            voiceEngine.speak(message)
            return
        }
        if (state.email.isNotEmpty() && !EMAIL_REGEX.matches(state.email)) {
            val message = "El correo no parece válido. Revísalo o déjalo vacío."
            _uiState.value = state.copy(errorMessage = message)
            voiceEngine.speak(message)
            return
        }

        _uiState.value = state.copy(isSubmitting = true, errorMessage = null)
        viewModelScope.launch {
            val outcome = try {
                sessionRepository.register(
                    nombreUsuario = state.nombreUsuario,
                    contrasena = state.contrasena,
                    nombreCompleto = state.nombreCompleto,
                    origen = state.institucion.origen,
                    email = state.email.ifBlank { null },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("CecapiRegister", "register crashed", e)
                val message = "No se pudo crear la cuenta por un problema del teléfono. Intenta de nuevo."
                _uiState.value = state.copy(isSubmitting = false, errorMessage = message)
                voiceEngine.speak(message)
                return@launch
            }
            when (val result = outcome) {
                is RegisterResult.Success -> {
                    _uiState.value = state.copy(isSubmitting = false)
                    cues.play(FeedbackCues.Cue.SUCCESS)
                    // Sin voz aquí: a continuación se abre el Dashboard, que da la bienvenida personalizada.
                    _registerSucceeded.tryEmit(Unit)
                }
                RegisterResult.UsernameTaken -> {
                    cues.play(FeedbackCues.Cue.ERROR)
                    val message = "Ese nombre de usuario ya existe. Di o escribe otro."
                    _uiState.value = state.copy(isSubmitting = false, errorMessage = message, nombreUsuario = "")
                    voiceEngine.speak(message)
                    voiceTarget = RegisterVoiceTarget.USUARIO
                }
            }
        }
    }
}
