package com.cecapi.app.feature.modulo4_lectordocumentos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProductoPorCodigoTest {
    @Test fun dicePorductoMarcaYCantidad() {
        assertEquals("Chocolate, de la marca Abuelita, 90 g.", ProductoPorCodigo.frase("Chocolate", "Abuelita, Nestlé", "90 g"))
    }

    @Test fun noRepiteLaMarcaSiYaEstaEnElNombre() {
        assertEquals("Coca-Cola Original.", ProductoPorCodigo.frase("Coca-Cola Original", "Coca-Cola", ""))
    }

    @Test fun sinNombreNiMarcaNoDiceNada() {
        assertNull(ProductoPorCodigo.frase(" ", "", "500 ml"))
    }

    @Test fun quitaLaEDeCantidadEstimada() {
        assertEquals("Nutella, de la marca Ferrero, 400 g.", ProductoPorCodigo.frase("Nutella", "Ferrero", "400 g e"))
    }
}
