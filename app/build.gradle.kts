import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.net.URL
import java.time.LocalDate
import java.util.Properties
import org.cyclonedx.Version
import org.cyclonedx.generators.json.BomJsonGenerator
import org.cyclonedx.gradle.CyclonedxDirectTask
import org.cyclonedx.model.Component
import org.cyclonedx.model.ExternalReference
import org.cyclonedx.parsers.JsonParser
import org.cyclonedx.model.Bom

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlinx.kover")
    id("org.cyclonedx.bom") version "3.4.1"
}

// Release signing credentials: app/keystore.properties (gitignored) or env vars.
val keystoreProperties = Properties().apply {
    val f = file("keystore.properties")
    if (f.exists()) {
        FileInputStream(f).use { load(it) }
    }
}

// ── Release versioning ───────────────────────────────────────────────────────
// Release builds get a date-based versionName (<yyyy>-<MM>-<dd>-<N>) and a
// monotonic versionCode, persisted in app/release-version.properties
// (gitignored). Only an explicit `release` invocation bumps the state; every
// other build uses the fixed fallback below and never touches the file.
val FALLBACK_VERSION_NAME = "1.0.0"
val FALLBACK_VERSION_CODE = 19

val releaseStateFile = file("release-version.properties")

fun readReleaseState(): Properties = Properties().apply {
    if (releaseStateFile.exists()) {
        FileInputStream(releaseStateFile).use { load(it) }
    }
}

fun writeReleaseState(state: Properties) {
    FileOutputStream(releaseStateFile).use {
        state.store(it, "NaviVeylin release version state (managed by the release task)")
    }
}

/**
 * Computes the next release version and persists it.
 * Same day → running number +1; new day → running number resets to 1.
 * versionCode increments by exactly one from the persisted value (first run: 20).
 *
 * The state file is machine-local (gitignored). "Release" FAILS FAST without it:
 * silently restarting the running number at 1 (or versionCode at 20) would emit
 * a duplicate versionName or a versionCode at or below a published one, which
 * Google Play rejects. Same for a state file stamped with a future date (clock/
 * timezone drift between machines near midnight makes the day counter regress).
 */
fun nextReleaseVersion(): Pair<String, Int> {
    if (!releaseStateFile.exists()) {
        throw GradleException(
            "Refusing to bump the release version: app/release-version.properties is missing.\n" +
                "Version state is machine-local (gitignored); running `release` without it would restart " +
                "the running number at -1 and versionCode at 20, producing a duplicate versionName or a " +
                "versionCode below the published one (Google Play rejects the upload).\n" +
                "Restore the file from the release machine, or recreate it with the last published version " +
                "(example: lastDate=/runningNumber=/versionCode=/lastVersionName= of the last AAB upload)."
        )
    }
    val state = readReleaseState()
    val today = LocalDate.now()
    val todayStr = today.toString() // ISO yyyy-MM-dd
    val lastDate = state.getProperty("lastDate")
    if (lastDate != null && lastDate > todayStr) {
        throw GradleException(
            "Refusing to bump the release version: persisted lastDate $lastDate is after today ($todayStr).\n" +
                "Likely clock/timezone drift between release machines near midnight — the day counter would " +
                "regress and emit a duplicate versionName. Sync clocks or fix the state file."
        )
    }
    val runningNumber = if (lastDate == todayStr) {
        (state.getProperty("runningNumber")?.toIntOrNull() ?: 0) + 1
    } else {
        1
    }
    val versionCode = (state.getProperty("versionCode")?.toIntOrNull() ?: FALLBACK_VERSION_CODE) + 1
    val versionName = String.format(
        "%04d-%02d-%02d-%d",
        today.year, today.monthValue, today.dayOfMonth, runningNumber
    )
    state.setProperty("lastDate", todayStr)
    state.setProperty("runningNumber", runningNumber.toString())
    state.setProperty("versionCode", versionCode.toString())
    state.setProperty("lastVersionName", versionName)
    writeReleaseState(state)
    return versionName to versionCode
}

// Bump only when the `release` task was explicitly requested.
val isReleaseBuild = gradle.startParameter.taskNames.any { it == "release" }

// Fail fast: android.injected.* flags (e.g. -Pandroid.injected.build.abi=arm64-v8a)
// are Android Studio internals for test deploys — AGP marks such builds
// android:testOnly=true and Google Play rejects the AAB. Only `release` is
// blocked; debug iteration with the ABI flag stays allowed.
val injectedFlags =
    gradle.startParameter.projectProperties.keys.filter { it.startsWith("android.injected.") }
if (isReleaseBuild) {
    check(injectedFlags.isEmpty()) {
        "release must not run with -Pandroid.injected.* flags (got: ${injectedFlags.joinToString()}). " +
            "AGP marks such builds android:testOnly=true, which Google Play rejects. " +
            "Run plain ./gradlew release instead."
    }
}

val releaseVersion: Pair<String, Int>? = if (isReleaseBuild) nextReleaseVersion() else null

tasks.register("release") {
    group = "release"
    description = "Bumps the version state and builds both Play-ready AABs: mobile (phone + Android Auto) and automotive (AAOS)."
    dependsOn("bundleMobileRelease", "bundleAutomotiveRelease")
    doLast {
        val (versionName, versionCode) =
            releaseVersion ?: error("release task requires a generated version")
        println()
        println("NaviVeylin release: $versionName (versionCode $versionCode)")
        println("AAB phone + Android Auto:  ${layout.buildDirectory.file("outputs/bundle/mobileRelease/app-mobile-release.aab").get().asFile}")
        println("AAB Android Automotive OS: ${layout.buildDirectory.file("outputs/bundle/automotiveRelease/app-automotive-release.aab").get().asFile}")
    }
}

android {
    namespace = "com.naviveylin"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.framstag.naviveylin"
        minSdk = 29 // androidx.car.app:app-automotive (AAOS CarAppActivity) requires 29; API 28 (Android 9) dropped
        targetSdk = 36
        // `release` builds get the generated version; other builds use the fixed fallback.
        versionCode = releaseVersion?.second ?: FALLBACK_VERSION_CODE
        versionName = releaseVersion?.first ?: FALLBACK_VERSION_NAME

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    ndkVersion = "27.0.12077973"

    signingConfigs {
        create("release") {
            val keystoreFile = file("release.keystore")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = keystoreProperties.getProperty("storePassword")
                    ?: System.getenv("NAVIVEYLIN_STORE_PASSWORD")
                    ?: ""
                keyAlias = keystoreProperties.getProperty("keyAlias")
                    ?: System.getenv("NAVIVEYLIN_KEY_ALIAS")
                    ?: ""
                keyPassword = keystoreProperties.getProperty("keyPassword")
                    ?: System.getenv("NAVIVEYLIN_KEY_PASSWORD")
                    ?: ""
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile?.exists() == true) {
                signingConfig = releaseSigning
            } else {
                logger.warn(
                    "app/release.keystore not found — release builds will be UNSIGNED. " +
                        "Generate it and app/keystore.properties before shipping."
                )
            }
        }
    }

    // Two distribution flavors, same applicationId (single Play Store listing):
    //  - mobile     → phones/tablets + Android Auto (projection);
    //                 com.google.android.gms.car.application metadata only
    //  - automotive → standalone AAOS head units; android.hardware.type.automotive
    //    + com.android.automotive metadata via src/automotive/AndroidManifest.xml
    // Google Play rejects one AAB declaring BOTH android.hardware.type.automotive
    // and com.google.android.gms.car.application, so the AAOS build is a separate
    // bundle uploaded to the dedicated "Android Automotive OS" track.
    flavorDimensions += "dist"
    productFlavors {
        create("mobile") {
            dimension = "dist"
        }
        create("automotive") {
            dimension = "dist"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    sourceSets {
        getByName("main") {
            // Stylesheets are copied from the pinned libosmscout submodule into a
            // generated assets root at build time (syncSubmoduleStylesheets) — no
            // committed snapshot. Upstream style updates reach the APK with the
            // submodule bump; there is no snapshot to keep in sync.
            assets.srcDir("build/generated/assets")
        }
    }

    testOptions {
        unitTests {
            // Robolectric Compose UI tests need Android resources
            isIncludeAndroidResources = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        // i18n gate: HardcodedText elevated to error via lint.xml (see guidelines/UI.md)
        lintConfig = file("lint.xml")
        checkReleaseBuilds = true
        abortOnError = true
    }
}

// ── Test coverage (Kover) ───────────────────────────────────────────────
// JVM unit test coverage, report-only. Generated code (BuildConfig, R,
// Hilt/Dagger/KSP wiring) is excluded so metrics reflect hand-written
// logic. See guidelines/Build.md → Code coverage.
kover {
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

// Stylesheets are sourced from the libosmscout submodule at build time. Copy the
// submodule stylesheet directory into a generated assets root so the APK packages
// exactly the current submodule state — "stylesheets/..." in the APK, matching the
// AssetCopier contract.
tasks.register<Sync>("syncSubmoduleStylesheets") {
    from("src/main/cpp/libosmscout/stylesheets")
    into(layout.buildDirectory.dir("generated/assets/stylesheets"))
}

// Fail fast with an actionable message when the submodule is not checked out
// (fresh clone), instead of packaging an APK without stylesheets.
tasks.register("checkSubmoduleStylesheets") {
    val stylesheetsDir = file("src/main/cpp/libosmscout/stylesheets")
    doFirst {
        check(stylesheetsDir.isDirectory) {
            "libosmscout submodule stylesheets not found at $stylesheetsDir. " +
                "Initialize the submodule first: git submodule update --init --recursive"
        }
    }
}

tasks.named("preBuild") {
    dependsOn("checkSubmoduleStylesheets", "syncSubmoduleStylesheets")
}

// Ensure every asset merge (debug/release/test) copies the submodule stylesheets
// first — the sync output is a static build-dir path, so no automatic dependency
// is carried from the source set.
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }
    .configureEach {
        dependsOn("syncSubmoduleStylesheets")
    }

// ── SBOM (CycloneDX) ───────────────────────────────────────────────────────
// Per-variant CycloneDX SBOMs: the JVM dependency graph (:app + :auto + :core
// + :osmscout-client-java via the variant runtime classpath), merged with the
// native vcpkg dependency tree (converted from vcpkg's per-package SPDX
// SBOMs) and a libosmscout submodule record. `release` emits one SBOM per
// distribution flavor next to its AAB; any variant can generate standalone
// (CI emits the mobileDebug one). Outputs:
//   app/build/outputs/sbom/<variant>/bom.json   (full merged SBOM)
//   app/build/outputs/sbom/<variant>/jvm-bom.json  (JVM section, from plugin)
//   app/build/outputs/sbom/native/native-bom.json  (native section, shared)
val sbomCliVersion = "0.33.1"
val sbomCliBaseUrl =
    "https://github.com/CycloneDX/cyclonedx-cli/releases/download/v$sbomCliVersion"

val vcpkgRootDir = System.getenv("VCPKG_ROOT") ?: rootProject.file("vcpkg").canonicalPath
val vcpkgTriplets = listOf("arm64-android", "arm-neon-android", "x64-android")

// Variants that can emit an SBOM; release wires the two release variants.
val sbomVariants = listOf("mobileDebug", "mobileRelease", "automotiveRelease")

// cyclonedx-cli ships platform binaries; pick the one for the host.
val sbomHostOs = System.getProperty("os.name").lowercase()
val sbomHostArch = System.getProperty("os.arch").lowercase()
val sbomCliAsset = when {
    sbomHostOs.contains("linux") &&
        (sbomHostArch == "amd64" || sbomHostArch == "x86_64") -> "cyclonedx-linux-x64"
    sbomHostOs.contains("linux") &&
        (sbomHostArch == "aarch64" || sbomHostArch == "arm64") -> "cyclonedx-linux-arm64"
    (sbomHostOs.contains("mac") || sbomHostOs.contains("darwin")) &&
        (sbomHostArch == "aarch64" || sbomHostArch == "arm64") -> "cyclonedx-osx-arm64"
    sbomHostOs.contains("mac") || sbomHostOs.contains("darwin") -> "cyclonedx-osx-x64"
    sbomHostOs.contains("win") -> "cyclonedx-win-x64.exe"
    else -> error(
        "Unsupported host OS for SBOM generation: ${System.getProperty("os.name")} / " +
            "$sbomHostArch. Supported: Linux amd64/arm64, macOS x64/arm64, Windows x64."
    )
}

val sbomCliFile = layout.buildDirectory.file("cyclonedx-cli/$sbomCliAsset")
val nativeBomFile = layout.buildDirectory.file("outputs/sbom/native/native-bom.json")
val sbomOutputRoot = layout.buildDirectory.dir("outputs/sbom")

// One-time (cached) download of the pinned CLI. Pure-Java download so the
// task works on every host; fails with an actionable message on network error.
val downloadSbomCli by tasks.registering {
    group = "sbom"
    description =
        "Downloads the pinned cyclonedx-cli binary (cached under build/cyclonedx-cli/)."
    inputs.property("sbomCliVersion", sbomCliVersion)
    inputs.property("sbomCliAsset", sbomCliAsset)
    outputs.file(sbomCliFile)
    doLast {
        val cli = sbomCliFile.get().asFile
        if (cli.isFile && cli.canExecute()) return@doLast // already cached
        val url = "$sbomCliBaseUrl/$sbomCliAsset"
        try {
            cli.parentFile.mkdirs()
            val tmp = File(cli.parentFile, "${cli.name}.part")
                        URL(url).openStream().use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            if (!tmp.renameTo(cli)) {
                tmp.copyTo(cli, overwrite = true)
                tmp.delete()
            }
            cli.setExecutable(true)
        } catch (e: Exception) {
            throw GradleException(
                "Failed to download cyclonedx-cli v$sbomCliVersion from $url: ${e.message}. " +
                    "Check network access (one-time download; cached at ${cli.parentFile}).",
                e
            )
        }
    }
}

// Runs cyclonedx-cli, failing with its stderr on a non-zero exit.
fun runSbomCli(cli: File, vararg args: String): String {
    val process = ProcessBuilder(listOf(cli.absolutePath) + args).start()
    val stdout = process.inputStream.readBytes().toString(Charsets.UTF_8)
    val stderr = process.errorStream.readBytes().toString(Charsets.UTF_8)
    val exit = process.waitFor()
    if (exit != 0) {
        throw GradleException(
            "cyclonedx-cli ${args.joinToString(" ")} failed (exit $exit):\n$stderr"
        )
    }
    return stdout
}

fun writeBom(file: File, bom: Bom) {
    FileWriter(file).use { it.write(BomJsonGenerator(bom, Version.VERSION_17).toJsonString()) }
}

// vcpkg SPDX conversions carry per-file and per-binary components; keep only
// the real pkg:vcpkg packages, deduplicated by name+version (identical
// packages span all three triplets).
fun collectNativeComponents(convertedFiles: List<File>): List<Component> {
    val seen = mutableSetOf<Triple<String?, String?, String?>>()
    val result = mutableListOf<Component>()
    convertedFiles.forEach { file ->
        JsonParser().parse(file).components.orEmpty().forEach { c ->
            if (c.purl?.startsWith("pkg:vcpkg/") == true &&
                seen.add(Triple(c.group, c.name, c.version))
            ) {
                result += c
            }
        }
    }
    return result
}

// Collapses components that share group+name+version into the first entry.
fun dedupeComponents(bom: Bom) {
    val seen = mutableSetOf<Triple<String?, String?, String?>>()
    bom.components =
        (bom.components.orEmpty()).filter { seen.add(Triple(it.group, it.name, it.version)) }
}

// vcpkg derives source-origin URLs from portfile.cmake; unexpanded version
// templates (e.g. ${VERSION_MAJOR_MINOR}) are not valid URIs and fail
// CycloneDX schema validation. Drop such external references.
fun stripTemplateUrls(bomFile: File) {
    val bom = JsonParser().parse(bomFile)
    bom.components?.forEach { component ->
        val refs = component.externalReferences
        if (refs != null) {
            val clean = refs.filterTo(mutableListOf()) { it.url?.contains("\${") != true }
            component.externalReferences = if (clean.isEmpty()) null else clean
        }
    }
    writeBom(bomFile, bom)
}

// Converts every installed vcpkg package's SPDX SBOM (all triplets) to
// CycloneDX and merges them into one shared native BOM. The port list comes
// from vcpkg's installed-status file, filtered to the triplet's architecture
// (share/ also contains non-port data dirs, and the status file mixes
// host-tool records). Fails naming the missing file when an installed port
// has no SPDX record, so an SBOM is never silently produced without the
// native section.
fun vcpkgPorts(vcpkgStatusFile: File, triplet: String): List<String> {
    if (!vcpkgStatusFile.isFile) {
        throw GradleException(
            "vcpkg installed-status file not found at $vcpkgStatusFile " +
                "— run ./setup-vcpkg.sh first."
        )
    }
    val excluded = setOf(
        // vcpkg helper/tool ports carry no third-party sources (vcpkg-cmake,
        // vcpkg-make, ... are build plumbing, not shipped dependencies).
        "vcpkg-cmake", "vcpkg-cmake-config", "vcpkg-cmake-get-vars",
        "vcpkg-make", "vcpkg-tool-meson",
        // Stale status entry: intentionally NOT installed via vcpkg (headers
        // would shadow the submodule headers — see setup-vcpkg.sh).
        "libosmscout"
    )
    val ports = mutableSetOf<String>()
    val lines = vcpkgStatusFile.readLines()
    var i = 0
    while (i < lines.size) {
        val block = lines.drop(i).takeWhile { it.isNotBlank() }
        i += block.size + 1
        val pkg = block.firstOrNull { it.startsWith("Package: ") }
            ?.removePrefix("Package: ")?.trim()
        val arch = block.firstOrNull { it.startsWith("Architecture: ") }
            ?.removePrefix("Architecture: ")?.trim()
        if (pkg != null && arch == triplet && pkg !in excluded) ports += pkg
    }
    return ports.sorted()
}

val mergeNativeSbom by tasks.registering {
    group = "sbom"
    description =
        "Converts every vcpkg package SPDX SBOM (all triplets) to CycloneDX and merges them."
    val vcpkgStatusFile = File(vcpkgRootDir, "installed/vcpkg/status")
    val shareDirs = vcpkgTriplets.map { File(vcpkgRootDir, "installed/$it/share") }
    inputs.file(vcpkgStatusFile)
    shareDirs.forEach { inputs.dir(it) }
    inputs.property("vcpkgPorts", vcpkgTriplets.map { vcpkgPorts(vcpkgStatusFile, it) })
    outputs.file(nativeBomFile)
    dependsOn(downloadSbomCli)
    doLast {
        val cli = sbomCliFile.get().asFile
        if (shareDirs.none { it.isDirectory }) {
            throw GradleException(
                "vcpkg dependency tree not found under $vcpkgRootDir " +
                    "(expected installed/{${vcpkgTriplets.joinToString(",")}}/share). " +
                    "Set VCPKG_ROOT or run ./setup-vcpkg.sh first."
            )
        }
        val convertedDir = File(layout.buildDirectory.get().asFile, "outputs/sbom/native/converted")
        convertedDir.mkdirs()
        val converted = mutableListOf<File>()
        vcpkgTriplets.forEach { triplet ->
            val share = File(vcpkgRootDir, "installed/$triplet/share")
            vcpkgPorts(vcpkgStatusFile, triplet).forEach { port ->
                val spdx = File(share, "$port/vcpkg.spdx.json")
                if (!spdx.isFile) {
                    throw GradleException(
                        "Missing SBOM data for vcpkg package '$port' ($triplet): " +
                            "expected $spdx. The installed package predates vcpkg SBOM " +
                            "support — rebuild it: rm -rf vcpkg/buildtrees/$port " +
                            "&& ./setup-vcpkg.sh"
                    )
                }
                val out = File(convertedDir, "$triplet-$port.json")
                runSbomCli(
                    cli,
                    "convert",
                    "--input-file", spdx.absolutePath,
                    "--input-format", "spdxjson",
                    "--output-format", "json",
                    "--output-file", out.absolutePath
                )
                stripTemplateUrls(out)
                converted += out
            }
        }
        if (converted.isEmpty()) {
            throw GradleException(
                "No vcpkg SBOM data found under $vcpkgRootDir/installed — " +
                    "cannot build the native SBOM section."
            )
        }
        val nativeBom = Bom().apply {
            components = collectNativeComponents(converted)
            metadata = JsonParser().parse(converted.first()).metadata
        }
        writeBom(nativeBomFile.get().asFile, nativeBom)
        runSbomCli(cli, "validate", "--input-file", nativeBomFile.get().asFile.absolutePath)
    }
}

// The libosmscout submodule SHA, recorded as a component in every SBOM.
fun submoduleSha(): String {
    val dir = file("src/main/cpp/libosmscout")
    val process = ProcessBuilder("git", "-C", dir.absolutePath, "rev-parse", "HEAD").start()
    val out = process.inputStream.readBytes().toString(Charsets.UTF_8).trim()
    val err = process.errorStream.readBytes().toString(Charsets.UTF_8)
    val exit = process.waitFor()
    if (exit != 0) {
        throw GradleException(
            "Could not read the libosmscout submodule HEAD at $dir ($err). " +
                "Initialise the submodule: git submodule update --init --recursive"
        )
    }
    return out
}

sbomVariants.forEach { variant ->
    val variantLabel = variant.replaceFirstChar(Char::uppercaseChar)
    // JVM section: resolved dependency graph of exactly this variant's
    // runtime classpath (test configurations excluded by construction).
    val jvmTask = tasks.register<CyclonedxDirectTask>("generateSbomJvm$variantLabel") {
        group = "sbom"
        description = "Generates the JVM dependency CycloneDX BOM for $variant."
        includeConfigs = listOf("${variant}RuntimeClasspath")
        skipConfigs = listOf("(?i).*test.*")
        schemaVersion = Version.VERSION_17
        projectType = Component.Type.APPLICATION
        componentName = "NaviVeylin"
        componentVersion = releaseVersion?.first ?: FALLBACK_VERSION_NAME
        jsonOutput = sbomOutputRoot.map { it.dir(variant).file("jvm-bom.json") }
        xmlOutput.unsetConvention()
    }

    // Final merged SBOM: JVM + native + submodule, validated.
    val finalTask = tasks.register("generateSbom$variantLabel") {
        group = "sbom"
        description =
            "Generates the full CycloneDX SBOM for $variant (JVM + native + submodule)."
        val finalBom = sbomOutputRoot.map { it.dir(variant).file("bom.json") }
        val jvmBom = jvmTask.flatMap { it.jsonOutput }
        inputs.file(jvmBom)
        inputs.file(nativeBomFile)
        outputs.file(finalBom)
        dependsOn(jvmTask, mergeNativeSbom, downloadSbomCli)
        doLast {
            val cli = sbomCliFile.get().asFile
            val finalFile = finalBom.get().asFile
            finalFile.parentFile.mkdirs()
            val merged = finalFile.parentFile.resolve("merged-tmp.json")
            runSbomCli(
                cli,
                "merge",
                "--input-files", jvmBom.get().asFile.absolutePath,
                "--input-files", nativeBomFile.get().asFile.absolutePath,
                "--output-file", merged.absolutePath,
                "--output-format", "json"
            )
            val bom = JsonParser().parse(merged)
            dedupeComponents(bom)
            // Submodule record — version is the checked-out SHA.
            val sha = submoduleSha()
            val libosmscout = Component().apply {
                type = Component.Type.LIBRARY
                group = "Framstag"
                name = "libosmscout"
                version = sha
                purl = "pkg:github/framstag/libosmscout@$sha"
                externalReferences = listOf(
                    ExternalReference().apply {
                        type = ExternalReference.Type.VCS
                        url = "https://github.com/Framstag/libosmscout"
                    }
                )
            }
            bom.components = (bom.components ?: emptyList()) + libosmscout
            // Top-level component: NaviVeylin at the build version.
            val metadataComponent = bom.metadata?.component
                ?: Component().also { bom.metadata?.component = it }
            metadataComponent.apply {
                type = Component.Type.APPLICATION
                name = "NaviVeylin"
                version = releaseVersion?.first ?: FALLBACK_VERSION_NAME
            }
            writeBom(finalFile, bom)
            merged.delete()
            runSbomCli(cli, "validate", "--input-file", finalFile.absolutePath)
        }
    }
}

// The release target ships both AABs with their SBOMs.
tasks.named("release") {
    dependsOn("generateSbomMobileRelease", "generateSbomAutomotiveRelease")
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Lifecycle + ViewModel
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.59")
    ksp("com.google.dagger:hilt-compiler:2.59")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // WindowManager (foldable support)
    implementation("androidx.window:window:1.3.0")

    // Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Core shared module
    implementation(project(":core"))

    // Android Auto module
    implementation(project(":auto"))

    // JNI bridge (libosmscout-client-java)
    implementation(project(":osmscout-client-java"))

    // Core library desugaring (java.net.http, etc.)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Google Play Services Location (GPS) — optional, fallback to LocationManager
    implementation("com.google.android.gms:play-services-location:21.1.0")

    // Car App Library — Android Automotive OS entry point (CarAppActivity
    // binds the AAOS template host; MainActivity trampolines to it on cars).
    implementation("androidx.car.app:app-automotive:1.7.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.robolectric:robolectric:4.16")
    // Mocking for ViewModel state in Compose UI tests (final Kotlin classes)
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    // Compose UI tests under Robolectric (createComposeRule)
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
