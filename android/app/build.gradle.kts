import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val pwaHost: String = localProps.getProperty("PWA_HOST") ?: "timely-hamster-5836fc.netlify.app"

android {
    namespace = "corsica.universita.objetscampus"
    compileSdk = 36

    defaultConfig {
        applicationId = "corsica.universita.objetscampus"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["pwaHost"] = pwaHost
        buildConfigField("String", "PWA_HOST", "\"$pwaHost\"")
        buildConfigField("String", "PWA_URL", "\"https://$pwaHost/\"")
        buildConfigField("String", "SYNC_BASE_URL", "\"https://$pwaHost/api/\"")
    }

    signingConfigs {
        getByName("debug") {
            val debugFile = rootProject.file("debug.keystore")
            if (debugFile.exists()) {
                storeFile = debugFile
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        create("release") {
            val ksPath = localProps.getProperty("RELEASE_STORE_FILE")
            val ksFile = if (ksPath != null) rootProject.file(ksPath) else null
            if (ksFile != null && ksFile.exists()) {
                storeFile = ksFile
                storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD")
            } else {
                // Fallback pour que le build release fonctionne en TD meme sans keystore dedie
                val debugCfg = getByName("debug")
                storeFile = debugCfg.storeFile
                storePassword = debugCfg.storePassword
                keyAlias = debugCfg.keyAlias
                keyPassword = debugCfg.keyPassword
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Room (persistance structuree + migrations SQL)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore Preferences (token FCM, consentement analytics, flags permissions)
    implementation(libs.androidx.datastore.preferences)

    // WorkManager (synchronisation en arriere-plan sous contraintes reseau)
    implementation(libs.androidx.work.runtime.ktx)

    // Reseau : Retrofit + OkHttp + kotlinx.serialization
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    // Images (Coil Compose)
    implementation(libs.coil.compose)

    // Capteurs & Play Services Location
    implementation(libs.play.services.location)

    // Fonctionnalite Native 1 : CameraX + ML Kit Barcode / QR Scanning (impossible en PWA avec analyse temps-reel native)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)

    // Fonctionnalite Native 2 : Jetpack Glance AppWidget (Widget ecran d'accueil Android)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // Firebase Cloud Messaging & Analytics (Annexe 4)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)

    testImplementation(libs.junit)
}
