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

fun quoted(key: String): String = "\"" + (localProperties.getProperty(key) ?: "").trim() + "\""

android {
    namespace = "com.monzatti.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.monzatti.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "SUPABASE_URL", quoted("SUPABASE_URL"))
        buildConfigField("String", "SUPABASE_ANON_KEY", quoted("SUPABASE_ANON_KEY"))
        // Servidor de fotos con IA (tryon-server/), opcional.
        buildConfigField("String", "TRYON_SERVER_URL", quoted("TRYON_SERVER_URL"))
        buildConfigField("String", "TRYON_SERVER_TOKEN", quoted("TRYON_SERVER_TOKEN"))
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
