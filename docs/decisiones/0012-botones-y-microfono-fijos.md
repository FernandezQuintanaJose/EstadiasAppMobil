# 0012. Tamaños de botón y micrófono fijo

> Aceptada; falta unificar el micrófono de Actividades · Dueño: Pp · Decidida el 8 oct 2026 · Fuente: documento «Plan para equipos»

## Decisión (Pp, 8 oct 2026)

- Seis tamaños de tarjeta en toda la app. Cuatro son `BigBtn` (1 Hero, 2 Secondary, 3 Standard y 6 Compact); el 4 (2.º y 3.er lugar del podio) y el 5 (4.º y 5.º lugar) se dibujan en `RankingScreen.kt`.
- Ningún equipo hace botones a mano; el color va solo en el ícono.
- El micrófono (`MicPad`) va anclado abajo, con la misma altura en todas las pantallas. No sube con el teclado ni con el contenido, y el anillo de escucha se dibuja encima sin empujar nada.

## Consecuencias

- `VibrationScreen` usa otro micrófono (`VoiceMicButton`) y debe cambiarse al estándar.
- Alonso revisa los tamaños y el micrófono en cada panel nuevo.
- El comentario del código dice «6 tamaños», pero el `enum BigBtnSize` solo tiene 4.
