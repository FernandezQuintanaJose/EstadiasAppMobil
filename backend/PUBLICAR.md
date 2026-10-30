# Publicar el servidor del asistente (Render + Neon)

Decisión 0017: el servidor va en Render y la base en Neon (PostgreSQL), los dos en plan gratuito.
Render da HTTPS solo, con una dirección `https://<nombre>.onrender.com`. La app debe usar esa
dirección: Android no deja mandar `http://` a internet.

## 1. Base de datos en Neon
1. Entra a https://console.neon.tech y crea un proyecto (región: la más cercana a la de Render, p. ej. AWS us-east-2 si Render está en Ohio).
2. En **Connection Details** copia la cadena de conexión (`postgresql://...?sslmode=require`). Esa es `DATABASE_URL`.
3. No la pegues en ningún archivo del repo ni en la app: solo en Render (paso 2).

## 2. Servidor en Render
1. Entra a https://dashboard.render.com → **New** → **Blueprint** y elige el repositorio. Render lee `render.yaml` (en la raíz del repo).
2. Te pedirá los valores que no van en el repo: `DATABASE_URL` (la de Neon), `GROQ_API_KEY` y `GEMINI_API_KEY` (puede ir vacía mientras Gemini siga con 403).
3. `CECAPI_CLAVE_APP` la genera Render al azar. Cópiala de **Environment**: la necesita la app (paso 3) y el panel.
4. Al arrancar, Render corre `alembic upgrade head` (crea las tablas en Neon) y luego el servidor.
5. Prueba: abre `https://<nombre>.onrender.com/` (debe decir "CECAPI backend activo") y `https://<nombre>.onrender.com/panel` (escribe la clave).

## 3. La app
En `local.properties` (no se sube al repo) de la computadora donde se compila el APK:

```
cecapi.servidor=https://<nombre>.onrender.com
cecapi.claveApp=<la CECAPI_CLAVE_APP de Render>
```

La clave dentro del APK se puede sacar descompilándolo: frena el uso casual, no a alguien decidido.
El control de verdad llegará con las cuentas y el token por usuario (ficha del backend, tarea 4).

## Lo que hay que saber
- **Arranque en frío.** En el plan gratuito Render apaga el servidor tras 15 minutos sin uso y tarda cerca de un minuto en despertar; Neon se suspende tras 5 minutos. La app ya espera hasta 75 segundos y avisa por voz (`AiMessages.WAKING`). La ruta `/` no toca la base: sirve para despertarlo.
- **Límite por minuto.** Con `--proxy-headers` el servidor ve la IP real de cada teléfono. Sin eso, todos compartirían la IP del proxy de Render y el límite de 20 por minuto sería para todos juntos.
- **Uso de la IA.** `/panel` muestra cuántas peticiones lleva cada IA contra `*_LIMITE_DIARIO`. En Groq se acaban antes los tokens (200,000 al día) que las peticiones: ver `.env.example`.
- **Antes de datos reales:** aviso de privacidad, consentimiento y revisión de seguridad (decisión 0005) y la auditoría OWASP (ficha del backend, tarea 11).
