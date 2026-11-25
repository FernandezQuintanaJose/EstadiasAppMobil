package com.cecapi.app.core.voice

/**
 * Decide si el micrófono oye a una persona hablando encima del asistente (interrupción por voz).
 *
 * El monitor recibe el volumen (RMS) de cada cuadro de audio mientras el asistente habla. Durante los primeros
 * [asentamientoMs] solo mide: ahí queda registrado el ruido del cuarto y también lo que se cuela de la propia voz
 * del asistente por la bocina, que el cancelador de eco no siempre quita (la voz sale como accesibilidad). El umbral
 * se fija sobre el **pico** de esa medición, no sobre su promedio, porque la voz del asistente sube y baja y un
 * promedio quedaba por debajo de sus propios picos: así se cortaba sola a media descripción.
 * Después, solo interrumpe si el volumen supera el umbral de forma sostenida durante [sostenidoMs].
 */
class DetectorInterrupcion(
    private val cuadroMs: Int,
    private val asentamientoMs: Long = ASENTAMIENTO_MS,
    private val sostenidoMs: Int = SOSTENIDO_MS,
    private val umbralMinimo: Double = UMBRAL_MINIMO,
    private val margenSobrePico: Double = MARGEN_SOBRE_PICO,
) {
    private var transcurridoMs = 0L
    private var picoInicial = 0.0
    private var asentado = false
    private var fuerteMs = 0

    /** Umbral actual de volumen; antes de asentarse es el mínimo. */
    var umbral: Double = umbralMinimo
        private set

    /** Procesa un cuadro de audio con su volumen [rms]. Devuelve true cuando hay que dejar de hablar y escuchar. */
    fun procesar(rms: Double): Boolean {
        transcurridoMs += cuadroMs
        if (transcurridoMs <= asentamientoMs) {
            picoInicial = maxOf(picoInicial, rms)
            return false
        }
        if (!asentado) {
            asentado = true
            umbral = maxOf(umbralMinimo, picoInicial * margenSobrePico)
        }
        fuerteMs = if (rms > umbral) fuerteMs + cuadroMs else maxOf(0, fuerteMs - cuadroMs / 2)
        return fuerteMs >= sostenidoMs
    }

    companion object {
        /** Tiempo que solo mide, para incluir el arranque de la voz del asistente y el ruido del cuarto. */
        const val ASENTAMIENTO_MS = 1_500L

        /** Cuánto debe durar el volumen alto para contar como una persona hablando. */
        const val SOSTENIDO_MS = 600

        /** El habla normal cerca del teléfono está en los miles bajos (PCM de 16 bits). */
        const val UMBRAL_MINIMO = 1_800.0

        const val MARGEN_SOBRE_PICO = 1.4

        /** Sin cancelador de eco la propia voz se cuela más: se pide más margen y más duración. */
        const val SOSTENIDO_SIN_ECO_MS = 900
        const val MARGEN_SIN_ECO = 2.0
    }
}
