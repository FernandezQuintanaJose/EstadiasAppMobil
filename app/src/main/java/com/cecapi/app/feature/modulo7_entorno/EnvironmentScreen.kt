package com.cecapi.app.feature.modulo7_entorno

import androidx.compose.material.icons.filled.AddAPhoto
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Replay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.theme.CecapiBackground
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.ui.SelectorModoCamara
import com.cecapi.app.core.ui.CameraViewfinder
import com.cecapi.app.core.ui.CapturedPhotoPreview
import com.cecapi.app.core.ui.CaptureButton
import com.cecapi.app.core.ui.FramingGuide
import com.cecapi.app.core.ui.TopAction
import com.cecapi.app.core.ui.capturePhoto
import com.cecapi.app.core.ui.copyPickedImage
import com.cecapi.app.core.voice.VoiceState
import kotlinx.coroutines.launch

@Composable
fun EnvironmentScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: EnvironmentViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) viewModel.onCameraPermissionDenied()
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    val takePhoto: () -> Unit = {
        val capture = imageCapture
        if (capture == null) {
            viewModel.onCaptureFailed()
        } else {
            capture.capturePhoto(
                context = context,
                folder = "cecapi_entorno",
                onFailed = viewModel::onCaptureFailed,
            ) { uri, path -> viewModel.onPhotoCaptured(uri, path) }
        }
    }

    LaunchedEffect(Unit) { viewModel.captureRequests.collect { takePhoto() } }
    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }
    LaunchedEffect(Unit) { viewModel.routes.collect(onOpen) }

    // El selector de fotos del sistema: la persona elige una foto y la aplicación solo ve esa.
    val coroutineScope = rememberCoroutineScope()
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) {
            viewModel.onPickCancelled()
        } else {
            coroutineScope.launch {
                val copy = copyPickedImage(context, uri, "cecapi_entorno")
                if (copy == null) viewModel.onPickFailed() else viewModel.onPhotoCaptured(copy.first, copy.second)
            }
        }
    }
    LaunchedEffect(Unit) {
        viewModel.pickRequests.collect {
            pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // En cuanto hay una foto, ella ocupa esta área — su propio lugar donde mirar, separado de la vista
        // en vivo — en lugar de una cámara en vivo que ya nadie observa mientras se lee la descripción.
        val photoPath = uiState.photoPath
        if (photoPath == null) {
            CameraViewfinder(
                hasPermission = hasCameraPermission,
                onReady = { imageCapture = it },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (hasCameraPermission && photoPath == null) {
            FramingGuide(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.8f)
                    .aspectRatio(3f / 4f),
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenTopBar(
                eyebrow = "CÁMARA",
                title = "Qué hay enfrente",
                onBack = onBack,
                onCommands = viewModel::onCommandsRequested,
            )

            // La foto tiene su propio espacio, separado de los botones: así no se encima con ellos.
            if (photoPath != null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp)),
                ) {
                    CapturedPhotoPreview(path = photoPath, modifier = Modifier.fillMaxSize())
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            if (uiState.descripcion != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(CecapiBackground.copy(alpha = 0.82f))
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TopAction(
                        icon = Icons.Filled.Replay,
                        label = "Repetir",
                        help = "Repetir. Vuelve a decir la última descripción.",
                        onClick = viewModel::repetir,
                    )
                }
            }

            val listening = voiceState is VoiceState.Listening
            if (photoPath != null) {
                SelectorModoCamara(leyendoTexto = false, onLeerTexto = viewModel::cambiarALectura, onEnfrente = {})
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TopAction(
                        icon = Icons.Filled.AddAPhoto,
                        label = "Tomar otra foto",
                        help = "Tomar otra foto. Vuelve a la cámara para tomar una nueva foto. También puedes decir: tomar otra foto.",
                        onClick = viewModel::tomarOtraFoto,
                    )
                    TopAction(
                        icon = if (listening) Icons.Filled.MicOff else Icons.Filled.Mic,
                        label = if (listening) "Escuchando" else "Hablar",
                        help = "Micrófono. Tócalo para hablar — di qué hay enfrente, o lo que necesites.",
                        onClick = viewModel::onMicTapped,
                    )
                }
            } else Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopAction(
                    icon = Icons.Filled.PhotoLibrary,
                    label = "Mis fotos",
                    help = "Mis fotos. Abre el selector de fotos de tu teléfono para que elijas una imagen que ya tienes " +
                        "y te la describa. La aplicación solo ve la foto que tú elijas. También puedes decir: elige una foto.",
                    onClick = viewModel::pedirGaleria,
                )
                CaptureButton(
                    processing = uiState.isProcessing,
                    enabled = imageCapture != null && !uiState.isProcessing,
                    help = "Botón de captura. Toca dos veces para describir lo que hay enfrente. " +
                        "También puedes decir: qué hay enfrente.",
                    onClick = viewModel::pedirCaptura,
                )
                // Mismo ancho que "Mis fotos" para que el obturador quede centrado, pero ahora es
                // un micrófono real en vez de un espacio vacío.
                val listening = voiceState is VoiceState.Listening
                TopAction(
                    icon = if (listening) Icons.Filled.MicOff else Icons.Filled.Mic,
                    label = if (listening) "Escuchando" else "Hablar",
                    help = "Micrófono. Tócalo para hablar — di qué hay enfrente, o lo que necesites.",
                    onClick = viewModel::onMicTapped,
                )
            }
        }
    }
}
