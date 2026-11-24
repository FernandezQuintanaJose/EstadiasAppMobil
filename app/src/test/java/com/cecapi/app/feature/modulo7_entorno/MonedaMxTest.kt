package com.cecapi.app.feature.modulo7_entorno

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de la decisión de [MonedaMx] con probabilidades simuladas. No corre el modelo: fija el
 * comportamiento de los umbrales (0.75 pedida / 0.95 automática) y de las reglas de acuerdo.
 */
class MonedaMxTest {

    private fun lecturas(vararg pares: Pair<String, Float>) = pares.toList()

    @Test
    fun `etiqueta con numero se convierte en denominacion`() {
        assertEquals(DenominacionMoneda.M5, MonedaMx.desdeEtiqueta("5pesos"))
        assertEquals(DenominacionMoneda.M10, MonedaMx.desdeEtiqueta("$10"))
        assertEquals(DenominacionMoneda.M20, MonedaMx.desdeEtiqueta("20 pesos"))
        assertEquals(DenominacionMoneda.M1, MonedaMx.desdeEtiqueta("1 peso"))
        assertEquals(DenominacionMoneda.M2, MonedaMx.desdeEtiqueta("2"))
    }

    @Test
    fun `nombre de clase generico no se toma por moneda`() {
        // Si monedas_labels.txt no coincide con el modelo, las clases quedan como "clase_2": no es "2 pesos".
        assertNull(MonedaMx.desdeEtiqueta("clase_2"))
        assertNull(MonedaMx.desdeEtiqueta("moneda"))
    }

    @Test
    fun `sin lecturas no se decide nada`() {
        val r = MonedaMx.decidir(emptyList())
        assertNull(r.denominacion)
        assertEquals(MotivoMoneda.NO_CLARO, r.motivo)
        assertFalse(r.esSegura)
    }

    @Test
    fun `moneda pedida con confianza alta y acuerdo es segura`() {
        val r = MonedaMx.decidir(
            lecturas("5pesos" to 0.90f, "1peso" to 0.10f),
            listOf(
                lecturas("5pesos" to 0.91f, "1peso" to 0.09f),
                lecturas("5pesos" to 0.88f, "1peso" to 0.12f),
                lecturas("1peso" to 0.55f, "5pesos" to 0.45f), // una variante se equivoca: con 2 de 3 basta
            ),
        )
        assertTrue(r.esSegura)
        assertEquals(DenominacionMoneda.M5, r.denominacion)
    }

    @Test
    fun `moneda pedida con confianza media es dudosa`() {
        val r = MonedaMx.decidir(lecturas("5pesos" to 0.70f, "1peso" to 0.15f))
        assertFalse(r.esSegura)
        assertEquals(MotivoMoneda.DUDOSA, r.motivo)
        assertEquals(DenominacionMoneda.M5, r.denominacion)
    }

    @Test
    fun `moneda pedida con confianza baja no se reconoce`() {
        val r = MonedaMx.decidir(lecturas("5pesos" to 0.50f, "1peso" to 0.20f))
        assertNull(r.denominacion)
        assertEquals(MotivoMoneda.NO_CLARO, r.motivo)
    }

    @Test
    fun `foto normal exige que las tres variantes coincidan`() {
        val r = MonedaMx.decidir(
            lecturas("10pesos" to 0.97f, "20pesos" to 0.03f),
            listOf(
                lecturas("10pesos" to 0.97f),
                lecturas("10pesos" to 0.95f),
                lecturas("20pesos" to 0.80f), // el espejo ve otra cosa: el modelo está adivinando
            ),
            automatico = true,
        )
        assertFalse(r.esSegura)
        assertEquals(MotivoMoneda.DUDOSA, r.motivo)
    }

    @Test
    fun `foto normal con el modelo seguro es segura`() {
        val r = MonedaMx.decidir(
            lecturas("10pesos" to 0.97f, "20pesos" to 0.03f),
            listOf(
                lecturas("10pesos" to 0.97f),
                lecturas("10pesos" to 0.96f),
                lecturas("10pesos" to 0.92f),
            ),
            automatico = true,
        )
        assertTrue(r.esSegura)
        assertEquals(DenominacionMoneda.M10, r.denominacion)
    }

    @Test
    fun `foto normal con una variante floja es dudosa`() {
        val r = MonedaMx.decidir(
            lecturas("10pesos" to 0.97f, "20pesos" to 0.03f),
            listOf(
                lecturas("10pesos" to 0.97f),
                lecturas("10pesos" to 0.96f),
                lecturas("10pesos" to 0.65f), // por debajo de 0.70
            ),
            automatico = true,
        )
        assertFalse(r.esSegura)
        assertEquals(MotivoMoneda.DUDOSA, r.motivo)
    }

    @Test
    fun `clase especial sin_moneda`() {
        assertEquals(MotivoMoneda.NO_HAY_MONEDA, MonedaMx.decidir(lecturas("sin_moneda" to 0.80f)).motivo)
        assertEquals(MotivoMoneda.NO_CLARO, MonedaMx.decidir(lecturas("sin_moneda" to 0.50f)).motivo)
    }

    @Test
    fun `clase especial varias`() {
        assertEquals(MotivoMoneda.VARIAS, MonedaMx.decidir(lecturas("varias" to 0.90f)).motivo)
    }

    @Test
    fun `solo se dice como hecho lo seguro`() {
        assertTrue(
            MonedaMx.frase(MonedaMx.decidir(lecturas("5pesos" to 0.95f)))
                .startsWith("Es una moneda de cinco pesos."),
        )
        assertTrue(
            MonedaMx.frase(MonedaMx.decidir(lecturas("5pesos" to 0.70f)))
                .startsWith("Parece una moneda de cinco pesos"),
        )
    }

    @Test
    fun `rescate por recortes nunca afirma una moneda`() {
        // Aunque la rejilla esté muy confiada, el rescate solo puede decir "parece".
        val r = MonedaMx.dudosa("20pesos", 0.99f)
        assertEquals(DenominacionMoneda.M20, r.denominacion)
        assertEquals(MotivoMoneda.DUDOSA, r.motivo)
        assertFalse(r.esSegura)
        assertTrue(MonedaMx.frase(r).startsWith("Parece una moneda de veinte pesos"))
    }

    @Test
    fun `rescate conserva las clases especiales`() {
        assertEquals(MotivoMoneda.NO_HAY_MONEDA, MonedaMx.dudosa("sin_moneda", 0.90f).motivo)
        assertEquals(MotivoMoneda.VARIAS, MonedaMx.dudosa("varias", 0.90f).motivo)
    }

    @Test
    fun `rescate con una clase desconocida no inventa una moneda`() {
        val r = MonedaMx.dudosa("clase_2", 0.90f)
        assertNull(r.denominacion)
        assertEquals(MotivoMoneda.NO_CLARO, r.motivo)
    }

    @Test
    fun `el apoyo corrige una duda cuando ve otra moneda con mucha confianza`() {
        // El principal duda entre 5 y 1 pesos; el apoyo ve 1 peso con 0.95.
        val r = MonedaMx.correccionPorApoyo(
            etiquetaPrincipal = "5pesos", confianzaPrincipal = 0.70f,
            etiquetaApoyo = "1peso", confianzaApoyo = 0.95f,
            margen = 0.10f, umbral = 0.85f,
        )
        assertEquals(DenominacionMoneda.M1, r?.denominacion)
        assertEquals(MotivoMoneda.DUDOSA, r?.motivo)
        assertFalse(r!!.esSegura)
    }

    @Test
    fun `el apoyo no corrige si coincide con el principal`() {
        assertNull(
            MonedaMx.correccionPorApoyo("1peso", 0.70f, "1peso", 0.99f, margen = 0.10f, umbral = 0.85f),
        )
    }

    @Test
    fun `el apoyo no corrige sin confianza suficiente o sin ventaja`() {
        // Muy seguro pero apenas mejor que el principal.
        assertNull(
            MonedaMx.correccionPorApoyo("5pesos", 0.90f, "1peso", 0.95f, margen = 0.10f, umbral = 0.85f),
        )
        // Clara ventaja pero por debajo del umbral de corrección.
        assertNull(
            MonedaMx.correccionPorApoyo("5pesos", 0.40f, "1peso", 0.80f, margen = 0.10f, umbral = 0.85f),
        )
    }

    @Test
    fun `el apoyo no convierte una duda en no hay moneda`() {
        assertNull(
            MonedaMx.correccionPorApoyo("5pesos", 0.70f, "sin_moneda", 0.99f, margen = 0.10f, umbral = 0.85f),
        )
    }

    @Test
    fun `foto general sin denominacion no dice que no hay moneda`() {
        val noClaro = ResultadoMoneda(null, 0.40f, MotivoMoneda.NO_CLARO)
        assertTrue(
            MonedaMx.fraseParaFotoGeneral(noClaro)
                .startsWith("Veo una moneda, pero no pude reconocer de cuánto es."),
        )
        // Aunque el clasificador diga "sin moneda", la IA ya vio una: no se contradice.
        val sinMoneda = ResultadoMoneda(null, 0.80f, MotivoMoneda.NO_HAY_MONEDA)
        assertTrue(
            MonedaMx.fraseParaFotoGeneral(sinMoneda)
                .startsWith("Veo una moneda, pero no pude reconocer de cuánto es."),
        )
        assertFalse(MonedaMx.fraseParaFotoGeneral(sinMoneda).startsWith("No veo una moneda"))
    }

    @Test
    fun `foto general afirma la denominacion cuando es segura`() {
        val segura = MonedaMx.decidir(lecturas("5pesos" to 0.95f))
        assertEquals("Es una moneda de cinco pesos.", MonedaMx.fraseParaFotoGeneral(segura))
    }

    @Test
    fun `foto general dice parece cuando la lectura es dudosa`() {
        // La duda se advierte ("parece"), no se afirma; así la persona sabe de cuánto podría ser
        // sin que la app asegure una denominación que quizá no sea.
        val dudosa = MonedaMx.decidir(lecturas("1peso" to 0.64f))
        val frase = MonedaMx.fraseParaFotoGeneral(dudosa)
        assertTrue(frase.startsWith("Parece una moneda de un peso, pero no estoy segura."))
        assertFalse(frase.startsWith("Es una moneda"))
    }

    @Test
    fun `foto general no nombra la denominacion cuando viene de un rescate`() {
        // La rejilla de recortes es el último recurso: con 5 pesos propuso 20 con 0.575. Aunque
        // supere el umbral de rescate, en una foto general no se nombra una denominación.
        val rescate = MonedaMx.dudosa("5pesos", 0.575f, esRescate = true)
        assertEquals(MotivoMoneda.DUDOSA, rescate.motivo)
        assertEquals(DenominacionMoneda.M5, rescate.denominacion)
        val frase = MonedaMx.fraseParaFotoGeneral(rescate)
        assertTrue(frase.startsWith("Veo una moneda, pero no pude reconocer de cuánto es."))
        assertFalse(frase.contains("cinco pesos"))
    }

    @Test
    fun `peticion explicita si dice parece cuando viene de un rescate`() {
        // En la petición explícita la persona preguntó por la moneda; la duda se dice con "parece".
        val rescate = MonedaMx.dudosa("5pesos", 0.575f, esRescate = true)
        assertTrue(MonedaMx.frase(rescate).startsWith("Parece una moneda de cinco pesos"))
        assertFalse(MonedaMx.frase(rescate).startsWith("Es una moneda"))
    }

    @Test
    fun `foto general no afirma una lectura segura que el apoyo contradice`() {
        // Reproducido con la política offline: un acercamiento de una moneda de 5 pesos hizo que el
        // principal dijera 10 pesos con 0.782; el apoyo vio 5 pesos con 0.932. La foto general no
        // debe afirmar 10; la corrección del apoyo se dice como duda.
        val contradiccion = MonedaMx.correccionPorApoyo(
            etiquetaPrincipal = "10pesos", confianzaPrincipal = 0.782f,
            etiquetaApoyo = "5pesos", confianzaApoyo = 0.932f,
            margen = 0.10f, umbral = 0.85f,
        )
        assertEquals(MotivoMoneda.DUDOSA, contradiccion?.motivo)
        assertEquals(DenominacionMoneda.M5, contradiccion?.denominacion)
        assertTrue(
            MonedaMx.fraseParaFotoGeneral(contradiccion!!)
                .startsWith("Parece una moneda de cinco pesos, pero no estoy segura."),
        )
    }
}
