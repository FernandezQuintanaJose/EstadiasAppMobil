package com.cecapi.app.feature.modulo7_entorno

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatoDescripcionTest {

    @Test
    fun `quita principal y secundario y cambia al centro por al frente`() {
        val texto = "En la imagen veo como objeto principal una laptop al centro " +
            "y como elemento secundario una lámpara a la izquierda."
        assertEquals(
            "Veo una laptop al frente y una lámpara a la izquierda.",
            FormatoDescripcion.aplicar(texto),
        )
    }

    @Test
    fun `convierte hay en veo y ordena las posiciones`() {
        assertEquals(
            "Veo una silla a la derecha y una botella de agua al frente.",
            FormatoDescripcion.aplicar("Hay una silla en el lado derecho y una botella de agua en el centro."),
        )
    }

    @Test
    fun `deja el ejemplo pedido con al frente`() {
        assertEquals(
            "Veo una laptop al frente y una lámpara a la izquierda y una botella de agua a la derecha.",
            FormatoDescripcion.aplicar(
                "Veo una laptop al centro y una lámpara a la izquierda y una botella de agua a la derecha.",
            ),
        )
    }

    @Test
    fun `quita principalmente`() {
        assertEquals(
            "Veo una silla a la derecha.",
            FormatoDescripcion.aplicar("Veo principalmente una silla a la derecha."),
        )
    }

    @Test
    fun `corrige palabras literales del traductor`() {
        assertEquals(
            "El saco de boxeo está colgado de una jaula de metal al frente de la habitación.",
            FormatoDescripcion.aplicar(
                "La bolsa de perforación está colgada de una jaula de metal al centro de la habitación.",
            ),
        )
        assertEquals(
            "La habitación tiene una cama con sábanas y almohadas blancas y varias pesas a la izquierda.",
            FormatoDescripcion.aplicar(
                "La habitación tiene una cama con hojas y almohadas blancas y varias placas de peso a la izquierda.",
            ),
        )
        assertEquals(
            "A ambos lados del saco de boxeo hay pesas.",
            FormatoDescripcion.aplicar("A ambos lados de la bolsa de punzonado hay placas de peso."),
        )
    }

    @Test
    fun `quita la marca y corrige el genero del saco`() {
        assertEquals(
            "El saco de boxeo es negro.",
            FormatoDescripcion.aplicar(
                "El saco de boxeo es negra con escritura blanca que dice \"the willible\".",
            ),
        )
        assertEquals(
            "El saco de boxeo.",
            FormatoDescripcion.aplicar(
                "El saco de boxeo tiene el texto \"THE N&BATABLE\" escrito en letras blancas.",
            ),
        )
    }

    @Test
    fun `concuerda dos puertas y quita la repeticion`() {
        assertEquals(
            "Veo dos puertas en la habitación; una a la derecha y otra a la izquierda.",
            FormatoDescripcion.aplicar(
                "Hay dos puertas en la habitación; uno a la derecha y otro a la izquierda.",
            ),
        )
        assertEquals(
            "La puerta revela un vistazo de un pasillo.",
            FormatoDescripcion.aplicar("La puerta revela un vistazo de un pasillo o un pasillo."),
        )
    }

    @Test
    fun `quita solo la frase que quedo incompleta`() {
        assertEquals(
            "La habitación tiene una cama y una lámpara.",
            FormatoDescripcion.aplicar(
                "La habitación tiene una cama y una lámpara. El armario está en",
            ),
        )
        assertEquals(
            "Veo una laptop al frente.",
            FormatoDescripcion.aplicar("Veo una laptop al frente y"),
        )
        assertNull(FormatoDescripcion.aplicar("El armario está en"))
    }

    @Test
    fun `conserva una frase con letras que no es una marca`() {
        assertEquals(
            "Veo un cartel con letras grandes en la pared.",
            FormatoDescripcion.aplicar("Hay un cartel con letras grandes en la pared."),
        )
    }

    @Test
    fun `la pantalla conserva menu principal y corta frases incompletas`() {
        assertEquals(
            "La pantalla muestra el menú principal de la aplicación.",
            FormatoDescripcion.aplicarPantalla(
                "La pantalla muestra el menú principal de la aplicación.",
            ),
        )
        assertEquals(
            "La pantalla muestra una conversación de WhatsApp con varios mensajes.",
            FormatoDescripcion.aplicarPantalla(
                "La pantalla muestra una conversación de WhatsApp con varios mensajes. El botón de enviar está en",
            ),
        )
    }

    @Test
    fun `conserva una frase natural sin palabras prohibidas`() {
        val texto = "Una computadora portátil de plata se coloca en un escritorio de madera."
        assertEquals(texto, FormatoDescripcion.aplicar(texto))
    }

    @Test
    fun `agrega el punto final`() {
        assertEquals(
            "Veo una laptop al frente.",
            FormatoDescripcion.aplicar("Veo una laptop al frente"),
        )
    }

    @Test
    fun `devuelve null cuando no hay texto`() {
        assertNull(FormatoDescripcion.aplicar("   "))
    }
}
