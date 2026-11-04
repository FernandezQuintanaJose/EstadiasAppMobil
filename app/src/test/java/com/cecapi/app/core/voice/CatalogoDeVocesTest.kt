package com.cecapi.app.core.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Una voz que el teléfono no puede usar nunca debe quedar activa: una persona ciega se quedaría sin voz. */
class CatalogoDeVocesTest {

    private val local = VozDelMotor("es-mx-local", "México", necesitaInternet = false, sinInstalar = false)
    private val otra = VozDelMotor("es-mx-otra", "México", necesitaInternet = false, sinInstalar = false)
    private val red = VozDelMotor("es-mx-red", "México", necesitaInternet = true, sinInstalar = false)
    private val sinDescargar = VozDelMotor("es-es-falta", "España", necesitaInternet = false, sinInstalar = true)
    private val redSinDescargar = VozDelMotor("es-es-red-falta", "España", necesitaInternet = true, sinInstalar = true)

    private fun armar(vararg voces: VozDelMotor, hayInternet: Boolean = true) =
        CatalogoDeVoces.armar(voces.toList(), hayInternet, conocidas = emptyList())

    @Test
    fun numeraPorNombreSinImportarQueVocesFuncionan() {
        val voces = armar(red, sinDescargar, local)
        assertEquals(listOf("es-es-falta", "es-mx-local", "es-mx-red"), voces.map { it.name })
        assertEquals(listOf("Voz 1", "Voz 2", "Voz 3"), voces.map { it.label })
        assertEquals(listOf(EstadoVoz.NO_DISPONIBLE, EstadoVoz.LISTA, EstadoVoz.LISTA), voces.map { it.estado })
    }

    @Test
    fun laMismaVozTieneElMismoNumeroEnTodosLosTelefonos() {
        val conocidas = listOf(local, otra, red).map { it.copy(sinInstalar = true) }
        val conTodas = CatalogoDeVoces.armar(listOf(local, otra, red), hayInternet = true, conocidas = conocidas)
        val faltaUna = CatalogoDeVoces.armar(listOf(local, red), hayInternet = true, conocidas = conocidas)
        assertEquals(conTodas.map { it.name to it.numero }, faltaUna.map { it.name to it.numero })
        assertEquals(EstadoVoz.NO_DISPONIBLE, faltaUna.first { it.name == otra.nombre }.estado)
    }

    @Test
    fun elIdiomaEnteroNoEsUnaVozMas() {
        val alias = listOf("es-ES-language", "es-US-language").map { local.copy(nombre = it) }
        assertEquals(listOf("es-mx-local"), CatalogoDeVoces.armar(alias + local, hayInternet = true, conocidas = emptyList()).map { it.name })
    }

    @Test
    fun lasConocidasSonLasDelOppoA57() {
        val nombres = CatalogoDeVoces.VOCES_CONOCIDAS.map { it.nombre }
        assertEquals(16, nombres.size)
        assertEquals(nombres.size, nombres.toSet().size)
        assertTrue(nombres.all { it.startsWith("es-es-x-") || it.startsWith("es-us-x-") })
        assertTrue(CatalogoDeVoces.VOCES_CONOCIDAS.all { it.necesitaInternet == it.nombre.endsWith("-network") })
    }

    @Test
    fun filtrarNoCambiaLosNumeros() {
        val voces = armar(local, red, sinDescargar, redSinDescargar)
        assertEquals(voces, CatalogoDeVoces.filtrar(voces, FiltroVoces.TODAS))
        assertEquals(listOf("Voz 1", "Voz 3"), CatalogoDeVoces.filtrar(voces, FiltroVoces.SIN_INTERNET).map { it.label })
        assertEquals(listOf("Voz 2", "Voz 4"), CatalogoDeVoces.filtrar(voces, FiltroVoces.CON_INTERNET).map { it.label })
    }

    @Test
    fun laVozSinDescargarNoSePuedeActivar() {
        val voz = armar(sinDescargar).single()
        assertEquals(EstadoVoz.NO_DISPONIBLE, voz.estado)
        assertEquals("No disponible", CatalogoDeVoces.textoEstado(voz))
        assertNotNull(CatalogoDeVoces.motivoDeBloqueo(voz))
        assertFalse(CatalogoDeVoces.sePuedeUsar(sinDescargar, hayInternet = true))
    }

    @Test
    fun sinDescargarGanaAunqueHayaInternet() {
        assertEquals(EstadoVoz.NO_DISPONIBLE, armar(redSinDescargar, hayInternet = true).single().estado)
        assertEquals(EstadoVoz.NO_DISPONIBLE, armar(redSinDescargar, hayInternet = false).single().estado)
    }

    @Test
    fun laVozDeInternetSeBloqueaSoloSinConexion() {
        assertNull(CatalogoDeVoces.motivoDeBloqueo(armar(red, hayInternet = true).single()))
        val sinRed = armar(red, hayInternet = false).single()
        assertEquals(EstadoVoz.SIN_CONEXION, sinRed.estado)
        assertNotNull(CatalogoDeVoces.motivoDeBloqueo(sinRed))
        assertFalse(CatalogoDeVoces.sePuedeUsar(red, hayInternet = false))
    }

    @Test
    fun laVozLocalFuncionaSinConexion() {
        assertEquals(EstadoVoz.LISTA, armar(local, hayInternet = false).single().estado)
    }

    @Test
    fun laAutomaticaSiempreSePuede() {
        assertNull(CatalogoDeVoces.motivoDeBloqueo(emptyList(), ""))
    }

    @Test
    fun noSeActivaPorNombreLoQueNoSePuede() {
        val voces = armar(local, sinDescargar)
        assertNull(CatalogoDeVoces.motivoDeBloqueo(voces, "es-mx-local"))
        assertNotNull(CatalogoDeVoces.motivoDeBloqueo(voces, "es-es-falta"))
        assertNotNull(CatalogoDeVoces.motivoDeBloqueo(voces, "voz-que-no-existe"))
        assertNotNull(CatalogoDeVoces.motivoDeBloqueo(emptyList(), "es-mx-local"))
    }

    @Test
    fun otraVozSaltaLasQueNoSePueden() {
        val voces = armar(local, otra, sinDescargar, red, hayInternet = false)
        assertEquals("es-mx-otra", CatalogoDeVoces.siguiente(voces, "es-mx-local", 1)?.name)
        assertEquals("es-mx-local", CatalogoDeVoces.siguiente(voces, "es-mx-otra", 1)?.name)
        assertEquals("es-mx-otra", CatalogoDeVoces.siguiente(voces, "es-mx-local", -1)?.name)
    }

    @Test
    fun otraVozDesdeAutomaticaOVozPerdida() {
        val voces = armar(local, otra)
        assertEquals("es-mx-local", CatalogoDeVoces.siguiente(voces, "", 1)?.name)
        assertEquals("es-mx-otra", CatalogoDeVoces.siguiente(voces, "", -1)?.name)
        assertEquals("es-mx-local", CatalogoDeVoces.siguiente(voces, "ya-no-existe", 1)?.name)
    }

    @Test
    fun otraVozSinNadaUsableNoDevuelveNada() {
        assertNull(CatalogoDeVoces.siguiente(armar(sinDescargar), "", 1))
        assertNull(CatalogoDeVoces.siguiente(emptyList(), "", 1))
    }

    @Test
    fun lasConocidasQueFaltanSalenComoNoDisponibles() {
        val voces = CatalogoDeVoces.armar(listOf(local), hayInternet = true, conocidas = listOf(sinDescargar))
        assertEquals(listOf(EstadoVoz.NO_DISPONIBLE, EstadoVoz.LISTA), voces.map { it.estado })
    }

    @Test
    fun loQueReportaElTelefonoGanaALaConocida() {
        val conocida = local.copy(sinInstalar = true)
        val voces = CatalogoDeVoces.armar(listOf(local), hayInternet = true, conocidas = listOf(conocida))
        assertEquals(EstadoVoz.LISTA, voces.single().estado)
    }

    @Test
    fun descartaNombresVaciosYRepetidos() {
        assertEquals(1, armar(local, local, local.copy(nombre = " ")).size)
    }

    @Test
    fun laListaDeConocidasNuncaSePuedeActivar() {
        CatalogoDeVoces.VOCES_CONOCIDAS.forEach { assertTrue(it.sinInstalar) }
    }

    @Test
    fun laDescripcionDiceTodoLoQueSeVe() {
        assertEquals("Voz 1. España. Sin internet. No disponible.", CatalogoDeVoces.descripcion(armar(sinDescargar).single()))
        assertEquals("Voz 1. México. Con internet. Sin conexión.", CatalogoDeVoces.descripcion(armar(red, hayInternet = false).single()))
        assertEquals("Voz 1. México. Sin internet.", CatalogoDeVoces.descripcion(armar(local).single()))
    }

    @Test
    fun entiendeElNumeroDeVoz() {
        assertEquals(3, CatalogoDeVoces.numeroPedido("usa la voz 3"))
        assertEquals(3, CatalogoDeVoces.numeroPedido("voz tres"))
        assertEquals(10, CatalogoDeVoces.numeroPedido("voz diez"))
        assertNull(CatalogoDeVoces.numeroPedido("voz grave"))
        assertNull(CatalogoDeVoces.numeroPedido("voz anterior"))
        assertNull(CatalogoDeVoces.numeroPedido("otra voz"))
    }

    @Test
    fun entiendeElFiltroSinConfundirlos() {
        assertEquals(FiltroVoces.SIN_INTERNET, CatalogoDeVoces.filtroPedido("voces sin internet"))
        assertEquals(FiltroVoces.CON_INTERNET, CatalogoDeVoces.filtroPedido("voces con internet"))
        assertEquals(FiltroVoces.TODAS, CatalogoDeVoces.filtroPedido("todas las voces"))
        assertNull(CatalogoDeVoces.filtroPedido("no tengo internet"))
    }

    @Test
    fun elFiltroDaLaVuelta() {
        assertEquals(FiltroVoces.SIN_INTERNET, FiltroVoces.TODAS.siguiente())
        assertEquals(FiltroVoces.CON_INTERNET, FiltroVoces.SIN_INTERNET.siguiente())
        assertEquals(FiltroVoces.TODAS, FiltroVoces.CON_INTERNET.siguiente())
    }
}
