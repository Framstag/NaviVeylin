plugins {
    id("java-library")
    id("idea")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

val clientJavaDir = "${rootDir}/app/src/main/cpp/libosmscout/libosmscout-client-java/java"

sourceSets {
    main {
        java {
            setSrcDirs(listOf("src/main/java"))
        }
    }
}

// Register submodule sources for IDE resolution (Android Studio / IntelliJ)
idea {
    module {
        sourceDirs.add(file(clientJavaDir))
    }
}

// Include all submodule Java sources except files provided locally
// (with Android-compatible HTTP and debug-suffix-aware library loading)
tasks.named<JavaCompile>("compileJava") {
    source(fileTree(clientJavaDir).matching {
        exclude(
            "**/OSMScoutClientBuilder.java",
            "**/OSMScoutClient.java",
            "**/MapDownloadManager.java",
            "**/AvailableMapEntry.java",
            "**/BasemapManager.java"
        )
    })
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("libosmscoutclientjava")
    archiveVersion.set("1.0.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
