# 0008. Android: solo el APK desde nuestra página, y costo cero

> Vigente · Dueño: Pp · Decidida el 8 oct 2026 · Fuente: documento «Plan para equipos»

## Contexto

Se quiere llegar a la mayor cantidad de teléfonos Android, y todo lo que cueste dinero se evita salvo que sea indispensable.

## Decisión (Pp, 8 oct 2026)

- En Android solo se distribuye el APK desde la página de descargas del proyecto, con su huella SHA-256 y un guion de instalación por voz.
- Microsoft Store y Linux sí, porque son gratis. La versión web instalable (PWA) cubre computadoras.
- Google Play y App Store quedan descartados por ahora.
- El único gasto aceptado es el dominio, de unos 10 USD al año. Lo demás cabe en planes gratuitos (Render o Cloud Run, Supabase, Cloudflare Pages, GitHub Releases).

## Consecuencias

- Desde Android 13, un APK descargado de una página no puede activar la accesibilidad hasta que la persona permite la «configuración restringida»; el guion de descarga debe explicarlo.
- Google anunció que pedirá registrar a quien distribuya APK, aunque sea fuera de Play. Alonso revisa cada mes la fecha para México y si hay que pagar algo.
- Reemplaza la nota de la decisión 0001 sobre Google Play: el `targetSdk` solo se sube si algún día se publica en la tienda.
