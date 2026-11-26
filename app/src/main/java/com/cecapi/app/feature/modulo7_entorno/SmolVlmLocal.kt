package com.cecapi.app.feature.modulo7_entorno

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Descripción general con SmolVLM2-500M, todo en el teléfono y en RAM.
 *
 * El modelo se carga la primera vez que se pide una descripción y se libera solo después de un rato
 * sin usarlo o al salir de la pantalla. Nunca se usa para billetes ni monedas: esos siguen con su
 * propio reconocimiento local.
 *
 * SmolVLM2 describe mucho mejor en inglés que en español, así que la descripción se pide en inglés
 * y se traduce en el teléfono con ML Kit. La primera traducción descarga el modelo de español una
 * sola vez; después funciona sin conexión. Si el traductor no está listo, se devuelve null y la
 * pantalla usa la descripción del detector local, siempre en español.
 */
@Singleton
class SmolVlmLocal @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val candado = Mutex()
    private val inferenciaEnCurso = AtomicBoolean(false)
    private var manejador: Long = 0L
    private var traductor: Translator? = null

    private var liberarPorInactividad: Job? = null

    /** Último motivo por el que no se pudo describir, ya en español y listo para avisar. */
    @Volatile
    var ultimoMotivo: String? = null
        private set

    /** False si este teléfono no puede correr el modelo: quien describe usa entonces el detector. */
    val disponible: Boolean get() = bibliotecaCargada

    private val bibliotecaCargada: Boolean by lazy {
        // La IA está compilada para arm64-v8a (teléfonos) y x86_64 (emuladores).
        if (Build.SUPPORTED_ABIS.none { it == "arm64-v8a" || it == "x86_64" }) {
            Log.i(TAG, "La IA local no está compilada para este procesador.")
            false
        } else if (Build.SUPPORTED_ABIS.first() == "arm64-v8a" &&
            !ProcesadorCompatible.admiteModeloLocal(runCatching { File("/proc/cpuinfo").readText() }.getOrDefault(""))
        ) {
            Log.i(TAG, "Este procesador no tiene las instrucciones que pide la IA local; se usa el detector.")
            false
        } else {
            try {
                System.loadLibrary("smolvlm_jni")
                true
            } catch (t: Throwable) {
                Log.w(TAG, "No se pudo cargar libsmolvlm_jni.so", t)
                false
            }
        }
    }

    /** La IA se puede intentar en este teléfono. */
    fun disponible(): Boolean = bibliotecaCargada

    /**
     * Empieza a descargar el traductor inglés-español en segundo plano. No usa la cámara ni el
     * modelo de visión; solo deja listo el español para la primera descripción. La descarga se
     * hace una vez y después la traducción funciona sin conexión.
     */
    fun prepararTraductor() {
        if (!bibliotecaCargada) return
        alcance.launch(Dispatchers.IO) {
            try {
                val cliente = obtenerTraductor()
                cliente.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
                Log.i(TAG, "Traductor inglés-español listo.")
            } catch (t: Throwable) {
                Log.i(TAG, "El traductor todavía no está listo: ${t.message}")
            }
        }
    }

    /**
     * Deja listo el modelo de visión en segundo plano. Al abrir la pantalla del entorno se gana
     * la carga inicial; cuando llega la foto solo queda analizarla.
     */
    fun precargar() {
        if (!bibliotecaCargada) return
        alcance.launch(Dispatchers.IO) {
            candado.withLock {
                try {
                    if (cargarModelo() != 0L) {
                        programarLiberacion()
                    }
                } catch (t: Throwable) {
                    Log.i(TAG, "Todavía no pude preparar el modelo: ${t.message}")
                }
            }
        }
    }

    /**
     * Describe la foto y devuelve el texto en español. El modelo genera en inglés (donde es mucho
     * más preciso) y ML Kit lo traduce. Devuelve null si no hay librería, modelo, texto o
     * traducción; "qué hay enfrente" avisa para reintentar y la accesibilidad usa su respaldo.
     */
    suspend fun describir(bitmap: Bitmap): String? = describirConPrompt(bitmap, PROMPT)

    /**
     * Describe una captura de pantalla del teléfono. Usa un prompt propio para nombrar la app o
     * la pantalla, el contenido principal y los controles, en vez del prompt de una escena.
     */
    suspend fun describirPantalla(bitmap: Bitmap): String? =
        describirConPrompt(
            bitmap,
            PROMPT_PANTALLA,
            paraPantalla = true,
            maxTokens = MAX_TOKENS_PANTALLA,
            tiempoMaximoMs = TIEMPO_PANTALLA_MS,
        )

    private suspend fun describirConPrompt(
        bitmap: Bitmap,
        prompt: String,
        paraPantalla: Boolean = false,
        maxTokens: Int = MAX_TOKENS,
        tiempoMaximoMs: Long = TIEMPO_INFERENCIA_MS,
    ): String? {
        ultimoMotivo = null
        if (!bibliotecaCargada) {
            ultimoMotivo = "No pude describir la foto."
            return null
        }
        if (bitmap.width <= 0 || bitmap.height <= 0) {
            ultimoMotivo = "La foto no tiene un tamaño válido."
            return null
        }

        cancelarLiberacion()
        if (!inferenciaEnCurso.compareAndSet(false, true)) {
            ultimoMotivo = "El modelo todavía está terminando el análisis anterior."
            return null
        }
        // La primera carga del modelo puede tardar. Se espera aquí, fuera del tiempo de
        // reconocimiento, para que la primera pulsación de "qué hay enfrente" no caiga al detector.
        val modeloListo = withContext(Dispatchers.IO) {
            candado.withLock {
                try {
                    cargarModelo() != 0L
                } catch (t: Throwable) {
                    Log.e(TAG, "No pude preparar el modelo antes de describir", t)
                    false
                }
            }
        }
        if (!modeloListo) {
            inferenciaEnCurso.set(false)
            programarLiberacion()
            return null
        }
        val rgb = withContext(Dispatchers.Default) { pixelesRgb(bitmap) } ?: run {
            ultimoMotivo = "No pude preparar la foto."
            inferenciaEnCurso.set(false)
            programarLiberacion()
            return null
        }
        // El JNI no coopera con la cancelación mientras genera texto. El trabajo vive en el scope
        // de la app y solo se espera un máximo; si la tarea nativa sigue, la pantalla ya puede
        // mostrar el aviso de reintento (o el respaldo de accesibilidad) sin quedarse girando.
        val trabajo = alcance.async(Dispatchers.Default) {
            try {
                candado.withLock {
                    try {
                        val id = cargarModelo()
                        if (id == 0L) {
                            ultimoMotivo = "No pude describir la foto."
                            return@withLock null
                        }
                        val inicio = SystemClock.elapsedRealtime()
                        val ingles = nativeDescribe(id, rgb.datos, rgb.ancho, rgb.alto, prompt, maxTokens)
                            .trim()
                        Log.d(TAG, "Inferencia local en ${SystemClock.elapsedRealtime() - inicio} ms.")
                        Log.d(TAG, "Descripción en inglés: ${ingles.take(240)}")
                        val traducido = traducir(ingles)
                        if (traducido == null) {
                            ultimoMotivo = "No pude traducir la descripción de la foto."
                            null
                        } else {
                            val espanol = if (paraPantalla) {
                                FormatoDescripcion.aplicarPantalla(traducido)
                            } else {
                                FormatoDescripcion.aplicar(traducido)
                            }
                            if (espanol == null || !descripcionUtil(espanol)) {
                                Log.d(TAG, "La descripción no salió clara: ${traducido.take(160)}")
                                ultimoMotivo = "La descripción no salió clara."
                                null
                            } else {
                                Log.d(TAG, "Descripción local: $espanol")
                                espanol
                            }
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Falló la descripción local", t)
                        ultimoMotivo = "No pude describir la foto."
                        null
                    }
                }
            } finally {
                inferenciaEnCurso.set(false)
                if (manejador != 0L) programarLiberacion()
            }
        }
        val descripcion = withTimeoutOrNull(tiempoMaximoMs) { trabajo.await() }
        if (descripcion == null) {
            if (!trabajo.isCompleted) ultimoMotivo = "El análisis tardó demasiado; intenta de nuevo."
            programarLiberacion()
            return null
        }

        Log.d(TAG, "Descripción local en español lista.")
        programarLiberacion()
        return descripcion
    }

    /**
     * El modelo pequeño a veces contesta con una negativa o con una frase vacía. Eso no sirve como
     * descripción: es mejor dejar que el detector local intente reconocer la foto.
     */
    private fun descripcionUtil(texto: String): Boolean {
        val limpio = texto.trim()
        if (limpio.length < 12) return false
        if (limpio.count { it.isLetter() } < 8) return false
        val palabras = limpio.split(Regex("\\s+")).filter { palabra -> palabra.any { it.isLetter() } }
        if (palabras.size < 3) return false
        val minusculas = limpio.lowercase()
        if (minusculas.startsWith("describe the main objects")) return false
        if (PATRONES_INUTILES.any { it.containsMatchIn(minusculas) }) return false
        if (PATRONES_INGLES.any { it.containsMatchIn(minusculas) }) return false
        return true
    }

    /** Libera el modelo de visión. Se llama al salir de la pantalla o al quedar inactiva. */
    fun liberar() {
        cancelarLiberacion()
        alcance.launch {
            candado.withLock { liberarAhora() }
        }
    }

    private suspend fun cargarModelo(): Long {
        if (manejador != 0L) return manejador
        val carpeta = File(context.filesDir, "smolvlm")
        val (modelo, proyector) = withContext(Dispatchers.IO) {
            copiarAsset(ASSET_MODELO, File(carpeta, "SmolVLM2-500M-Q8_0.gguf")) to
                copiarAsset(ASSET_MMPROJ, File(carpeta, "mmproj-SmolVLM2-500M-Q8_0.gguf"))
        }
        if (modelo == null || proyector == null) {
            ultimoMotivo = "No hay espacio suficiente para guardar los datos de la aplicación."
            return 0L
        }
        val hilos = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        val id = withContext(Dispatchers.IO) {
            nativeInit(modelo.absolutePath, proyector.absolutePath, hilos, IMAGE_MAX_TOKENS)
        }
        if (id == 0L) {
            ultimoMotivo = "No pude describir la foto."
            return 0L
        }
        manejador = id
        Log.i(TAG, "Modelo local cargado con $hilos hilos.")
        return id
    }

    private suspend fun traducir(texto: String): String? {
        if (texto.isBlank()) return null
        return try {
            val cliente = obtenerTraductor()
            // Si el modelo ya se descargó, esto termina de inmediato. El límite evita que una
            // primera descarga sin conexión retrase la foto que la persona ya está esperando.
            val descarga = withTimeoutOrNull(TIEMPO_DESCARGA_MS) {
                cliente.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
                true
            }
            if (descarga != true) {
                Log.i(TAG, "La descarga del traductor sigue en curso.")
                return null
            }
            withTimeoutOrNull(TIEMPO_TRADUCCION_MS) { cliente.translate(texto).await() }
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        } catch (t: Throwable) {
            Log.w(TAG, "No pude traducir la descripción al español: ${t.message}")
            null
        }
    }

    private fun obtenerTraductor(): Translator {
        traductor?.let { return it }
        val nuevo = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.SPANISH)
                .build(),
        )
        traductor = nuevo
        return nuevo
    }

    private fun programarLiberacion() {
        liberarPorInactividad?.cancel()
        liberarPorInactividad = alcance.launch {
            delay(INACTIVIDAD_MS)
            candado.withLock { liberarAhora() }
        }
    }

    private fun cancelarLiberacion() {
        liberarPorInactividad?.cancel()
        liberarPorInactividad = null
    }

    private fun liberarAhora() {
        if (manejador != 0L) {
            try {
                nativeFree(manejador)
            } catch (t: Throwable) {
                Log.w(TAG, "No pude liberar el modelo local", t)
            }
            manejador = 0L
        }
    }

    private fun pixelesRgb(origen: Bitmap): PixelesRgb? {
        val escala = LADO_MAXIMO.toFloat() / max(origen.width, origen.height)
        val ancho: Int
        val alto: Int
        val bitmap: Bitmap
        if (escala >= 1f) {
            ancho = origen.width
            alto = origen.height
            bitmap = origen
        } else {
            ancho = (origen.width * escala).roundToInt().coerceAtLeast(1)
            alto = (origen.height * escala).roundToInt().coerceAtLeast(1)
            bitmap = try {
                Bitmap.createScaledBitmap(origen, ancho, alto, true)
            } catch (t: Throwable) {
                Log.w(TAG, "No pude reducir la foto para la IA", t)
                return null
            }
        }
        val argb = IntArray(ancho * alto)
        bitmap.getPixels(argb, 0, ancho, 0, 0, ancho, alto)
        if (bitmap !== origen) bitmap.recycle()
        val datos = ByteArray(ancho * alto * 3)
        var destino = 0
        for (color in argb) {
            datos[destino++] = ((color shr 16) and 0xFF).toByte()
            datos[destino++] = ((color shr 8) and 0xFF).toByte()
            datos[destino++] = (color and 0xFF).toByte()
        }
        return PixelesRgb(datos, ancho, alto)
    }

    private fun copiarAsset(nombre: String, destino: File): File? {
        return try {
            val tamano = tamanoAsset(nombre)
            if (destino.exists() && destino.length() > 0L &&
                (tamano <= 0L || destino.length() == tamano)) {
                return destino
            }
            val carpeta = destino.parentFile
            if (carpeta == null) return null
            if (!carpeta.exists() && !carpeta.mkdirs()) return null
            if (tamano > 0L && carpeta.usableSpace < tamano + ESPACIO_EXTRA) {
                Log.w(TAG, "No hay espacio para copiar $nombre")
                return null
            }
            val temporal = File(carpeta, destino.name + ".parcial")
            context.assets.open(nombre).use { entrada ->
                FileOutputStream(temporal).use { salida ->
                    entrada.copyTo(salida, BUFFER_COPIA)
                }
            }
            if (destino.exists()) destino.delete()
            if (!temporal.renameTo(destino)) {
                temporal.copyTo(destino, overwrite = true)
                temporal.delete()
            }
            Log.i(TAG, "Modelo copiado a $destino")
            destino
        } catch (t: Throwable) {
            Log.e(TAG, "No pude copiar $nombre", t)
            null
        }
    }

    private fun tamanoAsset(nombre: String): Long = try {
        context.assets.openFd(nombre).use { it.declaredLength }
    } catch (t: Throwable) {
        -1L
    }

    private data class PixelesRgb(val datos: ByteArray, val ancho: Int, val alto: Int)

    private external fun nativeInit(
        rutaModelo: String,
        rutaProyector: String,
        hilos: Int,
        maxTokensImagen: Int,
    ): Long

    private external fun nativeDescribe(
        manejador: Long,
        rgb: ByteArray,
        ancho: Int,
        alto: Int,
        pregunta: String,
        maxTokens: Int,
    ): String

    private external fun nativeFree(manejador: Long)

    private companion object {
        const val TAG = "SmolVLM"
        const val ASSET_MODELO = "smolvlm/SmolVLM2-500M-Q8_0.gguf"
        const val ASSET_MMPROJ = "smolvlm/mmproj-SmolVLM2-500M-Q8_0.gguf"
        const val LADO_MAXIMO = 512
        // En el teléfono, 96 tokens tardaban ~12 s y la respuesta no llegaba al límite de 10 s.
        // Con 64 tokens la respuesta cabe en ~9 s. Si el modelo se queda corto, FormatoDescripcion
        // elimina la última frase incompleta en vez de decir una descripción a medias.
        const val MAX_TOKENS = 64
        // "Qué hay enfrente" usa siempre la IA local. Una foto compleja puede tardar ~11-12 s en
        // el teléfono; se le da hasta 20 s para que no caiga al detector ni muestre palabras
        // sueltas. Si aun así no termina, se avisa para reintentar.
        const val TIEMPO_INFERENCIA_MS = 20_000L
        // La accesibilidad no tiene el límite de 10 s del lector de entorno; se le da más margen
        // para mirar imágenes, colores, si son animadas, un conteo aproximado y personajes.
        const val MAX_TOKENS_PANTALLA = 128
        const val TIEMPO_PANTALLA_MS = 20_000L
        const val TIEMPO_DESCARGA_MS = 2_000L
        const val TIEMPO_TRADUCCION_MS = 4_000L

        // Detalle de la imagen que se codifica. Con 32 tokens se conserva la vista general y
        // se recorta el tiempo del codificador de visión, que es la parte más lenta.
        const val IMAGE_MAX_TOKENS = 32
        const val INACTIVIDAD_MS = 90_000L
        const val BUFFER_COPIA = 1024 * 1024
        const val ESPACIO_EXTRA = 20L * 1024 * 1024

        // En inglés: SmolVLM2 describe mucho mejor en inglés y luego se traduce con ML Kit.
        // Se piden escena completa, objetos grandes y pequeños, posiciones y relaciones; no color.
        const val PROMPT =
            "Describe this scene in English in 2 short sentences, using simple and clear English. " +
                "Name the main furniture and objects you can see clearly, including large and small " +
                "items, and say where each one is (left, center, right, foreground, background). " +
                "Explain how the objects are arranged. Be precise about left and right from the " +
                "viewer's perspective. If you are not sure about an object or where it is, say " +
                "'possibly' instead of inventing it. Do not read or repeat text, brand names, or " +
                "labels. Do not describe only one object."

        // Para la accesibilidad: la captura es una pantalla de teléfono, no una habitación.
        const val PROMPT_PANTALLA =
            "Describe this phone screen in English in 2 or 3 short sentences, using simple and " +
                "clear English. Say which app or screen is open and what the person can see: the " +
                "main content, buttons, important text, and every image, photo, drawing, or figure. " +
                "If it is an infographic, diagram, chart, map, or school poster, say what topic it " +
                "shows, the important person or historical figure if clearly recognizable, and the " +
                "main labels or data. For each image say what it is (person, animal, object, " +
                "flower, etc.), its main colors, whether it looks real or animated, and roughly " +
                "how many there are (one, two, several, many). If it looks like a meme, say 'it " +
                "looks like a meme'. If you clearly recognize a famous person, historical figure, " +
                "or pop-culture icon, name them; if not, describe the person or leave them out. " +
                "Only name a person if you are confident; do not invent names. Omit anything you " +
                "are not sure about. Summarize; do not read every word."

        // Solo frases que de verdad delatan inglés. La versión anterior incluía "a" y "with",
        // que aparecen en español ("a la derecha"), y descartaba descripciones válidas.
        val PATRONES_INGLES = listOf(
            Regex("\\b(there is|there are|on the|in the|sitting|standing|is placed|are placed|the image|the photo|the picture|on a|in a)\\b"),
        )

        /** Respuestas que no describen nada y que es mejor cambiar por el detector local. */
        val PATRONES_INUTILES = listOf(
            Regex("\\b(i'm sorry|i am sorry|i can't|i cannot|unable to|as an ai)\\b"),
            Regex("\\b(cannot|can't) (see|describe|identify|determine)\\b"),
            Regex("\\b(no|without) (image|photo|objects?|content)\\b"),
            Regex("\\b(image|photo) (does not|doesn't|do not|don't) (contain|show|appear)\\b"),
            Regex("\\b(nothing|no object) (is |was )?(visible|shown|detected)\\b"),
            Regex("\\b(the )?image is (blurry|too dark|unclear|not clear)\\b"),
            Regex("\\bplease (provide|upload|share) (an? )?(image|photo)\\b"),
            // El modelo también puede negarse en español. Si no describe objetos, no sirve
            // como respuesta; es mejor dejar que el detector local intente reconocer la foto.
            Regex("\\bno (hay|veo|puedo ver|identifico) (nada|ningún objeto|ningun objeto|objetos)\\b"),
            Regex("\\bno se (ve|observa|distingue) (nada|ningún objeto|ningun objeto)\\b"),
            Regex("\\b(lo siento|no puedo (ver|identificar|describir)|no me es posible)\\b"),
        )
    }
}
