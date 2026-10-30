package com.cecapi.app.core.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import com.cecapi.app.core.voice.VoiceEngine

/** Lets any composable make the assistant say something out loud without knowing about the engine. */
class VoiceHelp(private val voiceEngine: VoiceEngine) {
    fun speak(text: String) = voiceEngine.speak(text)
}

val LocalVoiceHelp = staticCompositionLocalOf<VoiceHelp?> { null }

/**
 * Makes a control explain itself out loud, for people who cannot see it:
 * - [focusLabel]: said when the control is selected (a text field gets focus), to say where they are;
 * - [help]: said when the finger stays pressed on it, to say what it is and what it does;
 * - [onHold]: also called when the finger stays pressed (used to light up the card that was held).
 * The press is only observed, never consumed, so a tap still does exactly what it did before.
 */
@Composable
fun Modifier.voiceHint(help: String, focusLabel: String? = null, onHold: (() -> Unit)? = null): Modifier {
    val helper = LocalVoiceHelp.current
    val withHold = pointerInput(help, onHold) {
        awaitEachGesture {
            val toque = awaitFirstDown(requireUnconsumed = false)
            // awaitLongPressOrCancellation tolera el temblor normal del dedo (el margen de movimiento de Android): antes,
            // cualquier movimiento mínimo cancelaba la explicación y el botón parecía no reaccionar. El toque no se consume.
            val sostenido = awaitLongPressOrCancellation(toque.id)
            if (sostenido != null) {
                helper?.speak(help)
                onHold?.invoke()
            }
        }
    }
    return if (focusLabel == null) {
        withHold
    } else {
        withHold.onFocusChanged { if (it.isFocused) helper?.speak(focusLabel) }
    }
}
