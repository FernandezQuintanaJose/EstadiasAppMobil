package com.cecapi.app.feature.modulo6_aprendizaje

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.navigation.CecapiDestinations
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceState
import com.cecapi.app.core.voice.VoiceText
import com.cecapi.app.feature.modulo7_entorno.EnvironmentRepository
import com.cecapi.app.feature.modulo7_entorno.NombreColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class FaseJuego { BUSCANDO, ANALIZANDO, RESULTADO }

data class JuegoCamaraUiState(
    val nivel: Int = 1,
    val reto: Reto = RetosCamara.nuevo(1),
    /** Aciertos en el nivel actual; al llegar a [RetosCamara.RETOS_POR_NIVEL] se sube de nivel. */
    val aciertosEnNivel: Int = 0,
    val aciertosTotales: Int = 0,
    val fase: FaseJuego = FaseJuego.BUSCANDO,
    val veredicto: Veredicto? = null,
    val fotoPath: String? = null,
    /** La caja misteriosa: lo que la persona cree que es, antes de enseñarlo a la cámara. */
    val adivinanza: String? = null,
)

/**
 * "Busca y encuentra": la voz da un reto, la persona busca la cosa y la enseña a la cámara, y la app dice
 * si lo logró (color en el centro de la foto, o el objeto que reconoce el detector). Es práctica: por ahora
 * no suma puntos a la clasificación.
 */
@HiltViewModel
class JuegoCamaraViewModel @Inject constructor(
    savedStateHandle: androidx.lifecycle.SavedStateHandle,
    private val voiceEngine: VoiceEngine,
    private val cues: FeedbackCues,
    private val entorno: EnvironmentRepository,
) : ViewModel() {

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    /** "adivina" es La caja misteriosa: se toca una cosa sin verla, se dice qué es y la cámara lo confirma. */
    val adivina: Boolean = savedStateHandle.get<String>("modo") == "adivina"

    private val _estado = MutableStateFlow(
        if (adivina) JuegoCamaraUiState(reto = Reto(0, RETO_ADIVINA)) else JuegoCamaraUiState(),
    )
    val estado: StateFlow<JuegoCamaraUiState> = _estado.asStateFlow()

    private val _capturas = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val capturas: SharedFlow<Unit> = _capturas

    private val _back = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val back: SharedFlow<Unit> = _back

    init {
        viewModelScope.launch {
            voiceEngine.speechFor(CecapiDestinations.JUEGO_CAMARA).collect { onSpeech(it.text) }
        }
        if (adivina) {
            voiceEngine.speak("La caja misteriosa. $RETO_ADIVINA", listenAfter = true)
        } else voiceEngine.speak(
            "Busca y encuentra. Te doy un reto, tú buscas la cosa y me la enseñas con la cámara. Cuando la tengas, " +
                "toca el botón o di ya lo tengo. ${_estado.value.reto.frase}",
            listenAfter = true,
        )
    }

    private fun onSpeech(hablado: String) {
        val texto = VoiceText.normalize(hablado)
        fun has(vararg palabras: String) = VoiceText.hasAny(texto, *palabras)
        when {
            has("atras", "volver", "vuelve", "regresa", "regresar", "salir", "menu") -> _back.tryEmit(Unit)
            _estado.value.fase == FaseJuego.ANALIZANDO -> Unit
            has("ya lo tengo", "listo", "ya", "toma la foto", "foto", "tomar foto") -> {
                if (_estado.value.fase == FaseJuego.BUSCANDO) pedirFoto()
            }
            has("siguiente", "otro", "otro reto", "nuevo", "sigue") -> siguienteReto()
            has("repite", "repetir", "otra vez", "cual era", "que busco") -> repetirReto()
            adivina && _estado.value.fase == FaseJuego.BUSCANDO && texto.isNotBlank() &&
                !has("siguiente", "repite", "comandos", "ayuda") -> adivinar(hablado)
            has("lista de comandos", "comandos", "ayuda") -> voiceEngine.speak(
                "Di ya lo tengo para tomar la foto, repite para oír el reto otra vez, siguiente para cambiar de reto, o atrás para salir.",
                listenAfter = true,
            )
            else -> voiceEngine.speak("No entendí. Di ya lo tengo, repite, siguiente, o atrás.", listenAfter = true)
        }
    }

    fun onMicTapped() = voiceEngine.startListening()

    fun onMicDoubleTap() = voiceEngine.mute()

    fun onCommandsRequested() = onSpeech("ayuda")

    fun pedirFoto() {
        if (_estado.value.fase != FaseJuego.BUSCANDO) return
        voiceEngine.stopSpeaking()
        _capturas.tryEmit(Unit)
    }

    fun onFalloDeCaptura() {
        cues.play(FeedbackCues.Cue.ERROR)
        voiceEngine.speak("No pude tomar la foto. Revisa que la cámara esté libre e inténtalo otra vez.", listenAfter = true)
    }

    fun repetirReto() = voiceEngine.speak(_estado.value.reto.frase, listenAfter = true)

    /** La foto ya está tomada: se mira el color del centro y, según el reto, lo que reconoce la cámara. */
    fun onFoto(uri: Uri, ruta: String) {
        if (adivina) return adivinarConFoto(uri, ruta)
        val reto = _estado.value.reto
        _estado.value = _estado.value.copy(fase = FaseJuego.ANALIZANDO, fotoPath = ruta, veredicto = null)
        voiceEngine.speak("Veamos.")
        viewModelScope.launch {
            val veredicto = try {
                val color = withContext(Dispatchers.Default) { colorDelCentro(ruta) }
                // Solo los retos con objeto necesitan el detector (es lo más lento); los de color, no.
                val escena = if (reto.objeto != null) {
                    entorno.processCapturedPhoto(null, uri, ruta).getOrNull()?.let { r ->
                        listOf(r.descripcion).plus(r.etiquetas).joinToString(". ")
                    }.orEmpty()
                } else {
                    ""
                }
                RetosCamara.evaluar(reto, color, escena)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("CecapiJuego", "no se pudo analizar la foto", e)
                Veredicto(false, "No pude analizar la foto. Inténtalo otra vez.")
            }
            terminarRonda(veredicto)
        }
    }

    private fun terminarRonda(veredicto: Veredicto) {
        val actual = _estado.value
        var nivel = actual.nivel
        var aciertosEnNivel = actual.aciertosEnNivel
        var aviso = ""
        if (veredicto.acerto) {
            aciertosEnNivel++
            if (aciertosEnNivel >= RetosCamara.RETOS_POR_NIVEL && nivel < RetosCamara.NIVEL_MAXIMO) {
                nivel++
                aciertosEnNivel = 0
                aviso = " ¡Subiste al nivel $nivel!"
            }
        }
        _estado.value = actual.copy(
            fase = FaseJuego.RESULTADO,
            veredicto = veredicto,
            nivel = nivel,
            aciertosEnNivel = aciertosEnNivel,
            aciertosTotales = actual.aciertosTotales + if (veredicto.acerto) 1 else 0,
        )
        cues.play(if (veredicto.acerto) FeedbackCues.Cue.SUCCESS else FeedbackCues.Cue.ERROR)
        voiceEngine.speak("${veredicto.mensaje}$aviso Di siguiente para otro reto, o repite.", listenAfter = true)
    }

    /** Lo que la persona cree que es (por voz o escrito). */
    fun adivinar(texto: String) {
        val limpio = texto.trim()
        if (limpio.isEmpty()) return
        _estado.value = _estado.value.copy(adivinanza = limpio)
        voiceEngine.speak("Dijiste: $limpio. Ahora enséñamelo a la cámara y di ya lo tengo.", listenAfter = true)
    }

    private fun adivinarConFoto(uri: Uri, ruta: String) {
        val dicho = _estado.value.adivinanza
        if (dicho == null) {
            voiceEngine.speak("Primero dime qué crees que es.", listenAfter = true)
            return
        }
        _estado.value = _estado.value.copy(fase = FaseJuego.ANALIZANDO, fotoPath = ruta, veredicto = null)
        voiceEngine.speak("Veamos si adivinaste.")
        viewModelScope.launch {
            val escena = try {
                entorno.processCapturedPhoto(null, uri, ruta).getOrNull()?.let { r -> listOf(r.descripcion).plus(r.etiquetas).joinToString(". ") }.orEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("CecapiJuego", "no se pudo analizar la foto", e)
                ""
            }
            terminarRonda(veredictoDeAdivinanza(dicho, escena))
        }
    }

    /** Un reto nuevo; si todavía no se logró el anterior, se cambia por otro del mismo nivel. */
    fun siguienteReto() {
        val actual = _estado.value
        if (actual.fase == FaseJuego.ANALIZANDO) return
        if (adivina) {
            _estado.value = actual.copy(fase = FaseJuego.BUSCANDO, veredicto = null, fotoPath = null, adivinanza = null)
            voiceEngine.speak("Otra cosa. $RETO_ADIVINA", listenAfter = true)
            return
        }
        val nuevo = RetosCamara.nuevo(actual.nivel)
        _estado.value = actual.copy(reto = nuevo, fase = FaseJuego.BUSCANDO, veredicto = null, fotoPath = null)
        voiceEngine.speak(nuevo.frase, listenAfter = true)
    }

    /** El color que domina la mitad central de la foto, o null si no hay uno claro. */
    private fun colorDelCentro(ruta: String): String? {
        val opciones = BitmapFactory.Options().apply { inSampleSize = 4 }
        val mapa = BitmapFactory.decodeFile(ruta, opciones) ?: return null
        try {
            val ancho = mapa.width
            val alto = mapa.height
            val paso = maxOf(1, minOf(ancho, alto) / 60)
            val muestra = ArrayList<Int>()
            var y = alto / 4
            while (y < alto - alto / 4) {
                var x = ancho / 4
                while (x < ancho - ancho / 4) {
                    muestra += mapa.getPixel(x, y)
                    x += paso
                }
                y += paso
            }
            return NombreColor.dominante(muestra.toIntArray(), 0.35f)?.nombre
        } finally {
            mapa.recycle()
        }
    }

    companion object {
        const val RETO_ADIVINA =
            "Toca una cosa sin verla, dime qué crees que es, y luego enséñala a la cámara y di ya lo tengo."

        /** Adivinó si alguna palabra de lo que dijo (sin artículos) aparece en lo que reconoció la cámara. */
        fun veredictoDeAdivinanza(dicho: String, escena: String): Veredicto {
            val vacias = setOf("una", "unas", "unos", "creo", "que", "es", "son", "algo", "como", "parece", "esto", "este", "esta")
            val palabras = RetosCamara.limpiar(dicho).split(Regex("[^a-zñ]+")).filter { it.length >= 3 && it !in vacias }
            val texto = RetosCamara.limpiar(escena)
            val acerto = palabras.any { palabra -> palabra in texto || palabra.removeSuffix("s") in texto }
            val visto = escena.trim().ifEmpty { "nada claro" }
            return if (acerto) {
                Veredicto(true, "¡Adivinaste! Yo veo: $visto")
            } else {
                Veredicto(false, "Tú dijiste $dicho. Yo veo: $visto")
            }
        }
    }
}
