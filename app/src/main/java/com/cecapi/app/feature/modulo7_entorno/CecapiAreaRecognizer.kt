package com.cecapi.app.feature.modulo7_entorno

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Reconoce el área del CECAPI comparando la foto con el índice precalculado.
 *
 * Es complementario: si el modelo o el índice faltan, o si no hay una coincidencia confiable,
 * devuelve null y no interrumpe la descripción normal.
 */
@Singleton
class CecapiAreaRecognizer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val candado = Mutex()
    private val indice: CecapiAreaIndex? by lazy { CecapiAreaIndex.cargar(context) }
    private val modelo: Modelo? by lazy { cargarModelo() }

    suspend fun reconocer(bitmap: Bitmap): String? {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return null
        val datos = indice ?: return null
        val red = modelo ?: return null

        return withContext(Dispatchers.Default) {
            candado.withLock {
                try {
                    val lado = red.lado
                    val escalado = Bitmap.createScaledBitmap(bitmap, lado, lado, true)
                    val pixeles = IntArray(lado * lado)
                    escalado.getPixels(pixeles, 0, lado, 0, 0, lado, lado)
                    if (escalado !== bitmap) escalado.recycle()

                    val entrada = CecapiAreaPreprocess.entrada(pixeles, lado, datos.normalization)

                    val salida = Array(1) { FloatArray(red.dimension) }
                    red.interprete.run(entrada, salida)

                    val vector = salida[0]
                    var norma = 0f
                    for (valor in vector) norma += valor * valor
                    norma = sqrt(norma)
                    if (norma <= 0f) return@withLock null
                    for (i in vector.indices) vector[i] /= norma

                    val area = CecapiAreaMatcher.buscar(vector, datos) ?: return@withLock null
                    Log.d(TAG, "Área reconocida: ${area.id}")
                    area.frase
                } catch (t: Throwable) {
                    Log.w(TAG, "No pude comparar el área: ${t.message}")
                    null
                }
            }
        }
    }

    private fun cargarModelo(): Modelo? {
        return try {
            val bytes = context.assets.open("cecapi_areas/embedder.tflite").use { it.readBytes() }
            val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
            buffer.put(bytes)
            buffer.rewind()
            val interprete = Interpreter(buffer, Interpreter.Options().setNumThreads(2))
            interprete.allocateTensors()

            val formaEntrada = interprete.getInputTensor(0).shape()
            val formaSalida = interprete.getOutputTensor(0).shape()
            val tipoEntrada = interprete.getInputTensor(0).dataType()
            val tipoSalida = interprete.getOutputTensor(0).dataType()

            if (tipoEntrada != DataType.FLOAT32 || tipoSalida != DataType.FLOAT32) {
                Log.w(TAG, "Modelo de áreas con tipo inesperado")
                return null
            }
            if (formaEntrada.size != 4 || formaEntrada[0] != 1 ||
                formaEntrada[1] != formaEntrada[2] || formaEntrada[3] != 3
            ) {
                Log.w(TAG, "Entrada inesperada del modelo de áreas: ${formaEntrada.contentToString()}")
                return null
            }
            if (formaSalida.size != 2 || formaSalida[0] != 1 || formaSalida[1] <= 0) {
                Log.w(TAG, "Salida inesperada del modelo de áreas: ${formaSalida.contentToString()}")
                return null
            }

            Log.i(
                TAG,
                "Modelo de áreas listo: ${formaEntrada[1]}x${formaEntrada[1]}, " +
                    "${formaSalida[1]} dimensiones.",
            )
            Modelo(
                lado = formaEntrada[1],
                dimension = formaSalida[1],
                interprete = interprete,
            )
        } catch (t: Throwable) {
            Log.i(TAG, "Modelo de áreas no disponible: ${t.message}")
            null
        }
    }

    private data class Modelo(
        val lado: Int,
        val dimension: Int,
        val interprete: Interpreter,
    )

    private companion object {
        const val TAG = "CecapiAreas"
    }
}
