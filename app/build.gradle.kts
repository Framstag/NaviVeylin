import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("androidx.room")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Release signing credentials: app/keystore.properties (gitignored) or env vars.
val keystoreProperties = Properties().apply {
    val f = file("keystore.properties")
    if (f.exists()) {
        FileInputStream(f).use { load(it) }
    }
}

android {
    namespace = "com.naviveylin"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.framstag.naviveylin"
        minSdk = 28
        targetSdk = 36
        versionCode = 5
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    ndkVersion = "27.0.12077973"

    signingConfigs {
        create("release") {
            val keystoreFile = file("release.keystore")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = keystoreProperties.getProperty("storePassword")
                    ?: System.getenv("NAVIVEYLIN_STORE_PASSWORD")
                    ?: ""
                keyAlias = keystoreProperties.getProperty("keyAlias")
                    ?: System.getenv("NAVIVEYLIN_KEY_ALIAS")
                    ?: ""
                keyPassword = keystoreProperties.getProperty("keyPassword")
                    ?: System.getenv("NAVIVEYLIN_KEY_PASSWORD")
                    ?: ""
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile?.exists() == true) {
                signingConfig = releaseSigning
            } else {
                logger.warn(
                    "app/release.keystore not found — release builds will be UNSIGNED. " +
                        "Generate it and app/keystore.properties before shipping."
                )
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }

    testOptions {
        unitTests {
            // Robolectric Compose UI tests need Android resources
            isIncludeAndroidResources = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Lifecycle + ViewModel
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.59")
    ksp("com.google.dagger:hilt-compiler:2.59")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // WindowManager (foldable support)
    implementation("androidx.window:window:1.3.0")

    // Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Core shared module
    implementation(project(":core"))

    // Android Auto module
    implementation(project(":auto"))

    // JNI bridge (libosmscout-client-java)
    implementation(project(":osmscout-client-java"))

    // Core library desugaring (java.net.http, etc.)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Google Play Services Location (GPS) — optional, fallback to LocationManager
    implementation("com.google.android.gms:play-services-location:21.1.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.robolectric:robolectric:4.16")
    // Compose UI tests under Robolectric (createComposeRule)
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
