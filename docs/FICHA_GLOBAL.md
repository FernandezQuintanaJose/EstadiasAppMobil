# Ficha global: ecosistema de accesibilidad CECAPI

Responsable: José Fernández Quintana. Estadía sept–dic 2026, UTD Durango.
Fecha de la ficha: 28 oct 2026. Las pruebas están previstas hacia el 10 nov 2026.
Regla: todo dato no verificado se marca como "SIN VERIFICAR". No se inventan datos.

## 1. Objetivo
Un ecosistema accesible para personas con discapacidad visual atendidas por CECAPI: app móvil Android, servidor de IA, asistente web con chatbot, sitio institucional y app para Windows. Un solo servidor de IA atiende a todos los clientes.

## 2. Componentes y repositorios
| Componente | Repositorio | Equipo |
|---|---|---|
| App Android | EstadiasAppMobil | Móvil B (núcleo, voz) |
| Servidor de IA | EstadiasAppMobil (carpeta backend) | Móvil A |
| Asistente web y chatbot | PaginaAsistenteCECAPI | Equipo de 3 (Móvil A puede apoyar) |
| Sitio CECAPI | PaginaCECAPI | Equipo de 2 (posible asesor externo al final) |
| App Windows | WindowsCECAPI | Alonso |

Titular de todas las cuentas: José Fernández Quintana, salvo decisión posterior de CECAPI.

## 3. Arquitectura
- Clientes (Android, Windows, web) -> HTTPS -> servidor FastAPI en Render -> Gemini (principal) y Groq (respaldo, y viceversa) y base PostgreSQL en Neon.
- Cuenta: inicio de sesión con Google. El servidor verifica el token (`id_token.verify_oauth2_token`) y emite o acepta un token por usuario. Una llave compartida dentro del cliente es extraíble y no debe usarse como único control.
- Cupos por usuario, no por IP.
- Windows replica la app móvil y oculta o desactiva lo que no se pueda hacer.
- Idioma: solo español.

## 4. Reglas comunes
### Texto y formato
- Respuestas de la IA sin markdown, viñetas, tablas, emojis ni enlaces (ya está en el prompt del servidor). Los lectores de pantalla leen los emojis.
- Interfaces con etiquetas y encabezados reales; todo control operable con lector de pantalla y teclado.

### Caché
- Respuestas autenticadas del servidor: `Cache-Control: no-store`.
- Recursos estáticos del sitio con nombre versionado y caché larga.
- Al cerrar sesión se borran datos locales y caché.
- Nada de datos personales en caché compartida ni en registros.

### Privacidad y legal (a revisar por asesor; no sustituye asesoría)
- Marco: LFPDPPP 2025 (arts. 2, 8, 10, 15, 21–26, 35–36). Aviso simplificado e integral. La discapacidad puede revelar datos de salud: tratar como datos sensibles.
- Uso solo de adultos: declaración 18+ con registro de aceptación. Iniciar sesión con Gmail no prueba la mayoría de edad.
- Términos de Gemini: 18+; contenido del nivel gratuito puede usarse para mejorar productos. Informarlo al usuario.
- Microsoft Store: políticas 10.5.x, 11.11 y 11.16.
- Google OAuth exige política de privacidad pública.
- Las políticas se leen en pantalla y por audio, con botones: repetir, siguiente, aceptar, rechazar, después. Omitir la lectura no equivale a aceptar.
- Filtro de privacidad del servidor (`privacidad.py`): cubre correo, teléfono, tarjeta, CURP, RFC y contraseñas. No cubre nombres, domicilios ni salud. Decidir si se amplía.

### Seguridad
- Llaves y cadenas de conexión solo en variables de entorno del servidor. Nunca en chat ni repositorio. (Revisión del 28 oct: sin coincidencias en las 16 ramas de EstadiasAppMobil.)
- HTTPS obligatorio. Antes de publicar: auditoría OWASP Top 10:2025.
- Evitar PHP en todo el ecosistema.

## 5. Costos (MXN y USD, mensuales y anuales)
Los precios cambian; confirmar antes de contratar. Lo que NO está confirmado se marca.
- Dominio .com: precio SIN VERIFICAR (confirmar en Cloudflare). Lo paga el titular.
- Render (servidor): el plan de pago que evita la suspensión por inactividad fue considerado caro (cerca de 7 USD al mes; confirmar precio vigente). Alternativas a evaluar: Fly.io (ajuste de memoria SIN VERIFICAR) y otras.
- Neon (PostgreSQL): plan gratuito; se suspende tras 5 min sin uso; tiempo de despertar SIN VERIFICAR.
- Gemini y Groq: niveles gratuitos; cuotas exactas SIN VERIFICAR (consultar AI Studio y consola de Groq).
- Microsoft Store: cuenta individual gratuita (ID y selfie); certificación hasta 3 días hábiles; firma y hospedaje gratuitos.
- GitHub Pages y Releases: gratuitos. Minutos de Actions SIN VERIFICAR.
- Tabla final mensual y anual en MXN: completar tras la reunión con CECAPI y las verificaciones.

## 6. Problema del arranque en frío
Render ~1 min y Neon 5 min de suspensión. Para una persona ciega la espera sin señal es el problema, no el ping. Medidas: ping de calentamiento al abrir la app, ruta de salud que no toque la base, mensaje por voz mientras espera y tono de espera.

## 7. Calendario
- 28 oct: reunión con CECAPI (dominio, nombres de IA y app, costos, paleta, asesor legal, lector de pantalla que usan).
- Hasta ~10 nov: integración, servidor con cuentas, políticas con audio, primera versión web y prototipo Windows.
- Después: memorias de todos los equipos y documentos legales.

## 8. Por decidir
1. Dominio y nombres de IA y app.
2. Hosting definitivo del servidor.
3. Si CECAPI cuenta con asesor legal.
4. Lector de pantalla que usan los estudiantes (NVDA, JAWS, TalkBack, Narrator).
5. Ampliar o no el filtro de privacidad.
6. Titularidad final de cuentas.

## 9. Verificaciones pendientes
Empaquetado MSIX de app Kotlin/JVM; guía de Sign in with Google en Android con accesibilidad; tiempo de despertar de Neon; política de Groq sobre menores y datos; visión en Gemini y Groq y cuotas; reconocimiento de voz sin conexión en Windows; tiempo y requisitos de verificación de marca de Google; política de Render sobre pings; Actions.

## 10. Lecturas
- LFPDPPP: diputados.gob.mx (PDF vigente).
- Términos de Gemini API y ayuda de edad de Google.
- Ayuda de Google OAuth y verificación de marca.
- Políticas de Microsoft Store 10.5, 11.11 y 11.16.
- Tarjetas de normas de Proyecto-IA: seguridad, apis-redes, datos-estado, pruebas-qa, publicacion, ux-ui.
