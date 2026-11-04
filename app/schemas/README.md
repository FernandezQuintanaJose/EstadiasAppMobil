# Esquemas de la base del teléfono

Un archivo por versión de la base (`com.cecapi.app.core.data.AppDatabase/N.json`). Room los escribe solo al compilar (`exportSchema = true`) y `MigracionesTest` los usa para probar cada actualización.

- **Al subir la versión de la base**, compilar una vez y subir el JSON nuevo junto con la migración y su prueba. Sin el JSON de la versión anterior no se puede probar la actualización.
- **Las versiones 8, 10, 11, 12, 14 y 15 no están**: nunca se subieron a git con su número. Se prueban de paso al correr la cadena completa desde otra versión.
- **`1.json` está derivado a mano**: el primer commit del proyecto no compila. Se obtuvo del esquema 2 quitando `rol` y `origen`, que es lo único que agrega la migración 1 a 2. No lo edites.
- **Los archivos están también en los assets de la versión de depuración** (`app/build.gradle.kts`), solo para que las pruebas los lean. La versión de publicación no los lleva.
