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
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector as TfliteObjectDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class EnvironmentResult(
    val escaneoId: Long?,
    val descripcion: String,
    val etiquetas: List<String>,
    /** Solo cuando la foto fue de billetes: lo que se leyó, para que la pantalla pueda llevar la cuenta. */
    val billetes: ResultadoBillete? = null,
)

/** Un objeto ya nombrado en español, con su color y dónde está en la foto. */
private data class ObjetoDescrito(
    val nombre: NombreEs,
    val color: String?,
    val posicion: String?,
    val confianza: Float,
    val area: Int,
    /** El modelo no está muy seguro: se dice "posiblemente" para no afirmar de más. */
    val dudoso: Boolean = false,
) {
    /** "silla negro" (sin artículo ni posición), para guardar en la base. */
    val etiqueta: String get() = if (color != null) "${nombre.texto} de color $color" else nombre.texto

    /** "una silla de color negro a la izquierda", para decirlo en voz alta. */
    val frase: String
        get() = buildString {
            if (dudoso) append("posiblemente ")
            append(nombre.conArticulo)
            if (color != null) append(" de color ").append(color)
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

    // Etiquetador con umbral bajo, solo para sospechar si una foto normal trae un billete (papel, dinero).
    private val etiquetadorBilletes = ImageLabeling.getClient(
        ImageLabelerOptions.Builder().setConfidenceThreshold(0.4f).build(),
    )

    // Lector de texto para los números y palabras del billete.
    private val reconocedorTexto = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun observeRecent(usuarioId: Long): Flow<List<EscaneoEntornoEntity>> = escaneoDao.observeRecent(usuarioId)

    /**
     * The camera is free for anyone: with no [usuarioId] (nobody signed in) the description is still said
     * out loud, it just is not saved to a history that would have nowhere to belong.
     */
    suspend fun processCapturedPhoto(
        usuarioId: Long?,
        imageUri: Uri,
        rutaImagen: String,
        buscarBillete: Boolean = false,
    ): Result<EnvironmentResult> {
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

            // "Qué billete es": solo se lee el billete. No se corre el detector de objetos, que no conoce billetes
            // y solo metería ruido ("veo un libro") en la respuesta.
            if (buscarBillete) {
                etapa = "leer el billete"
                val resultado = leerBillete(bitmap)
                bitmap.recycle()
                return Result.success(guardarBillete(usuarioId, rutaImagen, resultado, inicio))
            }

            // Foto normal ("qué hay enfrente"): si en la foto hay un billete cuya denominación se lee con claridad,
            // se dice de cuánto es sin que la persona tenga que pedirlo aparte.
            etapa = "revisar si hay un billete"
            val billeteAutomatico = detectarBilleteAutomatico(bitmap)
            if (billeteAutomatico != null) {
                bitmap.recycle()
                return Result.success(guardarBillete(usuarioId, rutaImagen, billeteAutomatico, inicio))
            }

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
                .mapNotNull { describirObjetoDetectado(it, bitmap) }
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

            // Si no se identificó ningún objeto pero toda la foto es casi de un solo color (una hoja, una tela, una
            // pared lisa), se dice el color en lugar de no decir nada.
            val colorLiso = if (finales.isEmpty()) NombreColor.dominante(pixelesDelCentro(bitmap), 0.6f) else null

            val descripcion = when {
                finales.isEmpty() && colorLiso != null ->
                    "No identifico un objeto, pero veo algo de color ${colorLiso.nombre}. " +
                        "Acércate o centra el objeto si quieres que lo describa."
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

    /** Guarda el resultado del billete en el historial (si hay usuario) y lo devuelve listo para decir en voz alta. */
    private suspend fun guardarBillete(
        usuarioId: Long?,
        rutaImagen: String,
        resultado: ResultadoBillete,
        inicio: Long,
    ): EnvironmentResult {
        val frase = fraseBillete(resultado)
        // Una etiqueta por cada denominación confirmada; si no se pudo leer ninguna, solo "billete".
        val etiquetas = resultado.todas.map {
            val n = resultado.cantidadDe(it)
            if (n > 1) "$n billetes de ${it.valor} pesos" else "billete de ${it.valor} pesos"
        }.ifEmpty { listOf("billete") }
        val escaneoId = usuarioId?.let { id ->
            val nuevoId = escaneoDao.insert(EscaneoEntornoEntity(usuarioId = id, rutaImagen = rutaImagen))
            objetoDao.insertAll(
                etiquetas.map { ObjetoDetectadoEntity(escaneoId = nuevoId, etiqueta = it, confianza = 1f) },
            )
            descripcionDao.insert(DescripcionEntornoEntity(escaneoId = nuevoId, textoDescripcion = frase))
            nuevoId
        }
        Log.d(TAG, "Billete listo en ${SystemClock.elapsedRealtime() - inicio} ms: $frase")
        return EnvironmentResult(escaneoId, frase, etiquetas, billetes = resultado)
    }

    /**
     * En una foto normal, ¿hay un billete del que se lea la denominación con claridad? Devuelve null si no, y
     * entonces sigue la descripción normal del entorno. Es más exigente que "qué billete es": aquí nadie dijo que
     * fuera un billete, así que hace falta un indicio (palabras del billete, etiqueta de imagen o color).
     */
    private suspend fun detectarBilleteAutomatico(bitmap: Bitmap): ResultadoBillete? {
        return try {
            val etiquetas = try {
                etiquetadorBilletes.process(InputImage.fromBitmap(bitmap, 0)).await().map { it.text.lowercase() }
            } catch (e: Exception) {
                emptyList()
            }
            val diceBillete = etiquetas.any { it in ETIQUETAS_DE_BILLETE }
            val dicePapel = etiquetas.any { it in ETIQUETAS_DE_PAPEL }
            val colores = BilleteMx.colorDominante(pixelesDelCentro(bitmap))

            // Derecha primero; si hay pinta de billete o papel y no salió nada, también girado.
            val giros = if (diceBillete || dicePapel) intArrayOf(0, 90, 270) else intArrayOf(0)
            for (grados in giros) {
                val imagen = if (grados == 0) bitmap else girar(bitmap, grados.toFloat())
                val palabras = try {
                    leerPalabras(imagen)
                } catch (e: Exception) {
                    emptyList()
                } finally {
                    if (imagen !== bitmap) imagen.recycle()
                }
                // 1) Palabras del billete o etiqueta de "billete/dinero": basta con que se lea la denominación.
                val seguro = BilleteMx.reconocer(palabras, esBilletePorImagen = diceBillete, colores = colores)
                if (seguro.denominacion != null && seguro.esBilleteSeguro) {
                    Log.d(TAG_BILLETE, "automático ($grados): $seguro")
                    return seguro
                }
                // 2) Sin palabras del billete: se acepta si el color dominante respalda la denominación leída
                //    (con menos exigencia si la etiqueta de imagen al menos dice "papel").
                val porColor = BilleteMx.reconocer(palabras, esBilletePorImagen = true, colores = colores)
                val d = porColor.denominacion
                // Aquí el color solo respalda a una denominación; con varias no hay forma de respaldar a todas,
                // y un cartel con dos precios grandes no debe pasar por billetes.
                if (d != null && porColor.adicionales.isEmpty()) {
                    val apoyo = colores[d] ?: 0f
                    if (apoyo >= (if (dicePapel) 0.35f else 0.5f)) {
                        Log.d(TAG_BILLETE, "automático por color ($grados, apoyo $apoyo): $porColor")
                        return porColor
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG_BILLETE, "Falló la revisión automática de billetes", e)
            null
        }
    }

    /** Qué dice en voz alta la app según lo que se pudo leer del billete. */
    private fun fraseBillete(r: ResultadoBillete): String = when {
        // Varios billetes (distintos o iguales): se dice cuántos hay de cada denominación, de mayor a menor. El
        // conteo es aproximado (billetes pegados o encimados se cuentan de menos), y se avisa.
        r.denominacion != null && r.hayVarios ->
            "Veo ${unir(r.todas.sortedByDescending { it.valor }.map { frasePorDenominacion(it, r.cantidadDe(it)) })}. " +
                "El conteo es aproximado; si están juntos o encimados puede fallar. " +
                "Para estar segura, muéstramelos de uno en uno."
        r.denominacion != null -> "Es un billete de ${r.denominacion.nombreHablado}."
        r.esBilleteSeguro && r.sugerida != null ->
            "Parece un billete de ${r.sugerida.nombreHablado}, pero no estoy segura. " +
                "Ponlo plano, acércalo y con buena luz, y pregunta otra vez."
        r.esBilleteSeguro ->
            "Veo un billete, pero no alcanzo a leer cuánto es. Ponlo plano frente a la cámara, " +
                "que se vean los números grandes y con buena luz, y pregunta otra vez."
        else -> "No veo un billete. Ponlo plano frente a la cámara, con buena luz, y pregunta otra vez."
    }

    /**
     * Lee el billete con OCR (palabra por palabra, con su tamaño) y apoya la decisión con el color. El billete
     * puede estar acostado o de pie respecto al teléfono, así que si no sale a la primera se prueba girado.
     */
    private suspend fun leerBillete(bitmap: Bitmap): ResultadoBillete {
        val colores = BilleteMx.colorDominante(pixelesDelCentro(bitmap))
        var mejor: ResultadoBillete? = null
        for (grados in intArrayOf(0, 90, 270)) {
            val imagen = if (grados == 0) bitmap else girar(bitmap, grados.toFloat())
            val palabras = try {
                leerPalabras(imagen)
            } catch (e: Exception) {
                Log.e(TAG, "Falló el OCR del billete ($grados grados)", e)
                emptyList()
            } finally {
                if (imagen !== bitmap) imagen.recycle()
            }
            val resultado = BilleteMx.reconocer(
                palabras = palabras,
                esBilletePorImagen = false,
                usuarioPideBillete = true,
                colores = colores,
            )
            Log.d(TAG_BILLETE, "giro $grados: ${palabras.size} palabras -> $resultado (todas: ${resultado.todas})")
            if (resultado.denominacion != null) return resultado
            if (mejor == null || (resultado.sugerida != null && mejor.sugerida == null)) mejor = resultado
        }
        return mejor ?: ResultadoBillete(null, true, true)
    }

    private suspend fun leerPalabras(bitmap: Bitmap): List<PalabraOcr> {
        val texto = reconocedorTexto.process(InputImage.fromBitmap(bitmap, 0)).await()
        return texto.textBlocks.flatMap { it.lines }.flatMap { it.elements }.map { e ->
            // Alto de la letra: la menor de las dos medidas de la caja sirve aunque la palabra esté girada.
            val caja = e.boundingBox
            if (caja == null) {
                PalabraOcr(e.text)
            } else {
                PalabraOcr(e.text, minOf(caja.width(), caja.height()), caja.centerX(), caja.centerY())
            }
        }
    }

    private fun girar(bitmap: Bitmap, grados: Float): Bitmap =
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(grados) }, true)

    /** Muestra de píxeles de la mitad central de la foto, donde está el billete, para estimar su color. */
    private fun pixelesDelCentro(bitmap: Bitmap): IntArray {
        val ancho = bitmap.width
        val alto = bitmap.height
        val x0 = ancho / 4
        val y0 = alto / 4
        val paso = maxOf(1, minOf(ancho, alto) / 120)
        val salida = ArrayList<Int>()
        var y = y0
        while (y < alto - alto / 4) {
            var x = x0
            while (x < ancho - ancho / 4) {
                salida += bitmap.getPixel(x, y)
                x += paso
            }
            y += paso
        }
        return salida.toIntArray()
    }

    /** Usa la etiqueta y la caja del detector específico; evita volver a adivinar con el modelo genérico. */
    private fun describirObjetoDetectado(deteccion: Detection, bitmap: Bitmap): ObjetoDescrito? {
        val categoria = deteccion.categories.maxByOrNull { it.score } ?: return null
        val nombre = EtiquetasEntorno.traducir(categoria.label) ?: run {
            Log.d(TAG_ETIQUETAS, "Clase COCO sin traducción: ${categoria.label} (${categoria.score})")
            return null
        }
        val caja = deteccion.boundingBox
        val color = if (EtiquetasEntorno.admiteColor(categoria.label)) colorDeLaCaja(bitmap, caja) else null
        return ObjetoDescrito(
            nombre = nombre,
            color = color,
            posicion = posicionHorizontal(caja, bitmap.width),
            confianza = categoria.score,
            area = (caja.width() * caja.height()).toInt(),
            dudoso = categoria.score < CONFIANZA_SEGURA_DETECTOR,
        )
    }

    /**
     * Color dominante del centro de la caja del objeto (el 60 % del medio, para no tomar el fondo). Si el objeto
     * mezcla varios colores sin que uno domine, no se dice ninguno en lugar de adivinar.
     */
    private fun colorDeLaCaja(bitmap: Bitmap, caja: RectF): String? {
        val ancho = caja.width()
        val alto = caja.height()
        val x0 = (caja.left + ancho * 0.2f).toInt().coerceIn(0, bitmap.width - 1)
        val x1 = (caja.right - ancho * 0.2f).toInt().coerceIn(x0 + 1, bitmap.width)
        val y0 = (caja.top + alto * 0.2f).toInt().coerceIn(0, bitmap.height - 1)
        val y1 = (caja.bottom - alto * 0.2f).toInt().coerceIn(y0 + 1, bitmap.height)
        val paso = maxOf(1, minOf(x1 - x0, y1 - y0) / 40)
        val muestra = ArrayList<Int>()
        var y = y0
        while (y < y1) {
            var x = x0
            while (x < x1) {
                muestra += bitmap.getPixel(x, y)
                x += paso
            }
            y += paso
        }
        return NombreColor.dominante(muestra.toIntArray(), 0.45f)?.nombre
    }

    /**
     * Sin cajas del detector: etiqueta la foto completa y devuelve lo más seguro, sin posición. Primero se buscan
     * etiquetas concretas ("mano", "teléfono celular"); las vagas ("dispositivo", "piel") solo si no hay otra.
     */
    private suspend fun describirFotoCompleta(bitmap: Bitmap): ObjetoDescrito? {
        val etiquetas = etiquetador.process(InputImage.fromBitmap(bitmap, 0)).await()
            .sortedByDescending { it.confidence }
        Log.d(TAG_ETIQUETAS, "Etiquetas de la foto: " + etiquetas.joinToString { "${it.text} (${"%.2f".format(it.confidence)})" })
        val candidatas = etiquetas.filter { !EtiquetasEntorno.esDemasiadoGeneral(it.text) }
        val elegida = candidatas.firstOrNull { !EtiquetasEntorno.esDebil(it.text) && EtiquetasEntorno.traducir(it.text) != null }
            ?: candidatas.firstOrNull { EtiquetasEntorno.traducir(it.text) != null }
        if (elegida == null) {
            etiquetas.filter { !EtiquetasEntorno.esIgnorada(it.text) }
                .forEach { Log.d(TAG_ETIQUETAS, "Sin traducción: ${it.text} (${it.confidence})") }
            return null
        }
        val nombre = EtiquetasEntorno.traducir(elegida.text) ?: return null
        val color = if (EtiquetasEntorno.admiteColor(elegida.text)) {
            NombreColor.dominante(pixelesDelCentro(bitmap), 0.5f)?.nombre
        } else {
            null
        }
        return ObjetoDescrito(
            nombre = nombre,
            color = color,
            posicion = null,
            confianza = elegida.confidence,
            area = bitmap.width * bitmap.height,
            dudoso = elegida.confidence < CONFIANZA_SEGURA_RESPALDO || EtiquetasEntorno.esDebil(elegida.text),
        )
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

    /** "un billete de veinte pesos", "dos billetes de cien pesos". */
    private fun frasePorDenominacion(d: DenominacionBillete, n: Int): String = when (n) {
        1 -> "un billete de ${d.nombreHablado}"
        else -> "${numeroEnPalabras(n)} billetes de ${d.nombreHablado}"
    }

    private fun numeroEnPalabras(n: Int): String = when (n) {
        2 -> "dos"; 3 -> "tres"; 4 -> "cuatro"; 5 -> "cinco"; 6 -> "seis"; 7 -> "siete"; 8 -> "ocho"; 9 -> "nueve"
        10 -> "diez"
        else -> n.toString()
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
        const val TAG_BILLETE = "EntornoBillete"
        val ETIQUETAS_DE_BILLETE = setOf("banknote", "money", "currency", "cash", "coin")
        val ETIQUETAS_DE_PAPEL = setOf("paper", "paper product", "document", "rectangle")
        const val MODELO_DE_OBJETOS = "efficientdet_lite0.tflite"
        const val MAX_OBJETOS = 5
        const val CONFIANZA_MINIMA = 0.4f
        const val CONFIANZA_RESPALDO = 0.55f
        // Por debajo de esto se dice "posiblemente" en lugar de afirmarlo.
        const val CONFIANZA_SEGURA_DETECTOR = 0.6f
        const val CONFIANZA_SEGURA_RESPALDO = 0.8f
        const val LADO_MAXIMO = 1280
    }
}
