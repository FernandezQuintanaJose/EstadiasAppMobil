package com.cecapi.app.feature.modulo7_entorno

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Comprueba que el índice empaquetado en assets se pueda leer tal cual.
 * Si se regenera el índice con otro modelo o dimensión, esta prueba lo detecta.
 */
@RunWith(RobolectricTestRunner::class)
class CecapiAreaIndexAssetsTest {

    @Test
    fun `el indice real se lee completo`() {
        val indice = CecapiAreaIndex.cargar(RuntimeEnvironment.getApplication())
        assertNotNull(indice)
        assertEquals(7, indice!!.areas.size)
        assertEquals(373, indice.embeddings.size)
        assertEquals(1280, indice.dimension)
        assertEquals(0.80f, indice.threshold, 0.0001f)
        assertEquals(0.08f, indice.margin, 0.0001f)
        assertEquals("0_1", indice.normalization)
        assertEquals(indice.embeddings.size, indice.areaIndices.size)
    }
}
