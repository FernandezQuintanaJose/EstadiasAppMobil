package com.cecapi.app.core.voice

/**
 * Reconoce las frases que controlan al asistente aunque lleguen con relleno ("cállate, por favor", "ya cállate")
 * o precedidas de su nombre ("ALIA, silencio"). Antes solo contaba la frase exacta, así que en Consultas
 * "ALIA" o "cállate por favor" se tomaban como una pregunta.
 */
object FrasesDeControl {
    enum class Control { SILENCIO, DETENER }

    val SILENCIO = setOf("silencio", "espera", "esperame", "callate", "un momento", "un segundo")
    val DETENER = setOf("para", "detente", "alto", "basta", "para ya", "ya para", "hasta luego", "adios", "apagate")

    private val RELLENO_INICIO = listOf("oye ", "ya ", "ahora ")
    private val RELLENO_FIN = listOf(" por favor", " porfa", " ya", " ahora")

    /** Qué control pide [texto], o null si es otra cosa. "para qué sirve" no es "para": tiene que ser la frase entera. */
    fun control(texto: String): Control? {
        val exacto = VoiceText.normalize(texto)
        clasificar(exacto)?.let { return it }
        var t = exacto
        repeat(2) {
            RELLENO_INICIO.firstOrNull { t.startsWith(it) }?.let { t = t.removePrefix(it) }
            RELLENO_FIN.firstOrNull { t.endsWith(it) }?.let { t = t.removeSuffix(it) }
        }
        return clasificar(t)
    }

    private fun clasificar(t: String): Control? = when (t) {
        in SILENCIO -> Control.SILENCIO
        in DETENER -> Control.DETENER
        else -> null
    }

    /**
     * Si [texto] empieza con el nombre del asistente (o "oye"/"hola" y el nombre), devuelve lo que sigue,
     * conservando acentos y mayúsculas; "" si solo dijo el nombre; null si no empieza con él.
     * Solo al inicio: "quién creó a ALIA" sigue siendo una pregunta.
     */
    fun quitarNombre(texto: String, nombres: Collection<String>): String? {
        if (nombres.isEmpty()) return null
        // fold conserva la posición de cada letra, así que el corte se aplica igual sobre el texto original.
        val plegado = VoiceText.fold(texto)
        for (nombre in nombres) {
            val patron = nombre.split(' ').filter { it.isNotBlank() }.joinToString("\\s+") { Regex.escape(it) }
            if (patron.isEmpty()) continue
            val inicio = Regex("^\\s*(?:(?:oye|hola)\\s+)?$patron(?![\\p{L}\\p{N}])").find(plegado) ?: continue
            return texto.substring(inicio.range.last + 1).trim { !it.isLetterOrDigit() }
        }
        return null
    }
}
