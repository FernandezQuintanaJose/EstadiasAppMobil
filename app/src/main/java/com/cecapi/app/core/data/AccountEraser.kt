package com.cecapi.app.core.data

import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Borra la cuenta de una persona y todo lo que depende de ella: sus chats, resultados, documentos y
 * descripciones guardadas (la base los borra junto con la cuenta), y las fotos suyas que el lector y el
 * asistente de entorno guardaron en el teléfono. No se conserva nada: esto es el "borrar mi cuenta y mis datos"
 * que pide la política de privacidad. Las fotos se borran primero, porque sus rutas viven en filas que se van con la cuenta.
 */
@Singleton
class AccountEraser @Inject constructor(
    private val database: AppDatabase,
) {
    suspend fun erase(userId: Long) = withContext(Dispatchers.IO) {
        val paths = mutableListOf<String>()
        PHOTO_TABLES.forEach { table ->
            database.query(SimpleSQLiteQuery("SELECT ruta_imagen FROM $table WHERE usuario_id = ?", arrayOf<Any>(userId)))
                .use { cursor ->
                    while (cursor.moveToNext()) cursor.getString(0)?.let(paths::add)
                }
        }
        paths.forEach { runCatching { File(it).delete() } }
        // Toda tabla que pertenece a una persona apunta a `usuarios` con ON DELETE CASCADE.
        database.openHelper.writableDatabase.execSQL("DELETE FROM usuarios WHERE id = ?", arrayOf<Any>(userId))
        // El borrado se hizo por fuera de las consultas propias de Room, así que hay que avisar a las pantallas que observan las tablas.
        database.invalidationTracker.refreshVersionsAsync()
    }

    private companion object {
        val PHOTO_TABLES = listOf("documentos_escaneados", "escaneos_entorno")
    }
}
