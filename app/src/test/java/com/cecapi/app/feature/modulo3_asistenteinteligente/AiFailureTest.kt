package com.cecapi.app.feature.modulo3_asistenteinteligente

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiFailureTest {
    @Test fun cadaFamiliaDeCodigosTieneSuMensaje() {
        assertEquals(AiFailure.RATE_LIMITED, AiFailure.deEstado(429))
        assertEquals(AiFailure.REJECTED, AiFailure.deEstado(400))
        assertEquals(AiFailure.REJECTED, AiFailure.deEstado(422))
        assertEquals(AiFailure.NOT_AUTHORIZED, AiFailure.deEstado(401))
        assertEquals(AiFailure.NOT_AUTHORIZED, AiFailure.deEstado(403))
        for (c in listOf(500, 502, 503, 504)) assertEquals(AiFailure.UNAVAILABLE, AiFailure.deEstado(c))
    }

    @Test fun el404SeDejaParaQueUnderstandLoManeje() {
        assertNull(AiFailure.deEstado(404))
    }

    @Test fun unCodigoRaroSeDiceComoNoPudeConectar() {
        assertEquals(AiFailure.UNREACHABLE, AiFailure.deEstado(302))
        assertEquals(AiFailure.UNREACHABLE, AiFailure.deEstado(418))
    }

    @Test fun cadaFallaTieneUnMensajeDistintoParaLaVoz() {
        val mensajes = AiFailure.entries.map { AiMessages.forFailure(AiException(it, "")) }
        assertEquals(mensajes.toSet().size, AiFailure.entries.size)
    }

    @Test fun siElServidorExplicaQuePasoConLaFotoSeDiceEsaFrase() {
        val frase = "La foto es demasiado grande."
        assertEquals(frase, AiMessages.forFailure(AiException(AiFailure.REJECTED, frase)))
        assertEquals(AiMessages.REJECTED, AiMessages.forFailure(AiException(AiFailure.REJECTED, "")))
    }
}
