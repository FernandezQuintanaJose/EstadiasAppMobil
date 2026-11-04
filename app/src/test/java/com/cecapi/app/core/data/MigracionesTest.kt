package com.cecapi.app.core.data

import android.app.Application
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Las actualizaciones de la base del teléfono (decisión 0004). Corren en la computadora, sin teléfono. Los esquemas
 * viejos de `app/schemas` se sacaron de cada commit que cambió la versión: 8, 10, 11, 12, 14 y 15 nunca se subieron.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MigracionesTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private fun actualizar(nombre: String, desde: Int): SupportSQLiteDatabase {
        helper.createDatabase(nombre, desde).close()
        return helper.runMigrationsAndValidate(nombre, VERSION_ACTUAL, true, *TODAS_LAS_MIGRACIONES)
    }

    /** Llena lo obligatorio que no se pasó, para no repetir todas las columnas que tenía cada versión. */
    private fun insertar(db: SupportSQLiteDatabase, tabla: String, valores: ContentValues): Long {
        db.query("PRAGMA table_info($tabla)").use { c ->
            while (c.moveToNext()) {
                val columna = c.getString(c.getColumnIndexOrThrow("name"))
                val obligatoria = c.getInt(c.getColumnIndexOrThrow("notnull")) == 1
                val esLlave = c.getInt(c.getColumnIndexOrThrow("pk")) > 0
                val conDefecto = !c.isNull(c.getColumnIndexOrThrow("dflt_value"))
                if (obligatoria && !esLlave && !conDefecto && !valores.containsKey(columna)) {
                    when (c.getString(c.getColumnIndexOrThrow("type")).uppercase()) {
                        "TEXT" -> valores.put(columna, "")
                        else -> valores.put(columna, 0)
                    }
                }
            }
        }
        return db.insert(tabla, SQLiteDatabase.CONFLICT_ABORT, valores)
    }

    private fun persona(nombre: String) = ContentValues().apply {
        put("nombre_usuario", nombre)
        put("contrasena_hash", "hash-de-$nombre")
        put("nombre_completo", "Nombre de $nombre")
    }

    private fun SupportSQLiteDatabase.texto(sql: String): String? =
        query(sql).use { if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else null }

    private fun SupportSQLiteDatabase.numero(sql: String): Long =
        query(sql).use { it.moveToFirst(); it.getLong(0) }

    @Test
    fun laCadenaNoTieneHuecos() {
        assertEquals((1 until VERSION_ACTUAL).toList(), TODAS_LAS_MIGRACIONES.map { it.startVersion })
        TODAS_LAS_MIGRACIONES.forEach { assertEquals(it.startVersion + 1, it.endVersion) }
    }

    @Test
    fun cadaVersionPublicadaLlegaALaActualConElEsquemaCorrecto() {
        VERSIONES_PUBLICADAS.forEach { version -> actualizar("desde-$version.db", version).close() }
    }

    @Test
    fun desdeLaPrimeraVersionLasCuentasSobreviven() {
        helper.createDatabase("cuentas.db", 1).use { db ->
            insertar(db, "usuarios", persona("ana"))
            insertar(db, "usuarios", persona("cecapi"))
        }
        helper.runMigrationsAndValidate("cuentas.db", VERSION_ACTUAL, true, *TODAS_LAS_MIGRACIONES).use { db ->
            assertEquals("hash-de-ana", db.texto("SELECT contrasena_hash FROM usuarios WHERE nombre_usuario = 'ANA'"))
            assertEquals("usuario", db.texto("SELECT rol FROM usuarios WHERE nombre_usuario = 'ANA'"))
            assertEquals("administrador", db.texto("SELECT rol FROM usuarios WHERE nombre_usuario = 'CECAPI'"))
        }
    }

    @Test
    fun reconstruirUsuariosNoBorraLoQueDependeDeCadaCuenta() {
        helper.createDatabase("reconstruir.db", 16).use { db ->
            val id = insertar(db, "usuarios", persona("ANA"))
            insertar(
                db, "consultas_ia",
                ContentValues().apply {
                    put("usuario_id", id)
                    put("texto_pregunta", "¿Quién fue Braille?")
                    put("fecha_hora", 1L)
                },
            )
        }
        helper.runMigrationsAndValidate("reconstruir.db", 17, true, *TODAS_LAS_MIGRACIONES).use { db ->
            assertEquals(1, db.numero("SELECT COUNT(*) FROM usuarios"))
            assertEquals("¿Quién fue Braille?", db.texto("SELECT texto_pregunta FROM consultas_ia"))
            db.query("PRAGMA foreign_key_check").use { assertFalse("Hay filas huérfanas", it.moveToFirst()) }
        }
    }

    @Test
    fun soloSeBorranLosPuntosDePruebaSinTocar() {
        helper.createDatabase("puntos.db", 17).use { db ->
            val juan = insertar(db, "usuarios", persona("JUAN"))
            val miriam = insertar(db, "usuarios", persona("MIRIAM"))
            val ana = insertar(db, "usuarios", persona("ANA"))
            fun avance(id: Long, nivel: Int, puntos: Int) = insertar(
                db, "niveles_aprendizaje",
                ContentValues().apply {
                    put("usuario_id", id)
                    put("nivel_actual", nivel)
                    put("puntos_totales", puntos)
                },
            )
            avance(juan, 3, 450) // el sembrado: se va
            avance(miriam, 5, 900) // ya jugó: se queda
            avance(ana, 3, 450) // mismos números, otra persona: se queda
        }
        helper.runMigrationsAndValidate("puntos.db", 18, true, *TODAS_LAS_MIGRACIONES).use { db ->
            assertEquals(2, db.numero("SELECT COUNT(*) FROM niveles_aprendizaje"))
            assertEquals(0, db.numero("SELECT COUNT(*) FROM niveles_aprendizaje n JOIN usuarios u ON u.id = n.usuario_id WHERE u.nombre_usuario = 'JUAN'"))
        }
    }

    @Test
    fun lasCuentasAnterioresNoQuedanComoSiHubieranConfirmadoSer18() {
        helper.createDatabase("adulto.db", 18).use { db -> insertar(db, "usuarios", persona("ANA")) }
        helper.runMigrationsAndValidate("adulto.db", 19, true, *TODAS_LAS_MIGRACIONES).use { db ->
            assertNull(db.texto("SELECT acepto_adulto_en FROM usuarios"))
            assertTrue(db.numero("SELECT COUNT(*) FROM usuarios") == 1L)
        }
    }

    private companion object {
        const val VERSION_ACTUAL = 19
        val VERSIONES_PUBLICADAS = listOf(1, 2, 3, 4, 5, 6, 7, 9, 13, 16, 17, 18)
    }
}
