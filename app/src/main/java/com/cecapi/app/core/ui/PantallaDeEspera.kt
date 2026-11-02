package com.cecapi.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cecapi.app.core.theme.CecapiBackground
import com.cecapi.app.core.theme.CecapiTextMuted

/** Lo que se dice en voz alta cuando el teléfono va muy apretado; la pantalla muestra lo mismo. */
const val MENSAJE_TELEFONO_CARGADO =
    "El teléfono está muy cargado y puede fallar. Puedes esperar un momento o cerrar la aplicación."

/**
 * Pantalla completa que tapa la app cuando el teléfono está muy corto de memoria o muy caliente. Dos botones
 * grandes, sin nada más: esperar (se oculta unos segundos para ver si se libera) o cerrar la aplicación.
 */
@Composable
fun PantallaDeEspera(onEsperar: () -> Unit, onCerrar: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(CecapiBackground).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("El teléfono está muy cargado", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(
            "Cierra otras aplicaciones o deja que se enfríe. Si sigues, la app puede cerrarse sola.",
            style = MaterialTheme.typography.bodyLarge,
            color = CecapiTextMuted,
            textAlign = TextAlign.Center,
        )
        CargandoConMensaje(titulo = "Esperando…", detalle = "Reviso cada pocos segundos si ya se liberó.")
        BigBtn(icon = Icons.Filled.HourglassTop, label = "Esperar un momento", variant = BigBtnVariant.Accent, onClick = onEsperar)
        BigBtn(icon = Icons.Filled.Close, label = "Cerrar la aplicación", variant = BigBtnVariant.Danger, onClick = onCerrar)
    }
}
