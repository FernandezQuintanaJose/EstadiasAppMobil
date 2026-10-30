package com.cecapi.app.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cecapi.app.core.theme.CecapiTheme
import com.cecapi.app.core.theme.Sections

/**
 * Vistas previas de la pantalla dividida (contenido que se desplaza + micrófono) en celular y
 * tablet, en vertical y horizontal. Sirven para revisar el reparto sin correr la app: en Android
 * Studio, abrir este archivo y ver el panel "Split" o "Design".
 */
@Composable
private fun PantallaDeMuestra() {
    CecapiTheme {
        PantallaConMicrofono {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                repeat(10) { n ->
                    BigBtn(icon = Icons.Filled.Settings, label = "Módulo ${n + 1}", section = Sections.Camera, onClick = {})
                }
            }
            MicPad(listening = false, hint = "Di el nombre o toca aquí", onClick = {})
        }
    }
}

@Preview(name = "Celular vertical", device = "spec:width=360dp,height=800dp", showBackground = true)
@Composable
private fun CelularVertical() = PantallaDeMuestra()

@Preview(name = "Celular horizontal", device = "spec:width=800dp,height=360dp", showBackground = true)
@Composable
private fun CelularHorizontal() = PantallaDeMuestra()

@Preview(name = "Tablet vertical", device = "spec:width=800dp,height=1280dp", showBackground = true)
@Composable
private fun TabletVertical() = PantallaDeMuestra()

@Preview(name = "Tablet horizontal", device = "spec:width=1280dp,height=800dp", showBackground = true)
@Composable
private fun TabletHorizontal() = PantallaDeMuestra()
