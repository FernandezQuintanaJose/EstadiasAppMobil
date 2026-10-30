package com.cecapi.app.core.navigation

/** Central route registry so every module refers to the same route strings. */
object CecapiDestinations {
    const val HOME = "home"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val DASHBOARD = "dashboard"
    const val SETTINGS = "settings"
    const val PERSONALIZATION = "personalization"
    const val CHATS = "chats"

    /** The "Cámara" card: one entry that leads to reading text or describing what is in front. */

    const val VOICE_ASSISTANT = "voice_assistant"
    const val AI_ASSISTANT = "ai_assistant"
    const val DOCUMENT_READER = "document_reader"
    const val REQUESTS = "requests"
    const val LEARNING = "learning"
    const val JUEGO_CAMARA = "juego_camara?modo={modo}"
    const val JUEGO = "juego/{tipo}"

    /** La ruta de un juego que acompaña el teléfono. */
    fun juego(id: String) = "juego/$id"

    /** El juego con cámara; [modo] "adivina" es La caja misteriosa. */
    fun juegoCamara(modo: String = "busca") = "juego_camara?modo=$modo"
    const val ENVIRONMENT = "environment"

    /** Only for administrador, directivo and educador — never shown or reachable for alumno/usuario. */
    const val GESTION = "gestion"

    /** Only for alumno and usuario — the opposite of GESTION: see their educador, report a problem. */
    const val SOPORTE = "soporte"

    /** Pantallas que necesitan una cuenta: sin sesión no se abren, ni tocándolas ni diciéndolas. */
    val REQUIEREN_SESION = setOf(CHATS, REQUESTS, LEARNING, GESTION, RANKING, EDIT_ACCOUNT)

    /** Individual and institution standings in Actividades. Open to anyone signed in, every role. */
    const val RANKING = "ranking"

    /** Each person's own: name, apodo, whether Clasificación shows one or the other, and password. */
    const val EDIT_ACCOUNT = "edit_account"
}
