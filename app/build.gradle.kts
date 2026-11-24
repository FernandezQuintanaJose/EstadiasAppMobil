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

dependencies {
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
