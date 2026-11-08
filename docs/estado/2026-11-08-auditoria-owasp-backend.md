# Auditoría OWASP Top 10:2025 del servidor (tarea S9)

> 8 nov 2026 · Alcance: `backend/` de la rama `tarea/cuentas-y-privacidad` (FastAPI, SQLAlchemy Core, SQLite local / PostgreSQL en Neon, Render) más `render.yaml`. Código del servidor; no incluye la app Android, las webs ni la configuración real de Render o Neon.
> Método: revisión estática, siguiendo cada dato que manda el cliente hasta donde se usa. Lo único que se ejecutó fue la batería de pruebas propia (`pytest`, 352 correctas). No se escaneó el servidor publicado (todavía no existe) ni se revisaron CVE de las dependencias.
> Primera revisión: no prueba que el servidor sea seguro en producción.

## Resumen

El servidor ya tenía buenas bases: consultas con parámetros (sin SQL armado a mano), contraseñas con Argon2id, tokens y códigos guardados solo como SHA-256, CORS con lista cerrada, `/docs` apagado, IP real configurable, EXIF quitado de las fotos y errores sin detalles internos.

Lo más importante que se encontró fue que **el código para cambiar la contraseña se podía adivinar** pidiendo uno nuevo cada minuto. **Se corrigió el mismo día** (A07-001). Queda un riesgo de diseño conocido: la clave de la app viaja dentro del APK (A07-002).

| Severidad | Cantidad | Confianza |
|---|---|---|
| Alta | 0 | — |
| Media | 2 | 2 altas |
| Baja | 5 | 4 altas, 1 media |
| Informativa | 2 | 2 altas |

Corregidos en esta rama: A07-001 y A09-001.

## Hallazgos

### A01:2025 Control de acceso roto

**OWASP-A01-001 · Se puede saber si una cuenta existe** · CWE-200 (Exposure of Sensitive Information to an Unauthorized Actor)
- **Severidad: Baja.** Solo revela que un usuario o correo tiene cuenta; no da acceso.
- **Confianza: Alta.**
- **Dónde:** `backend/cuentas.py:417` (`/cuenta/acceso` responde 429 "Hay demasiados intentos fallidos" solo si la cuenta existe y está bloqueada) y `cuentas.py:357` (`/cuenta/registro` responde 409 si ya hay una cuenta confirmada).
- **Camino:** cualquiera, sin sesión. Con 5 contraseñas malas a un usuario, la sexta da 429 si existe y 401 si no. El registro con un correo ajeno da 409 si ya tiene cuenta.
- **Lo que ya lo frena:** el mismo mensaje para "no existe" y "contraseña mala", un hash señuelo para igualar tiempos, y `/recuperar` responde igual siempre. El límite por IP (20 por minuto) lo hace lento, no imposible.
- **Arreglo:** aceptarlo (la mayoría de los registros públicos revela esto) o responder el bloqueo con el mismo 401. El 409 del registro es difícil de evitar sin mandar un correo en cada caso.
- [OWASP A01](https://owasp.org/Top10/2025/A01_2025-Broken_Access_Control/) · [CWE-200](https://cwe.mitre.org/data/definitions/200.html)

### A02:2025 Configuración insegura

**OWASP-A02-001 · Sin `CECAPI_CLAVE_APP`, el servidor no pide clave** · CWE-636 (Not Failing Securely) · se reporta aquí por ser configuración
- **Severidad: Informativa.** En Render la clave se genera sola (`render.yaml`, `generateValue: true`).
- **Confianza: Alta.**
- **Dónde:** `backend/seguridad.py:71-77` (`clave_valida` regresa `True` si la clave está vacía), `main.py:661` (`proteger_panel` deja entrar al panel sin clave).
- **Impacto si pasa:** las rutas de IA y el panel quedan abiertos, solo con el límite por IP.
- **Lo que ya lo frena:** al arrancar se escribe una advertencia (`main.py:45`) y `render.yaml` la genera.
- **Arreglo opcional:** que el servidor no arranque sin clave cuando hay `DATABASE_URL` (es decir, en producción).
- [OWASP A02](https://owasp.org/Top10/2025/A02_2025-Security_Misconfiguration/) · [CWE-636](https://cwe.mitre.org/data/definitions/636.html)

CORS (`main.py:117-122`): lista cerrada de `ORIGENES_WEB`, sin `*` y sin credenciales. Sin hallazgos.

### A03:2025 Fallas en la cadena de suministro

**OWASP-A03-001 · Dependencias sin versión fija** · CWE-1357 (Reliance on Insufficiently Trustworthy Component)
- **Severidad: Baja.** No se encontró una dependencia vulnerable concreta; el riesgo es que una versión nueva rompa o traiga una falla sin que nadie lo note.
- **Confianza: Alta.**
- **Dónde:** `backend/requirements.txt` (`fastapi`, `uvicorn`, `groq`, `google-genai`, `pillow`, `argon2-cffi` sin versión; `sqlalchemy>=2.0`, etc.) y `render.yaml` (`pip install -r requirements.txt`).
- **Camino:** cada despliegue en Render instala lo último que haya en PyPI.
- **Arreglo:** fijar versiones (`pip freeze` o `pip-compile --generate-hashes`) y revisarlas con `pip-audit` antes de publicar.
- [OWASP A03](https://owasp.org/Top10/2025/A03_2025-Software_Supply_Chain_Failures/) · [CWE-1357](https://cwe.mitre.org/data/definitions/1357.html)

### A04:2025 Fallas criptográficas

Sin hallazgos. Contraseñas con Argon2id (19 MiB, 2 pasadas, decisión 0019). Tokens con `secrets.token_urlsafe(32)` y códigos con `secrets.randbelow`; en la base solo su SHA-256 (los códigos con sal). Las comparaciones usan `hmac.compare_digest` y `secrets.compare_digest`. SMTP con STARTTLS y verificación del certificado (`ssl.create_default_context`). HTTPS lo da Render.

### A05:2025 Inyección

Sin hallazgos. Todas las consultas son de SQLAlchemy Core con parámetros. No hay `eval`, `subprocess` ni plantillas del lado del servidor. El correo no admite saltos de línea (`cuentas.py`, `CORREO`), así que no se pueden inyectar encabezados. `panel.html` se sirve como archivo fijo. La inyección de instrucciones a la IA (prompt injection) no es de este Top 10: el filtro de privacidad y las instrucciones del sistema limitan lo que sale, pero no se auditó.

### A06:2025 Diseño inseguro

**OWASP-A06-001 · El cupo diario por cuenta puede pasarse con peticiones al mismo tiempo** · CWE-362 (Race Condition)
- **Severidad: Baja.** Unas cuantas consultas de más, o un error 500 en la primera consulta del día.
- **Confianza: Media** (no se probó contra PostgreSQL).
- **Dónde:** `backend/cuentas.py:234` (`consumir_cupo`: lee y luego escribe, sin `SELECT ... FOR UPDATE`).
- **Camino:** con una sesión válida, mandar muchas preguntas al mismo tiempo. En SQLite no pasa (hay un candado); en PostgreSQL dos transacciones pueden leer el mismo número, y dos `INSERT` del primer día chocan en la llave primaria.
- **Arreglo:** `INSERT ... ON CONFLICT DO UPDATE SET peticiones = peticiones + 1 ... RETURNING peticiones`, y comparar con el cupo después.
- [OWASP A06](https://owasp.org/Top10/2025/A06_2025-Insecure_Design/) · [CWE-362](https://cwe.mitre.org/data/definitions/362.html)

### A07:2025 Fallas de autenticación

**OWASP-A07-001 · El código de recuperación se podía adivinar pidiendo códigos nuevos (CORREGIDO)** · CWE-307 (Improper Restriction of Excessive Authentication Attempts)
- **Severidad: Media.** Con suerte o con tiempo, alguien podía cambiar la contraseña de otra persona y quedarse con su cuenta. Necesita saber su correo, y la víctima recibe un correo con cada código.
- **Confianza: Alta.**
- **Dónde:** `backend/cuentas.py`, `_nuevo_codigo` y `_codigo_correcto` (antes de este cambio), rutas `POST /cuenta/recuperar` y `POST /cuenta/restablecer`.
- **Camino:** `/recuperar` con el correo de la víctima crea un código nuevo cada 60 segundos (`CODIGO_ESPERA_S`), cada uno con 5 intentos (`CODIGO_INTENTOS`). El límite de 20 por minuto es por IP, así que con varias IP salían unos 7,200 intentos al día por cuenta: cerca de 0.7 % de acertar cada día, o 20 % en un mes.
- **Arreglo hecho:** tope de `CODIGO_FALLOS_DIA` (10) intentos fallidos por cuenta en 24 horas, sumando todos los códigos que pida. Los códigos con intentos fallidos se guardan 24 horas para contarlos y solo vale el más nuevo. Al llegar al tope no se manda otro código y tampoco sirve el último. Se escribe una advertencia en el registro, sin el código ni el correo. Ahora la probabilidad es de 1 en 100,000 por día.
- **Pruebas nuevas:** `test_pedir_codigos_nuevos_no_da_intentos_sin_fin` y `test_solo_sirve_el_codigo_mas_nuevo` (`backend/test_cuentas.py`).
- **Lo que cuesta:** quien de verdad olvidó su contraseña y falla 10 veces espera un día.
- [OWASP A07](https://owasp.org/Top10/2025/A07_2025-Authentication_Failures/) · [CWE-307](https://cwe.mitre.org/data/definitions/307.html) · [Forgot Password Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html)

**OWASP-A07-002 · La clave de la app va dentro del APK y abre la IA sin cupo por cuenta y el panel** · CWE-798 (Use of Hard-coded Credentials)
- **Severidad: Media.** Quien descompile el APK puede gastar el límite diario de Groq y Gemini (el asistente deja de responder a todos ese día) y ver el panel (solo números, sin textos ni datos personales).
- **Confianza: Alta.** Ya está documentado en `backend/PUBLICAR.md` ("frena el uso casual, no a alguien decidido").
- **Dónde:** `app/build.gradle.kts:33-35` (`BuildConfig.AI_APP_KEY` desde `local.properties`), `backend/main.py:161` (`proteger`: con la clave, solo el límite por IP), `main.py:661` (el panel usa la misma clave).
- **Arreglo:** que la app use cuentas y token como la web (tarea M5, decisión 0020) y quitar `X-Clave-App` de las rutas de IA cuando ya no la use. Mientras tanto, darle al panel una clave aparte (por ejemplo `CECAPI_CLAVE_PANEL`) que no vaya en el APK.
- [OWASP A07](https://owasp.org/Top10/2025/A07_2025-Authentication_Failures/) · [CWE-798](https://cwe.mitre.org/data/definitions/798.html)

**OWASP-A07-003 · Cualquiera puede bloquear 15 minutos la cuenta de otra persona** · CWE-307 (la otra cara del mismo control)
- **Severidad: Baja.** Molestia, no robo: con 5 contraseñas malas al usuario de alguien, esa persona no entra en 15 minutos.
- **Confianza: Alta.** `backend/cuentas.py`, `acceso` (`ACCESO_FALLOS`, `ACCESO_BLOQUEO_MIN`).
- **Arreglo posible:** bloquear por cuenta y por IP a la vez, o pedir un código por correo en vez de bloquear. Es una decisión de equipo; para una población que depende de la voz, un bloqueo largo pesa más.

### A08:2025 Fallas de integridad de software o datos

Sin hallazgos. No se deserializa nada nativo (solo JSON validado con Pydantic). Los modelos de entrada declaran sus campos uno por uno; ninguno se copia directo a la base. No hay webhooks.

### A09:2025 Fallas de registro y alertas

**OWASP-A09-001 · No se registraban los bloqueos de cuenta (CORREGIDO)** · CWE-778 (Insufficient Logging)
- **Severidad: Baja.** Sin registro no se ve un ataque de adivinanza.
- **Confianza: Alta.** Ahora `acceso` escribe una advertencia con el número de cuenta (sin usuario, correo ni contraseña) al bloquear, y `_nuevo_codigo` al llegar al tope de códigos. Siguen sin alertas automáticas: hay que mirar **Logs** en Render.

**OWASP-A09-002 · Con `CORREO_MODO=consola`, los códigos quedan en el registro** · CWE-532 (Insertion of Sensitive Information into Log File)
- **Severidad: Informativa** mientras no haya personas reales: es a propósito para probar, y el correo sale enmascarado.
- **Confianza: Alta.** `backend/correo.py:63`; `render.yaml` arranca con `CORREO_MODO=consola`.
- **Arreglo:** pasar a `smtp` antes de abrir el registro a personas reales (`backend/PUBLICAR.md`, paso 4). Quien tenga acceso a los registros de Render podría usar esos códigos.

### A10:2025 Manejo incorrecto de condiciones excepcionales

Sin hallazgos aparte de A02-001. Los errores de la IA y de la base se registran solo con el tipo de error (`type(error).__name__`) y la persona recibe un mensaje en español sin detalles. Si guardar el historial falla, la respuesta se manda igual y queda en el registro (`historial.py`). Las fotos tienen tope de tamaño antes de decodificar y se atrapa `DecompressionBombError` (`imagenes.py`). Las rutas de cuenta cuentan los intentos fallidos fuera de la transacción, para que no se pierdan al responder el error.

## Cobertura

| Categoría | Estado | Por qué |
|---|---|---|
| A01 | Revisada | Rutas con sesión, conversaciones por cuenta (`historial.py`), panel. |
| A02 | Parcial | Se revisó `render.yaml` y el código; no hay un servidor publicado para ver encabezados ni la configuración real de Render. |
| A03 | Parcial | No hay archivo con versiones fijas ni se consultaron bases de CVE. |
| A04 | Revisada | |
| A05 | Revisada | La inyección de instrucciones a la IA queda fuera. |
| A06 | Revisada | Reglas de negocio de cuentas y cupos. |
| A07 | Revisada | |
| A08 | Revisada | |
| A09 | Revisada | Sin alertas automáticas que revisar. |
| A10 | Revisada | |

## Plan

1. Antes de abrir el registro a personas reales: `CORREO_MODO=smtp` (A09-002) y fijar versiones de dependencias (A03-001).
2. Con la tarea M5: la app usa cuentas y token, y se quita `X-Clave-App` de las rutas de IA (A07-002). Mientras, una clave aparte para el panel.
3. Cuando se publique en Neon: `consumir_cupo` con `ON CONFLICT` (A06-001).
4. Decidir en equipo el bloqueo de cuentas (A07-003) y el 429 que revela cuentas (A01-001).

## Límites

- No se vio el servidor publicado: encabezados, HTTPS real, proxies de Render y versiones instaladas quedan sin confirmar.
- No se buscaron CVE de las dependencias.
- No se auditó la app Android, las webs ni la inyección de instrucciones a la IA.
- Las reglas de negocio (cupo de 25, bloqueo de 15 minutos, 30 días de sesión) se tomaron de la decisión 0020; no se validaron con CECAPI.
