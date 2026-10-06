import com.naviveylin.build.testing.CoverageInstrumentation
import com.naviveylin.build.testing.ForcedTestExecution

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlinx.kover")
}

android {
    namespace = "com.naviveylin.auto"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = false
    }

    testOptions {
        unitTests {
            // Robolectric tests resolve localized strings from module resources
            isIncludeAndroidResources = true

            // Unit-test fork budget (change `fix-auto-unit-test-heap-overflow`, spec
            // `unit-test-suite-runtime` — "Unit-test fork budget is declared and bounded").
            // AGP's default fork heap (512 MB) cannot hold this suite in one JVM — measured
            // 2026-09-20 after the renderer-teardown fix: 141 `OutOfMemoryError` failures,
            // no result XMLs, BUILD FAILED after 9m8s — while 1024 MB does (49 classes,
            // 516 tests, one fork, green). Declared explicitly so a fresh checkout and CI get
            // the same budget. If a machine cannot afford one 1024 MB fork, `forkEvery`
            // (e.g. 24) bounds the peak at the cost of a JVM start per batch — see
            // guidelines/Build.md §6 for the measured numbers and the rule.
            all {
                it.maxHeapSize = "1024m"

                // Declared test-JVM concurrency (change `speed-up-build-test-gate`, spec
                // `unit-test-suite-runtime` — "Unit-test parallelism is declared and result-preserving").
                // Measured 2026-10-04 on this suite (76 classes / 779 tests, one invocation per setting):
                // 1 fork 38.3s / 1.21 GB peak worker RSS, 2 forks 40.0s / 1.70 GB, 4 forks 48.9s / 2.62 GB —
                // the class set was identical throughout, so here concurrency buys nothing and costs memory:
                // this suite is short enough that per-JVM start-up outweighs the split. One fork, declared.
                it.maxParallelForks = 1

                if (forceTests) {
                    it.outputs.upToDateWhen { false }
                }
            }
        }
    }

    lint {
        // i18n gate: HardcodedText elevated to error via lint.xml (see guidelines/UI.md)
        lintConfig = file("lint.xml")
        checkReleaseBuilds = true
        abortOnError = true
    }
}

// Forced test execution (change `speed-up-build-test-gate`, spec `build-test-gate` — "A gate run
// proves that its tests executed"): `-PforceTests` makes the unit-test tasks not up to date, so a
// run is evidence of what executed; pair it with `--no-build-cache` (guidelines/Build.md §4).
val forceTests: Boolean =
    ForcedTestExecution.isRequested(providers.gradleProperty(ForcedTestExecution.PROPERTY).orNull)
// Coverage instrumentation stays attached by default so the aggregated reports keep working;
// `-PnoCoverage` detaches the Kover agent for an iteration run (spec `test-coverage`).
val noCoverage: Boolean =
    CoverageInstrumentation.isDisabled(providers.gradleProperty(CoverageInstrumentation.PROPERTY).orNull)

// ── Test coverage (Kover) ───────────────────────────────────────────────
// JVM unit test coverage, report-only. Generated code (BuildConfig, R,
// Hilt/Dagger/KSP wiring) is excluded so metrics reflect hand-written
// logic. See guidelines/Build.md → Code coverage.
kover {
    currentProject {
        instrumentation {
            disabledForAll.set(noCoverage)
        }
    }
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

// i18n gate (spec: i18n-l10n — All user-facing text is translatable): the rule,
// its exemptions and its message live in `buildSrc`
// (`com.naviveylin.build.i18n.HardcodedStringScanner`, unit-tested). It covers
// Compose text positions, interpolated display text and `NotificationChannel`
// names — the shapes the previous inline regexes let through.
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
    implementation(project(":core"))
    implementation(project(":osmscout-client-java"))
    api("androidx.car.app:app:1.7.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.google.dagger:hilt-android:2.59")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
