# Archivos del motor de descripción

El motor de descripción y sus librerías nativas no se guardan en el repositorio porque pesan
alrededor de 600 MB. GitHub rechaza archivos de más de 100 MB en un push normal.

## Descarga automática

Después de clonar el proyecto, ejecuta una vez:

```bash
./gradlew descargarMotor
```

La tarea:

1. descarga `SHA256SUMS` del release `v1.0-motor-de-descripcion`;
2. descarga cada archivo;
3. verifica su SHA-256;
4. lo coloca en la carpeta correcta.

Si un archivo ya existe y su SHA-256 coincide, no se vuelve a descargar.

## Archivos y destinos

| Archivo del release | Destino |
| --- | --- |
| `SmolVLM2-500M-Q8_0.gguf` | `app/src/main/assets/smolvlm/SmolVLM2-500M-Q8_0.gguf` |
| `mmproj-SmolVLM2-500M-Q8_0.gguf` | `app/src/main/assets/smolvlm/mmproj-SmolVLM2-500M-Q8_0.gguf` |
| `libsmolvlm_jni-arm64-v8a.so` | `app/src/main/jniLibs/arm64-v8a/libsmolvlm_jni.so` |
| `libsmolvlm_jni-x86_64.so` | `app/src/main/jniLibs/x86_64/libsmolvlm_jni.so` |

## Checksums

```text
6f67b8036b2469fcd71728702720c6b51aebd759b78137a8120733b4d66438bc  SmolVLM2-500M-Q8_0.gguf
921dc7e259f308e5b027111fa185efcbf33db13f6e35749ddf7f5cdb60ef520b  mmproj-SmolVLM2-500M-Q8_0.gguf
b0353c28fb2705134f383ab36cbbf84c38c3158ee4c1c6492ab65a60ec99542a  libsmolvlm_jni-arm64-v8a.so
84abcc20d02c4088289506900942aa3456f1ad18bfdaa5b880882747cd7067c2  libsmolvlm_jni-x86_64.so
```

La copia de estos checksums también está en `docs/motor-descripcion/SHA256SUMS`.

## Descarga manual

Si la tarea de Gradle no puede conectarse, abre el release:

```text
https://github.com/FernandezQuintanaJose/EstadiasAppMobil/releases/tag/v1.0-motor-de-descripcion
```

Descarga los cuatro archivos y colócalos en las rutas de la tabla. Después comprueba el
SHA-256 con `sha256sum` o con la herramienta de tu sistema.

Sin estos archivos la app compila, pero la descripción automática en el teléfono no funciona.
