package com.cecapi.app.core.voice

/**
 * Decide qué hacer con una frase que oyó el detector local ([DetectorDeNombre]). Cada frase es lo dicho entre
 * dos pausas, y el detector solo distingue el nombre, las órdenes de control y los señuelos; lo demás es "[unk]".
 */
object FraseDeActivacion {
    enum class Accion { LLAMADA, SILENCIO, DETENER, NADA }

    /** Con más palabras ya es una conversación en la que se menciona el nombre, no una llamada. */
    const val MAX_PALABRAS = 6

    private val SILENCIO = setOf("silencio", "cállate")
    private val DETENER = setOf("detente", "basta")

    fun decidir(palabras: List<String>, nombre: String): Accion {
        if (palabras.isEmpty() || palabras.size > MAX_PALABRAS) return Accion.NADA
        // El nombre cuenta solo al inicio, o sea después de una pausa: "dijo que alia no sirve" no llama.
        val partes = nombre.split(' ')
        val llama = palabras.take(partes.size) == partes
        val resto = if (llama) palabras.drop(partes.size) else palabras
        // Una orden de control vale sola o justo después del nombre; en medio de otras palabras, no.
        if (resto.size == 1) {
            if (resto[0] in SILENCIO) return Accion.SILENCIO
            if (resto[0] in DETENER) return Accion.DETENER
        }
        return if (llama) Accion.LLAMADA else Accion.NADA
    }

    /** Las órdenes que el detector tiene que conocer, tal como aparecen en el vocabulario del modelo. */
    val ORDENES: Set<String> get() = SILENCIO + DETENER
}
