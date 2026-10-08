import com.naviveylin.build.testing.CoverageInstrumentation
import com.naviveylin.build.testing.ForcedTestExecution

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

    testOptions {
        unitTests {
            // Forced test execution (change `speed-up-build-test-gate`, spec `build-test-gate` —
            // "A gate run proves that its tests executed"): `-PforceTests` makes the unit-test
            // tasks not up to date, so a run is evidence of what executed; pair it with
            // `--no-build-cache`, or the result outputs come back from the cache (Build.md §4).
            all {
                // A failing run reports each failure's own message (change
                // `fix-aggregate-run-test-flakes`, spec `build-test-gate` — "A failing run reports each
                // failure's message"); guidelines/Build.md §6 carries the rule.
                it.testLogging {
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                }

                if (forceTests) {
                    it.outputs.upToDateWhen { false }
                }
            }
        }
    }

    lint {
        // i18n gate: HardcodedText elevated to error via lint.xml, like :app and
        // :auto (spec: i18n-l10n — Shared module owns no wording).
        lintConfig = file("lint.xml")
        checkReleaseBuilds = true
        abortOnError = true
    }
}

// Forced test execution (change `speed-up-build-test-gate`, spec `build-test-gate`):
// `-PforceTests` forces the unit-test tasks, `--no-build-cache` keeps the cache from
// answering instead of them (guidelines/Build.md §4); `-PnoCoverage` detaches the Kover
// agent for a run that does not need coverage data (spec `test-coverage`).
val forceTests: Boolean =
    ForcedTestExecution.isRequested(providers.gradleProperty(ForcedTestExecution.PROPERTY).orNull)
val noCoverage: Boolean =
    CoverageInstrumentation.isDisabled(providers.gradleProperty(CoverageInstrumentation.PROPERTY).orNull)

// ── Test coverage (Kover) ───────────────────────────────────────────────
// JVM unit test coverage, report-only. Generated code (BuildConfig, R,
// Hilt/Dagger/KSP wiring) is excluded so metrics reflect hand-written
// logic. See guidelines/Build.md → Code coverage.
kover {
    currentProject {
        instrumentation {
            // Attached by default so the aggregated reports keep working; `-PnoCoverage`
            // detaches it for an iteration run (spec `test-coverage` — "Instrumentation is
            // attached only when coverage is requested").
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

// ── Unit-test wall-clock gate (change `speed-up-test-iteration`, task 5.2) ────────────────────────
// Same rule as `:app`'s and `:auto`'s: a test decides its outcome from the behaviour under test and
// from time it controls, never from a sleep or a system-clock deadline (spec `unit-test-suite-runtime`
// — Wall-clock waits in test sources are refused by a build check). The scanner is the pure buildSrc
// object, so this task only points it at this module's own test sources, and there is no allowlist on
// purpose.
val checkNoWallClockWaits by tasks.registering {
    val testSources = file("src/test/java")
    inputs.files(testSources)
    doLast {
        val findings = testSources.walkTopDown()
            .filter { it.extension == "kt" }
            .flatMap { file ->
                com.naviveylin.build.testing.WallClockWaitScanner.scan(
                    file.relativeTo(rootProject.projectDir).path,
                    file.readText()
                ).asSequence()
            }
            .toList()
        if (findings.isNotEmpty()) {
            throw GradleException(
                com.naviveylin.build.testing.WallClockWaitScanner.report(findings)
            )
        }
    }
}
tasks.named("preBuild") { dependsOn(checkNoWallClockWaits) }

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
