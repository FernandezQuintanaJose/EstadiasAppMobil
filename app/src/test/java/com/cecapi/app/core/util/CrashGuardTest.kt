package com.cecapi.app.core.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashGuardTest {
    @Test
    fun laPrimeraFallaReabreLaApp() {
        assertTrue(CrashGuard.debeReiniciar(ahoraMs = 50_000L, ultimaFallaMs = 0L))
    }

    @Test
    fun unaFallaDespuesDeMuchoTiempoReabreLaApp() {
        assertTrue(CrashGuard.debeReiniciar(ahoraMs = 100_000L, ultimaFallaMs = 50_000L))
    }

    @Test
    fun dosFallasSeguidasNoEntranEnCiclo() {
        assertFalse(CrashGuard.debeReiniciar(ahoraMs = 55_000L, ultimaFallaMs = 50_000L))
    }

    @Test
    fun justoEnElLimiteYaSePuedeReabrir() {
        assertTrue(CrashGuard.debeReiniciar(ahoraMs = 60_000L, ultimaFallaMs = 50_000L))
    }
}
