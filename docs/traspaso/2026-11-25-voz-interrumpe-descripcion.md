# La voz se corta sola a media descripción (25 de noviembre de 2026)

**Síntoma (reportado por el equipo):** al describir el entorno con la cámara, el reconocimiento de la imagen
acierta, pero la lectura de la descripción se interrumpe sin que nadie hable ni haya un ruido fuerte. Hay que
volver a pulsar para que la repita y a veces se corta otra vez.

**Dueño:** el motor de voz (`core/voice/VoiceEngine.kt`, módulo 2 de José). La cámara (módulo 7, Jesús) solo le
pide que hable.

## Causa probable
Mientras el asistente habla, `VoiceEngine` abre un monitor del micrófono (`startBargeInDetection`) para que una
persona pueda interrumpirlo con la voz. El monitor medía el ruido durante 500 ms y desde ahí interrumpía si el
volumen superaba `max(1 800, promedio * 2,5)` durante 300 ms. Dos cosas hacían que oyera la voz del propio asistente:
1. Desde el commit que pasó la voz a "accesibilidad" (`USAGE_ASSISTANCE_ACCESSIBILITY`), el cancelador de eco del
   teléfono ya no recibe esa voz como referencia (eso es una hipótesis: no se midió en el aparato).
2. La medición de 500 ms usaba el **promedio**, y la voz del asistente tiene picos de más del doble del promedio; en una
   descripción larga alguno rebasaba el umbral y la lectura se cortaba sola.

## Qué cambia en esta rama
- `DetectorInterrupcion.kt` (nuevo, con 6 pruebas unitarias que pasan): mide 1,5 s, fija el umbral sobre el **pico**
  (x1,4) y exige 600 ms seguidos. Sin cancelador de eco, x2 y 900 ms.
- `VoiceEngine.speak(..., interrumpible = false)`: no activa el monitor. La cámara lo usa en la descripción local,
  la de la IA, "repetir" y el total de dinero. Siguen funcionando el micrófono (doble toque) y "silencio" y "para".

## Para comprobarlo en el teléfono (Claude de VS Code)
1. Compila e instala esta rama. Pide una descripción larga con la cámara, en un cuarto en silencio y con volumen medio.
   Debe terminar sin cortarse. Repite 10 veces y anota cuántas se cortaron.
2. Habla encima de una respuesta corta del asistente (por ejemplo, "silencio" o "para"): debe interrumpirse.
3. Guarda el registro de una descripción y anota cuántas líneas de `rms peak=... threshold=... eco=...` salen:
   ```bash
   adb logcat -c
   adb logcat -s CecapiVoice:D > docs/evidencia/2026-11-25/voz_descripcion.log
   ```
   Si `eco=false`, el teléfono no tiene cancelador de eco y el monitor es más exigente a propósito.
4. Si aun así se corta, el registro dirá si el pico de la propia voz supera el umbral; con esos números se ajusta
   `MARGEN_SOBRE_PICO` y `ASENTAMIENTO_MS` en `DetectorInterrupcion.kt`.
