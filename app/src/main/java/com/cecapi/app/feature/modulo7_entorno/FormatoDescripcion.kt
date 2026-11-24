package com.cecapi.app.feature.modulo7_entorno

/**
 * Deja la respuesta de la IA local lista para decirse en voz alta.
 *
 * La persona pidió una sola frase natural, con posiciones "a la izquierda", "al frente" o
 * "a la derecha", y sin las palabras "principal" ni "secundario". El modelo pequeño a veces
 * copia el formato antiguo; aquí se corrige antes de mostrarlo o hablar.
 */
object FormatoDescripcion {

    private val PREFIJOS_DE_IMAGEN = Regex(
        "^(en la (imagen|foto)|la (imagen|foto) (muestra|contiene))\\s*[:,]?\\s*",
        RegexOption.IGNORE_CASE,
    )

    private val PREFIJOS_QUE_SE_CAMBIAN = listOf(
        Regex("^(puedo ver|se ve|se observa)\\s+", RegexOption.IGNORE_CASE) to "Veo ",
        Regex("^hay\\s+", RegexOption.IGNORE_CASE) to "Veo ",
    )

    /**
     * El traductor de ML Kit acierta la idea pero a veces elige una palabra literal. Estas
     * correcciones son las que aparecieron en las pruebas con fotos reales.
     */
    private val CORRECCIONES = listOf(
        Regex("\\bde la bolsa de perforaci[oó]n\\b", RegexOption.IGNORE_CASE) to
            "del saco de boxeo",
        Regex("\\bde la bolsa de punzonado\\b", RegexOption.IGNORE_CASE) to "del saco de boxeo",
        Regex("\\bde la bolsa de boxeo\\b", RegexOption.IGNORE_CASE) to "del saco de boxeo",
        Regex("\\bde la bolsa de box\\b", RegexOption.IGNORE_CASE) to "del saco de boxeo",
        Regex("\\bla bolsa de perforaci[oó]n\\b", RegexOption.IGNORE_CASE) to "el saco de boxeo",
        Regex("\\bla bolsa de punzonado\\b", RegexOption.IGNORE_CASE) to "el saco de boxeo",
        Regex("\\buna bolsa de perforaci[oó]n\\b", RegexOption.IGNORE_CASE) to "un saco de boxeo",
        Regex("\\buna bolsa de punzonado\\b", RegexOption.IGNORE_CASE) to "un saco de boxeo",
        Regex("\\bla bolsa de boxeo\\b", RegexOption.IGNORE_CASE) to "el saco de boxeo",
        Regex("\\buna bolsa de boxeo\\b", RegexOption.IGNORE_CASE) to "un saco de boxeo",
        Regex("\\bel saco de box\\b", RegexOption.IGNORE_CASE) to "el saco de boxeo",
        Regex("\\bun saco de box\\b", RegexOption.IGNORE_CASE) to "un saco de boxeo",
        Regex("\\bbolsa de perforaci[oó]n\\b", RegexOption.IGNORE_CASE) to "saco de boxeo",
        Regex("\\bbolsa de punzonado\\b", RegexOption.IGNORE_CASE) to "saco de boxeo",
        Regex("\\bbolsa de boxeo\\b", RegexOption.IGNORE_CASE) to "saco de boxeo",
        Regex("\\bsaco de box\\b", RegexOption.IGNORE_CASE) to "saco de boxeo",
        Regex("\\bsaco de boxeo está colgada\\b", RegexOption.IGNORE_CASE) to
            "saco de boxeo está colgado",
        // El modelo a veces lee la marca o el texto de la imagen; eso no ayuda a la persona.
        // Solo se quita cuando hay una cita; un "cartel con letras grandes" es una descripción válida.
        Regex(
            "\\s+con (escritura|letras|texto)[^.]*?que dice\\s+[\"“'].*?[\"”']",
            RegexOption.IGNORE_CASE,
        ) to "",
        Regex("\\s+que dice\\s+[\"“'].*?[\"”']", RegexOption.IGNORE_CASE) to "",
        Regex("\\s+tiene el texto\\s+[\"“'].*?[\"”']", RegexOption.IGNORE_CASE) to "",
        Regex("\\s+escrito en letras [^.]*", RegexOption.IGNORE_CASE) to "",
        Regex("\\bsaco de boxeo es negra\\b", RegexOption.IGNORE_CASE) to
            "saco de boxeo es negro",
        Regex("\\bplacas de peso\\b", RegexOption.IGNORE_CASE) to "pesas",
        Regex("\\bcama con hojas\\b", RegexOption.IGNORE_CASE) to "cama con sábanas",
        Regex("\\bhojas y almohadas\\b", RegexOption.IGNORE_CASE) to "sábanas y almohadas",
        Regex("\\bde alquiler de luz natural\\b", RegexOption.IGNORE_CASE) to
            "que dejan entrar luz natural",
        Regex("\\balquiler de luz\\b", RegexOption.IGNORE_CASE) to "entrada de luz",
        Regex("\\bpantalla negra en blanco\\b", RegexOption.IGNORE_CASE) to "pantalla negra",
        Regex("\\b(dos puertas[^.]*?)\\buno\\b", RegexOption.IGNORE_CASE) to "\$1una",
        Regex("\\b(dos puertas[^.]*?)\\botro\\b", RegexOption.IGNORE_CASE) to "\$1otra",
        Regex("\\bpasillo o un pasillo\\b", RegexOption.IGNORE_CASE) to "pasillo",
    )

    private val QUITAR_PRINCIPAL = listOf(
        Regex(
            "\\bcomo\\s+(el\\s+|los\\s+)?(objeto|objetos|elemento|elementos)\\s+" +
                "(principal|principales|secundario|secundarios)\\b",
            RegexOption.IGNORE_CASE,
        ),
        Regex(
            "\\b(el\\s+|los\\s+)?(objeto|objetos|elemento|elementos)\\s+" +
                "(principal|principales|secundario|secundarios)\\b",
            RegexOption.IGNORE_CASE,
        ),
        Regex(
            "\\b(principal|principales|principalmente|secundario|secundarios)\\b",
            RegexOption.IGNORE_CASE,
        ),
    )

    private val POSICIONES = listOf(
        Regex("\\ben el centro de la (imagen|foto)\\b", RegexOption.IGNORE_CASE) to "al frente",
        Regex("\\bal centro de la (imagen|foto)\\b", RegexOption.IGNORE_CASE) to "al frente",
        Regex("\\ben el centro\\b", RegexOption.IGNORE_CASE) to "al frente",
        Regex("\\bal centro\\b", RegexOption.IGNORE_CASE) to "al frente",
        Regex("\\ben el medio\\b", RegexOption.IGNORE_CASE) to "al frente",
        Regex("\\ben el frente\\b", RegexOption.IGNORE_CASE) to "al frente",
        Regex("\\ben frente\\b", RegexOption.IGNORE_CASE) to "al frente",
        Regex("\\benfrente\\b", RegexOption.IGNORE_CASE) to "al frente",
        Regex("\\ben el lado izquierdo\\b", RegexOption.IGNORE_CASE) to "a la izquierda",
        Regex("\\ben el lado derecho\\b", RegexOption.IGNORE_CASE) to "a la derecha",
        Regex("\\bal lado izquierdo\\b", RegexOption.IGNORE_CASE) to "a la izquierda",
        Regex("\\bal lado derecho\\b", RegexOption.IGNORE_CASE) to "a la derecha",
    )

    /** Conjunciones con las que el modelo a veces termina una frase cortada. */
    private val CONJUNCIONES = setOf("y", "e", "o", "u")

    /** Palabras que dejan la frase abierta si el modelo se queda sin tokens. */
    private val PALABRAS_INCOMPLETAS = setOf(
        "de", "del", "en", "con", "sin", "para", "por", "que", "el", "la", "los", "las",
        "un", "una", "unos", "unas", "al", "a", "su", "sus", "se", "como", "más", "muy",
        "donde", "cuando", "mientras", "sobre", "entre", "desde", "hasta", "no",
        "es", "son", "está", "están", "hay", "tiene", "tienen", "puede", "pueden",
        "parece", "parecen", "ser", "estar", "haber",
    )

    /**
     * Si el modelo se queda sin tokens, la última frase puede terminar a medias. Se conserva
     * lo que sí quedó completo: quita una conjunción final o descarta la última frase abierta.
     * Devuelve null si no queda ninguna frase completa, para que responda el detector.
     */
    private fun completarTexto(texto: String): String? {
        val limpio = texto.trim().trimEnd(',', ';', ':', ' ')
        if (limpio.isEmpty()) return null
        if (limpio.endsWith(".") || limpio.endsWith("!") || limpio.endsWith("?")) return limpio

        val oraciones = limpio.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        val ultima = oraciones.lastOrNull() ?: return null
        val palabras = ultima.trim().split(Regex("\\s+"))
        val ultimaPalabra = palabras.lastOrNull()
            ?.trim(',', ';', ':', '.', '!', '?')
            ?.lowercase()
            .orEmpty()

        if (ultimaPalabra in CONJUNCIONES) {
            val sinUltima = palabras.dropLast(1).joinToString(" ").trimEnd(',', ';', ':', ' ')
            return if (sinUltima.isBlank()) null else "$sinUltima."
        }
        if (ultimaPalabra in PALABRAS_INCOMPLETAS) {
            if (oraciones.size <= 1) return null
            val anteriores = oraciones.dropLast(1).joinToString(" ").trim()
            return if (anteriores.isBlank()) null else anteriores
        }
        return "$limpio."
    }

    /**
     * Versión para capturas de pantalla. No quita "principal" (un "menú principal" es válido),
     * no borra textos entre comillas y no cambia posiciones: solo ordena espacios, puntuación y
     * descarta una última frase que haya quedado a medias.
     */
    fun aplicarPantalla(textoOriginal: String): String? {
        var texto = textoOriginal.trim().replace(Regex("\\s+"), " ")
        if (texto.isEmpty()) return null

        texto = texto
            .replace(Regex("\\s+([,.;:])"), "$1")
            .replace(Regex("([,.;:])\\s*\\1"), "$1")
            .replace(Regex("^[,.;:\\s]+"), "")
            .replace(Regex("\\s+$"), "")

        if (texto.isEmpty()) return null
        val completo = completarTexto(texto) ?: return null
        return completo.replaceFirstChar { it.uppercase() }
    }

    fun aplicar(textoOriginal: String): String? {
        var texto = textoOriginal.trim().replace(Regex("\\s+"), " ")
        if (texto.isEmpty()) return null

        texto = PREFIJOS_DE_IMAGEN.replace(texto, "")
        for ((patron, reemplazo) in PREFIJOS_QUE_SE_CAMBIAN) {
            texto = patron.replace(texto, reemplazo)
        }
        for ((patron, reemplazo) in CORRECCIONES) {
            texto = patron.replace(texto, reemplazo)
        }
        for (patron in QUITAR_PRINCIPAL) {
            texto = patron.replace(texto, " ")
        }
        for ((patron, reemplazo) in POSICIONES) {
            texto = patron.replace(texto, reemplazo)
        }

        texto = texto
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\s+([,.;:])"), "$1")
            .replace(Regex("([,.;:])\\s*\\1"), "$1")
            .replace(Regex(",\\s*[,;:]"), ",")
            .replace(Regex("^[,.;:\\s]+"), "")
            .replace(Regex("\\s+$"), "")

        if (texto.isEmpty()) return null
        val completo = completarTexto(texto) ?: return null
        return completo.replaceFirstChar { it.uppercase() }
    }
}
