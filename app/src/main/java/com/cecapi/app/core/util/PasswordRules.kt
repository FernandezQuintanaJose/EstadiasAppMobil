package com.cecapi.app.core.util

import java.text.Normalizer

/**
 * La regla de contraseña de CECAPI: al menos [MIN_LETTERS] letras, solo minúsculas de la a a la z y la ñ.
 * Está pensada para dictarse: una frase larga es fácil de decir y difícil de adivinar, y no depende de
 * símbolos ni números que el reconocimiento de voz escribe de formas distintas.
 *
 * Antes de validar o de calcular el hash, la contraseña se [normalize]: sin espacios, en minúsculas y sin
 * acentos ni diéresis (la ñ se conserva). Así "Mi Perro Café" dicho o escrito llega igual: "miperrocafe".
 * El servidor debe aplicar exactamente la misma normalización antes de su hash.
 */
object PasswordRules {
    const val MIN_LETTERS = 15

    /** Cómo se explica la regla en voz alta y en pantalla. */
    const val DESCRIPTION =
        "La contraseña debe tener al menos quince letras, sin números ni símbolos. Puede ser una frase; " +
            "los espacios y los acentos no cuentan."

    private val ONLY_LETTERS = Regex("^[a-zñ]+$")
    private val DIACRITICS = Regex("\\p{Mn}+")
    private val SPACES = Regex("\\s+")

    // Un carácter de uso privado: nunca aparece en lo que la persona escribe o dicta.
    private const val ENYE_PLACEHOLDER = ''

    /**
     * 1) Minúsculas. 2) La ñ se aparta para que no pierda su tilde. 3) Descomposición NFD y se quitan las
     * marcas (acentos y diéresis). 4) Se regresa la ñ. 5) Se quitan todos los espacios.
     */
    fun normalize(raw: String): String {
        val protectedEnye = raw.lowercase().replace('ñ', ENYE_PLACEHOLDER)
        val withoutMarks = Normalizer.normalize(protectedEnye, Normalizer.Form.NFD).replace(DIACRITICS, "")
        return Normalizer.normalize(withoutMarks, Normalizer.Form.NFC)
            .replace(ENYE_PLACEHOLDER, 'ñ')
            .replace(SPACES, "")
    }

    /** Null cuando la contraseña ya normalizada cumple la regla; si no, el motivo para decírselo a la persona. */
    fun problem(normalized: String): String? = when {
        normalized.isEmpty() -> "Falta la contraseña."
        !ONLY_LETTERS.matches(normalized) -> "La contraseña solo puede tener letras, sin números ni símbolos."
        normalized.length < MIN_LETTERS ->
            "La contraseña tiene ${normalized.length} letras y necesita al menos $MIN_LETTERS. Puedes usar una frase."
        else -> null
    }
}
