package com.cecapi.app.core.voice

import com.cecapi.app.core.voice.FraseDeActivacion.Accion
import org.junit.Assert.assertEquals
import org.junit.Test

class FraseDeActivacionTest {
    private fun decidir(frase: String, nombre: String = "alia") =
        FraseDeActivacion.decidir(frase.split(" ").filter { it.isNotBlank() }, nombre)

    @Test
    fun elNombreAlInicioLlama() {
        assertEquals(Accion.LLAMADA, decidir("alia"))
        assertEquals(Accion.LLAMADA, decidir("alia [unk] [unk]"))
    }

    @Test
    fun elNombreEnMedioDeUnaConversacionNoLlama() {
        assertEquals(Accion.NADA, decidir("[unk] alia [unk]"))
        assertEquals(Accion.NADA, decidir("maría alia"))
        assertEquals(Accion.NADA, decidir("alia [unk] [unk] [unk] [unk] [unk] [unk]"))
    }

    @Test
    fun lasParecidasNoLlaman() {
        listOf("había", "al día", "italia", "maría", "allá", "[unk]", "").forEach { assertEquals(it, Accion.NADA, decidir(it)) }
    }

    @Test
    fun lasOrdenesDeControlValenSolasODespuesDelNombre() {
        assertEquals(Accion.SILENCIO, decidir("cállate"))
        assertEquals(Accion.SILENCIO, decidir("alia silencio"))
        assertEquals(Accion.DETENER, decidir("detente"))
        assertEquals(Accion.NADA, decidir("[unk] silencio [unk]"))
    }

    @Test
    fun unNombreDeDosPalabras() {
        assertEquals(Accion.LLAMADA, decidir("doña luz [unk]", "doña luz"))
        assertEquals(Accion.NADA, decidir("luz [unk]", "doña luz"))
    }
}
