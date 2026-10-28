# 0011. Versión mínima de Android: la más baja posible

> Aceptada; falta el cambio en Gradle · Dueño: Pp · Decidida el 8 oct 2026 · Fuente: documento «Plan para equipos»

## Decisión (Pp, 8 oct 2026)

- Mínimo: Android 5 (API 21), porque las librerías actuales lo permiten mientras estén congeladas. Si se actualizan, puede subir a Android 6.
- Recomendada: Android 11 o superior, porque desde ahí funciona todo lo de fuera de la app.
- La aplicación avisa por voz qué funciones no existen en la versión del teléfono.
- Se prueba en emuladores de Android 5, 8, 11 y 15, además de teléfonos reales.

## Consecuencias

- Hoy `minSdk = 26`. Bajarlo es un cambio en Gradle, que está congelado (decisión 0001): lo hace solo Pp. Dos pantallas usan fechas de `java.time` y necesitan un ajuste para versiones anteriores a Android 8.
