package com.cecapi.app.core.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Escucha continua del nombre del asistente con Vosk, en el teléfono y sin internet (prueba). Solo detecta el
 * nombre y las órdenes de control; lo que la persona pide después lo atiende el reconocedor de siempre.
 * El vocabulario está limitado, así que lo demás nunca se convierte en texto, y no se guarda ni se anota nada.
 *
 * Solo se usa con el nombre original, el único probado. Con un apodo, con "hola" activado, sin el modelo o si
 * algo falla, el motor sigue con la escucha anterior. Se llama siempre desde el hilo principal.
 */
@Singleton
class DetectorDeNombre @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val principal = Handler(Looper.getMainLooper())
    private var modelo: Model? = null
    private var cargando = false
    private var descartado = false
    private var servicio: SpeechService? = null

    /** True si el modelo ya está cargado y estos nombres son los probados. La primera vez empieza a cargarlo. */
    fun puedeEscuchar(nombres: Set<String>): Boolean {
        if (descartado || nombres != setOf(NOMBRE_PROBADO)) return false
        if (modelo == null) cargar()
        return modelo != null
    }

    /** Empieza a escuchar; false si no se pudo (entonces se usa la escucha anterior). [alOir] llega en el hilo principal. */
    fun escuchar(alOir: (FraseDeActivacion.Accion) -> Unit): Boolean {
        val listo = modelo ?: return false
        if (servicio != null) return true
        return try {
            val opciones = JSONArray().put(NOMBRE_PROBADO)
            (SENUELOS + FraseDeActivacion.ORDENES).forEach { opciones.put(it) }
            val reconocedor = Recognizer(listo, MUESTRAS_POR_SEGUNDO, opciones.put("[unk]").toString())
            reconocedor.setWords(true)
            val nuevo = SpeechService(reconocedor, MUESTRAS_POR_SEGUNDO)
            servicio = nuevo
            nuevo.startListening(oyente(nuevo, alOir))
            Log.i(TAG, "Detector local: escuchando el nombre")
            true
        } catch (e: Exception) {
            // Casi siempre: otra app tiene el micrófono. Esta vez se usa la escucha anterior.
            Log.w(TAG, "No pude escuchar con el detector local", e)
            parar()
            false
        }
    }

    /** Suelta el micrófono. Al volver, ningún aviso pendiente de esta escucha se atiende. */
    fun parar() {
        val actual = servicio ?: return
        servicio = null
        runCatching {
            actual.stop()
            actual.shutdown()
        }.onFailure { Log.w(TAG, "No pude soltar el micrófono", it) }
    }

    private fun oyente(propio: SpeechService, alOir: (FraseDeActivacion.Accion) -> Unit) = object : RecognitionListener {
        override fun onResult(hipotesis: String) {
            if (servicio !== propio) return
            val palabras = runCatching {
                val lista = JSONObject(hipotesis).optJSONArray("result") ?: return
                (0 until lista.length()).map { lista.getJSONObject(it).optString("word") }
            }.getOrNull() ?: return
            val accion = FraseDeActivacion.decidir(palabras, NOMBRE_PROBADO)
            if (accion != FraseDeActivacion.Accion.NADA) {
                Log.i(TAG, "Detector local: $accion")
                principal.post { if (servicio === propio) alOir(accion) }
            }
        }

        override fun onPartialResult(hipotesis: String) = Unit
        override fun onFinalResult(hipotesis: String) = Unit
        override fun onTimeout() = Unit

        override fun onError(e: Exception) {
            Log.w(TAG, "El detector local falló; se usa la escucha anterior", e)
            descartado = true
            principal.post { parar() }
        }
    }

    private fun cargar() {
        if (cargando) return
        cargando = true
        val incluido = runCatching { context.assets.list(CARPETA)?.isNotEmpty() == true }.getOrDefault(false)
        if (!incluido) {
            Log.i(TAG, "Esta instalación no trae el modelo de voz (tarea descargarModeloDeVoz)")
            descartado = true
            return
        }
        try {
            StorageService.unpack(
                context, CARPETA, "modelo-voz",
                { listo -> modelo = listo },
                { error ->
                    Log.w(TAG, "No cargó el modelo de voz", error)
                    descartado = true
                },
            )
        } catch (t: Throwable) {
            // Incluye que falte la librería nativa en este procesador.
            Log.w(TAG, "El detector local no está disponible en este teléfono", t)
            descartado = true
        }
    }

    private companion object {
        const val TAG = "CecapiVoice"
        const val CARPETA = "vosk-es"
        const val NOMBRE_PROBADO = "alia"
        const val MUESTRAS_POR_SEGUNDO = 16_000f

        /** Palabras parecidas: sin ellas el reconocedor las convertía en "alia" con confianza 100. */
        val SENUELOS = listOf(
            "había", "al día", "día", "italia", "maría", "allá", "allí", "alias", "julia", "salida", "salía", "valía",
            "habían", "haya", "al", "ella", "hola", "area", "aria", "amalia", "natalia", "australia",
        )
    }
}
