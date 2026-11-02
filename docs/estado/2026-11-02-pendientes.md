# Pendientes al 2 nov 2026

> Lista de Pp y de las pruebas de esta semana. Lo que ya está hecho no aparece.

## Por pedido de Pp (después de las cuentas)

- ~~Widgets~~ Hecho el 2 nov: solo micrófono, Actividades y Cámara.
- ~~Logo~~ Hecho el 2 nov: el logo elegido es el monograma VA (azul y blanco, con una onda en la barra de la A): es el ícono de la app, de los widgets y está en el inicio. Si el nombre final deja de ser VozAcceso, hay que rehacerlo; las otras propuestas están en `docs/logo/`. El nombre que sale bajo el ícono sigue siendo «CECAPI» hasta decidir el nombre final.

## Hecho el 2 nov por seguridad y estabilidad

- Widgets: solo abren Actividades o Cámara.
- Tráfico sin cifrar solo en depuración; código optimizado y oculto en la versión de publicación.
- Aviso y pantalla de esperar o cerrar cuando el teléfono está muy cargado o caliente.
- Guía para llevar la app a otro sistema: [guias/replicar-en-otras-plataformas.md](../guias/replicar-en-otras-plataformas.md).

## Antes de publicar

- Firmar el APK de publicación y probarlo corriendo en un teléfono: el ocultamiento de código puede romper cosas que solo se ven ejecutando.
- Revisar por qué el APK pesa 222 MB.
- Cambiar la dirección del servidor de ejemplo por la real, con https.

## Pruebas pendientes en el teléfono

- Aviso de «parece un diagrama» al leer un mapa, y la rueda de carga en la cámara, Consultas y Clasificación.
- Nivel 3 de Sonidos y los juegos con cámara (Busca y encuentra, La caja misteriosa) con objetos reales.
- Una pasada completa como persona que no ve, solo por voz.
- Probar con otro teléfono y con letra grande.

## Depende de otras personas o de publicar

- Publicar el servidor, la base en Neon y el proveedor de correo; con eso, rutas de cuentas y el código por correo.
- Aviso de privacidad y términos, con revisión.
- Nombre y dominio finales.
- Gamificación: faltan las reglas (bonificadores, inventario, rachas, niveles).
- Ubicación (nota 0018): decisiones de Pp.
