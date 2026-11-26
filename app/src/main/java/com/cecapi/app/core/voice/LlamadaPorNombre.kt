package com.cecapi.app.core.voice

/**
 * El reconocedor no conoce nombres como "ALIA": en el teléfono de pruebas dio "palia" como primera opción y
 * "alia" como segunda con confianza 0.15, y la app lo descartaba. Si lo oído es muy corto y alguna de las
 * opciones es exactamente el nombre, se toma como una llamada. En frases largas (televisión, conversación)
 * no se aplica: ahí sigue haciendo falta la confianza normal.
 */
object LlamadaPorNombre {
    private const val MAX_PALABRAS = 2

    fun esElNombre(opciones: List<String>, nombres: Collection<String>): Boolean {
        val primera = opciones.firstOrNull() ?: return false
        if (VoiceText.normalize(primera).split(' ').size > MAX_PALABRAS) return false
        return opciones.any { VoiceText.normalize(it) in nombres }
    }
}
