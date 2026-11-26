package com.cecapi.app.feature.modulo5_solicitudes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuenteDeConsultaTest {
    private fun tema(consulta: String, esDocumento: Boolean = false) = FuenteDeConsulta.temaParaWikipedia(consulta, esDocumento)

    @Test
    fun lasPreguntasSencillasVanAWikipedia() {
        assertEquals("un círculo", tema("¿Qué es un círculo?"))
        assertEquals("Jake el perro", tema("quien es Jake el perro"))
        assertEquals("Querétaro", tema("Dónde queda Querétaro"))
        assertEquals("Miguel Hidalgo", tema("oye, quién fue Miguel Hidalgo"))
    }

    @Test
    fun lasComplejasVanAlServidor() {
        assertNull(tema("por qué el cielo es azul"))
        assertNull(tema("qué es la fotosíntesis y cómo funciona"))
        assertNull(tema("explícame qué es un átomo"))
        assertNull(tema("qué es mejor, un perro o un gato, dame ventajas"))
        assertNull(tema("cómo hago una carta"))
    }

    @Test
    fun losDocumentosYLosTemasLargosVanAlServidor() {
        assertNull(tema("qué es un círculo", esDocumento = true))
        assertNull(tema("qué es lo que debo poner en una carta para pedir permiso al director"))
        assertNull(tema("una carta para pedir un justificante"))
    }
}
