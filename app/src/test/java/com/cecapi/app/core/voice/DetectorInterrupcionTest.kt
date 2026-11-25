package com.cecapi.app.core.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** La interrupción por voz no debe cortar al asistente con su propia voz ni con un ruido corto. */
class DetectorInterrupcionTest {

    private fun alimentar(d: DetectorInterrupcion, rms: Double, cuadros: Int): Boolean {
        var disparo = false
        repeat(cuadros) { if (d.procesar(rms)) disparo = true }
        return disparo
    }

    @Test
    fun laPropiaVozDelAsistenteConstanteNoInterrumpe() {
        val d = DetectorInterrupcion(cuadroMs = 50)
        // 20 s de una voz que se cuela a volumen estable, con picos del doble en medio.
        assertFalse(alimentar(d, 2_500.0, 30))
        assertFalse(alimentar(d, 2_800.0, 400))
        assertEquals(3_500.0, d.umbral, 0.01)
    }

    @Test
    fun lasSubidasDeLaPropiaVozDentroDelMargenNoInterrumpen() {
        val d = DetectorInterrupcion(cuadroMs = 50)
        alimentar(d, 2_500.0, 30)
        // Un pico de 3 000, por debajo de 2 500 * 1,4 = 3 500, aunque dure varios segundos.
        assertFalse(alimentar(d, 3_000.0, 200))
    }

    @Test
    fun unRuidoCortoNoInterrumpe() {
        val d = DetectorInterrupcion(cuadroMs = 50)
        alimentar(d, 200.0, 30)
        // 250 ms fuertes: menos que el tiempo sostenido de 600 ms.
        assertFalse(alimentar(d, 9_000.0, 5))
        assertFalse(alimentar(d, 200.0, 20))
    }

    @Test
    fun unaPersonaQueHablaEncimaSiInterrumpe() {
        val d = DetectorInterrupcion(cuadroMs = 50)
        alimentar(d, 200.0, 30)
        assertTrue(alimentar(d, 9_000.0, 20))
    }

    @Test
    fun nuncaInterrumpeMientrasSoloMideElRuidoDelCuarto() {
        val d = DetectorInterrupcion(cuadroMs = 50)
        // 1 s a volumen muy alto, dentro de los 1 500 ms de medición.
        assertFalse(alimentar(d, 20_000.0, 20))
    }

    @Test
    fun sinCanceladorDeEcoPideMasMargenYMasDuracion() {
        val d = DetectorInterrupcion(
            cuadroMs = 50,
            sostenidoMs = DetectorInterrupcion.SOSTENIDO_SIN_ECO_MS,
            margenSobrePico = DetectorInterrupcion.MARGEN_SIN_ECO,
        )
        // 30 cuadros de medición y uno más, que ya fija el umbral.
        alimentar(d, 2_500.0, 31)
        assertEquals(5_000.0, d.umbral, 0.01)
        // 4 000 supera el umbral con eco (3 500) pero no el de sin eco (5 000).
        assertFalse(alimentar(d, 4_000.0, 200))
    }
}
