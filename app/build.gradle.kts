import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.cecapi.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cecapi.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // Tráfico sin cifrar (http) solo en depuración, para probar contra un servidor local.
        manifestPlaceholders["permiteHttp"] = "false"

        // Servidor del asistente (Módulo 3, backend/). Nunca la API de un proveedor de IA directo: su clave
        // quedaría dentro del APK. Se configura en local.properties (no se sube al repo); ver backend/PUBLICAR.md:
        //   cecapi.servidor=https://<nombre>.onrender.com   (o http://<IP-de-tu-PC>:8000 para probar en casa)
        //   cecapi.claveApp=<CECAPI_CLAVE_APP del servidor>  (vacía si el servidor no pide clave)
        val local = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        val servidor = local.getProperty("cecapi.servidor", "http://TU-IP-LOCAL:8000").trim()
        val claveApp = local.getProperty("cecapi.claveApp", "").trim()
        buildConfigField("String", "AI_PROXY_BASE_URL", "\"$servidor\"")
        buildConfigField("String", "AI_APP_KEY", "\"$claveApp\"")

        ndk {
            // La app se usa en teléfonos reales (arm64). El emulador de desarrollo agrega x86_64
            // solo en depuración; el APK de entrega no carga los binarios de escritorio.
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        debug {
            manifestPlaceholders["permiteHttp"] = "true"
            ndk {
                abiFilters += "x86_64"
            }
        }
        release {
            // Código optimizado y con nombres ocultos: más difícil de descompilar y más ligero.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sin x86 ni x86_64 (emulador de escritorio): un teléfono real es ARM. Cada arquitectura trae unos 50 MB de
            // librerías de ML Kit y TensorFlow Lite, así que sin ellas el APK baja a casi la mitad. La depuración las conserva.
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // Los GGUF se copian tal cual a filesDir; comprimirlos alarga la instalación y hace que
        // AssetManager no pueda informar su tamaño para reanudar copias.
        noCompress += "gguf"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Los esquemas de cada versión de la base, para probar las migraciones en la computadora (MigracionesTest).
    sourceSets.getByName("debug").assets.srcDir("$projectDir/schemas")
    testOptions.unitTests.isIncludeAndroidResources = true
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

/**
 * Modelo de voz en español (Vosk, Apache 2.0, unos 39 MB) para reconocer el nombre del asistente sin internet.
 * No va en el repositorio. El SHA-256 es el del archivo bajado el 25 de noviembre de 2026 de la página oficial
 * (alphacephei.com no publica la suma): si cambia el archivo, la tarea falla. Sin el modelo, la app compila y
 * usa la escucha anterior.
 */
tasks.register("descargarModeloDeVoz") {
    group = "build"
    description = "Descarga y verifica el modelo de voz que reconoce el nombre del asistente."

    doLast {
        val nombre = "vosk-model-small-es-0.42"
        val esperado = "09b239888f633ef2f0b4e09736e3d9936acfd810bc65d53fad45261762c6511f"
        val destino = rootProject.file("app/src/main/assets/vosk-es")
        if (File(destino, "uuid").exists()) {
            println("Ya está listo: $nombre")
            return@doLast
        }
        val zip = File(rootProject.projectDir, "build/modeloDeVoz-tmp/$nombre.zip").apply { parentFile.mkdirs() }
        val respuesta = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build().send(
            HttpRequest.newBuilder(URI.create("https://alphacephei.com/vosk/models/$nombre.zip")).GET().build(),
            HttpResponse.BodyHandlers.ofFile(zip.toPath()),
        )
        check(respuesta.statusCode() == 200) { "No se pudo descargar el modelo (HTTP ${respuesta.statusCode()})" }
        val suma = MessageDigest.getInstance("SHA-256").digest(zip.readBytes()).joinToString("") { "%02x".format(it) }
        check(suma == esperado) { "El SHA-256 del modelo no coincide" }
        copy {
            from(zipTree(zip))
            into(destino)
            // El zip trae todo dentro de una carpeta con el nombre del modelo.
            eachFile { path = path.removePrefix("$nombre/") }
            includeEmptyDirs = false
        }
        // Vosk usa este archivo para saber si tiene que volver a copiar el modelo al teléfono.
        File(destino, "uuid").writeText(nombre)
        println("Listo: $nombre")
    }
}

dependencies {
    // Reconocimiento del nombre del asistente en el teléfono (versiones de la demostración oficial de Vosk).
    implementation("net.java.dev.jna:jna:5.18.1@aar")
    implementation("com.alphacephei:vosk-android:0.3.75@aar")
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.room.testing)
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.image.labeling)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.mlkit.translate)
    implementation(libs.mlkit.language.id)
    implementation(libs.tensorflow.lite.task.vision) {
        // El intérprete directo 2.17 entiende las capas nuevas del modelo de monedas
        // (FULLY_CONNECTED v12). Se evita la API vieja que arrastra la tarea de visión.
        exclude(group = "org.tensorflow", module = "tensorflow-lite-api")
    }
    implementation(libs.tensorflow.lite)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.datastore.preferences)
    implementation(libs.argon2kt)
}

/**
 * Descarga los archivos grandes del motor de descripción desde el release de GitHub.
 *
 * No se guardan en el repositorio porque pesan ~600 MB y GitHub rechaza archivos de más de
 * 100 MB. El SHA-256 se verifica contra el archivo SHA256SUMS del release antes de mover
 * cada archivo a su carpeta.
 */
tasks.register("descargarMotor") {
    group = "build"
    description = "Descarga y verifica los archivos del motor de descripción."

    doLast {
        val repo = "FernandezQuintanaJose/EstadiasAppMobil"
        val tag = "v1.0-motor-de-descripcion"
        val base = "https://github.com/$repo/releases/download/$tag"
        val temporal = File(rootProject.projectDir, "build/descargarMotor-tmp").apply { mkdirs() }
        val cliente = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()

        fun descargar(url: String, destino: File) {
            val solicitud = HttpRequest.newBuilder(URI.create(url)).GET().build()
            val respuesta = cliente.send(solicitud, HttpResponse.BodyHandlers.ofInputStream())
            check(respuesta.statusCode() == 200) {
                "No se pudo descargar $url (HTTP ${respuesta.statusCode()})"
            }
            destino.parentFile?.mkdirs()
            respuesta.body().use { entrada ->
                Files.copy(entrada, destino.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }

        fun sha256(archivo: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            archivo.inputStream().use { entrada ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val leidos = entrada.read(buffer)
                    if (leidos < 0) break
                    digest.update(buffer, 0, leidos)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        val sums = File(temporal, "SHA256SUMS")
        descargar("$base/SHA256SUMS", sums)
        val esperados = sums.readLines().mapNotNull { linea ->
            val partes = linea.trim().split(Regex("\\s+"), limit = 2)
            if (partes.size == 2) partes[1].removePrefix("*") to partes[0].lowercase() else null
        }.toMap()

        val destinos = mapOf(
            "SmolVLM2-500M-Q8_0.gguf" to
                rootProject.file("app/src/main/assets/smolvlm/SmolVLM2-500M-Q8_0.gguf"),
            "mmproj-SmolVLM2-500M-Q8_0.gguf" to
                rootProject.file("app/src/main/assets/smolvlm/mmproj-SmolVLM2-500M-Q8_0.gguf"),
            "libsmolvlm_jni-arm64-v8a.so" to
                rootProject.file("app/src/main/jniLibs/arm64-v8a/libsmolvlm_jni.so"),
            "libsmolvlm_jni-x86_64.so" to
                rootProject.file("app/src/main/jniLibs/x86_64/libsmolvlm_jni.so"),
        )

        destinos.forEach { (nombre, destino) ->
            val esperado = esperados[nombre]
                ?: error("El release no contiene $nombre en SHA256SUMS")
            if (destino.exists() && sha256(destino) == esperado) {
                println("Ya está listo: $nombre")
                return@forEach
            }
            println("Descargando $nombre...")
            val copia = File(temporal, nombre)
            descargar("$base/$nombre", copia)
            check(sha256(copia) == esperado) { "El SHA-256 de $nombre no coincide" }
            destino.parentFile?.mkdirs()
            Files.move(copia.toPath(), destino.toPath(), StandardCopyOption.REPLACE_EXISTING)
            println("Listo: $nombre")
        }
    }
}
