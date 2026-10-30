package com.cecapi.app.feature.modulo6_aprendizaje

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SportsEsports
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.unit.dp
import com.cecapi.app.core.theme.CecapiError
import com.cecapi.app.core.theme.CecapiSuccess
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.theme.ModuleSection
import com.cecapi.app.core.theme.Sections
import com.cecapi.app.core.ui.BigBtn
import com.cecapi.app.core.ui.BigBtnSize
import com.cecapi.app.core.ui.BigBtnVariant
import com.cecapi.app.core.ui.CameraViewfinder
import com.cecapi.app.core.ui.CapturedPhotoPreview
import com.cecapi.app.core.ui.ColumnaDesplazable
import com.cecapi.app.core.ui.MicPad
import com.cecapi.app.core.ui.PantallaConMicrofono
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.ui.TarjetaLectura
import com.cecapi.app.core.ui.capturePhoto
import com.cecapi.app.core.voice.VoiceState

/** El juego "Busca y encuentra": un reto por voz, la cámara para enseñar la cosa y un veredicto. */
@Composable
fun JuegoCamaraScreen(
    onBack: () -> Unit,
    viewModel: JuegoCamaraViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val estado by viewModel.estado.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val escuchando = voiceState is VoiceState.Listening

    var tienePermisoDeCamara by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val pedirCamara = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tienePermisoDeCamara = it }
    val pedirMicrofono = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) viewModel.onMicTapped() }
    LaunchedEffect(Unit) { if (!tienePermisoDeCamara) pedirCamara.launch(Manifest.permission.CAMERA) }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val tomarFoto: () -> Unit = {
        val captura = imageCapture
        if (captura == null) {
            viewModel.onFalloDeCaptura()
        } else {
            captura.capturePhoto(context = context, folder = "cecapi_juego", onFailed = viewModel::onFalloDeCaptura) { uri, ruta ->
                viewModel.onFoto(uri, ruta)
            }
        }
    }
    LaunchedEffect(Unit) { viewModel.capturas.collect { tomarFoto() } }
    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }

    PantallaConMicrofono {
        ColumnaDesplazable(descripcionBarra = "Barra de desplazamiento del juego") {
            ScreenTopBar(
                eyebrow = "JUEGOS",
                title = "Busca y encuentra",
                onBack = onBack,
                onCommands = viewModel::onCommandsRequested,
            )

            TarjetaLectura(
                titulo = "Nivel ${estado.nivel} · ${estado.aciertosEnNivel} de ${RetosCamara.RETOS_POR_NIVEL}",
                cuerpo = estado.reto.frase,
                icono = Icons.Filled.SportsEsports,
                section = Sections.Activities,
                lectura = "Nivel ${estado.nivel}, ${estado.aciertosEnNivel} de ${RetosCamara.RETOS_POR_NIVEL} aciertos. ${estado.reto.frase}",
            )

            when (estado.fase) {
                FaseJuego.BUSCANDO -> {
                    if (tienePermisoDeCamara) {
                        Box(modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(20.dp))) {
                            CameraViewfinder(hasPermission = true, onReady = { imageCapture = it }, modifier = Modifier.fillMaxSize())
                        }
                    } else {
                        TarjetaLectura(
                            titulo = "Falta el permiso de la cámara",
                            cuerpo = "Actívalo en los ajustes de la aplicación para jugar.",
                            colorCuerpo = CecapiError,
                        )
                    }
                    BigBtn(
                        icon = Icons.Filled.CameraAlt,
                        label = "Ya lo tengo",
                        sub = "Toma la foto",
                        variant = BigBtnVariant.Accent,
                        size = BigBtnSize.Hero,
                        enabled = tienePermisoDeCamara,
                        motivoDeshabilitado = "Primero da el permiso de la cámara.",
                        onClick = viewModel::pedirFoto,
                    )
                    BigBtn(
                        icon = Icons.Filled.Replay,
                        label = "Repetir el reto",
                        section = Sections.Activities,
                        size = BigBtnSize.Secondary,
                        onClick = viewModel::repetirReto,
                    )
                }
                FaseJuego.ANALIZANDO -> {
                    estado.fotoPath?.let { FotoDelJuego(it) }
                    TarjetaLectura(
                        titulo = "Mirando tu foto…",
                        cuerpo = "",
                        icono = Icons.Filled.HourglassTop,
                        section = Sections.Activities,
                    )
                }
                FaseJuego.RESULTADO -> {
                    estado.fotoPath?.let { FotoDelJuego(it) }
                    estado.veredicto?.let { veredicto ->
                        TarjetaLectura(
                            titulo = if (veredicto.acerto) "¡Lo lograste!" else "Todavía no",
                            cuerpo = veredicto.mensaje,
                            icono = if (veredicto.acerto) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                            section = ModuleSection(
                                color = if (veredicto.acerto) CecapiSuccess else CecapiError,
                                bg = (if (veredicto.acerto) CecapiSuccess else CecapiError).copy(alpha = 0.15f),
                            ),
                            colorCuerpo = MaterialTheme.colorScheme.onBackground,
                            resaltada = true,
                        )
                    }
                    BigBtn(
                        icon = Icons.Filled.SkipNext,
                        label = "Siguiente reto",
                        variant = BigBtnVariant.Accent,
                        size = BigBtnSize.Hero,
                        onClick = viewModel::siguienteReto,
                    )
                }
            }

            Text(
                "Aciertos en esta sesión: ${estado.aciertosTotales}. Es práctica: todavía no suma a la clasificación.",
                style = MaterialTheme.typography.bodyMedium,
                color = CecapiTextMuted,
            )
        }

        MicPad(
            listening = escuchando,
            onDoubleTap = viewModel::onMicDoubleTap,
            hint = if (escuchando) "Escuchando…" else "Di ya lo tengo, o toca aquí",
            onClick = {
                val permiso = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (permiso) viewModel.onMicTapped() else pedirMicrofono.launch(Manifest.permission.RECORD_AUDIO)
            },
        )
    }
}

@Composable
private fun FotoDelJuego(ruta: String) {
    Box(modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(20.dp))) {
        CapturedPhotoPreview(path = ruta, modifier = Modifier.fillMaxSize())
    }
}
