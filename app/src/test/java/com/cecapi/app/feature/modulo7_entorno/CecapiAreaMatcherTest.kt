package com.cecapi.app.feature.modulo7_entorno

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.sqrt

class CecapiAreaMatcherTest {

    private val areaA = CecapiArea("A", "Te encuentras en el área A del CECAPI.", 0, 1)
    private val areaB = CecapiArea("B", "Te encuentras en el área B del CECAPI.", 1, 1)

    private fun indice(
        threshold: Float = 0.75f,
        margin: Float = 0.08f,
        topK: Int = 1,
    ) = CecapiAreaIndex(
        areas = listOf(areaA, areaB),
        embeddings = arrayOf(
            floatArrayOf(1f, 0f, 0f),
            floatArrayOf(0f, 1f, 0f),
        ),
        areaIndices = intArrayOf(0, 1),
        dimension = 3,
        threshold = threshold,
        margin = margin,
        topK = topK,
        normalization = "minus1_1",
    )

    private fun normalizado(vararg valores: Float): FloatArray {
        val norma = sqrt(valores.sumOf { (it * it).toDouble() }).toFloat()
        return FloatArray(valores.size) { valores[it] / norma }
    }

    @Test
    fun `elige el area correcta`() {
        val area = CecapiAreaMatcher.buscar(normalizado(1f, 0f, 0f), indice())
        assertEquals("A", area?.id)
    }

    @Test
    fun `no inventa si la similitud es baja`() {
        assertNull(CecapiAreaMatcher.buscar(normalizado(0.5f, 0.5f, 0f), indice()))
        assertNull(CecapiAreaMatcher.buscar(normalizado(0f, 0f, 1f), indice()))
    }

    @Test
    fun `exige margen sobre la segunda area`() {
        assertNull(
            CecapiAreaMatcher.buscar(
                normalizado(0.8f, 0.6f, 0f),
                indice(threshold = 0.5f, margin = 0.5f),
            ),
        )
    }
}
