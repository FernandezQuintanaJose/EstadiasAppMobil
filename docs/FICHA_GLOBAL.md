# Ficha global: ecosistema de accesibilidad CECAPI

Responsable: José Fernández Quintana. Estadía sept–dic 2026, UTD Durango.
Fecha de la ficha: 28 oct 2026. Las pruebas están previstas hacia el 10 nov 2026.
Regla: todo dato no verificado se marca como "SIN VERIFICAR". No se inventan datos.

## 0. Decisiones vigentes (30 oct 2026)
Estas decisiones mandan sobre cualquier línea contraria de esta ficha (esas líneas se marcan "REEMPLAZADO").
1. **Inicio de sesión:** cuenta propia con nombre de usuario o correo y contraseña. **Sin acceso con Google.** La contraseña lleva mínimo 15 caracteres y se guarda con Argon2id. El correo se valida con un código que se usa **solo** para registrarse y recuperar la contraseña. Regla de José: si mandar correos costara dinero, se haría sin código. Los planes gratuitos encontrados alcanzan para unas 10 personas (Brevo, 300 al día; Resend, 100 al día pero con dominio propio; cifras de fuentes de terceros, SIN VERIFICAR en las páginas oficiales), así que va con correo. Proveedor de correo: por elegir.
2. **Windows:** solo archivo `.exe` por descarga directa desde GitHub Releases. **Microsoft Store descartada definitivamente.** Pendiente: aviso de SmartScreen por falta de reputación (certificado de firma SIN DECIDIR).
3. **Android:** solo APK desde GitHub Releases, sin Google Play.
4. **Datos:** se conservan mientras exista la cuenta, sin borrado por inactividad. Se eliminan al borrar la cuenta o cuando la persona lo pida. Debe haber servicio para borrar chats (individuales) y para eliminar la cuenta.
5. **Edad:** el registro exige confirmar 18 años o más y las políticas se aceptan antes de la primera pregunta a la IA, con fecha y versión.
6. **Sitio CECAPI:** colores verde y blanco (dato de José, SIN VERIFICAR en fuentes de CECAPI). Servicios gratuitos de una institución sin fines de lucro, y lista de servicios dados por José el 29 oct: cocina, encuadernación, educación física, computación, elaboración de piñatas, aprendizaje de ciclismo y orientación y movilidad.
7. **Descargas:** el asistente web aloja la página de descargas (APK y `.exe`, enlaces a GitHub Releases). El sitio CECAPI no aloja descargas.
8. Lista de verificación del código: `docs/INFORME_VERIFICACION.md` (repositorio del asistente web).

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
- Cuenta: usuario o correo y contraseña, sin Google (sección 0). El servidor guarda la contraseña con Argon2id y emite un token por usuario. Una llave compartida dentro del cliente es extraíble y no debe usarse como único control. [REEMPLAZADO el 30 oct: antes decía inicio de sesión con Google y verificación del token con `id_token.verify_oauth2_token`.]
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
- Uso solo de adultos: declaración 18+ con registro de aceptación. Tener un correo o una cuenta no prueba la mayoría de edad: el registro pide confirmarla.
- Términos de Gemini: 18+; contenido del nivel gratuito puede usarse para mejorar productos. Informarlo al usuario.
- [REEMPLAZADO el 30 oct: Microsoft Store descartada; ya no aplican sus políticas 10.5.x, 11.11 y 11.16.]
- [REEMPLAZADO el 30 oct: ya no hay Google OAuth.]
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
- [REEMPLAZADO el 30 oct: Microsoft Store descartada.] Windows se distribuye como `.exe` por GitHub Releases (gratuito). Certificado de firma de código: SIN DECIDIR; sin él, Windows puede mostrar el aviso de SmartScreen.
- Correo para los códigos: planes gratuitos (Brevo, 300 al día; Resend, 100 al día con dominio propio). SIN VERIFICAR en las páginas oficiales. Proveedor por elegir.
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
Empaquetado `.exe` de app Kotlin/JVM (Compose Desktop); proveedor de correo (límite diario, entrega y si pide dominio); tiempo de despertar de Neon; política de Groq sobre menores y datos; visión en Gemini y Groq y cuotas; reconocimiento de voz sin conexión en Windows; certificado de firma de código y SmartScreen; política de Render sobre pings; Actions.

## 10. Lecturas
- LFPDPPP: diputados.gob.mx (PDF vigente).
- Términos de Gemini API y ayuda de edad de Google.
- [REEMPLAZADO el 30 oct: ya no aplica la ayuda de Google OAuth.]
- [REEMPLAZADO el 30 oct: ya no aplican las políticas de Microsoft Store.]
- Tarjetas de normas de Proyecto-IA: seguridad, apis-redes, datos-estado, pruebas-qa, publicacion, ux-ui.
