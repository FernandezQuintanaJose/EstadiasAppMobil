package com.cecapi.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class EvaluadorDeCargaTest {
    @Test fun todoNormalNoMolesta() {
        assertEquals(NivelCarga.NORMAL, EvaluadorDeCarga.nivel(false, 0.4f, 0, 0))
    }

    @Test fun elCalorSeveroAvisaYElCriticoPide() {
        assertEquals(NivelCarga.ALTA, EvaluadorDeCarga.nivel(false, 0.4f, 0, 3))
        assertEquals(NivelCarga.CRITICA, EvaluadorDeCarga.nivel(false, 0.4f, 0, 4))
    }

    @Test fun poquitaMemoriaDelSistemaEsCritica() {
        assertEquals(NivelCarga.CRITICA, EvaluadorDeCarga.nivel(true, 0.2f, 0, 0))
    }

    @Test fun elHeapDeLaAppSeEscalona() {
        assertEquals(NivelCarga.ALTA, EvaluadorDeCarga.nivel(false, 0.88f, 0, 0))
        assertEquals(NivelCarga.CRITICA, EvaluadorDeCarga.nivel(false, 0.95f, 0, 0))
    }

    @Test fun losAvisosDeMemoriaDeSegundoPlanoNoCuentan() {
        assertEquals(NivelCarga.ALTA, EvaluadorDeCarga.nivel(false, 0.2f, 10, 0))
        assertEquals(NivelCarga.CRITICA, EvaluadorDeCarga.nivel(false, 0.2f, 15, 0))
        assertEquals(NivelCarga.NORMAL, EvaluadorDeCarga.nivel(false, 0.2f, 40, 0))
        assertEquals(NivelCarga.NORMAL, EvaluadorDeCarga.nivel(false, 0.2f, 80, 0))
    }
}
