plugins {
    id("com.android.library")
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
        }
    }

    lint {
        // i18n gate: HardcodedText elevated to error via lint.xml (see guidelines/UI.md)
        lintConfig = file("lint.xml")
        checkReleaseBuilds = true
        abortOnError = true
    }
}

// i18n gate (fallback for lint HardcodedText, which does not flag Compose
// literals): fail the build on string literals in UI text positions.
// See guidelines/UI.md — Internationalisation / Localisation.
val checkHardcodedStrings by tasks.registering {
    val sourceDir = file("src/main/java")
    inputs.dir(sourceDir)
    doLast {
        val patterns = listOf(
            Regex(
                """(?:Text\(|text\s*=|label\s*=|title\s*=|contentDescription\s*=|placeholder\s*=|hint\s*=|\.setTitle\(|\.addText\(|\.setText\()\s*"([^"]+)"""
            ),
            // Conditional assignments: contentDescription = if (x) "A" else "B"
            Regex("""(?:contentDescription|text|label|title)\s*=\s*if\s*\([^)]*\)\s*"([^"]+)"""),
            Regex("""(?:contentDescription|text|label|title)\s*=\s*[^"]*\belse\s*"([^"]+)""")
        )
        val violations = mutableListOf<String>()
        sourceDir.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            file.readLines().forEachIndexed { idx, line ->
                patterns.forEach { pattern ->
                    pattern.findAll(line).forEach { m ->
                        val literal = m.groupValues[1]
                    // Skip dynamic template strings (e.g. "${zoomLevel}") — not hardcoded text
                    if (literal.contains("\${")) return@forEach
                    // Skip format templates (e.g. "%.5f, %.5f") — numeric formats, not display text
                    if (literal.contains('%')) return@forEach
                    // Skip camelCase identifiers (e.g. animation labels like "compassRotation")
                    if (Regex("^[a-z][a-zA-Z0-9]*$").matches(literal)) return@forEach
                    // Skip pure-symbol separators (e.g. "|", "-", "+")
                    if (Regex("^[^a-zA-Z0-9]+$").matches(literal)) return@forEach
                    violations += "${file.relativeTo(projectDir)}:${idx + 1}: $literal"
                    }
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Hardcoded user-facing strings found (i18n gate) — move them to res/values/strings.xml:\n" +
                    violations.joinToString("\n")
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
