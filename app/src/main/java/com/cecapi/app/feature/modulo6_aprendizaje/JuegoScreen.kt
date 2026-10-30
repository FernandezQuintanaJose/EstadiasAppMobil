package com.cecapi.app.feature.modulo6_aprendizaje

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cecapi.app.core.theme.CecapiError
import com.cecapi.app.core.theme.CecapiSuccess
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.theme.ModuleSection
import com.cecapi.app.core.theme.Sections
import com.cecapi.app.core.ui.BigBtn
import com.cecapi.app.core.ui.BigBtnSize
import com.cecapi.app.core.ui.BigBtnVariant
import com.cecapi.app.core.ui.ColumnaDesplazable
import com.cecapi.app.core.ui.MicPad
import com.cecapi.app.core.ui.PantallaConMicrofono
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.ui.TarjetaLectura
import com.cecapi.app.core.voice.VoiceState

/** Los juegos que acompaña el teléfono (ver [JuegoTipo]); la misma pantalla dividida que el resto de la app. */
@Composable
fun JuegoScreen(
    onBack: () -> Unit,
    viewModel: JuegoViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val estado by viewModel.estado.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val escuchando = voiceState is VoiceState.Listening
    val pedirMicrofono = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) viewModel.onMicTapped() }

    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }
    // Salir de la app (inicio, bloqueo) apaga sonidos, ritmo y relojes.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observador = LifecycleEventObserver { _, evento -> if (evento == Lifecycle.Event.ON_STOP) viewModel.onAppStopped() }
        lifecycleOwner.lifecycle.addObserver(observador)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observador) }
    }

    PantallaConMicrofono {
        ColumnaDesplazable(descripcionBarra = "Barra de desplazamiento del juego") {
            ScreenTopBar(eyebrow = "JUEGOS", title = estado.tipo.titulo, onBack = onBack, onCommands = viewModel::onCommandsRequested)
            TarjetaLectura(
                titulo = "Cómo se juega",
                cuerpo = estado.tipo.comoSeJuega,
                icono = Icons.Filled.Groups,
                section = Sections.Activities,
                mostrarCuerpo = false,
            )
            when (estado.tipo) {
                JuegoTipo.CAZA_SONIDO -> CazaSonido(estado, viewModel)
                JuegoTipo.MEMORIA_SONIDOS -> Memoria(estado, viewModel)
                JuegoTipo.MARCADOR -> PartidoConMarcador(estado, viewModel)
                JuegoTipo.TURNOS, JuegoTipo.ESTACIONES -> TurnosOEstaciones(estado, viewModel)
                JuegoTipo.RITMO -> Ritmo(estado, viewModel)
                JuegoTipo.GUIA -> Guia(estado, viewModel)
            }
        }

        MicPad(
            listening = escuchando,
            onDoubleTap = viewModel::onMicDoubleTap,
            hint = if (escuchando) "Escuchando…" else "Toca aquí para hablar",
            onClick = {
                val permiso = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (permiso) viewModel.onMicTapped() else pedirMicrofono.launch(Manifest.permission.RECORD_AUDIO)
            },
        )
    }
}

@Composable
private fun CazaSonido(estado: JuegoUiState, vm: JuegoViewModel) {
    when (estado.cazaFase) {
        CazaFase.PREPARADO, CazaFase.ENCONTRADO -> {
            if (estado.cazaFase == CazaFase.ENCONTRADO) {
                Resultado(bien = true, titulo = "¡Lo encontraste!", texto = "En ${tiempoEnPalabras(estado.segundos)}.")
            }
            estado.mejorTiempo?.let {
                TarjetaLectura(titulo = "Mejor tiempo", cuerpo = tiempoEnPalabras(it), icono = Icons.Filled.EmojiEvents, section = Sections.Activities)
            }
            BigBtn(
                icon = Icons.Filled.PlayArrow,
                label = if (estado.cazaFase == CazaFase.ENCONTRADO) "Jugar otra vez" else "Empezar",
                sub = "Suena en 10 segundos",
                variant = BigBtnVariant.Accent,
                size = BigBtnSize.Hero,
                onClick = vm::empezarCaza,
            )
        }
        CazaFase.CUENTA_REGRESIVA -> TarjetaLectura(
            titulo = "Escóndelo ya: ${estado.cuentaRegresiva}",
            cuerpo = "Empieza a sonar en ${estado.cuentaRegresiva} segundos.",
            icono = Icons.Filled.Timer,
            section = Sections.Activities,
            resaltada = true,
        )
        CazaFase.SONANDO -> {
            TarjetaLectura(
                titulo = "Buscando: ${tiempoEnReloj(estado.segundos)}",
                cuerpo = "Guíate por el oído. Cuando lo tengas en la mano, toca el botón.",
                icono = Icons.Filled.Hearing,
                section = Sections.Activities,
                resaltada = true,
            )
            BigBtn(icon = Icons.Filled.CheckCircle, label = "¡Lo encontré!", variant = BigBtnVariant.Success, size = BigBtnSize.Hero, onClick = vm::encontrado)
        }
    }
}

@Composable
private fun Memoria(estado: JuegoUiState, vm: JuegoViewModel) {
    if (estado.terminada) {
        Resultado(bien = true, titulo = "Terminaste", texto = "Acertaste ${estado.aciertos} de ${estado.preguntas.size}.")
        BigBtn(icon = Icons.Filled.Replay, label = "Otra ronda", variant = BigBtnVariant.Accent, size = BigBtnSize.Hero, onClick = vm::nuevaRonda)
        return
    }
    val pregunta = estado.pregunta ?: return
    TarjetaLectura(
        titulo = "Sonido ${estado.indice + 1} de ${estado.preguntas.size}",
        cuerpo = "${estado.aciertos} aciertos. ¿Qué sonido es?",
        icono = Icons.Filled.MusicNote,
        section = Sections.Activities,
    )
    estado.elegida?.let { elegida ->
        val bien = elegida == pregunta.sonido
        Resultado(bien = bien, titulo = if (bien) "¡Correcto!" else "No", texto = "Era ${pregunta.sonido.nombre.lowercase()}.")
    }
    pregunta.opciones.chunked(2).forEach { fila ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            fila.forEach { opcion ->
                val esLaElegida = estado.elegida == opcion
                val esLaCorrecta = estado.elegida != null && opcion == pregunta.sonido
                BigBtn(
                    icon = Icons.Filled.MusicNote,
                    label = opcion.nombre,
                    section = if (esLaElegida || esLaCorrecta) null else Sections.Activities,
                    variant = when {
                        esLaCorrecta -> BigBtnVariant.Success
                        esLaElegida -> BigBtnVariant.Danger
                        else -> BigBtnVariant.Neutral
                    },
                    half = true,
                    size = BigBtnSize.Secondary,
                    enabled = estado.elegida == null,
                    motivoDeshabilitado = "Ya respondiste. Toca siguiente.",
                    onClick = { vm.responderMemoria(opcion) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    BigBtn(
        icon = if (estado.elegida == null) Icons.Filled.VolumeUp else Icons.Filled.SkipNext,
        label = if (estado.elegida == null) "Escuchar de nuevo" else "Siguiente",
        variant = BigBtnVariant.Accent,
        onClick = if (estado.elegida == null) vm::reproducirPregunta else vm::siguientePregunta,
    )
}

@Composable
private fun PartidoConMarcador(estado: JuegoUiState, vm: JuegoViewModel) {
    TarjetaLectura(
        titulo = "${estado.marcador.equipo1}  —  ${estado.marcador.equipo2}",
        cuerpo = "${estado.marcador.enPalabras()} Tiempo: ${tiempoEnReloj(estado.restante)}.",
        icono = Icons.Filled.EmojiEvents,
        section = Sections.Activities,
        resaltada = true,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        BigBtn(icon = Icons.Filled.Add, label = "Punto equipo 1", variant = BigBtnVariant.Accent, half = true, onClick = { vm.sumarPunto(1) }, modifier = Modifier.weight(1f))
        BigBtn(icon = Icons.Filled.Add, label = "Punto equipo 2", variant = BigBtnVariant.Accent, half = true, onClick = { vm.sumarPunto(2) }, modifier = Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        BigBtn(icon = Icons.Filled.Remove, label = "Quitar al 1", section = Sections.Activities, half = true, size = BigBtnSize.Secondary, onClick = { vm.restarPunto(1) }, modifier = Modifier.weight(1f))
        BigBtn(icon = Icons.Filled.Remove, label = "Quitar al 2", section = Sections.Activities, half = true, size = BigBtnSize.Secondary, onClick = { vm.restarPunto(2) }, modifier = Modifier.weight(1f))
    }
    Reloj(estado, vm, opciones = listOf(300 to "5 min", 600 to "10 min", 720 to "12 min"))
    BigBtn(icon = Icons.Filled.Replay, label = "Partido nuevo", section = Sections.Activities, size = BigBtnSize.Secondary, onClick = vm::reiniciarPartido)
}

@Composable
private fun TurnosOEstaciones(estado: JuegoUiState, vm: JuegoViewModel) {
    val esEstacion = estado.tipo == JuegoTipo.ESTACIONES
    TarjetaLectura(
        titulo = if (esEstacion) "Estación ${estado.actual} de ${estado.participantes}" else "Turno del jugador ${estado.actual}",
        cuerpo = "Quedan ${tiempoEnPalabras(estado.restante)}.",
        icono = Icons.Filled.Timer,
        section = Sections.Activities,
        resaltada = estado.corriendo,
    )
    BigBtn(
        icon = Icons.Filled.SkipNext,
        label = if (esEstacion) "Cambiar de estación" else "Siguiente turno",
        variant = BigBtnVariant.Accent,
        size = BigBtnSize.Hero,
        onClick = { vm.siguienteTurno(manual = true) },
    )
    Reloj(estado, vm, opciones = if (esEstacion) listOf(120 to "2 min", 300 to "5 min", 600 to "10 min") else listOf(30 to "30 s", 60 to "1 min", 120 to "2 min"))
    Text(if (esEstacion) "¿CUÁNTAS ESTACIONES?" else "¿CUÁNTOS JUEGAN?", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        (if (esEstacion) listOf(3, 4, 6) else listOf(2, 3, 4)).forEach { n ->
            BigBtn(
                icon = Icons.Filled.Groups,
                label = "$n",
                variant = if (estado.participantes == n) BigBtnVariant.Accent else BigBtnVariant.Neutral,
                section = if (estado.participantes == n) null else Sections.Activities,
                half = true,
                size = BigBtnSize.Secondary,
                onClick = { vm.elegirParticipantes(n) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Empezar o pausar el reloj y elegir cuánto dura. */
@Composable
private fun Reloj(estado: JuegoUiState, vm: JuegoViewModel, opciones: List<Pair<Int, String>>) {
    BigBtn(
        icon = if (estado.corriendo) Icons.Filled.Pause else Icons.Filled.PlayArrow,
        label = if (estado.corriendo) "Pausar (${tiempoEnReloj(estado.restante)})" else "Empezar el tiempo (${tiempoEnReloj(estado.restante)})",
        section = Sections.Activities,
        onClick = if (estado.corriendo) vm::pausar else vm::iniciarReloj,
    )
    Text("DURACIÓN", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        opciones.forEach { (segundos, etiqueta) ->
            BigBtn(
                icon = Icons.Filled.Timer,
                label = etiqueta,
                variant = if (estado.duracion == segundos) BigBtnVariant.Accent else BigBtnVariant.Neutral,
                section = if (estado.duracion == segundos) null else Sections.Activities,
                half = true,
                size = BigBtnSize.Secondary,
                onClick = { vm.elegirDuracion(segundos) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Ritmo(estado: JuegoUiState, vm: JuegoViewModel) {
    TarjetaLectura(
        titulo = "${estado.bpm} por minuto",
        cuerpo = if (estado.ritmoSonando) "Sonando." else "Detenido.",
        icono = Icons.Filled.MusicNote,
        section = Sections.Activities,
        resaltada = estado.ritmoSonando,
    )
    BigBtn(
        icon = if (estado.ritmoSonando) Icons.Filled.Stop else Icons.Filled.PlayArrow,
        label = if (estado.ritmoSonando) "Parar" else "Empezar",
        variant = if (estado.ritmoSonando) BigBtnVariant.Danger else BigBtnVariant.Accent,
        size = BigBtnSize.Hero,
        onClick = if (estado.ritmoSonando) vm::detenerRitmo else vm::empezarRitmo,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        BigBtn(icon = Icons.Filled.Remove, label = "Más lento", section = Sections.Activities, half = true, onClick = { vm.cambiarRitmo(-10) }, modifier = Modifier.weight(1f))
        BigBtn(icon = Icons.Filled.Add, label = "Más rápido", section = Sections.Activities, half = true, onClick = { vm.cambiarRitmo(+10) }, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Guia(estado: JuegoUiState, vm: JuegoViewModel) {
    estado.ultimaIndicacion?.let {
        TarjetaLectura(titulo = it, cuerpo = "Última indicación.", icono = Icons.Filled.VolumeUp, section = Sections.Activities, resaltada = true)
    }
    BigBtn(icon = Icons.Filled.ArrowUpward, label = "Adelante", variant = BigBtnVariant.Accent, size = BigBtnSize.Hero, onClick = { vm.guiar("Adelante") })
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        BigBtn(icon = Icons.AutoMirrored.Filled.ArrowBack, label = "Izquierda", section = Sections.Activities, half = true, onClick = { vm.guiar("Izquierda") }, modifier = Modifier.weight(1f))
        BigBtn(icon = Icons.AutoMirrored.Filled.ArrowForward, label = "Derecha", section = Sections.Activities, half = true, onClick = { vm.guiar("Derecha") }, modifier = Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        BigBtn(icon = Icons.Filled.Stop, label = "Alto", variant = BigBtnVariant.Danger, half = true, onClick = { vm.guiar("Alto") }, modifier = Modifier.weight(1f))
        BigBtn(icon = Icons.Filled.ArrowDownward, label = "Atrás", section = Sections.Activities, half = true, onClick = { vm.guiar("Atrás") }, modifier = Modifier.weight(1f))
    }
}

/** Una tarjeta de resultado, verde si salió bien y roja si no. */
@Composable
private fun Resultado(bien: Boolean, titulo: String, texto: String) {
    TarjetaLectura(
        titulo = titulo,
        cuerpo = texto,
        icono = if (bien) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
        section = ModuleSection(
            color = if (bien) CecapiSuccess else CecapiError,
            bg = (if (bien) CecapiSuccess else CecapiError).copy(alpha = 0.15f),
        ),
        colorCuerpo = MaterialTheme.colorScheme.onBackground,
        resaltada = true,
    )
}
