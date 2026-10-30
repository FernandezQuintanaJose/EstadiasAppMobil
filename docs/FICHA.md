# Ficha: App móvil Android (EstadiasAppMobil)

Equipo: Móvil B (núcleo y voz). Complementa la ficha global (`00_global`).

## Objetivo
App Android accesible, operada por voz, para personas con discapacidad visual, conectada al servidor de IA.

## Tipo, dispositivos y plataformas
App nativa Android: Kotlin 2.0.21, Compose, Room, Hilt, AGP 8.5.2, minSdk 26, targetSdk 35 (versiones congeladas).

## Estado real (auditoría del 28 oct)
- 16 ramas remotas. `main` desactualizada (11 oct). Más recientes: `modulo03/integracion` y `alonso/interfaz-actividades-main` (25 oct).
- Cambios locales de José sin subir (contraseña de 15 letras, quitar acentos, correo, aviso por voz).
- Sin secretos en el repositorio (16 ramas revisadas).
- Contraseña mínima de 4 (`RegisterViewModel.kt:181`, `EditAccountScreen.kt:264`).
- Sin dependencia de inicio de sesión con Google (y ya no se va a usar).
- URL del servidor es marcador de posición; se permite tráfico sin cifrar; la app no envía identidad.
- En `main` no hay archivos de prueba (en ramas hay 38 funciones de prueba).
- Líneas de Kotlin por módulo en `main`: m1 9648, m2 328, m3 498, m4 1224, m5 509, m6 2069, m7 1252.

## Permisos
Revisar el manifiesto de la rama base y justificar cada permiso en el aviso de privacidad. Micrófono: servicio en primer plano de tipo micrófono no puede crearse desde segundo plano (restricción while-in-use) ni iniciarse en BOOT_COMPLETED. SpeechRecognizer requiere conexión.

## Tareas, en orden
1. Integración: elegir rama base (propuesta: `modulo03/integracion`), fusionar el resto, subir cambios locales de José. Bloquea todo lo demás.
2. Red: HTTPS obligatorio, quitar tráfico sin cifrar, URL por configuración (BuildConfig o similar), sin llaves en el cliente.
3. Cuenta propia (decisión del 30 oct, sin Google): registro con usuario, correo y contraseña de 15 caracteres como mínimo, código por correo solo para registrarse y recuperar la contraseña, acceso con correo o usuario, quitar acentos según la memoria, borrar chats y eliminar cuenta, enviar token en cada petición. [REEMPLAZADO: antes era inicio de sesión con Google (Credential Manager).]
4. Políticas: pantallas de aviso de privacidad y términos, leídas en pantalla y por audio (repetir, siguiente, aceptar, rechazar, después), declaración 18+ con registro de aceptación.
5. Escucha en segundo plano: servicio en primer plano con botón explícito de encendido/apagado, iniciado con la app visible; aviso por voz y consentimiento.
6. Arranque del servidor: ping de calentamiento al abrir, mensaje por voz y tono de espera.
7. Banderas de capacidad (para reutilizar la lógica en Windows).
8. Pruebas con TalkBack: voz, formularios, políticas, errores de red.
9. Cerrar sesión: borrar datos locales y caché.
10. Actualizar la memoria del Equipo 1 una vez terminadas las tareas 3 a 5.

## Herramientas por paso
Integración: git. Seguridad: OWASP Security Audit. Código: code-review y code-simplifier. Documentación de librerías: context7. Pruebas: playwright no aplica; usar pruebas instrumentadas y TalkBack.

## Riesgos
Ramas divergentes con conflictos; módulo 1 muy grande; Depende de que el servidor tenga las rutas de cuenta y de un proveedor de correo para los códigos; la escucha en segundo plano puede no ser posible como se redactó en la memoria.

## Costos
Sin costo directo. Cuenta de Google Play no requerida para las pruebas (distribución por APK en GitHub Releases).

## Por decidir
Rama base; si el aviso por voz es obligatorio en cada arranque; alcance de la escucha en segundo plano.
