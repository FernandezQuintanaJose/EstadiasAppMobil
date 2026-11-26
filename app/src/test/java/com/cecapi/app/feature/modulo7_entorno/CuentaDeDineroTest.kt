package com.cecapi.app.feature.modulo7_entorno

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CuentaDeDineroTest {
    private fun billete(d: DenominacionBillete?) = ResultadoBillete(denominacion = d, esBilleteSeguro = d != null, incierto = d == null)
    private fun moneda(d: DenominacionMoneda?, motivo: MotivoMoneda = MotivoMoneda.SEGURA) = ResultadoMoneda(d, 0.9f, motivo)

    @Test
    fun reconoceLosComandosDeDinero() {
        assertEquals(ComandoDeDinero.VER_BILLETE, ComandoDeDinero.de("qué billete es"))
        assertEquals(ComandoDeDinero.SUMAR_BILLETE, ComandoDeDinero.de("agrega este billete"))
        assertEquals(ComandoDeDinero.VER_MONEDA, ComandoDeDinero.de("qué moneda es"))
        assertEquals(ComandoDeDinero.SUMAR_MONEDA, ComandoDeDinero.de("agrega esta moneda"))
        assertEquals(ComandoDeDinero.TOTAL, ComandoDeDinero.de("cuánto llevo"))
        assertEquals(ComandoDeDinero.BORRAR_BILLETES, ComandoDeDinero.de("borra los billetes"))
        assertEquals(ComandoDeDinero.BORRAR_MONEDAS, ComandoDeDinero.de("borra las monedas"))
    }

    @Test
    fun losComandosDeLaCamaraNoSonDeDinero() {
        listOf("toma la foto", "leer texto", "qué hay enfrente", "repite", "siguiente párrafo", "empieza de nuevo", "atrás")
            .forEach { assertNull(it, ComandoDeDinero.de(it)) }
    }

    @Test
    fun sumaBilletesYMonedas() {
        val cuenta = CuentaDeDinero()
        cuenta.sumarBillete(billete(DenominacionBillete.D50))
        cuenta.sumarBillete(billete(DenominacionBillete.D50))
        val dicho = cuenta.sumarMoneda(moneda(DenominacionMoneda.M5))
        assertTrue(dicho, dicho.startsWith("Anotada, una moneda de cinco pesos."))
        assertEquals("Llevas 2 billetes de cincuenta pesos y una moneda de cinco pesos. Son 105 pesos en total.", cuenta.total())
    }

    @Test
    fun noSumaSiNoSeLeyoConClaridad() {
        val cuenta = CuentaDeDinero()
        assertTrue(cuenta.sumarBillete(billete(null)).startsWith("No sumé nada"))
        assertTrue(cuenta.sumarMoneda(moneda(DenominacionMoneda.M10, MotivoMoneda.entries.first { it != MotivoMoneda.SEGURA })).startsWith("No sumé nada"))
        assertTrue(cuenta.total().startsWith("Todavía no has sumado"))
    }

    @Test
    fun borrarUnaCuentaNoBorraLaOtra() {
        val cuenta = CuentaDeDinero()
        cuenta.sumarBillete(billete(DenominacionBillete.D100))
        cuenta.sumarMoneda(moneda(DenominacionMoneda.M2))
        cuenta.borrarMonedas()
        assertEquals("Llevas un billete de cien pesos. Son 1 billete, 100 pesos en total.", cuenta.total())
    }
}
