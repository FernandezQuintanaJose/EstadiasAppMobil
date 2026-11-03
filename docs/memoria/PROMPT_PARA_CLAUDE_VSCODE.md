# Instrucciones para el Claude de Visual Studio (datos que dependen del teléfono)

> **Actualización del 3 de noviembre de 2026:** los datos del dispositivo (Oppo CPH2387, Android 12, app 1.0 desde main b8e39d8) y las pruebas de cuenta, contraseñas, pantallas y estabilidad ya están integrados en los apartados 4.6 y 5.1 (Tabla 12). Lo único que sigue sin datos es el recorrido con TalkBack; es opcional y, si no se hace, no se agrega nada.

Contexto: la memoria de estadía de José Fernández Quintana (módulos 1 y 2 de la app Android CECAPI) está en `docs/memoria/Memoria_App_movil_Android_COMPLETA.docx`. Una revisión crítica pide **datos reales del teléfono** para el apartado 4.6 (Pruebas realizadas) y el 5.1. Ese es el único punto de la crítica que depende del dispositivo; todo lo demás ya se corrigió en el texto.

**Regla principal: no inventes nada.** Si un dato no se pudo comprobar, escribe «no probado». No modifiques código de la app salvo que el alumno lo pida.

## 1. Datos del dispositivo

Si hay `adb` y el teléfono está conectado con depuración USB:

```
adb devices
adb shell getprop ro.product.manufacturer
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell pm list packages | grep -i -E "talkback|accessibility"
adb shell dumpsys package com.google.android.marvin.talkback | grep versionName
adb shell dumpsys package com.cecapi.app | grep -E "versionName|versionCode|lastUpdateTime"
```

Si no hay `adb`, pídele al alumno que abra Ajustes > Acerca del teléfono y que te diga: modelo, versión de Android y, si usa TalkBack, su versión (Ajustes > Accesibilidad). Anota también si el `applicationId` de la app coincide con `com.cecapi.app` (revísalo en `app/build.gradle.kts`).

## 2. Recorrido de pantallas

La app tiene 17 destinos (`app/src/main/java/com/cecapi/app/core/navigation/CecapiNavGraph.kt`). Los del módulo 1: inicio, iniciar sesión, crear cuenta, panel, ajustes, personalización, gestión, soporte, clasificación, editar cuenta y chats; el módulo 2 es el asistente de voz. Con el APK actual instalado (compilado desde `main`), el alumno recorre cada pantalla y te dice el resultado. Completa esta tabla solo con lo que el alumno confirme:

| Destino | ¿Abre? | ¿Se recorre con TalkBack? | ¿Funciona la voz? | Observación |
| --- | --- | --- | --- | --- |
| Inicio | | | | |
| Iniciar sesión | | | | |
| Crear cuenta | | | | |
| (continúa con cada destino) | | | | |

Valores permitidos en las celdas: «sí», «no», «no probado».

Comprueba además, por separado y solo si el alumno lo prueba: pantalla dividida con micrófono, escucha en segundo plano (Ajustes > Privacidad > «Escuchar fuera de la aplicación»), borrar la cuenta y los datos, y registro con contraseña de 15 letras.

## 3. Qué devolver

Entrega un bloque en español, listo para pegar en la memoria:

1. Un párrafo (de 40 palabras o más, en tercera persona) para el apartado 4.6 con: modelo del teléfono, versión de Android, versión de TalkBack (o «sin lector de pantalla»), fecha y versión de la app probada.
2. La tabla anterior ya llena.
3. Una frase para el apartado 5.1.1 con el conteo real («de N destinos recorridos, M se pudieron recorrer con TalkBack»), usando solo números de la tabla.

Si el alumno prefiere no registrar estas pruebas, no se agrega nada al documento: el texto actual ya es coherente sin ellas.
