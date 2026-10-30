package com.cecapi.app.feature.modulo4_lectordocumentos

/** Un bloque de texto de ML Kit con su posición en la foto, en píxeles. */
internal data class BloqueTexto(
    val texto: String,
    val izquierda: Int,
    val arriba: Int,
    val derecha: Int,
    val abajo: Int,
) {
    val ancho: Int get() = derecha - izquierda
    val lineas: Int get() = texto.lines().count { it.isNotBlank() }
}

/**
 * Convierte los bloques de ML Kit en párrafos listos para leer en voz alta:
 * los pone en orden de lectura y limpia lo que la voz leería mal.
 */
internal fun prepararParrafos(bloques: List<BloqueTexto>): List<String> =
    ordenarBloques(bloques)
        .flatMap { limpiarTexto(it.texto) }
        .filter { parrafo -> parrafo.count { it.isLetterOrDigit() } >= MIN_ALFANUMERICOS }

/**
 * Ordena los bloques como los leería una persona. Los bloques que ocupan casi todo el ancho
 * (títulos, párrafos de una sola columna) dividen la página en franjas, y cada franja se lee
 * por columnas completas si es texto a varias columnas, o renglón por renglón si es un formulario o recibo.
 */
internal fun ordenarBloques(bloques: List<BloqueTexto>): List<BloqueTexto> {
    if (bloques.size < 2) return bloques
    val anchoTexto = bloques.maxOf { it.derecha } - bloques.minOf { it.izquierda }

    val resultado = mutableListOf<BloqueTexto>()
    val franja = mutableListOf<BloqueTexto>()
    for (bloque in bloques.sortedBy { it.arriba }) {
        if (bloque.ancho >= anchoTexto * FRACCION_ANCHO_COMPLETO) {
            resultado += ordenarFranja(franja)
            franja.clear()
            resultado += bloque
        } else {
            franja += bloque
        }
    }
    resultado += ordenarFranja(franja)
    return resultado
}

private fun ordenarFranja(bloques: List<BloqueTexto>): List<BloqueTexto> {
    if (bloques.size < 2) return bloques

    val columnas = mutableListOf<MutableList<BloqueTexto>>()
    for (bloque in bloques.sortedBy { it.izquierda }) {
        val columna = columnas.firstOrNull { col -> col.any { seSolapanEnHorizontal(it, bloque) } }
        if (columna != null) columna += bloque else columnas += mutableListOf(bloque)
    }

    // Solo es texto a columnas si al menos dos columnas tienen párrafos de varias líneas.
    // Si no, son etiquetas y valores sueltos ("Nombre:" ... "Juan"), que se leen por renglón.
    val columnasDeTexto = columnas.count { col -> col.any { it.lineas >= LINEAS_MIN_COLUMNA } }
    return if (columnasDeTexto >= 2) {
        columnas.flatMap { col -> col.sortedBy { it.arriba } }
    } else {
        ordenarPorRenglones(bloques)
    }
}

/** Agrupa los bloques que están a la misma altura y los une de izquierda a derecha en un solo bloque. */
private fun ordenarPorRenglones(bloques: List<BloqueTexto>): List<BloqueTexto> {
    val renglones = mutableListOf<MutableList<BloqueTexto>>()
    for (bloque in bloques.sortedBy { it.arriba }) {
        val centro = (bloque.arriba + bloque.abajo) / 2
        val renglon = renglones.lastOrNull()
        if (renglon != null && renglon.all { it.lineas == 1 } && bloque.lineas == 1 &&
            renglon.any { centro in it.arriba..it.abajo }
        ) {
            renglon += bloque
        } else {
            renglones += mutableListOf(bloque)
        }
    }
    return renglones.map { renglon ->
        if (renglon.size == 1) {
            renglon.first()
        } else {
            val enOrden = renglon.sortedBy { it.izquierda }
            BloqueTexto(
                texto = enOrden.joinToString(" ") { it.texto.trim() },
                izquierda = enOrden.minOf { it.izquierda },
                arriba = enOrden.minOf { it.arriba },
                derecha = enOrden.maxOf { it.derecha },
                abajo = enOrden.maxOf { it.abajo },
            )
        }
    }
}

/** Dos bloques están en la misma columna si se enciman en horizontal más de la mitad del más angosto. */
private fun seSolapanEnHorizontal(a: BloqueTexto, b: BloqueTexto): Boolean {
    val solape = minOf(a.derecha, b.derecha) - maxOf(a.izquierda, b.izquierda)
    return solape > minOf(a.ancho, b.ancho) * FRACCION_SOLAPE
}

/**
 * Limpia el texto de un bloque para la voz: une las líneas en frases continuas, pega las palabras
 * cortadas con guion, y quita líneas de puntos, rayas para escribir y espacios sobrantes.
 * Cada viñeta inicia su propio párrafo para que una lista no se lea como una sola frase.
 */
internal fun limpiarTexto(texto: String): List<String> {
    val grupos = mutableListOf<MutableList<String>>()
    for (linea in texto.lines().map { it.trim() }.filter { it.isNotEmpty() }) {
        val sinVineta = linea.replace(VINETA, "")
        if (grupos.isEmpty() || sinVineta != linea) grupos += mutableListOf(sinVineta) else grupos.last() += sinVineta
    }
    return grupos.map { unirLineas(it) }.filter { it.isNotBlank() }
}

private fun unirLineas(lineas: List<String>): String {
    val texto = StringBuilder()
    for (linea in lineas) {
        val terminaEnGuion = texto.length >= 2 && texto.last() in GUIONES && texto[texto.length - 2].isLetter()
        when {
            // "docu-/mento" es una palabra cortada; "Ciudad-/Estado" es una palabra compuesta y conserva el guion.
            terminaEnGuion && linea.first().isLowerCase() -> texto.setLength(texto.length - 1)
            terminaEnGuion -> Unit
            texto.isNotEmpty() -> texto.append(' ')
        }
        texto.append(linea)
    }
    return texto.toString()
        .replace(PUNTOS_GUIA, " ")
        .replace(BARRAS_SUELTAS, " ")
        .replace(ESPACIO_ANTES_DE_CIERRE, "$1")
        .replace(ESPACIO_DESPUES_DE_APERTURA, "$1")
        .replace(ESPACIOS, " ")
        .trim()
}

/** Un bloque debe ocupar al menos esta fracción del ancho del texto para contar como de ancho completo. */
private const val FRACCION_ANCHO_COMPLETO = 0.6f

/** Fracción del bloque más angosto que deben encimarse dos bloques para estar en la misma columna. */
private const val FRACCION_SOLAPE = 0.5f

/** Líneas que necesita un bloque para considerarse un párrafo de columna y no una etiqueta suelta. */
private const val LINEAS_MIN_COLUMNA = 3

/** Párrafos con menos letras o números que esto suelen ser ruido (sombras, bordes, manchas). */
private const val MIN_ALFANUMERICOS = 2

private val GUIONES = setOf('-', '‐', '¬', '­')
private val VINETA = Regex("""^[•·▪●■◦‣*\-–]\s+""")
private val PUNTOS_GUIA = Regex("""(?:[._·…]\s?){4,}""")
private val BARRAS_SUELTAS = Regex("""(?<=^|\s)[|¦]+(?=\s|$)""")
private val ESPACIO_ANTES_DE_CIERRE = Regex("""\s+([,.;:!?)\]])""")
private val ESPACIO_DESPUES_DE_APERTURA = Regex("""([¿¡(\[])\s+""")
private val ESPACIOS = Regex("""\s{2,}""")
