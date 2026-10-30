package com.cecapi.app.feature.modulo1_aplicacionprincipal

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiError
import com.cecapi.app.core.theme.CecapiEyebrowStyle
import com.cecapi.app.core.theme.CecapiSurfaceElevated
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.ui.BigBtn
import com.cecapi.app.core.ui.desplazableConBarra
import com.cecapi.app.core.ui.CampoTexto
import com.cecapi.app.core.ui.BigBtnVariant
import com.cecapi.app.core.ui.MicPad
import com.cecapi.app.core.ui.PantallaConMicrofono
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.ui.voiceHint
import com.cecapi.app.core.voice.VoiceState

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onBack: () -> Unit = {},
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.onMicTapped() else viewModel.onMicPermissionDenied() }

    LaunchedEffect(Unit) {
        viewModel.registerSucceeded.collect { onRegisterSuccess() }
    }
    LaunchedEffect(Unit) {
        viewModel.back.collect { onBack() }
    }

    val listening = voiceState is VoiceState.Listening

    // Mismo micrófono grande y fijo que en el resto de la app — un solo control, siempre en el
    // mismo lugar, para alguien que no ve dónde está un botón chico.
    PantallaConMicrofono {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .desplazableConBarra()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        ScreenTopBar(
            eyebrow = "CREAR CUENTA",
            title = "Nueva cuenta",
            onBack = onBack,
            onCommands = { viewModel.onMicTapped() },
        )

        Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            CampoTexto(
                titulo = "Nombre completo",
                value = uiState.nombreCompleto,
                onValueChange = viewModel::onNombreCompletoChange,
                placeholder = "Tu nombre y apellido",
                ayuda = "Campo de nombre completo. Aquí va tu nombre y apellido, como quieres que te salude.",
                icono = Icons.Filled.Badge,
            )
            CampoTexto(
                titulo = "Usuario",
                value = uiState.nombreUsuario,
                onValueChange = viewModel::onNombreUsuarioChange,
                placeholder = "Elige un nombre de usuario",
                ayuda = "Campo de usuario. Elige el nombre corto con el que vas a entrar. Se escribe en mayúsculas automáticamente.",
                icono = Icons.Filled.Person,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                ),
            )
            CampoTexto(
                titulo = "Contraseña",
                value = uiState.contrasena,
                onValueChange = viewModel::onContrasenaChange,
                placeholder = "Mínimo quince letras (puede ser una frase)",
                ayuda = "Campo de contraseña. Elige una frase de al menos quince letras, sin números ni símbolos. " +
                    "Se ve oculta y nunca la leo en voz alta.",
                icono = Icons.Filled.Lock,
                contrasena = true,
            )
            CampoTexto(
                titulo = "Confirma tu contraseña",
                value = uiState.confirmarContrasena,
                onValueChange = viewModel::onConfirmarContrasenaChange,
                placeholder = "Escríbela otra vez",
                ayuda = "Campo para confirmar la contraseña. Vuelve a escribirla igual.",
                icono = Icons.Filled.Lock,
                contrasena = true,
            )
            CampoTexto(
                titulo = "Correo (opcional)",
                value = uiState.email,
                onValueChange = viewModel::onEmailChange,
                placeholder = "correo@ejemplo.com",
                ayuda = "Campo de correo, opcional. Sirve para recuperar tu cuenta. Puedes dejarlo vacío.",
                icono = Icons.Filled.Email,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false),
            )
        }

        Text(
            text = "INSTITUCIÓN",
            style = CecapiEyebrowStyle,
            color = CecapiTextMuted,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InstitucionRegistro.entries.forEach { opcion ->
                BigBtn(
                    icon = Icons.Filled.School,
                    label = opcion.etiqueta,
                    variant = if (uiState.institucion == opcion) BigBtnVariant.Accent else BigBtnVariant.Neutral,
                    half = true,
                    onClick = { viewModel.onInstitucionChange(opcion) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        uiState.errorMessage?.let { error ->
            Text(
                text = error,
                color = CecapiError,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        BigBtn(
            icon = Icons.Filled.Check,
            label = if (uiState.isSubmitting) "Creando…" else "Crear mi cuenta",
            variant = BigBtnVariant.Accent,
            enabled = !uiState.isSubmitting,
            onClick = viewModel::submit,
            modifier = Modifier.padding(top = 24.dp),
        )
    }

        MicPad(
            listening = listening,
            onDoubleTap = viewModel::onMicDoubleTap,
            hint = if (listening) "Escuchando…" else "Di tu nombre, o toca aquí",
            onClick = {
                val granted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) viewModel.onMicTapped() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
        )
    }
}
