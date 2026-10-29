# Ficha: Servidor de IA (carpeta backend de EstadiasAppMobil)

Equipo: Móvil A. Complementa la ficha global (`00_global`).

## Objetivo
API única que atiende a la app, la web y Windows: chat con Gemini y Groq con respaldo mutuo, cuentas, cupos y registro mínimo.

## Estado real (rama `modulo03/integracion`)
- FastAPI, Uvicorn, Pydantic. `main.py` (rutas, dependencia Protegida), `seguridad.py` (comparación segura de llave y límite por minuto por IP), `privacidad.py` (patrones), `proveedores.py` (prompt con datos de CECAPI).
- `requirements.txt` sin versiones fijas: fastapi, uvicorn, python-dotenv, google-genai, groq, pillow.
- `.env.example`: IA_ORDEN=groq,gemini; GEMINI_MODEL=gemini-3.8-flash; GROQ_MODEL=openai/gpt-oss-20b; GROQ_VISION_MODEL=qwen/qwen3.8-27b (vista previa); LIMITE_POR_MINUTO=20; CECAPI_CLAVE_APP vacía.
- No hay cuentas, base de datos, token por usuario, CORS ni configuración de Render/Neon.

## Tipo y plataforma
Servicio web en Render; base PostgreSQL en Neon (no Render Postgres). Alembic y SQLAlchemy.

## Tareas, en orden
1. Fijar versiones en `requirements.txt` y probar arranque limpio.
2. Ruta de salud que no toque la base de datos.
3. Modelos y migraciones Alembic en Neon: usuarios, aceptaciones de políticas (con versión y fecha), uso por usuario.
4. Verificación del token de Google con `id_token.verify_oauth2_token(token, request, audience)`; emitir o validar token por usuario.
5. Cupos por usuario y por día, sustituyendo el límite por IP. Con proxy: `--proxy-headers` y `--forwarded-allow-ips` para ver la IP real.
6. CORS limitado a los dominios propios.
7. Respaldo entre Gemini y Groq con tiempos de espera; manejo de errores en español para lectores de pantalla.
8. Encabezado `Cache-Control: no-store` en respuestas autenticadas.
9. Decidir si `privacidad.py` se amplía (nombres, domicilios, salud).
10. Despliegue en Render con variables de entorno; prueba de arranque en frío y mensaje al cliente.
11. Auditoría OWASP Top 10:2025 antes de publicar.

## Herramientas
DB Schema Spy (esquema), OWASP Security Audit, context7 (FastAPI, SQLAlchemy, Alembic, google-auth), code-review.

## Riesgos
Arranque en frío de Render (~1 min) y suspensión de Neon (5 min): impacto directo en usuarios ciegos. Modelo de Groq para visión es de vista previa. Cuotas gratuitas SIN VERIFICAR. El nivel gratuito de Gemini puede usar el contenido para mejorar productos: informarlo en el aviso.

## Costos
Neon y modelos en nivel gratuito. Render: plan sin suspensión cercano a 7 USD al mes (confirmar precio vigente y valorar alternativas). Cálculo en MXN pendiente de verificación.

## Por decidir
Hosting definitivo; política de retención de datos; datos que se guardan del chat (idealmente ninguno o mínimos).
