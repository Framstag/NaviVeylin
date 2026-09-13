plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

// Match the app modules' Java 17 target so the Kotlin and Java compilation
// targets agree (otherwise buildSrc emits an inconsistent-JVM-target warning on
// a newer daemon JVM).
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

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
