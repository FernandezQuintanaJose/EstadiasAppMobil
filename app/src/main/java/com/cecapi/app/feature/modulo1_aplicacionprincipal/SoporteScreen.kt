package com.cecapi.app.feature.modulo1_aplicacionprincipal

import com.cecapi.app.core.theme.CecapiBorder
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Close
import com.cecapi.app.core.ui.BigBtnSize
import com.cecapi.app.core.ui.desplazableConBarra
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiSurface
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.theme.Sections
import com.cecapi.app.core.ui.BigBtn
import com.cecapi.app.core.ui.TarjetaLectura
import com.cecapi.app.core.ui.BigBtnVariant
import com.cecapi.app.core.ui.MicPad
import com.cecapi.app.core.ui.PantallaConMicrofono
import com.cecapi.app.core.ui.NoticeBanner
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.voice.VoiceState

private enum class VistaAyuda { Menu, Preguntas, Manual, Reportes, Escribir, Responder }

/** Ayuda y soporte: problemas con la app, información y ayuda, y mis reportes. Reportar y ver reportes piden cuenta. */
@Composable
fun SoporteScreen(
    onBack: () -> Unit,
    viewModel: SoporteViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val listening = voiceState is VoiceState.Listening
    var vista by remember { mutableStateOf(VistaAyuda.Menu) }
    var mensajeRespondido by remember { mutableStateOf<IncidenciaEntity?>(null) }
    val conCuenta = viewModel.hayCuenta()
    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }

    PantallaConMicrofono {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .desplazableConBarra()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScreenTopBar(
                eyebrow = "AYUDA",
                title = "Ayuda y soporte",
                onBack = { if (vista == VistaAyuda.Menu) onBack() else vista = VistaAyuda.Menu },
                onCommands = viewModel::onCommandsRequested,
            )

            when (vista) {
                VistaAyuda.Menu -> if (state.rol == RolUsuario.ADMINISTRADOR) {
                    MensajesParaAdmin(
                        mensajes = viewModel.mensajesDeSoporte.collectAsState().value,
                        onResolver = viewModel::marcarResuelto,
                        onResponder = {
                            mensajeRespondido = it
                            vista = VistaAyuda.Responder
                        },
                        onEliminar = viewModel::eliminarMensaje,
                        onPreguntas = { vista = VistaAyuda.Preguntas },
                        onManual = { vista = VistaAyuda.Manual },
                    )
                } else {
                    Text("PROBLEMAS CON LA APP", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
                    BigBtn(
                        icon = Icons.Filled.ReportProblem,
                        label = "Reportar un problema técnico",
                        section = Sections.Ayuda,
                        enabled = !state.esperandoMensaje,
                        onClick = { if (conCuenta) vista = VistaAyuda.Escribir else viewModel.avisarSinCuenta() },
                    )
                    BigBtn(
                        icon = Icons.Filled.ReportProblem,
                        label = "Reportar con mi voz",
                        section = Sections.Ayuda,
                        enabled = !state.esperandoMensaje,
                        onClick = { if (conCuenta) viewModel.pedirMensaje() else viewModel.avisarSinCuenta() },
                    )
                    if (!conCuenta) {
                        NoticeBanner("Para reportar un problema o ver tus reportes, inicia sesión.")
                    }

                    Text("INFORMACIÓN Y AYUDA", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
                    BigBtn(
                        icon = Icons.Filled.Info,
                        label = "Preguntas frecuentes",
                        section = Sections.Ayuda,
                        onClick = { vista = VistaAyuda.Preguntas },
                    )
                    BigBtn(
                        icon = Icons.AutoMirrored.Filled.Help,
                        label = "Manual de uso",
                        section = Sections.Ayuda,
                        onClick = { vista = VistaAyuda.Manual },
                    )

                    if (conCuenta) {
                        BigBtn(
                            icon = Icons.Filled.List,
                            label = "Mis reportes enviados",
                            section = Sections.Ayuda,
                            onClick = { vista = VistaAyuda.Reportes },
                        )
                    }
                }

                VistaAyuda.Responder -> {
                    var respuesta by remember { mutableStateOf("") }
                    val mensaje = mensajeRespondido
                    if (mensaje != null) {
                        BloqueTexto(titulo = mensaje.mensaje, cuerpo = "Escribe tu respuesta abajo.")
                        OutlinedTextField(
                            value = respuesta,
                            onValueChange = { respuesta = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Respuesta *") },
                            minLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent, unfocusedBorderColor = CecapiTextMuted),
                        )
                        BigBtn(
                            icon = Icons.Filled.Check,
                            label = "Enviar respuesta",
                            enabled = respuesta.isNotBlank(),
                            onClick = {
                                viewModel.responder(mensaje, respuesta)
                                vista = VistaAyuda.Menu
                            },
                        )
                    }
                }

                VistaAyuda.Escribir -> {
                    var texto by remember { mutableStateOf("") }
                    Text("REPORTAR UN PROBLEMA TÉCNICO", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
                    OutlinedTextField(
                        value = texto,
                        onValueChange = { texto = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Qué pasó *") },
                        minLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent, unfocusedBorderColor = CecapiTextMuted),
                    )
                    BigBtn(
                        icon = Icons.Filled.Check,
                        label = "Enviar reporte",
                        enabled = texto.isNotBlank(),
                        onClick = {
                            viewModel.enviarEscrito(texto)
                            vista = VistaAyuda.Menu
                        },
                    )
                }

                VistaAyuda.Preguntas -> {
                    Text("PREGUNTAS FRECUENTES", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
                    PreguntasFrecuentes.forEach { (pregunta, respuesta) ->
                        BloqueTexto(titulo = pregunta, cuerpo = respuesta)
                    }
                }

                VistaAyuda.Manual -> {
                    Text("MANUAL DE USO", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
                    ManualDeUso.forEach { (titulo, cuerpo) ->
                        BloqueTexto(titulo = titulo, cuerpo = cuerpo)
                    }
                }

                VistaAyuda.Reportes -> {
                    Text("MIS REPORTES ENVIADOS", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
                    if (state.misIncidencias.isEmpty()) {
                        Text(
                            "Todavía no has enviado reportes.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = CecapiTextMuted,
                        )
                    }
                    state.misIncidencias.take(10).forEach { incidencia ->
                        BloqueTexto(
                            titulo = incidencia.mensaje,
                            cuerpo = (incidencia.respuesta?.let { "Respuesta: $it\n" } ?: "") + if (incidencia.resuelta) "Resuelto" else "En espera",
                            colorCuerpo = if (incidencia.resuelta) CecapiAccent else CecapiTextMuted,
                        )
                    }
                }
            }
        }

        MicPad(
            listening = listening,
            onDoubleTap = viewModel::onMicDoubleTap,
            hint = if (listening) "Escuchando…" else "Di reportar un problema, o toca aquí",
            onClick = viewModel::onMicTapped,
        )
    }
}

/** Cada bloque de texto se lee en voz alta al tocarlo: sin eso, una tarjeta que solo se ve no le sirve a quien no ve. */
@Composable
private fun BloqueTexto(
    titulo: String,
    cuerpo: String,
    colorCuerpo: androidx.compose.ui.graphics.Color = CecapiTextMuted,
) {
    TarjetaLectura(titulo = titulo, cuerpo = cuerpo, colorCuerpo = colorCuerpo, section = Sections.Ayuda)
}

@Composable
private fun MensajesParaAdmin(
    mensajes: List<IncidenciaEntity>,
    onResolver: (IncidenciaEntity) -> Unit,
    onResponder: (IncidenciaEntity) -> Unit,
    onEliminar: (IncidenciaEntity) -> Unit,
    onPreguntas: () -> Unit,
    onManual: () -> Unit,
) {
    var aEliminar by remember { mutableStateOf<Long?>(null) }
    if (mensajes.isNotEmpty()) {
        Text("MENSAJES DE LAS PERSONAS", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
    }
    mensajes.take(20).forEach { mensaje ->
        BloqueTexto(
            titulo = mensaje.mensaje,
            cuerpo = (mensaje.respuesta?.let { "Respondido: $it\n" } ?: "") + if (mensaje.resuelta) "Resuelto" else "En espera",
            colorCuerpo = if (mensaje.resuelta) CecapiAccent else CecapiTextMuted,
        )
        BigBtn(icon = Icons.Filled.Check, label = "Responder", size = BigBtnSize.Secondary, onClick = { onResponder(mensaje) })
        if (!mensaje.resuelta) {
            BigBtn(icon = Icons.Filled.Check, label = "Marcar resuelto", size = BigBtnSize.Secondary, onClick = { onResolver(mensaje) })
        }
        if (aEliminar == mensaje.id) {
            BigBtn(icon = Icons.Filled.Close, label = "Confirmar eliminar", variant = BigBtnVariant.Danger, size = BigBtnSize.Secondary, onClick = {
                aEliminar = null
                onEliminar(mensaje)
            })
        } else {
            BigBtn(icon = Icons.Filled.Close, label = "Eliminar", variant = BigBtnVariant.Danger, size = BigBtnSize.Secondary, onClick = { aEliminar = mensaje.id })
        }
    }
    Text("INFORMACIÓN Y AYUDA", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
    BigBtn(icon = Icons.Filled.Info, label = "Preguntas frecuentes", section = Sections.Ayuda, onClick = onPreguntas)
    BigBtn(icon = Icons.AutoMirrored.Filled.Help, label = "Manual de uso", section = Sections.Ayuda, onClick = onManual)
}

private val PreguntasFrecuentes = listOf(
    "¿Cómo llamo al asistente?" to "Di el nombre del asistente o toca el micrófono, que ocupa toda la parte de abajo de la pantalla. Para usar también la palabra hola, actívala en Configuración, en Privacidad.",
    "¿Cómo cambio el nombre del asistente?" to "Di llámate y el nombre que quieras, o hazlo en Personalización.",
    "¿Qué funciona sin internet?" to "Abrir las pantallas, leer texto con la cámara, las actividades y los comandos del teléfono. Consultas necesita internet.",
    "¿Cómo pido un documento?" to "Abre Consultas y di qué necesitas, por ejemplo una carta para pedir un justificante. El documento aparece en pantalla y te lo leo si dices lee el documento.",
    "¿Dónde quedan mis consultas?" to "En Consultas, en Consultas anteriores. Ahí puedes volver a escucharlas o borrarlas.",
    "¿Mis fotos se guardan en la app?" to "No. Solo se guardan en tu galería si tú decides guardarlas.",
    "¿Quién ve mis datos?" to "En Cuenta, toca ¿Cómo me ven? para escuchar lo que ven los demás. Tu correo no se muestra a nadie.",
    "¿Cómo borro mi cuenta?" to "En Cuenta, hasta abajo, toca Borrar mis datos y mi cuenta. Te pido tu contraseña y luego que confirmes; no se puede deshacer.",
    "¿Cómo reporto un problema?" to "En Ayuda y soporte, toca Reportar un problema técnico o Reportar con mi voz. Necesitas iniciar sesión.",
)

private val ManualDeUso = listOf(
    "La pantalla" to "Arriba está el contenido y abajo, en casi la mitad de la pantalla, el micrófono. Toda esa parte de abajo es el botón: no tienes que atinarle a un círculo.",
    "Micrófono" to "Toca una vez para hablar. Dos toques seguidos, o un toque con dos dedos, me callan. Mantén presionado para escuchar esta ayuda.",
    "Moverte entre botones" to "Desliza un dedo a la derecha para pasar al siguiente botón y a la izquierda para regresar. El botón donde estás se ilumina y te digo su nombre.",
    "Escuchar un botón" to "Mantén presionado cualquier botón o tarjeta para escuchar qué hace sin abrirlo.",
    "Detener" to "Di para, o silencio, para que deje de hablar.",
    "Volver" to "Cada pantalla tiene arriba el botón Volver. También puedes decir atrás o menú.",
    "Comandos" to "En cualquier pantalla, di lista de comandos para escuchar lo que puedes decir ahí.",
    "Cámara" to "Apunta la cámara a un papel y di toma la foto; te leo el texto. También te digo qué hay enfrente.",
    "Consultas" to "Pide un documento por voz o escribiendo y la inteligencia artificial lo genera. Tus consultas anteriores quedan guardadas para volver a escucharlas.",
    "Actividades" to "Ejercicios de sonido y de vibración para entrenar el oído y el tacto, y juegos para hacer solo o en grupo.",
    "Cuenta" to "Escucha cómo te ven, edita tus datos, cierra sesión o borra tu cuenta.",
    "Lector de pantalla" to "Si prefieres TalkBack, el lector de pantalla de Android, actívalo en Configuración, en Lector de pantalla de Android.",
)
