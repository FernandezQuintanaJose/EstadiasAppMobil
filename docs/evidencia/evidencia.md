# Evidencia ALIA — rama integracion/2026-11-24 (parcial, 2026-11-23)

| Dato | Resultado | Comando |
|---|---|---|
| Rama / commit | integracion/2026-11-24, 4cb679a | git switch integracion/2026-11-24; git log --oneline -1 |
| Compilación | BUILD SUCCESSFUL (5 min 24 s), solo avisos | ./gradlew assembleDebug (23-nov-2026 23:12) |
| Pruebas unitarias | 91 pruebas, 0 fallos, 0 errores, 0 omitidas | ./gradlew testDebugUnitTest (23-nov-2026 23:13) |
| Teléfono | CPH2387, Android 12 (API 31) | adb shell getprop ro.product.model / ro.build.version.release / .sdk |
| Instalación limpia | Success (desinstalada y reinstalada) | adb uninstall com.cecapi.app; adb install -r app-debug.apk |
| Servidor en app | cecapi.servidor=https://cecapi-asistente.onrender.com en local.properties (ignorado por git) | — |
| Producción Render | /docs responde 200 y /preguntar sin clave responde 401: **sigue sin la versión nueva** | curl -w "%{http_code}" https://cecapi-asistente.onrender.com/docs |
| Pregunta al asistente desde el teléfono | no comprobado (teléfono bloqueado) | — |
| Cuenta, bloqueo 10 intentos, Wikipedia, valores por defecto, TalkBack | no comprobado (teléfono bloqueado) | — |
| Capturas | no hechas (pantalla bloqueada) | — |

Bloqueo: el teléfono tiene pantalla segura (keyguard activo; dumpsys window: isKeyguardShowing=true). No introduzco PIN ni patrón.

## Actualización 23:24 (teléfono desbloqueado)
- Arranque: muestra ALIA, Iniciar sesión y Crear cuenta (01_inicio.png).
- Registro: se abre con nombre, usuario, contraseña (mínimo quince letras), confirmación, correo opcional, afiliación y casilla de 18 años (02, 03 y 04 .png). Cada elemento pide dos toques: uno enfoca y otro activa.
- Validación de usuario y contraseña, aviso a menores, bloqueo de 10 intentos, Wikipedia, valores por defecto y TalkBack: **no comprobado**.

## Actualización 23:34 (registro, por coordenadas con uiautomator)
Comando de lectura de pantalla: `adb shell uiautomator dump /sdcard/u.xml` (con MSYS_NO_PATHCONV=1) y `adb pull`.

| Observación | Evidencia |
|---|---|
| Aviso "El teléfono está muy cargado" apareció una vez mientras escribía en el registro. En ese momento: Thermal Status 0, batería 28.9 °C, RAM libre 1.67 GB. `logcat -s CecapiSalud` no registra ningún nivel CRITICA (solo ALTA por trim=10 a las 23:30:48). **Causa: no comprobada.** | 05_aviso_telefono_cargado.png; adb shell dumpsys thermalservice / meminfo |
| El micrófono de la app dictó mi conversación (texto de la sesión) dentro del campo "Nombre completo" sin que yo lo pidiera: quedó "Si ocupas que te suba el modelo también me hace saber". La pantalla decía "Di tu nombre, o toca aquí" y luego "Escuchando…". | 06_registro_usuario_AB.png |
| Campo usuario muestra "AB" al escribir "ab" (el teclado o la app lo pasan a mayúsculas). **No comprobado cuál.** | 06_registro_usuario_AB.png |
| Contraseña oculta se muestra siempre con 10 puntos aunque se escriban 5 letras. | uiautomator dump |
| Casilla de edad: "Confirmo que tengo 18 años o más. La aplicación es solo para personas adultas" y opción "Tengo menos de 18 años. No se puede crear la cuenta". | 09_aviso_menores.png |
| Resultado de "Crear mi cuenta" con datos inválidos y con la opción de menor: **no comprobado** (el estado de la pantalla cambió entre toques y no pude confirmar qué opción quedó marcada). | 10_crear_con_menor_y_datos_invalidos.png |

## Actualización 23:45 — términos de uso y privacidad (cambio de código)
- **Corrección:** el dictado del nombre por micrófono en el registro es diseño de la app (RegisterViewModel arranca con `voiceTarget = NOMBRE_COMPLETO` y dice "Di tu nombre completo"). No es un fallo.
- Cambios: texto de políticas (mismo de Windows/web, versión `2026-10-30-borrador`), pantalla `PoliticasScreen` por partes (hay que llegar a la última y declarar 18+), obligatoria la primera vez; en el registro, botones "Leer términos de uso y privacidad" y "Acepto..." (sin aceptar no se crea la cuenta); se quitó el botón "Tengo menos de 18 años".
- Compilación y pruebas: BUILD SUCCESSFUL, 96 pruebas, 0 fallos (23-nov-2026 23:44), `./gradlew assembleDebug testDebugUnitTest`.
- Prueba en teléfono de la pantalla nueva: **no comprobada** (el teléfono volvió a bloquearse).

## Actualización 23:51 — lectura en voz alta de los términos
- Botones nuevos en la pantalla de políticas: "Leer esta parte en voz alta", "Leer todo desde el principio" y "Detener la lectura"; al abrir, la app explica cómo usarla; el título de cada parte está marcado como encabezado para TalkBack. La voz de la app ya le pide a TalkBack que se calle mientras habla (VoiceEngine.callarLectorDePantalla).
- Compilación y pruebas: BUILD SUCCESSFUL, 97 pruebas, 0 fallos (23-nov-2026 23:50).
- App reinstalada en el teléfono (instalación limpia). Verla en pantalla: **no comprobado**: el teléfono muestra la pantalla de bloqueo.

## Actualización 24-nov-2026 00:06 — pruebas en el teléfono (CPH2387, Android 12)
Compilación y pruebas: BUILD SUCCESSFUL, 97 pruebas, 0 fallos, 0 errores (`./gradlew testDebugUnitTest`, 24-nov-2026 00:06).

| Prueba | Esperado | Obtenido | Evidencia |
|---|---|---|---|
| Primera vez (instalación limpia) | Se abre la pantalla de términos antes del inicio | Se abre "Términos de uso y privacidad, parte 1 de 11" con botones de lectura | 11_politicas_primera_vez.png |
| Leer en voz alta | La voz habla | Google TTS sintetiza en español, con usage ACCESSIBILITY (`logcat`, `dumpsys audio`, 23:55) | — |
| Aceptar sin leer | Bloquea y explica | "Para aceptar, primero llega a la última parte. Vas en la parte 1 de 11." | 12_politicas_botones_aceptar.png |
| "Leer todo" y "Saltar al final" | Cuentan como llegar al final | Tras "Saltar al final" el título es "Parte 11 de 11: Cambios a estas políticas" y el botón Saltar desaparece (se corrigió un fallo: título y texto desfasados) | 17_ultima_parte.png |
| Aceptar con 18+ declarado | Va al inicio | Va al inicio de ALIA y el sistema pide permiso de micrófono | 18, 19 .png |
| "No acepto, salir de la aplicación" | Cierra la app | Cerró la app (la probé sin querer; la captura de lo que quedó atrás se borró por traer datos de otra aplicación) | — |
| Interruptores de Privacidad en instalación limpia | Todos apagados | Escuchar fuera de la app, Responder a la palabra hola, IA en internet y Búsqueda en Wikipedia: `checked="false"` (uiautomator) | 21, 22 .png |
| Wikipedia apagada: sin consulta | Ninguna consulta | **Solo comprobado en código** (HomeViewModel:380 exige `wikipediaOn`; DeviceSettings:82 pone `false` por defecto). En el teléfono no comprobado: no puedo hablarle ni escribirle una frase desconocida desde adb | — |
| Wikipedia encendida: sí consulta | Sí consulta | no comprobado | — |
| Registro completo, bloqueo 10 intentos, pregunta al asistente | — | no comprobado (el registro y la pregunta necesitan el deploy en Render) | — |
| TalkBack (Inicio, Registro, Configuración) | — | no comprobado (TalkBack no está encendido en el teléfono) | — |
| Capturas 02–10 | — | Son anteriores a los cambios: muestran el registro viejo (con el botón de menores). Rehacerlas | — |

## 25 de noviembre de 2026
Teléfono: CPH2387, Android 12. Rama `integracion/2026-11-24`, commit 8ce4745. `main`: acabab1.

| Prueba | Pasos / comando | Esperado | Obtenido | Evidencia |
|---|---|---|---|---|
| Motor de descripción | `./gradlew descargarMotor` (11:10–11:22) | 4 archivos con SHA-256 correcto | Los 4 en su carpeta (437 MB, 109 MB, 76 MB, 9 MB); la tarea solo los mueve si coincide el SHA-256. No vi la salida de Gradle | `ls -la` |
| Compilación y pruebas | `./gradlew assembleDebug testDebugUnitTest` (11:39) | Compila y pasan | BUILD SUCCESSFUL (14 min 17 s); 224 pruebas, 0 fallos | salida de Gradle |
| Peso APK | `./gradlew assembleRelease` (11:59) | Hasta 150 MB | Depuración 795,685,275 bytes; release sin firmar 741,601,886 bytes. **Pasa el límite** | `ls -la app/build/outputs/apk/` |
| Cuenta en versión anterior | APK de depuración de `main` (243 MB), instalación limpia; cuenta DEMO25 creada por adb y "Responder a la palabra hola" encendido | Cuenta y ajuste guardados | Menú con "Demo Alia"; existe `cecapi.db`; interruptor encendido. Los intentos manuales previos no dejaron base de datos (causa no comprobada) | 2026-11-25/actualizacion_antes_ajuste.png |
| Actualización sin perder datos | `adb install -r` de integración (12:26) | Cuenta y ajuste siguen, sin errores de Room | Pide los términos otra vez; tras aceptar, sesión de "Demo Alia" activa y "Responder a la palabra hola" encendido; 0 líneas en logcat con Migration, SQLiteException, IllegalStateException de Room o FATAL EXCEPTION (12:29) | actualizacion_despues_*.png; `adb logcat -d` |

### Paso 4, instalación limpia (25-nov 12:36–12:57, commit 8ce4745)
| Prueba | Esperado | Obtenido | Evidencia |
|---|---|---|---|
| Términos antes del inicio | Se abren | Se abren en "Parte 1 de 11" | 2026-11-25/02_terminos_parte1.png |
| Párrafo del servicio de accesibilidad | Aparece | Aparece en la parte 4 "Qué datos usamos" | 03_terminos_accesibilidad.png |
| Inicio con micrófono abajo | Pantalla dividida con ALIA | Correcto | 08_pantalla_dividida.png |
| Interruptores de Privacidad | Apagados | Escuchar fuera, "hola", IA en internet y Wikipedia: `checked=false`. "Modo simple" viene encendido (no es de privacidad) | 07_configuracion_privacidad.png; uiautomator |
| Registro con contraseña corta ("corta") | Muestra error y no crea la cuenta | **La cuenta se creó** (menú "Hola, Demo"). La pantalla pasó por "Escuchando…". Explicación probable según el código (RegisterViewModel, caso CONTRASENA): tras el error abre el micrófono, toma lo que oiga como contraseña y como confirmación y llama a `submit()` sin preguntar. **No comprobado qué audio oyó** | 04_registro.png; sin captura del error |

### Arreglo del registro por voz (25-nov 13:23–13:32)
- Cambio: la contraseña dictada se pide dos veces iguales (`DictadoDeContrasena`) y la cuenta solo se crea con un "sí" o tocando "Crear mi cuenta"; escribir la contraseña cancela la captura por voz.
- `./gradlew assembleDebug testDebugUnitTest`: BUILD SUCCESSFUL, 229 pruebas, 0 fallos (13:23).

| Prueba | Esperado | Obtenido | Evidencia |
|---|---|---|---|
| Registro con "corta" escrita | Error y no se crea la cuenta | "La contraseña tiene 5 letras y necesita al menos 15…"; 25 s después seguía en el registro (13:32) | 2026-11-25/05_registro_validacion.png |

### Bloqueo de inicio de sesión (25-nov 13:45–14:01, CPH2387, Android 12)
| Prueba | Esperado | Obtenido | Evidencia |
|---|---|---|---|
| 10 intentos fallidos (usuario demo25) | Se bloquea al décimo | La app cuenta "Intento N de 10"; `logcat -s CecapiLogin` registra "failed attempt #10" (13:57). Varios toques automáticos no se registraron como intento | adb logcat |
| Cerrar la app por completo y volver a intentar | Sigue bloqueado | "Inicio de sesión bloqueado por ahora." (14:01) tras `am force-stop` | 2026-11-25/06_bloqueo.png |
| Reiniciar el teléfono con el bloqueo activo | Sigue bloqueado como máximo 15 min | **no comprobado**. Riesgo visto en el código: LoginAttemptsStore guarda `elapsedRealtime + 15 min`, que vuelve a cero al reiniciar; con el teléfono encendido ~4.6 días (`/proc/uptime` 399183 s), el bloqueo podría durar ese tiempo | LoginAttemptsStore.kt |
| Registro dictando la contraseña dos veces | Pide repetir y confirma antes de crear | **no comprobado en el teléfono** (solo pruebas unitarias) | DictadoDeContrasenaTest |
| Observación | — | La burbuja del asistente de Google apareció encima de ALIA varias veces; causa no comprobada | dumpsys window |

### Capturas y asistente (25-nov 14:10–14:29, commit 1eaccaf, CPH2387, Android 12)
Cuenta de demostración DEMO26.

| Prueba | Esperado | Obtenido | Evidencia |
|---|---|---|---|
| Inicio con menú y Personalización | Muestran ALIA | Correcto | 2026-11-25/01_inicio.png, 10_personalizacion.png |
| Pregunta escrita en Consultas ("quien es Jake el perro", 14:19) | El asistente responde | Respondió "No estoy seguro de a qué Jake te refieres. ¿Puedes dar más detalles?". **Falta confirmar en el panel de Render que Groq sumó la petición** | 09_asistente_respuesta.png |
| Consultas anteriores | Guarda el chat | Aparece con fecha y botones de borrar | 11_chats.png |
| Interruptor "Inteligencia artificial en internet" apagado | — | Consultas envió la pregunta igual. Según el código, el interruptor (`aiEnabled`) solo controla la interpretación de órdenes no entendidas. Su texto dice "Todavía no está conectada", aunque Consultas ya responde | DeviceSettings.kt:98; ConsultasViewModel.generar |
| Consultas con el micrófono abierto | No enviar nada sin intención | **Tras leer la respuesta, la app abrió el micrófono y envió como consulta una frase que oyó, sin ningún toque.** Según el código, cualquier frase que no sea un comando se manda con `generar()`. Origen de la voz no comprobado; la frase y su captura no se guardan aquí | ConsultasViewModel (rama `else -> generar(spoken)`) |
| Pantalla del servicio de accesibilidad | Explica y viene apagado | "El servicio está apagado", explica volumen abajo dos veces y que la captura no se guarda ni se envía. Activarlo y lo que lee: no comprobado | 12_accesibilidad.png |

### Arreglo de Consultas (25-nov 14:47–14:52)
- Cambio: lo que se oye en Consultas ya no se envía directo. La app lo repite y pregunta "¿La envío?"; solo un "sí" claro lo envía y cualquier otra frase lo descarta (`ConfirmacionDeConsulta`). Lo escrito con el botón se envía como antes.
- `./gradlew assembleDebug testDebugUnitTest`: BUILD SUCCESSFUL, 235 pruebas, 0 fallos (14:47).

| Prueba | Esperado | Obtenido | Evidencia |
|---|---|---|---|
| Consulta escrita "que es un circulo" (14:51) | Se envía y responde | Respondió la definición de círculo en unos 40 s | 2026-11-25/consultas_escrita_tras_arreglo.png |
| Confirmación de lo oído por voz | Pregunta antes de enviar | **no comprobado en el teléfono** (solo pruebas unitarias). A las 14:52 no se había enviado nada nuevo, pero no se sabe si la app oyó algo | ConfirmacionDeConsultaTest |

### Prueba por voz de Consultas (25-nov 14:54–15:08, CPH2387, Android 12)
| Prueba | Esperado | Obtenido | Evidencia |
|---|---|---|---|
| A: preguntar por voz y responder "no" | Pregunta "¿La envío?" y no envía | La persona que probó reporta que la app preguntó y que respondió "no"; la pantalla siguió sin consulta ni respuesta (14:54) | 2026-11-25/voz_A_no_enviar.png |
| B: preguntar por voz y responder "sí" | Envía y responde | **no comprobado**. En los intentos con registro temporal ninguna frase llegó a Consultas | logcat |
| **Pendiente: ventana del micrófono principal** | La persona alcanza a hablar | En un toque sin que nadie hablara (15:07:51), el reconocedor de Google abrió el micrófono, marcó inicio de sonido a los 0.5 s, lo dio por terminado a los 2.0 s y devolvió NO_SPEECH_DETECTED (error 7 en la app). **No comprobado qué sonido oyó** (posible pitido de la app o ruido). Además el registro muestra "Failed to get language pack of required locale: error 12" (es-MX sin conexión) | logcat: RecognitionServiceImpl, SodaSpeechRecognizer, CecapiVoice |
| **Pendiente: escucha de fondo silenciosa** | Si la app oye algo y lo ignora, avisa | Con el micrófono principal cerrado, la escucha de fondo recibió voz en Consultas con `openMic=false` y la descartó sin decir nada. Quien no ve puede creer que la app sigue escuchando. Podría afectar también al dictado de contraseña (no comprobado) | logcat; VoiceEngine.kt |

El registro temporal de diagnóstico se quitó (no está en ningún commit). Después: 235 pruebas, 0 fallos (15:13) y la versión limpia instalada en el teléfono.
