# Mapa de carpetas: dónde está cada cosa

Escrito el 18 de noviembre de 2026. Sirve para saber en qué carpeta buscar según el tipo de trabajo.
No se movieron archivos: Render (`render.yaml`, `rootDir: backend`), Gradle y las importaciones de Python dependen de estas rutas. Moverlos requiere cambiar y volver a probar todo.

## Proyecto de la app (CECAPI-App) — nombre público: ALIA

| Parte | Carpeta | Qué hay |
|---|---|---|
| **Frontend (app Android)** | `app/src/main/java/com/cecapi/app/` | Pantallas (Compose), voz, navegación, tema |
| Pantallas por módulo | `feature/modulo1_aplicacionprincipal` … `modulo7_entorno` | Inicio, cuentas, ajustes, chats, lector de documentos, aprendizaje, entorno |
| Voz (entrada y salida) | `core/voice/` | Motor de voz, reconocimiento, palabra de activación, tonos |
| Componentes de pantalla | `core/ui/` | Botones, tarjetas, micrófono fijo |
| Tema y colores | `core/theme/` | `Color.kt`, `Type.kt`, `Theme.kt` |
| Recursos (textos, iconos) | `app/src/main/res/` | `values/strings.xml` (nombre ALIA), `drawable/` (logo) |
| **Base de datos en el teléfono** | `core/data/`, `app/schemas/` | Room (SQLite), migraciones |
| Pruebas de la app | `app/src/test/` | 91 pruebas unitarias |
| **Backend (servidor)** | `backend/` | FastAPI: IA, privacidad, seguridad, cuentas, panel; sus `test_*.py` |
| Migraciones del servidor | `backend/migraciones/` | Alembic |
| **Base de datos del servidor** | `database/` | `schema.sql`, `servidor.sql` |
| Despliegue | `render.yaml`, `backend/PUBLICAR.md` | Render + Neon |
| Documentación | `docs/` | Decisiones (`decisiones/`), estado (`estado/`), fichas, logo (`logo/`), pruebas (`Pruebas/`) |

## Otros proyectos

| Proyecto | Tipo | Dónde |
|---|---|---|
| CECAPI-Pagina | Frontend web (sitio de la institución) | `index.html`, `assets/`, `datos/*.json` (contenido), `admin/` (editor), `pruebas/` |
| CECAPI-Asistente | Frontend web (asistente y descargas) | `index.html`, `descargas.html`, `assets/`, `datos/`, `pruebas/` |
| CECAPI-Windows | App de escritorio (Kotlin/Compose) | `src/main/kotlin/cecapi/`, `docs/` |

El asistente web y la app de Windows no tienen servidor propio: usan el servidor de `CECAPI-App/backend`.

## Archivos fuera de los repositorios
- `CECAPI_EVALUACION_Y_SOLICITUD.md`: evaluación y lo que se pide a CECAPI.
- `evidencia_cap4_5.md` y `evidencia_capturas/`: evidencias para los capítulos 4 y 5.
