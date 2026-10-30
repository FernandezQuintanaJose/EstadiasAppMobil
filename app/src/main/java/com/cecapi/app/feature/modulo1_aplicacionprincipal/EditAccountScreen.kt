package com.cecapi.app.feature.modulo1_aplicacionprincipal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.theme.CecapiError
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.theme.Sections
import com.cecapi.app.core.ui.BigBtn
import com.cecapi.app.core.ui.BigBtnVariant
import com.cecapi.app.core.ui.CampoTexto
import com.cecapi.app.core.ui.ColumnaDesplazable
import com.cecapi.app.core.ui.MicPad
import com.cecapi.app.core.ui.PantallaConMicrofono
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.ui.TarjetaLectura
import com.cecapi.app.core.util.PasswordRules
import com.cecapi.app.core.voice.VoiceState

/**
 * Cuenta: "¿Cómo me ven?", editar los datos, institución, cerrar sesión y, hasta abajo, borrar la cuenta.
 * Los avisos importantes se dicen en voz alta (no se confía en que alguien lea un aviso en pantalla).
 */
@Composable
fun EditAccountScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: EditAccountViewModel = hiltViewModel(),
) {
    val user by viewModel.currentUser.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val pasoBorrado by viewModel.pasoBorrado.collectAsState()
    val listening = voiceState is VoiceState.Listening
    var editando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }
    LaunchedEffect(Unit) { viewModel.loggedOut.collect { onLoggedOut() } }
    LaunchedEffect(Unit) { viewModel.guardado.collect { editando = false } }
    LaunchedEffect(Unit) { viewModel.errorMensaje.collect { error = it } }

    PantallaConMicrofono {
        ColumnaDesplazable(descripcionBarra = "Barra de desplazamiento de la cuenta") {
            ScreenTopBar(
                eyebrow = "MENÚ",
                title = "Cuenta",
                onBack = if (editando) ({ editando = false }) else viewModel::onBack,
                onCommands = viewModel::onCommandsRequested,
            )

            val actual = user
            if (actual == null) {
                Text("No hay una sesión iniciada.", style = MaterialTheme.typography.bodyLarge, color = CecapiTextMuted)
            } else if (editando) {
                FormularioDatos(
                    usuario = actual,
                    error = error,
                    onGuardar = { nombre, apodo, usaApodo, contrasenaActual, nueva, correo ->
                        viewModel.onGuardarPerfil(nombre, apodo, usaApodo, contrasenaActual, nueva, correo)
                    },
                    onCancelar = { editando = false; error = null },
                )
            } else {
                TarjetaLectura(
                    titulo = "¿Cómo me ven?",
                    cuerpo = EditAccountViewModel.comoMeVen(actual),
                    icono = Icons.Filled.RecordVoiceOver,
                    section = Sections.Settings,
                    lectura = EditAccountViewModel.comoMeVen(actual),
                )
                BigBtn(
                    icon = Icons.Filled.Edit,
                    label = "Editar mis datos",
                    sub = "Nombre, apodo, correo y contraseña",
                    section = Sections.Settings,
                    onClick = { editando = true },
                )

                if (actual.rol != RolUsuario.ADMINISTRADOR.codigo) {
                    if (actual.origen.isBlank() && actual.institucionSolicitada == null) {
                        BigBtn(
                            icon = Icons.Filled.Badge,
                            label = "Pedir unirme a ${InstitucionRegistro.CECAPI.etiqueta}",
                            section = Sections.Settings,
                            onClick = { viewModel.onPedirInstitucion(InstitucionRegistro.CECAPI.origen) },
                        )
                    }
                    if (actual.rol == RolUsuario.ALUMNO.codigo && actual.origen.isNotBlank()) {
                        BigBtn(
                            icon = Icons.Filled.Person,
                            label = if (actual.solicitaCambioEducador) "Cambio de educador pedido" else "Pedir cambio de educador",
                            section = Sections.Settings,
                            onClick = viewModel::onPedirCambioEducador,
                        )
                    }
                }

                BigBtn(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    label = "Cerrar sesión",
                    variant = BigBtnVariant.Danger,
                    onClick = viewModel::onCerrarSesion,
                )

                // Hasta abajo, lo que no se puede deshacer.
                if (actual.rol != RolUsuario.ADMINISTRADOR.codigo) {
                    ZonaBorrado(
                        paso = pasoBorrado,
                        error = error,
                        onPedir = viewModel::onPedirBorrar,
                        onContrasena = viewModel::onContrasenaBorrado,
                        onConfirmar = viewModel::onConfirmarBorrado,
                        onCancelar = viewModel::onCancelarBorrado,
                    )
                }
            }
        }

        MicPad(
            listening = listening,
            onDoubleTap = viewModel::onMicDoubleTap,
            hint = if (listening) "Escuchando…" else "Di cómo me ven, o toca aquí",
            onClick = viewModel::onMicTapped,
        )
    }
}

@Composable
private fun ZonaBorrado(
    paso: PasoBorrado,
    error: String?,
    onPedir: () -> Unit,
    onContrasena: (String) -> Unit,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
) {
    when (paso) {
        PasoBorrado.NINGUNO -> BigBtn(
            icon = Icons.Filled.DeleteForever,
            label = "Borrar mis datos y mi cuenta",
            sub = "No se puede deshacer",
            variant = BigBtnVariant.Danger,
            onClick = onPedir,
        )
        PasoBorrado.CONTRASENA -> {
            var contrasena by remember { mutableStateOf("") }
            CampoTexto(
                titulo = "Tu contraseña",
                value = contrasena,
                onValueChange = { contrasena = it },
                placeholder = "Para confirmar que eres tú",
                ayuda = "Campo de contraseña, para confirmar que quieres borrar tu cuenta.",
                icono = Icons.Filled.Lock,
                contrasena = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onContrasena(contrasena) }),
            )
            error?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = CecapiError) }
            BotonesSiNo(
                si = "Continuar",
                siHabilitado = contrasena.isNotBlank(),
                onSi = { onContrasena(contrasena) },
                onNo = onCancelar,
            )
        }
        PasoBorrado.CONFIRMAR -> {
            TarjetaLectura(
                titulo = "¿Borro tu cuenta?",
                cuerpo = "Se borran tu cuenta y todo lo que guardaste. No se puede deshacer.",
                icono = Icons.Filled.DeleteForever,
                colorCuerpo = CecapiError,
            )
            BotonesSiNo(si = "Sí, borrar", siHabilitado = true, onSi = onConfirmar, onNo = onCancelar)
        }
    }
}

@Composable
private fun BotonesSiNo(si: String, siHabilitado: Boolean, onSi: () -> Unit, onNo: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BigBtn(
            icon = Icons.Filled.Check,
            label = si,
            variant = BigBtnVariant.Danger,
            half = true,
            enabled = siHabilitado,
            onClick = onSi,
            modifier = Modifier.weight(1f),
        )
        BigBtn(
            icon = Icons.Filled.Close,
            label = "No, dejarla",
            half = true,
            onClick = onNo,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FormularioDatos(
    usuario: UsuarioEntity,
    error: String?,
    onGuardar: (String, String, Boolean, String, String, String) -> Unit,
    onCancelar: () -> Unit,
) {
    var nombre by remember(usuario.id) { mutableStateOf(usuario.nombreCompleto) }
    var apodo by remember(usuario.id) { mutableStateOf(usuario.apodo.orEmpty()) }
    var usaApodo by remember(usuario.id) { mutableStateOf(usuario.usarApodoRanking) }
    var correo by remember(usuario.id) { mutableStateOf(usuario.email.orEmpty()) }
    var nueva by remember { mutableStateOf("") }
    var actual by remember { mutableStateOf("") }
    // La contraseña nueva es opcional: vacía no se cambia; escrita, debe cumplir la regla.
    val nuevaValida = nueva.isBlank() || PasswordRules.problem(PasswordRules.normalize(nueva)) == null
    val listo = nombre.isNotBlank() && actual.isNotBlank() && nuevaValida

    CampoTexto(
        titulo = "Nombre completo",
        value = nombre,
        onValueChange = { nombre = it },
        ayuda = "Campo de nombre completo.",
        icono = Icons.Filled.Badge,
    )
    CampoTexto(
        titulo = "Apodo (opcional)",
        value = apodo,
        onValueChange = { apodo = it },
        ayuda = "Campo de apodo, opcional. Puede mostrarse en clasificación en lugar de tu nombre.",
        icono = Icons.Filled.Face,
    )
    Text("EN CLASIFICACIÓN ME VEN CON", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BigBtn(
            icon = Icons.Filled.Badge,
            label = "Mi nombre",
            variant = if (!usaApodo) BigBtnVariant.Accent else BigBtnVariant.Neutral,
            half = true,
            onClick = { usaApodo = false },
            modifier = Modifier.weight(1f),
        )
        BigBtn(
            icon = Icons.Filled.Face,
            label = "Mi apodo",
            variant = if (usaApodo) BigBtnVariant.Accent else BigBtnVariant.Neutral,
            half = true,
            onClick = { usaApodo = true },
            modifier = Modifier.weight(1f),
        )
    }
    CampoTexto(
        titulo = "Correo (opcional)",
        value = correo,
        onValueChange = { correo = it },
        ayuda = "Campo de correo, opcional. Nadie más lo ve.",
        icono = Icons.Filled.Email,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false),
    )
    CampoTexto(
        titulo = "Nueva contraseña (opcional)",
        value = nueva,
        onValueChange = { nueva = it },
        placeholder = "Déjala vacía para no cambiarla",
        ayuda = "Campo de contraseña nueva, opcional. Mínimo quince letras, sin números ni símbolos.",
        icono = Icons.Filled.Lock,
        contrasena = true,
    )
    if (!nuevaValida) {
        Text(PasswordRules.DESCRIPTION, style = MaterialTheme.typography.bodyLarge, color = CecapiError)
    }
    CampoTexto(
        titulo = "Contraseña actual",
        value = actual,
        onValueChange = { actual = it },
        placeholder = "Para confirmar los cambios",
        ayuda = "Campo de contraseña actual, para confirmar los cambios.",
        icono = Icons.Filled.Lock,
        contrasena = true,
    )
    error?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = CecapiError) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BigBtn(
            icon = Icons.Filled.Check,
            label = "Guardar",
            variant = BigBtnVariant.Accent,
            half = true,
            enabled = listo,
            onClick = { onGuardar(nombre, apodo, usaApodo, actual, nueva, correo) },
            modifier = Modifier.weight(1f),
        )
        BigBtn(
            icon = Icons.Filled.Close,
            label = "Cancelar",
            half = true,
            onClick = onCancelar,
            modifier = Modifier.weight(1f),
        )
    }
}
