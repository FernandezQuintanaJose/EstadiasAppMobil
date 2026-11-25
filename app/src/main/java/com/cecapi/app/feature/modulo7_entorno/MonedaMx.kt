package com.cecapi.app.feature.modulo7_entorno

/** Denominaciones de las monedas mexicanas que reconoce la app y cómo se dicen en voz alta. */
enum class DenominacionMoneda(val valor: Int, val nombreHablado: String) {
    M1(1, "un peso"),
    M2(2, "dos pesos"),
    M5(5, "cinco pesos"),
    M10(10, "diez pesos"),
    M20(20, "veinte pesos"),
}

/** Por qué el resultado es el que es: de eso depende lo que se dice en voz alta. */
enum class MotivoMoneda {
    /** Se reconoció con confianza alta y sin duda entre dos valores. */
    SEGURA,

    /** Hay una candidata, pero la confianza no alcanza para decirla como un hecho. */
    DUDOSA,

    /** El modelo no se decide por ninguna clase. */
    NO_CLARO,

    /** El modelo dice que en la foto no hay una moneda (clase opcional "sin_moneda"). */
    NO_HAY_MONEDA,

    /** El modelo dice que hay varias monedas juntas (clase opcional "varias"). */
    VARIAS,

    /** Falta el archivo del modelo (assets/monedas.tflite): todavía no se ha entrenado o instalado. */
    SIN_MODELO,
}

/**
 * @property denominacion la candidata; solo se puede decir como un hecho si [esSegura].
 * @property confianza la probabilidad que dio el modelo a [denominacion] (0 a 1).
 * @property esRescate true si la candidata salió de la rejilla de recortes de último recurso. En una
 *   foto general un rescate no nombra la denominación: la rejilla acierta poco y una duda débil no
 *   debe sonar a un valor. La petición explícita sí puede decir "parece…".
 */
data class ResultadoMoneda(
    val denominacion: DenominacionMoneda?,
    val confianza: Float,
    val motivo: MotivoMoneda,
    val esRescate: Boolean = false,
) {
    val esSegura: Boolean get() = motivo == MotivoMoneda.SEGURA && denominacion != null
}

/**
 * Decide qué moneda es a partir de lo que devuelve el clasificador de imágenes (un modelo TFLite entrenado con
 * fotos de monedas). No lee números ni usa OCR: solo mira las probabilidades del modelo.
 *
 * Las clases del modelo son los nombres de las carpetas de entrenamiento: `1peso`, `2pesos`, `5pesos`,
 * `10pesos`, `20pesos` y, opcionales, `sin_moneda` y `varias`. De cada nombre se toma el número.
 */
object MonedaMx {
    const val ETIQUETA_SIN_MONEDA = "sin_moneda"
    const val ETIQUETA_VARIAS = "varias"

    // ---- Umbrales ----------------------------------------------------------------------------------------
    // Calibrados con las fotos únicas del entrenamiento (incluidas manos, sombra, luz interior y poca
    // luz). Con UMBRAL_SEGURA=0.75 el modelo acertó el 99.5% de las veces que dijo "segura"; con 0.65
    // acertaba el 97.7%. Como el modelo local es la única opción, conviene ser conservador: mejor
    // decir "parece" que afirmar una moneda equivocada.
    //
    // La app clasifica 3 variantes de la misma foto (completa, espejo y recorte central) y promedia. Se dice
    // "segura" solo si el promedio es alto Y las variantes están de acuerdo; si el promedio es alto pero las
    // variantes se contradicen, el modelo está adivinando.

    /** Moneda pedida ("qué moneda es"): probabilidad promedio mínima para decirla como un hecho. */
    const val UMBRAL_SEGURA = 0.75f

    /** Moneda pedida: cuántas de las 3 variantes deben coincidir en la misma moneda. */
    const val ACUERDO_MINIMO = 2

    /**
     * Foto normal ("qué hay enfrente"): nadie pidió una moneda y el modelo solo conoce monedas (siempre elige
     * alguna, aunque la foto sea de una laptop). Por eso aquí se exige mucho más antes de interrumpir la
     * descripción del entorno con "Es una moneda de ...".
     */
    const val UMBRAL_SEGURA_AUTOMATICA = 0.95f

    /** Foto normal: además del promedio, CADA variante debe dar al menos esto a esa misma moneda. */
    const val UMBRAL_VARIANTE_AUTOMATICA = 0.70f

    /** Por debajo de esto ni siquiera se dice "parece". */
    const val UMBRAL_DUDOSA = 0.60f

    /**
     * Cuando las 3 variantes no se deciden, la app prueba una rejilla de recortes (la moneda puede estar
     * pequeña o descentrada). El rescate solo se acepta si la rejilla coincide con la clase que ya insinuaba
     * el promedio y alcanza esta confianza; siempre se dice como duda ("parece"), nunca como hecho.
     */
    const val UMBRAL_RESCATE = 0.55f

    /** Probabilidad mínima para creerle al modelo que no hay moneda o que hay varias. */
    const val UMBRAL_CLASE_ESPECIAL = 0.60f

    // Solo nombres como "5pesos", "1peso", "20 pesos" o "$10". Un nombre como "clase_2" (el que se usa si no se
    // pudo leer monedas_labels.txt) NO debe tomarse por "2 pesos": diría una moneda equivocada con seguridad.
    private val FORMATO_ETIQUETA = Regex("""^[$]?(\d+)[ _]?(pesos?)?$""")

    /** La moneda que corresponde al nombre de una clase del modelo ("5pesos" -> 5 pesos), o null. */
    fun desdeEtiqueta(etiqueta: String): DenominacionMoneda? {
        val numero = FORMATO_ETIQUETA.matchEntire(etiqueta.trim().lowercase())
            ?.groupValues?.get(1)?.toIntOrNull() ?: return null
        return DenominacionMoneda.values().firstOrNull { it.valor == numero }
    }

    /**
     * @param lecturas pares (nombre de la clase, probabilidad) del promedio de las variantes, en cualquier orden.
     * @param porVariante lo mismo, pero de cada variante por separado. Vacío = no se sabe, y entonces solo
     *   cuenta el promedio.
     * @param automatico true si es una foto normal en la que nadie pidió una moneda (umbrales más estrictos).
     */
    fun decidir(
        lecturas: List<Pair<String, Float>>,
        porVariante: List<List<Pair<String, Float>>> = emptyList(),
        automatico: Boolean = false,
    ): ResultadoMoneda {
        val ordenadas = lecturas.sortedByDescending { it.second }
        val primera = ordenadas.firstOrNull() ?: return ResultadoMoneda(null, 0f, MotivoMoneda.NO_CLARO)
        val etiqueta = primera.first.trim().lowercase()
        val confianza = primera.second

        if (etiqueta == ETIQUETA_SIN_MONEDA) {
            val motivo = if (confianza >= UMBRAL_CLASE_ESPECIAL) MotivoMoneda.NO_HAY_MONEDA else MotivoMoneda.NO_CLARO
            return ResultadoMoneda(null, confianza, motivo)
        }
        if (etiqueta == ETIQUETA_VARIAS) {
            val motivo = if (confianza >= UMBRAL_CLASE_ESPECIAL) MotivoMoneda.VARIAS else MotivoMoneda.NO_CLARO
            return ResultadoMoneda(null, confianza, motivo)
        }

        val d = desdeEtiqueta(etiqueta) ?: return ResultadoMoneda(null, confianza, MotivoMoneda.NO_CLARO)

        // Qué dice cada variante sobre esta misma moneda, y por cuál se decidió cada una.
        val deEstaMoneda = porVariante.map { v -> v.firstOrNull { it.first.trim().lowercase() == etiqueta }?.second ?: 0f }
        val votos = porVariante.count { v -> v.maxByOrNull { it.second }?.first?.trim()?.lowercase() == etiqueta }
        val concuerdan = when {
            porVariante.isEmpty() -> true
            automatico -> votos == porVariante.size && deEstaMoneda.all { it >= UMBRAL_VARIANTE_AUTOMATICA }
            else -> votos >= minOf(ACUERDO_MINIMO, porVariante.size)
        }
        val umbral = if (automatico) UMBRAL_SEGURA_AUTOMATICA else UMBRAL_SEGURA

        return when {
            confianza >= umbral && concuerdan -> ResultadoMoneda(d, confianza, MotivoMoneda.SEGURA)
            confianza >= UMBRAL_DUDOSA -> ResultadoMoneda(d, confianza, MotivoMoneda.DUDOSA)
            else -> ResultadoMoneda(null, confianza, MotivoMoneda.NO_CLARO)
        }
    }

    /**
     * Convierte el resultado de la rejilla de recortes en una duda. Se usa solo cuando las 3 variantes
     * normales no se decidieron: sirve para decir "parece" en vez de "no logré reconocer". Nunca
     * devuelve [MotivoMoneda.SEGURA], aunque la confianza sea alta.
     */
    fun dudosa(etiqueta: String, confianza: Float, esRescate: Boolean = false): ResultadoMoneda {
        val limpia = etiqueta.trim().lowercase()
        if (limpia == ETIQUETA_SIN_MONEDA) {
            return ResultadoMoneda(null, confianza, MotivoMoneda.NO_HAY_MONEDA, esRescate)
        }
        if (limpia == ETIQUETA_VARIAS) return ResultadoMoneda(null, confianza, MotivoMoneda.VARIAS, esRescate)
        val d = desdeEtiqueta(limpia) ?: return ResultadoMoneda(null, confianza, MotivoMoneda.NO_CLARO, esRescate)
        return ResultadoMoneda(d, confianza, MotivoMoneda.DUDOSA, esRescate)
    }

    /**
     * Decide si el modelo de apoyo debe corregir la denominación de una duda del principal.
     *
     * Solo corrige cuando el apoyo ve **otra** moneda, está muy seguro ([umbral]) y supera al
     * principal por [margen]. La corrección siempre es [MotivoMoneda.DUDOSA]: una segunda opinión
     * puede cambiar la candidata, pero nunca afirmar la moneda como un hecho. Devuelve null si no
     * hay corrección.
     */
    fun correccionPorApoyo(
        etiquetaPrincipal: String,
        confianzaPrincipal: Float,
        etiquetaApoyo: String,
        confianzaApoyo: Float,
        margen: Float,
        umbral: Float,
    ): ResultadoMoneda? {
        val principal = etiquetaPrincipal.trim().lowercase()
        val apoyo = etiquetaApoyo.trim().lowercase()
        if (apoyo == principal) return null
        if (apoyo == ETIQUETA_SIN_MONEDA || apoyo == ETIQUETA_VARIAS) return null
        if (confianzaApoyo < umbral) return null
        if (confianzaApoyo < confianzaPrincipal + margen) return null
        val d = desdeEtiqueta(apoyo) ?: return null
        return ResultadoMoneda(d, confianzaApoyo, MotivoMoneda.DUDOSA)
    }

    /** Qué dice en voz alta la app. Una duda nunca se dice como un hecho. */
    fun frase(r: ResultadoMoneda): String = when {
        r.esSegura && r.denominacion != null -> "Es una moneda de ${r.denominacion.nombreHablado}."
        r.motivo == MotivoMoneda.DUDOSA && r.denominacion != null ->
            "Parece una moneda de ${r.denominacion.nombreHablado}, pero no estoy segura. " +
                "Ponla sola sobre una superficie lisa, con buena luz, y pregunta otra vez."
        r.motivo == MotivoMoneda.VARIAS ->
            "Veo varias monedas juntas. Muéstramelas de una en una para reconocerlas."
        r.motivo == MotivoMoneda.NO_HAY_MONEDA ->
            "No veo una moneda. Ponla sola frente a la cámara, con buena luz, y pregunta otra vez."
        r.motivo == MotivoMoneda.SIN_MODELO ->
            "Todavía no tengo instalado el modelo de monedas. Avisa al equipo para que lo agregue."
        else ->
            "No logré reconocer la moneda. Ponla sola sobre una superficie lisa, con buena luz, y pregunta otra vez."
    }

    /**
     * Frase para una foto general en la que la IA local detectó una moneda. Como nadie pidió la
     * denominación, solo se afirma cuando el clasificador está seguro; una duda se dice con
     * "parece", que advierte sin asegurar. No se dice "no veo una moneda": la IA ya la vio y el
     * problema es no poder leer de cuánto es.
     *
     * En las fotos generales una duda solo llega aquí si viene del modelo principal o de una
     * corrección del apoyo con confianza alta (≥ 0.85); el apoyo no puede introducir una
     * denominación por una ventaja pequeña. Un rescate de la rejilla nunca nombra la denominación:
     * se queda en "Veo una moneda, pero no pude reconocer de cuánto es", porque la rejilla es el
     * último recurso y acierta poco.
     */
    fun fraseParaFotoGeneral(r: ResultadoMoneda): String = when {
        r.esSegura -> frase(r)
        r.motivo == MotivoMoneda.DUDOSA && r.denominacion != null && !r.esRescate -> frase(r)
        r.motivo == MotivoMoneda.VARIAS -> frase(r)
        else ->
            "Veo una moneda, pero no pude reconocer de cuánto es. " +
                "Ponla sola sobre una superficie lisa, con buena luz, y pregunta otra vez."
    }
}
