# Revisión de calidad y camino a la entrega (4 nov 2026)

> Qué se probó en los cinco proyectos, qué se corrigió, qué hallazgos quedan y qué le toca a cada equipo para entregar. Todo lo que no se pudo comprobar aquí está marcado como SIN VERIFICAR.

## 1. Estado de las pruebas automáticas

| Proyecto | Repositorio | Resultado el 4 nov |
|---|---|---|
| App Android | EstadiasAppMobil (`app/`) | 60 pruebas correctas (`testDebugUnitTest`) |
| Servidor | EstadiasAppMobil (`backend/`) | 241 correctas y 6 `xfail` (huecos conocidos, sección 3) |
| Asistente web | PaginaAsistenteCECAPI | `revisar` 88, `flujo` 32, `cuenta` 48, `descargas` 7, `hostil` 28 |
| Sitio CECAPI | PaginaCECAPI | `revisar` 88, `galeria` 7 |
| App Windows | WindowsCECAPI | 23 correctas (9 son `HostilTest`) |

Las ramas con las correcciones de esta revisión se llaman `qa/correcciones` en PaginaCECAPI, PaginaAsistenteCECAPI y WindowsCECAPI. Hay que fusionarlas a `main` después de revisarlas.

**Lo que ninguna prueba automática cubre:** lectores de pantalla (NVDA, JAWS, Narrator, TalkBack), la voz de Windows en español, el instalador `.exe` en un equipo limpio, el APK de publicación corriendo en un teléfono y todo lo que dependa de Render, Neon o el proveedor de correo, que aún no están publicados.

## 2. Qué se corrigió (ya en las ramas `qa/correcciones`)

1. **Web, chat bloqueado:** un 200 que no era JSON (portal WiFi, proxy) dejaba el chat bloqueado para siempre. Ahora se avisa y el chat sigue usable.
2. **Web y Windows, contexto de más de 1000 caracteres:** cada línea llevaba el prefijo "Persona: " o "Asistente: " encima de los 1000 caracteres, y el servidor rechaza líneas de más de 1000 (error 422). Ahora el prefijo cuenta dentro del límite.
3. **Windows, excepciones sin atrapar:** una dirección del servidor o una clave de la app mal escritas en `local.properties` lanzaban una excepción y dejaban la conversación esperando para siempre. Ahora dicen que el servidor no está configurado.
4. **Windows, respuesta en blanco:** se leía en silencio. Ahora se avisa.
5. **Windows, preferencias:** `escalaTexto=NaN` en el archivo de preferencias pasaba sin filtro.
6. **Web, descargas:** `descargas.v3.js` aceptaba enlaces de releases de cualquier dueño de GitHub. Ahora solo `FernandezQuintanaJose/EstadiasAppMobil` y `WindowsCECAPI`, sin usuario ni clave en la dirección. Si los repositorios cambian de dueño, se cambia en ese archivo.
7. **Servidor de pruebas local (web):** la raíz del sitio daba 404 en Windows, y con eso fallaba la prueba del collage.

## 3. Hallazgos que quedan (decide el equipo dueño)

| # | Hallazgo | Quién | Qué hacer |
|---|---|---|---|
| H1 | **Límite por minuto falseable.** `render.yaml` arranca con `--forwarded-allow-ips '*'`. Con ese valor uvicorn 0.54 toma la IP más a la izquierda de `X-Forwarded-For`, y esa la escribe quien manda la petición. Reproducido con el middleware de uvicorn, no en Render. Que Render agregue la IP real a la derecha es SIN VERIFICAR. El límite diario de la IA sigue protegiendo la cuota. | Servidor | Verificar en Render qué manda en `X-Forwarded-For`. Si agrega la IP real a la derecha, leer ese valor en `_limitar` en lugar de confiar en la izquierda. Ojo: si luego se pone Cloudflare delante, hay dos saltos y la regla cambia. |
| H2 | **Filtro de privacidad con huecos.** No detecta: correo escrito como "juan [arroba] example punto com", "juan(at)example.com" o "juan @ example . com"; teléfono con números en palabras; CURP con espacios; contraseña en inglés. Están como `xfail` estricto en `backend/test_hostil.py`: cuando se arreglen, pytest pedirá quitar la marca. | Servidor | Decidir cuáles cubrir. Cuidado con los falsos positivos: bloquear una pregunta normal también es un error. |
| H3 | **La web no puede preguntar todavía:** falta CORS en `backend/main.py`, y `/preguntar` pide `X-Clave-App`, que una página pública no puede llevar. | Servidor | Ver README de PaginaAsistenteCECAPI, "Estado frente al servidor real", opciones (a) y (b). |
| H4 | **Documentación pública sin clave:** `/docs`, `/redoc` y `/openapi.json` responden 200 sin clave. No revelan la clave, pero muestran todas las rutas. | Servidor | Decidir si se apagan en producción (`docs_url=None`). |
| H5 | **La clave de la app queda dentro del `.exe` y del APK.** Frena el uso casual, no a alguien decidido. | Servidor | El control real es el token por usuario (cuentas). |
| H6 | **Servidor de pruebas local** (`npm run servir`) también sirve `.git/` y `node_modules/`. | Webs | Solo usarlo en la propia computadora, nunca exponerlo en red. |

**Lo que sí aguantó** (probado de forma hostil): HTML inyectado en preguntas, respuestas, nombres y JSON de descargas o galería; rutas con `..`, `%2e%2e`, `\` o `//`; 15 entradas raras en `/preguntar` (vacía, enormes, tipos equivocados, JSON roto, cuerpo de 5 MB); claves incorrectas, en mayúsculas o con espacio; fotos falsas, truncadas, de 10 MB y de 30000 × 30000 píxeles; redirecciones a otro servidor desde Windows; `localStorage` y archivos de preferencias manipulados; red caída a mitad de una pregunta; spam de Enter y de clics.

## 4. Pendientes por equipo para entregar

El orden importa: lo de abajo depende de que el servidor esté publicado.

### Servidor (Móvil A)
- [ ] Publicar en Render y Neon siguiendo `backend/PUBLICAR.md` y dar la URL a los demás equipos.
- [ ] CORS para `https://fernandezquintanajose.github.io` y el dominio final (H3).
- [ ] Rutas de cuenta, aceptación y borrado del contrato del README de PaginaAsistenteCECAPI (registro, verificación, acceso, recuperar, restablecer, aceptación, borrar chats, eliminar cuenta), con Argon2id.
- [ ] Elegir proveedor de correo (Brevo o Resend; las cifras de sus planes gratuitos son de terceros y están SIN VERIFICAR).
- [ ] Revisar H1, H2 y H4.
- [ ] Antes de publicar: auditoría OWASP Top 10:2025 (regla de la ficha global).

### Asistente web (equipo de 3)
- [ ] Poner la URL real en `assets/js/config.v2.js` (`apiBase`) y, si no es `*.onrender.com`, en `connect-src` de la CSP de `index.html`.
- [ ] Llenar `config.rutas` cuando el servidor tenga las rutas.
- [ ] Decidir `permitirSinCuenta`.
- [ ] Completar los datos entre corchetes de `politicas.html` y el correo para derechos ARCO (lo confirma CECAPI); revisión legal.
- [ ] Llenar `datos/descargas.v1.json` cuando existan los Releases.
- [ ] Probar con NVDA, Narrator y TalkBack.
- [ ] Publicar: Settings, Pages, rama `main`. Con dominio propio, cambiar `<base href>` de `404.html`.
- [ ] Cada vez que cambie un `.js` o `.css`, subirle la versión al nombre y actualizar los enlaces (caché).

### Sitio CECAPI (equipo de 2)
- [ ] Textos con recuadro `pendiente`: historia, qué incluye cada servicio, horarios y requisitos. No escribir nada que CECAPI no haya confirmado.
- [ ] Contacto: confirmar código postal (34226 o 34234) y el segundo teléfono 618 455 4155.
- [ ] Fotos del collage: texto alternativo y permiso por escrito de quienes aparecen.
- [ ] Aviso de privacidad y términos con revisión legal.
- [ ] Probar con NVDA, Narrator y TalkBack; publicar en Pages.

### App Windows (Alonso)
- [ ] Crear los secretos `CECAPI_SERVIDOR` y `CECAPI_CLAVE_APP` en GitHub (Settings, Secrets and variables, Actions).
- [ ] Ver que el flujo `windows.yml` genere el `.exe` y probar el instalador en un equipo Windows limpio (nunca se ha verificado).
- [ ] Probar la voz en español y NVDA/JAWS (Java Access Bridge).
- [ ] Decidir el certificado de firma; sin él, SmartScreen avisa.
- [ ] Publicar con etiqueta `v1.0.x` y agregar la versión, el tamaño y el SHA-256 en `datos/descargas.v1.json` del asistente web.
- [ ] Consultas (`/documento`) y abrir foto para Entorno (`/ver`), y decidir cuentas locales o del servidor.
- [ ] Requiere JDK 21.

### App Android (Móvil B)
- [ ] Firmar el APK de publicación y probarlo en un teléfono (el ocultamiento de código puede romper cosas que solo se ven ejecutando).
- [ ] Revisar por qué el APK pesa 222 MB.
- [ ] Cambiar la dirección del servidor de ejemplo por la real, con https.
- [ ] Pruebas del teléfono de `2026-11-02-pendientes.md` y una pasada completa por voz con TalkBack.
- [ ] Publicar en GitHub Releases (sin Google Play) y registrar en `datos/descargas.v1.json`.

## 5. Orden sugerido para una semana (7 a 11 nov; la ficha global prevé pruebas hacia el 10 nov)

1. **Primeros 2 días:** Servidor publicado, CORS y URL en manos de todos. Mientras, web y Windows fusionan `qa/correcciones` y los de contenido piden a CECAPI los datos pendientes.
2. **Días 3 y 4:** cuentas y correo; web y Windows configuran `apiBase` y secretos; Android firma el APK.
3. **Día 5:** generar `.exe` y APK, subir Releases y llenar `descargas.v1.json`.
4. **Días 6 y 7:** pruebas con lector de pantalla y con personas, corregir, y publicar en Pages y en el dominio que defina el titular.

Lo que depende de terceros (datos de CECAPI, revisión legal, dominio, certificado de firma) puede no caber en la semana; conviene pedirlo desde hoy.

## 6. Cómo repetir estas pruebas

- Webs: `npm install`, `npx playwright install chromium`, y `npm run revisar`, `galeria`, `flujo`, `cuenta`, `descargas`, `hostil` según el repositorio.
- Windows: `./gradlew test` con JDK 21.
- Servidor: en `backend/`, `python -m pytest`.
- Android: `./gradlew testDebugUnitTest`.
