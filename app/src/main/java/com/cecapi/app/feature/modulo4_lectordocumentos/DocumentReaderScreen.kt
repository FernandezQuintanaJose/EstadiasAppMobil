package com.cecapi.app.feature.modulo4_lectordocumentos

import com.cecapi.app.core.theme.ModuleSection
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview as ComposePreview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiBackground
import com.cecapi.app.core.theme.CecapiBorder
import com.cecapi.app.core.theme.CecapiSurfaceElevated
import com.cecapi.app.core.theme.CecapiTextPrimary
import com.cecapi.app.core.theme.ModuleCameraAccent
import com.cecapi.app.core.theme.ModuleLearningAccent
import com.cecapi.app.core.ui.CameraViewfinder
import com.cecapi.app.core.theme.Sections
import com.cecapi.app.core.ui.TarjetaLectura
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.ui.PantallaConMicrofono
import com.cecapi.app.core.ui.MicPad
import com.cecapi.app.core.ui.ColumnaDesplazable
import com.cecapi.app.core.ui.CapturedPhotoPreview
import com.cecapi.app.core.ui.BigBtnVariant
import com.cecapi.app.core.ui.BigBtnSize
import com.cecapi.app.core.ui.BigBtn
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Apps
import com.cecapi.app.core.ui.CaptureButton
import com.cecapi.app.core.ui.FramingGuide
import com.cecapi.app.core.ui.TopAction
import com.cecapi.app.core.ui.capturePhoto
import com.cecapi.app.core.ui.copyPickedImage
import com.cecapi.app.core.voice.VoiceState
import kotlinx.coroutines.launch

@Composable
fun DocumentReaderScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit = {},
    viewModel: DocumentReaderViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val isPreview = LocalInspectionMode.current

    var hasCameraPermission by remember {
        mutableStateOf(
            isPreview || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
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
        if (!isPreview && !hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    val takePhoto: () -> Unit = {
        val capture = imageCapture
        if (capture == null) {
            viewModel.onCaptureFailed()
        } else {
            capture.capturePhoto(
                context = context,
                folder = "cecapi_docs",
                onFailed = viewModel::onCaptureFailed,
            ) { uri, path -> viewModel.onPhotoCaptured(uri, path, desdeCamara = true) }
        }
    }

    LaunchedEffect(Unit) { viewModel.captureRequests.collect { takePhoto() } }
    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }
    LaunchedEffect(Unit) { viewModel.routes.collect(onOpen) }

    // El selector de fotos del sistema: la persona elige una foto y la aplicación solo ve esa.
    val coroutineScope = rememberCoroutineScope()
    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.onSavePhoto() else viewModel.onSavePhotoDenied()
    }
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) {
            viewModel.onPickCancelled()
        } else {
            coroutineScope.launch {
                val copy = copyPickedImage(context, uri, "cecapi_docs")
                if (copy == null) viewModel.onPickFailed() else viewModel.onPhotoCaptured(copy.first, copy.second)
            }
        }
    }
    LaunchedEffect(Unit) {
        viewModel.saveRequests.collect {
            // En Android 9 o anterior guardar en la galería necesita permiso de almacenamiento.
            val needsStorage = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED
            if (needsStorage) storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else viewModel.onSavePhoto()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.pickRequests.collect {
            pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    val hayDocumento = uiState.parrafos.isNotEmpty()
    // Todos los botones piden dos toques: el primero dice qué hacen, el segundo ejecutan la acción.
    val onVolverClick: () -> Unit = { viewModel.onBotonPresionado(BotonLector.VOLVER) }

    if (uiState.panelFoto && !hayDocumento && !uiState.isProcessing) {
        // Pantalla 2: la foto ya está tomada; se elige qué hacer con ella.
        PanelAccionesFoto(
            uiState = uiState,
            listening = voiceState is VoiceState.Listening,
            onBack = onVolverClick,
            onLeerTexto = viewModel::leerTexto,
            onEnfrente = viewModel::describirEntorno,
            onCodigo = viewModel::leerCodigos,
            onTraducir = viewModel::traducir,
            onGuardar = viewModel::guardarFoto,
            onOtraFoto = viewModel::otraFoto,
            onMic = viewModel::onMicTapped,
            onMicDoubleTap = viewModel::onMicDoubleTap,
            onCommands = viewModel::onCommandsRequested,
        )
    } else if (hayDocumento && !uiState.isProcessing) {
        // Pantalla 2: Interfaz futurista CECAPI de control de lectura con botón VOLVER AL MENÚ hasta abajo
        DocumentReadoutControlScreen(
            uiState = uiState,
            onBack = onVolverClick,
            onPause = { viewModel.onBotonPresionado(BotonLector.PAUSAR) },
            onResume = { viewModel.onBotonPresionado(BotonLector.REANUDAR) },
            onRepeat = { viewModel.onBotonPresionado(BotonLector.REPETIR) },
            onPrevious = { viewModel.onBotonPresionado(BotonLector.ANTERIOR) },
            onNext = { viewModel.onBotonPresionado(BotonLector.SIGUIENTE) },
            onSavePhoto = { viewModel.onBotonPresionado(BotonLector.GUARDAR) },
            onRetakePhoto = { viewModel.onBotonPresionado(BotonLector.OTRA_FOTO) },
            onOtrasAcciones = viewModel::otrasAcciones,
        )
    } else {
        // Pantalla 1: Vista previa de cámara limpia a pantalla completa
        DocumentCameraScreen(
            uiState = uiState,
            hasCameraPermission = hasCameraPermission,
            onReadyCamera = { capture ->
                // Flash automático: la cámara lo dispara sola cuando el documento está muy oscuro.
                capture?.flashMode = ImageCapture.FLASH_MODE_AUTO
                imageCapture = capture
            },
            onBack = onVolverClick,
            onCaptureClick = { if (viewModel.onBotonCapturaPresionado()) takePhoto() },
            onFramingHint = viewModel::onFramingHint,
            listening = voiceState is VoiceState.Listening,
            onGallery = viewModel::pedirGaleria,
            onMic = viewModel::onMicTapped,
        )
    }
}

/**
 * Pantalla 1: Vista previa de cámara a pantalla completa con botón VOLVER AL MENÚ, guía de encuadre y obturador.
 */
@Composable
private fun DocumentCameraScreen(
    uiState: DocumentReaderUiState,
    hasCameraPermission: Boolean,
    onReadyCamera: (ImageCapture?) -> Unit,
    onBack: () -> Unit,
    onCaptureClick: () -> Unit,
    onFramingHint: (FramingHint) -> Unit = {},
    listening: Boolean = false,
    onGallery: () -> Unit = {},
    onMic: () -> Unit = {},
) {
    // Analiza la vista previa para guiar por voz hacia dónde mover el teléfono; se apaga al salir de la cámara.
    val framingAnalyzer = remember { TextFramingAnalyzer(onFramingHint) }
    DisposableEffect(framingAnalyzer) { onDispose { framingAnalyzer.close() } }

    Box(modifier = Modifier.fillMaxSize().background(CecapiBackground)) {
        CameraViewfinder(
            hasPermission = hasCameraPermission,
            onReady = onReadyCamera,
            modifier = Modifier.fillMaxSize(),
            analyzer = framingAnalyzer,
        )

        if (hasCameraPermission) {
            FramingGuide(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.85f)
                    .aspectRatio(3f / 4f),
            )
        }

        // Las instrucciones ("apunta la cámara", "toma la foto") solo se dan por voz desde el ViewModel.
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VolverAlMenuButton(onClick = onBack)

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopAction(
                    icon = Icons.Filled.PhotoLibrary,
                    label = "Mis fotos",
                    help = "Mis fotos. Abre el selector de fotos de tu teléfono para que elijas una imagen que ya tienes. " +
                        "La aplicación solo ve la foto que tú elijas. También puedes decir: elige una foto.",
                    onClick = onGallery,
                )
                CaptureButton(
                    processing = uiState.isProcessing,
                    enabled = !uiState.isProcessing,
                    help = "Botón de captura. Tócalo para tomar la foto del documento o di toma la foto.",
                    onClick = onCaptureClick,
                    size = 136.dp,
                )
                // Mismo ancho que "Mis fotos" para que el obturador quede centrado; muestra si ya está escuchando.
                TopAction(
                    icon = if (listening) Icons.Filled.MicOff else Icons.Filled.Mic,
                    label = if (listening) "Escuchando" else "Hablar",
                    help = "Micrófono. Tócalo para hablar: di toma la foto, o elige una foto.",
                    tint = if (listening) CecapiAccent else CecapiTextPrimary,
                    onClick = onMic,
                )
            }
        }
    }
}

/**
 * Pantalla 2: Interfaz futurista CECAPI de lectura. El botón VOLVER AL MENÚ PRINCIPAL está directamente
 * debajo del botón de TOMAR OTRA FOTO en un tamaño GIGANTE.
 */
@Composable
private fun DocumentReadoutControlScreen(
    uiState: DocumentReaderUiState,
    onBack: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRepeat: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSavePhoto: () -> Unit,
    onRetakePhoto: () -> Unit,
    onOtrasAcciones: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CecapiBackground)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Tarjeta 1 Futurista: PAUSAR / REANUDAR. Explicar "pausar" ya detiene la lectura, pero el botón
        // sigue mostrando PAUSAR hasta el segundo toque para que no cambie bajo el dedo.
        val mostrarPausar = uiState.estaLeyendo || uiState.botonArmado == BotonLector.PAUSAR
        val pauseTint = CecapiAccent
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(CecapiSurfaceElevated)
                .border(2.dp, pauseTint, RoundedCornerShape(24.dp))
                .semantics {
                    role = Role.Button
                    contentDescription = if (mostrarPausar) "Pausar lectura" else "Reanudar lectura"
                }
                .clickable { if (mostrarPausar) onPause() else onResume() },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(pauseTint.copy(alpha = 0.2f))
                        .border(2.dp, pauseTint, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (mostrarPausar) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = pauseTint,
                        modifier = Modifier.size(34.dp),
                    )
                }
                Spacer(modifier = Modifier.size(16.dp))
                Text(
                    text = if (mostrarPausar) "PAUSAR LECTURA" else "REANUDAR LECTURA",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = CecapiTextPrimary,
                )
            }
        }

        // Tarjeta 2 Futurista: REPETIR DESDE EL INICIO
        val repeatTint = ModuleLearningAccent
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(CecapiSurfaceElevated)
                .border(2.dp, repeatTint, RoundedCornerShape(24.dp))
                .semantics {
                    role = Role.Button
                    contentDescription = "Repetir lectura desde el inicio"
                }
                .clickable { onRepeat() },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(repeatTint.copy(alpha = 0.2f))
                        .border(2.dp, repeatTint, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Replay,
                        contentDescription = null,
                        tint = repeatTint,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Spacer(modifier = Modifier.size(16.dp))
                Text(
                    text = "REPETIR LECTURA",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = CecapiTextPrimary,
                )
            }
        }

        // Tarjeta 3 Futurista: NAVEGACIÓN DE PÁRRAFOS (ANTERIOR / SIGUIENTE)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val navBorder = CecapiBorder
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(CecapiSurfaceElevated)
                    .border(2.dp, navBorder, RoundedCornerShape(24.dp))
                    .semantics {
                        role = Role.Button
                        contentDescription = "Ir al párrafo anterior"
                    }
                    .clickable { onPrevious() },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = null,
                        tint = CecapiAccent,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "ANTERIOR",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = CecapiTextPrimary,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(CecapiSurfaceElevated)
                    .border(2.dp, navBorder, RoundedCornerShape(24.dp))
                    .semantics {
                        role = Role.Button
                        contentDescription = "Ir al párrafo siguiente"
                    }
                    .clickable { onNext() },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(12.dp),
                ) {
                    Text(
                        text = "SIGUIENTE",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = CecapiTextPrimary,
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = null,
                        tint = CecapiAccent,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }

        // Tarjeta 4 Futurista: GUARDAR FOTO en la galería
        val saveTint = Color(0xFF22C55E)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(CecapiSurfaceElevated)
                .border(2.dp, saveTint, RoundedCornerShape(24.dp))
                .semantics {
                    role = Role.Button
                    contentDescription = "Guardar foto en la galería"
                }
                .clickable { onSavePhoto() },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(saveTint.copy(alpha = 0.2f))
                        .border(2.dp, saveTint, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Save,
                        contentDescription = null,
                        tint = saveTint,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(modifier = Modifier.size(16.dp))
                Text(
                    text = "GUARDAR FOTO",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = CecapiTextPrimary,
                )
            }
        }

        // Tarjeta 5 Futurista: TOMAR OTRA FOTO
        val cameraTint = ModuleCameraAccent
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(CecapiSurfaceElevated)
                .border(2.dp, cameraTint, RoundedCornerShape(24.dp))
                .semantics {
                    role = Role.Button
                    contentDescription = "Tomar otra foto"
                }
                .clickable { onRetakePhoto() },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(cameraTint.copy(alpha = 0.2f))
                        .border(2.dp, cameraTint, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = cameraTint,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(modifier = Modifier.size(16.dp))
                Text(
                    text = "TOMAR OTRA FOTO",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = CecapiTextPrimary,
                )
            }
        }

        // Con la misma foto se puede hacer otra cosa: describirla, leer un código QR o traducirla.
        BigBtn(
            icon = Icons.Filled.Apps,
            label = "Otras acciones con esta foto",
            variant = BigBtnVariant.Accent,
            size = BigBtnSize.Secondary,
            onClick = onOtrasAcciones,
        )
        VolverAlMenuButton(onClick = onBack)
    }
}

/**
 * Botón GIGANTE para volver al menú principal, compartido por la cámara y la pantalla de lectura.
 */
@Composable
private fun VolverAlMenuButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val menuTint = Color(0xFFEF4444)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(82.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(CecapiSurfaceElevated)
            .border(2.5.dp, menuTint, RoundedCornerShape(24.dp))
            .semantics {
                role = Role.Button
                contentDescription = "Volver al menú principal"
            }
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(menuTint.copy(alpha = 0.2f))
                    .border(2.dp, menuTint, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = null,
                    tint = menuTint,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(modifier = Modifier.size(14.dp))
            Text(
                text = "VOLVER AL MENÚ PRINCIPAL",
                fontSize = 21.sp,
                fontWeight = FontWeight.ExtraBold,
                color = CecapiTextPrimary,
            )
        }
    }
}

@ComposePreview(showBackground = true, name = "Futuristic Document Readout Control Screen")
@Composable
private fun DocumentReadoutControlScreenPreview() {
    MaterialTheme {
        DocumentReadoutControlScreen(
            uiState = DocumentReaderUiState(
                recognizedText = "Texto de ejemplo para la vista previa.",
                parrafos = listOf("Este es el primer párrafo del documento leído.", "Este es el segundo párrafo del documento."),
                parrafoActual = 0,
                estaLeyendo = true,
            ),
            onBack = {},
            onPause = {},
            onResume = {},
            onRepeat = {},
            onPrevious = {},
            onNext = {},
            onSavePhoto = {},
            onRetakePhoto = {},
        )
    }
}

/**
 * Pantalla 2 de la cámara: la foto ya está tomada y se elige qué hacer con ella. Arriba, el contenido que se
 * desplaza (la foto, lo que se está haciendo o el último resultado y las acciones); abajo, el micrófono.
 */
@Composable
private fun PanelAccionesFoto(
    uiState: DocumentReaderUiState,
    listening: Boolean,
    onBack: () -> Unit,
    onLeerTexto: () -> Unit,
    onEnfrente: () -> Unit,
    onCodigo: () -> Unit,
    onTraducir: () -> Unit,
    onGuardar: () -> Unit,
    onOtraFoto: () -> Unit,
    onMic: () -> Unit,
    onMicDoubleTap: () -> Unit,
    onCommands: () -> Unit,
) {
    val ocupado = uiState.trabajando != null
    PantallaConMicrofono {
        ColumnaDesplazable(descripcionBarra = "Barra de desplazamiento de las acciones de la foto") {
            ScreenTopBar(eyebrow = "CÁMARA", title = "¿Qué hacemos con la foto?", onBack = onBack, onCommands = onCommands)
            uiState.photoPath?.let { ruta ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(20.dp)),
                ) {
                    CapturedPhotoPreview(path = ruta, modifier = Modifier.fillMaxSize())
                }
            }
            uiState.trabajando?.let { TarjetaLectura(titulo = it, cuerpo = "", icono = Icons.Filled.HourglassTop, section = Sections.Camera) }
            uiState.resultado?.let { TarjetaLectura(titulo = it.titulo, cuerpo = it.texto, icono = Icons.Filled.CheckCircle, section = Sections.Camera) }
            BigBtn(icon = Icons.Filled.TextSnippet, label = "Leer texto", section = Sections.Camera, enabled = !ocupado, onClick = onLeerTexto)
            BigBtn(icon = Icons.Filled.Visibility, label = "Qué hay enfrente", section = Sections.Camera, enabled = !ocupado, onClick = onEnfrente)
            BigBtn(icon = Icons.Filled.QrCodeScanner, label = "Leer código QR", section = Sections.Camera, enabled = !ocupado, onClick = onCodigo)
            BigBtn(icon = Icons.Filled.Translate, label = "Traducir al español", section = Sections.Camera, enabled = !ocupado, onClick = onTraducir)
            BigBtn(icon = Icons.Filled.Save, label = "Guardar foto", section = Sections.Camera, onClick = onGuardar)
            BigBtn(icon = Icons.Filled.AddAPhoto, label = "Tomar otra foto", variant = BigBtnVariant.Accent, onClick = onOtraFoto)
        }
        MicPad(
            listening = listening,
            onDoubleTap = onMicDoubleTap,
            hint = if (listening) "Escuchando…" else "Di leer, qué hay enfrente, QR o traducir",
            onClick = onMic,
        )
    }
}
