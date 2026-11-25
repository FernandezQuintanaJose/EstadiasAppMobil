package com.cecapi.app.feature.modulo5_solicitudes

import com.cecapi.app.core.voice.VoiceText

/**
 * Lo que se oye en Consultas no se envía al servidor sin permiso: el micrófono queda abierto después de cada
 * respuesta y puede oír una conversación o una televisión. Primero se repite lo oído y solo un "sí" claro lo envía;
 * cualquier otra frase lo descarta, sin volver a preguntar.
 */
class ConfirmacionDeConsulta {
    sealed interface Resultado {
        data class Preguntar(val consulta: String) : Resultado
        data class Enviar(val consulta: String) : Resultado
        data object Descartar : Resultado
    }

    private var pendiente: String? = null

    fun reiniciar() {
        pendiente = null
    }

    fun escuchar(oido: String): Resultado {
        val anterior = pendiente
        if (anterior == null) {
            pendiente = oido.trim()
            return Resultado.Preguntar(oido.trim())
        }
        pendiente = null
        // Solo frases completas: una conversación que contenga la palabra "si" no debe contar como permiso.
        return if (VoiceText.normalize(oido) in PERMISOS) Resultado.Enviar(anterior) else Resultado.Descartar
    }

    private companion object {
        val PERMISOS = setOf("si", "si enviala", "si envia", "enviala", "envia", "si por favor", "si mandala", "mandala")
    }
}
