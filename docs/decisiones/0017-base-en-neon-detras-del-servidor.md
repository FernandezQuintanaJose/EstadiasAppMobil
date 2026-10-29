# 0017. La base va en Neon detrás del servidor propio

> Vigente · Dueño: Pp · Decidida el 28 oct 2026 · Reemplaza a la [0014](0014-reglas-por-fila-en-supabase.md) en lo de la plataforma · Fuente: indicación de Pp y fichas del proyecto (`FICHA_BACKEND.md`, `FICHA_GLOBAL.md`)

## Contexto

La decisión 0014 suponía una base en Supabase a la que se llega con una llave pública, por eso pedía reglas por fila (RLS) en cada tabla. La arquitectura que se está construyendo es otra: servidor FastAPI en Render y base PostgreSQL en Neon (plan gratuito, con migraciones de Alembic).

## Decisión (Pp, 28 oct 2026)

- No se usa Supabase. La base compartida (cuentas, instituciones, roles, reportes, aceptaciones de políticas y uso por usuario) va en PostgreSQL en Neon.
- La app y las páginas de los equipos hablan solo con el servidor. Ninguna app, página ni repositorio lleva la cadena de conexión de la base; esa solo vive como variable de entorno del servidor.
- Los permisos los aplica el servidor en cada ruta, con las mismas reglas de la 0014 que siguen vigentes:
  - Lo que no tiene regla queda prohibido.
  - El rol de la persona vive en una tabla propia o en datos que solo cambia el servidor, nunca en datos que la propia persona pueda editar.
  - Quién ve a quién sigue la lógica de la app: el administrador ve todo, el directivo su institución, el educador a sus alumnos, y el alumno y el usuario independiente solo lo suyo.
  - Cada regla se prueba con cada rol y con alguien sin cuenta.
- Alonso sigue diseñando y revisando las reglas, y cada equipo las aplica a sus rutas y tablas.

## Por decidir

- Si además se activa RLS de PostgreSQL como segunda capa de defensa. No está decidido.
- Cómo se manejan las pausas de Neon (se suspende tras 5 minutos sin uso) para quien depende de la voz; ver `FICHA_GLOBAL.md`.

## Consecuencias

- Hay que actualizar las referencias a Supabase en el documento «Plan para equipos» y en las páginas de los equipos.
- Antes de subir cualquier dato real: aviso de privacidad, consentimiento y revisión de seguridad (decisión 0005).
