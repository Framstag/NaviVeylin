import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
    // A failing run reports each failure's own message (guidelines/Build.md §6), like the three Android
    // modules' unit-test tasks.
    testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
