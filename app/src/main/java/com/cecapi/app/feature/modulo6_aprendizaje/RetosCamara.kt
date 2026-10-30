package com.cecapi.app.feature.modulo6_aprendizaje

import java.text.Normalizer
import kotlin.random.Random

/** Un reto del juego "Busca y encuentra": algo que la persona debe encontrar y mostrar a la cámara. */
data class Reto(
    val nivel: Int,
    /** Lo que se dice y se muestra: "Busca algo de color rojo y ponlo frente a la cámara." */
    val frase: String,
    val color: String? = null,
    /** Cómo se llama lo que se busca ("botella"), para encontrarlo en lo que la cámara reconoce. */
    val objeto: String? = null,
)

/** Si la foto cumple el reto y qué decirle a la persona. */
data class Veredicto(val acerto: Boolean, val mensaje: String)

/**
 * Los retos y la forma de calificarlos. Sin nada de Android, para poder probarlo aparte.
 *  - Nivel 1: encontrar algo rojo.
 *  - Nivel 2: encontrar algo de otro color (azul, verde, amarillo...).
 *  - Nivel 3: encontrar un objeto por su nombre.
 *  - Nivel 4: un objeto de un color ("una botella azul").
 * Cada [RETOS_POR_NIVEL] aciertos se sube de nivel.
 */
object RetosCamara {
    const val RETOS_POR_NIVEL = 3
    const val NIVEL_MAXIMO = 4

    private val COLORES = listOf("rojo", "azul", "verde", "amarillo", "naranja", "blanco", "negro")

    /** (cómo se reconoce, cómo se dice con artículo). Solo objetos que el detector de la cámara sabe nombrar. */
    private val OBJETOS = listOf(
        "botella" to "una botella",
        "taza" to "una taza",
        "libro" to "un libro",
        "reloj" to "un reloj",
        "tijeras" to "unas tijeras",
        "teléfono" to "un teléfono",
        "mochila" to "una mochila",
        "silla" to "una silla",
        "llaves" to "unas llaves",
        "manzana" to "una manzana",
    )

    fun nuevo(nivel: Int, random: Random = Random.Default): Reto = when (nivel.coerceIn(1, NIVEL_MAXIMO)) {
        1 -> Reto(1, "Busca algo de color rojo y ponlo frente a la cámara, que llene el centro.", color = "rojo")
        2 -> COLORES.filter { it != "rojo" }.random(random).let {
            Reto(2, "Busca algo de color $it y ponlo frente a la cámara, que llene el centro.", color = it)
        }
        3 -> OBJETOS.random(random).let { (clave, conArticulo) ->
            Reto(3, "Busca $conArticulo y enséñalo a la cámara.", objeto = clave)
        }
        else -> {
            val (clave, conArticulo) = OBJETOS.random(random)
            val color = COLORES.random(random)
            Reto(4, "Busca $conArticulo de color $color y enséñalo a la cámara.", color = color, objeto = clave)
        }
    }

    /**
     * @param colorVisto el color que domina el centro de la foto, o null si no hay uno claro.
     * @param escena lo que la cámara reconoció en la foto (la descripción y las etiquetas), en español.
     */
    fun evaluar(reto: Reto, colorVisto: String?, escena: String): Veredicto {
        val texto = limpiar(escena)
        val tieneObjeto = reto.objeto?.let { limpiar(it) in texto }
        val tieneColor = when {
            reto.color == null -> null
            reto.objeto == null -> colorVisto != null && limpiar(colorVisto) == limpiar(reto.color)
            // "roja" y "rojo" son el mismo color: se compara la raíz.
            else -> (colorVisto != null && limpiar(colorVisto) == limpiar(reto.color)) || limpiar(reto.color).removeSuffix("o") in texto
        }
        val acerto = (tieneObjeto ?: true) && (tieneColor ?: true)
        val mensaje = when {
            acerto && reto.objeto == null -> "¡Muy bien! Vi el color ${reto.color}."
            acerto -> "¡Lo encontraste! ${escena.trim().ifEmpty { "Lo vi en la foto." }}"
            reto.objeto == null && colorVisto != null -> "Vi el color $colorVisto, no ${reto.color}. Prueba con otra cosa."
            reto.objeto == null -> "No vi un color claro. Acerca la cosa a la cámara, que llene el centro."
            escena.isBlank() -> "No pude ver nada claro. Acerca la cámara e inténtalo otra vez."
            else -> "No vi lo que buscas. Vi: ${escena.trim()}"
        }
        return Veredicto(acerto, mensaje)
    }

    /** Minúsculas y sin acentos, para comparar "teléfono" con "telefono celular". */
    fun limpiar(texto: String): String =
        Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
}
