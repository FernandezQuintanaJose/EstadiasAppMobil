# Reconocimiento de áreas del CECAPI

Esta capacidad es complementaria al análisis que ya existe. No reemplaza al detector, no crea
una pantalla nueva y no modifica la base de datos. Si no hay una coincidencia confiable, la
descripción normal queda exactamente igual.

## Modelo

- **Nombre:** MobileNetV3-Large image embedder (MediaPipe).
- **Archivo:** `app/src/main/assets/cecapi_areas/embedder.tflite`.
- **Tamaño:** 10.4 MB (10 889 458 bytes), dentro del rango autorizado de 5–20 MB.
- **SHA-256:** `11af3c560dfeed7737cb4c03c23bf52a8403020784192d4dea0b74862a12828d`.
- **Origen:**
  `https://storage.googleapis.com/mediapipe-models/image_embedder/mobilenet_v3_large/float32/1/mobilenet_v3_large.tflite`
- **Licencia:** Apache License 2.0, indicada en los metadatos del modelo.
- **Entrada:** `float32[1, 224, 224, 3]`, RGB.
- **Salida:** `float32[1, 1280]` (vector de características).
- **Preprocesamiento:** imagen redimensionada a 224×224 con interpolación bilineal y
  normalización `[0, 1]` (`píxel / 255`). El índice guarda este mismo valor en `areas.json`.
- **Requisitos:** TensorFlow Lite 2.17, que ya usa la app. No se agregó una dependencia de
  Gradle nueva. Funciona sin conexión y en CPU.
- **Memoria estimada:** 10.4 MB del modelo, 1.9 MB del índice y alrededor de 10–20 MB de
  tensores temporales; aproximadamente 25–35 MB en total durante la consulta.
- **Procesamiento estimado:** una sola inferencia por foto, en dos hilos. Se medirá en el
  teléfono; la comparación contra 373 vectores es despreciable.

Se eligió la variante *Large* porque cumple el rango de 5–20 MB y separa mejor las fotografías
externas que la variante *Small*. Con *Large* se usa normalización `[0, 1]`; con `[-1, 1]` las
fotos externas subían demasiado y no era seguro.

El modelo no sustituye a la descripción automática ni al detector: solo produce un vector para
comparar la foto con las referencias.

## Referencias

La carpeta de fotografías no se copia al proyecto ni al APK:

```text
/home/jesus62/Escritorio/Referencias_CECAPI
```

Áreas actuales:

- `Area_de_Juegos`
- `Bienvenida`
- `Camino_De_Entrada`
- `Edificio_Principal`
- `Edificio_Recreativo`
- `Edificio_Recreativo_Dentro`
- `Pastizal`

`Camino_De_Entrada` conserva su nombre; no se trata como `Pasillo_Principal`.

## Índice

El índice se genera una sola vez y se guarda en:

```text
app/src/main/assets/cecapi_areas/areas.json
app/src/main/assets/cecapi_areas/embeddings.bin
```

- 373 vectores de 1280 dimensiones, normalizados.
- 7 áreas.
- Peso aproximado del índice: 1.9 MB.
- Umbral conservador: **0.80**.
- Margen sobre la segunda área: **0.08**.
- Promedio de las **3** mejores referencias por área.

Las fotografías originales no se guardan en el APK ni se suben al repositorio. Solo se
empaqueta el índice derivado.

### Validación realizada

Se apartó aproximadamente el 20 % de las referencias de cada área para probar el índice:

- Aciertos con umbral 0.80 y margen 0.08: **71 de 77 (92.2 %)**.
- Falsos positivos en cinco fotografías externas (laptop, habitación, gimnasio, infografía,
  sala): **0**.
- Mejor puntuación de una fotografía externa: **0.2675** (sala), muy por debajo del umbral.

El umbral es conservador a propósito: es preferible no reconocer un área a inventar una
ubicación.

### Pruebas en dispositivo y emulador

- **Samsung Galaxy A55 (Android 17):** se reconocieron `Edificio_Principal`, `Pastizal` y
  `Area_de_Juegos` con fotos transformadas (recorte y brillo) que no están en el índice.
- **Emulador x86_64 (API 37):** se reconocieron las 7 áreas y se rechazaron las 5 fotos
  externas (laptop, habitación, gimnasio, infografía de Hidalgo y sala).
- **La foto que se describe es la que se eligió:** el archivo que copia la app coincide byte
  a byte (SHA-256) con la imagen elegida en el selector. No hay cambio de imagen entre la
  vista previa y el análisis.
- La descripción del `Pastizal` en el teléfono fue correcta (campo abierto, camino de tierra,
  edificios al fondo), sin objetos inventados.
- En el emulador el modelo de descripción (SmolVLM) no terminó dentro del tiempo disponible;
  las descripciones se validaron en el teléfono. El reconocimiento de área sí terminó en
  aproximadamente un segundo en ambos.

## Generar el índice

El script de desarrollo requiere Python 3.11 y `tflite-runtime`; no se instala nada en la app:

```bash
python3.11 -m venv .venv-areas
.venv-areas/bin/pip install "numpy<2" tflite-runtime pillow
.venv-areas/bin/python tools/generar_indice_areas.py \
  --refs /home/jesus62/Escritorio/Referencias_CECAPI
```

El script no modifica las fotografías. Solo lee las referencias y escribe:

```text
app/src/main/assets/cecapi_areas/areas.json
app/src/main/assets/cecapi_areas/embeddings.bin
```

Para cambiar el umbral o el margen, se ajustan las constantes del script, se regenera el
índice y se vuelve a probar. No hace falta tocar la app.

## Integración

- `EnvironmentRepository.processCapturedPhoto()` lanza el reconocimiento de área en paralelo
  con la descripción.
- Solo se aplica a fotos generales que piden descripción (`usarIa = true`).
- No se aplica a billetes, monedas, OCR, juego ni otros flujos.
- Si hay área confiable, se agrega al principio:

  > “Te encuentras en el edificio principal del CECAPI. …descripción actual…”

- Si no hay área, la descripción actual queda sin cambios.
- Si el modelo o el índice faltan, o están dañados, se devuelve “sin área” y la app sigue
  funcionando.

No se guarda el área en la base de datos. Si más adelante se quiere historial por área, se
necesitaría una migración autorizada.

## Archivos

- `CecapiAreaIndex.kt`: lectura y validación del índice.
- `CecapiAreaRecognizer.kt`: modelo, preprocesamiento y consulta.
- `CecapiAreaMatcher.kt`: similitud, umbral y margen.
- `tools/generar_indice_areas.py`: generación del índice.
- `app/src/main/assets/cecapi_areas/`: modelo, metadatos y vectores.
