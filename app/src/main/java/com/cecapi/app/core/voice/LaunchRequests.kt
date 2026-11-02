package com.cecapi.app.core.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Something outside the app (the home-screen widget) asked it to start listening right away.
 * The first screen reads this instead of giving its usual greeting, so the user hears
 * "¿En qué te puedo ayudar?" and can talk, without a long welcome in between.
 */
@Singleton
class LaunchRequests @Inject constructor() {
    @Volatile
    private var listen = false

    fun requestListen() {
        listen = true
    }

    /** True once per request. */
    fun consumeListen(): Boolean = listen.also { listen = false }

    private val _openRoute = MutableStateFlow<String?>(null)

    /** Una pantalla que un widget pidió abrir (por ejemplo Actividades); la navegación la atiende y la limpia. */
    val openRoute: StateFlow<String?> = _openRoute.asStateFlow()

    fun requestOpen(route: String) {
        _openRoute.value = route
    }

    fun consumeOpen() {
        _openRoute.value = null
    }
}
