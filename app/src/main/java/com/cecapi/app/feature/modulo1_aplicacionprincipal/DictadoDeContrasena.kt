package com.cecapi.app.feature.modulo1_aplicacionprincipal

import com.cecapi.app.core.util.PasswordRules

/**
 * Contraseña dictada por voz: hay que decir la misma dos veces. Así un ruido, una televisión u otra persona no pueden
 * fijar la contraseña de la cuenta con una sola frase. Quien llama todavía debe pedir un "sí" antes de crear la cuenta.
 */
class DictadoDeContrasena {
    sealed interface Resultado {
        /** No cumple la regla; [motivo] se dice en voz alta. */
        data class Invalida(val motivo: String) : Resultado

        /** La primera vez es válida: hay que decirla otra vez. */
        data object Repetir : Resultado

        /** La segunda no fue igual: se empieza de nuevo. */
        data object NoCoincide : Resultado

        data class Lista(val contrasena: String) : Resultado
    }

    private var primera: String? = null

    fun reiniciar() {
        primera = null
    }

    fun escuchar(texto: String): Resultado {
        val dictada = PasswordRules.normalize(texto)
        val anterior = primera
        if (anterior != null) {
            primera = null
            return if (dictada == anterior) Resultado.Lista(dictada) else Resultado.NoCoincide
        }
        val problema = PasswordRules.problem(dictada)
        if (problema != null) return Resultado.Invalida(problema)
        primera = dictada
        return Resultado.Repetir
    }
}
