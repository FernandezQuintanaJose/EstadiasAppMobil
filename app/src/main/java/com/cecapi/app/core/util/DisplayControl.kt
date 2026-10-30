package com.cecapi.app.core.util

import com.cecapi.app.core.voice.DeviceSettings
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * La pantalla negra y el brillo mínimo, para personas que no usan la imagen.
 * Ambos solo se dibujan o se aplican mientras la app está en pantalla (el brillo se establece en la
 * ventana propia de la app, nunca en la configuración del sistema del teléfono), así que salir de la
 * app siempre devuelve el teléfono a la normalidad. La persona siempre debe tener una salida, así que
 * ambos se apagan por voz en toda pantalla, y la pantalla negra también se apaga manteniendo un dedo sobre ella.
 */
@Singleton
class DisplayControl @Inject constructor(
    private val deviceSettings: DeviceSettings,
    private val voiceEngine: VoiceEngine,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        // Volver a la normalidad desde la pantalla negra o el brillo mínimo siempre debe funcionar, incluso en una
        // pantalla que está capturando texto en bruto (un usuario, una contraseña, una respuesta en Documentos):
        // de otro modo las propias instrucciones de la pantalla negra ("di pantalla normal") dejarían de funcionar
        // justo en el momento en que más se necesitan.
        voiceEngine.addCriticalSpeechInterceptor { spoken ->
            val text = VoiceText.normalize(spoken)
            val reply = when {
                VoiceText.hasAny(text, BLACK_OFF_PHRASES) -> {
                    scope.launch { deviceSettings.setBlackScreen(false) }
                    BLACK_OFF
                }
                VoiceText.hasAny(text, BRIGHTNESS_NORMAL_PHRASES) -> {
                    scope.launch { deviceSettings.setMinBrightness(false) }
                    BRIGHTNESS_NORMAL
                }
                else -> null
            }
            if (reply != null) {
                voiceEngine.activate() // "silencio" o "para" no debe impedir que funcione esta salida de seguridad
                voiceEngine.speak(reply, force = true)
            }
            reply != null
        }
    }

    fun setBlackScreen(enabled: Boolean, announce: Boolean = true) {
        scope.launch { deviceSettings.setBlackScreen(enabled) }
        if (announce) voiceEngine.speak(if (enabled) BLACK_ON else BLACK_OFF)
    }

    fun setMinBrightness(enabled: Boolean, announce: Boolean = true) {
        scope.launch { deviceSettings.setMinBrightness(enabled) }
        if (announce) voiceEngine.speak(if (enabled) BRIGHTNESS_MIN else BRIGHTNESS_NORMAL)
    }

    /** La respuesta hablada cuando [spoken] es un comando de pantalla, o null cuando es otra cosa. */
    fun handle(spoken: String): String? {
        val text = VoiceText.normalize(spoken)
        return when {
            BLACK_OFF_PHRASES.let { phrases -> VoiceText.hasAny(text, phrases) } -> {
                scope.launch { deviceSettings.setBlackScreen(false) }
                BLACK_OFF
            }
            BLACK_ON_PHRASES.let { phrases -> VoiceText.hasAny(text, phrases) } -> {
                scope.launch { deviceSettings.setBlackScreen(true) }
                BLACK_ON
            }
            BRIGHTNESS_NORMAL_PHRASES.let { phrases -> VoiceText.hasAny(text, phrases) } -> {
                scope.launch { deviceSettings.setMinBrightness(false) }
                BRIGHTNESS_NORMAL
            }
            BRIGHTNESS_MIN_PHRASES.let { phrases -> VoiceText.hasAny(text, phrases) } -> {
                scope.launch { deviceSettings.setMinBrightness(true) }
                BRIGHTNESS_MIN
            }
            else -> null
        }
    }

    companion object {
        /** El nivel más bajo que no apaga la pantalla en teléfonos que leen 0 como "apagado". */
        const val MIN_BRIGHTNESS = 0.01f

        const val BLACK_ON =
            "Pantalla negra activada. Toca la pantalla una vez para hablarme. " +
                "Para volver, di pantalla normal, o mantén presionada la pantalla."
        const val BLACK_OFF = "Pantalla normal."
        const val BRIGHTNESS_MIN = "Brillo al mínimo. Para volver, di brillo normal."
        const val BRIGHTNESS_NORMAL = "Brillo normal."

        // Las frases de "apagar" se revisan primero: "apaga la pantalla negra" también contiene "pantalla negra".
        private val BLACK_OFF_PHRASES = listOf(
            "pantalla normal", "pantalla visible", "muestra la pantalla", "mostrar la pantalla", "muestrame la pantalla",
            "quita la pantalla negra", "quitar la pantalla negra", "desactiva la pantalla negra", "desactivar la pantalla negra",
            "apaga la pantalla negra", "sin pantalla negra", "enciende la pantalla", "prende la pantalla",
            "pantalla en normal", "salir de pantalla negra",
        )
        private val BLACK_ON_PHRASES = listOf(
            "pantalla negra", "pantalla en negro", "pon la pantalla en negro", "oscurece la pantalla", "pantalla oscura",
        )
        private val BRIGHTNESS_NORMAL_PHRASES = listOf(
            "brillo normal", "brillo automatico", "brillo maximo", "brillo alto", "sube el brillo", "subir el brillo",
            "quita el brillo minimo", "desactiva el brillo minimo",
        )
        private val BRIGHTNESS_MIN_PHRASES = listOf(
            "brillo minimo", "brillo bajo", "baja el brillo", "bajar el brillo", "brillo al minimo", "minimo brillo",
            "activa el brillo minimo",
        )
    }
}
