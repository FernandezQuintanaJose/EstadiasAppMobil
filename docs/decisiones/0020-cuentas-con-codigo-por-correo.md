# 0020. Las webs y las apps preguntan con cuenta propia y token; la cuenta se confirma con un código por correo

> Aceptada · Dueño: Pp · 8 nov 2026 · Responde a la tarea S4 (hueco H3) · Relacionada con la [0017](0017-base-en-neon-detras-del-servidor.md) y la [0019](0019-un-servidor-modular-y-argon2id.md)

## Contexto

La clave `X-Clave-App` no puede ir en el JavaScript de una página pública: cualquiera la lee con "ver código fuente". Sin otra forma de identificarse, el asistente web no podía preguntar al servidor. Las dos opciones eran (a) una ruta pública con límite por IP o (b) cuentas con token.

## Decisión

1. **Cuentas propias en el servidor** (opción b). Rutas bajo `/cuenta/`: `registro`, `verificar`, `acceso`, `recuperar`, `restablecer`, `aceptacion`, `borrarChats`, `eliminarCuenta` y `salir` (`backend/cuentas.py`).
2. **El correo se confirma con un código de 6 dígitos** que vence en 15 minutos (`CODIGO_MINUTOS`), admite 5 intentos (`CODIGO_INTENTOS`) y no se reenvía antes de 60 segundos (`CODIGO_ESPERA_S`). En la base solo queda su SHA-256 con sal, nunca el código. El correo es texto simple, con el código también dígito por dígito, y sin enlaces.
3. **Al entrar, el servidor da un token** (`Authorization: Bearer ...`) que dura 30 días (`SESION_DIAS`). En la base solo queda su SHA-256. Con ese token, `/preguntar`, `/entender`, `/ver` y `/documento` funcionan sin `X-Clave-App`, con un cupo diario por cuenta (`CUPO_DIARIO_POR_CUENTA`, 25) además del límite por IP.
4. **Contraseñas con Argon2id** (0019), normalizadas igual que en la app: minúsculas, sin acentos salvo la ñ, sin espacios, 15 caracteres o más. Tras 5 fallos seguidos (`ACCESO_FALLOS`) la cuenta se bloquea 15 minutos (`ACCESO_BLOQUEO_MIN`). El mismo mensaje para "no existe" y "contraseña mala".
5. **Cada conversación queda ligada a su cuenta**: nadie puede seguir el chat de otra cuenta aunque conozca su `conversacionId`.
6. **El envío del correo va detrás de `backend/correo.py`** con dos modos (`CORREO_MODO`):
   - `consola` (por defecto): no envía nada; el código sale en el registro del servidor. Solo para pruebas y demostraciones, nunca con personas reales.
   - `smtp`: cualquier proveedor con SMTP y STARTTLS (Gmail con contraseña de aplicación, Brevo, Resend). Usuario, contraseña y remitente van solo en las variables de entorno de Render, nunca en el repo.
7. **`VERIFICACION_POR_CORREO=0`** crea las cuentas ya verificadas. Es un respaldo para una demostración si el correo falla, no para uso real.
8. **La app Android y la de Windows usarán las mismas rutas** cuando dejen las cuentas locales (tarea M5). Mientras tanto, siguen con `X-Clave-App`.

## Lo que falta

- Elegir la cuenta de correo que enviará los códigos y su contraseña de aplicación (S6). Hasta entonces, `CORREO_MODO=consola`.
- Que el asistente web llame a estas rutas (`config.rutas` en PaginaAsistenteCECAPI).
- Revisión legal del aviso de privacidad antes de datos reales (0005).

## Cuándo reconsiderarlo

Si el proveedor de correo limita demasiado los envíos gratuitos, o si CECAPI ya tiene un sistema de cuentas propio al que haya que conectarse.
