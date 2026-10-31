package com.cecapi.app.feature.modulo1_aplicacionprincipal

/**
 * Revisa un correo sin salir del teléfono: forma válida y errores comunes de escritura. Que el buzón exista
 * solo lo confirma el servidor al mandar el código; esto evita gastar envíos en correos mal escritos.
 */
object EmailRules {
    private val FORMA = Regex("""^[A-Za-z0-9._%+\-]+@([A-Za-z0-9\-]+\.)+[A-Za-z]{2,}$""")

    private val CORRECCIONES = mapOf(
        "gmial.com" to "gmail.com", "gmai.com" to "gmail.com", "gmal.com" to "gmail.com", "gnail.com" to "gmail.com",
        "gamil.com" to "gmail.com", "gmail.con" to "gmail.com", "gmail.co" to "gmail.com", "gmail.cm" to "gmail.com",
        "hotmial.com" to "hotmail.com", "hotmal.com" to "hotmail.com", "hotmail.con" to "hotmail.com",
        "outlok.com" to "outlook.com", "outlook.con" to "outlook.com", "yahoo.con" to "yahoo.com", "yaho.com" to "yahoo.com",
        "icloud.con" to "icloud.com",
    )

    /** Qué está mal con el correo, o null si se ve bien. Dicho en palabras que se puedan oír. */
    fun problem(correo: String): String? {
        val texto = correo.trim()
        if (!FORMA.matches(texto) || ".." in texto || texto.substringBefore('@').let { it.startsWith(".") || it.endsWith(".") }) {
            return "El correo no parece válido. Revisa que tenga arroba y un punto después."
        }
        val dominio = texto.substringAfter('@').lowercase()
        CORRECCIONES[dominio]?.let { return "¿Quisiste decir $it? Revisa cómo escribiste el correo." }
        return null
    }
}
