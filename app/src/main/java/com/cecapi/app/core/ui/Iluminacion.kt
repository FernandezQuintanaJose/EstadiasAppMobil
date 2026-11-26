package com.cecapi.app.core.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiBorder

/** Grosor del borde del recuadro seleccionado: el mismo de las tarjetas de los menús. */
private val ANCHO_ILUMINADO = 4.dp

/**
 * El comportamiento de las tarjetas de los menús para cualquier otro recuadro (Volver, campos, interruptores,
 * paneles de la cámara...): solo se ilumina el que tiene el foco o se está tocando, y al iluminarse uno se apaga
 * el anterior. Reemplaza al `.border(...)` del recuadro y va antes de `.clickable`.
 *
 * [color] es el de iluminado; [colorNormal] y [anchoNormal] son el borde cuando no está seleccionado.
 */
@Composable
fun Modifier.iluminable(
    nombre: String,
    forma: Shape,
    color: Color = CecapiAccent,
    colorNormal: Color = CecapiBorder,
    anchoNormal: Dp = 1.dp,
    /** Para un borde normal con degradado (las alertas de Gestión); si se da, sustituye a [colorNormal]. */
    brochaNormal: Brush? = null,
): Modifier {
    val seleccion = LocalSeleccionTarjeta.current
    val yo = remember { Any() }
    var enFocoLocal by remember { mutableStateOf(false) }
    val nombreActual by rememberUpdatedState(nombre)
    val iluminado = if (seleccion != null) seleccion.actual === yo else enFocoLocal

    if (seleccion != null) {
        DisposableEffect(seleccion) { onDispose { if (seleccion.actual === yo) seleccion.actual = null } }
    }

    return this
        .border(
            if (iluminado) ANCHO_ILUMINADO else anchoNormal,
            if (iluminado) SolidColor(color) else brochaNormal ?: SolidColor(colorNormal),
            forma,
        )
        // hasFocus y no isFocused: en los campos y botones de Material el foco lo toma un hijo.
        .onFocusChanged { estado ->
            if (estado.hasFocus) {
                if (seleccion != null) seleccion.elegir(yo, nombreActual) else enFocoLocal = true
            } else if (seleccion == null) {
                enFocoLocal = false
            }
        }
        // Tocar no mueve el foco en Android: se elige al completar el toque (al desplazar la lista no), sin consumirlo.
        .pointerInput(seleccion) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (waitForUpOrCancellation(PointerEventPass.Initial) != null) {
                    if (seleccion != null) seleccion.elegir(yo, nombreActual) else enFocoLocal = true
                }
            }
        }
}

/** Para las pantallas sin micrófono dividido (las de la cámara): comparten una sola selección. */
@Composable
fun ConSeleccionDeTarjetas(contenido: @Composable () -> Unit) {
    val clave = rememberSaveable { mutableStateOf<String?>(null) }
    val seleccion = remember { SeleccionTarjeta(clave) }
    CompositionLocalProvider(LocalSeleccionTarjeta provides seleccion, content = contenido)
}
