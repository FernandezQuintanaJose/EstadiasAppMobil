# Tablas del servidor del asistente

> Propuesta · Dueño: Módulo 3 (asistente inteligente) · Revisado el 10 oct 2026 contra `6240887` · SQL: [database/servidor.sql](../../database/servidor.sql)

**Actualización del 30 oct 2026:** el servidor ya usa tres de las cinco tablas (`backend/db.py`, con Alembic): SQLite en la computadora de cada quien y PostgreSQL en Neon cuando está publicado (decisión 0017). Hay diferencias con el diseño de abajo mientras no existan `usuarios` ni `proveedores`:

- `conversaciones.id` es un UUID al azar (texto), no un entero: es el `conversacionId` que ve la app, y así nadie puede adivinar el de otra persona. `usuario_id` no tiene llave foránea y por ahora siempre es NULL.
- `mensajes` y `uso_diario` guardan el nombre del proveedor (`groq`, `gemini`, `groq_vision`) en vez de `proveedor_id`.
- `mensajes` tiene además `ruta` (`preguntar`, `entender`, `ver` o `documento`). De `/ver` y `/documento` se crea una conversación por petición.
- `mensajes.texto` y `conversaciones.titulo` quedan en NULL salvo con `GUARDAR_TEXTOS=1` (decisión 0005). Lo bloqueado por privacidad, el contexto y las fotos nunca se guardan.

`usuarios` y `proveedores` esperan a que el servidor tenga cuentas.

No son las tablas del Módulo 3 que están en `database/schema.sql` (`consultas_ia`, `respuestas_ia`, `contextos_conversacion`). Esas viven en el teléfono, con Room. Estas otras viven en la computadora donde corre el servidor.

![Diagrama de las tablas del servidor](tablas-servidor.png)

```mermaid
erDiagram
    usuarios ||--o{ conversaciones : "tiene"
    conversaciones ||--o{ mensajes : "contiene"
    proveedores |o--o{ mensajes : "contesta"
    proveedores ||--o{ uso_diario : "lleva la cuenta de"

    usuarios {
        INTEGER id PK
        TEXT nombre_usuario UK "el mismo de la app"
        INTEGER ia_activada "0 al inicio (decisión 0002)"
        INTEGER fecha_registro
    }
    conversaciones {
        INTEGER id PK
        INTEGER usuario_id FK
        TEXT titulo "primera pregunta"
        INTEGER iniciada_en
        INTEGER ultima_actividad
    }
    mensajes {
        INTEGER id PK
        INTEGER conversacion_id FK
        TEXT rol "usuario o asistente"
        TEXT tipo "pregunta, respuesta, comando, bloqueado, error"
        TEXT texto "NULL si se bloqueó"
        TEXT comando "storageCode si es comando"
        INTEGER proveedor_id FK "NULL si no hubo IA"
        INTEGER hay_mas
        INTEGER duracion_ms
        INTEGER creado_en
    }
    proveedores {
        INTEGER id PK
        TEXT nombre UK "gemini, groq"
        TEXT modelo
        INTEGER orden_respaldo UK "1 = principal"
        INTEGER limite_diario "0 = sin límite"
        INTEGER activo
    }
    uso_diario {
        TEXT fecha PK "AAAA-MM-DD"
        INTEGER proveedor_id PK,FK
        INTEGER peticiones
        INTEGER aviso_enviado "ya avisó del 80%"
    }
```

## Relaciones

| Relación | Tipo | Al borrar |
|---|---|---|
| `usuarios` → `conversaciones` | Una persona tiene cero o muchas conversaciones. | Se borran sus conversaciones. |
| `conversaciones` → `mensajes` | Una conversación tiene cero o muchos mensajes. | Se borran sus mensajes. |
| `proveedores` → `mensajes` | Cada respuesta la dio un proveedor, o ninguno si fue comando, bloqueo o error. | El mensaje se queda, con `proveedor_id` en NULL. |
| `proveedores` → `uso_diario` | Un proveedor tiene una fila por día. La llave es `(fecha, proveedor_id)`. | Se borra su historial de uso. |

Borrar a una persona borra en cascada sus conversaciones y mensajes. Eso hace falta para "borra mi cuenta / borra mis datos".

## De dónde sale cada tabla en el código de hoy

| Tabla | Hoy está en | Notas |
|---|---|---|
| `proveedores` | Clases `Gemini` y `Groq` y la lista `PROVEEDORES` de `backend/proveedores.py`. Límites en `.env`. | Las **claves no** van en la tabla, siguen solo en `.env`. |
| `usuarios` | No existe en el servidor. La app no manda quién pregunta. | Por confirmar con Pp: cómo manda la app el usuario sin mandar la contraseña. |
| `conversaciones` | No existe. La app manda las últimas 6 líneas en `contexto` cada vez. | Con esta tabla, el servidor podría armar el contexto solo. |
| `mensajes` | No se guarda. Las rutas `/preguntar` y `/entender` de `main.py` regresan `respuesta`, `proveedor`, `hayMas`, `tipo` y `comando`. | Si `privacidad.py` bloquea un mensaje, se guarda con `texto` en NULL: el dato sensible nunca se escribe. |
| `uso_diario` | `backend/uso_diario.json` (`fecha`, `conteo`, `avisados`), escrito por `uso.py`. | Misma lógica: avisa al 80% y al 100% salta al respaldo. |

## Pendiente de decidir

- **Cuánto tiempo se guardan los mensajes.** Por confirmar con el equipo de normativa ([borradores de políticas](../agentes/normativa/borradores-politicas.md)).
- **Si la IA se queda desactivada** para menores ([decisión 0005](../decisiones/0005-responsable-menores-datos-reales.md)). Afecta a `usuarios.ia_activada`.

Para regenerar la imagen: `mmdc -i diagrama.mmd -o docs/referencia/tablas-servidor.png -b white`, con el bloque `mermaid` de arriba guardado como `diagrama.mmd`.
