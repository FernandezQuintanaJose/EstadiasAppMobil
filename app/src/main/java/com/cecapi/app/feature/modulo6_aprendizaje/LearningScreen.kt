package com.cecapi.app.feature.modulo6_aprendizaje

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import com.cecapi.app.core.theme.ModuleSection
import com.cecapi.app.core.theme.CecapiBorder
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Vibration
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
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
import com.cecapi.app.core.theme.Sections
import com.cecapi.app.core.ui.BigBtn
import com.cecapi.app.core.ui.BigBtnSize
import com.cecapi.app.core.ui.BigBtnVariant
import com.cecapi.app.core.ui.MicPad
import com.cecapi.app.core.ui.TarjetaLectura
import com.cecapi.app.core.ui.LocalVoiceHelp
import com.cecapi.app.core.ui.PantallaConMicrofono
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.ui.TopAction
import com.cecapi.app.core.ui.voiceHint
import com.cecapi.app.core.voice.VoiceState
import kotlinx.coroutines.launch

/**
 * Primero un menú para elegir qué actividad hacer — nada de caer directo en sonidos y tener que
 * adivinar que se puede deslizar, que ni el comando de voz distinguía bien. Elegido un modo (toque
 * o diciendo su nombre), se abre su pantalla completa; "Atrás" ahí regresa a este menú, no sale de
 * Actividades de un salto.
 */
@Composable
fun LearningScreen(
    onBack: () -> Unit,
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

    // null = en el menú. Si dicen "vibración" mientras están en el menú (o en otra tarjeta), la
    // pantalla sigue a la voz igual que antes — solo que ahora parte de un menú, no de un swipe.
    var seleccion by remember { mutableStateOf<ActivityMode?>(null) }
    LaunchedEffect(state.mode) {
        if (seleccion != null && seleccion != state.mode) seleccion = state.mode
    }
    var recreativas by remember { mutableStateOf(false) }
    val listening = voiceState is VoiceState.Listening

    // Pantalla dividida (igual que el resto de la app): arriba el contenido con su desplazamiento y abajo,
    // en la zona del micrófono, todo el botón de voz.
    PantallaConMicrofono {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                recreativas -> {
                    ScreenTopBar(
                        eyebrow = "ACTIVIDADES",
                        title = "Juegos",
                        onBack = { recreativas = false },
                        onCommands = viewModel::onCommandsRequested,
                    )
                    RecreationalPage(modifier = Modifier.weight(1f))
                }
                seleccion != null -> {
                    ScreenTopBar(
                        eyebrow = "ACTIVIDADES",
                        title = if (seleccion == ActivityMode.AUDIO) "Sonidos" else "Vibración",
                        onBack = { seleccion = null },
                        onCommands = viewModel::onCommandsRequested,
                    )
                    ActivityPage(
                        pageMode = seleccion!!,
                        state = state,
                        onRepeat = viewModel::repeat,
                        onNext = viewModel::next,
                        onPrevious = viewModel::previous,
                        onAnswer = viewModel::responder,
                        onSetLevel = viewModel::setLevel,
                        onPractice = viewModel::setPractice,
                        onLeave = viewModel::leave,
                        modifier = Modifier.weight(1f),
                    )
                }
                else -> {
                    ScreenTopBar(
                        eyebrow = "MENÚ",
                        title = "Actividades",
                        onBack = onBack,
                        onCommands = viewModel::onCommandsRequested,
                    )
                    ActivityScrollArea(modifier = Modifier.weight(1f)) {
                        BigBtn(
                            icon = Icons.Filled.Hearing,
                            label = "Sonidos",
                            section = Sections.Activities,
                            onClick = { viewModel.setMode(ActivityMode.AUDIO); seleccion = ActivityMode.AUDIO },
                        )
                        BigBtn(
                            icon = Icons.Filled.Vibration,
                            label = "Vibración",
                            section = Sections.Activities,
                            onClick = { viewModel.setMode(ActivityMode.VIBRATION); seleccion = ActivityMode.VIBRATION },
                        )
                        BigBtn(
                            icon = Icons.Filled.SportsEsports,
                            label = "Juegos",
                            section = Sections.Activities,
                            onClick = { recreativas = true },
                        )
                    }
                }
            }
        }

        MicPad(
            listening = listening,
            hint = if (listening) "Escuchando…" else "Di sonidos o vibración, o toca aquí",
            onDoubleTap = viewModel::onMicDoubleTap,
            onClick = {
                val granted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) viewModel.onMicTapped()
                else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
        )
    }
}

/** Desplazamiento vertical con indicador visible y arrastrable, reutilizado en todas las vistas. */
@Composable
private fun ActivityScrollArea(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(end = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
        if (scrollState.maxValue > 0) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(20.dp)
                    .pointerInput(scrollState) {
                        detectVerticalDragGestures { change, dragAmount ->
                            change.consume()
                            val maximum = scrollState.maxValue
                            if (maximum > 0 && size.height > 0) {
                                scope.launch {
                                    scrollState.scrollBy(
                                        dragAmount * (maximum + size.height).toFloat() / size.height,
                                    )
                                }
                            }
                        }
                    }
                    .semantics { contentDescription = "Barra de desplazamiento de actividades" },
            ) {
                val visible = (size.height / (size.height + scrollState.maxValue.toFloat()))
                    .coerceIn(0f, 1f)
                val thumbHeight = (size.height * visible)
                    .coerceAtLeast(36.dp.toPx())
                    .coerceAtMost(size.height)
                val travel = (size.height - thumbHeight).coerceAtLeast(0f)
                val thumbY = (scrollState.value.toFloat() / scrollState.maxValue) * travel
                val barWidth = 6.dp.toPx()
                val barX = (size.width - barWidth) / 2f
                drawRoundRect(
                    color = CecapiTextMuted.copy(alpha = 0.25f),
                    topLeft = Offset(barX, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = CornerRadius(barWidth / 2f),
                )
                drawRoundRect(
                    color = CecapiAccent,
                    topLeft = Offset(barX, thumbY),
                    size = Size(barWidth, thumbHeight),
                    cornerRadius = CornerRadius(barWidth / 2f),
                )
            }
        }
    }
}

/**
 * La página de un ejercicio, de arriba a abajo:
 *  1. (solo sonidos) el nivel;
 *  2. el ejercicio: cuál es, qué se pide y cuánto va la ronda (se lee al tocarlo);
 *  3. el resultado de lo que se respondió, con color, apenas se responde;
 *  4. las respuestas, con íconos de dirección, para contestar tocando o diciéndolas;
 *  5. "Escuchar de nuevo", justo debajo;
 *  6. Anterior y Siguiente (Siguiente se resalta cuando ya se respondió).
 * Mientras [state] no corresponde a [pageMode] (el modelo carga un solo modo a la vez) se muestra un aviso breve.
 */
@Composable
private fun ActivityPage(
    pageMode: ActivityMode,
    state: ActivitiesUiState,
    onRepeat: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onAnswer: (String) -> Unit,
    onSetLevel: (Int) -> Unit,
    onPractice: (Boolean) -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ActivityScrollArea(modifier = modifier) {
        if (state.mode != pageMode) {
            Text("Preparando…", style = MaterialTheme.typography.bodyLarge, color = CecapiTextMuted, modifier = Modifier.padding(8.dp))
            return@ActivityScrollArea
        }

        if (state.finished) {
            ResultadoDeRonda(state = state, onRepeat = onRepeat, onLeave = onLeave)
            return@ActivityScrollArea
        }

        val item = state.current
        if (item == null) {
            Text("Preparando los ejercicios…", style = MaterialTheme.typography.bodyLarge, color = CecapiTextMuted, modifier = Modifier.padding(8.dp))
            return@ActivityScrollArea
        }

        val respondido = state.index in state.respondidos
        // Qué botón tocó la persona en este ejercicio, para pintarlo del color del resultado.
        var elegida by remember(state.index, state.mode, state.level) { mutableStateOf<String?>(null) }

        // 2. El ejercicio
        val enPractica = if (state.practice) " Modo práctica: no se guarda nada." else ""
        TarjetaLectura(
            titulo = "Ejercicio ${state.index + 1} de ${state.items.size}",
            cuerpo = item.instruction,
            icono = if (pageMode == ActivityMode.AUDIO) Icons.Filled.Hearing else Icons.Filled.Vibration,
            section = Sections.Activities,
            mostrarCuerpo = false,
            lectura = "Ejercicio ${state.index + 1} de ${state.items.size}. ${item.instruction}$enPractica",
        )
        Progreso(hechos = state.respondidos.size, total = state.items.size, aciertos = state.correctCount)

        // 3. El resultado
        state.feedback?.takeIf { respondido }?.let { mensaje ->
            val bien = state.correct == true
            TarjetaLectura(
                titulo = if (bien) "¡Correcto!" else "Incorrecto",
                cuerpo = mensaje,
                icono = if (bien) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                section = ModuleSection(
                    color = if (bien) CecapiSuccess else CecapiError,
                    bg = if (bien) CecapiSuccess.copy(alpha = 0.15f) else CecapiError.copy(alpha = 0.15f),
                ),
                colorCuerpo = MaterialTheme.colorScheme.onBackground,
                resaltada = true,
            )
        }

        // 4. Las respuestas
        val opciones = opcionesDe(item.answer)
        if (opciones.isNotEmpty()) {
            Text(
                if (pageMode == ActivityMode.AUDIO) "¿DE DÓNDE VINO?" else "¿CÓMO FUE?",
                style = MaterialTheme.typography.labelLarge,
                color = CecapiTextMuted,
            )
            opciones.chunked(2).forEach { fila ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    fila.forEach { opcion ->
                        val esLaElegida = elegida == opcion.respuesta
                        BigBtn(
                            icon = opcion.icono,
                            label = opcion.etiqueta,
                            section = if (esLaElegida) null else Sections.Activities,
                            variant = when {
                                esLaElegida && state.correct == true -> BigBtnVariant.Success
                                esLaElegida && state.correct == false -> BigBtnVariant.Danger
                                else -> BigBtnVariant.Neutral
                            },
                            half = true,
                            size = BigBtnSize.Standard,
                            enabled = !respondido && !state.busy,
                            motivoDeshabilitado = if (state.busy) "Espera a que termine el sonido." else "Ya respondiste. Toca siguiente.",
                            onClick = {
                                elegida = opcion.respuesta
                                onAnswer(opcion.respuesta)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // 5. Escuchar de nuevo, justo debajo de las respuestas
        BigBtn(
            icon = if (pageMode == ActivityMode.AUDIO) Icons.Filled.Replay else Icons.Filled.Vibration,
            label = if (pageMode == ActivityMode.AUDIO) "Escuchar de nuevo" else "Sentir de nuevo",
            variant = BigBtnVariant.Accent,
            size = BigBtnSize.Standard,
            enabled = !state.busy,
            motivoDeshabilitado = "Espera a que termine.",
            onClick = onRepeat,
        )

        // 6. Moverse entre ejercicios
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            BigBtn(
                icon = Icons.Filled.SkipPrevious,
                label = "Anterior",
                section = Sections.Activities,
                size = BigBtnSize.Secondary,
                half = true,
                enabled = state.index > 0,
                motivoDeshabilitado = "Estás en el primer ejercicio.",
                onClick = onPrevious,
                modifier = Modifier.weight(1f),
            )
            BigBtn(
                icon = Icons.Filled.SkipNext,
                label = if (state.index + 1 >= state.items.size) "Ver resultado" else "Siguiente",
                variant = if (respondido) BigBtnVariant.Accent else BigBtnVariant.Neutral,
                section = if (respondido) null else Sections.Activities,
                size = BigBtnSize.Secondary,
                half = true,
                onClick = onNext,
                modifier = Modifier.weight(1f),
            )
        }

        // El nivel va al final: lo primero que se ve es el ejercicio, no los ajustes.
        if (pageMode == ActivityMode.AUDIO) {
            Text("NIVEL", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                (1..3).forEach { level ->
                    val actual = state.level == level
                    BigBtn(
                        icon = Icons.Filled.Hearing,
                        label = "Nivel $level",
                        section = if (actual) null else Sections.Activities,
                        variant = if (actual) BigBtnVariant.Accent else BigBtnVariant.Neutral,
                        size = BigBtnSize.Secondary,
                        half = true,
                        onClick = { onSetLevel(level) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // El modo práctica no guarda nada ni cambia el nivel.
        BigBtn(
            icon = Icons.Filled.School,
            label = if (state.practice) "Salir del modo práctica" else "Modo práctica",
            sub = if (state.practice) "Ahora no se guarda nada" else "Practica sin guardar puntos",
            section = Sections.Activities,
            size = BigBtnSize.Secondary,
            onClick = { onPractice(!state.practice) },
        )
    }
}

/** La barra de avance de la ronda y cuántos aciertos lleva; se lee al tocarla. */
@Composable
private fun Progreso(hechos: Int, total: Int, aciertos: Int) {
    val texto = "Llevas $hechos de $total ejercicios y $aciertos aciertos."
    val helper = LocalVoiceHelp.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { helper?.speak(texto) }
            .semantics { contentDescription = texto },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else (hechos.toFloat() / total).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
            color = CecapiAccent,
            trackColor = CecapiBorder,
        )
        Text("$hechos de $total · $aciertos aciertos", style = MaterialTheme.typography.bodyMedium, color = CecapiTextMuted)
    }
}

/** El final de la ronda: el puntaje y qué hacer ahora. */
@Composable
private fun ResultadoDeRonda(state: ActivitiesUiState, onRepeat: () -> Unit, onLeave: () -> Unit) {
    val resultado = state.finalMessage ?: "Acertaste ${state.correctCount} de ${state.items.size}"
    TarjetaLectura(
        titulo = "Terminaste la ronda",
        cuerpo = resultado,
        icono = Icons.Filled.EmojiEvents,
        section = Sections.Activities,
        lectura = "Terminaste la ronda. $resultado",
    )
    BigBtn(icon = Icons.Filled.Replay, label = "Repetir la ronda", variant = BigBtnVariant.Accent, size = BigBtnSize.Hero, onClick = onRepeat)
    BigBtn(icon = Icons.Filled.Close, label = "Salir", section = Sections.Activities, onClick = onLeave)
}

/** Una respuesta posible: lo que dice el botón, lo que se manda como respuesta y su ícono. */
private data class OpcionDeRespuesta(val etiqueta: String, val respuesta: String, val icono: ImageVector)

private fun opcionesDe(respuesta: String): List<OpcionDeRespuesta> = when (respuesta.lowercase()) {
    "izquierda", "derecha", "centro" -> listOf(
        OpcionDeRespuesta("Izquierda", "izquierda", Icons.AutoMirrored.Filled.ArrowBack),
        OpcionDeRespuesta("Derecha", "derecha", Icons.AutoMirrored.Filled.ArrowForward),
        OpcionDeRespuesta("Los dos lados", "los dos lados", Icons.Filled.SwapHoriz),
    )
    "enfrente", "atras", "atrás" -> listOf(
        OpcionDeRespuesta("Enfrente", "enfrente", Icons.Filled.ArrowUpward),
        OpcionDeRespuesta("Atrás", "atrás", Icons.Filled.ArrowDownward),
    )
    "cerca", "lejos" -> listOf(
        OpcionDeRespuesta("Cerca", "cerca", Icons.Filled.ZoomIn),
        OpcionDeRespuesta("Lejos", "lejos", Icons.Filled.ZoomOut),
    )
    "corto", "largo", "mixto" -> listOf(
        OpcionDeRespuesta("Corto", "corto", Icons.Filled.MoreHoriz),
        OpcionDeRespuesta("Largo", "largo", Icons.Filled.HorizontalRule),
        OpcionDeRespuesta("Mixto", "mixto", Icons.Filled.Waves),
    )
    "de izquierda a derecha", "de derecha a izquierda" -> listOf(
        OpcionDeRespuesta("De izquierda a derecha", "de izquierda a derecha", Icons.AutoMirrored.Filled.ArrowForward),
        OpcionDeRespuesta("De derecha a izquierda", "de derecha a izquierda", Icons.AutoMirrored.Filled.ArrowBack),
    )
    else -> emptyList()
}

/**
 * Tercera tarjeta: ideas para divertirse y estimularse, no ejercicios calificados — primer borrador de
 * contenido para ir trabajando con el Equipo 4, no algo terminado todavía.
 */
@Composable
private fun RecreationalPage(modifier: Modifier = Modifier) {
    val helper = LocalVoiceHelp.current
    ActivityScrollArea(modifier = modifier) {
        // Un juego al azar, para quien no quiere recorrer toda la lista.
        BigBtn(
            icon = Icons.Filled.SportsEsports,
            label = "Un juego al azar",
            section = Sections.Activities,
            onClick = {
                val juego = RecreationalActivities.todas.random()
                helper?.speak("${juego.titulo}. ${juego.categoria.etiqueta}. ${juego.descripcion}")
            },
        )
        CategoriaRecreativa.entries.forEach { categoria ->
            val actividades = RecreationalActivities.todas.filter { it.categoria == categoria }
            if (actividades.isEmpty()) return@forEach
            Text(
                categoria.etiqueta.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = CecapiAccent,
                modifier = Modifier.padding(top = 4.dp),
            )
            // Cada juego es una tarjeta: se ve el título y, al tocarla, se escucha cómo se juega.
            actividades.forEach { actividad ->
                TarjetaLectura(
                    titulo = actividad.titulo,
                    cuerpo = actividad.descripcion,
                    icono = Icons.Filled.SportsEsports,
                    section = Sections.Activities,
                    mostrarCuerpo = false,
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
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) CecapiAccent else MaterialTheme.colorScheme.onBackground,
        )
    }
}
