package com.cecapi.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Solo lo que no necesita la librería nativa de Argon2 (esa se prueba en el teléfono). */
class PasswordHasherTest {
    @Test fun reconoceElFormatoArgon2() {
        assertTrue(PasswordHasher.esArgon2("\$argon2id\$v=19\$m=32768,t=3,p=1\$c2FsdA\$aGFzaA"))
        assertFalse(PasswordHasher.esArgon2("5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8"))
    }

    @Test fun lasCuentasViejasConSha256SiguenEntrando() {
        val viejo = PasswordHasher.sha256Antiguo("una frase larga de prueba")
        assertTrue(PasswordHasher.verify(viejo, "una frase larga de prueba"))
        assertFalse(PasswordHasher.verify(viejo, "otra frase distinta de prueba"))
        assertTrue(PasswordHasher.necesitaActualizar(viejo))
    }

    @Test fun elSha256AntiguoEsElDeSiempre() {
        assertEquals("5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8", PasswordHasher.sha256Antiguo("password"))
    }
}
