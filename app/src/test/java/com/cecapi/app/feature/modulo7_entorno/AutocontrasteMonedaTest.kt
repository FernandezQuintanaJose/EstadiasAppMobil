package com.cecapi.app.feature.modulo7_entorno

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pruebas del autocontraste por canal que se aplica antes de clasificar una moneda. */
class AutocontrasteMonedaTest {

    private fun histograma(vararg pares: Pair<Int, Int>): IntArray {
        val h = IntArray(256)
        for ((valor, veces) in pares) h[valor] = veces
        return h
    }

    @Test
    fun `una imagen de un solo tono no se altera`() {
        val h = histograma(50 to 1000)
        val luts = AutocontrasteMoneda.calcular(arrayOf(h, h, h), 1000)
        for (valor in 0..255) assertEquals(valor, luts[0][valor])
    }

    @Test
    fun `una imagen oscura se estira a todo el rango`() {
        // 30 píxeles en 0, 940 en 60 y 30 en 120. Con recorte del 2 % quedan 0 y 120 como extremos.
        val h = histograma(0 to 30, 60 to 940, 120 to 30)
        val luts = AutocontrasteMoneda.calcular(arrayOf(h, h, h), 1000)
        assertEquals(0, luts[0][0])
        assertEquals(127, luts[0][60])
        assertEquals(255, luts[0][120])
        // Fuera del rango útil se recorta, nunca se desborda.
        assertEquals(255, luts[0][200])
    }

    @Test
    fun `cada canal usa su propio histograma`() {
        val rojo = histograma(100 to 995, 255 to 5)
        val verde = histograma(50 to 500, 150 to 500)
        val azul = histograma(10 to 1000)
        val luts = AutocontrasteMoneda.calcular(arrayOf(rojo, verde, azul), 1000)
        // El rojo está casi plano: se queda igual.
        for (valor in 0..255) assertEquals(valor, luts[0][valor])
        // El verde ocupa 50..150: se estira.
        assertEquals(0, luts[1][50])
        assertEquals(127, luts[1][100])
        assertEquals(255, luts[1][150])
        // El azul es de un solo tono: no cambia.
        for (valor in 0..255) assertEquals(valor, luts[2][valor])
    }

    @Test
    fun `sin recorte usa el minimo y el maximo reales`() {
        val h = histograma(20 to 10, 100 to 980, 200 to 10)
        val luts = AutocontrasteMoneda.calcular(arrayOf(h, h, h), 1000, porcentaje = 0.0)
        assertEquals(0, luts[0][20])
        assertEquals(113, luts[0][100])
        assertEquals(255, luts[0][200])
        assertEquals(255, luts[0][255])
    }

    @Test
    fun `las tres tablas tienen 256 valores`() {
        val h = histograma(80 to 1000)
        val luts = AutocontrasteMoneda.calcular(arrayOf(h, h, h), 1000)
        assertEquals(3, luts.size)
        for (lut in luts) assertEquals(256, lut.size)
        assertArrayEquals(IntArray(256) { it }, luts[0])
    }
}
