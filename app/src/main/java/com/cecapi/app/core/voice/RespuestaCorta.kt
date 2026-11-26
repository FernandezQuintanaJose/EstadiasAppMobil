package com.cecapi.app.core.voice

/**
 * Modo simple: quita de lo que dice la app las frases de ayuda ("Di lista de comandos…", "Toca el micrófono…"),
 * y deja solo la respuesta. Se quitan frases concretas escritas por la app; lo que es contenido (documentos,
 * texto de una foto, políticas) se manda a decir tal cual y no pasa por aquí.
 */
object RespuestaCorta {
    private val FIN_DE_FRASE = Regex("(?<=[.!?])\\s+")

    private val AYUDAS = listOf(
        "Di lista de comandos", "Di volver", "Di sí o no", "Di toma la foto", "Di repite", "Di tomar otra foto",
        "Di continúa", "Di leer texto", "Toca el micrófono", "También puedes", "Si quieres", "Puedes volver a pedirlo",
        "Puedes probar otra acción", "Puedes decir lista de comandos",
    )

    fun acortar(texto: String): String {
        // Una lista de comandos se pide justamente para oírla completa.
        if (texto.contains("puedes decir:", ignoreCase = true)) return texto
        val frases = texto.trim().split(FIN_DE_FRASE)
        val utiles = frases.filterNot { frase -> AYUDAS.any { frase.startsWith(it, ignoreCase = true) } }
        // Si todo era ayuda, es lo único que hay que decir.
        return if (utiles.isEmpty()) texto else utiles.joinToString(" ")
    }
}
