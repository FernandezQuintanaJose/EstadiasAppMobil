package com.cecapi.app.core.util

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.PowerManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Qué tan apretado va el teléfono: lo que la app hace con cada nivel está en MainActivity. */
enum class NivelCarga { NORMAL, ALTA, CRITICA }

/** La decisión, sin tocar Android, para poder probarla. */
object EvaluadorDeCarga {
    // Los números son los de ComponentCallbacks2.TRIM_MEMORY_RUNNING_* y PowerManager.THERMAL_STATUS_*.
    private const val TRIM_RUNNING_LOW = 10
    private const val TRIM_RUNNING_CRITICAL = 15
    private const val TRIM_ULTIMO_EN_PRIMER_PLANO = 19
    private const val TERMICO_SEVERO = 3
    private const val TERMICO_CRITICO = 4
    const val HEAP_ALTA = 0.85f
    const val HEAP_CRITICA = 0.93f

    /**
     * @param memoriaBajaDelSistema Android avisa que el teléfono entero está corto de memoria.
     * @param usoDeHeap fracción 0..1 de la memoria que Android le deja a la app.
     * @param trim último aviso de memoria mientras la app está al frente (0 si no hubo).
     * @param termico estado térmico de Android (0 normal ... 6 apagado).
     */
    fun nivel(memoriaBajaDelSistema: Boolean, usoDeHeap: Float, trim: Int, termico: Int): NivelCarga = when {
        memoriaBajaDelSistema || usoDeHeap >= HEAP_CRITICA || trim in TRIM_RUNNING_CRITICAL..TRIM_ULTIMO_EN_PRIMER_PLANO ||
            termico >= TERMICO_CRITICO -> NivelCarga.CRITICA
        usoDeHeap >= HEAP_ALTA || trim in TRIM_RUNNING_LOW until TRIM_RUNNING_CRITICAL || termico >= TERMICO_SEVERO -> NivelCarga.ALTA
        else -> NivelCarga.NORMAL
    }
}

/**
 * Vigila la memoria y el calor mientras la app está al frente. No cierra nada por su cuenta: avisa, y la pantalla
 * ofrece esperar o cerrar. Cerrar a la fuerza sin avisar es peor para quien no ve la pantalla.
 */
@Singleton
class SaludDelDispositivo @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _nivel = MutableStateFlow(NivelCarga.NORMAL)
    val nivel: StateFlow<NivelCarga> = _nivel.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var vigilancia: Job? = null
    @Volatile private var trim = 0
    @Volatile private var trimEn = 0L
    @Volatile private var termico = 0

    private val avisosDeMemoria = object : ComponentCallbacks2 {
        override fun onTrimMemory(level: Int) {
            trim = level
            trimEn = System.currentTimeMillis()
            recalcular()
        }

        override fun onConfigurationChanged(newConfig: Configuration) = Unit

        override fun onLowMemory() {
            trim = ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
            trimEn = System.currentTimeMillis()
            recalcular()
        }
    }

    private var escuchaTermica: Any? = null

    /** Empieza a vigilar; llámalo cuando la app queda al frente. */
    fun iniciar() {
        if (vigilancia != null) return
        context.registerComponentCallbacks(avisosDeMemoria)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val energia = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val escucha = PowerManager.OnThermalStatusChangedListener { estado ->
                termico = estado
                recalcular()
            }
            energia.addThermalStatusListener(escucha)
            escuchaTermica = escucha
            termico = energia.currentThermalStatus
        }
        vigilancia = scope.launch {
            while (true) {
                recalcular()
                delay(INTERVALO_MS)
            }
        }
    }

    /** Deja de vigilar cuando la app sale de pantalla. */
    fun detener() {
        vigilancia?.cancel()
        vigilancia = null
        runCatching { context.unregisterComponentCallbacks(avisosDeMemoria) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            (escuchaTermica as? PowerManager.OnThermalStatusChangedListener)?.let {
                runCatching { (context.getSystemService(Context.POWER_SERVICE) as PowerManager).removeThermalStatusListener(it) }
            }
        }
        escuchaTermica = null
        _nivel.value = NivelCarga.NORMAL
    }

    private fun recalcular() {
        val actividad = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also { actividad.getMemoryInfo(it) }
        val runtime = Runtime.getRuntime()
        val heap = (runtime.totalMemory() - runtime.freeMemory()).toFloat() / runtime.maxMemory().toFloat()
        // Un aviso de memoria solo cuenta unos segundos: después el sistema ya se acomodó.
        val trimVigente = if (System.currentTimeMillis() - trimEn < VIGENCIA_TRIM_MS) trim else 0
        val nuevo = EvaluadorDeCarga.nivel(info.lowMemory, heap, trimVigente, termico)
        // Deja en el registro por qué cambió el nivel (`adb logcat -s CecapiSalud`): solo números del teléfono.
        if (nuevo != _nivel.value) {
            Log.i("CecapiSalud", "nivel=$nuevo lowMemory=${info.lowMemory} heap=${"%.2f".format(heap)} trim=$trimVigente termico=$termico")
        }
        _nivel.value = nuevo
    }

    private companion object {
        const val INTERVALO_MS = 5_000L
        const val VIGENCIA_TRIM_MS = 15_000L
    }
}
