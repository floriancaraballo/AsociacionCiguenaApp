plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    //id("com.android.application")
    //id("com.google.gms.google-services")
}

android {
    namespace = "com.asociacionciguena.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.asociacionciguena.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Firma de la aplicación (configurar más adelante)
            signingConfig = signingConfigs.getByName("debug")
        }

        debug {
            isDebuggable = true
            //applicationIdSuffix = ".debug"
            //versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"

        // Opciones del compilador de Kotlin
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
        )
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
    // JETPACK COMPOSE (UI)
    // ========================================
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // ViewModel en Compose
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // ========================================
    // NAVEGACIÓN
    // ========================================
    implementation(libs.androidx.navigation.compose)

    // ========================================
    // HILT (INYECCIÓN DE DEPENDENCIAS)
    // ========================================
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // ========================================
    // FIREBASE
    // ========================================
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.storage.ktx)
    implementation(libs.firebase.analytics.ktx)

    //implementation(platform("com.google.firebase:firebase-bom:34.8.0"))
    //implementation("com.google.firebase:firebase-analytics")

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
    // DATASTORE (ALMACENAMIENTO LOCAL)
    // ========================================
    implementation(libs.androidx.datastore.preferences)

    // ========================================
    // COROUTINES (PROGRAMACIÓN ASÍNCRONA)
    // ========================================
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // ========================================
    // ACCOMPANIST (UTILIDADES PARA COMPOSE)
    // ========================================
    implementation(libs.accompanist.permissions)
    implementation(libs.accompanist.systemuicontroller)

    // ========================================
    // SPLASH SCREEN API
    // ========================================
    implementation(libs.androidx.core.splashscreen)

    // ========================================
    // FECHA Y HORA
    // ========================================
    implementation(libs.kotlinx.datetime)


    // ========================================
    // TESTING
    // ========================================
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    // Testing para Hilt
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.android.compiler)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.android.compiler)

    // Testing para Coroutines
    testImplementation(libs.kotlinx.coroutines.test)

    // MockK para mocking
    testImplementation(libs.mockk)
    androidTestImplementation(libs.mockk.android)

    // ========================================
    // DEBUG TOOLS
    // ========================================
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation("androidx.compose.material:material:1.7.6")

    // Firebase Storage
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-storage-ktx")
    // Accompanist Permissions - AÑADIR
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")
    implementation("com.google.accompanist:accompanist-swiperefresh:0.32.0")
}

