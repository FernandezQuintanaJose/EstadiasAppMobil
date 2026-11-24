package com.cecapi.app.feature.modulo7_entorno

import android.content.ContextWrapper
import android.content.res.AssetManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
class CecapiAreaIndexTest {

    private val json = """
        {
          "version": 1,
          "threshold": 0.75,
          "margin": 0.08,
          "topK": 1,
          "normalization": "minus1_1",
          "areas": [
            {"id": "A", "frase": "Te encuentras en el área A del CECAPI.", "offset": 0, "count": 1},
            {"id": "B", "frase": "Te encuentras en el área B del CECAPI.", "offset": 1, "count": 1}
          ]
        }
    """.trimIndent()

    private fun binario(magic: String = "CAPI", truncar: Boolean = false): ByteArray {
        val buffer = ByteBuffer.allocate(68).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(magic.toByteArray(Charsets.US_ASCII))
        buffer.putInt(1)
        buffer.putInt(3)
        buffer.putInt(2)
        buffer.putInt(2)
        buffer.putFloat(0.75f)
        buffer.putFloat(0.08f)
        buffer.putInt(1)
        buffer.putInt(1)
        buffer.putFloat(1f); buffer.putFloat(0f); buffer.putFloat(0f)
        buffer.putFloat(0f); buffer.putFloat(1f); buffer.putFloat(0f)
        buffer.putInt(0)
        buffer.putInt(1)
        val bytes = buffer.array()
        return if (truncar) bytes.copyOf(30) else bytes
    }

    @Test
    fun `lee un indice valido`() {
        val indice = CecapiAreaIndex.parse(json, binario())
        assertNotNull(indice)
        assertEquals(3, indice!!.dimension)
        assertEquals(2, indice.areas.size)
        assertEquals("A", indice.areas[0].id)
        assertEquals(1f, indice.embeddings[0][0], 0.0001f)
        assertEquals(0.75f, indice.threshold, 0.0001f)
        assertEquals("minus1_1", indice.normalization)
    }

    @Test
    fun `rechaza magia incorrecta`() {
        assertNull(CecapiAreaIndex.parse(json, binario(magic = "XXXX")))
    }

    @Test
    fun `rechaza binario truncado`() {
        assertNull(CecapiAreaIndex.parse(json, binario(truncar = true)))
    }

    @Test
    fun `sin assets el indice no rompe`() {
        val sinAssets = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getAssets(): AssetManager = throw IllegalStateException("sin assets")
        }
        assertNull(CecapiAreaIndex.cargar(sinAssets))
    }
}
