package com.cecapi.app.feature.modulo1_aplicacionprincipal

import com.cecapi.app.feature.modulo1_aplicacionprincipal.DictadoDeContrasena.Resultado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DictadoDeContrasenaTest {
    private val frase = "mi perro come manzanas verdes"

    @Test fun unaSolaFraseNuncaDejaLaContrasenaLista() {
        assertEquals(Resultado.Repetir, DictadoDeContrasena().escuchar(frase))
    }

    @Test fun dosVecesIgualQuedaListaYNormalizada() {
        val dictado = DictadoDeContrasena()
        dictado.escuchar(frase)
        assertEquals(Resultado.Lista("miperrocomemanzanasverdes"), dictado.escuchar("Mi Perro come manzanas VERDES"))
    }

    @Test fun siLaSegundaEsDistintaSeEmpiezaDeNuevo() {
        val dictado = DictadoDeContrasena()
        dictado.escuchar(frase)
        assertEquals(Resultado.NoCoincide, dictado.escuchar("otra frase distinta de la television"))
        assertEquals(Resultado.Repetir, dictado.escuchar(frase))
    }

    @Test fun unaCortaNoCuenta() {
        val dictado = DictadoDeContrasena()
        assertTrue(dictado.escuchar("corta") is Resultado.Invalida)
        assertEquals(Resultado.Repetir, dictado.escuchar(frase))
    }

    @Test fun reiniciarOlvidaLaPrimera() {
        val dictado = DictadoDeContrasena()
        dictado.escuchar(frase)
        dictado.reiniciar()
        assertEquals(Resultado.Repetir, dictado.escuchar(frase))
    }
}
