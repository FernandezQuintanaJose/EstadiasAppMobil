package com.cecapi.app.feature.modulo1_aplicacionprincipal

import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Close
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiSuccess
import com.cecapi.app.core.theme.CecapiSurface
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.theme.CecapiBorder
import com.cecapi.app.core.theme.CecapiWarning
import com.cecapi.app.core.ui.BigBtn
import com.cecapi.app.core.ui.BigBtnSize
import com.cecapi.app.core.ui.BigBtnVariant
import com.cecapi.app.core.ui.MicPad
import com.cecapi.app.core.ui.NoticeBanner
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.ui.voiceHint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.cecapi.app.core.voice.VoiceState

@Composable
fun GestionScreen(
    onBack: () -> Unit,
    viewModel: GestionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val listening = voiceState is VoiceState.Listening
    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }

    val esEducador = state.rolObservador == RolUsuario.EDUCADOR
    val puedeEditar = viewModel.puedeEditar()
    // La persona que se está editando tiene su propia pantalla, en vez de abrirse inline en la
    // tarjeta: validar, cambiar rol y asignar educador juntos podían llenar la tarjeta de chips chicos.
    val personaEnEdicion = state.personas.firstOrNull { it.id == state.expandidoId }
    // Si hay pendientes, la gestión se abre primero con ellos; "Ver toda la gestión" lleva a la lista normal.
    var pendientesVistos by remember { mutableStateOf(false) }
    var abrirPendientes by remember { mutableStateOf(false) }
    var vista by remember { mutableStateOf(GestionVista.Menu) }
    val personasPendientes = state.personas.filter { it.solicitaValidacion && !it.validado }
    val incidenciasAbiertas = state.incidencias.filter { !it.resuelta }
    val mostrarPendientes = personaEnEdicion == null &&
        (abrirPendientes || (!pendientesVistos && (personasPendientes.isNotEmpty() || incidenciasAbiertas.isNotEmpty())))
    val alertasDe: (UsuarioEntity) -> Set<AlertaPersona> = { persona ->
        buildSet {
            if (persona.solicitaValidacion && !persona.validado) add(AlertaPersona.PorValidar)
            if (state.incidencias.any { it.reportanteId == persona.id && !it.resuelta }) add(AlertaPersona.Soporte)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .padding(bottom = 230.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (personaEnEdicion != null) {
            PersonaEditScreen(
                persona = personaEnEdicion,
                puedeEditar = puedeEditar,
                puedeValidar = viewModel.puedeValidar(personaEnEdicion),
                onRechazar = { viewModel.onRechazarSolicitud(personaEnEdicion) },
                rolesAsignables = viewModel.rolesAsignables(),
                educadoresDisponibles = { viewModel.educadoresPara(personaEnEdicion) },
                onBack = { viewModel.onPersonaTocada(personaEnEdicion) },
                onValidar = { viewModel.onValidar(personaEnEdicion) },
                onRolElegido = { rol -> viewModel.onRolElegido(personaEnEdicion, rol) },
                onEducadorElegido = { educador -> viewModel.onEducadorElegido(personaEnEdicion, educador) },
                puedeCambiarContrasena = viewModel.puedeCambiarContrasenaDe(personaEnEdicion),
                onCambiarContrasena = { nueva -> viewModel.onCambiarContrasena(personaEnEdicion, nueva) },
                puedeAutorizarBaja = viewModel.puedeAutorizarBajaDe(personaEnEdicion),
                onAutorizarBaja = { viewModel.onAutorizarBajaDe(personaEnEdicion) },
            )
        } else if (mostrarPendientes) {
            PendientesPanel(
                personas = personasPendientes,
                incidencias = incidenciasAbiertas,
                alertasDe = alertasDe,
                onBack = {
                    abrirPendientes = false
                    pendientesVistos = true
                },
                onPersonaTocada = viewModel::onPersonaTocada,
                onResolverIncidencia = viewModel::onResolverIncidencia,
                onVerTodo = {
                    abrirPendientes = false
                    pendientesVistos = true
                },
            )
        } else if (vista == GestionVista.Menu) {
            ScreenTopBar(
                eyebrow = "GESTIÓN",
                title = if (esEducador) "Mis alumnos" else "Mi institución",
                onBack = onBack,
                onCommands = viewModel::onCommandsRequested,
            )
            BigBtn(
                icon = Icons.Filled.Person,
                label = "Por roles",
                size = BigBtnSize.Hero,
                onClick = { vista = GestionVista.Roles },
            )
            BigBtn(
                icon = Icons.Filled.Warning,
                label = "Pendientes de validación",
                size = BigBtnSize.Hero,
                onClick = { abrirPendientes = true },
            )
            if (state.rolObservador == RolUsuario.ADMINISTRADOR) {
                BigBtn(
                    icon = Icons.Filled.Check,
                    label = "Validar cuentas",
                    size = BigBtnSize.Hero,
                    onClick = { vista = GestionVista.Cuentas },
                )
            }
            if (state.rolObservador == RolUsuario.ADMINISTRADOR || state.rolObservador == RolUsuario.DIRECTIVO) {
                BigBtn(
                    icon = Icons.Filled.Person,
                    label = "Asignar educador",
                    size = BigBtnSize.Hero,
                    onClick = { vista = GestionVista.Asignacion },
                )
            }
        } else if (vista == GestionVista.Cuentas) {
            val cuentas by viewModel.cuentasPorRevisar.collectAsState()
            ScreenTopBar(eyebrow = "GESTIÓN", title = "Validar cuentas", onBack = { vista = GestionVista.Menu }, onCommands = {})
            if (cuentas.isEmpty()) {
                Text("No hay cuentas nuevas por revisar.", style = MaterialTheme.typography.bodyLarge, color = CecapiTextMuted)
            }
            cuentas.forEach { cuenta ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(CecapiSurface)
                        .border(1.dp, CecapiBorder, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(cuenta.nombreCompleto, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        "Cuenta nueva, como usuario regular.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CecapiTextMuted,
                    )
                    BigBtn(
                        icon = Icons.Filled.Check,
                        label = "Confirmar",
                        size = BigBtnSize.Secondary,
                        onClick = { viewModel.onConfirmarCuenta(cuenta) },
                    )
                    BigBtn(
                        icon = Icons.Filled.Close,
                        label = "No la conozco",
                        variant = BigBtnVariant.Danger,
                        size = BigBtnSize.Secondary,
                        onClick = { viewModel.onNoConozcoCuenta(cuenta) },
                    )
                }
            }
        } else if (vista == GestionVista.Asignacion) {
            val sinEducador = viewModel.alumnosSinEducador()
            ScreenTopBar(eyebrow = "GESTIÓN", title = "Asignar educador", onBack = { vista = GestionVista.Menu }, onCommands = {})
            if (sinEducador.isEmpty()) {
                Text("Todos los alumnos validados ya tienen educador.", style = MaterialTheme.typography.bodyLarge, color = CecapiTextMuted)
            }
            sinEducador.forEach { alumno ->
                PersonaCard(
                    persona = alumno,
                    alertas = emptySet(),
                    mostrarOrigen = false,
                    editable = true,
                    onClick = { viewModel.onPersonaTocada(alumno) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            ScreenTopBar(
                eyebrow = "GESTIÓN",
                title = if (esEducador) "Mis alumnos" else "Mi institución",
                onBack = { vista = GestionVista.Menu },
                onCommands = viewModel::onCommandsRequested,
            )

            if (state.incidencias.isNotEmpty()) {
                Text("REPORTES RECIBIDOS", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
                val formatter = remember { SimpleDateFormat("dd/MM HH:mm", Locale("es", "MX")) }
                state.incidencias.forEach { incidencia ->
                    IncidenciaCard(incidencia = incidencia, fecha = formatter.format(Date(incidencia.fechaReporte)), onResolver = { viewModel.onResolverIncidencia(incidencia) })
                }
            }

            Text(
                if (esEducador) "MIS ALUMNOS" else "PERSONAS DE LA INSTITUCIÓN",
                style = MaterialTheme.typography.labelLarge,
                color = CecapiTextMuted,
            )
            if (state.personas.isEmpty()) {
                Text(
                    if (esEducador) "Todavía no tienes alumnos asignados." else "No hay nadie que mostrar.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = CecapiTextMuted,
                    modifier = Modifier.padding(8.dp),
                )
            } else {
                // Dos por fila para que la lista quepa sin tanto scroll.
                state.personas.chunked(2).forEach { fila ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        fila.forEach { persona ->
                            PersonaCard(
                                persona = persona,
                                alertas = alertasDe(persona),
                                mostrarOrigen = state.rolObservador == RolUsuario.ADMINISTRADOR,
                                editable = puedeEditar || viewModel.puedeValidar(persona),
                                onClick = { viewModel.onPersonaTocada(persona) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (fila.size == 1) Row(modifier = Modifier.weight(1f)) {}
                    }
                }
            }
        }
    }

        MicPad(
            listening = listening,
            onDoubleTap = viewModel::onMicDoubleTap,
            hint = if (listening) "Escuchando…" else "Di cuántas personas, o toca aquí",
            onClick = viewModel::onMicTapped,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

private enum class GestionVista { Menu, Roles, Cuentas, Asignacion }

/** Qué espera de alguien: su validación, o un reporte suyo sin resolver. El color lo dice sin texto. */
private enum class AlertaPersona { PorValidar, Soporte }

private val AzulSoporte = Color(0xFF5AC8FA)

/** Borde de la tarjeta: neutro si no hay nada, un color si hay una alerta, y degradado si hay varias. */
private fun bordeDe(alertas: Set<AlertaPersona>, intensidad: Float): Brush {
    val colores = alertas.map { (if (it == AlertaPersona.PorValidar) CecapiWarning else AzulSoporte).copy(alpha = intensidad) }
    return when (colores.size) {
        0 -> SolidColor(CecapiBorder)
        1 -> SolidColor(colores.first())
        else -> Brush.horizontalGradient(colores)
    }
}

/** Lo que espera una decisión: personas por validar y reportes sin resolver. Se muestra antes que la lista normal. */
@Composable
private fun PendientesPanel(
    personas: List<UsuarioEntity>,
    incidencias: List<IncidenciaEntity>,
    alertasDe: (UsuarioEntity) -> Set<AlertaPersona>,
    onBack: () -> Unit,
    onPersonaTocada: (UsuarioEntity) -> Unit,
    onResolverIncidencia: (IncidenciaEntity) -> Unit,
    onVerTodo: () -> Unit,
) {
    val formatter = remember { SimpleDateFormat("dd/MM HH:mm", Locale("es", "MX")) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTopBar(eyebrow = "GESTIÓN", title = "Pendientes", onBack = onBack, onCommands = {})

        if (personas.isNotEmpty()) {
            Text("PENDIENTES DE VALIDAR", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
            personas.chunked(2).forEach { fila ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    fila.forEach { persona ->
                        PersonaCard(
                            persona = persona,
                            alertas = alertasDe(persona),
                            mostrarOrigen = false,
                            editable = true,
                            onClick = { onPersonaTocada(persona) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (fila.size == 1) Row(modifier = Modifier.weight(1f)) {}
                }
            }
        }

        if (incidencias.isNotEmpty()) {
            Text("REPORTES RECIBIDOS", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
            incidencias.forEach { incidencia ->
                IncidenciaCard(
                    incidencia = incidencia,
                    fecha = formatter.format(Date(incidencia.fechaReporte)),
                    onResolver = { onResolverIncidencia(incidencia) },
                )
            }
        }

        BigBtn(
            icon = Icons.Filled.Person,
            label = "Ver toda la gestión",
            size = BigBtnSize.Standard,
            onClick = onVerTodo,
        )
    }
}

/** Pantalla propia para validar, cambiar rol y asignar educador. */
@Composable
private fun PersonaEditScreen(
    persona: UsuarioEntity,
    puedeEditar: Boolean,
    puedeValidar: Boolean,
    rolesAsignables: List<RolUsuario>,
    educadoresDisponibles: () -> List<UsuarioEntity>,
    onBack: () -> Unit,
    onValidar: () -> Unit,
    onRechazar: () -> Unit,
    onRolElegido: (RolUsuario) -> Unit,
    onEducadorElegido: (UsuarioEntity?) -> Unit,
    puedeCambiarContrasena: Boolean,
    onCambiarContrasena: (String) -> Unit,
    puedeAutorizarBaja: Boolean,
    onAutorizarBaja: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTopBar(
            eyebrow = "GESTIÓN",
            title = persona.nombreCompleto,
            onBack = onBack,
            onCommands = {},
        )

        if (persona.solicitaValidacion && !persona.validado) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, CecapiWarning, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "PENDIENTE DE VALIDACIÓN",
                    style = MaterialTheme.typography.labelMedium,
                    color = CecapiWarning,
                )
                if (puedeValidar) {
                    BigBtn(icon = Icons.Filled.Check, label = "Validar persona", size = BigBtnSize.Hero, onClick = onValidar)
                    BigBtn(icon = Icons.Filled.Close, label = "Rechazar solicitud", variant = BigBtnVariant.Danger, onClick = onRechazar)
                }
            }
        }

        if (persona.solicitaBaja) {
            Text("PIDIÓ SU BAJA", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
            if (puedeAutorizarBaja) {
                BigBtn(icon = Icons.Filled.Close, label = "Confirmar baja", variant = BigBtnVariant.Danger, onClick = onAutorizarBaja)
            }
        }

        if (puedeCambiarContrasena) {
            var nueva by remember { mutableStateOf("") }
            Text("CONTRASEÑA", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
            OutlinedTextField(
                value = nueva,
                onValueChange = { nueva = it.filter(Char::isDigit) },
                modifier = Modifier.fillMaxWidth().voiceHint("Nueva contraseña para esta persona. Se le avisará al abrir su cuenta."),
                label = { Text("Nueva contraseña *") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
            BigBtn(
                icon = Icons.Filled.Lock,
                label = "Cambiar contraseña",
                enabled = nueva.isNotBlank(),
                onClick = { onCambiarContrasena(nueva); nueva = "" },
            )
        }

        if (puedeEditar) {
            Text("CAMBIAR ROL", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
            rolesAsignables.chunked(2).forEach { fila ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    fila.forEach { rol ->
                        val label = rol.codigo.replaceFirstChar { it.uppercase() }
                        BigBtn(
                            icon = Icons.Filled.Check,
                            label = label,
                            variant = if (persona.rol == rol.codigo) BigBtnVariant.Accent else BigBtnVariant.Neutral,
                            half = true,
                            onClick = { onRolElegido(rol) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (fila.size == 1) Row(modifier = Modifier.weight(1f)) {}
                }
            }
        }

        if (puedeEditar && persona.rol == RolUsuario.ALUMNO.codigo) {
            val educadores = educadoresDisponibles()
            Text("ASIGNAR EDUCADOR", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted, modifier = Modifier.padding(top = 8.dp))
            if (educadores.isEmpty()) {
                Text(
                    "No hay educadores en esta institución todavía.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CecapiTextMuted,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    educadores.forEach { educador ->
                        BigBtn(
                            icon = Icons.Filled.Person,
                            label = educador.nombreCompleto,
                            variant = if (persona.educadorId == educador.id) BigBtnVariant.Accent else BigBtnVariant.Neutral,
                            onClick = { onEducadorElegido(educador) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonaCard(
    persona: UsuarioEntity,
    alertas: Set<AlertaPersona>,
    mostrarOrigen: Boolean,
    editable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    val latido by rememberInfiniteTransition(label = "alerta").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1100), repeatMode = RepeatMode.Reverse),
        label = "intensidad",
    )
    val intensidad = if (alertas.isEmpty()) 1f else latido
    val rolLegible = persona.rol.replaceFirstChar { it.uppercase() }
    val detalle = buildString {
        append(rolLegible)
        if (mostrarOrigen && persona.origen.isNotBlank()) append(" · ${persona.origen}")
    }
    val help = if (editable) {
        "$rolLegible ${persona.nombreCompleto}. Toca para cambiarle el rol."
    } else {
        "$rolLegible ${persona.nombreCompleto}."
    }

    Column(
        modifier = modifier
            .clip(shape)
            .background(CecapiSurface)
            .border(2.dp, bordeDe(alertas, intensidad), shape)
            .then(if (editable) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = help }
            .voiceHint(help)
            .padding(16.dp),
    ) {
        if (persona.solicitaValidacion && !persona.validado) {
            Text(
                "PENDIENTE",
                style = MaterialTheme.typography.labelSmall,
                color = CecapiTextMuted,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(CecapiTextMuted.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Text(
            persona.nombreCompleto,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = if (persona.solicitaValidacion && !persona.validado) 6.dp else 0.dp),
        )
        Text(detalle, style = MaterialTheme.typography.bodySmall, color = CecapiTextMuted)
    }
}

/** Un reporte de Ayuda y soporte, visible solo para quien debe atenderlo (administrador o directivo). */
@Composable
private fun IncidenciaCard(incidencia: IncidenciaEntity, fecha: String, onResolver: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CecapiSurface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(incidencia.mensaje, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(fecha, style = MaterialTheme.typography.labelSmall, color = CecapiTextMuted)
        if (incidencia.resuelta) {
            Text("RESUELTO", style = MaterialTheme.typography.labelMedium, color = CecapiSuccess)
        } else {
            BigBtn(icon = Icons.Filled.Check, label = "Marcar resuelto", half = true, onClick = onResolver)
        }
    }
}
