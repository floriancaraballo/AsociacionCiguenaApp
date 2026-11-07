import com.android.build.gradle.api.ApkVariantOutput
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

// Cargar keystore properties
val keystorePropertiesFile: File = project.file("keystore.properties")
val keystoreProperties = Properties()
val localPropertiesFile: File = rootProject.file("local.properties")
val localProperties = Properties()

if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

android {
    namespace = "com.asociacionciguena.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.asociacionciguena.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["MAPS_API_KEY"] =
            localProperties.getProperty("MAPS_API_KEY", "")
        buildConfigField(
            "String",
            "MAPS_API_KEY_KEY",
            "\"${localProperties.getProperty("MAPS_API_KEY_KEY", "")}\""
        )
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
            buildConfigField("String", "FIRESTORE_DATABASE_ID", "\"(default)\"")
            buildConfigField("String", "NOTIFICATION_ENVIRONMENT", "\"release\"")

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
            buildConfigField("String", "FIRESTORE_DATABASE_ID", "\"debug\"")
            buildConfigField("String", "NOTIFICATION_ENVIRONMENT", "\"debug\"")

            // Opcional: prefijo en el nombre de la app para distinguir
            resValue("string", "app_name", "Asociación Ciguena (Debug)")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
    // Nombrar el APK de release a partir de la version de la aplicacion.
    applicationVariants.all {
        if (buildType.name == "release") {
            val releaseVersionName = versionName
            outputs.withType<ApkVariantOutput>().configureEach {
                outputFileName = "ciguena${releaseVersionName}.apk"
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.foundation.ExperimentalFoundationApi"
        )
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
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.messaging)

    // ========================================
    // NETWORKING & IMAGES
    // ========================================
    implementation(libs.coil.compose)
    implementation(libs.okhttp)

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

    // ========================================
    // SPLASH & UTILS
    // ========================================
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.kotlinx.datetime)
    implementation(libs.google.maps.compose)
    implementation(libs.google.places)
    implementation(libs.google.material)
    implementation("id.zelory:compressor:3.0.1")
    implementation(libs.androidx.work.runtime)

    // ========================================
    // PDF Generation (para la app, no para Cloud Functions)
    // ========================================
    implementation("com.itextpdf:itext7-core:7.2.5")  // ← Comentado si no lo usas en Android

    // Media3 (ExoPlayer) para reproducción de vídeo
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)

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

    // ========================================
    // DEBUG TOOLS
    // ========================================
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
