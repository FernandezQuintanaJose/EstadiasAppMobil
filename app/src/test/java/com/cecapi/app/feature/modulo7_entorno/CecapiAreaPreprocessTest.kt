package com.cecapi.app.feature.modulo7_entorno

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CecapiAreaPreprocessTest {

    private val pixeles = intArrayOf(
        0x00FF0000, // rojo
        0x0000FF00, // verde
        0x000000FF, // azul
        0x00FFFFFF, // blanco
    )

    @Test
    fun `normaliza de 0 a 1 en orden de pixeles`() {
        val buffer = CecapiAreaPreprocess.entrada(pixeles, 2, "0_1")
        assertEquals(2 * 2 * 3 * 4, buffer.capacity())

        val valores = FloatArray(12) { buffer.float }
        assertArrayEquals(
            floatArrayOf(
                1f, 0f, 0f,
                0f, 1f, 0f,
                0f, 0f, 1f,
                1f, 1f, 1f,
            ),
            valores,
            0.0001f,
        )
    }

    @Test
    fun `normaliza de menos uno a uno cuando el indice lo pide`() {
        val buffer = CecapiAreaPreprocess.entrada(pixeles, 2, "minus1_1")
        val valores = FloatArray(12) { buffer.float }
        assertArrayEquals(
            floatArrayOf(
                1f, -1f, -1f,
                -1f, 1f, -1f,
                -1f, -1f, 1f,
                1f, 1f, 1f,
            ),
            valores,
            0.0001f,
        )
    }
}
