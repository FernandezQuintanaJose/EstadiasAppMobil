package com.cecapi.app.core.politicas

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Guarda en el teléfono qué versión de las políticas aceptó la persona y cuándo. Si la versión cambia,
 * [aceptada] vuelve a ser falsa y se muestran otra vez. Solo se guarda la versión y la fecha.
 */
@Singleton
class AceptacionPoliticas @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs get() = context.getSharedPreferences("politicas", Context.MODE_PRIVATE)

    val aceptada: Boolean get() = prefs.getString(CLAVE_VERSION, null) == Politicas.VERSION

    fun registrar() {
        prefs.edit()
            .putString(CLAVE_VERSION, Politicas.VERSION)
            .putString(CLAVE_FECHA, Instant.now().toString())
            .apply()
    }

    private companion object {
        const val CLAVE_VERSION = "version_aceptada"
        const val CLAVE_FECHA = "fecha_aceptacion"
    }
}
