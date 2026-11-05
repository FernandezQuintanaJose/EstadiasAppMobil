package com.cecapi.app.core.voice

/** Qué voces se ven en la pantalla de elegir voz. */
enum class FiltroVoces(val etiqueta: String) {
    TODAS("Todas"),
    SIN_INTERNET("Sin internet"),
    CON_INTERNET("Con internet");

    fun siguiente(): FiltroVoces = entries[(ordinal + 1) % entries.size]
}

/** Si la voz se puede activar ahora. Solo [LISTA] se puede. */
enum class EstadoVoz { LISTA, SIN_CONEXION, NO_DISPONIBLE }

/** Lo que el motor de voz dice de una de sus voces, sin depender de Android para poder probarlo. */
data class VozDelMotor(
    val nombre: String,
    val pais: String,
    val necesitaInternet: Boolean,
    val sinInstalar: Boolean,
)

/**
 * Arma las tarjetas de "Voz 1, Voz 2...". No hay una lista oficial de voces de Android: cada teléfono y
 * cada motor de voz tiene las suyas, y solo se saben preguntándole al teléfono (`TextToSpeech.getVoices`).
 * Una voz sin descargar se trata como no disponible porque activarla inicia su descarga, y mientras tanto
 * el motor puede fallar o hablar con otra voz.
 */
object CatalogoDeVoces {

    // Todas las voces conocidas tienen versión local; la de internet solo si el teléfono la reportó (eee y eef no).
    private val SIN_VERSION_DE_INTERNET = setOf("es-es-x-eee", "es-es-x-eef")

    const val MOTOR_GOOGLE = "com.google.android.tts"

    /**
     * Voces vistas en teléfonos reales, para mostrar "No disponible" si este no las tiene. Solo se agregan nombres
     * confirmados en un teléfono: la app los escribe en el registro con la etiqueta `CecapiVoces`
     * (`adb logcat -s CecapiVoces`). Con las voces de cada teléfono, la lista y los números son los mismos en todos.
     *
     * Vistas en: OPPO A57 (CPH2387), Android 12, motor Google (com.google.android.tts), 4 nov 2026. Ahí no hay
     * ninguna voz de México: solo España y Estados Unidos.
     */
    val VOCES_CONOCIDAS: List<VozDelMotor> =
        listOf("es-es-x-eea", "es-es-x-eec", "es-es-x-eed", "es-es-x-eee", "es-es-x-eef").flatMap { voz(it, "España") } +
            listOf("es-us-x-esc", "es-us-x-esd", "es-us-x-esf", "es-us-x-sfb").flatMap { voz(it, "Estados Unidos") }

    private fun voz(base: String, pais: String): List<VozDelMotor> = buildList {
        add(VozDelMotor("$base-local", pais, necesitaInternet = false, sinInstalar = true))
        if (base !in SIN_VERSION_DE_INTERNET) add(VozDelMotor("$base-network", pais, necesitaInternet = true, sinInstalar = true))
    }

    /**
     * Las voces conocidas son de un motor: cada marca puede traer el suyo (Samsung tiene el propio y deja elegir entre
     * el de Samsung y el de Google) y sus voces se llaman distinto. Con otro motor no se inventan tarjetas "No disponible":
     * solo salen las voces que ese teléfono reporta. Se amplía cuando se confirme un motor en un teléfono real.
     */
    fun conocidasDe(motor: String?): List<VozDelMotor> = if (motor == MOTOR_GOOGLE) VOCES_CONOCIDAS else emptyList()

    /** El motor dice el idioma con 2 letras ("es") o, en motores viejos, con 3 ("spa"). */
    fun esEspanol(idioma: String?): Boolean = idioma.equals("es", ignoreCase = true) || idioma.equals("spa", ignoreCase = true)

    /** `es-ES-language` y `es-US-language` son el idioma entero (lo que ya hace "Automática"), no una voz más. */
    private fun esVozPropia(nombre: String) = nombre.isNotBlank() && !nombre.endsWith("-language", ignoreCase = true)

    const val NO_DISPONIBLE = "No disponible"
    const val SIN_CONEXION = "Sin conexión"

    /**
     * Ordenadas por nombre: "Voz 3" es la misma voz en cualquier teléfono que tenga las mismas voces, y una que falta
     * aparece como "No disponible" en su lugar en vez de recorrer los números.
     */
    fun armar(
        delMotor: List<VozDelMotor>,
        hayInternet: Boolean,
        conocidas: List<VozDelMotor> = emptyList(),
    ): List<VoiceOption> =
        (delMotor + conocidas)
            .filter { esVozPropia(it.nombre) }
            .distinctBy { it.nombre }
            .sortedBy { it.nombre }
            .mapIndexed { indice, voz ->
                VoiceOption(
                    name = voz.nombre,
                    label = "Voz ${indice + 1}",
                    needsInternet = voz.necesitaInternet,
                    numero = indice + 1,
                    pais = voz.pais,
                    estado = estado(voz, hayInternet),
                )
            }

    private fun estado(voz: VozDelMotor, hayInternet: Boolean): EstadoVoz = when {
        voz.sinInstalar -> EstadoVoz.NO_DISPONIBLE
        voz.necesitaInternet && !hayInternet -> EstadoVoz.SIN_CONEXION
        else -> EstadoVoz.LISTA
    }

    fun sePuedeUsar(voz: VozDelMotor, hayInternet: Boolean): Boolean = estado(voz, hayInternet) == EstadoVoz.LISTA

    /** Filtrar no cambia los números: la Voz 3 sigue siendo la Voz 3. */
    fun filtrar(voces: List<VoiceOption>, filtro: FiltroVoces): List<VoiceOption> = when (filtro) {
        FiltroVoces.TODAS -> voces
        FiltroVoces.SIN_INTERNET -> voces.filter { !it.needsInternet }
        FiltroVoces.CON_INTERNET -> voces.filter { it.needsInternet }
    }

    /** Por qué no se puede activar esta voz, o null si se puede. */
    fun motivoDeBloqueo(voz: VoiceOption): String? = when (voz.estado) {
        EstadoVoz.LISTA -> null
        EstadoVoz.SIN_CONEXION -> "${voz.label} necesita internet y ahora no hay conexión. Elige otra."
        EstadoVoz.NO_DISPONIBLE -> "${voz.label} no está disponible en este teléfono. Elige otra."
    }

    /** Lo mismo buscando por nombre. El nombre vacío es la voz automática, que siempre se puede. */
    fun motivoDeBloqueo(voces: List<VoiceOption>, nombre: String): String? {
        if (nombre.isEmpty()) return null
        val voz = voces.firstOrNull { it.name == nombre } ?: return "Esa voz no está en este teléfono. Elige otra."
        return motivoDeBloqueo(voz)
    }

    fun textoEstado(voz: VoiceOption): String? = when (voz.estado) {
        EstadoVoz.LISTA -> null
        EstadoVoz.SIN_CONEXION -> SIN_CONEXION
        EstadoVoz.NO_DISPONIBLE -> NO_DISPONIBLE
    }

    fun textoTipo(voz: VoiceOption): String = if (voz.needsInternet) "Con internet" else "Sin internet"

    /** Lo que se escucha al llegar a una tarjeta. Null es la voz automática. */
    fun descripcion(voz: VoiceOption?): String {
        if (voz == null) return "Automática. El teléfono elige la voz."
        val estado = textoEstado(voz)?.let { " $it." }.orEmpty()
        return "${voz.label}. ${voz.pais}. ${textoTipo(voz)}.$estado"
    }

    /** La siguiente voz que sí se puede activar; salta las demás. Null si no queda ninguna. */
    fun siguiente(voces: List<VoiceOption>, actual: String, paso: Int): VoiceOption? {
        val usables = voces.filter { it.estado == EstadoVoz.LISTA }
        if (usables.isEmpty()) return null
        val indice = usables.indexOfFirst { it.name == actual }
        if (indice < 0) return if (paso >= 0) usables.first() else usables.last()
        return usables[(indice + paso).mod(usables.size)]
    }

    /** "usa la voz 3" o "voz tres" en texto ya normalizado. */
    fun numeroPedido(texto: String): Int? {
        val dicho = NUMERO_PEDIDO.find(texto)?.groupValues?.get(1) ?: return null
        return dicho.toIntOrNull() ?: NUMEROS[dicho]
    }

    /** El filtro que se pidió de viva voz, en texto ya normalizado. */
    fun filtroPedido(texto: String): FiltroVoces? = when {
        VoiceText.hasAny(texto, "voces sin internet", "voz sin internet") -> FiltroVoces.SIN_INTERNET
        VoiceText.hasAny(texto, "voces con internet", "voz con internet") -> FiltroVoces.CON_INTERNET
        VoiceText.hasAny(texto, "todas las voces") -> FiltroVoces.TODAS
        else -> null
    }

    private val NUMEROS = mapOf(
        "uno" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5,
        "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9, "diez" to 10,
    )
    private val NUMERO_PEDIDO = Regex("\\bvoz (\\d{1,2}|${NUMEROS.keys.joinToString("|")})\\b")
}
