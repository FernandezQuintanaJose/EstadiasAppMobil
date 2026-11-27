package com.cecapi.app.feature.modulo5_solicitudes

import com.cecapi.app.feature.modulo5_solicitudes.OrdenEnConsultas.Orden
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrdenEnConsultasTest {
    @Test
    fun `las ordenes para cerrar la app no son consultas`() {
        listOf("cierra la aplicación", "Salir de la app", "cerrar aplicación", "salir a la app").forEach {
            assertEquals(it, Orden.CERRAR_APP, OrdenEnConsultas.reconocer(it))
        }
    }

    @Test
    fun `cerrar y volver juntos regresan`() {
        listOf("cerrar, volver", "cerrar", "volver volver").forEach {
            assertEquals(it, Orden.VOLVER, OrdenEnConsultas.reconocer(it))
        }
    }

    @Test
    fun `cerrar sesion y ayuda se reconocen`() {
        assertEquals(Orden.CERRAR_SESION, OrdenEnConsultas.reconocer("cerrar sesión"))
        assertEquals(Orden.AYUDA, OrdenEnConsultas.reconocer("ayuda"))
    }

    @Test
    fun `las preguntas y pedidos siguen siendo consultas`() {
        listOf(
            "como cierro la aplicación de mi banco",
            "dime 3 comidas para hacer con huevo",
            "redacta un aviso para pedir cerrar la aplicación de becas este año",
            "necesito ayuda con una carta",
            "que significa volver",
        ).forEach { assertNull(it, OrdenEnConsultas.reconocer(it)) }
    }

    @Test
    fun `solo una frase completa confirma el cierre`() {
        assertTrue(OrdenEnConsultas.confirmaCierre("Sí"))
        assertTrue(OrdenEnConsultas.confirmaCierre("sí, cerrar"))
        assertFalse(OrdenEnConsultas.confirmaCierre("no"))
        assertFalse(OrdenEnConsultas.confirmaCierre("sí, debo comprar baterías"))
    }
}
