package com.cecapi.app.feature.modulo7_entorno

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Convierte los píxeles ya escalados al tensor de entrada del modelo.
 *
 * Se separa del reconocedor para poder probar la normalización sin cargar el modelo nativo.
 * El índice debe usar exactamente la misma normalización que aquí.
 */
internal object CecapiAreaPreprocess {
    const val NORMALIZACION_MENOS_UNO_UNO = "minus1_1"

    fun entrada(pixeles: IntArray, lado: Int, normalizacion: String): ByteBuffer {
        require(lado > 0 && pixeles.size >= lado * lado) { "Píxeles insuficientes" }

        val buffer = ByteBuffer.allocateDirect(lado * lado * 3 * 4)
            .order(ByteOrder.nativeOrder())
        var posicion = 0
        for (y in 0 until lado) {
            for (x in 0 until lado) {
                val color = pixeles[posicion++]
                val r = ((color shr 16) and 0xFF).toFloat()
                val g = ((color shr 8) and 0xFF).toFloat()
                val b = (color and 0xFF).toFloat()
                if (normalizacion == NORMALIZACION_MENOS_UNO_UNO) {
                    buffer.putFloat(r / 127.5f - 1f)
                    buffer.putFloat(g / 127.5f - 1f)
                    buffer.putFloat(b / 127.5f - 1f)
                } else {
                    buffer.putFloat(r / 255f)
                    buffer.putFloat(g / 255f)
                    buffer.putFloat(b / 255f)
                }
            }
        }
        buffer.rewind()
        return buffer
    }
}
