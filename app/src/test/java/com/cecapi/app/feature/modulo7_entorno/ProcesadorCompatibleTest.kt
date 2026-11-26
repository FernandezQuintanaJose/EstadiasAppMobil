package com.cecapi.app.feature.modulo7_entorno

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcesadorCompatibleTest {
    @Test
    fun cortexA53NoAdmiteElModelo() {
        val cpuinfo = "processor\t: 0\nFeatures\t: fp asimd evtstrm aes pmull sha1 sha2 crc32 cpuid\n"
        assertFalse(ProcesadorCompatible.admiteModeloLocal(cpuinfo))
    }

    @Test
    fun armv82SiLoAdmite() {
        val linea = "Features\t: fp asimd aes crc32 atomics fphp asimdhp cpuid asimdrdm lrcpc dcpop asimddp\n"
        assertTrue(ProcesadorCompatible.admiteModeloLocal(linea + linea))
    }

    @Test
    fun unSoloNucleoSinLasInstruccionesLoDescarta() {
        val cpuinfo = "Features\t: fp asimd asimdhp asimddp\nFeatures\t: fp asimd\n"
        assertFalse(ProcesadorCompatible.admiteModeloLocal(cpuinfo))
    }

    @Test
    fun sinInformacionNoSeArriesga() {
        assertFalse(ProcesadorCompatible.admiteModeloLocal(""))
    }
}
