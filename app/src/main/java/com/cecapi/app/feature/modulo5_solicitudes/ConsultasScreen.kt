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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.ui.unit.sp
import com.cecapi.app.core.ui.CampoTexto
import com.cecapi.app.core.ui.ColumnaDesplazable
import com.cecapi.app.core.ui.TarjetaLectura
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
    onOpenChats: () -> Unit,
    viewModel: ConsultasViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val voiceState by viewModel.voiceState.collectAsState()
    val estado by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }
    LaunchedEffect(Unit) { viewModel.chats.collect { onOpenChats() } }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.onMicTapped() }

    val listening = voiceState is VoiceState.Listening

    PantallaConMicrofono {
        ColumnaDesplazable(descripcionBarra = "Barra de desplazamiento de consultas") {
            ScreenTopBar(
                eyebrow = "MENÚ",
                title = "Consultas",
                onBack = onBack,
                onCommands = viewModel::onCommandsRequested,
            )

            when (estado.paso) {
                ConsultaPaso.ESPERANDO -> {
                    var texto by remember(estado.consulta) { mutableStateOf(estado.consulta) }
                    CampoTexto(
                        titulo = "Tu consulta",
                        value = texto,
                        onValueChange = { texto = it },
                        placeholder = "Pregunta algo o pide un documento, por ejemplo: una carta para pedir un justificante",
                        ayuda = "Campo de consulta. Escribe o di tu pregunta, o qué documento necesitas.",
                        icono = Icons.Filled.Description,
                        singleLine = false,
                        minLines = 3,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { viewModel.generar(texto) }),
                    )
                    estado.error?.let { mensaje ->
                        TarjetaLectura(titulo = "No se pudo generar", cuerpo = mensaje, colorCuerpo = CecapiError)
                    }
                    BigBtn(
                        icon = Icons.Filled.AutoAwesome,
                        label = "Enviar consulta",
                        section = Sections.Documents,
                        enabled = texto.isNotBlank(),
                        onClick = { viewModel.generar(texto) },
                    )
                    BigBtn(
                        icon = Icons.Filled.EditNote,
                        label = "Ya tengo el texto",
                        sub = "Escribirlo o pegarlo aquí para editarlo y escucharlo",
                        section = Sections.Documents,
                        onClick = viewModel::escribirPropio,
                    )
                }
                ConsultaPaso.GENERANDO -> TarjetaLectura(
                    titulo = "Pensando tu respuesta…",
                    cuerpo = estado.consulta,
                    icono = Icons.Filled.HourglassTop,
                    section = Sections.Documents,
                )
                ConsultaPaso.LISTO -> {
                    TarjetaLectura(
                        titulo = "Tu consulta",
                        cuerpo = estado.consulta,
                        icono = Icons.Filled.QuestionAnswer,
                        section = Sections.Documents,
                    )
                    // El documento es un cuadro editable: se puede corregir lo que generó la IA, o pegar aquí el texto
                    // que dio otro asistente, y lo que se lee en voz alta es lo que está escrito.
                    CampoTexto(
                        titulo = if (estado.esDocumento) "Documento" else "Respuesta",
                        value = estado.documento.orEmpty(),
                        onValueChange = viewModel::editarDocumento,
                        placeholder = "Escribe o pega aquí el texto",
                        ayuda = "Es un cuadro de texto: puedes corregirlo o escribir encima. Lo que leo es lo que está aquí.",
                        icono = Icons.Filled.Description,
                        singleLine = false,
                        minLines = 8,
                    )
                    BigBtn(
                        icon = Icons.Filled.VolumeUp,
                        label = if (estado.esDocumento) "Leer el documento" else "Leer la respuesta",
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
            }

            // Lo que antes era Chats: todas las consultas anteriores, para volver a escucharlas.
            BigBtn(
                icon = Icons.Filled.History,
                label = "Consultas anteriores",
                sub = "Escucha otra vez lo que te respondí",
                section = Sections.Chats,
                onClick = viewModel::abrirChats,
            )
        }

        MicPad(
            listening = listening,
            onDoubleTap = viewModel::onMicDoubleTap,
            hint = if (listening) "Escuchando…" else "Pregunta o pide un documento, o toca aquí",
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
