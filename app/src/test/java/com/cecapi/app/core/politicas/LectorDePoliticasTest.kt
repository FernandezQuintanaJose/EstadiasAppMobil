package com.cecapi.app.core.politicas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LectorDePoliticasTest {
    @Test fun noSePuedeAceptarSinLlegarAlFinal() {
        val lector = LectorDePoliticas()
        lector.mayorDeEdad = true
        assertNotNull(lector.faltaParaAceptar())
    }

    @Test fun noSePuedeAceptarSinDeclararMayoriaDeEdad() {
        val lector = LectorDePoliticas()
        while (lector.siguiente()) { /* avanza */ }
        assertTrue(lector.llegoAlFinal)
        assertNotNull(lector.faltaParaAceptar())
    }

    @Test fun seAceptaAlFinalYSiendoMayor() {
        val lector = LectorDePoliticas()
        while (lector.siguiente()) { /* avanza */ }
        lector.mayorDeEdad = true
        assertNull(lector.faltaParaAceptar())
    }

    @Test fun regresarNoQuitaLoVisto() {
        val lector = LectorDePoliticas()
        while (lector.siguiente()) { /* avanza */ }
        lector.anterior()
        assertFalse(lector.esUltima)
        assertTrue(lector.llegoAlFinal)
    }

    @Test fun leerTodoEnVozCuentaComoLlegarAlFinal() {
        val lector = LectorDePoliticas()
        lector.irAlFinal()
        lector.mayorDeEdad = true
        assertNull(lector.faltaParaAceptar())
        assertTrue(Politicas.textoCompletoParaVoz().contains("Parte 1 de ${Politicas.partes.size}"))
    }

    @Test fun laVersionEstaDefinidaYHayPartes() {
        assertTrue(Politicas.VERSION.isNotBlank())
        assertTrue(Politicas.partes.size >= 5)
    }
}
