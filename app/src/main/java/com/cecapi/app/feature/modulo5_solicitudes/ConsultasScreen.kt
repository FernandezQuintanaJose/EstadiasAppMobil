package com.cecapi.app.feature.modulo5_solicitudes

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiBorder
import com.cecapi.app.core.theme.CecapiError
import com.cecapi.app.core.theme.CecapiEyebrowStyle
import com.cecapi.app.core.theme.CecapiSurfaceElevated
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.theme.CecapiTextPrimary
import com.cecapi.app.core.theme.Sections
import com.cecapi.app.core.ui.BigBtn
import com.cecapi.app.core.ui.BigBtnVariant
import com.cecapi.app.core.ui.MicPad
import com.cecapi.app.core.ui.PantallaConMicrofono
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.voice.VoiceState

/**
 * Consultas: se pide un documento por voz o escribiendo y la IA lo genera. Lo que se pidió y el documento
 * resultante van en paneles separados; la zona de abajo es el micrófono, igual que en el resto de la app.
 */
@Composable
fun ConsultasScreen(
    onBack: () -> Unit,
    viewModel: ConsultasViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val voiceState by viewModel.voiceState.collectAsState()
    val estado by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.onMicTapped() }

    val listening = voiceState is VoiceState.Listening

    PantallaConMicrofono {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScreenTopBar(
                eyebrow = "MENÚ",
                title = "Consultas",
                onBack = onBack,
                onCommands = viewModel::onCommandsRequested,
            )

            Panel(titulo = "TU CONSULTA") {
                if (estado.paso == ConsultaPaso.ESPERANDO) {
                    var texto by remember(estado.consulta) { mutableStateOf(estado.consulta) }
                    OutlinedTextField(
                        value = texto,
                        onValueChange = { texto = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Di o escribe qué documento necesitas") },
                        minLines = 2,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { viewModel.generar(texto) }),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent),
                    )
                    estado.error?.let { mensaje ->
                        Text(mensaje, style = MaterialTheme.typography.bodyMedium, color = CecapiError)
                    }
                    BigBtn(
                        icon = Icons.Filled.Description,
                        label = "Generar documento",
                        section = Sections.Documents,
                        enabled = texto.isNotBlank(),
                        onClick = { viewModel.generar(texto) },
                    )
                } else {
                    Text(estado.consulta, style = MaterialTheme.typography.bodyLarge, color = CecapiTextPrimary)
                }
            }

            when (estado.paso) {
                ConsultaPaso.GENERANDO -> Panel(titulo = "DOCUMENTO") {
                    Text("Generando el documento…", style = MaterialTheme.typography.bodyLarge, color = CecapiTextMuted)
                }
                ConsultaPaso.LISTO -> {
                    Panel(titulo = "DOCUMENTO") {
                        SelectionContainer {
                            Text(
                                estado.documento.orEmpty(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = CecapiTextPrimary,
                            )
                        }
                    }
                    BigBtn(
                        icon = Icons.Filled.VolumeUp,
                        label = "Leer el documento",
                        section = Sections.Documents,
                        onClick = viewModel::leerDocumento,
                    )
                    BigBtn(
                        icon = Icons.Filled.Refresh,
                        label = "Nueva consulta",
                        variant = BigBtnVariant.Accent,
                        onClick = viewModel::nuevaConsulta,
                    )
                }
                ConsultaPaso.ESPERANDO -> Unit
            }
        }

        MicPad(
            listening = listening,
            onDoubleTap = viewModel::onMicDoubleTap,
            hint = if (listening) "Escuchando…" else "Di qué documento necesitas, o toca aquí",
            onClick = {
                val granted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) viewModel.onMicTapped() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
        )
    }
}

/** Un panel con título: la consulta y el documento van cada uno en el suyo. */
@Composable
private fun Panel(titulo: String, contenido: @Composable () -> Unit) {
    val forma = RoundedCornerShape(20.dp)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(titulo, style = CecapiEyebrowStyle, color = CecapiTextMuted)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(forma)
                .background(CecapiSurfaceElevated)
                .border(2.dp, CecapiBorder, forma)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            contenido()
        }
    }
}
