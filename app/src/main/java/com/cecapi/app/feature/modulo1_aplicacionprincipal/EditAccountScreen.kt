package com.cecapi.app.feature.modulo1_aplicacionprincipal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiBorder
import com.cecapi.app.core.theme.CecapiSurface
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.ui.BigBtn
import com.cecapi.app.core.ui.BigBtnVariant
import com.cecapi.app.core.ui.MicPad
import com.cecapi.app.core.ui.NoticeBanner
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.ui.voiceHint
import com.cecapi.app.core.voice.VoiceState

private enum class PestanaCuenta { MisDatos, ComoMeVen }

@Composable
fun EditAccountScreen(
    onBack: () -> Unit,
    viewModel: EditAccountViewModel = hiltViewModel(),
) {
    val user by viewModel.currentUser.collectAsState()
    var pestana by remember { mutableStateOf(PestanaCuenta.MisDatos) }
    var nombre by remember(user?.nombreCompleto) { mutableStateOf(user?.nombreCompleto.orEmpty()) }
    var apodo by remember(user?.apodo) { mutableStateOf(user?.apodo.orEmpty()) }
    var usarApodo by remember(user?.usarApodoRanking) { mutableStateOf(user?.usarApodoRanking ?: false) }
    var contrasenaActual by remember { mutableStateOf("") }
    var contrasenaNueva by remember { mutableStateOf("") }
    var contrasenaVisible by remember { mutableStateOf(false) }
    var confirmarBaja by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val voiceState by viewModel.voiceState.collectAsState()
    val listening = voiceState is VoiceState.Listening

    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }
    LaunchedEffect(Unit) { viewModel.guardado.collect { onBack() } }
    LaunchedEffect(Unit) { viewModel.errorMensaje.collect { error = it } }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .padding(bottom = 230.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTopBar(
            eyebrow = "CUENTA",
            title = "Mi cuenta",
            onBack = viewModel::onBack,
            onCommands = viewModel::onCommandsRequested,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigBtn(
                icon = Icons.Filled.Edit,
                label = "Mis datos",
                variant = if (pestana == PestanaCuenta.MisDatos) BigBtnVariant.Accent else BigBtnVariant.Neutral,
                half = true,
                onClick = { pestana = PestanaCuenta.MisDatos },
                modifier = Modifier.weight(1f),
            )
            BigBtn(
                icon = Icons.Filled.Person,
                label = "Cómo me ven",
                variant = if (pestana == PestanaCuenta.ComoMeVen) BigBtnVariant.Accent else BigBtnVariant.Neutral,
                half = true,
                onClick = { pestana = PestanaCuenta.ComoMeVen },
                modifier = Modifier.weight(1f),
            )
        }

        when (pestana) {
            PestanaCuenta.MisDatos -> {
                NoticeBanner("Los campos con * son obligatorios. Para guardar cambios confirma con tu contraseña actual.")

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(CecapiSurface)
                        .border(1.dp, CecapiBorder, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OutlinedTextField(
                        value = nombre,
                        onValueChange = { nombre = it },
                        modifier = Modifier.fillMaxWidth().voiceHint("Tu nombre completo. Es obligatorio."),
                        label = { Text("Tu nombre completo *") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent, unfocusedBorderColor = CecapiTextMuted),
                    )

                    OutlinedTextField(
                        value = apodo,
                        onValueChange = { apodo = it },
                        modifier = Modifier.fillMaxWidth().voiceHint(
                            "Tu apodo. Es opcional, y solo se usa en Clasificación si eliges mostrarlo en vez de tu nombre.",
                        ),
                        label = { Text("Apodo (opcional)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent, unfocusedBorderColor = CecapiTextMuted),
                    )

                    ToggleNombreApodo(usarApodo = usarApodo, onChange = { usarApodo = it })

                    val solicitud = user?.institucionSolicitada
                    when {
                        !user?.origen.isNullOrBlank() -> Text(
                            "Eres parte de ${user?.origen}.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = CecapiTextMuted,
                        )
                        solicitud != null -> NoticeBanner(
                            "Solicitud enviada a $solicitud. Mientras tanto eres usuario.",
                        )
                        else -> BigBtn(
                            icon = Icons.Filled.Badge,
                            label = "Pedir unirme a ${InstitucionRegistro.CECAPI.etiqueta}",
                            onClick = { viewModel.onPedirInstitucion(InstitucionRegistro.CECAPI.origen) },
                        )
                    }

                    OutlinedTextField(
                        value = contrasenaNueva,
                        onValueChange = { contrasenaNueva = it.filter(Char::isDigit) },
                        modifier = Modifier.fillMaxWidth().voiceHint("Nueva contraseña, si quieres cambiarla. Déjalo vacío para no cambiarla."),
                        label = { Text("Nueva contraseña (opcional)") },
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                        singleLine = true,
                        visualTransformation = if (contrasenaVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { contrasenaVisible = !contrasenaVisible }) {
                                Icon(
                                    imageVector = if (contrasenaVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (contrasenaVisible) "Ocultar contraseña" else "Mostrar contraseña",
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent, unfocusedBorderColor = CecapiTextMuted),
                    )

                    OutlinedTextField(
                        value = contrasenaActual,
                        onValueChange = { contrasenaActual = it.filter(Char::isDigit) },
                        modifier = Modifier.fillMaxWidth().voiceHint("Tu contraseña actual, para confirmar los cambios. Es obligatoria."),
                        label = { Text("Tu contraseña actual, para confirmar *") },
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = { viewModel.onGuardarPerfil(nombre, apodo, usarApodo, contrasenaActual, contrasenaNueva) },
                        ),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent, unfocusedBorderColor = CecapiTextMuted),
                    )

                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BigBtn(
                            icon = Icons.Filled.Check,
                            label = "Guardar",
                            variant = BigBtnVariant.Accent,
                            half = true,
                            onClick = { viewModel.onGuardarPerfil(nombre, apodo, usarApodo, contrasenaActual, contrasenaNueva) },
                            modifier = Modifier.weight(1f),
                        )
                        BigBtn(
                            icon = Icons.Filled.Close,
                            label = "Cancelar",
                            half = true,
                            onClick = viewModel::onBack,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!user?.origen.isNullOrBlank()) {
                        BigBtn(
                            icon = Icons.Filled.Close,
                            label = if (user?.solicitaBaja == true) "Baja pedida, falta confirmación" else "Pedir mi baja",
                            variant = BigBtnVariant.Danger,
                            onClick = viewModel::onPedirBajaDeMenor,
                        )
                    } else if (!confirmarBaja) {
                        BigBtn(
                            icon = Icons.Filled.Close,
                            label = "Darme de baja",
                            variant = BigBtnVariant.Danger,
                            onClick = { confirmarBaja = true },
                        )
                    } else {
                        NoticeBanner(
                            "¿Borro tu cuenta y todo lo que guardaste? No se puede deshacer. Escribe tu contraseña actual arriba y toca Confirmar baja.",
                        )
                        BigBtn(
                            icon = Icons.Filled.Close,
                            label = "Confirmar baja",
                            variant = BigBtnVariant.Danger,
                            onClick = { viewModel.onDarseDeBaja(contrasenaActual) },
                        )
                    }
                }
            }

            PestanaCuenta.ComoMeVen -> {
                NoticeBanner("Así te ven otras personas en la app. Esta pestaña solo muestra, no cambia nada.")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(CecapiSurface)
                        .border(1.dp, CecapiBorder, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val nombreVisible = if (usarApodo && !user?.apodo.isNullOrBlank()) user?.apodo else user?.nombreCompleto
                    Text(nombreVisible.orEmpty(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        "Rol: ${user?.rol.orEmpty().replaceFirstChar { it.uppercase() }}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = CecapiTextMuted,
                    )
                    if (!user?.origen.isNullOrBlank()) {
                        Text("Institución: ${user?.origen}", style = MaterialTheme.typography.bodyLarge, color = CecapiTextMuted)
                    }
                    Text(
                        when {
                            !user?.origen.isNullOrBlank() -> "Miembro de ${user?.origen}, validado por la institución."
                            user?.institucionSolicitada != null -> "Solicitud pendiente en ${user?.institucionSolicitada}. Por ahora eres usuario."
                            else -> "Usuario. Puedes pedir unirte a una institución en Mis datos."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = CecapiTextMuted,
                    )
                    Text(
                        "Tu correo y tu fecha de nacimiento no se muestran a otras personas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CecapiTextMuted,
                    )
                }
            }
        }
    }

        MicPad(
            listening = listening,
            onDoubleTap = viewModel::onMicDoubleTap,
            hint = if (listening) "Escuchando…" else "Toca aquí para hablar",
            onClick = viewModel::onMicTapped,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}


@Composable
private fun ToggleNombreApodo(usarApodo: Boolean, onChange: (Boolean) -> Unit) {
    Column {
        Text(
            "EN CLASIFICACIÓN, MUÉSTRAME COMO:",
            style = MaterialTheme.typography.labelLarge,
            color = CecapiTextMuted,
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BigBtn(
                icon = Icons.Filled.Badge,
                label = "Mi nombre",
                variant = if (!usarApodo) BigBtnVariant.Accent else BigBtnVariant.Neutral,
                half = true,
                onClick = { onChange(false) },
                modifier = Modifier.weight(1f),
            )
            BigBtn(
                icon = Icons.Filled.Face,
                label = "Mi apodo",
                variant = if (usarApodo) BigBtnVariant.Accent else BigBtnVariant.Neutral,
                half = true,
                onClick = { onChange(true) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
