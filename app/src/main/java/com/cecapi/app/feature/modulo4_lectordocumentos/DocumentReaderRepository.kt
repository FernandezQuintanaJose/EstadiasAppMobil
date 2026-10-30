package com.cecapi.app.feature.modulo4_lectordocumentos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class DocumentReaderRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentoDao: DocumentoEscaneadoDao,
    private val textoDao: TextoExtraidoDao,
    private val historialDao: HistorialLecturaDao,
) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun observeRecent(usuarioId: Long): Flow<List<DocumentoEscaneadoEntity>> = documentoDao.observeRecent(usuarioId)

    /**
     * Runs on-device OCR on the captured photo. The camera is free for anyone: with no [usuarioId] (nobody
     * signed in) the text is still read out loud, it just is not saved to a history that would have nowhere
     * to belong. Rejects the photo before running ML Kit if it looks too dark or too blurry to read.
     *
     * Aplica estructuración inteligente para Menús, Listas y Recibos:
     * 1. Extrae líneas individuales y las agrupa por renglones horizontales (mismo nivel Y).
     * 2. Asocia productos con sus respectivos precios en la misma línea ("Hamburguesa — $85.00").
     * 3. Formatea la lectura para voz natural (desglosa "Expreso / Americano $35 / $45" en líneas claras).
     */
    suspend fun processCapturedPhoto(usuarioId: Long?, imageUri: Uri, rutaImagen: String): OcrOutcome {
        // A rejected or failed photo is never written to the database, so nothing would ever delete it from
        // disk on its own. Clean it up right here instead.
        return try {
            val bitmap = decodeDownsampledBitmap(imageUri)
                ?: return reject(rutaImagen, OcrOutcome.Error(IllegalStateException("No se pudo abrir la imagen: $imageUri")))

            val grises = toGrayscaleMatrix(bitmap)
            bitmap.recycle()

            if (averageLuminance(grises) < BRIGHTNESS_THRESHOLD) {
                return reject(rutaImagen, OcrOutcome.PocaLuz)
            }
            // Borrosa ya no rechaza la foto: quien no ve no puede saber de antemano si quedó movida,
            // así que se intenta leer igual y solo se avisa si algo sí se reconoció.
            val borrosa = laplacianVariance(grises) < BLUR_VARIANCE_THRESHOLD

            val inputImage = InputImage.fromFilePath(context, imageUri)
            val visionText = recognizer.process(inputImage).await()

            // Extraemos todas las líneas de todos los bloques
            val todasLasLineas = visionText.textBlocks.flatMap { it.lines }

            if (todasLasLineas.isEmpty()) {
                return reject(rutaImagen, OcrOutcome.SinTexto)
            }

            // Agrupamos las líneas por filas horizontales (mismo renglón Y)
            val filas = ArrayList<MutableList<Text.Line>>()

            for (linea in todasLasLineas.sortedBy { it.boundingBox?.top ?: 0 }) {
                val rect = linea.boundingBox ?: continue
                val centerY = rect.centerY()

                val filaExistente = filas.find { fila ->
                    fila.any { item ->
                        val r = item.boundingBox ?: return@any false
                        val avgH = (r.height() + rect.height()) / 2f
                        abs(r.centerY() - centerY) < (avgH * 0.6f)
                    }
                }

                if (filaExistente != null) {
                    filaExistente.add(linea)
                } else {
                    filas.add(mutableListOf(linea))
                }
            }

            // Para cada fila horizontal, ordenamos de izquierda a derecha y aplicamos formato de voz natural
            val lineasBrutas = filas.map { fila ->
                fila.sortedBy { it.boundingBox?.left ?: 0 }
                    .joinToString(" — ") { juntarPalabrasCortadas(it.text.trim()) }
                    .trim()
            }.filter { texto ->
                val limpio = texto.trim()
                limpio.length > 2 && limpio.any { it.isLetterOrDigit() }
            }

            // Formateamos las líneas para que la lectura de menús y opciones sea clara y natural por voz
            val parrafos = ArrayList<String>()
            for (linea in lineasBrutas) {
                val formateadas = formatearTextoParaVozNatural(linea)
                formateadas.split("\n").forEach { p ->
                    val t = p.trim()
                    if (t.isNotBlank()) parrafos.add(t)
                }
            }

            if (parrafos.isEmpty()) {
                return reject(rutaImagen, OcrOutcome.SinTexto)
            }

            val documentoId = usuarioId?.let { id ->
                val nuevoId = documentoDao.insert(DocumentoEscaneadoEntity(usuarioId = id, rutaImagen = rutaImagen))
                textoDao.insert(
                    TextoExtraidoEntity(documentoId = nuevoId, textoCompleto = parrafos.joinToString("\n\n")),
                )
                nuevoId
            }
            OcrOutcome.Exito(documentoId, parrafos, borrosa = borrosa)
        } catch (e: Exception) {
            reject(rutaImagen, OcrOutcome.Error(e))
        }
    }

    private fun reject(rutaImagen: String, outcome: OcrOutcome): OcrOutcome {
        runCatching { java.io.File(rutaImagen).delete() }
        return outcome
    }

    suspend fun logLectura(documentoId: Long?) {
        if (documentoId == null) return // nothing was saved for an anonymous reading, so there is nothing to log
        historialDao.insert(HistorialLecturaEntity(documentoId = documentoId))
    }

    /**
     * Transforma patrones confusos de menús (ej. "Expreso / Americano — $35 / $45") en líneas
     * claras para ser leídas por el motor de síntesis de voz sin atropellarse.
     */
    private fun formatearTextoParaVozNatural(texto: String): String {
        var resultado = texto

        // 1. Patrón de doble opción y doble precio en menús: "Expreso / Americano — $35 / $45"
        val patronDobleOpcion = Regex("""^(.+?)\s*[/|]\s*(.+?)\s*—\s*\$?(\d+(?:\.\d{2})?)\s*[/|]\s*\$?(\d+(?:\.\d{2})?)$""", RegexOption.IGNORE_CASE)
        val matchDoble = patronDobleOpcion.find(resultado.trim())
        if (matchDoble != null) {
            val (opcion1, opcion2, precio1, precio2) = matchDoble.destructured
            return "${opcion1.trim()}: $precio1 pesos.\n${opcion2.trim()}: $precio2 pesos."
        }

        // 2. Reemplaza '$' por "pesos" para pronunciación hablada clara
        resultado = resultado.replace(Regex("""\$\s*(\d+(?:\.\d{2})?)"""), "$1 pesos")

        // 3. Reemplaza '/' o '|' entre palabras por " o " para evitar que diga la palabra "barra"
        resultado = resultado.replace(Regex("""(\w+)\s*[/|]\s*(\w+)"""), "$1 o $2")

        return resultado
    }

    /**
     * Junta las palabras que fueron cortadas con un guión al final de una línea
     * Ejemplo: "pala-\nbra" -> "palabra"
     */
    private fun juntarPalabrasCortadas(texto: String): String {
        return texto.replace(Regex("""(\w+)-\s*[\r\n]+\s*(\w+)"""), "$1$2")
    }

    private fun decodeDownsampledBitmap(uri: Uri): Bitmap? {
        val options = BitmapFactory.Options().apply { inSampleSize = 4 }
        return context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
    }

    /** Shrinks the photo to a fixed-size grayscale grid so brightness/blur checks stay fast and size-independent. */
    private fun toGrayscaleMatrix(bitmap: Bitmap, gridSize: Int = 100): Array<DoubleArray> {
        val scaled = Bitmap.createScaledBitmap(bitmap, gridSize, gridSize, true)
        val matrix = Array(gridSize) { y ->
            DoubleArray(gridSize) { x ->
                val pixel = scaled.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                0.299 * r + 0.587 * g + 0.114 * b
            }
        }
        if (scaled !== bitmap) scaled.recycle()
        return matrix
    }

    private fun averageLuminance(matrix: Array<DoubleArray>): Double =
        matrix.sumOf { row -> row.sum() } / (matrix.size.toDouble() * matrix[0].size)

    /** Blur estimate: sharp photos have high-contrast edges, so the Laplacian's variance is high; blurry ones are flat. */
    private fun laplacianVariance(matrix: Array<DoubleArray>): Double {
        val height = matrix.size
        val width = matrix[0].size
        val valores = ArrayList<Double>((height - 2) * (width - 2))
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val laplaciano = matrix[y - 1][x] + matrix[y + 1][x] + matrix[y][x - 1] + matrix[y][x + 1] - 4 * matrix[y][x]
                valores.add(laplaciano)
            }
        }
        val media = valores.average()
        return valores.sumOf { (it - media) * (it - media) } / valores.size
    }

    private companion object {
        /** Average grayscale luminance (0-255) below this is treated as "too dark to read". */
        const val BRIGHTNESS_THRESHOLD = 45.0

        /** Laplacian variance below this is treated as "too blurry to read". Tuned empirically; adjust after device testing. */
        const val BLUR_VARIANCE_THRESHOLD = 60.0
    }
}
