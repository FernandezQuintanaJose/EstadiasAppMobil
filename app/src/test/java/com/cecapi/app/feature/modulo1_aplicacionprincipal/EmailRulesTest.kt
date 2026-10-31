package com.cecapi.app.feature.modulo1_aplicacionprincipal

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EmailRulesTest {
    @Test fun aceptaCorreosNormales() {
        assertNull(EmailRules.problem("ana.lopez@gmail.com"))
        assertNull(EmailRules.problem("a+b@correo.utd.edu.mx"))
    }

    @Test fun rechazaFormasInvalidas() {
        for (malo in listOf("ana", "ana@", "@gmail.com", "ana@gmail", "ana@@gmail.com", "ana..x@gmail.com", "ana @gmail.com", ".ana@gmail.com")) {
            assertNotNull(malo, EmailRules.problem(malo))
        }
    }

    @Test fun avisaDeErroresComunesDeEscritura() {
        assertNotNull(EmailRules.problem("ana@gmial.com"))
        assertNotNull(EmailRules.problem("ana@hotmail.con"))
    }
}
