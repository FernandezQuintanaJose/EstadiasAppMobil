package com.cecapi.app.core.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class RespuestaCortaTest {
    private fun corta(t: String) = RespuestaCorta.acortar(t)

    @Test
    fun quitaLasFrasesDeAyuda() {
        assertEquals("No te escuché.", corta("No te escuché. Toca el micrófono para intentarlo otra vez."))
        assertEquals("No entendí.", corta("No entendí. Di lista de comandos de cámara para escuchar lo que puedes decir."))
        assertEquals("Escuché: qué es un círculo. ¿La envío?", corta("Escuché: qué es un círculo. ¿La envío? Di sí o no."))
        assertEquals("Privacidad.", corta("Privacidad. Di volver para regresar."))
    }

    @Test
    fun siTodoEsAyudaSeDiceCompleto() {
        assertEquals("Di toma la foto.", corta("Di toma la foto."))
    }

    @Test
    fun unaListaDeComandosSeOyeCompleta() {
        val lista = "En la cámara puedes decir: toma la foto. Di volver para regresar."
        assertEquals(lista, corta(lista))
    }

    @Test
    fun lasPreguntasNecesariasSeConservan() {
        val consentimiento = "Voy a escuchar fuera de la aplicación. Di acepto para activarlo, o no acepto."
        assertEquals(consentimiento, corta(consentimiento))
    }
}
