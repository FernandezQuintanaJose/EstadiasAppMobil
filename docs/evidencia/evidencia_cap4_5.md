# Evidencia para los capítulos 4 y 5

Generado el 2026-11-18 11:42 en CECAPI-App (rama diseno/alia). Todo lo de aquí salió de comandos reales; lo que no se pudo comprobar dice "no comprobado".

## A. Repositorio

### A1.1 Ramas (total y por tipo)

Comando: `echo "total remotas: $(git branch -r | grep -v HEAD | wc -l)"; for p in tarea feature docs modulo03 ajuste; do echo "$p/*: $(git branch -r | grep -c "origin/$p/")"; done; git branch -r | grep -v HEAD`

```
total remotas: 31
tarea/*: 3
feature/*: 3
docs/*: 5
modulo03/*: 9
ajuste/*: 1
  origin/ajuste/mic-mitad
  origin/alonso/interfaz-actividades-main
  origin/desarrollo
  origin/diseno/alia
  origin/docs/decisiones-0008-0016
  origin/docs/decisiones-30oct
  origin/docs/fichas
  origin/docs/memoria-movil-cap-iv-v
  origin/docs/revision-qa-4nov
  origin/feature/interfaz-invidentes
  origin/feature/lector-entorno-mejorado
  origin/feature/voces-filtro-y-tarjetas
  origin/integracion-entorno-colores
  origin/main
  origin/mejoras-30oct
  origin/modulo03/backend-asistente
  origin/modulo03/base-datos
  origin/modulo03/historial-chats-alonso
  origin/modulo03/imagenes
  origin/modulo03/integracion
  origin/modulo03/servidor-base-datos
  origin/modulo03/servidor-tablas-pruebas
  origin/modulo03/ver-limite-imagen
  origin/modulo03/vision-proveedores
  origin/modulo07/camara-hub-entorno
  origin/modulo07/entorno-colores
  origin/modulo6_aprendizaje
  origin/rama-miranda-sensor-camara
  origin/tarea/cuentas-y-privacidad
  origin/tarea/publicacion
  origin/tarea/red-bd-y-servidor
```

### A1.2 Fusiones de integración

Comando: `git log --all --merges --format="%h %ad %s" --date=short`

```
3342111 2026-11-18 Une main en diseno/alia
3f5a58c 2026-11-15 Merge remote-tracking branch 'origin/main' into tarea/publicacion
acabab1 2026-11-15 Fusiona #9: bloquea respuestas que repiten las instrucciones y registra el código de error
920c577 2026-11-15 Fusiona #8: pruebas con ids cortos para Windows
8e92f97 2026-11-02 Fusiona modulo03/servidor-base-datos (Alejandro: Neon, /documento, panel de uso, Render, X-Clave-App) en desarrollo
49f6e5c 2026-10-30 Fusiona main (328a453): Consultas manda los documentos a /documento y las preguntas a /preguntar
0ae3e1e 2026-10-30 Merge remote-tracking branch 'origin/main' into modulo03/servidor-base-datos
1e42dff 2026-10-30 Fusiona main en la rama del Módulo 3
4eb4f16 2026-10-30 Fusiona docs/decisiones-30oct (cuenta propia sin Google, Windows .exe)
e73e988 2026-10-30 Fusiona ajuste/mic-mitad (pantalla dividida, tarjeta iluminada) y pasa Actividades al mismo esquema
7da26c0 2026-10-30 Fusiona ajuste/mic-mitad (pantalla dividida)
911706f 2026-10-29 Merge equipo/main: integra cambios del equipo en el lector y agrega botón Guardar foto
83135c7 2026-10-28 Fusiona docs/decisiones-0008-0016
00324d4 2026-10-28 Fusiona origin/main (fichas del proyecto) con la integración de módulo 6, lector y micrófono
d8e3ef2 2026-10-27 Fusiona modulo6_aprendizaje (Equipo 4): modo práctica, aviso de audífonos, Nivel 3 y audios nuevos
d17626f 2026-10-27 Fusiona alonso/interfaz-actividades-main
ec2ad7c 2026-10-27 Fusiona integracion-entorno-colores: colores y billetes en el entorno (Equipo 5)
0658366 2026-10-27 Fusiona modulo03/integracion: servidor con seguridad, pruebas y tablas (Equipo 2)
7d531dc 2026-10-24 Merge remote-tracking branch 'origin/modulo03/servidor-tablas-pruebas' into modulo03/integracion
f419bcf 2026-10-11 Junta main (prompts y /ver de Alo) con visión de Alejandro e imagenes.py
e2e4fda 2026-10-08 Merge remote-tracking branch 'origin/main'
7d839cf 2026-10-07 Merge origin/main into main with smart menu formatting
d853070 2026-10-07 Merge equipo/main (EstadiasAppMobil) en main
15ec37e 2026-10-05 mencionar la accion al precionar un boton
abca9ff 2026-09-30 Fusiona backend/comandos.py (Alondra, catálogo de navegación por voz)
9ba17c2 2026-09-30 Merge remote-tracking branch 'origin/main' into modulo03/backend-asistente
9b1c9d8 2026-09-29 Fusiona el backend de Alonso (modulo03/backend-asistente)
d312301 2026-09-29 Merge branch 'main' of https://github.com/JuanMiranda1306/CECAPI_equipo3
17b74e6 2026-09-28 Merge branch 'main' of https://github.com/JuanMiranda1306/CECAPI_equipo3
a2c62b6 2026-09-28 Merge origin/main into master preserving modulo4 prototype interface
5f08da8 2026-09-26 merge: integrar main en modulo03, respetar versiones AGP 8.5.2 y Gradle 8.9
e5e1fed 2026-09-26 Fusiona la interfaz para personas invidentes y la cámara nueva en main
3f1c057 2026-09-26 Fusiona rama de Miranda (lector con sensores) y rediseña la cámara con comandos de voz
edafb51 2026-09-26 Fusiona modulo07/camara-hub-entorno (Edgar): Entorno
```

### A1.3 Primer commit

Comando: `git log --all --reverse --format="%h %ad %an %s" --date=short | head -1`

```
391e0e7 2026-07-13 JoseFernandezQuintana Initial commit: proyecto CECAPI-App
```

### A1.4 Commits por semana (14 sep a 17 nov) en las carpetas indicadas

Comando: `git log --all --since=2026-09-14 --until=2026-11-18 --date=format:"%G-S%V" --format="%ad" -- app/src/main/java/com/cecapi/app/feature/modulo1_aplicacionprincipal app/src/main/java/com/cecapi/app/feature/modulo2_asistentevoz app/src/main/java/com/cecapi/app/core/voice app/src/main/java/com/cecapi/app/core/data | sort | uniq -c`

```
      2 2026-S38
      6 2026-S39
     30 2026-S40
      1 2026-S41
     21 2026-S44
     12 2026-S45
      2 2026-S47
```

### A2 Todos los commits del 5 al 25 de octubre (todo el repositorio)

Comando: `git log --all --since=2026-10-05 --until=2026-10-26 --format="%h %ad %an %s" --date=short`

```
13b5726 2026-10-25 Alonso Rocha fix(ui): reorganiza actividades y fija microfono central
9cbbe7a 2026-10-25 Alejandro Arellano Modulo 3: base de datos SQLite y panel de uso
5c03d39 2026-10-25 Alejandro Arellano Pruebas: corrida comparativa con gpt-oss-120b (se sigue con 20b)
927e18e 2026-10-25 Alejandro Arellano Modulo 3: modo escena en /ver, documentacion de /docs e instrucciones corregidas
6f8a8b6 2026-10-24 Alejandro Arellano Modulo 3: filtro de privacidad mejorado, clave de app y limite por minuto, pruebas automaticas
41531c9 2026-10-24 Alejandro Arellano Pruebas: resultados del 24/10/2026 con Groq, antes y despues de corregir
7d531dc 2026-10-24 Alejandro Arellano Merge remote-tracking branch 'origin/modulo03/servidor-tablas-pruebas' into modulo03/integracion
c155b91 2026-10-24 Alejandro Arellano Modulo 3: Groq como principal (IA_ORDEN), instrucciones con datos del CECAPI y sinonimos nuevos
8983721 2026-10-19 JuanMiranda1306 Flash integrado y foto tomada
32ea142 2026-10-16 luism06gt Modulo 6: modo práctica, aviso de audífonos y Nivel 3 de 12 ejercicios
084863a 2026-10-16 luism06gt Modulo 6: 6 audios de enfrente y atrás
9022996 2026-10-12 Jesús Entorno: integra colores y billetes sobre main (modulo07/entorno-colores)
f546285 2026-10-12 jesusmartin97 Agrega deteccion de colores y billetes al entorno
6c11ce2 2026-10-11 Alejandro Arellano /ver: rechaza fotos demasiado grandes antes de decodificar el base64
f419bcf 2026-10-11 0wxen Junta main (prompts y /ver de Alo) con visión de Alejandro e imagenes.py
6b0f0b8 2026-10-11 Alondra-SanchezR clase Imagen
6779a20 2026-10-11 Alondra-SanchezR Prompts
c510973 2026-10-11 0wxen Módulo 3, Fase 1: imagenes.py prepara la foto para /ver
c05f682 2026-10-10 Alejandro Arellano README: mueve la línea de visión para no chocar con otras ramas
64cecc1 2026-10-10 Alejandro Arellano comandos.py: conserva los finales de línea CRLF del archivo original
8cf570a 2026-10-10 Alejandro Arellano Módulo 3, Fase 1: ver() de visión en Gemini y Groq, con respaldo
bd1c5fd 2026-10-10 Alejandro Arellano Pruebas: resultados con IA, pausa entre preguntas y observaciones
c659361 2026-10-10 Alejandro Arellano Módulo 3: tablas del servidor, pruebas de preguntas/sinónimos y un solo servidor
6240887 2026-10-08 JoseFernandezQuintana Entorno: detector de objetos con TensorFlow Lite (Equipo 3), nombre de la app
9507440 2026-10-08 JoseFernandezQuintana backend: corrige dos errores de sintaxis/datos que traía origin/main
e2e4fda 2026-10-08 JoseFernandezQuintana Merge remote-tracking branch 'origin/main'
fa45941 2026-10-08 JoseFernandezQuintana Quita del repo un worktree de agente que quedó registrado como submódulo
7d839cf 2026-10-07 Eduardo Diaz Merge origin/main into main with smart menu formatting
46a15db 2026-10-07 Eduardo Diaz feat(modulo4): estructuracion inteligente de menus y precios en el repositorio de lectura
d853070 2026-10-07 JuanMiranda1306 Merge equipo/main (EstadiasAppMobil) en main
0b3430e 2026-10-07 JuanMiranda1306 Lector de parrafos mejorado
b6a0723 2026-10-06 JoseFernandezQuintana Interfaz incompleta
15ec37e 2026-10-05 Humberto Castillo mencionar la accion al precionar un boton
f0bb98a 2026-10-05 Humberto Castillo mencionar la accion al precionar un boton
5c3a41a 2026-10-05 Humberto Castillo mover botn de volver al menu principal
```

### A2 Archivos de docs/ con fecha en ese periodo (por último commit)

Comando: `git log --all --since=2026-10-05 --until=2026-10-26 --name-only --format="" -- docs | sort -u`

```
docs/Pruebas/billetes.md
docs/Pruebas/correr_pruebas.py
docs/Pruebas/resultados.md
docs/Pruebas/resultados_2026-10-24_groq.md
docs/Pruebas/resultados_2026-10-24_groq_corregido.md
docs/Pruebas/resultados_2026-10-25.md
docs/Pruebas/resultados_2026-10-25_120b.md
docs/Pruebas/resultados_2026-10-25b.md
docs/herramientas/generar_pagina.py
docs/referencia/tablas-servidor.md
docs/referencia/tablas-servidor.png
```

### A3.1 Archivos Kotlin y líneas no vacías por carpeta

Comando: `for d in core/voice core/data core/ui core/util core/theme core/navigation feature/modulo1_aplicacionprincipal feature/modulo2_asistentevoz feature/modulo3_asistenteinteligente feature/modulo4_lectordocumentos feature/modulo5_solicitudes feature/modulo6_aprendizaje feature/modulo7_entorno; do p=app/src/main/java/com/cecapi/app/$d; echo "$d: $(find $p -name "*.kt" | wc -l) archivos, $(cat $(find $p -name "*.kt") | grep -c -v "^\s*$") lineas"; done; echo "TOTAL: $(find app/src/main -name "*.kt" | wc -l) archivos, $(cat $(find app/src/main -name "*.kt") | grep -c -v "^\s*$") lineas"`

```
core/voice: 19 archivos, 1944 lineas
core/data: 3 archivos, 361 lineas
core/ui: 10 archivos, 1905 lineas
core/util: 13 archivos, 1112 lineas
core/theme: 3 archivos, 94 lineas
core/navigation: 3 archivos, 275 lineas
feature/modulo1_aplicacionprincipal: 35 archivos, 8339 lineas
feature/modulo2_asistentevoz: 4 archivos, 297 lineas
feature/modulo3_asistenteinteligente: 8 archivos, 654 lineas
feature/modulo4_lectordocumentos: 9 archivos, 2064 lineas
feature/modulo5_solicitudes: 2 archivos, 420 lineas
feature/modulo6_aprendizaje: 17 archivos, 3495 lineas
feature/modulo7_entorno: 7 archivos, 1992 lineas
TOTAL: 145 archivos, 23985 lineas
```

### A3.2 Destinos de navegación en CecapiNavGraph.kt (composable)

Comando: `grep -c "composable(" app/src/main/java/com/cecapi/app/core/navigation/CecapiNavGraph.kt`

```
19
```

### A3.3 Accesibilidad: contentDescription con texto, null, semantics, liveRegion (módulo 1 y core/ui)

Comando: `for p in app/src/main/java/com/cecapi/app/feature/modulo1_aplicacionprincipal app/src/main/java/com/cecapi/app/core/ui; do echo "== $p"; echo "contentDescription con texto: $(grep -rE "contentDescription = \"|contentDescription = [a-zA-Z]" $p | grep -vc "= null")"; echo "contentDescription = null: $(grep -rc "contentDescription = null" $p | awk -F: "{s+=\$2} END{print s}")"; echo "semantics {: $(grep -rE "semantics\s*(\(.*\))?\s*\{" $p | wc -l)"; echo "liveRegion: $(grep -r "liveRegion" $p | wc -l)"; done`

```
== app/src/main/java/com/cecapi/app/feature/modulo1_aplicacionprincipal
contentDescription con texto: 7
contentDescription = null: 4
semantics {: 6
liveRegion: 2
== app/src/main/java/com/cecapi/app/core/ui
contentDescription con texto: 13
contentDescription = null: 9
semantics {: 14
liveRegion: 6
```

### A4 Versiones (gradle/libs.versions.toml)

Comando: `sed -n 1,60p gradle/libs.versions.toml`

```
[versions]
agp = "8.5.2"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.28"
coreKtx = "1.13.1"
lifecycle = "2.8.6"
activityCompose = "1.9.2"
composeBom = "2024.10.00"
navigationCompose = "2.8.2"
room = "2.6.1"
hilt = "2.51.1"
hiltNavigationCompose = "1.2.0"
cameraX = "1.4.2"
mlkitTextRecognition = "16.0.1"
mlkitImageLabeling = "17.0.9"
mlkitBarcodeScanning = "17.3.0"
mlkitTranslate = "17.0.3"
mlkitLanguageId = "17.0.6"
tensorflowLiteTaskVision = "0.4.4"
coroutines = "1.9.0"
datastore = "1.1.1"
argon2kt = "1.5.0"

[libraries]
core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }

room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }

hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }

camera-core = { group = "androidx.camera", name = "camera-core", version.ref = "cameraX" }
camera-camera2 = { group = "androidx.camera", name = "camera-camera2", version.ref = "cameraX" }
camera-lifecycle = { group = "androidx.camera", name = "camera-lifecycle", version.ref = "cameraX" }
camera-view = { group = "androidx.camera", name = "camera-view", version.ref = "cameraX" }

mlkit-text-recognition = { group = "com.google.mlkit", name = "text-recognition", version.ref = "mlkitTextRecognition" }
mlkit-image-labeling = { group = "com.google.mlkit", name = "image-labeling", version.ref = "mlkitImageLabeling" }
mlkit-barcode-scanning = { group = "com.google.mlkit", name = "barcode-scanning", version.ref = "mlkitBarcodeScanning" }
mlkit-translate = { group = "com.google.mlkit", name = "translate", version.ref = "mlkitTranslate" }
mlkit-language-id = { group = "com.google.mlkit", name = "language-id", version.ref = "mlkitLanguageId" }
tensorflow-lite-task-vision = { group = "org.tensorflow", name = "tensorflow-lite-task-vision", version.ref = "tensorflowLiteTaskVision" }

kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-play-services = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-play-services", version.ref = "coroutines" }
```

### A4 Versión mínima de Android Studio según README

Comando: `grep -n -i "android studio\|jdk" README.md | head -5`

```
9:1. Instala **Android Studio** (Ladybug 2024.2 o más reciente).
12:4. Si Android Studio ofrece actualizar el Android Gradle Plugin, di que no: las versiones están congeladas ([decisión 0001](docs/decisiones/0001-versiones-congeladas.md)).
```

### A5 RF1 a RF8 (archivos)

Comando: `grep -rn -E "RF[1-8]\b" docs/*.md docs/estado docs/decisiones 2>/dev/null | head -40`

```
```

### A6 Decisiones (número, título, estado)

Comando: `for f in docs/decisiones/0*.md; do echo "$(basename $f) | $(head -1 $f) | $(grep -m1 -i -E "^\*?\*?(estado|status)" $f) | $(git log -1 --format=%ad --date=short -- $f)"; done`

```
0001-versiones-congeladas.md | # 0001. Versiones de Gradle, AGP y Kotlin congeladas |  | 2026-10-03
0002-ia-apagada-por-defecto.md | # 0002. IA como "cerebro temporal", apagada por defecto |  | 2026-10-03
0003-pantalla-negra.md | # 0003. Pantalla negra con salida por voz y por toque |  | 2026-10-03
0004-base-de-datos-version-3.md | # 0004. Base de datos en la versión 3 con migraciones |  | 2026-10-03
0005-responsable-menores-datos-reales.md | # 0005. Responsable legal, menores y datos reales |  | 2026-10-03
0006-silencio-y-para.md | # 0006. "Silencio" y "para" solo como frase completa |  | 2026-10-03
0007-escucha-fuera-de-la-app.md | # 0007. Escuchar fuera de la app: opcional y apagado |  | 2026-10-03
0008-apk-sin-tiendas-y-costo-cero.md | # 0008. Android: solo el APK desde nuestra página, y costo cero |  | 2026-10-28
0009-acceso-por-roles-sin-planes.md | # 0009. El acceso se maneja con roles, no con planes |  | 2026-10-28
0010-la-ia-es-un-filtro.md | # 0010. La IA es un filtro entre paneles y proveedores |  | 2026-10-28
0011-version-minima-de-android.md | # 0011. Versión mínima de Android: la más baja posible |  | 2026-10-28
0012-botones-y-microfono-fijos.md | # 0012. Tamaños de botón y micrófono fijo |  | 2026-10-28
0013-luz-de-la-camara.md | # 0013. Luz de la cámara: sin luz, pregunta, linterna baja, nunca flash |  | 2026-10-28
0014-reglas-por-fila-en-supabase.md | # 0014. Supabase con reglas por fila (RLS) en todas las tablas |  | 2026-10-28
0015-lo-no-terminado-se-acumula-con-tope.md | # 0015. Lo que no se termina se acumula, con tope de tiempo |  | 2026-10-28
0016-los-equipos-no-dependen-unos-de-otros.md | # 0016. Los equipos no dependen unos de otros, y lo de IA es de Neurona |  | 2026-10-28
0017-base-en-neon-detras-del-servidor.md | # 0017. La base va en Neon detrás del servidor propio |  | 2026-10-28
0018-ubicacion-evaluacion.md | # 0018. Ubicación para orientarse: evaluación (no implementada) |  | 2026-10-30
0019-un-servidor-modular-y-argon2id.md | # 0019. Un solo servidor con módulos, no un microservicio por cada cosa; contraseñas con Argon2id |  | 2026-11-02
```

### A7 Pruebas unitarias: archivos en app/src/test y androidTest

Comando: `find app/src/test -name "*.kt" | wc -l; ls app/src/androidTest 2>&1 | head -1`

```
16
ls: cannot access 'app/src/androidTest': No such file or directory
```

### A7 Resultado testDebugUnitTest (XML de resultados)

Comando: `python -I -c "
import glob,re
t=f=e=s=0
for p in glob.glob(\"app/build/test-results/testDebugUnitTest/*.xml\"):
    h=open(p,encoding=\"utf-8\").read(2000)
    m=re.search(r\"tests=.(\d+). skipped=.(\d+). failures=.(\d+). errors=.(\d+)\",h)
    if m: a,b,c,d=map(int,m.groups()); t+=a;s+=b;f+=c;e+=d
print(\"pruebas=%d omitidas=%d fallas=%d errores=%d\"%(t,s,f,e))
"`

```
pruebas=91 omitidas=0 fallas=0 errores=0
```

### A9 APK debug: tamaño y huella SHA-256

Comando: `ls -l app/build/outputs/apk/debug/app-debug.apk; sha256sum app/build/outputs/apk/debug/app-debug.apk`

```
-rw-r--r-- 1 josep 197609 246973317 Nov 18 11:38 app/build/outputs/apk/debug/app-debug.apk
7bbc4ee876d29e40309892606e5c8abe8457c60a2649036add07f0e57c0aed87 *app/build/outputs/apk/debug/app-debug.apk
```

### A9 APK release: tamaño, huella y firma

Comando: `ls -l app/build/outputs/apk/release/app-release-unsigned.apk; sha256sum app/build/outputs/apk/release/app-release-unsigned.apk; echo "nombre indica sin firmar: app-release-unsigned.apk (apksigner no comprobado)"`

```
-rw-r--r-- 1 josep 197609 108569397 Nov  5 12:11 app/build/outputs/apk/release/app-release-unsigned.apk
ec5bb3134f667d58edb5e18ceb111939ef66abafe1128b4640e3c8fc34a78f0b *app/build/outputs/apk/release/app-release-unsigned.apk
nombre indica sin firmar: app-release-unsigned.apk (apksigner no comprobado)
```

### A9 minSdk / targetSdk / versionName

Comando: `grep -n -E "minSdk|targetSdk|versionName|versionCode" app/build.gradle.kts`

```
17:        minSdk = 26
18:        targetSdk = 35
19:        versionCode = 1
20:        versionName = "1.0"
```

### A10 Commit f6f8553 (interruptor de Wikipedia)

Comando: `git cat-file -t f6f8553 2>&1; git log --all --oneline -i --grep="wikipedia" | head -8`

```
fatal: Not a valid object name f6f8553
4eafb15 Memoria de estadía: límite de Wikipedia, pruebas acotadas y formato final
03ebe32 Memoria de estadía: flujo a Wikipedia, responsable legal, índices y anexo
190ddd0 Pantalla de Soporte (alumno/usuario), e incluye el cambio de Wikipedia que quedó fuera del commit anterior por error
d016faf Lector de documentos: botones gigantes al leer, cámara más robusta
95c5145 Guía de encuadre por voz en el lector, y registro de errores de Wikipedia
3f8bbb2 Reconoce cómo, cuándo, dónde, por qué y cuál, no solo qué/quién
ecc7b8b Todas las contraseñas a 1234 (una vez), y Wikipedia encuentra más cosas
36e4acd Agrega Wikidata para datos puntuales (nació/murió, capital, población)
```

## Lectura de A1.4 y A2 (hueco del 5 al 25 de octubre)

- En las carpetas de la app (módulo 1, módulo 2, `core/voice`, `core/data`) hay 1 commit en la semana 41 y 21 en la 44. Ahí sí se ve un hueco.
- En todo el repositorio no hay hueco completo: entre el 5 y el 25 de octubre hay commits de otros equipos (servidor, entorno, módulo 6, Alonso). El último commit antes del 5 de octubre es del 4 de octubre. Hay commits el 11, 12, 16, 19, 24 y 25 de octubre.
- La **razón real** del hueco en el módulo 1 y 2 no sale del repositorio: ver sección C. No comprobado.

## A8 Lint

No comprobado: no se ejecutó `./gradlew lint` en esta corrida.

## A10 Commit f6f8553

No existe en este repositorio (`fatal: Not a valid object name`). No comprobado. Si es de otra copia, pasar el repositorio o el hash correcto. Hay varios commits sobre Wikipedia (ver A10).

## B. Teléfono

Teléfono de pruebas: **OPPO CPH2387, Android 12** (SDK 31). Se confirma con `adb shell getprop ro.product.model` y `ro.build.version.release` (18 de noviembre de 2026).

| Archivo | Estado |
|---|---|
| `evidencia_capturas/01_pantalla_actual_oppo.png` | Hecha: pantalla actual del teléfono, 18 nov 2026 |
| 01_inicio, 02_pantalla_dividida, 03_registro, 04_bloqueo, 05_configuracion_privacidad, 06_gestion, 07_chats, 08_personalizacion, 09_antes_despues_microfono | No comprobado: hay que navegar a cada pantalla con una cuenta de demostración |

### Pruebas con fecha y resultado hechas el 18 de noviembre de 2026 (OPPO CPH2387, Android 12)

| Prueba | Resultado |
|---|---|
| Voz de la app como accesibilidad | `dumpsys audio` reporta `usage=USAGE_ASSISTANCE_ACCESSIBILITY content=CONTENT_TYPE_SPEECH` para el motor de voz (11:38 y 11:40). Antes salía por multimedia |
| Reconocimiento sin internet (wifi y datos apagados) | Con `es-MX`: `Failed to get language pack of required locale: error 12` (Google no ofrece es-MX sin conexión). Con `es-US`: `error 13` (paquete aún no descargado). Falta descargarlo y repetir |
| TalkBack | La voz de la app y TalkBack se encimaban; se agregó una llamada para callar a TalkBack antes de hablar. Verificado en código; **no comprobado de oído** |
| Pruebas unitarias | `testDebugUnitTest`: 91 pruebas, 0 fallas, 0 omitidas, 0 errores |

Sin hacer (no comprobado): crear cuenta, bloqueo tras 10 intentos, barra fija, interruptor de Wikipedia con tráfico, ajustes apagados en instalación limpia, 10 frases de voz, monkey con semilla 42.

## C. Datos de personas

No están en el repositorio. Los debe dar el equipo: coordinación y asesoría, prototipo del 13 de julio, razón real del hueco de octubre en los módulos 1 y 2, y visita al CECAPI (fecha, autorización, fotos).
