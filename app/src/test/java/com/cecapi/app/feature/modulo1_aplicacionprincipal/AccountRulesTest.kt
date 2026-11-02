package com.cecapi.app.feature.modulo1_aplicacionprincipal

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AccountRulesTest {
    @Test fun aceptaUsuariosNormales() {
        for (u in listOf("JUAN", "ANA.LOPEZ", "PRUEBAUNO", "MARIA_2", "JOSÉ-LUIS")) assertNull(u, AccountRules.usuarioProblem(u))
    }

    @Test fun rechazaUsuariosMalos() {
        for (u in listOf("", "AB", "A".repeat(31), "CON ESPACIO", "ROBA'; DROP TABLE--", "<script>", "CON@ARROBA", "emoji😀")) {
            assertNotNull(u, AccountRules.usuarioProblem(u))
        }
    }

    @Test fun validaElNombre() {
        assertNull(AccountRules.nombreProblem("Ana López"))
        assertNotNull(AccountRules.nombreProblem(" "))
        assertNotNull(AccountRules.nombreProblem("A"))
        assertNotNull(AccountRules.nombreProblem("x".repeat(81)))
    }
}
