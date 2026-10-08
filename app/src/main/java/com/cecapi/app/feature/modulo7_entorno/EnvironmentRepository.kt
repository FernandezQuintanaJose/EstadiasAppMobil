package com.cecapi.app.feature.modulo7_entorno

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector as TfliteObjectDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class EnvironmentResult(val escaneoId: Long?, val descripcion: String, val etiquetas: List<String>)

/** Un objeto ya nombrado en español, con su color y dónde está en la foto. */
private data class ObjetoDescrito(
    val nombre: NombreEs,
    val color: String?,
    val posicion: String?,
    val confianza: Float,
    val area: Int,
) {
    /** "silla negra" (sin artículo ni posición), para guardar en la base. */
    val etiqueta: String get() = if (color != null) "${nombre.texto} $color" else nombre.texto

    /** "una silla negra a la izquierda", para decirlo en voz alta. */
    val frase: String
        get() = buildString {
            append(nombre.conArticulo)
            if (color != null) append(" ").append(color)
            if (posicion != null) append(" ").append(posicion)
        }
}

/**
 * Describe lo que hay enfrente sin internet con un detector local de objetos y un etiquetador de respaldo.
 */
@Singleton
class EnvironmentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val escaneoDao: EscaneoEntornoDao,
    private val objetoDao: ObjetoDetectadoDao,
    private val descripcionDao: DescripcionEntornoDao,
) {
    // Detector de objetos COCO: devuelve clases concretas y una caja por objeto, no solo
    // categorías amplias como "comida" o "instrumento musical".
    private val detectorDetallado by lazy {
        val opciones = TfliteObjectDetector.ObjectDetectorOptions.builder()
            .setMaxResults(MAX_OBJETOS)
            .setScoreThreshold(CONFIANZA_MINIMA)
            .build()
        TfliteObjectDetector.createFromFileAndOptions(context, MODELO_DE_OBJETOS, opciones)
    }

    // Etiquetador: dice QUÉ es cada objeto (silla, laptop, botella...). Modelo incluido en la app.
    private val etiquetador = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(CONFIANZA_RESPALDO)
            .build(),
    )

    fun observeRecent(usuarioId: Long): Flow<List<EscaneoEntornoEntity>> = escaneoDao.observeRecent(usuarioId)

    /**
     * The camera is free for anyone: with no [usuarioId] (nobody signed in) the description is still said
     * out loud, it just is not saved to a history that would have nowhere to belong.
     */
    suspend fun processCapturedPhoto(usuarioId: Long?, imageUri: Uri, rutaImagen: String): Result<EnvironmentResult> {
        var etapa = "validar la foto"
        return try {
            val inicio = SystemClock.elapsedRealtime()

            val file = File(rutaImagen)
            if (!file.exists()) {
                return Result.failure(Exception("La imagen no existe en el disco."))
            }

            // Una sola imagen, ya girada y reducida: detección, recortes y color usan las mismas coordenadas.
            val bitmap = cargarBitmapOrientado(rutaImagen)
                ?: return Result.failure(Exception("No pude abrir la imagen."))

            etapa = "detectar objetos"
            // Si el modelo detallado falla en algún teléfono, intenta el etiquetador de respaldo
            // en vez de abandonar el análisis completo de la foto.
            val detecciones = try {
                detectorDetallado.detect(TensorImage.fromBitmap(bitmap))
            } catch (e: Exception) {
                Log.e(TAG, "Falló el detector detallado; probaré el etiquetador de respaldo.", e)
                emptyList()
            }
            val descritos = detecciones
                .mapNotNull { describirObjetoDetectado(it, bitmap.width) }
                .sortedByDescending { it.confianza }
                .take(MAX_OBJETOS)

            // Si el detector no encontró cajas (por ejemplo, un objeto muy de cerca),
            // se etiqueta la foto completa y se dice lo principal sin posición.
            val principal: ObjetoDescrito? =
                if (descritos.isEmpty()) {
                    etapa = "etiquetar la foto completa"
                    describirFotoCompleta(bitmap)
                } else {
                    null
                }

            val finales = when {
                descritos.isNotEmpty() -> descritos
                    .sortedBy { ordenHorizontal(it.posicion) }
                principal != null -> listOf(principal)
                else -> emptyList()
            }

            val descripcion = when {
                finales.isEmpty() ->
                    "No logré identificar objetos claros enfrente. Intenta centrar el objeto, acercarte un poco más o mejorar la iluminación."
                descritos.isEmpty() ->
                    "Veo principalmente ${finales.first().frase}."
                else ->
                    "Veo ${unir(finales.map { it.frase })}."
            }

            bitmap.recycle()

            val escaneoId = usuarioId?.let { id ->
                etapa = "guardar el resultado"
                val nuevoId = escaneoDao.insert(EscaneoEntornoEntity(usuarioId = id, rutaImagen = rutaImagen))
                if (finales.isNotEmpty()) {
                    objetoDao.insertAll(
                        finales.map {
                            ObjetoDetectadoEntity(
                                escaneoId = nuevoId,
                                etiqueta = it.etiqueta,
                                confianza = it.confianza,
                            )
                        },
                    )
                }
                descripcionDao.insert(DescripcionEntornoEntity(escaneoId = nuevoId, textoDescripcion = descripcion))
                nuevoId
            }

            Log.d(TAG, "Descripción lista en ${SystemClock.elapsedRealtime() - inicio} ms: $descripcion")

            Result.success(EnvironmentResult(escaneoId, descripcion, finales.map { it.etiqueta }))
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo analizar la foto durante la etapa: $etapa", e)
            Result.failure(e)
        }
    }

    /** Usa la etiqueta y la caja del detector específico; evita volver a adivinar con el modelo genérico. */
    private fun describirObjetoDetectado(deteccion: Detection, anchoImagen: Int): ObjetoDescrito? {
        val categoria = deteccion.categories.maxByOrNull { it.score } ?: return null
        val nombre = EtiquetasEntorno.traducir(categoria.label) ?: run {
            Log.d(TAG_ETIQUETAS, "Clase COCO sin traducción: ${categoria.label} (${categoria.score})")
            return null
        }
        val caja = deteccion.boundingBox
        return ObjetoDescrito(
            nombre = nombre,
            // Se omite el color estimado: promediar una página con sombra suele llamarla "gris".
            color = null,
            posicion = posicionHorizontal(caja, anchoImagen),
            confianza = categoria.score,
            area = (caja.width() * caja.height()).toInt(),
        )
    }

    /** Sin cajas del detector: etiqueta la foto completa y devuelve lo más seguro, sin posición. */
    private suspend fun describirFotoCompleta(bitmap: Bitmap): ObjetoDescrito? {
        val etiquetas = etiquetador.process(InputImage.fromBitmap(bitmap, 0)).await()
            .sortedByDescending { it.confidence }
        for (etiqueta in etiquetas) {
            if (EtiquetasEntorno.esDemasiadoGeneral(etiqueta.text)) continue
            val traducido = EtiquetasEntorno.traducir(etiqueta.text)
            if (traducido != null) {
                return ObjetoDescrito(
                    nombre = traducido,
                    color = null,
                    posicion = null,
                    confianza = etiqueta.confidence,
                    area = bitmap.width * bitmap.height,
                )
            } else if (!EtiquetasEntorno.esIgnorada(etiqueta.text)) {
                Log.d(TAG_ETIQUETAS, "Sin traducción: ${etiqueta.text} (${etiqueta.confidence})")
            }
        }
        return null
    }

    private fun posicionHorizontal(caja: RectF, anchoImagen: Int): String {
        val centro = caja.centerX() / anchoImagen.toFloat()
        return when {
            centro < 0.33f -> "a la izquierda"
            centro > 0.66f -> "a la derecha"
            else -> "al centro"
        }
    }

    private fun ordenHorizontal(posicion: String?): Int = when (posicion) {
        "a la izquierda" -> 0
        "al centro" -> 1
        "a la derecha" -> 2
        else -> 3
    }

    private fun unir(frases: List<String>): String = when (frases.size) {
        0 -> ""
        1 -> frases[0]
        else -> frases.dropLast(1).joinToString(", ") + " y " + frases.last()
    }

    /** Carga la foto, la reduce si es enorme y la gira según el EXIF para que quede derecha. */
    private fun cargarBitmapOrientado(ruta: String): Bitmap? {
        return try {
            val bordes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(ruta, bordes)
            if (bordes.outWidth <= 0 || bordes.outHeight <= 0) return null

            var muestreo = 1
            while (maxOf(bordes.outWidth, bordes.outHeight) / muestreo > LADO_MAXIMO) muestreo *= 2

            val opciones = BitmapFactory.Options().apply { inSampleSize = muestreo }
            val original = BitmapFactory.decodeFile(ruta, opciones) ?: return null

            val grados = when (
                ExifInterface(ruta).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (grados == 0f) return original

            val matriz = Matrix().apply { postRotate(grados) }
            val girado = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matriz, true)
            if (girado !== original) original.recycle()
            girado
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val TAG = "EntornoTiempo"
        const val TAG_ETIQUETAS = "EntornoEtiquetas"
        const val MODELO_DE_OBJETOS = "efficientdet_lite0.tflite"
        const val MAX_OBJETOS = 5
        const val CONFIANZA_MINIMA = 0.5f
        const val CONFIANZA_RESPALDO = 0.7f
        const val LADO_MAXIMO = 1280
    }
}
