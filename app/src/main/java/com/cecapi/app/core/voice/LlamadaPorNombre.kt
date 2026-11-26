package com.cecapi.app.core.voice

/**
 * El reconocedor no conoce nombres como "ALIA": en el teléfono de pruebas dio "palia" como primera opción y
 * "alia" como segunda con confianza 0.15, y la app lo descartaba. Si lo oído es muy corto y alguna de las
 * opciones es exactamente el nombre, se toma como una llamada. En frases largas (televisión, conversación)
 * no se aplica: ahí sigue haciendo falta la confianza normal.
 */
object LlamadaPorNombre {
    private const val MAX_PALABRAS = 2
    private const val MAX_PALABRAS_ORDEN = 6

    /**
     * Lo que el reconocedor de Google escribe cuando alguien dice solo "ALIA". Se toman como llamada solo si
     * es lo único que se dijo; si no, "palia" terminaba buscándose en Wikipedia.
     */
    private val CASI_ALIA = setOf("palia", "alias", "alian")

    fun esElNombre(opciones: List<String>, nombres: Collection<String>): Boolean {
        val primera = opciones.firstOrNull() ?: return false
        val oido = VoiceText.normalize(primera)
        if (oido.split(' ').size > MAX_PALABRAS) return false
        if ("alia" in nombres && oido in CASI_ALIA) return true
        return opciones.any { VoiceText.normalize(it) in nombres }
    }

    /**
     * "palia abre cámara" con "alia abre cámara" como segunda opción: devuelve la que sí empieza con el nombre
     * y trae una orden, o null si la primera ya empieza con él, si la frase es larga o si ninguna lo trae.
     */
    fun ordenConNombre(opciones: List<String>, nombres: Collection<String>): String? {
        val primera = opciones.firstOrNull() ?: return null
        if (VoiceText.normalize(primera).split(' ').size > MAX_PALABRAS_ORDEN) return null
        if (FrasesDeControl.quitarNombre(primera, nombres) != null) return null
        return opciones.drop(1).firstOrNull { !FrasesDeControl.quitarNombre(it, nombres).isNullOrBlank() }
    }
}
