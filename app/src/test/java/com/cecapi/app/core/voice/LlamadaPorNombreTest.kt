package com.cecapi.app.core.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun unaOrdenConElNombreComoSegundaOpcion() {
        assertEquals("alia abre cámara", LlamadaPorNombre.ordenConNombre(listOf("palia abre cámara", "alia abre cámara"), nombres))
        assertNull(LlamadaPorNombre.ordenConNombre(listOf("alia abre cámara", "alia abre la cámara"), nombres))
        assertNull(LlamadaPorNombre.ordenConNombre(listOf("hoy en las noticias dijeron que no habrá clases", "alia no habrá clases"), nombres))
    }

    @Test
    fun sinElNombreExactoNoCuenta() {
        assertFalse(LlamadaPorNombre.esElNombre(listOf("Italia", "había", "paya"), nombres))
        assertFalse(LlamadaPorNombre.esElNombre(listOf("palia abre cámara"), nombres))
        assertFalse(LlamadaPorNombre.esElNombre(emptyList(), nombres))
    }

    @Test
    fun loQueGoogleEscribePorAliaCuentaSoloSiEsLoUnico() {
        assertTrue(LlamadaPorNombre.esElNombre(listOf("palia"), nombres))
        assertTrue(LlamadaPorNombre.esElNombre(listOf("Alias"), nombres))
        assertFalse(LlamadaPorNombre.esElNombre(listOf("palia"), setOf("luna")))
    }
}
