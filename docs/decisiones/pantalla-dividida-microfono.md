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

## Tarjeta iluminada (selección), como un lector de pantalla

Fecha: 30 oct 2026. Misma rama.

**Qué se quiere:** que solo se ilumine la tarjeta donde está la persona, con el color de su módulo, y que las demás queden neutras; y que se pueda mover de una a otra.

**Qué hay** (en `BigBtn`, `PantallaConMicrofono` y `MicPad`, `core/ui/AssistantComponents.kt`):

- Las tarjetas de módulo (las que reciben `section = Sections.X`) ahora son **neutras** (borde y texto grises). La **seleccionada** se ilumina: borde de 4 dp con el color de su módulo, fondo del módulo e icono con color. Los botones con significado (Danger, Success, Accent) conservan su color y también se iluminan al seleccionarse.
- Solo hay una seleccionada por pantalla (`SeleccionTarjeta`). La **primera tarjeta de módulo** nace seleccionada. Si la seleccionada desaparece (por ejemplo, al cambiar de submenú), la nueva primera toma su lugar.
- **Se selecciona** al recibir el foco (teclado, mando, Switch Access o TalkBack si sincroniza el foco), al **mantener oprimida** una tarjeta (ya leía su ayuda) y al **tocarla**.
- **Deslizar un dedo a la derecha** pasa a la siguiente tarjeta y a la **izquierda** a la anterior; la nueva se ilumina y se lee su ayuda en voz alta. No actúa sobre campos de texto ni carruseles, que usan ese deslizamiento. Con TalkBack encendido no actúa: TalkBack maneja los gestos.
- **Micrófono:** un toque habla; dos toques seguidos **o un toque con dos dedos** callan al asistente.
- En **Configuración** hay un botón "Lector de pantalla de Android" que abre los ajustes de accesibilidad del teléfono, para encender o apagar TalkBack.

**Qué NO se puede hacer, y por qué:** el recuadro que dibuja TalkBack cuando mueve su foco lo pinta Android; una app no puede saber cuál tarjeta tiene ese foco. Por eso, con TalkBack encendido, la iluminación de la app solo coincide si TalkBack también mueve el foco de entrada de Compose (**no verificado**: depende de la versión de TalkBack y de Android). Si no coincide, se ven dos indicadores: el de TalkBack y el de la app.

**Qué falta verificar** (nada de esto se pudo compilar ni probar aquí):

1. Que compile.
2. Sin TalkBack: deslizar a la derecha y a la izquierda en Inicio, Panel y Ajustes; la tarjeta se ilumina y se lee. Que el desplazamiento vertical siga igual y no se disparen toques por accidente.
3. Con TalkBack: si al mover su foco la tarjeta de la app también se ilumina. Que no se lea dos veces la misma tarjeta.
4. Mantener oprimida una tarjeta la ilumina y lee su ayuda; tocarla sigue abriéndola.
5. Micrófono: un toque con dos dedos calla y **no** lo activa.
6. Que la primera tarjeta iluminada al abrir una pantalla no moleste (se le pide el foco sin leerla).

## Pendiente

- Cámara: unir en una sola pantalla las dos mitades (leer texto y describir el entorno), con una forma sencilla de elegir cuál usar dentro de la cámara.
- Panel de consultas, para repetir lo que dijo el asistente: se prefiere un panel propio. Falta definir dónde va y qué guarda.

El módulo de Actividades "se mueve constantemente": no se encontró la causa todavía. Hace falta saber qué pantalla es y qué se mueve.
