package com.cecapi.app.core.util

import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import kotlin.system.exitProcess

/**
 * Red de seguridad para que la aplicación no se cierre sin más cuando algo falla dentro de un panel.
 *
 * Una persona que no ve la pantalla no sabe qué pasó cuando la app desaparece. Ante una excepción que nadie
 * atrapó, se anota el error en el registro, se vuelve a abrir la app desde el inicio y, al abrir, el
 * asistente avisa por voz que hubo un problema ([consumirAviso]). Si la app falla otra vez a los pocos
 * segundos de reabrirse, no se reinicia de nuevo: se deja el cierre normal de Android para no quedar en un ciclo.
 */
object CrashGuard {
    private const val PREFS = "crash_guard"
    private const val KEY_ULTIMA_FALLA = "ultima_falla_ms"
    private const val KEY_AVISO = "aviso_pendiente"

    /** Dos fallas con menos de esto entre una y otra se consideran un ciclo: no se reinicia. */
    internal const val VENTANA_CICLO_MS = 10_000L

    /** Verdadero si conviene reabrir la app tras esta falla: no hubo otra hace un momento. */
    internal fun debeReiniciar(ahoraMs: Long, ultimaFallaMs: Long): Boolean =
        ultimaFallaMs <= 0L || ahoraMs - ultimaFallaMs >= VENTANA_CICLO_MS

    fun install(context: Context) {
        val app = context.applicationContext
        val anterior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { hilo, error ->
            Log.e("CecapiCrash", "Falla sin atrapar en ${hilo.name}", error)
            val reiniciada = try {
                reabrir(app)
            } catch (e: Exception) {
                Log.e("CecapiCrash", "No se pudo reabrir la app", e)
                false
            }
            if (reiniciada) {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            } else {
                anterior?.uncaughtException(hilo, error)
            }
        }
    }

    private fun reabrir(app: Context): Boolean {
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ahora = System.currentTimeMillis()
        val reiniciar = debeReiniciar(ahora, prefs.getLong(KEY_ULTIMA_FALLA, 0L))
        // commit y no apply: el proceso se va a terminar enseguida.
        prefs.edit().putLong(KEY_ULTIMA_FALLA, ahora).putBoolean(KEY_AVISO, reiniciar).commit()
        if (!reiniciar) return false
        val intento = app.packageManager.getLaunchIntentForPackage(app.packageName) ?: return false
        app.startActivity(intento.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        return true
    }

    /** True una sola vez después de una reapertura por falla, para decirlo en voz alta al abrir. */
    fun consumirAviso(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pendiente = prefs.getBoolean(KEY_AVISO, false)
        if (pendiente) prefs.edit().putBoolean(KEY_AVISO, false).apply()
        return pendiente
    }
}
