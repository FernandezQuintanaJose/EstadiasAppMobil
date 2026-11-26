package com.cecapi.app.core.politicas

/** Una parte de las políticas: se lee y se escucha por separado. */
data class ParteDePolitica(val titulo: String, val parrafos: List<String>) {
    val textoParaVoz: String get() = "$titulo. " + parrafos.joinToString(" ")
}

/**
 * Políticas de uso y privacidad. BORRADOR del 26 nov 2026, a revisar por un asesor legal. El texto es
 * el mismo de PaginaAsistenteCECAPI/politicas.html: si cambia allá, cambiarlo aquí y subir [VERSION]
 * (quien ya aceptó tendrá que aceptar de nuevo). Lo que va entre corchetes está pendiente de CECAPI.
 */
object Politicas {
    const val VERSION = "2026-11-26-borrador"

    /** Todas las partes, numeradas, para leerlas de corrido en voz alta. */
    fun textoCompletoParaVoz(): String =
        partes.mapIndexed { i, p -> "Parte ${i + 1} de ${partes.size}. ${p.textoParaVoz}" }.joinToString(" ")

    val partes: List<ParteDePolitica> = listOf(
        ParteDePolitica(
            "Qué es el asistente",
            listOf(
                "ALIA responde preguntas en español usando inteligencia artificial. Funciona en la página web, en la app para Android y en la app de escritorio para Windows.",
                "Las apps se descargan solo desde la página del asistente o desde GitHub: el archivo APK para Android y el archivo .exe para Windows. No están en Google Play ni en la Microsoft Store.",
                "Es solo para personas de 18 años o más.",
            ),
        ),
        ParteDePolitica(
            "Quién es responsable de tus datos",
            listOf("Responsable: [nombre, domicilio y correo de contacto, por definir con CECAPI]."),
        ),
        ParteDePolitica(
            "Tu cuenta",
            listOf(
                "Para usar el asistente creas una cuenta con un nombre de usuario, un correo electrónico y una contraseña. Después puedes entrar con tu correo o con tu nombre de usuario. No se usa el acceso con Google.",
                "Al crear la cuenta confirmas que tienes 18 años o más.",
                "Te enviamos un código al correo para confirmar tu registro y para recuperar tu contraseña. El código sirve solo para eso.",
                "Tu contraseña se guarda cifrada, no tal como la escribes.",
            ),
        ),
        ParteDePolitica(
            "Qué datos usamos",
            listOf(
                "Tu nombre de usuario, tu correo y tu contraseña cifrada.",
                "Las preguntas que escribes o dices y las respuestas que recibes.",
                "La fecha en que aceptas estas políticas y la versión que aceptaste.",
                "Cuántas preguntas haces al día, para repartir el uso entre todas las personas.",
                "En la app para Android, si activas el servicio de accesibilidad de ALIA, la app puede leer el contenido de la pantalla y tomar una captura temporal para leer su texto o describirla en voz alta. Esa captura se analiza en el teléfono, no se guarda y no se envía al servidor.",
            ),
        ),
        ParteDePolitica(
            "Datos de salud y datos personales en tus preguntas",
            listOf(
                "No te pedimos datos de salud. No los escribas ni los dictes en tus preguntas, porque los datos sobre tu salud o una discapacidad se consideran sensibles.",
                "El asistente bloquea las preguntas que traen correo, teléfono, número de tarjeta, CURP, RFC o contraseñas, y no las manda a la inteligencia artificial. No detecta nombres, domicilios ni datos de salud: si los escribes, sí se envían.",
            ),
        ),
        ParteDePolitica(
            "Para qué los usamos",
            listOf(
                "Para tu cuenta, para responder tus preguntas, para que cada persona tenga un límite diario justo de uso y para guardar constancia de que aceptaste estas políticas.",
                "No usamos tus datos para publicidad y no los vendemos.",
            ),
        ),
        ParteDePolitica(
            "Con quién se comparten",
            listOf(
                "Para generar la respuesta, tu pregunta se envía a uno de dos servicios de inteligencia artificial: Gemini, de Google, o Groq.",
                "Usamos la versión gratuita de Gemini. En esa versión, Google puede usar el contenido de las preguntas para mejorar sus productos.",
                "El servidor del asistente funciona en Render y la base de datos en Neon. Estos servicios pueden estar fuera de México. [Ubicación por confirmar].",
                "Para enviarte los códigos de registro y de recuperación se usa Resend, un servicio de envío de correo. Recibe tu dirección de correo y el mensaje con el código. También puede estar fuera de México.",
                "En la app para Android, si activas la búsqueda en Wikipedia, que viene apagada, el texto de una frase que la app no entiende o de una pregunta sencilla se envía a Wikipedia o Wikidata.",
            ),
        ),
        ParteDePolitica(
            "Cuánto tiempo guardamos los datos",
            listOf(
                "Guardamos tus datos y tus chats mientras tu cuenta exista. No los borramos por inactividad.",
                "Se eliminan cuando borras tu cuenta o cuando nos lo pides.",
                "Desde el asistente puedes borrar tus chats guardados y eliminar tu cuenta con todos tus datos.",
            ),
        ),
        ParteDePolitica(
            "Tus derechos",
            listOf(
                "Puedes pedir acceso a tus datos, corregirlos, cancelarlos u oponerte a su uso. También puedes retirar tu consentimiento.",
                "Para hacerlo, escribe a [correo de contacto, por confirmar con CECAPI].",
            ),
        ),
        ParteDePolitica(
            "Límites del asistente",
            listOf(
                "Las respuestas las genera una inteligencia artificial y pueden tener errores. No sustituyen la opinión de un médico, un abogado u otro profesional.",
                "En una emergencia, llama al 911.",
            ),
        ),
        ParteDePolitica(
            "Cambios a estas políticas",
            listOf("Si estas políticas cambian, te las volveremos a mostrar y tendrás que aceptarlas de nuevo para seguir usando el asistente."),
        ),
    )
}

/**
 * Reglas de la lectura de las políticas (sin interfaz, para poder probarlas): se avanza por partes,
 * y solo se puede aceptar después de llegar a la última y declarar 18 años o más. Pasar las partes
 * sin leerlas no cuenta como aceptar: hay que pulsar Aceptar.
 */
class LectorDePoliticas(private val total: Int = Politicas.partes.size) {
    var indice = 0
        private set
    private var maximoVisto = 0
    var mayorDeEdad = false

    val esUltima: Boolean get() = indice == total - 1
    val llegoAlFinal: Boolean get() = maximoVisto >= total - 1

    /** Se leyó todo en voz alta de corrido: cuenta como haber llegado al final. */
    fun irAlFinal() {
        indice = total - 1
        maximoVisto = total - 1
    }

    fun siguiente(): Boolean {
        if (esUltima) return false
        indice++
        maximoVisto = maxOf(maximoVisto, indice)
        return true
    }

    fun anterior(): Boolean {
        if (indice == 0) return false
        indice--
        return true
    }

    /** null si se puede aceptar; si no, la frase que explica qué falta. */
    fun faltaParaAceptar(): String? = when {
        !llegoAlFinal -> "Para aceptar, primero llega a la última parte. Vas en la parte ${indice + 1} de $total."
        !mayorDeEdad -> "Para aceptar, marca la declaración de que tienes 18 años o más."
        else -> null
    }
}
