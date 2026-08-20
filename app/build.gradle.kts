import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDate
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

// ── Release versioning ───────────────────────────────────────────────────────
// Release builds get a date-based versionName (<yyyy>-<MM>-<dd>-<N>) and a
// monotonic versionCode, persisted in app/release-version.properties
// (gitignored). Only an explicit `release` invocation bumps the state; every
// other build uses the fixed fallback below and never touches the file.
val FALLBACK_VERSION_NAME = "1.0.0"
val FALLBACK_VERSION_CODE = 19

val releaseStateFile = file("release-version.properties")

fun readReleaseState(): Properties = Properties().apply {
    if (releaseStateFile.exists()) {
        FileInputStream(releaseStateFile).use { load(it) }
    }
}

fun writeReleaseState(state: Properties) {
    FileOutputStream(releaseStateFile).use {
        state.store(it, "NaviVeylin release version state (managed by the release task)")
    }
}

/**
 * Computes the next release version and persists it.
 * Same day → running number +1; new day → running number resets to 1.
 * versionCode increments by exactly one from the persisted value (first run: 20).
 */
fun nextReleaseVersion(): Pair<String, Int> {
    val state = readReleaseState()
    val today = LocalDate.now()
    val todayStr = today.toString() // ISO yyyy-MM-dd
    val runningNumber = if (state.getProperty("lastDate") == todayStr) {
        (state.getProperty("runningNumber")?.toIntOrNull() ?: 0) + 1
    } else {
        1
    }
    val versionCode = (state.getProperty("versionCode")?.toIntOrNull() ?: FALLBACK_VERSION_CODE) + 1
    state.setProperty("lastDate", todayStr)
    state.setProperty("runningNumber", runningNumber.toString())
    state.setProperty("versionCode", versionCode.toString())
    writeReleaseState(state)
    val versionName = String.format(
        "%04d-%02d-%02d-%d",
        today.year, today.monthValue, today.dayOfMonth, runningNumber
    )
    return versionName to versionCode
}

// Bump only when the `release` task was explicitly requested.
val isReleaseBuild = gradle.startParameter.taskNames.any { it == "release" }

// Fail fast: android.injected.* flags (e.g. -Pandroid.injected.build.abi=arm64-v8a)
// are Android Studio internals for test deploys — AGP marks such builds
// android:testOnly=true and Google Play rejects the AAB. Only `release` is
// blocked; debug iteration with the ABI flag stays allowed.
val injectedFlags =
    gradle.startParameter.projectProperties.keys.filter { it.startsWith("android.injected.") }
if (isReleaseBuild) {
    check(injectedFlags.isEmpty()) {
        "release must not run with -Pandroid.injected.* flags (got: ${injectedFlags.joinToString()}). " +
            "AGP marks such builds android:testOnly=true, which Google Play rejects. " +
            "Run plain ./gradlew release instead."
    }
}

val releaseVersion: Pair<String, Int>? = if (isReleaseBuild) nextReleaseVersion() else null

tasks.register("release") {
    group = "release"
    description = "Bumps the version state and builds a Play-ready AAB via :app:bundleRelease."
    dependsOn("bundleRelease")
    doLast {
        val (versionName, versionCode) =
            releaseVersion ?: error("release task requires a generated version")
        println()
        println("NaviVeylin release: $versionName (versionCode $versionCode)")
        println("AAB: ${layout.buildDirectory.file("outputs/bundle/release/app-release.aab").get().asFile}")
    }
}

android {
    namespace = "com.naviveylin"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.framstag.naviveylin"
        minSdk = 28
        targetSdk = 36
        // `release` builds get the generated version; other builds use the fixed fallback.
        versionCode = releaseVersion?.second ?: FALLBACK_VERSION_CODE
        versionName = releaseVersion?.first ?: FALLBACK_VERSION_NAME

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
        buildConfig = true
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

    sourceSets {
        getByName("main") {
            // Stylesheets are copied from the pinned libosmscout submodule into a
            // generated assets root at build time (syncSubmoduleStylesheets) — no
            // committed snapshot. Upstream style updates reach the APK with the
            // submodule bump; there is no snapshot to keep in sync.
            assets.srcDir("build/generated/assets")
        }
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

// Stylesheets are sourced from the libosmscout submodule at build time. Copy the
// submodule stylesheet directory into a generated assets root so the APK packages
// exactly the current submodule state — "stylesheets/..." in the APK, matching the
// AssetCopier contract.
tasks.register<Sync>("syncSubmoduleStylesheets") {
    from("src/main/cpp/libosmscout/stylesheets")
    into(layout.buildDirectory.dir("generated/assets/stylesheets"))
}

// Fail fast with an actionable message when the submodule is not checked out
// (fresh clone), instead of packaging an APK without stylesheets.
tasks.register("checkSubmoduleStylesheets") {
    val stylesheetsDir = file("src/main/cpp/libosmscout/stylesheets")
    doFirst {
        check(stylesheetsDir.isDirectory) {
            "libosmscout submodule stylesheets not found at $stylesheetsDir. " +
                "Initialize the submodule first: git submodule update --init --recursive"
        }
    }
}

tasks.named("preBuild") {
    dependsOn("checkSubmoduleStylesheets", "syncSubmoduleStylesheets")
}

// Ensure every asset merge (debug/release/test) copies the submodule stylesheets
// first — the sync output is a static build-dir path, so no automatic dependency
// is carried from the source set.
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }
    .configureEach {
        dependsOn("syncSubmoduleStylesheets")
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
