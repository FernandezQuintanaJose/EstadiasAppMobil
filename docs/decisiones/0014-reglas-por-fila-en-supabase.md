# 0014. Supabase con reglas por fila (RLS) en todas las tablas

> Vigente · Dueño: Pp · Decidida el 8 oct 2026 · Fuente: documento «Plan para equipos»

## Contexto

La base compartida va en Supabase (cuentas, instituciones, roles, reportes y las tablas de las páginas de los equipos). Sin reglas por fila, cualquiera con la llave pública puede leer o escribir todo.

## Decisión (Pp, 8 oct 2026)

- Cada tabla lleva RLS activado desde el momento en que se crea, aunque la regla sea «nadie». Lo que no tiene regla queda prohibido.
- Una regla por operación: leer, crear, cambiar y borrar.
- El rol de la persona vive en una tabla propia o en datos que solo cambia el servidor, nunca en datos editables por la propia persona (`user_metadata`).
- La llave de servicio (`service_role`) se salta todas las reglas: solo vive en el servidor, nunca en la app, en una página ni en GitHub.
- Raíz (Alonso) diseña y revisa las reglas; cada equipo las aplica a sus tablas. Cada regla se prueba con cada rol y con alguien sin cuenta.
- Quién ve a quién sigue la misma lógica que la app: el administrador ve todo, el directivo su institución, el educador a sus alumnos, y el alumno y el usuario independiente solo lo suyo.

## Consecuencias

- La tabla completa de qué lee y qué escribe cada rol está en el documento «Plan para equipos».
- Alonso incluye las reglas en la lista fija de revisión de cada panel nuevo.
- Antes de subir cualquier dato real: aviso de privacidad, consentimiento y revisión de seguridad (decisión 0005).
