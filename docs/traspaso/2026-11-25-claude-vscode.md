# Traspaso del 25 de noviembre de 2026: trabajo con el teléfono

Para el Claude Code de VS Code, con el teléfono conectado por `adb`. Trabaja en la rama
**`integracion/2026-11-24`** (esta rama `traspaso/2026-11-25` solo trae este archivo).

## Reglas
- No fusiones a `main` ni hagas push a `main`. Sube tus cambios a `integracion/2026-11-24`.
- No imprimas, guardes ni pegues claves, tokens ni cadenas de conexión. `cecapi.claveApp` va solo en
  `local.properties`, que no se sube.
- Cuenta de demostración y sin datos personales en capturas y registros.
- Si no puedes comprobar algo, escribe "no comprobado". No lo supongas.

## Contexto
- La rama integra: servidor para Render, cuentas y privacidad, interruptor de Wikipedia (opt-in),
  nombre ALIA, términos de uso con lectura en voz alta y la cámara y el servicio de accesibilidad de
  Jesús (módulos 7 y 10).
- Las políticas pasaron a la versión `2026-11-25-borrador`: quien ya las aceptó debe verlas otra vez.
- El servidor de producción (`https://cecapi-asistente.onrender.com`) se corrige con el PR #13. Hasta
  que José lo publique, el registro con cuenta no funciona contra Render.

## 1. Motor de descripción y compilación
```bash
git fetch origin && git switch integracion/2026-11-24 && git pull
./gradlew descargarMotor          # baja ~600 MB del release v1.0-motor-de-descripcion y revisa SHA-256
./gradlew assembleDebug testDebugUnitTest
```
Anota: si compila, cuántas pruebas pasan, y cualquier error completo. Si `descargarMotor` falla
(el release no existe o no hay red), dilo y sigue sin el motor: la app compila igual.

## 2. Peso del APK
```bash
./gradlew assembleRelease
ls -la app/build/outputs/apk/release/
```
Anota el tamaño en MB. Antes de la cámara de Jesús pesaba unos 109 MB. Si pasa de 150 MB, avísale
a José: los modelos van dentro del APK y conviene descargarlos aparte.

## 3. Actualización de la base del teléfono
1. Instala el APK de `main` (versión anterior), crea una cuenta de demostración y cambia un ajuste.
2. Sin desinstalar, instala el de `integracion/2026-11-24`: `adb install -r app-debug.apk`.
3. Comprueba que la cuenta y el ajuste siguen ahí, y revisa `adb logcat` por errores de `Migration`
   o `IllegalStateException` de Room.

## 4. Pruebas en el teléfono (fecha, pasos, esperado, obtenido, evidencia)
- Términos: en instalación limpia se abren antes del inicio; tras actualizar, vuelven a pedirse por la
  versión nueva; aparece el párrafo del servicio de accesibilidad.
- Interruptores de Privacidad apagados en instalación limpia.
- Wikipedia apagada: una frase que la app no entiende, con internet, no sale a Wikipedia
  (`adb logcat` y, si se puede, un monitor de red). Encendida: sí sale.
- TalkBack en Inicio, Registro, Términos y Configuración: qué lee y qué no.
- Registro completo y bloqueo a los 10 intentos fallidos; qué pasa al reiniciar el teléfono.
- Servicio de accesibilidad de ALIA: cómo se activa, si Android lo restringe y qué lee.
- Pregunta al asistente desde la app con `cecapi.servidor=https://cecapi-asistente.onrender.com`
  (y la clave en `local.properties`). Revisa en el panel que Groq sumó una petición.

## 5. Capturas para la memoria
Con `adb exec-out screencap -p > nombre.png`, en `docs/evidencia/2026-11-25/`, mostrando ALIA:

| Archivo | Pantalla |
|---|---|
| `01_inicio.png` | Inicio con el menú |
| `02_terminos_parte1.png` | Términos, parte 1, con los botones de lectura |
| `03_terminos_accesibilidad.png` | Parte con el párrafo del servicio de accesibilidad |
| `04_registro.png` | Registro (sin el botón de menores) |
| `05_registro_validacion.png` | Mensaje de contraseña corta o usuario inválido |
| `06_bloqueo.png` | Aviso tras 10 intentos fallidos |
| `07_configuracion_privacidad.png` | Interruptores de privacidad apagados |
| `08_pantalla_dividida.png` | Micrófono en la parte inferior |
| `09_asistente_respuesta.png` | El asistente respondiendo una pregunta |
| `10_personalizacion.png` | Personalización |
| `11_chats.png` | Chats |
| `12_accesibilidad.png` | Pantalla del servicio de accesibilidad |

## 6. Entrega
Agrega al final de `docs/evidencia/evidencia.md` una sección "25 de noviembre de 2026" con una fila por
prueba (fecha, teléfono y Android, pasos, esperado, obtenido, evidencia) y el comando de cada dato.
Haz commit y push a `integracion/2026-11-24`. Avisa a José con el hash.
