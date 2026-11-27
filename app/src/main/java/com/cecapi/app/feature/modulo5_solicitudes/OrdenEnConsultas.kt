package com.cecapi.app.feature.modulo5_solicitudes

import com.cecapi.app.core.voice.VoiceText
import com.cecapi.app.feature.modulo1_aplicacionprincipal.SessionCommands

/**
 * Órdenes para la app que se dicen estando en Consultas. Ahí todo lo oído se toma como una pregunta, así que
 * "cierra la aplicación" o "cerrar, volver" terminaban en "¿La envío?". Solo cuentan en frases cortas que no
 * empiezan como pregunta: "¿cómo cierro la aplicación de mi banco?" sigue siendo una consulta.
 */
object OrdenEnConsultas {
    enum class Orden { CERRAR_APP, CERRAR_SESION, VOLVER, AYUDA }

    private const val MAX_PALABRAS = 7
    private val PREGUNTAS = setOf("como", "que", "porque", "por", "quien", "cuando", "donde", "cual", "cuanto")
    private val SALIR_A_LA_APP = setOf("salir a la app", "salir a la aplicacion", "salida de la app", "cerrar la apps")
    private val PALABRAS_DE_VOLVER = setOf("cerrar", "cierra", "volver", "vuelve", "regresa", "regresar", "atras", "salir")
    private val AYUDA = setOf("ayuda", "necesito ayuda", "comandos", "lista de comandos", "que puedo decir")

    private val SI_CERRAR = setOf(
        "si", "si cerrar", "si cierra", "si cierrala", "si por favor", "cerrar", "cierra", "confirmo", "hazlo", "correcto",
    )

    fun reconocer(oido: String): Orden? {
        val texto = VoiceText.normalize(oido)
        if (texto in AYUDA) return Orden.AYUDA
        val palabras = texto.split(' ').filter { it.isNotEmpty() }
        if (palabras.isEmpty() || palabras.size > MAX_PALABRAS || palabras.first() in PREGUNTAS) return null
        return when {
            SessionCommands.isExitApp(oido) || texto in SALIR_A_LA_APP -> Orden.CERRAR_APP
            SessionCommands.isLogout(oido) -> Orden.CERRAR_SESION
            palabras.size <= 3 && palabras.all { it in PALABRAS_DE_VOLVER } -> Orden.VOLVER
            else -> null
        }
    }

    /** Solo la frase completa confirma el cierre: un "sí" dentro de una conversación no cuenta. */
    fun confirmaCierre(oido: String): Boolean = VoiceText.normalize(oido) in SI_CERRAR
}
