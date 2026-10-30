package com.cecapi.app.feature.modulo5_solicitudes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsultasViewModelTest {
    private fun doc(t: String) = ConsultasViewModel.pideDocumento(t)

    @Test fun pedirUnDocumentoSeReconoce() {
        assertTrue(doc("redacta una carta para pedir un justificante"))
        assertTrue(doc("Hazme un oficio para la dirección"))
        assertTrue(doc("necesito una constancia de estudios"))
        assertTrue(doc("elabora un aviso para los padres"))
        assertTrue(doc("escríbeme una carta de agradecimiento"))
    }

    @Test fun unaPreguntaNoEsUnDocumento() {
        assertFalse(doc("qué es una carta de presentación"))
        assertFalse(doc("cuál es la capital de Francia"))
        assertFalse(doc("cómo hago una constancia"))
        assertFalse(doc("qué es el queso"))
        assertFalse(doc("quién escribió Don Quijote"))
        assertFalse(doc(""))
    }
}
