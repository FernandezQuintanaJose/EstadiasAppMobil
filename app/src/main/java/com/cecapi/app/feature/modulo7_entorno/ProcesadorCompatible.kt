package com.cecapi.app.feature.modulo7_entorno

/**
 * `libsmolvlm_jni.so` usa instrucciones de ARMv8.2 (producto punto y FP16). En un procesador sin ellas
 * (por ejemplo Cortex-A53) la app se cerraba con SIGILL al describir una foto, y eso no se puede atrapar
 * con try/catch: hay que comprobarlo antes de cargar la librería.
 */
object ProcesadorCompatible {
    private val NECESARIAS = listOf("asimddp", "asimdhp")

    /** [cpuinfo] es el contenido de /proc/cpuinfo. Todos los núcleos deben anunciar las instrucciones. */
    fun admiteModeloLocal(cpuinfo: String): Boolean {
        val nucleos = cpuinfo.lineSequence()
            .filter { it.startsWith("Features") }
            .map { it.substringAfter(':').trim().split(' ').toSet() }
            .toList()
        return nucleos.isNotEmpty() && nucleos.all { it.containsAll(NECESARIAS) }
    }
}
