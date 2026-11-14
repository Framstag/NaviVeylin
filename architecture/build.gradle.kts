import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import com.naviveylin.build.testing.ForcedTestExecution

plugins {
    id("org.jetbrains.kotlin.jvm")
}

// A host-only JVM module: no Android plugin, no dependency on `:app`, `:auto` or `:core` (change
// `package-structure-as-five-kinds`, design D3). Konsist reads the three modules' sources as text, which is
// why the module that judges them needs nothing from them.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // The two coordinates the module is allowed, as literals: this project has no version catalog.
    implementation("com.lemonappdev:konsist:0.17.3")
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<Test> {
    useJUnit()
    // Forced test execution (change `speed-up-build-test-gate`, spec `build-test-gate` — "A gate run proves
    // that its tests executed"): `-PforceTests` makes this task not up to date, so a run is evidence of what
    // executed; pair it with `--no-build-cache`, or the result outputs come back from the cache
    // (guidelines/Build.md §4).
    if (ForcedTestExecution.isRequested(providers.gradleProperty(ForcedTestExecution.PROPERTY).orNull)) {
        outputs.upToDateWhen { false }
    }
    // A failing run reports each failure's own message (guidelines/BuildTests.md §6f), like the three Android
    // modules' unit-test tasks.
    testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
