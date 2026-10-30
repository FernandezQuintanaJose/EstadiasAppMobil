package com.cecapi.app.feature.modulo6_aprendizaje

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RetosCamaraTest {
    @Test fun elNivelUnoPideRojo() {
        assertEquals("rojo", RetosCamara.nuevo(1).color)
    }

    @Test fun elNivelDosNuncaPideRojo() {
        repeat(30) { assertFalse(RetosCamara.nuevo(2, Random(it)).color == "rojo") }
    }

    @Test fun losNivelesTresYCuatroPidenUnObjeto() {
        assertNotNull(RetosCamara.nuevo(3).objeto)
        val cuatro = RetosCamara.nuevo(4)
        assertNotNull(cuatro.objeto)
        assertNotNull(cuatro.color)
    }

    @Test fun unRetoDeColorSeCumpleConEseColor() {
        val reto = RetosCamara.nuevo(1)
        assertTrue(RetosCamara.evaluar(reto, "rojo", "").acerto)
        val mal = RetosCamara.evaluar(reto, "azul", "")
        assertFalse(mal.acerto)
        assertTrue("azul" in mal.mensaje)
        assertFalse(RetosCamara.evaluar(reto, null, "").acerto)
    }

    @Test fun unRetoDeObjetoSeCumpleSiLaCamaraLoReconoce() {
        val reto = Reto(3, "Busca una botella", objeto = "botella")
        assertTrue(RetosCamara.evaluar(reto, null, "Veo una botella de agua. botella").acerto)
        assertFalse(RetosCamara.evaluar(reto, null, "Veo una silla.").acerto)
    }

    @Test fun losAcentosNoImportan() {
        val reto = Reto(3, "Busca un teléfono", objeto = "teléfono")
        assertTrue(RetosCamara.evaluar(reto, null, "Veo un telefono celular").acerto)
    }

    @Test fun objetoYColorPidenLasDosCosas() {
        val reto = Reto(4, "Busca una taza de color rojo", color = "rojo", objeto = "taza")
        assertTrue(RetosCamara.evaluar(reto, "rojo", "una taza").acerto)
        assertTrue(RetosCamara.evaluar(reto, null, "Veo una taza roja. taza").acerto)
        assertFalse(RetosCamara.evaluar(reto, "azul", "una taza").acerto)
        assertFalse(RetosCamara.evaluar(reto, "rojo", "una silla").acerto)
    }
}
