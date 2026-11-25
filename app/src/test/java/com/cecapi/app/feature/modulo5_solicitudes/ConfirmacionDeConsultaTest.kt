package com.cecapi.app.feature.modulo5_solicitudes

import com.cecapi.app.feature.modulo5_solicitudes.ConfirmacionDeConsulta.Resultado
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfirmacionDeConsultaTest {
    @Test fun loOidoNuncaSeEnviaALaPrimera() {
        assertEquals(Resultado.Preguntar("que es un circulo"), ConfirmacionDeConsulta().escuchar("que es un circulo"))
    }

    @Test fun unSiEnviaLaConsultaPendiente() {
        val confirmacion = ConfirmacionDeConsulta()
        confirmacion.escuchar("que es un circulo")
        assertEquals(Resultado.Enviar("que es un circulo"), confirmacion.escuchar("Sí"))
    }

    @Test fun unNoLaDescarta() {
        val confirmacion = ConfirmacionDeConsulta()
        confirmacion.escuchar("que es un circulo")
        assertEquals(Resultado.Descartar, confirmacion.escuchar("no"))
    }

    @Test fun unaFraseConLaPalabraSiNoCuentaComoPermiso() {
        val confirmacion = ConfirmacionDeConsulta()
        confirmacion.escuchar("algo de la television")
        assertEquals(Resultado.Descartar, confirmacion.escuchar("no se si vamos manana"))
    }

    @Test fun despuesDeDescartarEmpiezaDeNuevo() {
        val confirmacion = ConfirmacionDeConsulta()
        confirmacion.escuchar("una")
        confirmacion.escuchar("otra cosa")
        assertEquals(Resultado.Preguntar("tres"), confirmacion.escuchar("tres"))
    }

    @Test fun reiniciarOlvidaLaPendiente() {
        val confirmacion = ConfirmacionDeConsulta()
        confirmacion.escuchar("una")
        confirmacion.reiniciar()
        assertEquals(Resultado.Preguntar("si"), confirmacion.escuchar("si"))
    }
}
