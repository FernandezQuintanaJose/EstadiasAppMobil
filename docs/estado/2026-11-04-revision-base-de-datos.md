# Revisión de las bases de datos y del servidor (4 nov 2026)

> Solo lectura: no se cambió ninguna tabla. Se revisó la base del teléfono (Room, versión 19, 22 tablas), la del servidor (`backend/db.py`, 3 tablas) y los esquemas documentados. Lo que no se pudo comprobar está marcado como SIN VERIFICAR. **No se tuvo acceso a Neon**: lo del servidor sale del código y de la migración, no de la base publicada.

## Resumen

1. **El servidor está bien normalizado** (tres tablas, cada una con un caso de uso claro). Falta decidir las cuentas. Retención y zona horaria quedaron resueltas (ver sección 2).
2. **La base del teléfono tiene 22 tablas, pero 10 no tienen quien las lea** en la app. Se pueden reducir hasta 7 tablas (22 a 15), sin perder ninguna función visible.
3. **No conviene reducirlas esta semana.** Ya hay pruebas de migración (`MigracionesTest`), pero la actualización de una app ya instalada sigue sin probarse en un teléfono (riesgo principal de la decisión 0004). Primero esa prueba, después la limpieza.
4. **Dos documentos de esquema mienten** (`database/schema.sql` y `database/servidor.sql`) y confunden a los demás equipos.

## 1. Base del teléfono (Room)

Método: por cada tabla se buscó en el código qué métodos de su DAO se llaman. **Es una búsqueda de texto**: antes de borrar algo, volver a buscar a mano (`grep -rn "NombreDao" app/src/main`).

| Tabla | Quién la usa | Veredicto |
|---|---|---|
| `usuarios` | Cuentas, Gestión, Soporte | Se queda. Ver 1.2 |
| `configuracion_usuario` | Solo se crea con valores por defecto al iniciar sesión; nadie lee sus columnas. La velocidad y el tono viven en `DeviceSettings`. El propio código dice que se conserva "solo para que las cuentas antiguas y el esquema se mantengan intactos" | **Sobra** |
| `permisos_modulos` | Se crea al iniciar sesión y la lee el Dashboard | Se queda. Sin índice único (usuario, módulo): la guarda `countByUser > 0` no es atómica |
| `incidencias` | Soporte y Gestión | Se queda |
| `cambios_cuenta` | Gestión la escribe en 5 lugares; ninguna pantalla la lee. Sin llaves foráneas | Decidir. Ver 1.3 |
| `comandos_voz` | `observeRecent` en el asistente de voz | Se queda |
| `historial_comandos` | Solo se escribe | **Sobra** (1 a 1 con `comandos_voz`) |
| `respuestas_auditivas` | Solo se escribe; `findByComando` existe y nadie lo llama | **Sobra** (1 a 1 con `comandos_voz`) |
| `consultas_ia` | Pantalla de Chats | Se queda |
| `respuestas_ia` | Pantalla de Chats | Se queda; puede fundirse con `consultas_ia` (1 a 1 en la práctica, índice no único) |
| `contextos_conversacion` | La app lee una sola fila por persona (`LIMIT 1`), pero sin índice único | Se queda. Falta el índice único |
| `documentos_escaneados` | `observeRecent` | Se queda |
| `textos_extraidos` | Solo se escribe. Índice único con el documento (1 a 1) | **Sobra** como tabla: el texto va en `documentos_escaneados` |
| `historial_lecturas` | Solo se escribe | **Sobra** |
| `ejercicios`, `ejercicios_vibracion` | Sus repositorios | Se quedan (forma distinta: audio contra patrón de vibración). No fundir |
| `niveles_aprendizaje` | Aprendizaje y el ranking (las tres consultas del ranking salen de aquí) | Se queda |
| `resultados_ejercicios`, `resultados_vibracion` | Solo se escriben; el ranking usa `niveles_aprendizaje` | Decidir: historial sin lector |
| `escaneos_entorno` | `observeRecent` | Se queda |
| `descripciones_entorno` | Solo se escribe. Índice único con el escaneo (1 a 1) | **Sobra** como tabla |
| `objetos_detectados` | Solo se escribe | Decidir: sin lector |

### 1.1 Cuántas se pueden quitar

| Cambio | Tablas menos |
|---|---|
| Quitar `configuracion_usuario` | 1 |
| Fundir `historial_comandos` y `respuestas_auditivas` en `comandos_voz` (o quitarlas) | 2 |
| Fundir `textos_extraidos` en `documentos_escaneados`, y quitar `historial_lecturas` | 2 |
| Fundir `descripciones_entorno` en `escaneos_entorno` | 1 |
| Fundir `respuestas_ia` en `consultas_ia` | 1 |
| **Total** | **7 (22 a 15)** |

`objetos_detectados`, `resultados_ejercicios`, `resultados_vibracion` y `cambios_cuenta` quedan como **decisión del dueño de cada módulo**: guardan historial que hoy nadie ve. Si no hay una pantalla o un informe planeado que los use, también sobran. Menos datos guardados es menos riesgo (decisión 0005): `textos_extraidos`, `comandos_voz` y `consultas_ia` guardan lo que la persona dijo o leyó, y los que nadie lee no dan ningún beneficio.

### 1.2 Normalización de `usuarios`

- **`origen` es texto libre y es la institución.** El ranking por institución compara `u.origen = :origen COLLATE NOCASE`. Un espacio de más o un acento distinto parte una institución en dos. `database/schema.sql` ya diseñaba una tabla `usuarios_institucion` que nunca se hizo. Opción barata: normalizar al guardar (recortar y mayúsculas, como ya se hace con el nombre de usuario). Opción completa: tabla `instituciones` (id, nombre). Solo se justifica si alguien administra instituciones; Gestión ya confirma y quita personas de una institución, así que es defendible, pero no urgente.
- **`nombre_usuario` no tiene índice único en Room**, aunque `schema.sql` lo declara `UNIQUE`. Solo lo cuida el código al registrar: dos registros simultáneos podrían repetirlo. Agregarlo requiere quitar antes los repetidos; va en la migración 19 a 20.
- **`educador_id` apunta a otro usuario pero no tiene llave foránea.** Si se borra al educador, el alumno puede quedar con un número huérfano. Hay que revisar que el código lo limpie (no se probó).
- **Una tabla con ~20 columnas** mezcla identidad, rol, consentimiento (`acepto_adulto_en`) y cinco banderas de trámites (`solicita_validacion`, `aviso_pendiente`, `revision_cuenta`, `institucion_solicitada`, `solicita_cambio_educador`). No es un error; si crecen los trámites, conviene una tabla `solicitudes`. Hoy no.

### 1.3 Lo que sobrevive a "borrar mi cuenta"

`AccountEraser` borra solo la fila de `usuarios` y confía en el `ON DELETE CASCADE`. Las fotos también se borran. Pero `cambios_cuenta` no tiene llave foránea, así que **sus filas siguen ahí** con `objetivo_id`, `autor_id` y `detalle`. El comentario de `AccountEraser` dice "No se conserva nada". Qué hay en `detalle` (si trae nombres) es SIN VERIFICAR. Dos salidas: borrar esas filas también, o decidir y documentar que es una bitácora y qué guarda.

### 1.4 Lo que sí está bien

- `allowBackup="false"`: la copia automática de Android no sube la base.
- Las cuentas de demostración con contraseña `1234` solo se crean si la app es de depuración.
- No hay `fallbackToDestructiveMigration`: una migración que falte hace fallar la app en vez de borrar los datos en silencio.
- Cada tabla de una persona apunta a `usuarios` con borrado en cascada.

## 2. Servidor (`backend/db.py`, Alembic)

| Tabla | Caso de uso | Estado |
|---|---|---|
| `conversaciones` | Agrupa los mensajes de un chat. `id` es un UUID al azar | Bien. `usuario_id` sin llave foránea hasta que haya cuentas |
| `mensajes` | Qué pasó en cada petición (ruta, tipo, IA, tiempo). El texto solo con `GUARDAR_TEXTOS=1` | Bien. CHECK de rol, tipo, ruta, y "bloqueado no guarda texto" |
| `uso_diario` | Peticiones por día y proveedor (límite diario de la IA) | Bien. Llave primaria (fecha, proveedor) |

Está en tercera forma normal. `proveedor` es un texto, no una tabla: aceptable con dos proveedores.

Detalles a decidir:

1. **Cuentas (tarea S5).** Falta `usuarios`. Antes de programar las rutas, fijar el mínimo: correo normalizado y único, hash Argon2id, fechas de verificación y de aceptación del aviso, y `ia_activada` (decisión 0002). Una tabla para los códigos de verificación y recuperación (guardar su hash, no el código, con vencimiento). **No** crear tablas de roles, permisos ni sesiones si el token ya identifica a la persona. Sin cuentas la web no puede preguntar (H3).
2. **Retención. Resuelto:** `RETENCION_DIAS` (90 por defecto; 0 = no borrar) borra mensajes, conversaciones sin actividad y conteos viejos, al arrancar y una vez al día. Los 90 días coinciden con el máximo del panel: **es una propuesta, se confirma con la revisión legal**. El tope de almacenamiento del plan gratuito de Neon sigue SIN VERIFICAR.
3. **Zona horaria. Resuelto:** `render.yaml` ya traía `TZ=America/Mexico_City`, pero dependía de la computadora. Ahora el día sale de `db.hoy()` (`CECAPI_ZONA`, con `tzdata` en las dependencias) y lo usan el contador y el panel.
4. **Conexiones de Neon.** El motor abre hasta 10 conexiones (`pool_size=5`, `max_overflow=5`). El límite del plan gratuito es SIN VERIFICAR.
5. **Doble arranque en frío.** Render duerme el servidor (cerca de un minuto) y Neon suspende la base tras 5 minutos sin uso (`db.py`). La primera petición paga los dos. La app ya habla durante la espera (hasta 75 segundos); falta medirlo con el servidor real.
6. **`alembic downgrade` borra las tablas.** Correrlo por error en producción pierde el historial. No hay copia de seguridad documentada de Neon: SIN VERIFICAR qué ofrece el plan.

## 3. Documentos de esquema desfasados

| Archivo | Problema | Qué hacer |
|---|---|---|
| `database/schema.sql` (453 líneas, 38 tablas) | `usuarios` aparece con 5 columnas; la real tiene ~20 (rol, origen, apodo, validado...). Dice ser "funcionalmente equivalente" a Room. Incluye módulos 8 a 14 que no existen | Generarlo desde Room con `exportSchema = true` y marcar lo de los módulos 8 a 14 como propuesta |
| `database/servidor.sql` (5 tablas, identificadores enteros) | El servidor real usa 3 tablas con UUID. `docs/referencia/tablas-servidor.md` ya lo aclara, pero el SQL sigue como si fuera lo vigente | Una sola fuente de verdad: `backend/db.py` con Alembic. Marcar `servidor.sql` como diseño futuro de cuentas |

## 4. Orden recomendado

**Esta semana (sin cambiar tablas):**
1. ~~Activar `exportSchema = true` y guardar `app/schemas/`.~~ Hecho.
2. ~~Escribir pruebas de migración con `MigrationTestHelper`.~~ Hecho: `MigracionesTest`, en la computadora y sin teléfono. Parte de 12 versiones (1 a 7, 9, 13, 16, 17 y 18; la 1 con esquema derivado, ver `app/schemas/README.md`) y comprueba que llegan al esquema de la 19. Se comprobó que fallan si una migración está mal. Quedan sin probar las versiones 8, 10, 11, 12, 14 y 15 como punto de partida.
3. Probar una actualización real: instalar una versión vieja con datos y actualizar a la nueva en un teléfono.
4. ~~Fijar la zona horaria del servidor y definir la retención de `mensajes`.~~ Hecho; falta confirmar los 90 días.
5. Decidir qué guarda `cambios_cuenta` y si sobrevive al borrado de la cuenta.
6. Regenerar o marcar `schema.sql` y `servidor.sql`.

**Después de entregar (una sola migración 19 a 20, con prueba):**
7. Quitar `configuracion_usuario` y las fundiciones de 1.1, en ese orden de riesgo: primero `configuracion_usuario` (nadie la lee), luego las tablas de solo escritura, al final `respuestas_ia` (la leen los Chats).
8. Índices únicos en `contextos_conversacion(usuario_id)` y `permisos_modulos(usuario_id, modulo_codigo)`, quitando antes las filas repetidas.
9. Normalizar `origen` o crear `instituciones`. **Esperar antes a la decisión M5** (cuentas locales o del servidor, `docs/TAREAS.md`): si las cuentas pasan al servidor, `usuarios` cambia de dueño y este trabajo se hace una sola vez, allá.

Cada paso del 7 al 9 se puede hacer sin tocar el servidor y sin afectar a los demás equipos: la base del teléfono es de la app; el servidor y los demás proyectos no la leen.

## 5. Otros detalles que conviene evaluar antes de entregar

- **Copia de seguridad de Neon** y cuánto tiempo se guarda (SIN VERIFICAR).
- **Qué datos salen del teléfono hacia el servidor**: `docs/referencia/que-sale-del-telefono.md` debe coincidir con `historial.py` (hoy: nunca la foto, el contexto ni la IP; el texto solo con `GUARDAR_TEXTOS=1`).
- **Cifrado de la base del teléfono**: Room no cifra por defecto. Hay hashes de contraseña y texto de documentos y chats. Con `allowBackup="false"` el riesgo baja, pero un teléfono con acceso de superusuario o perdido sigue expuesto. Decidir si se acepta o se cifra (cambia la dependencia de la base: no es trivial).
- **Auditoría OWASP (S9)** sobre el servidor antes de publicar.
- **Respuesta a fallos de la base en el servidor**: hoy `historial.py` anota el error y responde igual. Está bien, pero hay que vigilar que el aviso llegue a alguien.
