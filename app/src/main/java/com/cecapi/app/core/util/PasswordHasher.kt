package com.cecapi.app.core.util

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Contraseñas con Argon2id y sal propia por cuenta (el mismo algoritmo que usará el servidor). El resultado es una
 * sola cadena `$argon2id$v=19$m=...,t=...,p=...$sal$hash` que ya trae sus parámetros, así que se pueden subir más
 * adelante sin romper las cuentas guardadas.
 *
 * Las cuentas anteriores guardaron SHA-256 sin sal. Esas se siguen reconociendo para que puedan entrar, y
 * [necesitaActualizar] avisa para volver a guardarlas con Argon2id en cuanto la persona entra.
 */
object PasswordHasher {
    // Los parámetros de un teléfono: 32 MiB y 3 pasadas tardan unos cientos de milisegundos y no ahogan la memoria.
    private const val MEMORIA_KIB = 32 * 1024
    private const val ITERACIONES = 3
    private const val PARALELISMO = 1
    private const val LARGO_HASH = 32
    private const val LARGO_SAL = 16
    private const val PREFIJO = "\$argon2id\$"

    private val argon2 by lazy { Argon2Kt() }
    private val azar = SecureRandom()

    /** Guarda la contraseña: Argon2id con una sal nueva. Dos llamadas con la misma contraseña dan cadenas distintas. */
    fun hash(rawPassword: String): String {
        val sal = ByteArray(LARGO_SAL).also { azar.nextBytes(it) }
        return argon2.hash(
            mode = Argon2Mode.ARGON2_ID,
            password = rawPassword.toByteArray(Charsets.UTF_8),
            salt = sal,
            tCostInIterations = ITERACIONES,
            mCostInKibibyte = MEMORIA_KIB,
            parallelism = PARALELISMO,
            hashLengthInBytes = LARGO_HASH,
        ).encodedOutputAsString()
    }

    /** ¿[rawPassword] es la contraseña guardada en [stored]? Sirve para Argon2id y para el SHA-256 de cuentas viejas. */
    fun verify(stored: String, rawPassword: String): Boolean =
        if (esArgon2(stored)) {
            argon2.verify(Argon2Mode.ARGON2_ID, stored, rawPassword.toByteArray(Charsets.UTF_8))
        } else {
            constantTimeEquals(stored, sha256Antiguo(rawPassword))
        }

    /** True cuando lo guardado es el SHA-256 de antes y conviene volver a guardarlo con Argon2id. */
    fun necesitaActualizar(stored: String): Boolean = !esArgon2(stored)

    internal fun esArgon2(stored: String): Boolean = stored.startsWith(PREFIJO)

    internal fun sha256Antiguo(rawPassword: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(rawPassword.toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { "%02x".format(it) }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
}
