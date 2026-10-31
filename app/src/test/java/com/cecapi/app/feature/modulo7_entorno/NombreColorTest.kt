package com.cecapi.app.feature.modulo7_entorno

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NombreColorTest {
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test fun nombraLosColoresBasicos() {
        assertEquals("rojo", NombreColor.nombrePixel(rgb(220, 20, 20)))
        assertEquals("verde", NombreColor.nombrePixel(rgb(20, 180, 40)))
        assertEquals("azul", NombreColor.nombrePixel(rgb(20, 40, 200)))
        assertEquals("amarillo", NombreColor.nombrePixel(rgb(240, 220, 20)))
        assertEquals("naranja", NombreColor.nombrePixel(rgb(240, 130, 20)))
        assertEquals("morado", NombreColor.nombrePixel(rgb(130, 40, 190)))
        assertEquals("negro", NombreColor.nombrePixel(rgb(10, 10, 10)))
        assertEquals("blanco", NombreColor.nombrePixel(rgb(245, 245, 245)))
        assertEquals("gris", NombreColor.nombrePixel(rgb(128, 128, 128)))
        assertEquals("café", NombreColor.nombrePixel(rgb(110, 70, 30)))
    }

    @Test fun dominanteExigeQueUnColorPredomine() {
        val casiTodoRojo = IntArray(100) { if (it < 80) rgb(220, 20, 20) else rgb(20, 40, 200) }
        assertEquals("rojo", NombreColor.dominante(casiTodoRojo, 0.6f)?.nombre)
        val mezcla = IntArray(100) { if (it % 2 == 0) rgb(220, 20, 20) else rgb(20, 40, 200) }
        assertNull(NombreColor.dominante(mezcla, 0.6f))
        assertNull(NombreColor.dominante(IntArray(0)))
    }
}
