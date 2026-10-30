package com.cecapi.app.core.ui

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiBorder
import com.cecapi.app.core.theme.CecapiEyebrowStyle
import com.cecapi.app.core.theme.CecapiSurface
import com.cecapi.app.core.theme.CecapiSurfaceElevated
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.theme.CecapiTextPrimary
import com.cecapi.app.core.theme.ModuleSection
import kotlinx.coroutines.launch

/**
 * La columna con desplazamiento de todas las pantallas: el contenido se mueve y, a la derecha, una barra
 * visible y arrastrable (la de Actividades) dice cuánto falta y deja moverse arrastrándola. La posición se
 * guarda, así que al volver de otra pantalla se queda donde estaba.
 */
@Composable
fun ColumnaDesplazable(
    modifier: Modifier = Modifier,
    estado: ScrollState = rememberScrollState(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    espacio: Arrangement.Vertical = Arrangement.spacedBy(14.dp),
    descripcionBarra: String = "Barra de desplazamiento",
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(estado)
                .padding(contentPadding)
                .padding(end = ANCHO_BARRA),
            verticalArrangement = espacio,
            content = content,
        )
        if (estado.maxValue > 0) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(ANCHO_BARRA)
                    .pointerInput(estado) {
                        detectVerticalDragGestures { change, arrastre ->
                            change.consume()
                            val maximo = estado.maxValue
                            if (maximo > 0 && size.height > 0) {
                                scope.launch { estado.scrollBy(arrastre * (maximo + size.height).toFloat() / size.height) }
                            }
                        }
                    }
                    .semantics { contentDescription = descripcionBarra },
            ) {
                val visible = (size.height / (size.height + estado.maxValue.toFloat())).coerceIn(0f, 1f)
                val altoGuia = (size.height * visible).coerceAtLeast(36.dp.toPx()).coerceAtMost(size.height)
                val recorrido = (size.height - altoGuia).coerceAtLeast(0f)
                val guiaY = (estado.value.toFloat() / estado.maxValue) * recorrido
                val anchoLinea = 6.dp.toPx()
                val x = (size.width - anchoLinea) / 2f
                drawRoundRect(
                    color = CecapiTextMuted.copy(alpha = 0.25f),
                    topLeft = Offset(x, 0f),
                    size = Size(anchoLinea, size.height),
                    cornerRadius = CornerRadius(anchoLinea / 2f),
                )
                drawRoundRect(
                    color = CecapiAccent,
                    topLeft = Offset(x, guiaY),
                    size = Size(anchoLinea, altoGuia),
                    cornerRadius = CornerRadius(anchoLinea / 2f),
                )
            }
        }
    }
}

private val ANCHO_BARRA = 20.dp

private fun lectorActivo(contexto: Context): Boolean =
    (contexto.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager)?.isTouchExplorationEnabled == true

/**
 * Una tarjeta de información que se lee en voz alta al tocarla (preguntas frecuentes, manual, juegos,
 * reportes, chats...). Se ilumina como las demás tarjetas cuando es la seleccionada. Sin esto, una tarjeta
 * que solo muestra texto no le sirve a quien no ve la pantalla.
 */
@Composable
fun TarjetaLectura(
    titulo: String,
    cuerpo: String,
    modifier: Modifier = Modifier,
    icono: ImageVector? = null,
    section: ModuleSection? = null,
    mostrarCuerpo: Boolean = true,
    colorCuerpo: Color = CecapiTextMuted,
    /** Siempre iluminada con el color de [section] (un resultado, por ejemplo), sin depender de la selección. */
    resaltada: Boolean = false,
    /** Lo que se dice al tocarla; por omisión el título y el cuerpo completos. */
    lectura: String = "$titulo. $cuerpo",
) {
    val seleccion = LocalSeleccionTarjeta.current
    val helper = LocalVoiceHelp.current
    val contexto = LocalContext.current
    val yo = remember { Any() }
    var enFocoLocal by remember { mutableStateOf(false) }
    val iluminada = resaltada || (if (seleccion != null) seleccion.actual === yo else enFocoLocal)
    val color = section?.color ?: CecapiAccent
    val forma = RoundedCornerShape(20.dp)

    if (seleccion != null) {
        DisposableEffect(seleccion) { onDispose { if (seleccion.actual === yo) seleccion.actual = null } }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clip(forma)
            .background(if (iluminada) (section?.bg ?: CecapiSurfaceElevated) else CecapiSurface)
            .border(if (iluminada) 4.dp else 1.dp, if (iluminada) color else CecapiBorder, forma)
            .onFocusChanged { estado ->
                if (estado.isFocused) {
                    if (seleccion != null) seleccion.elegir(yo, titulo) else enFocoLocal = true
                    if (!lectorActivo(contexto)) helper?.speak(lectura)
                } else if (seleccion == null) {
                    enFocoLocal = false
                }
            }
            .clickable { seleccion?.elegir(yo, titulo); helper?.speak(lectura) }
            .semantics { contentDescription = lectura }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icono != null) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(if (iluminada) color.copy(alpha = 0.25f) else CecapiSurfaceElevated),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icono, contentDescription = null, tint = if (iluminada) color else CecapiTextPrimary, modifier = Modifier.size(30.dp))
            }
            Box(modifier = Modifier.width(14.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleLarge, color = if (iluminada) color else CecapiTextPrimary)
            if (mostrarCuerpo && cuerpo.isNotBlank()) {
                Text(cuerpo, style = MaterialTheme.typography.bodyLarge, color = colorCuerpo)
            }
        }
    }
}

/**
 * El campo de texto de toda la app: título arriba en mayúsculas pequeñas, caja alta con letra grande y
 * esquinas redondeadas, y la misma ayuda por voz. Así crear cuenta, iniciar sesión y editar los datos
 * se ven y se sienten igual. Con [contrasena] se oculta lo escrito y aparece el botón para mostrarlo.
 */
@Composable
fun CampoTexto(
    titulo: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    ayuda: String? = null,
    icono: ImageVector? = null,
    contrasena: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    minLines: Int = 1,
    enabled: Boolean = true,
    centrado: Boolean = false,
) {
    var visible by remember { mutableStateOf(false) }
    val textoAyuda = ayuda ?: "Campo de ${titulo.lowercase()}."
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(titulo.uppercase(), style = CecapiEyebrowStyle, color = CecapiTextMuted)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .voiceHint(help = textoAyuda, focusLabel = textoAyuda),
            enabled = enabled,
            textStyle = if (centrado) MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center) else MaterialTheme.typography.titleLarge,
            placeholder = placeholder?.let { { Text(it, style = MaterialTheme.typography.titleMedium) } },
            leadingIcon = icono?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(28.dp)) } },
            trailingIcon = if (contrasena) {
                {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (visible) "Ocultar contraseña" else "Mostrar contraseña",
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            } else {
                null
            },
            singleLine = singleLine,
            minLines = minLines,
            visualTransformation = if (contrasena && !visible) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = if (contrasena && keyboardOptions == KeyboardOptions.Default) {
                KeyboardOptions(keyboardType = KeyboardType.Password)
            } else {
                keyboardOptions
            },
            keyboardActions = keyboardActions,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent, unfocusedBorderColor = CecapiTextMuted),
        )
    }
}

/**
 * Reemplazo directo de `verticalScroll(rememberScrollState())`: además de desplazar, dibuja a la derecha la
 * barra visible de Actividades y deja arrastrarla con el dedo (franja de 24 dp del borde derecho). Como usa
 * un estado guardable, al volver a la pantalla el contenido se queda donde estaba.
 */
@Composable
fun Modifier.desplazableConBarra(estado: ScrollState = rememberScrollState()): Modifier {
    val scope = rememberCoroutineScope()
    return this
        .drawWithContent {
            drawContent()
            if (estado.maxValue > 0 && estado.maxValue != Int.MAX_VALUE) {
                val visible = (size.height / (size.height + estado.maxValue.toFloat())).coerceIn(0f, 1f)
                val altoGuia = (size.height * visible).coerceAtLeast(36.dp.toPx()).coerceAtMost(size.height)
                val recorrido = (size.height - altoGuia).coerceAtLeast(0f)
                val guiaY = (estado.value.toFloat() / estado.maxValue) * recorrido
                val anchoLinea = 6.dp.toPx()
                val x = size.width - 10.dp.toPx() - anchoLinea / 2f
                drawRoundRect(
                    color = CecapiTextMuted.copy(alpha = 0.25f),
                    topLeft = Offset(x, 0f),
                    size = Size(anchoLinea, size.height),
                    cornerRadius = CornerRadius(anchoLinea / 2f),
                )
                drawRoundRect(
                    color = CecapiAccent,
                    topLeft = Offset(x, guiaY),
                    size = Size(anchoLinea, altoGuia),
                    cornerRadius = CornerRadius(anchoLinea / 2f),
                )
            }
        }
        .pointerInput(estado) {
            // Solo los toques que empiezan en la franja de la barra la arrastran; el resto desplaza como siempre.
            awaitEachGesture {
                val inicio = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (estado.maxValue <= 0 || inicio.position.x < size.width - 24.dp.toPx()) return@awaitEachGesture
                inicio.consume()
                var anterior = inicio.position.y
                while (true) {
                    val evento = awaitPointerEvent(PointerEventPass.Initial)
                    val cambio = evento.changes.firstOrNull { it.id == inicio.id } ?: break
                    if (!cambio.pressed) break
                    val arrastre = cambio.position.y - anterior
                    anterior = cambio.position.y
                    cambio.consume()
                    if (size.height > 0) {
                        scope.launch { estado.scrollBy(arrastre * (estado.maxValue + size.height).toFloat() / size.height) }
                    }
                }
            }
        }
        .verticalScroll(estado)
}

/**
 * Una sola cámara con dos modos: leer texto y decir qué hay enfrente. Los dos botones van arriba en las
 * dos pantallas de cámara; el modo actual se ve encendido y el otro cambia de modo sin volver al menú.
 */
@Composable
fun SelectorModoCamara(leyendoTexto: Boolean, onLeerTexto: () -> Unit, onEnfrente: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BigBtn(
            icon = Icons.Filled.TextSnippet,
            label = "Leer texto",
            variant = if (leyendoTexto) BigBtnVariant.Accent else BigBtnVariant.Neutral,
            half = true,
            size = BigBtnSize.Secondary,
            onClick = { if (!leyendoTexto) onLeerTexto() },
            modifier = Modifier.weight(1f),
        )
        BigBtn(
            icon = Icons.Filled.Visibility,
            label = "Qué hay enfrente",
            variant = if (!leyendoTexto) BigBtnVariant.Accent else BigBtnVariant.Neutral,
            half = true,
            size = BigBtnSize.Secondary,
            onClick = { if (leyendoTexto) onEnfrente() },
            modifier = Modifier.weight(1f),
        )
    }
}
