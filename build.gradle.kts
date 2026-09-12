plugins {
    id("com.android.application") version "9.4.0" apply false
    id("com.android.library") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("com.google.dagger.hilt.android") version "2.59" apply false
    id("com.google.devtools.ksp") version "2.3.6" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.8"
}

// ── Test coverage (Kover) ───────────────────────────────────────────────────
// JVM unit test coverage for the Kotlin modules (app, auto, core). The root
// project is the Kover merging module: the `kover` configuration below
// collects per-module coverage into one cross-module HTML/XML report (tasks
// `:koverHtmlReport`, `:koverXmlReport`). Generated code (BuildConfig, R,
// Hilt/Dagger/KSP wiring) is excluded so metrics reflect hand-written logic.
// :osmscout-client-java (pure Java, no Kotlin plugin) is measured by the
// standard Gradle JaCoCo plugin instead — see guidelines/Build.md.
// Report-only — no threshold gate.
dependencies {
    // String (project-path) notation — Project-object notation is deprecated
    // in Gradle 9.6 and fails in Gradle 10.
    kover(project(":app"))
    kover(project(":auto"))
    kover(project(":core"))
}

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
