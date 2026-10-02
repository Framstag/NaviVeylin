plugins {
    id("com.android.library")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlinx.kover")
}

android {
    namespace = "com.naviveylin.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = false
    }

    lint {
        // i18n gate: HardcodedText elevated to error via lint.xml, like :app and
        // :auto (spec: i18n-l10n — Shared module owns no wording).
        lintConfig = file("lint.xml")
        checkReleaseBuilds = true
        abortOnError = true
    }
}

// ── Test coverage (Kover) ───────────────────────────────────────────────
// JVM unit test coverage, report-only. Generated code (BuildConfig, R,
// Hilt/Dagger/KSP wiring) is excluded so metrics reflect hand-written
// logic. See guidelines/Build.md → Code coverage.
kover {
    reports {
        filters {
            excludes {
                classes(
                    "*.BuildConfig",
                    "*.R",
                    "*.R$*",
                    "dagger.hilt.*",
                    "hilt_aggregated_deps.*",
                    "*.Hilt_*",
                    "*_Hilt*",
                    "*.Dagger*Component*"
                )
            }
        }
    }
}

// i18n gate (spec: i18n-l10n — All user-facing text is translatable, Shared module
// owns no wording): `:core` composes user-facing text for both surfaces, so a
// literal here (the shared details resolver's generic title was one) is a defect
// the app-side gate could not see. The rule lives in `buildSrc`
// (`com.naviveylin.build.i18n.HardcodedStringScanner`, unit-tested).
val checkHardcodedStrings by tasks.registering {
    val sourceDirs = listOf(file("src/main/java"))
    inputs.files(sourceDirs)
    doLast {
        val findings = com.naviveylin.build.i18n.HardcodedStringGate
            .scanTrees(sourceDirs, rootProject.projectDir)
        if (findings.isNotEmpty()) {
            throw GradleException(
                com.naviveylin.build.i18n.HardcodedStringScanner.report(findings)
            )
        }
    }
}
tasks.named("preBuild") { dependsOn(checkHardcodedStrings) }

dependencies {
    implementation(project(":osmscout-client-java"))
    implementation("com.google.dagger:hilt-android:2.59")
    ksp("com.google.dagger:hilt-compiler:2.59")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
}
