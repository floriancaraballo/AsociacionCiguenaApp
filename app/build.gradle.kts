import java.util.Properties


plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

// Cargar keystore properties
val keystorePropertiesFile: File = project.file("keystore.properties")
val keystoreProperties = Properties()

if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.asociacionciguena.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.asociacionciguena.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    // ✅ UN SOLO BLOQUE buildTypes (corregido)
    // ✅ buildTypes corregido
    buildTypes {
        release {
            // ✅ Firmar con la configuración de release (NO debug)
            signingConfig = signingConfigs.getByName("release")

            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false  // ← Explícito: no debuggable en release

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        debug {
            isDebuggable = true
            // ✅ Firmar con debug (puede ser implícito, pero mejor explícito)
            signingConfig = signingConfigs.getByName("debug")

            // ✅ IMPORTANTE: Sufijo para poder tener debug y release instalados a la vez
            applicationIdSuffix = ".debug"

            // Opcional: prefijo en el nombre de la app para distinguir
            resValue("string", "app_name", "Asociación Ciguena (Debug)")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // ✅ Reemplaza el bloque kotlinOptions por esto:
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.addAll(
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
                "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // ========================================
    // CORE ANDROID & KOTLIN
    // ========================================
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // ========================================
    // JETPACK COMPOSE (UI) - SIN VERSIONES FIJAS (el BOM las gestiona)
    // ========================================
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    // ========================================
    // HILT
    // ========================================
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // ========================================
    // FIREBASE - SIN VERSIONES FIJAS (el BOM las gestiona)
    // ========================================
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.storage.ktx)
    implementation(libs.firebase.analytics.ktx)
    // ✅ Dependencia directa con versión:
    implementation("com.google.firebase:firebase-functions-ktx:21.0.0")
    implementation("com.google.firebase:firebase-messaging-ktx:23.4.1")

    // ========================================
    // NETWORKING & IMAGES
    // ========================================
    implementation(libs.coil.compose)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    // ========================================
    // SERIALIZACIÓN & JSON
    // ========================================
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.gson)

    // ========================================
    // DATASTORE & COROUTINES
    // ========================================
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // ========================================
    // ACCOMPANIST
    // ========================================
    implementation(libs.accompanist.permissions)
    implementation(libs.accompanist.systemuicontroller)
    // ✅ Eliminado: accompanist-swiperefresh (usamos PullToRefreshBox nativo)

    // ========================================
    // SPLASH & UTILS
    // ========================================
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.kotlinx.datetime)
    implementation("id.zelory:compressor:3.0.1")
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // ========================================
    // PDF Generation (para la app, no para Cloud Functions)
    // ========================================
    implementation("com.itextpdf:itext7-core:7.2.5")  // ← Comentado si no lo usas en Android

    // ========================================
    // TESTING
    // ========================================
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.android.compiler)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.android.compiler)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.mockk.android)

    // ========================================
    // DEBUG TOOLS
    // ========================================
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}