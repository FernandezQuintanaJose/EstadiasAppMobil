package com.cecapi.app.core.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubmenuPorVozTest {
    private val opciones = mapOf(
        "PRIVACIDAD" to listOf("privacidad"),
        "AVISOS" to listOf("avisos", "avisos y vibracion", "notificaciones"),
        "VOZ" to listOf("mi voz"),
    )
    private fun buscar(t: String) = SubmenuPorVoz.buscar(t, opciones)

    @Test
    fun elNombreSoloOConUnaOrdenDeAbrir() {
        assertEquals("PRIVACIDAD", buscar("Privacidad"))
        assertEquals("PRIVACIDAD", buscar("abre el panel de privacidad, por favor"))
        assertEquals("AVISOS", buscar("quiero ver avisos y vibración"))
        assertEquals("VOZ", buscar("abre mi voz"))
    }

    @Test
    fun otrasOrdenesQueLoMencionanNoAbren() {
        assertNull(buscar("activa los avisos"))
        assertNull(buscar("desactiva notificaciones"))
        assertNull(buscar("qué es la privacidad"))
        assertNull(buscar("abre"))
    }
}
