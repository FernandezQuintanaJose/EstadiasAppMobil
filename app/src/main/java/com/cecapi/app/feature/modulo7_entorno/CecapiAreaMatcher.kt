package com.cecapi.app.feature.modulo7_entorno

/**
 * Compara un vector de la foto contra el índice y decide si hay un área confiable.
 *
 * Se usa el promedio de las mejores [CecapiAreaIndex.topK] referencias de cada área para no
 * depender de una sola fotografía. Si el área ganadora no supera el umbral, o no saca el margen
 * suficiente a la segunda, devuelve null.
 */
internal object CecapiAreaMatcher {

    fun buscar(consulta: FloatArray, indice: CecapiAreaIndex): CecapiArea? {
        if (consulta.size != indice.dimension) return null

        var mejorArea: CecapiArea? = null
        var mejorPuntaje = -1f
        var segundoPuntaje = -1f

        for (area in indice.areas) {
            if (area.count <= 0 || area.offset < 0) continue
            val fin = area.offset + area.count
            if (fin > indice.embeddings.size) continue

            val puntajes = FloatArray(area.count)
            for (i in 0 until area.count) {
                puntajes[i] = productoPunto(consulta, indice.embeddings[area.offset + i])
            }
            puntajes.sort()

            val k = minOf(indice.topK.coerceAtLeast(1), puntajes.size)
            var suma = 0f
            for (i in puntajes.size - k until puntajes.size) {
                suma += puntajes[i]
            }
            val puntaje = suma / k

            if (puntaje > mejorPuntaje) {
                segundoPuntaje = mejorPuntaje
                mejorPuntaje = puntaje
                mejorArea = area
            } else if (puntaje > segundoPuntaje) {
                segundoPuntaje = puntaje
            }
        }

        if (mejorArea == null || mejorPuntaje < indice.threshold) return null
        if (segundoPuntaje >= 0f && mejorPuntaje - segundoPuntaje < indice.margin) return null
        return mejorArea
    }

    private fun productoPunto(a: FloatArray, b: FloatArray): Float {
        var suma = 0f
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            suma += a[i] * b[i]
        }
        return suma
    }
}
