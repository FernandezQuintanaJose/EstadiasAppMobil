package com.cecapi.app.feature.modulo1_aplicacionprincipal

import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Phone
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiError
import com.cecapi.app.core.theme.CecapiEyebrowStyle
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.theme.ModuleSection
import com.cecapi.app.core.theme.Sections
import com.cecapi.app.core.ui.BigBtn
import com.cecapi.app.core.ui.BigBtnVariant
import com.cecapi.app.core.ui.MenuItem
import com.cecapi.app.core.ui.MicPad
import com.cecapi.app.core.ui.PantallaConMicrofono
import com.cecapi.app.core.ui.NoticeBanner
import com.cecapi.app.core.ui.OfflineBanner
import com.cecapi.app.core.util.openTtsSettings
import com.cecapi.app.core.voice.VoiceMessages
import com.cecapi.app.core.voice.VoiceState


/** Inicio con sesión abierta. Mismo diseño y comportamiento de voz que el inicio público, más los módulos propios del usuario. */
@Composable
fun DashboardScreen(
    onModuleClick: (String) -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val enabledModules by viewModel.enabledModules.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val assistantName by viewModel.assistantName.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navEvents.collect(onModuleClick)
    }
    // Salir de la app también libera el teléfono: la pantalla deja de mantenerse encendida y se libera el micrófono.
    LaunchedEffect(Unit) {
        viewModel.exitEvents.collect { (context as? Activity)?.finishAffinity() }
    }
    LaunchedEffect(currentUser) {
        if (currentUser == null) onLoggedOut()
    }
    // Dashboard sigue activo debajo de las pantallas de módulo; solo reacciona al habla mientras se muestra.
    DisposableEffect(Unit) {
        viewModel.setScreenActive(true)
        onDispose { viewModel.setScreenActive(false) }
    }

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasMicPermission = granted
        if (granted) viewModel.onMicTapped() else viewModel.onMicPermissionDenied()
    }
    // "Hola" (o el nombre del asistente) abre el micrófono mientras se muestra el dashboard.
    DisposableEffect(hasMicPermission) {
        viewModel.setWakeWordEnabled(hasMicPermission)
        onDispose { viewModel.setWakeWordEnabled(false) }
    }

    val listening = voiceState is VoiceState.Listening
    val menu by viewModel.menu.collectAsState()

    // La barra del micrófono queda fija sobre el contenido que se desplaza (como en el prototipo de
    // Figma), no en línea dentro del flujo de scroll, para que siempre sea alcanzable sin importar
    // cuánto haya desplazado la persona.
    PantallaConMicrofono {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("ASISTENTE PARA PERSONAS INVIDENTES", style = CecapiEyebrowStyle, color = CecapiTextMuted, textAlign = TextAlign.Center)
            Text(
                text = "Hola,",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                text = currentUser?.nombreCompleto.orEmpty(),
                style = MaterialTheme.typography.headlineLarge,
                color = CecapiAccent,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            currentUser?.let { user ->
                Text(
                    text = buildString {
                        append(user.rol.replaceFirstChar { it.uppercase() })
                        if (user.origen.isNotBlank()) append(" · ${user.origen}")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = CecapiTextMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Lo que dice la voz no se repite en pantalla. Solo queda visible lo que necesita atención.
        if (!hasMicPermission) {
            NoticeBanner(VoiceMessages.MIC_DENIED, modifier = Modifier.padding(horizontal = 24.dp).padding(top = 16.dp))
        } else if (voiceState is VoiceState.Error) {
            val error = voiceState as VoiceState.Error
            NoticeBanner(
                error.message,
                modifier = Modifier.padding(horizontal = 24.dp).padding(top = 16.dp),
                actionLabel = if (error.openTtsSettings) "Elegir motor de voz" else null,
                onAction = if (error.openTtsSettings) {
                    { openTtsSettings(context) }
                } else null,
            )
        }
        if (!isOnline) {
            OfflineBanner(modifier = Modifier.padding(horizontal = 24.dp).padding(top = 16.dp))
        }

        // Orden actualizado según el mockup de Figma: Clasificación + Mi cuenta en media fila,
        // Cámara, Documentos, Actividades, Chats, Personalización, Configuración, Gestión (si
        // aplica), Ayuda y soporte (si aplica), Cerrar sesión. "Mi cuenta" reutiliza la pantalla
        // de editar cuenta ya existente; no hay pantallas reales de Emergencias ni de Reportar un
        // incidente (distinta de Ayuda y soporte), así que esas dos tarjetas del Figma no se
        // agregan todavía.
        val rol = currentUser?.rol?.let(RolUsuario::fromCodigo)
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigBtn(
                    icon = Icons.Filled.EmojiEvents,
                    label = "Clasificación",
                    section = Sections.Ranking,
                    half = true,
                    onClick = viewModel::onRankingSelected,
                    modifier = Modifier.weight(1f),
                )
                BigBtn(
                    icon = Icons.Filled.Person,
                    label = "Mi cuenta",
                    section = ModuleSection(color = Color(0xFF60A5FA), bg = Color(0xFF1A2740)),
                    half = true,
                    onClick = viewModel::onEditarCuentaSelected,
                    modifier = Modifier.weight(1f),
                )
            }
            BigBtn(
                icon = Icons.Filled.CameraAlt,
                label = "Cámara",
                section = Sections.Camera,
                onClick = { viewModel.onMenuItemSelected(MenuItem.Camera) },
            )
            menu.firstOrNull { it.key == "REQUESTS" }?.let { documentos ->
                BigBtn(
                    icon = Icons.Filled.Description,
                    label = documentos.title,
                    section = Sections.Documents,
                    onClick = { viewModel.onMenuItemSelected(documentos) },
                )
            }
            menu.firstOrNull { it.key == "LEARNING" }?.let { actividades ->
                BigBtn(
                    icon = Icons.Filled.Hearing,
                    label = actividades.title,
                    section = Sections.Activities,
                    onClick = { viewModel.onMenuItemSelected(actividades) },
                )
            }
            BigBtn(
                icon = Icons.Filled.Chat,
                label = "Chats",
                section = Sections.Chats,
                onClick = { viewModel.onMenuItemSelected(MenuItem.Chats) },
            )
            BigBtn(
                icon = Icons.Filled.Tune,
                label = "Personalización",
                section = Sections.Personalization,
                onClick = { viewModel.onMenuItemSelected(MenuItem.Personalization) },
            )
            if (rol == RolUsuario.ADMINISTRADOR || rol == RolUsuario.DIRECTIVO || rol == RolUsuario.EDUCADOR) {
                BigBtn(
                    icon = Icons.Filled.AdminPanelSettings,
                    label = "Gestión",
                    variant = BigBtnVariant.Accent,
                    onClick = viewModel::onGestionSelected,
                )
            }
            BigBtn(
                icon = Icons.Filled.Settings,
                label = "Configuración",
                section = Sections.Settings,
                onClick = viewModel::onSettingsSelected,
            )
            BigBtn(
                icon = Icons.AutoMirrored.Filled.Logout,
                label = "Cerrar sesión",
                variant = BigBtnVariant.Danger,
                onClick = viewModel::onLogout,
            )
            // Ayuda y soporte es para cualquiera; el administrador ve ahí los mensajes de todos.
            BigBtn(
                icon = Icons.AutoMirrored.Filled.Help,
                label = "Ayuda y soporte",
                section = Sections.Ayuda,
                onClick = viewModel::onSoporteSelected,
            )
            if (rol != RolUsuario.USUARIO) {
                BigBtn(
                    icon = Icons.Filled.Warning,
                    label = "Reportar un incidente",
                    section = Sections.Reportar,
                    onClick = viewModel::onReportarIncidenteSelected,
                )
            }
            BigBtn(
                icon = Icons.Filled.Phone,
                label = "Emergencias",
                section = Sections.Emergency,
                onClick = viewModel::onEmergenciasSelected,
            )
        }
    }

        // El micrófono es el control principal de toda la app: queda fijo sobre el contenido
        // que se desplaza arriba, para que siempre sea alcanzable sin importar cuánto haya
        // desplazado la persona.
        MicPad(
            onDoubleTap = viewModel::onMicDoubleTap,
            listening = listening,
            hint = when {
                listening -> "Escuchando…"
                !hasMicPermission -> "Toca aquí para dar permiso"
                assistantName.isBlank() -> "Toca aquí para hablar"
                else -> "Di \"$assistantName\" o toca aquí"
            },
            onClick = {
                if (hasMicPermission) viewModel.onMicTapped() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
        )
    }
}
