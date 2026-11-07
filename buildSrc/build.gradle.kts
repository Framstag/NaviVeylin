plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

// No `java { toolchain { ... } }` here, on purpose (issue #92). buildSrc is built as a build of its own and has
// no settings.gradle.kts, so the foojay toolchain resolver that the root settings.gradle.kts applies does not
// reach it: a toolchain could only be auto-detected, and a machine with no JDK of that version failed at
// configuration — "Cannot find a Java installation ... matching: {languageVersion=17 ...}. Toolchain download
// repositories have not been configured." — before any task, naming neither this file nor the fix.
// The JVM that compiles the build logic is therefore the daemon JVM the criteria in
// gradle/gradle-daemon-jvm.properties select (21), which every machine that can run this build already has. The
// app modules state their Java 17 target as source/target compatibility, which installs nothing; buildSrc needs
// no target of its own (it carries no Java source at all: `:buildSrc:compileJava NO-SOURCE`). Guarded by
// BuildSrcJvmRequirementTest and the CI step "Check daemon JVM criteria"; see guidelines/Build.md §2d.

dependencies {
    testImplementation("junit:junit:4.13.2")
}

// License policy evaluation is build logic, so its tests run with the build
// (Gradle builds buildSrc on every invocation; unchanged inputs are up-to-date).
tasks.named<Test>("test") {
    testLogging {
        events("failed")
    }
}
