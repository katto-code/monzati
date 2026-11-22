import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Las claves de Supabase viven en local.properties (no se versiona).
// Si faltan, la app usa los datos de ejemplo en memoria.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// Versión de la app: se sube en gradle.properties antes de publicar una actualización.
val appVersionCode = (findProperty("monzatti.versionCode") as String).toInt()
val appVersionName = findProperty("monzatti.versionName") as String

// Llave de firma de release (ver docs/actualizaciones.md). Sin ella, la APK de release sale sin firmar.
val releaseKeystore = localProperties.getProperty("RELEASE_STORE_FILE")?.trim()?.takeIf { it.isNotEmpty() }

fun quoted(key: String): String = "\"" + (localProperties.getProperty(key) ?: "").trim() + "\""

android {
    namespace = "com.monzatti.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.monzatti.app"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        buildConfigField("String", "SUPABASE_URL", quoted("SUPABASE_URL"))
        buildConfigField("String", "SUPABASE_ANON_KEY", quoted("SUPABASE_ANON_KEY"))
        // Servidor de fotos con IA (tryon-server/), opcional.
        buildConfigField("String", "TRYON_SERVER_URL", quoted("TRYON_SERVER_URL"))
        buildConfigField("String", "TRYON_SERVER_TOKEN", quoted("TRYON_SERVER_TOKEN"))
        // Fotos con IA desactivadas por defecto. Para activarlas: monzatti.aiPhotos=true en gradle.properties.
        buildConfigField("boolean", "AI_PHOTOS_ENABLED", (findProperty("monzatti.aiPhotos")?.toString() == "true").toString())
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = rootProject.file(releaseKeystore)
                storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Las builds de depuración buscan actualizaciones en el canal de pruebas.
            buildConfigField("String", "UPDATE_CHANNEL", "\"pruebas\"")
        }
        release {
            buildConfigField("String", "UPDATE_CHANNEL", "\"estable\"")
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            // Solo celulares ARM de 64 bits y librerías comprimidas: la APK cabe en el límite de 50 MB de Supabase Storage.
            ndk { abiFilters += "arm64-v8a" }
        }
    }

    packaging {
        jniLibs { useLegacyPackaging = true }
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
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.okhttp)

    // Probador con cámara: vista previa + detección de pose en el dispositivo.
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.camerax.mlkit)
    implementation(libs.mlkit.pose)
    // Digitalización de prendas: detección, recorte de fondo y etiquetas, todo en el dispositivo.
    implementation(libs.mlkit.segmentation)
    implementation(libs.mlkit.objects)
    implementation(libs.mlkit.labeling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
