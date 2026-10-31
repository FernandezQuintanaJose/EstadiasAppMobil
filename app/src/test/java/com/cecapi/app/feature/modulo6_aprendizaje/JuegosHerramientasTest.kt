package com.cecapi.app.feature.modulo6_aprendizaje

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class JuegosHerramientasTest {
    @Test fun unaRondaDeMemoriaTieneCadaSonidoUnaVezYLaRespuestaEntreLasOpciones() {
        val ronda = MemoriaSonidos.ronda(Random(7))
        assertEquals(MemoriaSonidos.PREGUNTAS_POR_RONDA, ronda.size)
        assertEquals(ronda.size, ronda.map { it.sonido }.toSet().size)
        ronda.forEach { pregunta ->
            assertEquals(4, pregunta.opciones.size)
            assertTrue(pregunta.sonido in pregunta.opciones)
            assertEquals(4, pregunta.opciones.toSet().size)
        }
    }

    @Test fun laVozReconoceLaOpcionNombrada() {
        val opciones = MemoriaSonidos.SONIDOS.take(4)
        assertEquals("tambor", MemoriaSonidos.reconocer("es un tambor", opciones)?.archivo)
        assertEquals("aplauso", MemoriaSonidos.reconocer("Son palmadas", opciones)?.archivo)
        assertNull(MemoriaSonidos.reconocer("no sé", opciones))
    }

    @Test fun elMarcadorSumaRestaYNoBajaDeCero() {
        val m = Marcador().sumar(1).sumar(1).sumar(2).restar(2).restar(2)
        assertEquals(2, m.equipo1)
        assertEquals(0, m.equipo2)
        assertEquals("Gana el equipo uno, 2 a 0.", m.enPalabras())
        assertEquals("Empate a 0.", Marcador().enPalabras())
    }

    @Test fun elTiempoSeDiceBien() {
        assertEquals("45 segundos", tiempoEnPalabras(45))
        assertEquals("1 minuto", tiempoEnPalabras(60))
        assertEquals("2 minutos y 5 segundos", tiempoEnPalabras(125))
        assertEquals("02:05", tiempoEnReloj(125))
    }

    @Test fun laCajaMisteriosaCompruebaLaAdivinanza() {
        assertTrue(JuegoCamaraViewModel.veredictoDeAdivinanza("creo que es una taza", "Veo una taza blanca. taza").acerto)
        assertTrue(JuegoCamaraViewModel.veredictoDeAdivinanza("unas tijeras", "tijera").acerto)
        assertFalse(JuegoCamaraViewModel.veredictoDeAdivinanza("una botella", "Veo un libro").acerto)
    }

    @Test fun todosLosTiposTienenSuId() {
        JuegoTipo.entries.forEach { assertEquals(it, JuegoTipo.desdeId(it.id)) }
    }

    @Test fun laCajaSoloAceptaUnaFraseExplicita() {
        assertEquals("una taza", JuegoCamaraViewModel.frasePropuesta("creo que es una taza"))
        assertEquals("unas llaves", JuegoCamaraViewModel.frasePropuesta("son unas llaves"))
        assertEquals("un libro", JuegoCamaraViewModel.frasePropuesta("yo digo que es un libro"))
        assertNull(JuegoCamaraViewModel.frasePropuesta("de ahi empece"))
        assertNull(JuegoCamaraViewModel.frasePropuesta("ya lo tengo"))
        assertNull(JuegoCamaraViewModel.frasePropuesta(""))
    }
}
