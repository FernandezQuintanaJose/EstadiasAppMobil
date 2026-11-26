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
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector as TfliteObjectDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

data class EnvironmentResult(
    val escaneoId: Long?,
    val descripcion: String,
    val etiquetas: List<String>,
    /** Solo cuando la foto fue de billetes: lo que se leyó, para que la pantalla pueda llevar la cuenta. */
    val billetes: ResultadoBillete? = null,
    /** Solo cuando la foto fue de una moneda: lo que se reconoció, para que la pantalla pueda llevar la cuenta. */
    val monedas: ResultadoMoneda? = null,
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
    private val iaLocal: SmolVlmLocal,
) {
    // Detector de objetos COCO: devuelve clases concretas y una caja por objeto, no solo
    // categorías amplias como "comida" o "instrumento musical".
    private val detectorDetallado by lazy {
        val opciones = TfliteObjectDetector.ObjectDetectorOptions.builder()
            .setMaxResults(MAX_DETECCIONES)
            .setScoreThreshold(CONFIANZA_MINIMA)
            .build()
        TfliteObjectDetector.createFromFileAndOptions(context, MODELO_DE_OBJETOS, opciones)
    }

    // Un solo etiquetador para todo el módulo. Crear dos clientes de ML Kit a la vez duplicaba su
    // registro interno y podía cerrar la app; cada camino filtra después la confianza que necesita.
    private val etiquetador = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(CONFIANZA_ETIQUETADOR)
            .build(),
    )

    // Lector de texto para los números y palabras del billete.
    private val reconocedorTexto = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // Intérpretes TFLite del clasificador de monedas. Se cargan solo la primera vez que hacen falta:
    // una foto normal sin monedas no paga ni su carga ni su inferencia.
    private val interpreteMonedas: Interpreter? by lazy { cargarInterprete(MODELO_DE_MONEDAS) }
    private val interpreteApoyo: Interpreter? by lazy { cargarInterprete(MODELO_DE_APOYO) }

    private fun cargarInterprete(nombre: String): Interpreter? {
        return try {
            // El intérprete necesita un buffer mapeado en memoria, no un arreglo copiado.
            val archivo = context.assets.openFd(nombre)
            val canal = archivo.createInputStream().channel
            val buffer = canal.map(
                java.nio.channels.FileChannel.MapMode.READ_ONLY,
                archivo.startOffset,
                archivo.declaredLength,
            )
            canal.close()
            archivo.close()
            val opciones = Interpreter.Options()
            val interpreter = Interpreter(buffer, opciones)
            Log.i(TAG_MONEDA, "Modelo cargado desde assets: $nombre")
            interpreter
        } catch (e: Exception) {
            Log.w(TAG_MONEDA, "No se pudo cargar el modelo ($nombre).", e)
            null
        }
    }

    fun observeRecent(usuarioId: Long): Flow<List<EscaneoEntornoEntity>> = escaneoDao.observeRecent(usuarioId)

    /**
     * Deja listo el modelo de visión cuando ya hay una foto. La descripción solo se ejecuta si la
     * persona elige "qué hay enfrente"; esto únicamente evita que esa opción espere la carga.
     */
    fun prepararModeloIa() {
        // El traductor es pequeño y se descarga una sola vez; se deja listo junto con el modelo
        // para que la primera descripción no llegue en inglés ni espere la descarga.
        iaLocal.prepararTraductor()
        iaLocal.precargar()
    }

    /** Suelta el modelo de visión (cientos de MB en memoria) al salir de la cámara. */
    fun liberarModeloIa() = iaLocal.liberar()

    /**
     * The camera is free for anyone: with no [usuarioId] (nobody signed in) the description is still said
     * out loud, it just is not saved to a history that would have nowhere to belong.
     */
    suspend fun processCapturedPhoto(
        usuarioId: Long?,
        imageUri: Uri,
        rutaImagen: String,
        buscarBillete: Boolean = false,
        buscarMoneda: Boolean = false,
        usarIa: Boolean = false,
    ): Result<EnvironmentResult> {
        var etapa = "validar la foto"
        return try {
            val inicio = SystemClock.elapsedRealtime()

            val file = File(rutaImagen)
            if (!file.exists()) {
                return Result.failure(Exception("La imagen no existe en el disco."))
            }

            // Una sola imagen, ya girada y reducida: detección, recortes y color usan las mismas coordenadas.
            // La decodificación se hace fuera del hilo principal para que la pantalla no se congele.
            val bitmap = withContext(Dispatchers.Default) { cargarBitmapOrientado(rutaImagen) }
                ?: return Result.failure(Exception("No pude abrir la imagen."))

            // "Qué billete es": solo se lee el billete. No se corre el detector de objetos, que no conoce billetes
            // y solo metería ruido ("veo un libro") en la respuesta.
            if (buscarBillete) {
                etapa = "leer el billete"
                val resultado = leerBillete(bitmap)
                bitmap.recycle()
                return Result.success(guardarBillete(usuarioId, rutaImagen, resultado, inicio))
            }

            // "Qué moneda es": solo se clasifica la moneda con sus modelos locales, sin detector de
            // objetos ni lectura de billetes. Nunca pasa por la IA de descripción.
            if (buscarMoneda) {
                etapa = "reconocer la moneda"
                val resultado = reconocerMonedaConRespaldo(bitmap)
                bitmap.recycle()
                return Result.success(guardarMoneda(usuarioId, rutaImagen, resultado, inicio))
            }

            // Foto normal ("qué hay enfrente"): si en la foto hay un billete cuya denominación se lee con claridad,
            // se dice de cuánto es sin que la persona tenga que pedirlo aparte. Esto va antes de la IA para que
            // un billete nunca se describa como una escena cualquiera.
            etapa = "revisar si hay un billete"
            val billeteAutomatico = detectarBilleteAutomatico(bitmap)
            if (billeteAutomatico != null) {
                bitmap.recycle()
                return Result.success(guardarBillete(usuarioId, rutaImagen, billeteAutomatico, inicio))
            }

            // "Qué hay enfrente" usa siempre la IA local. Billetes y monedas conservan sus modelos
            // especializados: si la IA menciona dinero, la denominación la decide el clasificador.
            // En un teléfono que no puede correr el modelo se sigue con el detector, más abajo.
            if (usarIa && iaLocal.disponible) {
                etapa = "describir la foto"
                val textoIa = iaLocal.describir(bitmap)
                if (textoIa != null) {
                    // Puente con el modelo de monedas: la IA solo avisa que hay una moneda; la
                    // denominación la decide el clasificador entrenado para pesos mexicanos. La IA
                    // nunca afirma de cuánto es. En una foto general un rescate se queda genérico.
                    if (MONEDA_EN_DESCRIPCION.containsMatchIn(textoIa)) {
                        etapa = "reconocer la moneda de la descripción"
                        val moneda = reconocerMoneda(bitmap, fotoGeneral = true)
                        bitmap.recycle()
                        return Result.success(
                            guardarMoneda(usuarioId, rutaImagen, moneda, inicio, paraFotoGeneral = true),
                        )
                    }
                    // Si la descripción menciona billetes o dinero, la lectura de billetes manda:
                    // la IA no debe decir de cuánto es ni describir el billete como escena.
                    if (BILLETE_EN_DESCRIPCION.containsMatchIn(textoIa)) {
                        etapa = "leer el billete de la descripción"
                        val billete = leerBillete(bitmap)
                        bitmap.recycle()
                        return Result.success(guardarBillete(usuarioId, rutaImagen, billete, inicio))
                    }
                    bitmap.recycle()
                    val escaneoId = usuarioId?.let { id ->
                        etapa = "guardar el resultado"
                        val nuevoId = escaneoDao.insert(
                            EscaneoEntornoEntity(usuarioId = id, rutaImagen = rutaImagen),
                        )
                        descripcionDao.insert(
                            DescripcionEntornoEntity(escaneoId = nuevoId, textoDescripcion = textoIa),
                        )
                        nuevoId
                    }
                    Log.d(TAG, "Descripción de la IA lista en ${SystemClock.elapsedRealtime() - inicio} ms: $textoIa")
                    return Result.success(EnvironmentResult(escaneoId, textoIa, emptyList()))
                }
                Log.d(
                    TAG,
                    "No pude describir la foto (${iaLocal.ultimoMotivo ?: "sin motivo"}); " +
                        "no uso el detector para 'qué hay enfrente'.",
                )
                bitmap.recycle()
                return Result.failure(
                    Exception("No lo logré describir. Intenta con otra foto."),
                )
            }

            // Una foto casi toda de un solo color (hoja, tela, pared, tarjeta de color): se dice el color; el
            // detector la tomaría por "cielo" u otra cosa sin haberla.
            val liso = NombreColor.dominante(pixelesDelCentro(bitmap), 0.92f)

            etapa = "detectar objetos"
            // Si el modelo detallado falla en algún teléfono, intenta el etiquetador de respaldo
            // en vez de abandonar el análisis completo de la foto.
            // El detector corre siempre. Antes se saltaba cuando el centro era de un solo color y
            // una laptop contra una pared lisa terminaba descrita como "superficie lisa".
            val detecciones = try {
                withContext(Dispatchers.Default) {
                    detectorDetallado.detect(TensorImage.fromBitmap(bitmap))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Falló el detector detallado; probaré el etiquetador de respaldo.", e)
                emptyList()
            }
            Log.d(TAG, "Detector: ${detecciones.size} objetos; color liso: ${liso?.nombre ?: "no"}")
            val descritos = detecciones
                .mapNotNull { describirObjetoDetectado(it, bitmap) }
                .filter { it.confianza >= CONFIANZA_MINIMA }
                .sortedByDescending { it.area }
                .take(MAX_OBJETOS)

            // Si el detector no encontró cajas (por ejemplo, un objeto muy de cerca),
            // se etiqueta la foto completa y se dice lo principal sin posición.
            val principal: ObjetoDescrito? =
                if (descritos.isEmpty() && liso == null) {
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

            val descripcionBase = when {
                finales.isEmpty() && liso != null ->
                    "Veo una superficie lisa de color ${liso.nombre}. Si buscas un objeto, centralo y acércalo."
                finales.isEmpty() && colorLiso != null ->
                    "No identifico un objeto, pero veo algo de color ${colorLiso.nombre}. " +
                        "Acércate o centra el objeto si quieres que lo describa."
                finales.isEmpty() ->
                    "No logré identificar objetos claros enfrente. Intenta centrar el objeto, acercarte un poco más o mejorar la iluminación."
                descritos.isEmpty() && finales.first().dudoso ->
                    "Posiblemente veo ${finales.first().frase.removePrefix("posiblemente ")}."
                descritos.isEmpty() ->
                    "Veo ${finales.first().frase}."
                else ->
                    fraseEscena(finales)
            }

            val descripcion = descripcionBase

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
     * Guarda el resultado de la moneda en el historial (si hay usuario) y lo devuelve listo para
     * decir en voz alta. En una foto general la duda nunca nombra la denominación si vino de un
     * rescate; en una petición explícita sí puede decir "parece".
     */
    private suspend fun guardarMoneda(
        usuarioId: Long?,
        rutaImagen: String,
        resultado: ResultadoMoneda,
        inicio: Long,
        paraFotoGeneral: Boolean = false,
    ): EnvironmentResult {
        val frase = if (paraFotoGeneral) {
            MonedaMx.fraseParaFotoGeneral(resultado)
        } else {
            MonedaMx.frase(resultado)
        }
        val d = resultado.denominacion
        val etiqueta = if (resultado.esSegura && d != null) "moneda de ${d.valor} pesos" else "moneda sin identificar"
        val etiquetas = listOf(etiqueta)
        val escaneoId = usuarioId?.let { id ->
            val nuevoId = escaneoDao.insert(EscaneoEntornoEntity(usuarioId = id, rutaImagen = rutaImagen))
            objetoDao.insertAll(
                listOf(
                    ObjetoDetectadoEntity(
                        escaneoId = nuevoId,
                        etiqueta = etiqueta,
                        confianza = resultado.confianza,
                    ),
                ),
            )
            descripcionDao.insert(DescripcionEntornoEntity(escaneoId = nuevoId, textoDescripcion = frase))
            nuevoId
        }
        Log.d(TAG, "Moneda lista en ${SystemClock.elapsedRealtime() - inicio} ms: $frase")
        return EnvironmentResult(escaneoId, frase, etiquetas, monedas = resultado)
    }

    /**
     * En una foto normal, ¿hay un billete del que se lea la denominación con claridad? Devuelve null si no, y
     * entonces sigue la descripción normal del entorno. Es más exigente que "qué billete es": aquí nadie dijo que
     * fuera un billete, así que hace falta un indicio (palabras del billete, etiqueta de imagen o color).
     */
    private suspend fun detectarBilleteAutomatico(bitmap: Bitmap): ResultadoBillete? {
        return try {
            val etiquetas = try {
                etiquetador.process(InputImage.fromBitmap(bitmap, 0)).await().map { it.text.lowercase() }
            } catch (e: Exception) {
                emptyList()
            }
            // Si la etiqueta dice "moneda", no es un billete: la foto sigue su camino normal y el
            // modelo de monedas decide. Una moneda de 20 pesos puede tener un "20" grande y, sin
            // este freno, se respondería como billete antes de llegar al clasificador.
            if (etiquetas.any { it in ETIQUETAS_DE_MONEDA }) return null
            val diceBillete = etiquetas.any { it in ETIQUETAS_DE_BILLETE }
            val dicePapel = etiquetas.any { it in ETIQUETAS_DE_PAPEL }
            // Una laptop, un monitor o un teclado no son billetes aunque su pantalla tenga números.
            if (!diceBillete && etiquetas.any { it in ETIQUETAS_DE_OBJETO_NO_BILLETE }) return null
            val colores = BilleteMx.colorDominante(pixelesDelCentro(bitmap))
            val apoyoColor = colores.values.maxOrNull() ?: 0f

            // Sin etiqueta de billete, sin papel y sin un color de billete claro, no vale la pena
            // gastar OCR en cada foto general: se sigue directo a la descripción.
            if (!diceBillete && !(dicePapel && apoyoColor >= 0.35f) && apoyoColor < 0.20f) {
                Log.d(TAG_BILLETE, "Sin indicios de billete; no gasto OCR.")
                return null
            }

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

    /** Nombres de las clases del modelo de monedas, leídos una sola vez desde assets. Vacío si falta el archivo. */
    private val etiquetasMonedas: List<String> by lazy {
        try {
            val etiquetas = context.assets.open(ARCHIVO_ETIQUETAS_MONEDAS).bufferedReader().use { lector ->
                lector.readLines().map { it.trim() }.filter { it.isNotEmpty() }
            }
            Log.i(TAG_MONEDA, "Etiquetas cargadas desde assets (${etiquetas.size}): $etiquetas")
            if (etiquetas.size != NUM_CLASES_ESPERADAS) {
                Log.w(TAG_MONEDA, "Se esperaban $NUM_CLASES_ESPERADAS etiquetas, hay ${etiquetas.size}.")
            }
            etiquetas
        } catch (e: Exception) {
            Log.w(TAG_MONEDA, "No se pudo leer $ARCHIVO_ETIQUETAS_MONEDAS.", e)
            emptyList()
        }
    }

    /**
     * Reconoce una moneda siempre con el modelo local. No hay llamadas a internet: la app funciona
     * igual con o sin conexión.
     */
    private suspend fun reconocerMonedaConRespaldo(bitmap: Bitmap): ResultadoMoneda {
        return reconocerMoneda(bitmap)
    }

    /** Clasifica la foto con el modelo de monedas. Sin modelo o si algo falla, lo dice sin cerrar la app. */
    private suspend fun reconocerMoneda(
        bitmap: Bitmap,
        automatico: Boolean = false,
        fotoGeneral: Boolean = false,
    ): ResultadoMoneda {
        val interpreter = interpreteMonedas ?: return ResultadoMoneda(null, 0f, MotivoMoneda.SIN_MODELO)
        return try {
            // El número de clases sale del propio modelo, así que esto sirve igual si se vuelve a
            // entrenar con más clases. Si las etiquetas no coinciden, MonedaMx nunca las toma por
            // una denominación.
            val numClases = interpreter.getOutputTensor(0).shape().last()
            val nombres = if (etiquetasMonedas.size == numClases) {
                etiquetasMonedas
            } else {
                Log.w(TAG_MONEDA, "El modelo tiene $numClases clases y las etiquetas ${etiquetasMonedas.size}.")
                (0 until numClases).map { "clase_$it" }
            }
            // Las fotos del teléfono suelen salir oscuras. El autocontraste por canal (recorte del
            // 2 %) sube el acierto en poca luz y sombra sin cambiar el color de la moneda. Se calcula
            // fuera del hilo principal porque recorre todos los píxeles de la foto.
            val luts = withContext(Dispatchers.Default) { calcularLuts(bitmap) }
            val porVariante = promediarVariantes(bitmap, interpreter, numClases, luts)
                ?: return ResultadoMoneda(null, 0f, MotivoMoneda.NO_CLARO)
            val promedios = promediar(porVariante)

            fun comoLecturas(probabilidades: FloatArray) =
                probabilidades.mapIndexed { i, p -> nombres[i] to p }
            var lecturas = comoLecturas(promedios)
            var lecturasPorVariante = porVariante.map { comoLecturas(it) }
            var variantesElegidas = porVariante

            Log.d(TAG_MONEDA, "probabilidades promedio: " + lecturas.joinToString { "${it.first}=${"%.4f".format(it.second)}" })
            listOf("completa", "espejo", "centro").forEachIndexed { i, nombre ->
                Log.d(
                    TAG_MONEDA,
                    "  variante $nombre: " +
                        lecturasPorVariante[i].joinToString { "${it.first}=${"%.3f".format(it.second)}" },
                )
            }
            var decision = MonedaMx.decidir(lecturas, lecturasPorVariante, automatico)
            Log.d(TAG_MONEDA, "decisión (${if (automatico) "automática" else "pedida"}): $decision")

            if (decision.motivo == MotivoMoneda.NO_CLARO && !automatico) {
                // Segundo modelo de apoyo. Solo se consulta cuando el principal no se decide: si el
                // apoyo está bastante más seguro, se usa su lectura; si no, se conserva la principal.
                var interpreteElegido = interpreter
                val apoyo = interpreteApoyo?.let { promediarVariantes(bitmap, it, numClases, luts) }
                if (apoyo != null) {
                    val promApoyo = promediar(apoyo)
                    val confianzaPrincipal = promedios.maxOrNull() ?: 0f
                    val confianzaApoyo = promApoyo.maxOrNull() ?: 0f
                    // En una foto general nadie pidió la moneda. El apoyo solo puede aportar una
                    // denominación si está muy seguro. En la petición explícita se conserva el
                    // margen de siempre.
                    val cambioPermitido = if (fotoGeneral) {
                        confianzaApoyo >= UMBRAL_APOYO_CORRECCION
                    } else {
                        confianzaApoyo >= confianzaPrincipal + MARGEN_APOYO
                    }
                    if (cambioPermitido) {
                        interpreteElegido = interpreteApoyo!!
                        lecturas = comoLecturas(promApoyo)
                        lecturasPorVariante = apoyo.map { comoLecturas(it) }
                        variantesElegidas = apoyo
                        decision = MonedaMx.decidir(lecturas, lecturasPorVariante, automatico = false)
                        // Un segundo modelo nunca convierte una duda en certeza: si el apoyo
                        // "afirma" la moneda, se dice igual como duda ("parece").
                        if (decision.motivo == MotivoMoneda.SEGURA && decision.denominacion != null) {
                            decision = MonedaMx.dudosa(lecturas.maxByOrNull { it.second }?.first ?: "", decision.confianza)
                        }
                        Log.d(TAG_MONEDA, "el modelo de apoyo está más seguro: $decision")
                    }
                }
                if (decision.motivo == MotivoMoneda.NO_CLARO) {
                    // La moneda puede estar pequeña o descentrada. Antes de rendirse, se prueba una
                    // rejilla de recortes; solo se acepta como duda si coincide con la clase que ya
                    // insinuaba el promedio.
                    decision = rescatarConRecortes(
                        bitmap, interpreteElegido, nombres, numClases, variantesElegidas, lecturas, luts,
                    ) ?: decision
                }
            } else if (decision.motivo == MotivoMoneda.DUDOSA && !automatico && interpreteApoyo != null) {
                // Corrección de una duda: si el apoyo ve otra moneda con mucha más confianza, se usa
                // su clase. Sigue siendo duda, nunca hecho.
                val apoyo = promediarVariantes(bitmap, interpreteApoyo!!, numClases, luts)
                if (apoyo != null) {
                    val promApoyo = promediar(apoyo)
                    val confianzaPrincipal = promedios.maxOrNull() ?: 0f
                    val confianzaApoyo = promApoyo.maxOrNull() ?: 0f
                    val etiquetaPrincipal = lecturas.maxByOrNull { it.second }?.first ?: ""
                    val etiquetaApoyo = promApoyo.mapIndexed { i, p -> nombres[i] to p }.maxByOrNull { it.second }?.first
                    val correccion = etiquetaApoyo?.let {
                        MonedaMx.correccionPorApoyo(
                            etiquetaPrincipal, confianzaPrincipal, it, confianzaApoyo,
                            MARGEN_APOYO, UMBRAL_APOYO_CORRECCION,
                        )
                    }
                    if (correccion != null) {
                        decision = correccion
                        Log.d(TAG_MONEDA, "el apoyo corrige la duda: $decision")
                    }
                }
            } else if (fotoGeneral && decision.motivo == MotivoMoneda.SEGURA) {
                // En una foto general nadie pidió la moneda. Si el principal afirma una denominación
                // pero el apoyo ve otra con más confianza, se prefiere no afirmar ninguna.
                val apoyo = interpreteApoyo?.let { promediarVariantes(bitmap, it, numClases, luts) }
                if (apoyo != null) {
                    val promApoyo = promediar(apoyo)
                    val etiquetaPrincipal = lecturas.maxByOrNull { it.second }?.first ?: ""
                    val etiquetaApoyo = promApoyo.mapIndexed { i, p -> nombres[i] to p }.maxByOrNull { it.second }?.first
                    val contradiccion = etiquetaApoyo?.let {
                        MonedaMx.correccionPorApoyo(
                            etiquetaPrincipal, decision.confianza, it, promApoyo.maxOrNull() ?: 0f,
                            MARGEN_APOYO, UMBRAL_APOYO_CORRECCION,
                        )
                    }
                    if (contradiccion != null) {
                        decision = contradiccion
                        Log.d(TAG_MONEDA, "el apoyo contradice una lectura segura: $decision")
                    }
                }
            }
            decision
        } catch (e: Exception) {
            Log.e(TAG_MONEDA, "Falló la clasificación de la moneda", e)
            ResultadoMoneda(null, 0f, MotivoMoneda.NO_CLARO)
        }
    }

    /**
     * Clasifica las tres variantes de la foto (completa, espejo y recorte central). Devuelve las
     * probabilidades de cada variante, o null si no se pudo. Las copias se liberan al terminar.
     */
    private suspend fun promediarVariantes(
        bitmap: Bitmap,
        interpreter: Interpreter,
        numClases: Int,
        luts: Array<IntArray>,
    ): List<FloatArray>? = withContext(Dispatchers.Default) {
        val espejo = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height,
            Matrix().apply { postScale(-1f, 1f) }, true,
        )
        val centro = recorteCentral(bitmap)
        try {
            listOf(bitmap, espejo, centro).map { variante ->
                val scaled = escalarParaMoneda(variante)
                try {
                    val salida = Array(1) { FloatArray(numClases) }
                    interpreter.run(prepararEntrada(scaled, luts), salida)
                    salida[0]
                } finally {
                    if (scaled !== variante) scaled.recycle()
                }
            }
        } finally {
            // Solo se liberan las copias; la foto original se sigue usando después.
            espejo.recycle()
            if (centro !== bitmap) centro.recycle()
        }
    }

    /** Promedio de las probabilidades de varias variantes. */
    private fun promediar(variantes: List<FloatArray>): FloatArray {
        val promedios = FloatArray(variantes.first().size)
        variantes.forEach { arr -> for (i in promedios.indices) promedios[i] += arr[i] / variantes.size }
        return promedios
    }

    /**
     * Segunda oportunidad para una moneda pequeña o descentrada: promedia una rejilla de recortes al
     * 75 % (ponderando los recortes en los que el modelo está más seguro) y devuelve una duda solo
     * si la clase ganadora coincide con la que ya insinuaba el promedio de las 3 variantes normales.
     */
    private suspend fun rescatarConRecortes(
        bitmap: Bitmap,
        interpreter: Interpreter,
        nombres: List<String>,
        numClases: Int,
        porVariante: List<FloatArray>,
        lecturas: List<Pair<String, Float>>,
        luts: Array<IntArray>,
    ): ResultadoMoneda? = withContext(Dispatchers.Default) {
        try {
            val insinuada = lecturas.maxByOrNull { it.second }?.first?.trim()?.lowercase()
                ?: return@withContext null
            val sumas = FloatArray(numClases)
            var pesoTotal = 0f
            fun acumular(probabilidades: FloatArray) {
                val peso = (probabilidades.maxOrNull() ?: 0f).pow(3)
                if (peso <= 0f) return
                pesoTotal += peso
                for (i in sumas.indices) sumas[i] += probabilidades[i] * peso
            }
            // Se reutilizan las dos variantes que ya se calcularon (foto completa y espejo).
            porVariante.getOrNull(0)?.let(::acumular)
            porVariante.getOrNull(1)?.let(::acumular)

            for (cy in POSICIONES_RESCATE) {
                for (cx in POSICIONES_RESCATE) {
                    val recorte = recorteRelativo(bitmap, ESCALA_RESCATE, cx, cy)
                    val escalado = escalarParaMoneda(recorte)
                    try {
                        val salida = Array(1) { FloatArray(numClases) }
                        interpreter.run(prepararEntrada(escalado, luts), salida)
                        acumular(salida[0])
                    } finally {
                        if (escalado !== recorte) escalado.recycle()
                        if (recorte !== bitmap) recorte.recycle()
                    }
                }
            }
            if (pesoTotal <= 0f) return@withContext null
            val promedios = FloatArray(numClases) { sumas[it] / pesoTotal }
            val indice = promedios.indices.maxByOrNull { promedios[it] } ?: return@withContext null
            val etiqueta = nombres.getOrElse(indice) { "clase_$indice" }
            if (etiqueta.trim().lowercase() != insinuada) return@withContext null
            val confianza = promedios[indice]
            if (confianza < MonedaMx.UMBRAL_RESCATE) return@withContext null
            MonedaMx.dudosa(etiqueta, confianza, esRescate = true).also {
                Log.d(TAG_MONEDA, "rescate por recortes: $it")
            }
        } catch (e: Exception) {
            Log.w(TAG_MONEDA, "No se pudo rescatar la moneda con recortes", e)
            null
        }
    }

    /** Recorte cuadrado relativo a la foto: [escala] es la fracción del lado corto y [cx]/[cy] el centro. */
    private fun recorteRelativo(original: Bitmap, escala: Float, cx: Float, cy: Float): Bitmap {
        val lado = (minOf(original.width, original.height) * escala).toInt().coerceAtLeast(1)
        val x = (original.width * cx - lado / 2f).toInt().coerceIn(0, (original.width - lado).coerceAtLeast(0))
        val y = (original.height * cy - lado / 2f).toInt().coerceIn(0, (original.height - lado).coerceAtLeast(0))
        return Bitmap.createBitmap(original, x, y, lado, lado)
    }

    /**
     * Calcula una tabla de autocontraste por canal (recorte del 1.5 % arriba y abajo), igual que
     * `ImageOps.autocontrast(cutoff=2)` de PIL. Se recorre la foto por filas para no reservar una
     * copia completa de píxeles.
     */
    private fun calcularLuts(original: Bitmap): Array<IntArray> {
        val hist = Array(3) { IntArray(256) }
        val fila = IntArray(original.width)
        for (y in 0 until original.height) {
            original.getPixels(fila, 0, original.width, 0, y, original.width, 1)
            for (px in fila) {
                hist[0][(px shr 16) and 0xFF]++
                hist[1][(px shr 8) and 0xFF]++
                hist[2][px and 0xFF]++
            }
        }
        return AutocontrasteMoneda.calcular(hist, original.width * original.height)
    }

    /**
     * Reduce la foto a [LADO_MONEDA]×[LADO_MONEDA] promediando áreas completas.
     *
     * `Bitmap.createScaledBitmap` con filtro muestrea solo 2×2 píxeles. Cuando la foto mide 1280 px,
     * reducirla de golpe a 224 descarta casi toda la textura de la moneda y el clasificador baja su
     * confianza. El promedio por áreas conserva el detalle y equipara el resultado del teléfono con
     * el del simulador. Se hace en dos pasos (columnas y luego filas) para no recorrer la foto una
     * vez por píxel destino. Si la imagen es más chica que el destino, se usa el escalado normal.
     */
    private fun escalarParaMoneda(original: Bitmap): Bitmap {
        val lado = LADO_MONEDA
        if (original.width == lado && original.height == lado) return original
        if (original.width < lado || original.height < lado) {
            return Bitmap.createScaledBitmap(original, lado, lado, true)
        }

        val ancho = original.width
        val alto = original.height
        val pixeles = IntArray(ancho * alto)
        original.getPixels(pixeles, 0, ancho, 0, 0, ancho, alto)

        val columnas = IntArray(lado + 1) { (it * ancho) / lado }
        val filas = IntArray(lado + 1) { (it * alto) / lado }

        // Promedio horizontal: queda un arreglo de lado × alto.
        val horizontal = IntArray(lado * alto)
        for (y in 0 until alto) {
            val base = y * ancho
            val destino = y * lado
            for (tx in 0 until lado) {
                val desde = base + columnas[tx]
                val hasta = base + maxOf(columnas[tx + 1], columnas[tx] + 1)
                var rojo = 0
                var verde = 0
                var azul = 0
                var cuenta = 0
                var i = desde
                while (i < hasta) {
                    val px = pixeles[i]
                    rojo += (px shr 16) and 0xFF
                    verde += (px shr 8) and 0xFF
                    azul += px and 0xFF
                    cuenta++
                    i++
                }
                horizontal[destino + tx] =
                    (((rojo / cuenta) shl 16) or ((verde / cuenta) shl 8) or (azul / cuenta))
            }
        }

        // Promedio vertical: queda la imagen final de lado × lado.
        val salida = IntArray(lado * lado)
        for (ty in 0 until lado) {
            val desde = filas[ty]
            val hasta = maxOf(filas[ty + 1], desde + 1)
            val destino = ty * lado
            for (tx in 0 until lado) {
                var rojo = 0
                var verde = 0
                var azul = 0
                var y = desde
                while (y < hasta) {
                    val px = horizontal[y * lado + tx]
                    rojo += (px shr 16) and 0xFF
                    verde += (px shr 8) and 0xFF
                    azul += px and 0xFF
                    y++
                }
                val cuenta = hasta - desde
                salida[destino + tx] =
                    (0xFF shl 24) or ((rojo / cuenta) shl 16) or ((verde / cuenta) shl 8) or (azul / cuenta)
            }
        }
        return Bitmap.createBitmap(salida, lado, lado, Bitmap.Config.ARGB_8888)
    }

    /** Prepara un bitmap 224×224 como tensor float32 normalizado con MobileNet V2 preprocessing. */
    private fun prepararEntrada(bitmap: Bitmap, luts: Array<IntArray>): ByteBuffer {
        val entrada = ByteBuffer.allocateDirect(4 * LADO_MONEDA * LADO_MONEDA * 3).order(ByteOrder.nativeOrder())
        val pixels = IntArray(LADO_MONEDA * LADO_MONEDA)
        bitmap.getPixels(pixels, 0, LADO_MONEDA, 0, 0, LADO_MONEDA, LADO_MONEDA)
        val rojo = luts[0]
        val verde = luts[1]
        val azul = luts[2]
        for (px in pixels) {
            entrada.putFloat((rojo[(px shr 16) and 0xFF] - 127.5f) / 127.5f)
            entrada.putFloat((verde[(px shr 8) and 0xFF] - 127.5f) / 127.5f)
            entrada.putFloat((azul[px and 0xFF] - 127.5f) / 127.5f)
        }
        entrada.rewind()
        return entrada
    }

    /** Recorte central cuadrado para dar más peso a la moneda que al fondo. */
    private fun recorteCentral(original: Bitmap): Bitmap {
        val lado = minOf(original.width, original.height)
        val x = (original.width - lado) / 2
        val y = (original.height - lado) / 2
        return Bitmap.createBitmap(original, x, y, lado, lado)
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
        val utiles = etiquetas.filter { !EtiquetasEntorno.esDemasiadoGeneral(it.text) }
        // Primero las etiquetas con confianza suficiente; las dudosas solo si no hay otra cosa.
        val seguras = utiles.filter { it.confidence >= CONFIANZA_RESPALDO }
        val candidatas = if (seguras.isNotEmpty()) seguras else utiles
        // En una foto de una laptop sobre un escritorio, "escritorio" puede tener más confianza que
        // "computadora". Se prefiere el objeto concreto que la persona quiere reconocer y el mueble
        // o la sala quedan solo como respaldo.
        val principales = candidatas.filter {
            EtiquetasEntorno.esObjetoPrincipal(it.text) && EtiquetasEntorno.traducir(it.text) != null
        }
        val elegida = principales.firstOrNull { !EtiquetasEntorno.esDebil(it.text) }
            ?: candidatas.firstOrNull { !EtiquetasEntorno.esDebil(it.text) && EtiquetasEntorno.traducir(it.text) != null }
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
            else -> "al frente"
        }
    }

    private fun ordenHorizontal(posicion: String?): Int = when (posicion) {
        "a la izquierda" -> 0
        "al frente" -> 1
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

    /** Una sola frase natural con todos los objetos, sin "principal" ni "secundario". */
    private fun fraseEscena(objetos: List<ObjetoDescrito>): String {
        if (objetos.isEmpty()) return "No logré identificar objetos claros enfrente."
        val ordenados = objetos.sortedBy { ordenHorizontal(it.posicion) }
        return "Veo ${ordenados.joinToString(" y ") { it.frase }}."
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
        const val TAG_MONEDA = "EntornoMoneda"
        val ETIQUETAS_DE_BILLETE = setOf("banknote", "money", "currency", "cash")
        // "coin" va aparte: una moneda nunca debe tratarse como billete automático.
        val ETIQUETAS_DE_MONEDA = setOf("coin", "coins")
        val ETIQUETAS_DE_PAPEL = setOf("paper", "paper product", "document", "rectangle")
        // Objetos que el etiquetador puede confundir con papel o con un billete por sus números.
        val ETIQUETAS_DE_OBJETO_NO_BILLETE = setOf(
            "laptop", "netbook", "personal computer", "desktop computer", "computer",
            "computer keyboard", "keyboard", "computer monitor", "monitor", "display", "screen",
            "television", "tablet computer", "mobile phone", "smartphone", "computer mouse", "mouse",
            "book", "notebook",
        )
        const val MODELO_DE_OBJETOS = "efficientdet_lite0.tflite"
        const val MODELO_DE_MONEDAS = "monedas.tflite"
        const val MODELO_DE_APOYO = "monedas_apoyo.tflite"
        const val ARCHIVO_ETIQUETAS_MONEDAS = "monedas_labels.txt"
        const val NUM_CLASES_ESPERADAS = 6
        const val LADO_MONEDA = 224
        // El modelo de apoyo solo reemplaza al principal si está al menos 0.10 más seguro.
        const val MARGEN_APOYO = 0.10f
        // Para corregir una duda ya existente, el apoyo debe estar muy seguro (0.85).
        const val UMBRAL_APOYO_CORRECCION = 0.85f
        // Rescate para monedas pequeñas o descentradas: recortes al 75 % del lado corto.
        const val ESCALA_RESCATE = 0.75f
        val POSICIONES_RESCATE = floatArrayOf(0.5f, 0.22f, 0.78f)
        const val MAX_OBJETOS = 5
        const val MAX_DETECCIONES = 10
        const val CONFIANZA_MINIMA = 0.4f
        // Un solo cliente de ML Kit: se usa el umbral bajo y cada camino filtra lo que necesita.
        const val CONFIANZA_ETIQUETADOR = 0.4f
        const val CONFIANZA_RESPALDO = 0.55f
        // Por debajo de esto se dice "posiblemente" en lugar de afirmarlo.
        const val CONFIANZA_SEGURA_DETECTOR = 0.6f
        // El etiquetador de respaldo acierta desde ~0.55 (es el mismo umbral con el que se acepta).
        // Exigir 0.8 hacía que casi todo se dijera como "posiblemente", incluso una computadora clara.
        const val CONFIANZA_SEGURA_RESPALDO = 0.55f
        const val LADO_MAXIMO = 1280
        // Palabra completa: "monedero" o "monedita" no deben disparar el clasificador de monedas.
        val MONEDA_EN_DESCRIPCION = Regex("\\bmonedas?\\b", RegexOption.IGNORE_CASE)
        // Si la descripción general menciona billetes o dinero, la lectura de billetes manda.
        val BILLETE_EN_DESCRIPCION = Regex("\\b(billetes?|dinero|efectivo)\\b", RegexOption.IGNORE_CASE)
    }
}
