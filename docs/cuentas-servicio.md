# Servicio de cuentas: lo que hace hoy el teléfono y lo que debe hacer el servidor

> Propuesta para pasar las cuentas de la base local (Room) al servidor propio con Neon, según la [decisión 0017](decisiones/0017-base-en-neon-detras-del-servidor.md). Escrita el 2 nov 2026. Nada de esto está publicado: el servidor (`backend/`) solo tiene `/`, `/uso`, `/preguntar`, `/entender` y `/ver`.

## 1. Cómo funciona hoy (todo en el teléfono)

Clase `SessionRepository` y tabla `usuarios` de Room (versión 19).

| Operación | Hoy | Dónde |
|---|---|---|
| Crear cuenta | Revisa que el usuario no exista (sin importar mayúsculas), guarda el hash y entra de inmediato. Queda "pendiente de validar" si eligió institución. | `SessionRepository.register` |
| Entrar | Busca el usuario y compara el hash. El bloqueo por intentos fallidos lo hace la pantalla, no el repositorio. | `SessionRepository.login`, `LoginViewModel` |
| Recordar la sesión | Guarda el `id` del usuario en DataStore. Al abrir la app entra solo. | `restoreRememberedSession` |
| Cerrar sesión | Borra ese `id`. | `logout` |
| Editar datos | Pide la contraseña actual antes de cambiar nombre, contraseña, apodo o correo. | `actualizarPerfil` |
| Borrar cuenta | Pide contraseña y confirmación; borra la cuenta y todos sus datos. | `deleteCurrentAccount`, `AccountEraser` |
| Roles y validación | Los cambia otra persona desde Gestión (administrador > directivo > educador > alumno). | `GestionViewModel` |

## 2. Reglas que ya se revisan (y que el servidor debe repetir)

- Mayor de 18 años: se guarda cuándo lo confirmó (`acepto_adulto_en`). Quien dice ser menor no crea cuenta.
- Usuario: 3 a 30 caracteres, solo letras, números, punto, guion y guion bajo, sin espacios; se guarda en mayúsculas y es único sin importar mayúsculas. (`AccountRules`)
- Nombre: 2 a 80 caracteres. (`AccountRules`)
- Contraseña: al menos 15 letras, puede ser una frase, sin números ni símbolos obligatorios. (`PasswordRules`)
- Correo (opcional): forma válida y errores comunes de escritura como `gmial.com`. (`EmailRules`) Que el buzón exista solo lo confirma el servidor al mandar un código.
- Editar o borrar datos sensibles exige la contraseña actual.

## 3. Hallazgos antes de migrar

1. **Hash de contraseña débil.** Se usa SHA-256 sin sal (`PasswordHasher`). El servidor debe usar argon2id (o bcrypt) con sal. Las cuentas locales de prueba no se migran: no hay datos reales.
2. **La sesión recordada es solo un número.** Con servidor hace falta un token de sesión con vencimiento, guardado cifrado (Keystore), que el servidor pueda revocar al cerrar sesión o borrar la cuenta.
3. **El usuario no es único en la base.** Hoy la unicidad se revisa en código; en Neon debe ser un índice único (`lower(nombre_usuario)`), porque dos registros a la vez podrían crear el mismo.
4. **El bloqueo por intentos vive en la pantalla.** En el servidor debe ser por cuenta y por dirección, con espera creciente, no en la app.
5. **El rol no debe poder escribirlo la propia persona.** Solo el servidor lo cambia (regla vigente de la 0014/0017).
6. **Cambiar el correo sin verificarlo** permitiría recibir códigos de otra persona. Debe pedir un código al correo nuevo.
7. **Borrar una cuenta** debe borrar también lo que guarda el servidor: chats, uso, reportes, aceptaciones de políticas. Hoy `AccountEraser` solo limpia el teléfono.

## 4. Rutas propuestas

Todas por HTTPS. Los errores devuelven `{"codigo": "...", "mensaje": "..."}` con un texto que se pueda leer en voz alta.

| Ruta | Para qué | Cuerpo | Respuesta |
|---|---|---|---|
| `POST /cuentas` | Crear cuenta | usuario, nombre, contraseña, correo?, institucion?, `mayor_de_18: true` | 201 con token y perfil; 409 `usuario_ocupado`; 422 si falla una regla |
| `POST /cuentas/correo/codigo` | Mandar el código al correo | correo | 204 (siempre, para no revelar qué correos existen) |
| `POST /cuentas/correo/verificar` | Confirmar el correo | correo, código | 204 o 400 |
| `POST /sesion` | Entrar | usuario, contraseña | 200 con token; 401 `credenciales`; 429 `demasiados_intentos` con segundos de espera |
| `DELETE /sesion` | Cerrar sesión (revoca el token) | — | 204 |
| `GET /yo` | Perfil de quien tiene sesión | — | perfil |
| `PATCH /yo` | Editar nombre, apodo, correo, contraseña | `contrasena_actual` + campos | 200; 403 si la contraseña actual falla |
| `POST /yo/recuperar` | Recuperar contraseña por correo verificado | usuario | 204 (siempre) |
| `DELETE /yo` | Borrar la cuenta y todos sus datos | `contrasena_actual` | 204 |
| `GET /gestion/personas` | Quién ve a quién según el rol | — | lista filtrada por el servidor |

Cualquier ruta que no esté en la tabla queda prohibida. Cada una se prueba con cada rol y con alguien sin cuenta (0017).

## 5. Cómo migrar sin romper la app

1. Extraer de `SessionRepository` una interfaz `ServicioDeCuentas` con las operaciones de la sección 1. Hoy la implementa Room (`CuentasLocales`).
2. Escribir `CuentasRemotas` que llama a las rutas de la sección 4 y guarda solo el token (cifrado) y el perfil en caché.
3. Elegir una u otra con un interruptor de compilación, para probar con el servidor sin perder la versión local.
4. Antes de subir cualquier dato real: aviso de privacidad, consentimiento y revisión de seguridad (decisión 0005), y el proveedor de correo elegido.

## 6. Lo que sigue sin poder probarse

Todo lo de las secciones 3 y 4 depende de publicar el servidor, la base en Neon y el proveedor de correo. Sin eso solo se valida lo local, que ya cuenta con pruebas de las reglas de la sección 2.
