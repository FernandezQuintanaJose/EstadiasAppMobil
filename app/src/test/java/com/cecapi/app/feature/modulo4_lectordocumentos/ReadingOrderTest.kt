package com.cecapi.app.feature.modulo4_lectordocumentos

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingOrderTest {
    private fun b(t: String, x: Int, y: Int, ancho: Int = 400) = BloqueTexto(t, x, y, x + ancho, y + 40)

    @Test fun unMenuDeDosColumnasSeLeePorColumna() {
        val bloques = listOf(
            b("RESTAURANTE EL FOGON", 380, 30, 600),
            b("Sopa de tortilla $85", 40, 150), b("Agua de jamaica $40", 660, 150),
            b("Guacamole $95", 40, 230), b("Limonada $45", 660, 230),
            b("Quesadillas $110", 40, 310), b("Refresco $35", 660, 310),
        )
        val leido = prepararParrafos(bloques)
        assertEquals(
            listOf("RESTAURANTE EL FOGON", "Sopa de tortilla $85", "Guacamole $95", "Quesadillas $110", "Agua de jamaica $40", "Limonada $45", "Refresco $35"),
            leido,
        )
    }

    @Test fun preciosAlineadosALaDerechaSeQuedanConSuPlatillo() {
        val bloques = listOf(
            b("Sopa", 40, 150, 150), b("$85", 400, 150, 80), b("Jamaica", 660, 150, 200), b("$40", 1090, 150, 80),
            b("Guacamole", 40, 230, 250), b("$95", 400, 230, 80), b("Limonada", 660, 230, 200), b("$45", 1090, 230, 80),
            b("Quesadillas", 40, 310, 250), b("$110", 400, 310, 80), b("Refresco", 660, 310, 200), b("$35", 1090, 310, 80),
        )
        assertEquals(
            listOf("Sopa $85", "Guacamole $95", "Quesadillas $110", "Jamaica $40", "Limonada $45", "Refresco $35"),
            prepararParrafos(bloques),
        )
    }

    @Test fun etiquetaYValorDeUnFormularioSiguenPorRenglon() {
        val bloques = listOf(b("Nombre:", 40, 100, 200), b("Juan", 300, 100, 200), b("Edad:", 40, 200, 200), b("30", 300, 200, 200))
        assertEquals(listOf("Nombre: Juan", "Edad: 30"), prepararParrafos(bloques))
    }

    @Test fun reconoceLosTextosSueltosDeUnDiagrama() {
        assertEquals(true, pareceDiagrama(listOf("Seres vivos", "Animales", "Plantas", "pueden ser", "Vertebrados", "realizan")))
        assertEquals(false, pareceDiagrama(listOf("ENTRADAS", "Sopa 85 pesos", "Guacamole 95 pesos", "Quesadillas 110 pesos", "BEBIDAS", "Limonada 45 pesos")))
        assertEquals(false, pareceDiagrama(listOf("Una frase larga que tiene muchas palabras dentro", "Otra frase igual de larga que la anterior")))
    }
}
