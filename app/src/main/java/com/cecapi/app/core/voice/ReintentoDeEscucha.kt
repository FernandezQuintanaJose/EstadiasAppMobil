package com.cecapi.app.core.voice

import android.speech.SpeechRecognizer

/**
 * Decide si conviene abrir el micrófono otra vez. El reconocedor a veces toma un ruido como el inicio
 * de la voz y cierra en un par de segundos, antes de que la persona termine de hablar.
 * Solo permite un reintento por cada vez que se pide escuchar.
 */
class ReintentoDeEscucha(private val limiteMs: Long = LIMITE_MS) {
    private var usado = false

    /** Una escucha nueva (toque del micrófono o pregunta del asistente): vuelve a haber un reintento. */
    fun reiniciar() {
        usado = false
    }

    /**
     * [msDesdeListo] es el tiempo entre que el micrófono quedó listo y el cierre, o null si nunca quedó listo.
     * Si el cierre tarda más, la persona tuvo tiempo de hablar y no dijo nada: no se insiste.
     */
    fun debeReintentar(error: Int, msDesdeListo: Long?): Boolean {
        if (usado || msDesdeListo == null || msDesdeListo >= limiteMs) return false
        if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) return false
        usado = true
        return true
    }

    companion object {
        /** En el OPPO de pruebas, sin ningún sonido el reconocedor esperó entre 5.1 y 5.8 segundos. */
        const val LIMITE_MS = 4_500L
    }
}
