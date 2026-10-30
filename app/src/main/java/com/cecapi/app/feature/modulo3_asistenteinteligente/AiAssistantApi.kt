package com.cecapi.app.feature.modulo3_asistenteinteligente

import android.util.Base64
import com.cecapi.app.BuildConfig
import com.cecapi.app.core.voice.IntentFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/** Lo que `/entender` decidió: un módulo a abrir ([moduleCode], un [com.cecapi.app.core.model.ModuloCecapi.storageCode]) o una [answer]. */
data class AiUnderstanding(
    val moduleCode: String? = null,
    val answer: String? = null,
)

/** Por qué la IA no respondió, para decirle a la persona algo útil en vez de un error genérico. */
enum class AiFailure { DISABLED, RATE_LIMITED, UNAVAILABLE, UNREACHABLE, UNAUTHORIZED, REJECTED }

/**
 * [REJECTED]: el servidor no aceptó la petición y explica por qué en [message], listo para decirse en voz alta
 * (foto inválida o demasiado grande, o un pedido con datos personales que no se manda a la IA).
 */
class AiException(val failure: AiFailure, message: String) : IllegalStateException(message)

/** Qué tiene que decir `/ver` de la foto. */
enum class ModoVision(val valor: String) {
    /** La denominación de un billete mexicano ("Billete de 50 pesos"). */
    BILLETE("billete"),

    /** Hasta 3 objetos y si están a la izquierda, al centro o a la derecha. */
    ESCENA("escena"),
}

/** Los textos exactos que se dicen en voz alta; viven aquí para que Inicio y el asistente digan lo mismo. */
object AiMessages {
    const val RATE_LIMITED = "Hiciste muchas preguntas seguidas. Espera un minuto y vuelve a intentar."
    const val UNAVAILABLE = "El asistente no está disponible en este momento. Intenta más tarde."
    const val UNREACHABLE = "No pude conectar con el asistente inteligente. Intenta de nuevo en un momento."
    const val DISABLED = "La inteligencia artificial está desactivada. Actívala en Configuración."
    const val UNAUTHORIZED = "El servidor del asistente no reconoce a esta versión de la app. Avisa a quien te la instaló."

    /** Se dice si la respuesta tarda: el servidor gratuito se duerme y tarda cerca de un minuto en despertar. */
    const val WAKING = "El servidor estaba dormido y está despertando. Puede tardar hasta un minuto. Sigo aquí."

    fun forFailure(error: Throwable): String = when ((error as? AiException)?.failure) {
        AiFailure.RATE_LIMITED -> RATE_LIMITED
        AiFailure.UNAVAILABLE -> UNAVAILABLE
        AiFailure.DISABLED -> DISABLED
        AiFailure.UNAUTHORIZED -> UNAUTHORIZED
        AiFailure.REJECTED -> error.message?.takeIf { it.isNotBlank() } ?: UNAVAILABLE
        else -> UNREACHABLE
    }
}

interface AiAssistantApi {
    suspend fun ask(pregunta: String, contexto: List<String>): Result<String>

    /** Igual que [ask], pero el backend primero revisa si la frase nombra un módulo a abrir. */
    suspend fun understand(pregunta: String, contexto: List<String>): Result<AiUnderstanding>

    /** Redacta el documento que pidió la persona (pantalla Consultas). Puede ser largo, a diferencia de [ask]. */
    suspend fun document(pedido: String): Result<String>

    /** Describe una foto ya comprimida en JPEG ([FotoParaIa]) según el [modo]. */
    suspend fun describe(fotoJpeg: ByteArray, modo: ModoVision): Result<String>
}

/**
 * Habla con un backend pequeño que TÚ controlas — nunca directamente con la API de un proveedor
 * de LLM externo desde el teléfono. Incluir la clave de API de cualquier proveedor dentro de un
 * APK de Android significa que cualquiera que lo descompile obtiene la clave — no existe un
 * almacenamiento secreto del lado del cliente que solucione esto, así que la solicitud debe
 * retransmitirse a través de un servidor que este equipo (Módulo 3) sea dueño y despliegue.
 *
 * Proveedor recomendado para ese backend: **Gemini API** (Google AI Studio) —
 * tiene un nivel gratuito genuino sin límite de tiempo, lo cual importa para un
 * proyecto estudiantil sin presupuesto. Esta es una decisión solo del backend: cambiar a
 * otro proveedor después nunca toca este archivo ni ningún otro código de Android,
 * ya que la app solo habla con tu propio endpoint de abajo.
 *
 * Contrato del backend (ver backend/README.md):
 *   POST {AI_PROXY_BASE_URL}   (termina en /preguntar)
 *   body:     {"pregunta": "...", "contexto": ["turno 1", "turno 2", ...]}
 *   response: {"respuesta": "..."}
 *   POST <same server>/entender   (same body)
 *   response: {"tipo": "comando", "comando": "<storageCode>"} or {"tipo": "respuesta", "respuesta": "..."}
 *   POST <same server>/documento  body: {"pedido": "..."}  response: {"documento": "...", "aviso": null}
 *   POST <same server>/ver        body: {"imagen": "<base64>", "modo": "billete"|"escena"}  response: {"respuesta": "..."}
 *
 * Si el servidor tiene `CECAPI_CLAVE_APP`, cada petición lleva el encabezado `X-Clave-App` con
 * [BuildConfig.AI_APP_KEY] (sale de `cecapi.claveApp` en local.properties; ver backend/PUBLICAR.md).
 */
@Singleton
class ProxyAiAssistantApi @Inject constructor(
    private val intentFallback: IntentFallback,
) : AiAssistantApi {

    override suspend fun ask(pregunta: String, contexto: List<String>): Result<String> =
        post("/preguntar", preguntaJson(pregunta, contexto)).mapCatching { it.getString("respuesta") }

    override suspend fun document(pedido: String): Result<String> {
        val resultado = post("/documento", JSONObject().put("pedido", pedido))
        // Un servidor de antes de /documento responde 404: se pide por /preguntar, como antes.
        if ((resultado.exceptionOrNull() as? HttpError)?.code == HttpURLConnection.HTTP_NOT_FOUND) {
            return ask("$INSTRUCCION_DOCUMENTO_ANTERIOR $pedido", emptyList())
        }
        return resultado.mapCatching { json ->
            if (json.isNull("documento")) {
                // El pedido traía datos personales: no salió a la IA y el servidor dice por qué.
                throw AiException(AiFailure.REJECTED, json.optString("aviso"))
            }
            json.getString("documento")
        }
    }

    override suspend fun describe(fotoJpeg: ByteArray, modo: ModoVision): Result<String> {
        val body = JSONObject()
            .put("imagen", Base64.encodeToString(fotoJpeg, Base64.NO_WRAP))
            .put("modo", modo.valor)
        return post("/ver", body).mapCatching { it.getString("respuesta") }
    }

    private fun preguntaJson(pregunta: String, contexto: List<String>): JSONObject =
        JSONObject().put("pregunta", pregunta).put("contexto", JSONArray(contexto))

    override suspend fun understand(pregunta: String, contexto: List<String>): Result<AiUnderstanding> {
        val resultado = post("/entender", preguntaJson(pregunta, contexto))
        // Un backend de antes de que existiera /entender responde 404: preguntarle de la forma simple en vez de fallar.
        if ((resultado.exceptionOrNull() as? HttpError)?.code == HttpURLConnection.HTTP_NOT_FOUND) {
            return ask(pregunta, contexto).map { AiUnderstanding(answer = it) }
        }
        return resultado.mapCatching { json ->
            if (json.optString("tipo") == "comando") {
                AiUnderstanding(moduleCode = json.getString("comando"))
            } else {
                AiUnderstanding(answer = json.getString("respuesta"))
            }
        }
    }

    /** [detail] es el mensaje del servidor (FastAPI lo manda en `detail`), si lo hubo. */
    private class HttpError(val code: Int, val detail: String? = null) :
        IllegalStateException("El asistente no respondió (código $code).")

    /** Traduce un código HTTP o un fallo de red a [AiException]; 404 se deja como [HttpError] porque `understand` lo maneja. */
    private fun classify(error: Throwable): Throwable = when {
        error is HttpError && error.code == 429 -> AiException(AiFailure.RATE_LIMITED, error.message.orEmpty())
        error is HttpError && error.code == 401 -> AiException(AiFailure.UNAUTHORIZED, error.message.orEmpty())
        // 400 de /ver: el servidor explica qué pasó con la foto con una frase que se puede decir.
        error is HttpError && error.code == 400 && !error.detail.isNullOrBlank() ->
            AiException(AiFailure.REJECTED, error.detail)
        error is HttpError && error.code in 500..599 -> AiException(AiFailure.UNAVAILABLE, error.message.orEmpty())
        error is HttpError -> error
        error is AiException -> error
        else -> AiException(AiFailure.UNREACHABLE, error.message.orEmpty())
    }

    private suspend fun post(path: String, body: JSONObject): Result<JSONObject> =
        withContext(Dispatchers.IO) {
            // La persona activa esto en Configuración; desactivado por defecto (los proveedores de IA no permiten menores).
            // Se revisa aquí, en el único lugar que en realidad llega a la red, para que ningún llamador pueda evitarlo.
            if (!intentFallback.disponible) {
                return@withContext Result.failure(
                    AiException(AiFailure.DISABLED, "La inteligencia artificial está desactivada."),
                )
            }
            // El servidor gratuito se duerme tras unos minutos sin uso y tarda cerca de un minuto en despertar:
            // se espera hasta TIMEOUT_READ_MS por la respuesta y, si ni siquiera se logró conectar, se reintenta una vez.
            var lastError: Throwable? = null
            repeat(ATTEMPTS) { attempt ->
                try {
                    return@withContext attemptPost(path, body)
                } catch (error: HttpError) {
                    // El servidor sí contestó (429, 503...): reintentar enseguida no ayuda.
                    return@withContext Result.failure(classify(error))
                } catch (error: Exception) {
                    lastError = error
                    if (attempt < ATTEMPTS - 1) delay(RETRY_PAUSE_MS)
                }
            }
            Result.failure(classify(lastError ?: IllegalStateException("Sin respuesta.")))
        }

    /** Una sola petición. Lanza [HttpError] si el servidor contestó con error, o la excepción de red si no hubo respuesta. */
    private fun attemptPost(path: String, body: JSONObject): Result<JSONObject> {
        // La configuración puede ser el servidor ("http://IP:8000") o la URL completa anterior (".../preguntar"): ambas funcionan.
        val base = BuildConfig.AI_PROXY_BASE_URL.trimEnd('/').removeSuffix("/preguntar")
        val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = TIMEOUT_CONNECT_MS
            readTimeout = TIMEOUT_READ_MS
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (BuildConfig.AI_APP_KEY.isNotBlank()) setRequestProperty("X-Clave-App", BuildConfig.AI_APP_KEY)
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            if (connection.responseCode !in 200..299) throw HttpError(connection.responseCode, leerDetalle(connection))
            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            return Result.success(JSONObject(responseText))
        } finally {
            connection.disconnect()
        }
    }

    /** El `detail` de un error de FastAPI, o null si no se puede leer. */
    private fun leerDetalle(connection: HttpURLConnection): String? = try {
        connection.errorStream?.bufferedReader()?.use { it.readText() }
            ?.let { JSONObject(it).opt("detail") as? String }
    } catch (e: Exception) {
        null
    }

    private companion object {
        /** Solo para servidores sin /documento (los de antes del 30 oct 2026). */
        const val INSTRUCCION_DOCUMENTO_ANTERIOR =
            "Redacta en español el documento que se pide a continuación. Entrega solo el documento listo para " +
                "usar, con título, sin explicaciones antes ni después y sin formato especial. Pedido:"

        /** Conectar con un servidor dormido puede tardar: se da más margen que antes (5 s). */
        const val TIMEOUT_CONNECT_MS = 15_000

        /** Despertar el servidor tarda cerca de un minuto; antes se esperaban solo 12 s. */
        const val TIMEOUT_READ_MS = 75_000
        const val ATTEMPTS = 2
        const val RETRY_PAUSE_MS = 2_000L
    }
}
