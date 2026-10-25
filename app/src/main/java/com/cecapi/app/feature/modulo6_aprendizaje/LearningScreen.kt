package com.cecapi.app.feature.modulo6_aprendizaje

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiError
import com.cecapi.app.core.theme.CecapiSuccess
import com.cecapi.app.core.theme.CecapiSurface
import com.cecapi.app.core.theme.CecapiSurfaceElevated
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.ui.TopAction
import com.cecapi.app.core.ui.VoiceCaptionBubble
import com.cecapi.app.core.ui.VoiceMicButton
import com.cecapi.app.core.ui.voiceHint
import com.cecapi.app.core.voice.VoiceState

@Composable
fun LearningScreen(
    onBack: () -> Unit = {},
    viewModel: LearningViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val voiceState by viewModel.voiceState.collectAsState()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }

    // Leaving the app (home button, screen lock) must stop the sound and the vibration.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) viewModel.onAppStopped() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.onMicTapped() }

    val onMicClick: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.onMicTapped() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTopBar(
            eyebrow = "MENÚ",
            title = "Actividades",
            onBack = onBack,
            onCommands = viewModel::onCommandsRequested,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ChoiceButton(
                label = "Sonidos",
                selected = state.mode == ActivityMode.AUDIO,
                help = "Sonidos. Practica con sonidos que vienen de un lado. También puedes decir: sonidos.",
                onClick = { viewModel.setMode(ActivityMode.AUDIO) },
                modifier = Modifier.weight(1f),
            )
            ChoiceButton(
                label = "Vibración",
                selected = state.mode == ActivityMode.VIBRATION,
                help = "Vibración. Practica con patrones de vibración cortos, largos o mixtos. También puedes decir: vibración.",
                onClick = { viewModel.setMode(ActivityMode.VIBRATION) },
                modifier = Modifier.weight(1f),
            )
        }

        if (state.mode == ActivityMode.AUDIO) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                (1..3).forEach { level ->
                    ChoiceButton(
                        label = "Nivel $level",
                        selected = state.level == level,
                        help = "Nivel $level. También puedes decir: nivel " + listOf("uno", "dos", "tres")[level - 1] + ".",
                        onClick = { viewModel.setLevel(level) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        val item = state.current
        if (item == null) {
            Text(
                "Preparando los ejercicios…",
                style = MaterialTheme.typography.bodyLarge,
                color = CecapiTextMuted,
                modifier = Modifier.padding(8.dp),
            )
        } else if (state.finished) {
            val shape = RoundedCornerShape(24.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(CecapiSurface)
                    .border(2.dp, CecapiAccent.copy(alpha = 0.5f), shape)
                    .padding(20.dp)
                    .semantics { contentDescription = "Resultado. Acertaste ${state.correctCount} de ${state.items.size}." },
            ) {
                Text("Resultado", style = MaterialTheme.typography.bodyMedium, color = CecapiTextMuted)
                Text(
                    "Acertaste ${state.correctCount} de ${state.items.size}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            VoiceCaptionBubble(
                text = (voiceState as? VoiceState.Speaking)?.text ?: state.finalMessage ?: "",
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopAction(
                    icon = Icons.Filled.Replay,
                    label = "Repetir",
                    help = "Repetir. Vuelve a empezar la actividad desde el primer ejercicio. También puedes decir: repetir.",
                    onClick = viewModel::restartActivity,
                )
                VoiceMicButton(
                    isListening = voiceState is VoiceState.Listening,
                    onDoubleTap = viewModel::onMicDoubleTap,
                    onClick = onMicClick,
                )
                TopAction(
                    icon = Icons.Filled.Close,
                    label = "Salir",
                    help = "Salir. Vuelve al menú. También puedes decir: salir.",
                    onClick = viewModel::leave,
                )
            }
        } else {
            val shape = RoundedCornerShape(24.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(CecapiSurface)
                    .border(2.dp, CecapiAccent.copy(alpha = 0.5f), shape)
                    .padding(20.dp),
            ) {
                Text(
                    "Ejercicio ${state.index + 1} de ${state.items.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CecapiTextMuted,
                )
                Text(item.title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
            }

            VoiceCaptionBubble(
                text = when {
                    state.busy -> "Escucha con atención…"
                    state.feedback != null -> state.feedback ?: ""
                    else -> (voiceState as? VoiceState.Speaking)?.text ?: item.instruction
                },
            )

            state.correct?.let { correct ->
                Text(
                    if (correct) "Correcto" else "Incorrecto",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (correct) CecapiSuccess else CecapiError,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopAction(
                    icon = Icons.Filled.Replay,
                    label = "Repetir",
                    help = "Repetir. Vuelve a reproducir el sonido o la vibración. También puedes decir: repite.",
                    onClick = viewModel::repeat,
                )
                VoiceMicButton(
                    isListening = voiceState is VoiceState.Listening,
                    onDoubleTap = viewModel::onMicDoubleTap,
                    onClick = onMicClick,
                )
                TopAction(
                    icon = Icons.Filled.SkipNext,
                    label = "Siguiente",
                    help = "Siguiente. Pasa al siguiente ejercicio. También puedes decir: siguiente.",
                    onClick = viewModel::next,
                )
            }
        }
    }
}

@Composable
private fun ChoiceButton(
    label: String,
    selected: Boolean,
    help: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(CecapiSurfaceElevated)
            .border(
                if (selected) BorderStroke(3.dp, CecapiAccent) else BorderStroke(1.dp, CecapiTextMuted.copy(alpha = 0.4f)),
                shape,
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = if (selected) "$label, seleccionado. $help" else "$label. $help" }
            .voiceHint(help)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (selected) "$label  ✓" else label,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) CecapiAccent else MaterialTheme.colorScheme.onBackground,
        )
    }
}
