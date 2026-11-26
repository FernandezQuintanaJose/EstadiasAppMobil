package com.cecapi.app.feature.modulo5_solicitudes

import com.cecapi.app.core.voice.VoiceText

/**
 * Decide si una consulta es una pregunta sencilla de enciclopedia ("qué es un círculo", "quién fue Miguel Hidalgo",
 * "dónde queda Querétaro"), que se busca primero en Wikipedia para no gastar consultas del servidor (Groq).
 * Lo más complejo ("cómo funciona", "por qué", "explícame", documentos) va directo al servidor.
 */
object FuenteDeConsulta {
    private val SENCILLA = Regex(
        "^\\s*(?:(?:oye|dime|sabes|me puedes decir|me dices)\\s+)?" +
            "(?:que\\s+es|que\\s+son|que\\s+significa|quien\\s+es|quien\\s+fue|quien\\s+era|quienes\\s+son|" +
            "quienes\\s+fueron|donde\\s+queda|donde\\s+esta|donde\\s+se\\s+encuentra|informacion\\s+sobre|" +
            "busca\\s+en\\s+wikipedia|busca)\\s+(\\S.*)$",
    )

    /** Palabras que piden una explicación o una opinión, no una definición: eso lo responde mejor la IA. */
    private val COMPLEJA = listOf(
        "por que", "como", "explica", "explicame", "diferencia", "compara", "opinas", "recomienda", "recomiendas",
        "ventajas", "desventajas", "pasos", "ejemplos", "para que sirve", "para que sirven",
    )

    const val MAX_PALABRAS_TEMA = 6

    /** El tema que se busca en Wikipedia (con los acentos originales), o null si debe ir al servidor. */
    fun temaParaWikipedia(consulta: String, esDocumento: Boolean): String? {
        if (esDocumento) return null
        val normal = VoiceText.normalize(consulta)
        if (COMPLEJA.any { contiene(normal, it) }) return null
        // fold conserva la posición de cada letra, así que el tema se corta del texto original con sus acentos.
        val grupo = SENCILLA.find(VoiceText.fold(consulta))?.groups?.get(1) ?: return null
        val tema = consulta.substring(grupo.range.first).trim { !it.isLetterOrDigit() }
        val palabras = VoiceText.normalize(tema).split(' ').filter { it.isNotBlank() }
        if (tema.length < 2 || palabras.isEmpty() || palabras.size > MAX_PALABRAS_TEMA) return null
        return tema
    }

    private fun contiene(normal: String, frase: String): Boolean =
        Regex("(?<![\\p{L}\\p{N}])${Regex.escape(frase)}(?![\\p{L}\\p{N}])").containsMatchIn(normal)
}
