package com.cecapi.app.feature.modulo1_aplicacionprincipal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Stop
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.cecapi.app.core.politicas.AceptacionPoliticas
import com.cecapi.app.core.politicas.LectorDePoliticas
import com.cecapi.app.core.politicas.Politicas
import com.cecapi.app.core.theme.CecapiError
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.ui.BigBtn
import com.cecapi.app.core.ui.BigBtnVariant
import com.cecapi.app.core.ui.ColumnaDesplazable
import com.cecapi.app.core.voice.VoiceEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PoliticasViewModel @Inject constructor(
    val voiceEngine: VoiceEngine,
    private val aceptacion: AceptacionPoliticas,
) : ViewModel() {
    fun registrarAceptacion() = aceptacion.registrar()
}

/**
 * Políticas de uso y privacidad por partes. Para aceptar hay que llegar a la última parte y declarar 18 años o más.
 * [obligatoria] es la primera vez: no hay "Volver", solo aceptar o salir de la app.
 */
@Composable
fun PoliticasScreen(
    obligatoria: Boolean,
    onAceptadas: () -> Unit,
    onBack: () -> Unit,
    onRechazar: () -> Unit = {},
    viewModel: PoliticasViewModel = hiltViewModel(),
) {
    val lector = remember { LectorDePoliticas() }
    var cambio by remember { mutableIntStateOf(0) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    // El lector no es estado observable: [cambio] obliga a volver a elegir la parte cuando se avanza, se salta o se lee todo.
    val parte = remember(cambio) { Politicas.partes[lector.indice] }
    val total = Politicas.partes.size
    LaunchedEffect(Unit) {
        viewModel.voiceEngine.speak(
            "Términos de uso y política de privacidad. Son $total partes. Usa el botón Leer esta parte en voz alta, " +
                "o Leer todo desde el principio. Para aceptar, llega a la última parte, declara que tienes 18 años o más y pulsa Acepto.",
        )
    }

    ColumnaDesplazable(
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        espacio = Arrangement.Top,
    ) {
        // El valor de [cambio] hace que Compose vuelva a leer el lector, que no es estado observable.
        @Suppress("UNUSED_EXPRESSION") cambio
        Text("TÉRMINOS DE USO Y PRIVACIDAD", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
        Text(
            "Parte ${lector.indice + 1} de $total: ${parte.titulo}",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 8.dp).semantics { heading() },
        )
        Text(
            "Borrador a revisar por un asesor legal. Lo que va entre corchetes lo define CECAPI.",
            style = MaterialTheme.typography.bodySmall,
            color = CecapiTextMuted,
            modifier = Modifier.padding(top = 8.dp),
        )
        // Quien no ve la pantalla no puede leer: la voz de la app (o TalkBack) lleva toda la lectura con estos botones.
        BigBtn(
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            label = "Leer esta parte en voz alta",
            sub = "Parte ${lector.indice + 1} de $total",
            variant = BigBtnVariant.Accent,
            onClick = { viewModel.voiceEngine.speak("Parte ${lector.indice + 1} de $total. " + parte.textoParaVoz) },
            modifier = Modifier.padding(top = 16.dp),
        )
        BigBtn(
            icon = Icons.Filled.MenuBook,
            label = "Leer todo desde el principio",
            sub = "Son $total partes; avanza solo hasta el final",
            onClick = {
                lector.irAlFinal()
                cambio++
                mensaje = null
                viewModel.voiceEngine.speak(Politicas.textoCompletoParaVoz())
            },
            modifier = Modifier.padding(top = 12.dp),
        )
        BigBtn(
            icon = Icons.Filled.Stop,
            label = "Detener la lectura",
            onClick = { viewModel.voiceEngine.stopSpeaking() },
            modifier = Modifier.padding(top = 12.dp),
        )
        Column(modifier = Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            parte.parrafos.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
        }

        BigBtn(
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            label = if (lector.esUltima) "Última parte" else "Siguiente parte",
            enabled = !lector.esUltima,
            onClick = { if (lector.siguiente()) { cambio++; mensaje = null; viewModel.voiceEngine.speak(Politicas.partes[lector.indice].textoParaVoz) } },
            modifier = Modifier.padding(top = 20.dp),
        )
        if (!lector.esUltima) {
            // Quien no quiere leerlo todo va directo a la declaración y al botón de aceptar; los términos siguen disponibles arriba.
            BigBtn(
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                label = "Saltar al final",
                sub = "Ir directo a aceptar",
                onClick = {
                    lector.irAlFinal()
                    cambio++
                    mensaje = null
                    viewModel.voiceEngine.speak("Última parte. Para aceptar, declara que tienes 18 años o más y pulsa Acepto.")
                },
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        if (lector.indice > 0) {
            BigBtn(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                label = "Parte anterior",
                onClick = { if (lector.anterior()) { cambio++; mensaje = null; viewModel.voiceEngine.speak(Politicas.partes[lector.indice].textoParaVoz) } },
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        BigBtn(
            icon = if (lector.mayorDeEdad) Icons.Filled.Check else Icons.Filled.Person,
            label = if (lector.mayorDeEdad) "Tengo 18 años o más" else "Declaro que tengo 18 años o más",
            sub = if (lector.mayorDeEdad) "Toca otra vez para quitarlo" else "La aplicación es solo para personas adultas",
            variant = if (lector.mayorDeEdad) BigBtnVariant.Success else BigBtnVariant.Neutral,
            onClick = { lector.mayorDeEdad = !lector.mayorDeEdad; cambio++; mensaje = null },
            modifier = Modifier.padding(top = 12.dp),
        )
        mensaje?.let { Text(it, color = CecapiError, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp)) }
        BigBtn(
            icon = Icons.Filled.Check,
            label = "Acepto los términos de uso y la política de privacidad",
            variant = BigBtnVariant.Accent,
            onClick = {
                val falta = lector.faltaParaAceptar()
                if (falta != null) {
                    mensaje = falta
                    viewModel.voiceEngine.speak(falta)
                } else {
                    viewModel.registrarAceptacion()
                    onAceptadas()
                }
            },
            modifier = Modifier.padding(top = 12.dp),
        )
        BigBtn(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            label = if (obligatoria) "No acepto, salir de la aplicación" else "Volver",
            onClick = { if (obligatoria) onRechazar() else onBack() },
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
