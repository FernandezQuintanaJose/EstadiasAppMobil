package com.cecapi.app.feature.modulo1_aplicacionprincipal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.navigation.CecapiDestinations
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceState
import com.cecapi.app.core.voice.VoiceText
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

/** En qué paso va el borrado de la cuenta: se pide la contraseña y luego una confirmación explícita. */
enum class PasoBorrado { NINGUNO, CONTRASENA, CONFIRMAR }

/**
 * Cuenta: la misma pantalla desde el menú y desde Configuración. Ahí la persona escucha cómo la ven los
 * demás, edita sus datos, cierra sesión y, hasta abajo, borra sus datos y su cuenta. Cada quien edita solo
 * la suya — Gestión nunca cambia esto, solo el rol — y todo cambio pide la contraseña actual.
 */
@HiltViewModel
class EditAccountViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val voiceEngine: VoiceEngine,
    private val cues: FeedbackCues,
) : ViewModel() {

    val currentUser: StateFlow<UsuarioEntity?> = sessionRepository.currentUser

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    private val _back = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val back: SharedFlow<Unit> = _back

    private val _loggedOut = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val loggedOut: SharedFlow<Unit> = _loggedOut

    private val _guardado = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val guardado: SharedFlow<Unit> = _guardado

    private val _errorMensaje = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorMensaje: SharedFlow<String> = _errorMensaje

    private val _pasoBorrado = MutableStateFlow(PasoBorrado.NINGUNO)
    val pasoBorrado: StateFlow<PasoBorrado> = _pasoBorrado.asStateFlow()

    init {
        voiceEngine.speak(
            "Cuenta. Toca cómo me ven para escuchar tus datos. Aquí también editas tus datos, cierras sesión y, " +
                "hasta abajo, borras tu cuenta.",
            listenAfter = true,
        )
        viewModelScope.launch {
            _pasoBorrado.collect { paso -> voiceEngine.rawInput = paso == PasoBorrado.CONTRASENA }
        }
        viewModelScope.launch {
            voiceEngine.speechFor(CecapiDestinations.EDIT_ACCOUNT).collect { speech -> onSpeech(speech.text) }
        }
    }

    private fun onSpeech(spoken: String) {
        val texto = VoiceText.normalize(spoken)
        fun has(vararg palabras: String) = VoiceText.hasAny(texto, *palabras)
        when (_pasoBorrado.value) {
            // Lo que se dice aquí es la contraseña, salvo "cancelar" como frase completa.
            PasoBorrado.CONTRASENA -> if (texto in CANCELAR) onCancelarBorrado() else onContrasenaBorrado(spoken)
            PasoBorrado.CONFIRMAR -> when {
                VoiceText.isNo(spoken) -> onCancelarBorrado()
                texto.split(" ").any { it in CONFIRMAR_BORRADO } -> onConfirmarBorrado()
                else -> voiceEngine.speak("Di sí, borrar, para borrar tu cuenta, o no para dejarla.", listenAfter = true)
            }
            PasoBorrado.NINGUNO -> when {
                has("como me ven", "mis datos", "quien soy", "lee mi cuenta") -> leerComoMeVen()
                has("cerrar sesion", "cierra sesion", "salir de mi cuenta") -> onCerrarSesion()
                has("borra mi cuenta", "borrar mi cuenta", "elimina mi cuenta", "borra mis datos", "borrar mis datos") -> onPedirBorrar()
                has("atras", "volver", "vuelve", "regresa", "regresar", "salir", "menu", "inicio") -> _back.tryEmit(Unit)
                else -> voiceEngine.speak(
                    "Puedes decir: cómo me ven, cerrar sesión, borrar mi cuenta, o atrás.",
                    listenAfter = true,
                )
            }
        }
    }

    fun onBack() {
        _back.tryEmit(Unit)
    }

    fun onCommandsRequested() {
        voiceEngine.speak(
            "Cuenta. Di cómo me ven, para escuchar tus datos. Editar datos se hace en pantalla: cambia tu nombre, " +
                "tu apodo, tu correo y tu contraseña, y confirma con tu contraseña actual. Di cerrar sesión, " +
                "o borrar mi cuenta. Atrás, para volver.",
            listenAfter = true,
        )
    }

    fun onMicTapped() = voiceEngine.startListening()

    /** Dos toques rápidos en el micrófono silencian al asistente, para quien usa el tacto en vez de la voz. */
    fun onMicDoubleTap() = voiceEngine.mute()

    /** Lo mismo que muestra la tarjeta "¿Cómo me ven?", dicho en voz alta. */
    fun leerComoMeVen() {
        val user = currentUser.value ?: return voiceEngine.speak("No hay una sesión iniciada.")
        voiceEngine.speak(comoMeVen(user))
    }

    fun onCerrarSesion() {
        sessionRepository.logout()
        _loggedOut.tryEmit(Unit)
    }

    fun onPedirInstitucion(institucion: String) {
        viewModelScope.launch {
            sessionRepository.pedirInstitucion(institucion)
            cues.play(FeedbackCues.Cue.SUCCESS)
            voiceEngine.speak("Listo. Pedí unirme a $institucion. Mientras tanto sigues siendo usuario.")
        }
    }

    fun onPedirCambioEducador() {
        viewModelScope.launch {
            sessionRepository.pedirCambioEducador()
            cues.play(FeedbackCues.Cue.SUCCESS)
            voiceEngine.speak("Listo. Pedí cambiar de educador. Alguien de tu institución lo revisará.")
        }
    }

    /** Paso 1 del borrado: se pide la contraseña. Un administrador no puede borrarse a sí mismo. */
    fun onPedirBorrar() {
        val user = currentUser.value ?: return
        if (user.rol == RolUsuario.ADMINISTRADOR.codigo) {
            voiceEngine.speak("Un administrador no puede borrar su propia cuenta.")
            return
        }
        _pasoBorrado.value = PasoBorrado.CONTRASENA
        voiceEngine.speak(
            "Para borrar tu cuenta y todos tus datos, primero escribe o di tu contraseña. Di cancelar para dejarla.",
            listenAfter = true,
        )
    }

    /** Paso 2: con la contraseña correcta se pide una confirmación explícita; no se borra nada todavía. */
    fun onContrasenaBorrado(contrasena: String) {
        val user = currentUser.value ?: return
        if (!sessionRepository.passwordMatches(user.contrasenaHash, contrasena)) {
            cues.play(FeedbackCues.Cue.ERROR)
            _errorMensaje.tryEmit("Esa no es tu contraseña.")
            voiceEngine.speak("Esa no es tu contraseña. No borré nada. Inténtalo otra vez o di cancelar.", listenAfter = true)
            return
        }
        _pasoBorrado.value = PasoBorrado.CONFIRMAR
        cues.play(FeedbackCues.Cue.WARNING)
        voiceEngine.speak(
            "¿Seguro que borro tu cuenta y todo lo que guardaste? No se puede deshacer. Di sí, borrar, o no.",
            listenAfter = true,
        )
    }

    fun onConfirmarBorrado() {
        if (_pasoBorrado.value != PasoBorrado.CONFIRMAR) return
        _pasoBorrado.value = PasoBorrado.NINGUNO
        viewModelScope.launch {
            if (sessionRepository.deleteCurrentAccount()) {
                voiceEngine.speak("Listo. Borré tu cuenta y tus datos.")
                _loggedOut.tryEmit(Unit)
            }
        }
    }

    fun onCancelarBorrado() {
        _pasoBorrado.value = PasoBorrado.NINGUNO
        voiceEngine.speak("De acuerdo, no borré tu cuenta.")
    }

    /** [nuevaContrasena] vacía significa "no la cambies". */
    fun onGuardarPerfil(
        nombreCompleto: String,
        apodo: String,
        usarApodoRanking: Boolean,
        contrasenaActual: String,
        nuevaContrasena: String,
        correo: String,
    ) {
        viewModelScope.launch {
            when (
                sessionRepository.actualizarPerfil(
                    nombreCompleto = nombreCompleto,
                    contrasenaActual = contrasenaActual,
                    nuevaContrasena = nuevaContrasena.ifBlank { null },
                    apodo = apodo,
                    usarApodoRanking = usarApodoRanking,
                    email = correo,
                )
            ) {
                SessionRepository.EditarPerfilResult.Exito -> {
                    cues.play(FeedbackCues.Cue.SUCCESS)
                    voiceEngine.speak("Listo, guardé los cambios de tu cuenta.")
                    _guardado.tryEmit(Unit)
                }
                SessionRepository.EditarPerfilResult.ContrasenaActualIncorrecta -> {
                    cues.play(FeedbackCues.Cue.ERROR)
                    voiceEngine.speak("Esa no es tu contraseña actual. No guardé nada.")
                    _errorMensaje.tryEmit("Esa no es tu contraseña actual.")
                }
                SessionRepository.EditarPerfilResult.SinSesion -> {
                    voiceEngine.speak("No hay una sesión iniciada.")
                    _errorMensaje.tryEmit("No hay una sesión iniciada.")
                }
            }
        }
    }

    override fun onCleared() {
        voiceEngine.rawInput = false
        super.onCleared()
    }

    companion object {
        private val CANCELAR = setOf("cancelar", "cancela", "no", "dejala", "mejor no")

        /** Palabras completas que confirman el borrado; un simple "ok" no basta para algo que no se deshace. */
        private val CONFIRMAR_BORRADO = setOf("si", "confirmo", "borrar", "borrala", "borralo", "elimina", "eliminala")

        /** Cómo ven a la persona los demás, en una frase para decir en voz alta y mostrar en la tarjeta. */
        fun comoMeVen(user: UsuarioEntity): String = buildString {
            append("Te llamas ${user.nombreCompleto}. ")
            if (!user.apodo.isNullOrBlank()) {
                append("Tu apodo es ${user.apodo}. ")
                append(if (user.usarApodoRanking) "En clasificación te ven con tu apodo. " else "En clasificación te ven con tu nombre. ")
            }
            append("Tu usuario es ${user.nombreUsuario}. ")
            append("Tu rol es ${user.rol}. ")
            append(
                when {
                    user.origen.isNotBlank() -> "Perteneces a ${user.origen}. "
                    user.institucionSolicitada != null -> "Pediste unirte a ${user.institucionSolicitada}; mientras tanto eres usuario. "
                    else -> "No perteneces a ninguna institución. "
                },
            )
            if (!user.email.isNullOrBlank()) append("Tu correo es ${user.email}; nadie más lo ve.")
        }.trim()
    }
}
