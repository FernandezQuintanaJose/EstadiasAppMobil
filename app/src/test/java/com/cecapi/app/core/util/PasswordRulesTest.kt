package com.cecapi.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Pruebas de la regla de contraseña: normalización idéntica a la que debe aplicar el servidor, y validación. */
class PasswordRulesTest {

    @Test
    fun quitaEspaciosYPasaAMinusculas() {
        assertEquals("miperrollamadoluna", PasswordRules.normalize("Mi Perro Llamado LUNA"))
    }

    @Test
    fun quitaAcentosYDieresisPeroConservaLaEnye() {
        assertEquals("cañonpinguinoyvergüenza".replace("ü", "u"), PasswordRules.normalize("Cañón Pingüino y Vergüenza"))
        assertEquals("niño", PasswordRules.normalize("NIÑO"))
        assertEquals("ñandú".replace("ú", "u"), PasswordRules.normalize("Ñandú"))
    }

    @Test
    fun dictadaYEscritaLlegaIgual() {
        assertEquals(
            PasswordRules.normalize("la casa del árbol grande"),
            PasswordRules.normalize("La Casa Del Arbol  Grande "),
        )
    }

    @Test
    fun aceptaQuinceLetras() {
        assertNull(PasswordRules.problem(PasswordRules.normalize("abcdefghijklmno")))
        assertNull(PasswordRules.problem(PasswordRules.normalize("la casa del árbol grande")))
    }

    @Test
    fun rechazaMenosDeQuince() {
        assertNotNull(PasswordRules.problem(PasswordRules.normalize("abcdefghijklmn")))
        assertNotNull(PasswordRules.problem(PasswordRules.normalize("1234")))
    }

    @Test
    fun rechazaNumerosYSimbolos() {
        assertNotNull(PasswordRules.problem(PasswordRules.normalize("abcdefghijklmn1")))
        assertNotNull(PasswordRules.problem(PasswordRules.normalize("abcdefghijklmn!")))
    }

    @Test
    fun rechazaVacia() {
        assertNotNull(PasswordRules.problem(PasswordRules.normalize("   ")))
    }

    @Test
    fun laEnyeCuentaComoLetra() {
        assertNull(PasswordRules.problem(PasswordRules.normalize("ñandúñandúñandú")))
    }
}
