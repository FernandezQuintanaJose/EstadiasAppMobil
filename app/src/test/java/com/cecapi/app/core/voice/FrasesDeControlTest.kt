package com.cecapi.app.core.voice

import com.cecapi.app.core.voice.FrasesDeControl.Control
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrasesDeControlTest {
    private val nombres = setOf("alia")

    @Test
    fun reconoceLasFrasesConRelleno() {
        assertEquals(Control.SILENCIO, FrasesDeControl.control("Cállate"))
        assertEquals(Control.SILENCIO, FrasesDeControl.control("cállate, por favor"))
        assertEquals(Control.SILENCIO, FrasesDeControl.control("Ya cállate"))
        assertEquals(Control.SILENCIO, FrasesDeControl.control("Silencio."))
        assertEquals(Control.DETENER, FrasesDeControl.control("para ya"))
        assertEquals(Control.DETENER, FrasesDeControl.control("Detente por favor"))
    }

    @Test
    fun unaPreguntaConEsasPalabrasNoEsControl() {
        assertNull(FrasesDeControl.control("para qué sirve un círculo"))
        assertNull(FrasesDeControl.control("qué es el silencio"))
        assertNull(FrasesDeControl.control("ya"))
    }

    @Test
    fun quitaElNombreAlInicio() {
        assertEquals("", FrasesDeControl.quitarNombre("ALIA", nombres))
        assertEquals("", FrasesDeControl.quitarNombre("Alía.", nombres))
        assertEquals("cállate", FrasesDeControl.quitarNombre("Alia, cállate", nombres))
        assertEquals("qué es un círculo", FrasesDeControl.quitarNombre("oye Alia qué es un círculo", nombres))
    }

    @Test
    fun elNombreEnMedioNoSeQuita() {
        assertNull(FrasesDeControl.quitarNombre("quién creó a Alia", nombres))
        assertNull(FrasesDeControl.quitarNombre("Aliado de México", nombres))
        assertNull(FrasesDeControl.quitarNombre("Alia", emptySet()))
    }

    @Test
    fun respetaUnNombrePersonalizadoDeDosPalabras() {
        assertEquals("silencio", FrasesDeControl.quitarNombre("Doña Luz, silencio", setOf("dona luz")))
    }
}
