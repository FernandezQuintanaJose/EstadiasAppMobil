# Tareas por equipo — Servidor y App Android

Semana del 4 al 11 nov 2026. Complementa la sección 4 de [estado/2026-11-04-revision-qa-y-entrega.md](estado/2026-11-04-revision-qa-y-entrega.md) y las guías `docs/TAREAS.md` de PaginaCECAPI, PaginaAsistenteCECAPI y WindowsCECAPI. Lo que no se comprobó se marca **SIN VERIFICAR**.

## Reglas comunes

- Una rama por tarea: `tarea/<nombre-corto>`. Pull request a `main`. Nada de `--force`.
- Antes de cada pull request: servidor `cd backend && python -m pytest`; Android `./gradlew testDebugUnitTest`.
- No subir `local.properties`, claves, cadenas de conexión ni `.env`. Si una clave se pegó en un chat o commit, se cambia, no se borra del historial.
- Lo que cambie en el servidor y afecte a los demás equipos se avisa en `docs/estado/` (fecha en el nombre).
- Los equipos no dependen unos de otros para trabajar ([decisión 0016](decisiones/0016-los-equipos-no-dependen-unos-de-otros.md)): cada uno usa el servidor simulado de sus pruebas hasta que el real exista.

## Servidor (`backend/`) — Móvil A

Estado verificado el 4 nov 2026: `python -m pytest` da 241 correctas y 6 `xfail` (huecos conocidos H2). Es lo único que se corrió; **no** se ha corrido contra Render ni Neon reales.

| # | Tarea | Dónde | Hecho cuando |
|---|---|---|---|
| S1 | **Publicar** en Neon y Render con [backend/PUBLICAR.md](../backend/PUBLICAR.md). Dar la URL a los demás equipos. | `render.yaml`, `backend/PUBLICAR.md` | `GET /` responde desde la URL pública y `alembic upgrade head` corrió sin error en Neon. |
| S2 (código hecho, falta Render) | **Verificar `X-Forwarded-For` en Render** (H1): mandar una petición con un `X-Forwarded-For: 1.2.3.4` falso y ver en el registro qué IP queda. Si Render agrega la IP real a la derecha, leer ese valor en `_limitar`; si no, quitar `--forwarded-allow-ips '*'`. | `render.yaml` (`startCommand`), `backend/main.py` (`_limitar`, línea 162) | Una prueba (`test_hostil.py`) demuestra que mandar una IP falsa no salta el límite por minuto. |
| S3 (código hecho, falta Render) | **CORS** para `https://fernandezquintanajose.github.io` y el dominio final (H3). Hoy `backend/main.py` no define CORS. Lista cerrada de orígenes, nunca `*` junto con credenciales. | `backend/main.py` | Una petición desde esa web funciona y desde otro origen no. Prueba nueva. |
| S4 (hecho 8 nov) | **Cómo preguntan las webs sin llevar la clave** (H3): elegir (a) ruta pública con límite por IP o (b) cuentas con token. Documentar la decisión como `docs/decisiones/0020-...md`. La clave `X-Clave-App` **no** puede ir en JavaScript público. | `backend/`, README de PaginaAsistenteCECAPI | Decisión escrita y avisada a los equipos web. |
| S5 (código hecho 8 nov) | **Rutas de cuenta** del contrato del README del asistente web: registro, verificación, acceso, recuperar, restablecer, aceptación, borrar chats, eliminar cuenta. Argon2id ([decisión 0019](decisiones/0019-un-servidor-modular-y-argon2id.md)). | `backend/` | Cada ruta con pruebas felices y hostiles (contraseña vacía, enorme, repetida, usuario inexistente, límite de intentos). |
| S6 (código hecho, falta la cuenta) | **Proveedor de correo**: `backend/correo.py` ya envía por SMTP (Gmail, Brevo o Resend); falta crear la cuenta y poner sus variables en Render (`backend/PUBLICAR.md`, paso 4). Las cifras de sus planes gratuitos son de terceros: **SIN VERIFICAR**, comprobarlas en su sitio oficial. | — | Correo de prueba recibido. Clave en variable de entorno, no en el código. |
| S7 (hecho 8 nov) | **Filtro de privacidad** (H2): decidir qué huecos cubrir (correo "arroba", teléfono en palabras, CURP con espacios, contraseña en inglés). Cada hueco arreglado quita su `xfail` de `test_hostil.py`. Cuidado con los falsos positivos. | `backend/privacidad.py`, `backend/test_hostil.py` | `pytest` sin `xfail` de los huecos elegidos y con pruebas de preguntas normales que **no** se bloquean. |
| S8 (código hecho, falta Render) | **Documentación pública** (H4): apagar `/docs`, `/redoc` y `/openapi.json` en producción (`docs_url=None`, etc.) o decidir que se quedan. | `backend/main.py` (`FastAPI(...)`, línea 104) | Decisión escrita; si se apagan, prueba que dan 404. |
| S9 (primera revisión 8 nov: [informe](estado/2026-11-08-auditoria-owasp-backend.md)) | **Auditoría OWASP Top 10:2025** antes de publicar (regla de la ficha global). Revisar acceso, inyección, autenticación, secretos, criptografía, registros y manejo de errores. | `backend/` | Informe en `docs/estado/` con hallazgos, severidad y qué se corrigió. |
| S10 | **Panel de uso** (`/panel`): comprobar que exige clave y no muestra preguntas ni datos personales. | `backend/panel.py`, `panel.html` | Prueba que sin clave no entra y con clave solo muestra conteos. |

**Hecho en código el 8 nov (350 pruebas, 0 `xfail`):** S4 ([decisión 0020](decisiones/0020-cuentas-con-codigo-por-correo.md): cuentas con token y código por correo), S5 (`backend/cuentas.py`, `backend/test_cuentas.py`, migración 0002) y S7 (los seis huecos del filtro de privacidad, con pruebas de frases normales que siguen pasando). Falta S6: la cuenta de correo real.

**Hecho en código el 4 nov (`backend/test_acceso_web.py`, 280 pruebas):** S2 (la IP ya no se toma de lo que escribe quien manda la petición; se configura con `PROXIES_DE_CONFIANZA`), S3 (CORS con lista cerrada, `ORIGENES_WEB`) y S8 (`/docs` apagado salvo `MOSTRAR_DOCS=1`), más zona horaria de México (`CECAPI_ZONA`) y retención (`RETENCION_DIAS`, 90 por defecto: confirmar con la revisión legal). Lo que falta es comprobar S2 en Render con los pasos de `backend/PUBLICAR.md`, porque la cantidad de proxies de Render no está en su documentación.

Orden sugerido: S1 y S2 primero (todos esperan la URL), luego S3 y S4, luego S5 y S6, S7–S10 en paralelo.

## App Android (`app/`) — Móvil B

Estado verificado el 4 nov 2026: 60 pruebas unitarias correctas (`./gradlew testDebugUnitTest`). **No** se ha probado un APK de publicación en un teléfono.

| # | Tarea | Dónde | Hecho cuando |
|---|---|---|---|
| M1 | **Servidor real**: en `local.properties` poner `cecapi.servidor=https://<nombre>.onrender.com` y `cecapi.claveApp=...`. La dirección de ejemplo por defecto (`http://TU-IP-LOCAL:8000`) no debe llegar al APK de publicación. | `app/build.gradle.kts` (líneas 27–32 y `permiteHttp`) | El APK de publicación no permite http (`permiteHttp` en falso) y habla con el servidor real. |
| M2 | **Firmar el APK** de publicación y probarlo en un teléfono real. `isMinifyEnabled = true` puede romper cosas que solo se ven ejecutando (voz, cámara, ML Kit, Room). Recorrer todas las pantallas. | `app/build.gradle.kts` (línea 44) | APK instalado y recorrido completo sin cierres. Lista de lo probado, modelo de teléfono y versión de Android. | **Avance (5 nov):** el APK de publicación (compilado con R8, firmado solo con la llave de depuración para probar) se instaló sobre la versión anterior en un OPPO A57 y se comprobó que abre, restaura la sesión, muestra Elegir voz y abre la cámara, sin cierres ni errores de clases o librerías. **No comprobado:** iniciar sesión (Argon2 nativo con R8; las reglas existen en `proguard-rules.pro`), OCR, etiquetas, traducción y detector de objetos, el resto de las pantallas, y la firma real.
| M3 | **Por qué pesa 222 MB**: revisar el APK en Android Studio (Analyze APK) y decidir qué sobra (modelos, audios, imágenes, arquitecturas). | `app/` | Informe de qué ocupa cuánto y decisión de qué se recorta. | **Causa y arreglo (5 nov):** de los 242 MB del APK de depuración, unos 190 MB eran librerías nativas de ML Kit y TensorFlow Lite repetidas en 4 arquitecturas (x86, x86_64, arm64-v8a, armeabi-v7a). La versión de publicación ahora solo lleva las dos ARM (`ndk.abiFilters` en `app/build.gradle.kts`) y pesa **108,6 MB, contra 223 MB** (R8 ya reducía el código de 33 a 6 MB). Quedan 49,5 MB de arm64 y 32,5 MB de armeabi-v7a; quitar la de 32 bits la bajaría a unos 76 MB, pero dejaría sin la app a teléfonos que solo son de 32 bits: decisión de la persona titular. El OPPO A57 reporta arm64-v8a.
| M4 | **Pasada completa por voz con TalkBack**: cada pantalla alcanzable con gestos y con nombre hablado. Pruebas del teléfono de [2026-11-02-pendientes.md](estado/2026-11-02-pendientes.md). | Todas | Documento `docs/estado/2026-11-XX-talkback.md` con lo que se oyó mal y qué se corrigió. |
| M5 | **Cuentas**: hoy viven en el teléfono (`ServicioDeCuentas`/`CuentasLocales`). Con S5 se decide si siguen locales o pasan al servidor. No migrar sin esa decisión. | `ServicioDeCuentas` | Decisión escrita (con Windows, que espera la misma respuesta). |
| M6 | **Publicar en GitHub Releases** (sin Google Play, [decisión 0008](decisiones/0008-apk-sin-tiendas-y-costo-cero.md)): etiqueta, SHA-256 y registro en `datos/descargas.v1.json` de PaginaAsistenteCECAPI. | GitHub | El botón de descarga del asistente web muestra versión, tamaño y SHA-256 correctos (`Get-FileHash` coincide). |
| M7 (revisado 8 nov: [permisos](estado/2026-11-08-permisos-android.md), falta TalkBack) | **Revisión de permisos**: cámara, micrófono, notificaciones, llamadas. Solo los que se usan, cada uno con texto claro para quien no ve. | `AndroidManifest.xml` | Lista de permisos con su justificación; los que sobran se quitan. |
| M8 | **Voces por modelo de teléfono**: no hay lista oficial; cada teléfono y motor tiene las suyas. En cada teléfono de prueba correr `adb logcat -s CecapiVoces`, abrir la app y copiar lo que sale. Agregar a `VOCES_CONOCIDAS` solo nombres vistos en un teléfono real. Probar también: elegir una voz de internet y activar modo avión (debe seguir hablando con otra), y que una voz "No disponible" no se active ni tocando ni con "voz 3". | `core/voice/CatalogoDeVoces.kt` | Tabla de modelo, motor, Android y voces en `docs/estado/`; ningún caso deja a la app callada. | **Hecho en un OPPO A57** (Android 12, motor Google): ver `docs/estado/2026-11-04-voces-en-telefono.md`. Faltan otros modelos (Samsung, Xiaomi, Motorola, LG; ver "Otras marcas y otros motores" en ese archivo) y una pasada con TalkBack. Cada marca se agrega atada a su motor.
| M9 | **Pantalla "El teléfono está muy cargado"** apareció en 2 de ~6 pruebas sin calor ni falta de memoria. Repetir con `adb logcat -s CecapiSalud` en este y otro teléfono y decidir si `CRITICA` debe exigir más de un aviso de memoria. | `SaludDelDispositivo.kt`, `MainActivity.kt` | Decisión escrita con datos de al menos dos teléfonos. Mientras tanto, la pantalla deja esperar o cerrar. |

## Para ambos

- **Datos de menores y datos reales** ([decisión 0005](decisiones/0005-responsable-menores-datos-reales.md)): confirmar con CECAPI quién es responsable antes de recibir datos de personas reales.
- **Revisión legal** del aviso de privacidad y los términos: fuera del código, hay que pedirla desde hoy.
- **Qué falta para la semana:** lo que depende de terceros (CECAPI, abogado, dominio, certificado de firma) puede no caber en 7 días.

## Entregable final

Servidor publicado con URL y pruebas, APK firmado en un Release, `docs/estado/` con lo probado y lo que quedó pendiente, y las decisiones nuevas en `docs/decisiones/`.
