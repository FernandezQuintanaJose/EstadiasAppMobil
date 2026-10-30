# Pantalla dividida: contenido arriba, micrófono abajo

Fecha: 30 oct 2026. Rama `ajuste/mic-mitad`.

## Qué se quería

En el teléfono en vertical: arriba, el contenido con desplazamiento (todos los módulos); abajo, un poco menos de la mitad de la pantalla, solo el botón del micrófono. Lo mismo en horizontal, y que no se descuadre en tablet.

## Qué estaba mal

El micrófono era un panel que flotaba encima del contenido, colocado con medidas fijas en dp (`MIC_PANEL_ALTO = 279.dp`, `MIC_BAJADA`, un `offset` y `padding(bottom = 230.dp)` o `250.dp` en cada pantalla). Esas medidas no se adaptan al alto del teléfono, al texto grande ni a la rotación, y el panel quedaba más arriba de lo esperado. Además el contenido seguía existiendo debajo del panel: TalkBack podía enfocar elementos tapados.

## Qué hay ahora

`PantallaConMicrofono` (en `core/ui/AssistantComponents.kt`) reparte el espacio con proporciones:

| | Contenido | Micrófono |
|---|---|---|
| Vertical | 58 % de arriba | 42 % de abajo, todo el ancho |
| Horizontal | 58 % de la izquierda | 42 % de la derecha, todo el alto |

- Las dos zonas no se encimian: el contenido termina donde empieza el micrófono.
- **Toda la zona del micrófono es el botón.** Un toque habla, dos toques callan al asistente, mantener oprimido da la ayuda. El círculo y las leyendas se dibujan centrados y el círculo se ajusta al espacio (entre 88 y 176 dp; 220 en pantallas grandes).
- En pantallas anchas (tablet) el contenido no pasa de 720 dp y queda centrado.
- La proporción es una sola constante: `MIC_FRACCION` (0.42).
- Los márgenes del sistema los sigue poniendo `MainActivity` con `safeDrawingPadding()`.

## Cómo usarlo en una pantalla

```kotlin
PantallaConMicrofono {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { /* contenido */ }
    MicPad(listening = ..., hint = ..., onClick = ...)
}
```

No hace falta `padding(bottom = ...)` ni `Modifier.align(...)` en el `MicPad`. Un hijo que no sea el `MicPad` va a la zona del contenido.

## Pantallas migradas (17)

`HomeScreen`, `LoginScreen`, `RegisterScreen`, `DashboardScreen`, `SettingsScreen`, `PersonalizationScreen`, `EditAccountScreen`, `ChatsScreen`, `GestionScreen`, `RankingScreen`, `EmergenciasScreen`, `SoporteScreen`, `ReportarIncidenteScreen`, `CameraHubScreen`, `VoiceAssistantScreen`, `AiAssistantScreen` y `RequestsScreen`.

## Qué falta verificar (no se pudo compilar ni correr en el entorno donde se hizo)

1. Que compile (`./gradlew :app:assembleDebug`).
2. Vistas previas de `PantallaConMicrofonoPreview.kt`: celular y tablet, vertical y horizontal.
3. En un teléfono real: cada pantalla en vertical y en horizontal, con el texto en tamaño grande, y con TalkBack: el micrófono se anuncia y responde a un toque, doble toque y mantener oprimido.
4. Pantallas con mucho contenido en la zona de arriba (Gestión, Ajustes): que todo se alcance con el desplazamiento.
5. Que el 42 % se sienta bien en la mano. Si no, cambiar `MIC_FRACCION`.

## Pendiente

El módulo de Actividades "se mueve constantemente": no se encontró la causa todavía. Hace falta saber qué pantalla es y qué se mueve.
