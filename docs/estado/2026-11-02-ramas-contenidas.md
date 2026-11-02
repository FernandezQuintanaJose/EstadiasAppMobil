# Ramas que ya están dentro de `main` (2 nov 2026)

> Estado de las ramas al 2 nov 2026, cuando `main` y `desarrollo` van en `8e92f97`. Revisado comparando archivo por archivo y función por función. **Antes de empezar una tarea, parte de `main` actual**; no de una de las ramas viejas de esta lista.

## Qué hacer si programas sobre una de estas ramas

No fusiones estas ramas con `main`: chocarían (de 4 a 14 archivos) y, en dos casos, revertirían trabajo que otros agregaron después. Lo que tenían de valor ya está en `main`. Crea tu rama nueva desde `main`:

```
git fetch origin
git switch -c moduloNN/descripcion origin/main
```

## Ramas con commits que `main` no tiene, pero cuyo contenido ya está

| Rama | De quién | Último commit | Qué traía | Dónde está ahora | Qué hacer |
|---|---|---|---|---|---|
| `modulo03/base-datos` | Alejandro | 25 oct | Base en SQLite (`bd.py`), historial y panel de uso | `db.py` con SQLAlchemy y Neon, más `historial.py`, `panel.py` y `uso.py`, fusionados desde `modulo03/servidor-base-datos` | Borrar la rama |
| `modulo07/entorno-colores` | Jesús | 12 oct | Detección de colores y billetes | `BilleteMx.kt`, `NombreColor.kt`, `EtiquetasEntorno.kt` y `docs/Pruebas/billetes.md`, idénticos en `main` | Borrar la rama. Fusionarla borraría parte de `prompts_vision.py` |
| `feature/lector-entorno-mejorado` | Jesús | 3 oct | Lector de documentos y modelo de objetos | El modelo `efficientdet_lite0.tflite` es idéntico; la pantalla del lector se rediseñó después y las tarjetas viejas (`BigActionCard`, `NavCard`) ya no existen | Borrar la rama |

## Ramas que no tienen nada que `main` no tenga

`ajuste/mic-mitad`, `mejoras-30oct`, `docs/decisiones-0008-0016`, `docs/decisiones-30oct`, `docs/fichas`, `integracion-entorno-colores`, `modulo6_aprendizaje`, `modulo03/servidor-base-datos` (PR #7), `modulo03/integracion`, `modulo03/imagenes`, `modulo03/ver-limite-imagen`, `modulo03/vision-proveedores`, `modulo03/servidor-tablas-pruebas`, `modulo03/backend-asistente`, `modulo03/historial-chats-alonso`, `modulo07/camara-hub-entorno`, `rama-miranda-sensor-camara`, `alonso/interfaz-actividades-main`, `feature/interfaz-invidentes`. Todas se pueden borrar cuando su dueño quiera.

## Lo que `main` cambió y que conviene saber antes de programar

- Las cuentas ya no se piden con `SessionRepository`, sino con la interfaz `ServicioDeCuentas` (la implementa `CuentasLocales`). Ver [cuentas-servicio.md](../cuentas-servicio.md).
- Las fallas de la IA ahora tienen `REJECTED` y `NOT_AUTHORIZED`, además de las de antes (`AiFailure`). El 400 con una frase del servidor se dice tal cual.
- Las contraseñas se guardan con Argon2id (`PasswordHasher`).
- El encabezado de cada pantalla (`ScreenTopBar`) queda fijo arriba cuando está dentro de `ColumnaDesplazable`.
- La dirección del servidor de la IA y la clave de la app salen de `local.properties` (`cecapi.servidor`, `cecapi.claveApp`); ver `backend/PUBLICAR.md`.
