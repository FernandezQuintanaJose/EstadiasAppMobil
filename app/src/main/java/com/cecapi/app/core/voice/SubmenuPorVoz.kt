package com.cecapi.app.core.voice

/**
 * Abre por voz un submenú de la pantalla ("privacidad", "abre el panel de privacidad"). La frase tiene que ser
 * solo el nombre del submenú, con o sin "abre..." y "por favor": así no se confunde con órdenes que lo
 * mencionan ("activa los avisos") ni con una pregunta.
 */
object SubmenuPorVoz {
    private val INICIOS = listOf(
        "oye", "quiero", "puedes", "abre", "abrir", "abreme", "entra", "entrar", "ir", "ve", "vamos", "llevame",
        "muestra", "muestrame", "mostrar", "ver", "dame", "a", "en", "al", "el", "la", "los", "las", "de", "del",
        "panel", "seccion", "menu", "apartado", "opcion", "opciones",
    )
    private val FINES = listOf("por favor", "porfa", "ahora")

    /** La clave del submenú pedido, o null. [opciones] va de cada clave a los nombres con los que se puede pedir. */
    fun buscar(texto: String, opciones: Map<String, List<String>>): String? {
        var t = VoiceText.normalize(texto)
        FINES.forEach { fin -> t = t.removeSuffix(" $fin") }
        // Se quita el relleno del inicio palabra por palabra y se compara en cada paso: "mi voz" no debe perder "mi".
        while (true) {
            opciones.entries.firstOrNull { t in it.value }?.let { return it.key }
            val inicio = t.substringBefore(' ', "")
            if (inicio !in INICIOS) return null
            t = t.substringAfter(' ')
        }
    }
}
