package com.cecapi.app.feature.modulo7_entorno

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** Un área del CECAPI con la frase que se dice y su bloque de vectores en el índice. */
data class CecapiArea(
    val id: String,
    val frase: String,
    val offset: Int,
    val count: Int,
)

/**
 * Índice precalculado de las áreas del CECAPI.
 *
 * Los vectores ya vienen normalizados y las fotografías originales no se guardan en la app.
 * Si el índice no existe o está dañado, se devuelve null y el resto del análisis sigue igual.
 */
data class CecapiAreaIndex(
    val areas: List<CecapiArea>,
    val embeddings: Array<FloatArray>,
    val areaIndices: IntArray,
    val dimension: Int,
    val threshold: Float,
    val margin: Float,
    val topK: Int,
    val normalization: String,
) {
    companion object {
        private const val TAG = "CecapiAreas"
        private const val VERSION = 1
        private const val MAGIC = "CAPI"
        private const val HEADER_BYTES = 36

        fun cargar(context: Context): CecapiAreaIndex? {
            return try {
                val json = context.assets.open("cecapi_areas/areas.json")
                    .bufferedReader()
                    .use { it.readText() }
                val binario = context.assets.open("cecapi_areas/embeddings.bin")
                    .use { it.readBytes() }
                parse(json, binario)
            } catch (t: Throwable) {
                Log.i(TAG, "Índice de áreas no disponible: ${t.message}")
                null
            }
        }

        internal fun parse(json: String, binario: ByteArray): CecapiAreaIndex? {
            return try {
                val obj = JSONObject(json)
                if (obj.optInt("version", 0) != VERSION) return null

                val areasJson = obj.optJSONArray("areas") ?: return null
                val areas = ArrayList<CecapiArea>(areasJson.length())
                for (i in 0 until areasJson.length()) {
                    val area = areasJson.getJSONObject(i)
                    val id = area.optString("id")
                    val frase = area.optString("frase")
                    val offset = area.optInt("offset", -1)
                    val count = area.optInt("count", 0)
                    if (id.isBlank() || frase.isBlank() || offset < 0 || count <= 0) {
                        return null
                    }
                    areas.add(CecapiArea(id, frase, offset, count))
                }
                if (areas.isEmpty()) return null

                val threshold = obj.optDouble("threshold", 0.75).toFloat()
                val margin = obj.optDouble("margin", 0.08).toFloat()
                val topK = obj.optInt("topK", 3).coerceAtLeast(1)
                val normalization = obj.optString("normalization", "minus1_1")

                val buffer = ByteBuffer.wrap(binario).order(ByteOrder.LITTLE_ENDIAN)
                if (binario.size < HEADER_BYTES) return null
                val magic = ByteArray(4)
                buffer.get(magic)
                if (String(magic, Charsets.US_ASCII) != MAGIC) return null

                val version = buffer.int
                val dim = buffer.int
                val count = buffer.int
                val areaCount = buffer.int
                buffer.float // umbral del archivo; la configuración se toma del JSON
                buffer.float // margen del archivo
                buffer.int   // topK del archivo
                buffer.int   // código de normalización

                if (version != VERSION || dim <= 0 || count <= 0) return null
                if (areaCount != areas.size) return null
                if (count != areas.sumOf { it.count }) return null
                if (binario.size != HEADER_BYTES + count * dim * 4 + count * 4) return null

                val embeddings = Array(count) { FloatArray(dim) }
                for (fila in 0 until count) {
                    var norma = 0f
                    for (columna in 0 until dim) {
                        val valor = buffer.float
                        embeddings[fila][columna] = valor
                        norma += valor * valor
                    }
                    norma = sqrt(norma)
                    if (norma <= 0f) return null
                    for (columna in 0 until dim) {
                        embeddings[fila][columna] /= norma
                    }
                }

                val areaIndices = IntArray(count)
                for (fila in 0 until count) {
                    val indice = buffer.int
                    if (indice !in 0 until areaCount) return null
                    areaIndices[fila] = indice
                }

                // Comprueba que cada bloque de área apunte a los vectores declarados.
                for ((indiceArea, area) in areas.withIndex()) {
                    if (area.offset + area.count > count) return null
                    for (fila in area.offset until area.offset + area.count) {
                        if (areaIndices[fila] != indiceArea) return null
                    }
                }

                CecapiAreaIndex(
                    areas = areas,
                    embeddings = embeddings,
                    areaIndices = areaIndices,
                    dimension = dim,
                    threshold = threshold,
                    margin = margin,
                    topK = topK,
                    normalization = normalization,
                )
            } catch (t: Throwable) {
                Log.i(TAG, "No pude leer el índice de áreas: ${t.message}")
                null
            }
        }
    }
}
