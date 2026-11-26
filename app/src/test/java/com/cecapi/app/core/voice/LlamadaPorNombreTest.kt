package com.cecapi.app.core.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamadaPorNombreTest {
    private val nombres = setOf("alia")

    @Test
    fun elNombreComoSegundaOpcionDeUnaFraseCortaCuenta() {
        // Lo que dio el reconocedor en el teléfono.
        assertTrue(LlamadaPorNombre.esElNombre(listOf("palia", "alia", "paya"), nombres))
        assertTrue(LlamadaPorNombre.esElNombre(listOf("Alía"), nombres))
    }

    @Test
    fun enUnaFraseLargaNoBastaUnaOpcion() {
        assertFalse(LlamadaPorNombre.esElNombre(listOf("necesita nuestras oraciones", "alia"), nombres))
    }

    @Test
    fun sinElNombreExactoNoCuenta() {
        assertFalse(LlamadaPorNombre.esElNombre(listOf("palia", "alias", "alian"), nombres))
        assertFalse(LlamadaPorNombre.esElNombre(emptyList(), nombres))
    }
}
