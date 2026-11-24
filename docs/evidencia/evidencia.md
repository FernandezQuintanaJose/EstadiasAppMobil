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
