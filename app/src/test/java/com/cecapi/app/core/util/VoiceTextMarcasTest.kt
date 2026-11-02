package com.cecapi.app.core.util

import com.cecapi.app.core.voice.VoiceText
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceTextMarcasTest {
    @Test fun marcasEnMayusculasSeDicenComoPalabra() {
        assertEquals("Nike Just do it", VoiceText.forSpeech("NIKE Just do it"))
        assertEquals("Adidas", VoiceText.forSpeech("ADIDAS"))
    }

    @Test fun palabrasConGuionSeSeparan() {
        assertEquals("ele gate", VoiceText.forSpeech("ele-gate"))
        assertEquals("tel 55-1234", VoiceText.forSpeech("tel 55-1234"))
    }

    @Test fun siglasSeDejan() {
        assertEquals("IMSS y SEP y UTD", VoiceText.forSpeech("IMSS y SEP y UTD"))
    }
}
