import com.naviveylin.build.testing.ForcedTestExecution

plugins {
    id("java-library")
    id("idea")
    id("jacoco")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

val clientJavaDir = "${rootDir}/app/src/main/cpp/libosmscout/libosmscout-client-java/java"

// The module compiles two provenances: its own local overrides (`src/main/java`) and the vendored libosmscout Java
// client API the submodule ships. They are two source sets, so each has its own classes directory and therefore its
// own coverage report — one coverage-row key per provenance rather than one denominator for both (issue #678).
sourceSets {
    main {
        java {
            setSrcDirs(listOf("src/main/java"))
        }
    }
    create("vendored") {
        java {
            setSrcDirs(listOf(clientJavaDir))
            // All submodule Java sources except the files provided locally (with Android-compatible HTTP and
            // debug-suffix-aware library loading). RoadInfo.java is NOT overridden: upstream's version is
            // byte-identical in behavior (public ctor + hasInfo), so the submodule file compiles as-is.
            exclude(
                "**/OSMScoutClientBuilder.java",
                "**/OSMScoutClient.java",
                "**/MapDownloadManager.java",
                "**/AvailableMapEntry.java",
                "**/BasemapManager.java"
            )
        }
    }
}

// Register submodule sources for IDE resolution (Android Studio / IntelliJ)
idea {
    module {
        sourceDirs.add(file(clientJavaDir))
    }
}

// The local overrides compile against the vendored types beside them, and the published jar carries both — the app
// consumes one artifact, as before.
dependencies {
    compileOnly(sourceSets["vendored"].output)
    testImplementation(sourceSets["vendored"].output)
}

tasks.named<Jar>("jar") {
    from(sourceSets["vendored"].output)
    archiveBaseName.set("libosmscoutclientjava")
    archiveVersion.set("1.0.0")
}

// Forced test execution (change `speed-up-build-test-gate`, spec `build-test-gate` — "A gate run
// proves that its tests executed"): `-PforceTests` forces this module's test task too, so one
// invocation can quote all four modules' tallies; `--no-build-cache` keeps the build cache from
// answering instead of the task (guidelines/Build.md §4).
val forceTests: Boolean =
    ForcedTestExecution.isRequested(providers.gradleProperty(ForcedTestExecution.PROPERTY).orNull)

tasks.withType<Test> {
    useJUnitPlatform()
    // The host JNI stub `:app` and `:auto` ship for their unit tests (`guidelines/Build.md` §6h) is on this
    // module's test path too, so `OSMScoutClient`'s static `System.loadLibrary` succeeds here: without it the
    // class is unreachable from a host case and the module's own JNI surface is measured by nothing.
    systemProperty(
        "java.library.path",
        layout.projectDirectory.dir("src/test/jniLibs").asFile.absolutePath
    )
    if (forceTests) {
        outputs.upToDateWhen { false }
    }
}

// ── Test coverage (JaCoCo) ───────────────────────────────────────────────
// Pure-Java module (no Kotlin plugin): Kover cannot measure it (empty
// reports), so the standard Gradle JaCoCo plugin is used here. Report-only;
// HTML + XML at build/reports/jacoco/test. Not part of the root Kover merge.
// See guidelines/Build.md → Code coverage.
//
// One report per provenance: `jacocoTestReport` measures the module's own overrides, `jacocoVendoredTestReport`
// the vendored client API compiled beside them. Both read the one execution data of the suite, so a case that
// reaches a vendored class counts there and a case that reaches an override counts here, never both (#678).
val jacocoExec = layout.buildDirectory.file("jacoco/test.exec")

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    classDirectories.setFrom(files(sourceSets["main"].output.classesDirs))
    sourceDirectories.setFrom(files(sourceSets["main"].allSource.srcDirs))
    executionData.setFrom(jacocoExec)
    reports {
        html.required.set(true)
        xml.required.set(true)
    }
}

tasks.register<JacocoReport>("jacocoVendoredTestReport") {
    group = "verification"
    description = "Instruction coverage of the vendored libosmscout Java client API this module compiles"
    dependsOn(tasks.named("test"))
    classDirectories.setFrom(files(sourceSets["vendored"].output.classesDirs))
    sourceDirectories.setFrom(files(sourceSets["vendored"].allSource.srcDirs))
    executionData.setFrom(jacocoExec)
    reports {
        html.required.set(true)
        xml.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/vendored/html"))
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/vendored/jacocoVendoredTestReport.xml"))
    }
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
