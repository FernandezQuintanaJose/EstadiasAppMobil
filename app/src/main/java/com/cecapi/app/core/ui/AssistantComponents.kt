package com.cecapi.app.core.ui

import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import android.content.res.Configuration
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import kotlin.math.abs
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiBackground
import com.cecapi.app.core.theme.CecapiBorder
import com.cecapi.app.core.theme.CecapiError
import com.cecapi.app.core.theme.CecapiSuccess
import com.cecapi.app.core.theme.ModuleSection
import com.cecapi.app.core.theme.CecapiSurface
import com.cecapi.app.core.theme.CecapiSurfaceElevated
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.theme.CecapiTextPrimary
import com.cecapi.app.core.theme.CecapiWarning

/** Si la pantalla está en horizontal el micrófono va a la derecha; la fija [PantallaConMicrofono]. */
private val LocalMicAlLado = compositionLocalOf { false }

/**
 * Qué tarjeta está "seleccionada" (iluminada) en la pantalla. Se comparte con todos los [BigBtn]
 * de la [PantallaConMicrofono]: solo una se ilumina a la vez, con el color de su módulo, y las demás
 * quedan neutras. Cambia cuando una tarjeta recibe el foco, cuando se toca y se mantiene oprimida
 * o al deslizar un dedo a la derecha (siguiente) o a la izquierda (anterior).
 */
class SeleccionTarjeta(private val claveGuardada: MutableState<String?> = mutableStateOf(null)) {
    var actual: Any? by mutableStateOf(null)

    /**
     * El nombre de la tarjeta elegida. Se guarda con la pantalla, así que al volver de un submódulo se
     * ilumina la misma tarjeta donde se quedó la persona, no otra vez la primera.
     */
    var clave: String?
        get() = claveGuardada.value
        set(value) { claveGuardada.value = value }

    fun elegir(tarjeta: Any, nombre: String) {
        actual = tarjeta
        clave = nombre
    }

    /** La próxima vez que una tarjeta reciba el foco no se lee en voz alta (foco inicial automático). */
    var silenciar: Boolean = false
}

val LocalSeleccionTarjeta = compositionLocalOf<SeleccionTarjeta?> { null }

/** Si TalkBack (el lector de pantalla de Android) está encendido, él lee y dibuja su propio foco. */
private fun lectorDePantallaActivo(contexto: Context): Boolean =
    (contexto.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager)?.isTouchExplorationEnabled == true

/** Marca el hijo de [PantallaConMicrofono] que es el micrófono. */
private const val MIC_ID = "zona-microfono"

/**
 * Parte de la pantalla que se queda para el micrófono: un poco menos de la mitad. Es una
 * proporción, no una medida en dp, así que se adapta a cualquier teléfono, al texto grande y
 * a la rotación. En vertical es el alto de abajo; en horizontal, el ancho de la derecha.
 */
private const val MIC_FRACCION = 0.42f

/** En tablets y pantallas muy anchas el contenido no pasa de este ancho y queda centrado. */
private val ANCHO_MAXIMO_CONTENIDO = 720.dp

/**
 * Pantalla dividida en dos zonas que nunca se encimian:
 *  - Zona del contenido: todos los hijos menos el [MicPad]. Es la parte que se desplaza.
 *  - Zona del micrófono: el [MicPad] ocupa todo el [MIC_FRACCION] restante.
 *
 * Vertical: contenido arriba, micrófono abajo. Horizontal: contenido a la izquierda, micrófono a
 * la derecha, a todo el alto. Los márgenes del sistema ya los pone MainActivity (`safeDrawingPadding`).
 *
 * Reemplaza al `Box` con `padding(bottom = 230.dp)` y el panel flotante de antes: aquellas medidas
 * fijas no se adaptaban a la pantalla, y el contenido seguía "debajo" del panel, aunque no se viera,
 * con lo que TalkBack podía enfocar elementos tapados.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantallaConMicrofono(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val alLado = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Con el teclado abierto la persona está escribiendo y ve la pantalla: el micrófono se esconde para que el
    // campo de texto tenga todo el espacio; vuelve en cuanto se cierra el teclado.
    val tecladoAbierto = WindowInsets.isImeVisible
    val claveGuardada = rememberSaveable { mutableStateOf<String?>(null) }
    val seleccion = remember { SeleccionTarjeta(claveGuardada) }
    val focusManager = LocalFocusManager.current
    val contexto = LocalContext.current
    CompositionLocalProvider(LocalMicAlLado provides alLado, LocalSeleccionTarjeta provides seleccion) {
        Layout(
            content = content,
            modifier = modifier
                .fillMaxSize()
                // Deslizar un dedo a la derecha pasa a la tarjeta siguiente; a la izquierda, a la anterior (como
                // el lector de pantalla). Solo si TalkBack está apagado: con TalkBack, él maneja los gestos.
                // Los deslizamientos que ya usa otro elemento (campos de texto, carruseles) no llegan aquí.
                .pointerInput(Unit) {
                    var acumulado = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { acumulado = 0f },
                        onDragCancel = { acumulado = 0f },
                        onDragEnd = {
                            if (!lectorDePantallaActivo(contexto) && abs(acumulado) > 72.dp.toPx()) {
                                focusManager.moveFocus(if (acumulado > 0) FocusDirection.Next else FocusDirection.Previous)
                            }
                        },
                        onHorizontalDrag = { _, delta -> acumulado += delta },
                    )
                },
        ) { medibles, restricciones ->
            val ancho = restricciones.maxWidth
            val alto = restricciones.maxHeight
            val fraccion = if (tecladoAbierto) 0f else MIC_FRACCION
            val anchoMic = if (alLado) (ancho * fraccion).roundToInt() else ancho
            val altoMic = if (alLado) alto else (alto * fraccion).roundToInt()
            val anchoContenido = if (alLado) ancho - anchoMic else ancho
            val altoContenido = if (alLado) alto else alto - altoMic
            val anchoUtil = minOf(anchoContenido, ANCHO_MAXIMO_CONTENIDO.roundToPx())

            val colocables = medibles.map { medible ->
                if (medible.layoutId == MIC_ID) {
                    medible to medible.measure(Constraints.fixed(anchoMic, altoMic))
                } else {
                    medible to medible.measure(Constraints.fixed(anchoUtil, altoContenido))
                }
            }
            layout(ancho, alto) {
                colocables.forEach { (medible, colocable) ->
                    if (medible.layoutId == MIC_ID) {
                        colocable.place(x = if (alLado) ancho - anchoMic else 0, y = if (alLado) 0 else alto - altoMic)
                    } else {
                        colocable.place(x = (anchoContenido - anchoUtil) / 2, y = 0)
                    }
                }
            }
        }
    }
}

/**
 * El micrófono es el control principal de toda la app. Va dentro de [PantallaConMicrofono] y
 * ocupa toda su zona: **toda esa zona es el botón** (tocar una vez para hablar, dos veces para
 * callar al asistente, mantener para oír la ayuda). Quien no ve la pantalla no tiene que
 * apuntar a un círculo pequeño. El círculo, la leyenda y el anillo que pulsa al escuchar se
 * dibujan centrados y se escalan al espacio disponible.
 */
@Composable
fun MicPad(
    listening: Boolean,
    hint: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDoubleTap: (() -> Unit)? = null,
) {
    val ringTransition = rememberInfiniteTransition(label = "mic-ring")
    val ringScale by ringTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "mic-ring-scale",
    )
    val ringAlpha by ringTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "mic-ring-alpha",
    )
    val helper = LocalVoiceHelp.current
    val alLado = LocalMicAlLado.current
    val holdHelp = "Este es el micrófono. Tócalo una vez para hablarme y dime lo que necesitas, " +
        "por ejemplo: módulos disponibles, o estado del teléfono. Tócalo dos veces seguidas, o una vez con " +
        "dos dedos, para que se calle."
    // true mientras un toque lleva dos dedos: ese toque calla al asistente y no debe activar el micrófono.
    val dosDedos = remember { BooleanArray(1) }

    BoxWithConstraints(
        modifier = modifier
            .layoutId(MIC_ID)
            .fillMaxSize()
            .background(CecapiSurface)
            .pointerInput(onDoubleTap) {
                // Un toque breve con dos dedos calla al asistente. Solo se observa; no consume el toque.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    dosDedos[0] = false
                    var maximo = 1
                    var terminado = false
                    withTimeoutOrNull(400) {
                        while (!terminado) {
                            val evento = awaitPointerEvent(PointerEventPass.Initial)
                            val abajo = evento.changes.count { it.pressed }
                            if (abajo > maximo) {
                                maximo = abajo
                                if (maximo >= 2) dosDedos[0] = true
                            }
                            if (abajo == 0) terminado = true
                        }
                    }
                    if (terminado && maximo >= 2) onDoubleTap?.invoke()
                }
            }
            .pointerInput(onDoubleTap, holdHelp) {
                detectTapGestures(
                    onTap = { if (!dosDedos[0]) onClick() },
                    onDoubleTap = { onDoubleTap?.invoke() },
                    onLongPress = { helper?.speak(holdHelp) },
                )
            }
            .semantics {
                contentDescription = "Micrófono. Toca dos veces para hablar con el asistente."
                onClick(label = "Hablar") { onClick(); true }
                onDoubleTap?.let { silence ->
                    customActions = listOf(
                        CustomAccessibilityAction("Silenciar al asistente") { silence(); true },
                    )
                }
            },
    ) {
        // Línea que separa el micrófono del contenido: arriba en vertical, a la izquierda en horizontal.
        if (alLado) {
            Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(1.dp).background(CecapiBorder))
        } else {
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(1.dp).background(CecapiBorder))
        }

        // El círculo se ajusta al espacio: lo que sobra después de la leyenda y el aire, entre 88 y 176 dp
        // (220 en pantallas grandes), y nunca más ancho que la zona.
        val textos = 14.dp + 64.dp + 4.dp + 24.dp
        val tope = if (maxWidth >= 600.dp || maxHeight >= 600.dp) 220.dp else 176.dp
        val circulo = (maxHeight - textos - 32.dp).coerceIn(88.dp, tope).coerceAtMost(maxWidth - 48.dp)

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.size(circulo), contentAlignment = Alignment.Center) {
                if (listening) {
                    Box(
                        modifier = Modifier
                            .size(circulo * 1.2f)
                            .graphicsLayer {
                                scaleX = ringScale
                                scaleY = ringScale
                                alpha = ringAlpha
                            }
                            .clip(CircleShape)
                            .border(3.dp, CecapiAccent, CircleShape),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(circulo)
                        .clip(CircleShape)
                        .background(CecapiBackground)
                        .border(if (listening) 3.dp else 2.dp, if (listening) CecapiAccent else CecapiBorder, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (listening) Icons.Filled.MicOff else Icons.Filled.Mic,
                        contentDescription = null,
                        tint = if (listening) CecapiAccent else CecapiTextPrimary,
                        modifier = Modifier.size(circulo * 0.44f),
                    )
                }
            }
            Text(
                text = hint,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = if (listening) CecapiAccent else CecapiTextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 14.dp).height(64.dp).fillMaxWidth(),
            )
            Text(
                text = "Di lista de comandos",
                style = MaterialTheme.typography.bodyMedium,
                color = CecapiTextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp).height(24.dp),
            )
        }
    }
}

/** Qué apariencia toma un [BigBtn] cuando no tiene [section]. */
enum class BigBtnVariant { Neutral, Accent, Danger, Success, Locked }

/**
 * Los 6 tamaños que puede tener una tarjeta grande en toda la app ("Volver" y el micrófono no
 * pasan por BigBtn, son sus propios estándares aparte). [Standard] es el que ya usaba casi toda
 * la pantalla y se queda como el default.
 * [Hero] = tamaño 1, la más grande (referencia: el micrófono y el 1er lugar del podio).
 * [Standard] = tamaño 3, el más común: el de casi todos los botones de la app.
 * [Secondary] = tamaño 2, más chica: opciones bloqueadas/secundarias, o rejillas densas de 2.
 * [Compact] = tamaño 6: como un [Standard] pero en rejilla de 2, para cuando el contenido no
 * necesita tanto espacio (ej. "Probar avisos") — más grande que [Secondary], más chica que
 * [Standard] a ancho completo.
 */
enum class BigBtnSize { Hero, Standard, Secondary, Compact }

/**
 * El botón recurrente de ancho completo del prototipo de Figma: una insignia de icono grande
 * arriba, una etiqueta en negrita debajo, y un subtítulo apagado opcional bajo esa — todo centrado.
 * Es el bloque de construcción principal de casi toda pantalla (Inicio, Iniciar sesión, Registro,
 * el menú de la sesión iniciada...), así que las pantallas deberían preferir este en vez de botones
 * improvisados para mantenerse visualmente consistentes.
 */
@Composable
fun BigBtn(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sub: String? = null,
    section: ModuleSection? = null,
    variant: BigBtnVariant = BigBtnVariant.Neutral,
    enabled: Boolean = true,
    /** Si el botón está deshabilitado, esto es lo que dice al tocarlo: nadie debe quedarse sin saber por qué no responde. */
    motivoDeshabilitado: String? = null,
    // Dos de estos comparten el ancho de una fila cuando la acción no es la prioridad de la pantalla
    // (p. ej. Iniciar sesión / Crear cuenta en Inicio) — coincide con el HalfBtn del prototipo de Figma.
    half: Boolean = false,
    size: BigBtnSize = BigBtnSize.Standard,
) {
    val seleccion = LocalSeleccionTarjeta.current
    val contexto = LocalContext.current
    val helper = LocalVoiceHelp.current
    val yo = remember { Any() }
    val foco = remember { FocusRequester() }
    var enFocoLocal by remember { mutableStateOf(false) }
    // Iluminada = es la tarjeta seleccionada de la pantalla (o, sin pantalla dividida, la que tiene el foco).
    val iluminado = if (seleccion != null) seleccion.actual === yo else enFocoLocal

    val colorVariante = when (variant) {
        BigBtnVariant.Danger -> CecapiError
        BigBtnVariant.Success -> CecapiSuccess
        BigBtnVariant.Accent -> CecapiAccent
        BigBtnVariant.Locked -> CecapiTextMuted
        BigBtnVariant.Neutral -> CecapiTextPrimary
    }
    // Con el color de su módulo, o el de su tipo, o el de acento si es un botón neutro.
    val colorIluminado = section?.color ?: if (variant == BigBtnVariant.Neutral) CecapiAccent else colorVariante
    // Las tarjetas de módulo son neutras y solo se iluminan con su color cuando están seleccionadas.
    val tint = if (section != null) (if (iluminado) section.color else CecapiTextPrimary) else colorVariante
    val borderColor = when {
        iluminado -> colorIluminado
        section != null -> CecapiBorder
        else -> when (variant) {
            BigBtnVariant.Danger -> CecapiError
            BigBtnVariant.Success -> CecapiSuccess
            BigBtnVariant.Accent -> CecapiAccent
            else -> CecapiBorder
        }
    }
    val borderWidth = when {
        iluminado -> 4.dp
        variant == BigBtnVariant.Neutral || section != null -> 1.dp
        else -> 2.dp
    }
    val fondo = if (iluminado) (section?.bg ?: CecapiSurfaceElevated) else CecapiSurface
    val iconBg = if (iluminado) colorIluminado.copy(alpha = 0.25f) else CecapiSurfaceElevated
    val shape = RoundedCornerShape(20.dp)
    val help = if (sub != null) "$label. $sub" else label
    val locked = variant == BigBtnVariant.Locked

    // La primera tarjeta de módulo de la pantalla nace seleccionada; si la seleccionada deja la
    // pantalla (cambio de submenú), la siguiente primera toma su lugar.
    if (seleccion != null) {
        DisposableEffect(seleccion) {
            onDispose { if (seleccion.actual === yo) seleccion.actual = null }
        }
        LaunchedEffect(seleccion) {
            val guardada = seleccion.clave
            // Al volver a la pantalla se recupera la tarjeta donde estaba la persona; si no hay ninguna
            // guardada, la primera tarjeta de módulo nace seleccionada.
            val tomarla = if (guardada != null) guardada == help else section != null && seleccion.actual == null
            // Un botón deshabilitado nunca nace seleccionado: se vería como "agarrado" sin poder usarse.
            if (enabled && tomarla && seleccion.actual == null) {
                seleccion.elegir(yo, help)
                seleccion.silenciar = true
                try {
                    foco.requestFocus()
                } catch (e: Exception) {
                    seleccion.silenciar = false
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(fondo)
            .border(borderWidth, borderColor, shape)
            .alpha(if (enabled) 1f else 0.6f)
            .focusRequester(foco)
            .onFocusChanged { estado ->
                if (estado.isFocused) {
                    if (seleccion != null) seleccion.elegir(yo, help) else enFocoLocal = true
                    if (seleccion?.silenciar == true) {
                        seleccion.silenciar = false
                    } else if (!lectorDePantallaActivo(contexto)) {
                        helper?.speak(help)
                    }
                } else if (seleccion == null) {
                    enFocoLocal = false
                }
            }
            .clickable(
                enabled = (enabled || motivoDeshabilitado != null) && !locked,
                onClick = {
                    if (enabled) {
                        seleccion?.elegir(yo, help)
                        onClick()
                    } else {
                        helper?.speak(motivoDeshabilitado.orEmpty())
                    }
                },
            )
            .semantics { contentDescription = help }
            .voiceHint(help, onHold = { seleccion?.elegir(yo, help) })
            .padding(
                top = if (half) 18.dp else 20.dp,
                start = if (half) 10.dp else 16.dp,
                end = if (half) 10.dp else 16.dp,
                bottom = if (half) 16.dp else 18.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (half) 10.dp else 12.dp),
    ) {
        val iconSize = when (size) {
            BigBtnSize.Hero -> if (half) 64.dp else 76.dp
            BigBtnSize.Standard -> if (half) 56.dp else 64.dp
            BigBtnSize.Compact -> if (half) 50.dp else 58.dp
            BigBtnSize.Secondary -> if (half) 44.dp else 50.dp
        }
        val iconShape = RoundedCornerShape(if (half) 16.dp else 18.dp)
        Box(
            modifier = Modifier.size(iconSize).clip(iconShape).background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (locked) Icons.Filled.Lock else icon,
                contentDescription = null,
                tint = if (locked) CecapiTextMuted else tint,
                modifier = Modifier.size(
                    when (size) {
                        BigBtnSize.Hero -> if (half) 44.dp else 48.dp
                        BigBtnSize.Standard -> if (half) 36.dp else 44.dp
                        BigBtnSize.Compact -> if (half) 30.dp else 36.dp
                        BigBtnSize.Secondary -> if (half) 26.dp else 30.dp
                    },
                ),
            )
        }
        Text(
            text = label,
            fontSize = when (size) {
                BigBtnSize.Hero -> if (half) 20.sp else 24.sp
                BigBtnSize.Standard -> if (half) 18.sp else 22.sp
                BigBtnSize.Compact -> if (half) 17.sp else 20.sp
                BigBtnSize.Secondary -> if (half) 15.sp else 17.sp
            },
            fontWeight = FontWeight.Black,
            lineHeight = if (half) 23.sp else 29.sp,
            color = tint,
            textAlign = TextAlign.Center,
        )
        if (sub != null && !half) {
            Text(
                text = sub,
                style = MaterialTheme.typography.bodyLarge,
                color = CecapiTextMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Un botón de icono grande con etiqueta para el encabezado de una pantalla (iniciar sesión, cerrar
 * sesión...). Pasa un [tint] diferente para acciones que deben resaltar, como el rojo de "Cerrar sesión".
 */
@Composable
fun TopAction(
    icon: ImageVector,
    label: String,
    help: String,
    onClick: () -> Unit,
    tint: Color = CecapiAccent,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = help }
            .voiceHint(help)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val iconShape = RoundedCornerShape(18.dp)
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(iconShape)
                .background(tint.copy(alpha = 0.14f))
                .border(2.dp, tint, iconShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(34.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (tint == CecapiAccent) CecapiTextMuted else tint,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Una frase que el usuario puede decir, también se puede tocar. Se muestra bajo "PUEDES DECIR". Suficientemente alta para tocarla (48 dp). */
@Composable
fun SuggestionChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(50))
            .background(CecapiSurfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "\"$label\"", color = CecapiAccent, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Algo que necesita la atención del usuario y que no se repite por voz en pantalla.
 * Un [actionLabel]/[onAction] opcional agrega un botón debajo del texto, para cuando hay
 * un lugar concreto a donde enviar a la persona en lugar de solo nombrar el problema.
 */
@Composable
fun NoticeBanner(
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CecapiWarning.copy(alpha = 0.15f))
            .padding(16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(text = text, color = CecapiWarning, style = MaterialTheme.typography.bodyLarge)
        if (actionLabel != null && onAction != null) {
            androidx.compose.material3.TextButton(
                onClick = onAction,
                modifier = Modifier.padding(top = 4.dp).voiceHint("$actionLabel. $text"),
            ) {
                Text(actionLabel, color = CecapiAccent)
            }
        }
    }
}

/** Se muestra solo mientras no hay internet: las respuestas serán menos precisas. */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    NoticeBanner("Sin conexión a internet. Mis respuestas pueden ser menos precisas.", modifier)
}
