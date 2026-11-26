package com.cecapi.app.core.voice

import android.speech.SpeechRecognizer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReintentoDeEscuchaTest {
    private val reintento = ReintentoDeEscucha()

    @Test
    fun cierreProntoSinEntenderSeReintentaUnaSolaVez() {
        assertTrue(reintento.debeReintentar(SpeechRecognizer.ERROR_NO_MATCH, 2_100))
        assertFalse(reintento.debeReintentar(SpeechRecognizer.ERROR_NO_MATCH, 2_100))
    }

    @Test
    fun alPedirEscucharDeNuevoVuelveElReintento() {
        reintento.debeReintentar(SpeechRecognizer.ERROR_NO_MATCH, 2_100)
        reintento.reiniciar()
        assertTrue(reintento.debeReintentar(SpeechRecognizer.ERROR_SPEECH_TIMEOUT, 3_000))
    }

    @Test
    fun siTuvoTiempoDeHablarNoInsiste() {
        assertFalse(reintento.debeReintentar(SpeechRecognizer.ERROR_NO_MATCH, ReintentoDeEscucha.LIMITE_MS))
        assertFalse(reintento.debeReintentar(SpeechRecognizer.ERROR_SPEECH_TIMEOUT, 5_200))
    }

    @Test
    fun siElMicrofonoNuncaQuedoListoNoReintenta() {
        assertFalse(reintento.debeReintentar(SpeechRecognizer.ERROR_NO_MATCH, null))
    }

    @Test
    fun otrosErroresNoSeReintentan() {
        listOf(
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_AUDIO,
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
        ).forEach { assertFalse(reintento.debeReintentar(it, 1_000)) }
        assertTrue("un error ajeno no gasta el reintento", reintento.debeReintentar(SpeechRecognizer.ERROR_NO_MATCH, 1_000))
    }
}
