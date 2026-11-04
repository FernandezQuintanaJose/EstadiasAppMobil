# Voces de Personalización probadas en un teléfono (4 nov 2026)

> Una sola prueba, en un solo teléfono. Lo que no se pudo comprobar está marcado como SIN VERIFICAR. Las pruebas automáticas (`CatalogoDeVocesTest`) cubren las reglas; esto cubre lo que solo se ve en un teléfono.

## Teléfono

OPPO A57 (CPH2387), Android 12, 4 GB de memoria, motor de voz Google (`com.google.android.tts`). Versión de depuración, instalada encima de la anterior sin borrar datos.

## Qué voces en español reporta

18 entradas, de las cuales 16 son voces y 2 son alias del idioma (`es-ES-language`, `es-US-language`), que la app ya no cuenta como voz.

| Origen | Voces | Con internet |
|---|---|---|
| España (`es-es-x-`) | eea, eec, eed (local y de internet), eee, eef (solo local) | eea, eec, eed |
| Estados Unidos (`es-us-x-`) | esc, esd, esf, sfb (local y de internet) | las cuatro |
| México | **ninguna** | — |

Ninguna salió con "sin instalar" (`notInstalled`). SIN VERIFICAR si eso significa que todas están descargadas o que el motor no usa esa marca: no se pudo comparar con otro teléfono. Se sacó con `adb logcat -s CecapiVoces`.

Esta lista está en `CatalogoDeVoces.VOCES_CONOCIDAS`. En un teléfono que no tenga alguna, aparece como "No disponible" en su lugar; los números no se recorren ("Voz 11" es `es-us-x-esd-local` en cualquier teléfono que la conozca).

## Resultados

| Prueba | Resultado |
|---|---|
| La pantalla "Elegir voz" con el filtro | Bien. Se cambió el filtro de una tarjeta grande a tres botones chicos y la tarjeta de la voz cabe sin desplazarse |
| Filtro "Sin internet" | Bien. Muestra solo las locales y conserva sus números (en ese teléfono, Voz 15) |
| Sin Wi-Fi ni datos, una voz de internet (Voz 12) | Bien. La tarjeta sale apagada con "Sin conexión" y el botón dice "Sin conexión" |
| Tocar 5 veces el botón bloqueado | Bien. La voz guardada no cambió (`es-us-x-esd-local`), la app no se cerró y no hubo errores |
| Voz de internet elegida y sin internet, la app habla | Bien. Sigue hablando (audio activo a los 1,2 s, contra 0,6 s con internet) y conserva la voz guardada (`es-us-x-esd-network`) |
| Regreso del internet | Bien. La voz guardada sigue siendo la de internet |

**No se pudo comprobar:** que lo que se oye sea la voz esperada (se midió que hay audio, no cuál voz), ni los mensajes hablados ("Voz 12 necesita internet…"), porque no hay forma de oírlos desde la computadora. Falta además una pasada con TalkBack encendido y con una persona.

## Hallazgo fuera de las voces: "El teléfono está muy cargado"

La pantalla de espera apareció **2 veces en unas 6 pruebas** con el teléfono a 32 °C, estado térmico normal, batería al 100 % y 1,6 GB de memoria libre. Una vez, el registro (`adb logcat -s CecapiSalud`, agregado en esta versión) mostró `nivel=ALTA ... heap=0.02 trim=10`: el aviso `TRIM_MEMORY_RUNNING_LOW` del sistema, con el heap de la app casi vacío. La pantalla pide `CRITICA` (`MainActivity`), así que en las dos apariciones hubo un aviso más fuerte que no quedó en el registro.

Probable causa: este teléfono tiene WhatsApp, Pinterest y X abiertos y Android manda avisos de memoria con frecuencia. No se cambió ningún umbral porque con tan pocos datos sería adivinar. Antes de entregar: repetir en este y en otro teléfono con `CecapiSalud` en un archivo y decidir si `CRITICA` debe exigir que el aviso se repita, o si basta el heap y el estado térmico.

## Comandos usados

```
adb logcat -G 8M                          # el teléfono llena el registro de otros avisos
adb logcat -s CecapiVoces CecapiSalud
adb shell svc wifi disable ; adb shell svc data disable   # y enable al terminar
adb shell run-as com.cecapi.app strings files/datastore/device_settings.preferences_pb   # solo versión de depuración
```
