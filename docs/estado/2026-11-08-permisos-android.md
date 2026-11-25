# Permisos de la app Android (tarea M7)

> Revisión del 8 nov 2026 sobre `app/src/main/AndroidManifest.xml` de la rama `tarea/red-bd-y-servidor`. Se buscó en el código dónde se usa cada permiso. No se probó en teléfono.

## Resultado

Los 9 permisos que pide la app se usan. **No sobra ninguno**, así que no se quitó nada.

| Permiso | Para qué | Dónde se usa | Se pide al usar |
|---|---|---|---|
| `RECORD_AUDIO` | Escuchar las órdenes de voz y lo que se dicta (registro, acceso, asistentes) | `VoiceEngine.kt`, `LoginScreen.kt`, `RegisterScreen.kt`, `HomeScreen.kt`, `DashboardScreen.kt`, `VoiceAssistantScreen.kt`, `AiAssistantScreen.kt` | Sí (en tiempo de ejecución) |
| `CAMERA` | Leer documentos, describir el entorno y los juegos con cámara | `DocumentReaderScreen.kt`, `EnvironmentScreen.kt`, `JuegoCamaraScreen.kt` | Sí |
| `INTERNET` | Asistente inteligente (servidor propio), Wikipedia y Wikidata, voces descargables | `AiAssistantApi.kt`, `WikipediaLookup.kt`, `WikidataLookup.kt`, `CatalogoDeVoces.kt` | No (lo concede Android al instalar) |
| `ACCESS_NETWORK_STATE` | Decir si hay internet antes de usar la IA y leer el estado de la red | `ConnectivityObserver.kt`, `PhoneStatusReader.kt` | No |
| `VIBRATE` | Patrones de vibración del módulo de aprendizaje y respuesta al tocar | `VibrationEngine.kt` (módulo 6), personalización | No |
| `WRITE_EXTERNAL_STORAGE` (solo hasta Android 9, `maxSdkVersion=28`) | Guardar en la galería la foto de un documento | `DocumentReaderScreen.kt`, `PhotoGallery.kt` | Sí, solo en Android 9 o anterior |
| `FOREGROUND_SERVICE` | "Escuchar fuera de la app", opcional | `ListeningService.kt` | Lo activa la persona en Ajustes |
| `FOREGROUND_SERVICE_MICROPHONE` | Lo exige Android 14 para que ese servicio use el micrófono (`foregroundServiceType="microphone"`) | `ListeningService.kt` | Junto con el anterior |
| `POST_NOTIFICATIONS` | La notificación fija que avisa que la app escucha fuera de ella (Android 13 o más) | `SettingsScreen.kt` | Sí, al activar "escuchar fuera de la app" |

Además, `CecapiNotificationListener` declara `BIND_NOTIFICATION_LISTENER_SERVICE`. No es un permiso que pida la app: es el que protege al servicio para que solo Android se conecte a él. Leer notificaciones solo funciona si la persona enciende "Acceso a notificaciones" en los ajustes del teléfono.

`camera` y `microphone` están como `uses-feature required="false"`: la app se puede instalar en un teléfono sin ellos.

## Lo que no se revisó

- Que cada solicitud se anuncie por voz con un texto claro para quien no ve. Hay que comprobarlo con TalkBack en el teléfono.
- Qué pasa si la persona niega un permiso dos veces (Android ya no vuelve a mostrar el cuadro).
