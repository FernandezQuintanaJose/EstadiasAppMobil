package com.cecapi.app.feature.modulo1_aplicacionprincipal

/**
 * Reglas de los datos de una cuenta nueva. Son las mismas que debe aplicar el servidor cuando las cuentas pasen
 * a Neon (ver docs/cuentas-servicio.md): el teléfono las revisa para avisar pronto, pero la última palabra es del servidor.
 */
object AccountRules {
    const val USUARIO_MIN = 3
    const val USUARIO_MAX = 30
    const val NOMBRE_MIN = 2
    const val NOMBRE_MAX = 80

    private val USUARIO_VALIDO = Regex("""^[A-Za-z0-9ÁÉÍÓÚÜÑáéíóúüñ._-]+$""")

    /** Qué está mal con el nombre de usuario, dicho para oírse, o null si sirve. */
    fun usuarioProblem(usuario: String): String? = when {
        usuario.length < USUARIO_MIN -> "El usuario necesita al menos $USUARIO_MIN letras o números."
        usuario.length > USUARIO_MAX -> "El usuario es muy largo; usa $USUARIO_MAX caracteres o menos."
        !USUARIO_VALIDO.matches(usuario) -> "El usuario solo puede llevar letras, números, punto, guion o guion bajo, sin espacios."
        else -> null
    }

    fun nombreProblem(nombre: String): String? = when {
        nombre.trim().length < NOMBRE_MIN -> "Escribe tu nombre completo."
        nombre.trim().length > NOMBRE_MAX -> "El nombre es muy largo; usa $NOMBRE_MAX caracteres o menos."
        else -> null
    }
}
