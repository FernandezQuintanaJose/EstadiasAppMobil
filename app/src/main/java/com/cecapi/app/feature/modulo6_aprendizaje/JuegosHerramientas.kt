package com.cecapi.app.feature.modulo6_aprendizaje

import kotlin.random.Random

/**
 * Los juegos que el teléfono puede acompañar. Los individuales se juegan con el teléfono; los de grupo
 * (goalball, ajedrez, tándem...) se juegan en persona y el teléfono hace de herramienta: marcador, turnos,
 * ritmo o guía por voz.
 */
enum class JuegoTipo(val id: String, val titulo: String, val comoSeJuega: String) {
    CAZA_SONIDO(
        "caza_sonido", "Caza el sonido",
        "Alguien esconde el teléfono; cuando empieza a sonar, búscalo guiándote por el oído y toca Lo encontré.",
    ),
    MEMORIA_SONIDOS(
        "memoria_sonidos", "Memoria de sonidos",
        "Escucha un sonido del día a día y di qué es. Hay cuatro opciones en cada pregunta.",
    ),
    MARCADOR(
        "marcador", "Marcador y tiempo",
        "Lleva los puntos de dos equipos y el tiempo del partido, y los dice en voz alta.",
    ),
    TURNOS(
        "turnos", "Turnos",
        "Avisa en voz alta de quién es el turno y cuánto tiempo queda, para juegos de mesa por turnos.",
    ),
    ESTACIONES(
        "estaciones", "Estaciones",
        "Avisa cuándo cambiar de estación y a cuál pasar, para que todos pasen por todas.",
    ),
    RITMO(
        "ritmo", "Ritmo",
        "Marca el ritmo con un sonido, para pedalear en tándem o saltar en el trampolín al mismo paso.",
    ),
    GUIA(
        "guia", "Guía por voz",
        "Un compañero toca adelante, izquierda, derecha o alto, y el teléfono lo dice fuerte para guiarte.",
    ),
    ;

    companion object {
        fun desdeId(id: String?): JuegoTipo = entries.firstOrNull { it.id == id } ?: MEMORIA_SONIDOS
    }
}

/** Un sonido del banco de Actividades con el nombre que la persona debe adivinar. */
data class SonidoCotidiano(val archivo: String, val nombre: String, val palabras: List<String>)

/** Una pregunta de Memoria de sonidos: el sonido que suena y las cuatro opciones (una es la correcta). */
data class PreguntaMemoria(val sonido: SonidoCotidiano, val opciones: List<SonidoCotidiano>)

object MemoriaSonidos {
    /** Los sonidos que ya tiene la app (res/raw), con sus nombres y otras formas de decirlos. */
    val SONIDOS = listOf(
        SonidoCotidiano("tambor", "Tambor", listOf("tambor", "bombo", "bateria")),
        SonidoCotidiano("aplauso", "Aplausos", listOf("aplauso", "aplausos", "palmadas", "palmas")),
        SonidoCotidiano("campana", "Campana", listOf("campana", "campanita", "timbre")),
        SonidoCotidiano("clic", "Clic", listOf("clic", "click", "chasquido", "boton")),
        SonidoCotidiano("motor", "Motor", listOf("motor", "carro", "coche", "auto", "moto")),
        SonidoCotidiano("pasos", "Pasos", listOf("pasos", "caminar", "caminando", "alguien camina")),
        SonidoCotidiano("silbido", "Silbido", listOf("silbido", "chiflido", "silbar", "chiflar")),
        SonidoCotidiano("tarareo", "Alguien tarareando", listOf("tarareo", "tararear", "cantar", "canto", "tarareando")),
    )

    const val PREGUNTAS_POR_RONDA = 8

    /** Una ronda: cada sonido una vez, en desorden, con tres opciones falsas al azar. */
    fun ronda(random: Random = Random.Default): List<PreguntaMemoria> =
        SONIDOS.shuffled(random).take(PREGUNTAS_POR_RONDA).map { sonido ->
            val otras = SONIDOS.filter { it != sonido }.shuffled(random).take(3)
            PreguntaMemoria(sonido, (otras + sonido).shuffled(random))
        }

    /** Qué opción nombró la persona, o null si no dijo ninguna de las cuatro. */
    fun reconocer(dicho: String, opciones: List<SonidoCotidiano>): SonidoCotidiano? {
        val texto = RetosCamara.limpiar(dicho)
        return opciones.firstOrNull { opcion -> opcion.palabras.any { it in texto } }
    }
}

/** El marcador de un partido de dos equipos. */
data class Marcador(val equipo1: Int = 0, val equipo2: Int = 0) {
    fun sumar(equipo: Int) = if (equipo == 1) copy(equipo1 = equipo1 + 1) else copy(equipo2 = equipo2 + 1)

    /** Quita un punto, sin bajar de cero (para corregir un error). */
    fun restar(equipo: Int) =
        if (equipo == 1) copy(equipo1 = (equipo1 - 1).coerceAtLeast(0)) else copy(equipo2 = (equipo2 - 1).coerceAtLeast(0))

    fun enPalabras(): String = when {
        equipo1 == equipo2 -> "Empate a $equipo1."
        equipo1 > equipo2 -> "Gana el equipo uno, $equipo1 a $equipo2."
        else -> "Gana el equipo dos, $equipo2 a $equipo1."
    }
}

/** El tiempo en palabras: "2 minutos y 5 segundos". */
fun tiempoEnPalabras(segundos: Int): String {
    val m = segundos / 60
    val s = segundos % 60
    return when {
        m == 0 -> "$s segundos"
        s == 0 -> if (m == 1) "1 minuto" else "$m minutos"
        else -> (if (m == 1) "1 minuto" else "$m minutos") + " y $s segundos"
    }
}

/** El tiempo como reloj: "02:05". */
fun tiempoEnReloj(segundos: Int): String = "%02d:%02d".format(segundos / 60, segundos % 60)
