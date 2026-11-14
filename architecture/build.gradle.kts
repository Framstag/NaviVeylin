import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}

// `-PforceTests` makes the check not up to date, like the three modules' unit-test tasks (`guidelines/
// BuildEvidence.md` §4b — "to force it, use `-PforceTests --no-build-cache`").
val forceTests: Boolean = providers.gradleProperty("forceTests").isPresent

// What the check reads: the declaration and the three modules' main sources, as text. They are declared as
// inputs of the test task, or Gradle answers UP-TO-DATE from a previous run's green result and a violating
// tree passes — the defect task 1.9 closes.
val checkedTree = rootProject.fileTree(rootProject.projectDir) {
    include("architecture/structure.tsv")
    include("app/src/main/java/**")
    include("auto/src/main/java/**")
    include("core/src/main/java/**")
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
    inputs.files(checkedTree)
    if (forceTests) {
        outputs.upToDateWhen { false }
    }
    // A failing run reports each failure's own message (guidelines/BuildTests.md §6f), like the three Android
    // modules' unit-test tasks.
    testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
