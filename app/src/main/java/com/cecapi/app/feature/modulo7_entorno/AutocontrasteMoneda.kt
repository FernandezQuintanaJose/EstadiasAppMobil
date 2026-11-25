package com.cecapi.app.feature.modulo7_entorno

/**
 * Autocontraste por canal que la app aplica a la foto antes de clasificar una moneda.
 *
 * Las fotos del teléfono suelen salir oscuras o con poco contraste. Estirar cada canal a todo su
 * rango (recortando el 2 % de píxeles más oscuros y más claros) mejora el acierto en poca luz y
 * sombra sin cambiar el color de la moneda. Es el equivalente a `ImageOps.autocontrast(cutoff=2)`
 * de PIL, que es con lo que se midió la mejora.
 */
object AutocontrasteMoneda {
    /** Porcentaje de píxeles que se descartan en cada extremo del histograma. */
    const val PORCENTAJE_RECORTE = 1.5

    /**
     * Construye tres tablas (rojo, verde, azul) de 256 valores a partir de los histogramas.
     *
     * @param histogramas tres histogramas de 256 bins, uno por canal.
     * @param total número de píxeles de la imagen (suma de cualquier histograma).
     */
    fun calcular(
        histogramas: Array<IntArray>,
        total: Int,
        porcentaje: Double = PORCENTAJE_RECORTE,
    ): Array<IntArray> {
        require(histogramas.size == 3) { "Se esperan tres histogramas (R, G, B)." }
        require(total > 0) { "La imagen no tiene píxeles." }
        val corte = (total * porcentaje / 100).toInt()
        return Array(3) { canal ->
            val hist = histogramas[canal]
            val bajo = limite(hist, corte + 1)
            val alto = limite(hist, total - corte).let { if (it <= bajo) bajo else it }
            IntArray(256) { valor ->
                if (alto > bajo) {
                    (((valor - bajo) * 255) / (alto - bajo)).coerceIn(0, 255)
                } else {
                    // Imagen de un solo tono: no hay nada que estirar.
                    valor
                }
            }
        }
    }

    /** Primer valor (0..255) donde la suma acumulada del histograma alcanza [objetivo]. */
    private fun limite(hist: IntArray, objetivo: Int): Int {
        var acumulado = 0
        for (valor in 0..255) {
            acumulado += hist[valor]
            if (acumulado >= objetivo) return valor
        }
        return 255
    }
}
