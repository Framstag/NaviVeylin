import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.net.URL
import java.time.LocalDate
import java.util.Properties
import groovy.json.JsonSlurper
import com.naviveylin.build.licensing.ComponentLicense
import com.naviveylin.build.licensing.ComponentProbes
import com.naviveylin.build.licensing.Election
import com.naviveylin.build.licensing.GenerateLicenseAssets
import com.naviveylin.build.licensing.LicenseData
import com.naviveylin.build.licensing.LicenseGate
import com.naviveylin.build.licensing.LicensePolicy
import com.naviveylin.build.licensing.LicenseRefDeclaration
import com.naviveylin.build.licensing.LicenseResolution
import com.naviveylin.build.licensing.LicenseResolver
import com.naviveylin.build.licensing.NativeScopeClassifier
import com.naviveylin.build.licensing.Scope
import com.naviveylin.build.licensing.ScopeEvidence
import org.cyclonedx.Version
import org.cyclonedx.generators.json.BomJsonGenerator
import org.cyclonedx.gradle.CyclonedxDirectTask
import org.cyclonedx.model.Component
import org.cyclonedx.model.ExternalReference
import org.cyclonedx.model.License
import org.cyclonedx.model.LicenseChoice
import org.cyclonedx.model.Property
import org.cyclonedx.model.license.Expression
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
// automotiveDebug is included because the license inventory is generated per
// variant and read at runtime: an automotive debug build must carry its own.
val sbomVariants =
    listOf("mobileDebug", "mobileRelease", "automotiveDebug", "automotiveRelease")

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

// ── License compliance ──────────────────────────────────────────────────────
// Curated inputs live at the repository root and are committed data:
//   licenses/native-license-map.json — the license each native component
//     declares, the evidence for that claim, and symbol probes used to find the
//     component's code inside a shipped shared object
//   licenses/license-policy.json — permitted licenses per distribution scope,
//     recorded elections for dual-licensed components, LicenseRef declarations
//     for licenses without an SPDX identifier, and the identifiers that still
//     need the application license decision
//   licenses/texts/<identifier>.txt — canonical license texts distributed with
//     the application
// The evaluation logic itself is pure Kotlin in buildSrc (unit-tested by the
// build); this file only reads data, resolves each component, and writes the
// result into the SBOM and into generated assets.
val licenseMapFile = rootProject.file("licenses/native-license-map.json")
val licensePolicyFile = rootProject.file("licenses/license-policy.json")
val licenseTextsDir = rootProject.file("licenses/texts")

// Our own components resolve to a declared LicenseRef: the project's own license
// is undecided, so the gate records that fact instead of exempting first-party
// code from the rules the third-party components follow.
val FIRST_PARTY_LICENSE_REF = "LicenseRef-NaviVeylin"

/** Group that our own Gradle modules and the application component use. */
val FIRST_PARTY_GROUP = "NaviVeylin"

/** ABIs this build produces, when the build restricts them (`-Pandroid.injected.build.abi`). */
val injectedAbis: Set<String> = (project.findProperty("android.injected.build.abi") as? String)
    .orEmpty()
    .split(",")
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .toSet()

fun parseLicenseJson(file: File): Map<String, Any?> =
    LicenseData.parseObject(file, "license data")

fun loadNativeLicenseMap(): Map<String, Map<String, Any?>> = LicenseData.nativeMap(licenseMapFile)

fun loadNativeLicenseProbes(): Map<String, Map<String, Any?>> = LicenseData.probes(licenseMapFile)

fun loadLicensePolicy(): LicensePolicy = LicenseData.policy(licensePolicyFile)

// ── Shipped / build-time classification ─────────────────────────────────────
// The scope of a native component is derived from the built artifact: its code
// is present in a packaged shared object (probe symbol), or one of its static
// archives is referenced by the native link configuration while the packaged
// object carries no probe (hidden symbols). Everything else is build-time only.
fun definedSymbols(sharedObject: File): Set<String> {
    val process = try {
        ProcessBuilder("nm", "-D", "--defined-only", sharedObject.absolutePath).start()
    } catch (e: Exception) {
        throw GradleException(
            "Could not run 'nm' to classify native components (needed for ${sharedObject.name}): " +
                "${e.message}. Install binutils (Linux) or Xcode command line tools (macOS).",
            e
        )
    }
    val out = process.inputStream.readBytes().toString(Charsets.UTF_8)
    process.errorStream.readBytes()
    process.waitFor()
    return out.lineSequence()
        .mapNotNull { line -> line.trim().split(" ").lastOrNull()?.takeIf { it.isNotBlank() } }
        .toSet()
}

/** Static archives installed by a vcpkg port, from its own SPDX file list. */
fun portArchives(port: String, triplet: String): Set<String> {
    val spdx = File(vcpkgRootDir, "installed/$triplet/share/$port/vcpkg.spdx.json")
    if (!spdx.isFile) return emptySet()
    @Suppress("UNCHECKED_CAST")
    val root = JsonSlurper().parse(spdx) as Map<String, Any?>
    @Suppress("UNCHECKED_CAST")
    val files = (root["files"] as? List<Map<String, Any?>>).orEmpty()
    return files.mapNotNull { it["fileName"] as? String }
        .filter { it.contains("/lib/") && it.endsWith(".a") && !it.startsWith("./debug/") }
        .map { it.substringAfterLast('/') }
        .toSet()
}

/**
 * Static archives the native build actually links into shipped shared objects.
 *
 * Only archives that reach a link line count: literal `lib*.a` names inside
 * `target_link_libraries(...)` calls, plus the archive lists that are passed to
 * such a call through a variable. Cache-variable defaults for optional
 * dependencies (FindXml2's `LIBXML2_LIBRARY`, FindProtobuf's `PROTOBUF_LIBRARY`)
 * are deliberately ignored — they exist so the find module can succeed, and
 * neither library ends up in a packaged object.
 */
fun linkedArchives(): Set<String> {
    val cmakeFiles = listOf(
        file("src/main/cpp/CMakeLists.txt"),
        file("src/main/cpp/libosmscout/libosmscout/CMakeLists.txt"),
        file("src/main/cpp/libosmscout/libosmscout-map/CMakeLists.txt"),
        file("src/main/cpp/libosmscout/libosmscout-map-cairo/CMakeLists.txt")
    ).filter { it.isFile }
    val archivePattern = Regex("lib[A-Za-z0-9_.+-]*\\.a")
    val linkCall = Regex("target_link_libraries\\s*\\(([^)]*)\\)", RegexOption.DOT_MATCHES_ALL)
    val archiveList = Regex(
        "set\\s*\\(\\s*(?:CAIRO|MARISA|PANGO|PNG|ZLIB|FREETYPE)_LIBRARIES[^)]*\\)",
        RegexOption.DOT_MATCHES_ALL
    )
    val result = mutableSetOf<String>()
    cmakeFiles.forEach { cmake ->
        val text = cmake.readText()
        (linkCall.findAll(text) + archiveList.findAll(text)).forEach { match ->
            archivePattern.findAll(match.value).forEach { result += it.value }
        }
    }
    return result
}

/**
 * Classifies every component of the native license map against one built
 * variant's packaged native libraries. Reading the artifact (symbol tables of
 * the packaged objects, installed archives, native link lines) happens here;
 * the decision itself is in buildSrc, where it is unit-tested.
 */
fun classifyNativeScopes(
    nativeMap: Map<String, Map<String, Any?>>,
    probes: Map<String, Map<String, Any?>>,
    packagedLibs: List<File>,
    linked: Set<String>,
    triplets: List<String>
): Map<String, ScopeEvidence> {
    val packagedObjects = packagedLibs.associate { lib ->
        lib.name.removeSuffix(".so") to definedSymbols(lib)
    }
    val probeSpecs = probes.mapValues { (_, spec) ->
        @Suppress("UNCHECKED_CAST")
        ComponentProbes(
            exact = (spec["exact"] as? List<String>).orEmpty(),
            prefix = (spec["prefix"] as? List<String>).orEmpty(),
        )
    }
    val archivesByComponent = nativeMap.keys.associateWith { name ->
        triplets.flatMap { portArchives(name, it) }.toSet()
    }
    return NativeScopeClassifier.classify(
        components = nativeMap.keys.toList(),
        probes = probeSpecs,
        packagedObjects = packagedObjects,
        archivesByComponent = archivesByComponent,
        linkedArchives = linked,
    )
}

/**
 * License text source problems for shipped components: the app distributes
 * these texts, so a missing source would ship a license screen with a hole.
 */
fun missingLicenseTextSources(
    nativeMap: Map<String, Map<String, Any?>>,
    scopes: Map<String, ScopeEvidence>
): List<String> = nativeMap.entries.mapNotNull { (name, entry) ->
    if (scopes[name]?.scope != Scope.SHIPPED) return@mapNotNull null
    when (entry["textSource"] as? String) {
        "vcpkgPort" -> {
            val missing = vcpkgTriplets.filter {
                !File(vcpkgRootDir, "installed/$it/share/$name/copyright").isFile
            }
            if (missing.isEmpty()) null else
                "$name: license text missing for ${missing.joinToString(", ")} — rebuild the port: " +
                    "rm -rf vcpkg/buildtrees/$name && ./setup-vcpkg.sh"
        }
        else -> null
    }
}

/**
 * Packaged native libraries with no corresponding inventory component: every
 * object in the application must be accounted for, so an unrecorded library
 * fails the build instead of shipping unnoticed.
 *
 * Names are compared with separators normalised: a component is called
 * `graphics-path` while its packaged object is `libandroidx.graphics.path.so`.
 */
fun unrecordedPackagedLibraries(
    packagedLibs: List<File>,
    nativeMap: Map<String, Map<String, Any?>>,
    componentNames: Set<String>
): List<String> {
    fun normalise(value: String) = value.lowercase().filter { it.isLetterOrDigit() }
    return packagedLibs.distinctBy { it.name }.filter { lib ->
        val base = lib.name.removeSuffix(".so")
        val nativeHit = nativeMap.keys.any { NativeScopeClassifier.matches(it, base) }
        val componentHit = componentNames.any { normalise(it) in normalise(base) }
        !nativeHit && !componentHit
    }.map { it.name }
}

// ── License resolution against the SBOM component set ───────────────────────
data class ComponentLicenseData(
    val scope: Scope,
    val evidence: String?,
    val caveat: String?,
)

fun componentName(component: Component): String =
    component.group?.let { "$it:${component.name}" } ?: component.name

/** SPDX identifiers a JVM component declares in its published metadata. */
fun declaredJvmLicense(component: Component): Pair<String?, Boolean> {
    val choices = component.licenses?.items.orEmpty()
    val ids = choices.mapNotNull { it.license?.id }
    if (ids.isNotEmpty()) return ids.joinToString(" AND ") to true
    val names = choices.mapNotNull { it.license?.name }
    if (names.isNotEmpty()) return names.first() to false
    return null to false
}

/**
 * Resolves every component's license, records the distribution scope and
 * evidence as component properties, and fails when the artifact-derived scope
 * disagrees with the curated expectation in the license map.
 */
fun applyLicenseData(
    bom: Bom,
    nativeMap: Map<String, Map<String, Any?>>,
    policy: LicensePolicy,
    scopes: Map<String, ScopeEvidence>,
) {
    val resolver = LicenseResolver(policy)
    val problems = mutableListOf<String>()

    bom.components.orEmpty().forEach { component ->
        val name = component.name
        val native = nativeMap.containsKey(name)
        // First-party components: our own Gradle modules and the application
        // itself. They carry no third-party license, so they resolve to the
        // declared first-party LicenseRef instead of a published license.
        val firstParty = !native && (
            component.group == FIRST_PARTY_GROUP ||
                component.purl?.startsWith("pkg:maven/$FIRST_PARTY_GROUP/") == true ||
                (name == "NaviVeylin" && component.type == Component.Type.APPLICATION)
            )
        val scopeEvidence = scopes[name]

        val declared: String?
        val declaredIsSpdx: Boolean
        val scope: Scope
        val caveat: String?
        val evidence: String?

        when {
            native -> {
                val entry = nativeMap.getValue(name)
                declared = entry["declared"] as? String
                declaredIsSpdx = true
                caveat = entry["caveat"] as? String
                evidence = entry["evidence"] as? String
                val derived = scopeEvidence
                if (derived == null) {
                    problems += "$name: the license map has a scope for this component but the " +
                        "classification produced none"
                    scope = Scope.BUILD_TIME_ONLY
                } else {
                    val expected = entry["scope"] as? String
                    if (expected != null && expected != derived.scope.id) {
                        problems += "$name: curated scope '$expected' disagrees with the derived scope " +
                            "'${derived.scope.id}' (${derived.evidence}). Update licenses/native-license-map.json " +
                            "if the shipped library set legitimately changed."
                    }
                    scope = derived.scope
                }
            }
            firstParty -> {
                declared = FIRST_PARTY_LICENSE_REF
                declaredIsSpdx = false
                scope = Scope.SHIPPED
                caveat = "first-party code: the project's own license is undecided"
                evidence = "first-party component ($name)"
            }
            else -> {
                val (value, isSpdx) = declaredJvmLicense(component)
                declared = value
                declaredIsSpdx = isSpdx
                scope = Scope.SHIPPED
                caveat = null
                evidence = "declared in the component's published metadata"
            }
        }

        val claim = ComponentLicense(
            component = componentName(component),
            declared = declared,
            declaredIsSpdx = declaredIsSpdx,
            scope = scope,
            caveat = caveat,
        )

        // Report resolution failures at build time, with the component named.
        val noticeRequired = when (val resolution = resolver.resolve(claim)) {
            is LicenseResolution.Unresolved -> {
                problems += "${claim.component}: ${resolution.reason}"
                false
            }
            is LicenseResolution.NeedsElection -> {
                problems += "${claim.component}: license '${resolution.declared}' offers " +
                    "alternatives (${resolution.offered.joinToString(", ")}) without a recorded election"
                false
            }
            is LicenseResolution.Resolved -> {
                component.licenses = LicenseChoice().apply {
                    if (resolution.identifier.startsWith(LicenseGate.LICENSE_REF_PREFIX)) {
                        addExpression(Expression(resolution.identifier))
                    } else {
                        resolution.ids.forEach { addLicense(License().apply { id = it }) }
                    }
                }
                resolution.ids.any { it in policy.noticeRequired } && scope == Scope.SHIPPED
            }
        }

        val properties = component.properties?.toMutableList() ?: mutableListOf()
        properties += Property("naviveylin:license:scope", scope.id)
        evidence?.let { properties += Property("naviveylin:license:scope-evidence", it) }
        if (scopeEvidence?.ambiguous == true) {
            properties += Property(
                "naviveylin:license:scope-ambiguity",
                "shipped without symbol evidence: the link configuration is the only " +
                    "evidence that this component's code is in the application"
            )
        }
        caveat?.let { properties += Property("naviveylin:license:caveat", it) }
        properties += Property(
            "naviveylin:license:notice",
            if (noticeRequired) "required" else "not-required"
        )
        component.properties = properties
    }

    if (problems.isNotEmpty()) {
        throw GradleException(
            "License data could not be resolved for every component:\n  " +
                problems.joinToString("\n  ")
        )
    }
}

/**
 * Reads the dependency inventory back out of a generated SBOM and rebuilds the
 * claims the gate evaluates. The gate deliberately reads the SBOM rather than
 * in-memory state: it validates the artifact that ships, not the intentions of
 * the code that produced it.
 */
fun sbomLicenseClaims(bomFile: File): List<ComponentLicense> {
    if (!bomFile.isFile) {
        throw GradleException(
            "SBOM not found at $bomFile — generate it first (e.g. " +
                "./gradlew :app:generateSbomMobileDebug)."
        )
    }
    @Suppress("UNCHECKED_CAST")
    val root = JsonSlurper().parse(bomFile) as Map<String, Any?>
    @Suppress("UNCHECKED_CAST")
    val components = (root["components"] as? List<Map<String, Any?>>).orEmpty()
    return components.map { component ->
        val group = component["group"] as? String
        val name = component["name"] as? String ?: "?"
        val key = if (group == null) name else "$group:$name"
        @Suppress("UNCHECKED_CAST")
        val licenses = (component["licenses"] as? List<Map<String, Any?>>).orEmpty()
        val expression = licenses.mapNotNull { it["expression"] as? String }.firstOrNull()
        val ids = licenses.mapNotNull { (it["license"] as? Map<*, *>)?.get("id") as? String }
        val names = licenses.mapNotNull { (it["license"] as? Map<*, *>)?.get("name") as? String }
        val declared = expression ?: ids.joinToString(" AND ").ifEmpty { names.firstOrNull() }
        val declaredIsSpdx = expression != null || ids.isNotEmpty()
        @Suppress("UNCHECKED_CAST")
        val properties = (component["properties"] as? List<Map<String, Any?>>).orEmpty()
        val scopeId = properties.firstOrNull { it["name"] == "naviveylin:license:scope" }
            ?.get("value") as? String
            ?: throw GradleException(
                "Component '$key' in $bomFile carries no distribution scope. The SBOM was " +
                    "generated before license enrichment — regenerate it."
            )
        val caveat = properties.firstOrNull { it["name"] == "naviveylin:license:caveat" }
            ?.get("value") as? String
        ComponentLicense(
            component = key,
            declared = declared,
            declaredIsSpdx = declaredIsSpdx,
            scope = Scope.entries.first { it.id == scopeId },
            caveat = caveat,
        )
    }
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
        // Shipped native libraries are the input to the scope classification:
        // the stripped objects are what the APK packages.
        val strippedRoot = layout.buildDirectory.dir("intermediates/stripped_native_libs/$variant")
        val packagedNativeLibs = fileTree(strippedRoot) { include("**/lib/*/*.so") }
        val stripTask = tasks.matching {
            it.name.startsWith("strip") && it.name.endsWith("Symbols") &&
                it.name.contains(variantLabel)
        }
        inputs.file(jvmBom)
        inputs.file(nativeBomFile)
        inputs.files(licenseMapFile, licensePolicyFile)
        inputs.files(packagedNativeLibs)
        // The classification and the license-text check read the installed
        // vcpkg tree (SPDX file lists, archives, `copyright` texts), so a change
        // there must invalidate this task.
        vcpkgTriplets.forEach { inputs.dir(File(vcpkgRootDir, "installed/$it/share")) }
        outputs.file(finalBom)
        dependsOn(jvmTask, mergeNativeSbom, downloadSbomCli, stripTask)
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
            // Shipped Android OpenMP runtime: provided by the NDK toolchain, not
            // by vcpkg, so it never appeared in the native section before.
            val packagedLibs = packagedNativeLibs.files
                // Leftovers from earlier builds can leave other ABIs in the
                // intermediates directory; classify only what this build ships.
                .filter { injectedAbis.isEmpty() || it.parentFile.name in injectedAbis }
                .sortedBy { it.name }
            if (packagedLibs.isEmpty()) {
                throw GradleException(
                    "No packaged native libraries found for $variant under $strippedRoot. " +
                        "The SBOM records what the application ships, so the native build must " +
                        "have produced its libraries first."
                )
            }
            val libomp = packagedLibs.firstOrNull { it.name == "libomp.so" }
                ?: throw GradleException(
                    "libomp.so is not packaged for $variant (found: " +
                        "${packagedLibs.joinToString(", ") { it.name }}). The shipped native " +
                        "set changed — update licenses/native-license-map.json to match."
                )
            val libompVersion = Regex("clang/(\\d+)/").find(libomp.absolutePath)
                ?.groupValues?.get(1) ?: android.ndkVersion
            bom.components = (bom.components ?: emptyList()) + Component().apply {
                type = Component.Type.LIBRARY
                group = "llvm"
                name = "libomp"
                version = libompVersion
                purl = "pkg:generic/libomp@$libompVersion"
                description =
                    "LLVM OpenMP runtime shipped with the Android NDK toolchain (NDK ${android.ndkVersion})"
            }
            // Top-level component: NaviVeylin at the build version.
            val metadataComponent = bom.metadata?.component
                ?: Component().also { bom.metadata?.component = it }
            metadataComponent.apply {
                type = Component.Type.APPLICATION
                name = "NaviVeylin"
                version = releaseVersion?.first ?: FALLBACK_VERSION_NAME
            }
            // License data: resolve every component, record distribution scope
            // and the evidence behind it, then validate the result.
            val nativeMap = loadNativeLicenseMap()
            val scopes = classifyNativeScopes(
                nativeMap = nativeMap,
                probes = loadNativeLicenseProbes(),
                packagedLibs = packagedLibs,
                linked = linkedArchives(),
                triplets = vcpkgTriplets
            )
            val inventoryProblems = buildList {
                addAll(missingLicenseTextSources(nativeMap, scopes))
                val componentNames = bom.components.orEmpty().map { it.name }.toSet()
                addAll(
                    unrecordedPackagedLibraries(packagedLibs, nativeMap, componentNames).map {
                        "$it: packaged in the application but recorded by no SBOM component " +
                            "— add it to licenses/native-license-map.json or to the component set"
                    }
                )
            }
            if (inventoryProblems.isNotEmpty()) {
                throw GradleException(
                    "Native license inventory is incomplete for ${variant}:\n  " +
                        inventoryProblems.joinToString("\n  ")
                )
            }
            applyLicenseData(
                bom = bom,
                nativeMap = nativeMap,
                policy = loadLicensePolicy(),
                scopes = scopes
            )
            writeBom(finalFile, bom)
            merged.delete()
            runSbomCli(cli, "validate", "--input-file", finalFile.absolutePath)
        }
    }

    // License policy gate for this variant. A check, so it has no outputs and
    // no `assemble` task depends on it: a policy failure must not block local
    // iteration on an unrelated change, but it must fail the gate itself.
    tasks.register("checkLicensePolicy$variantLabel") {
        group = "license"
        description = "Checks the $variant SBOM against licenses/license-policy.json."
        val bomFile = sbomOutputRoot.map { it.dir(variant).file("bom.json") }
        inputs.file(bomFile)
        inputs.file(licensePolicyFile)
        dependsOn(finalTask)
        doLast {
            val result = LicenseGate(loadLicensePolicy())
                .evaluate(sbomLicenseClaims(bomFile.get().asFile))
            result.warnings.distinct().forEach { logger.warn("license policy ($variant): $it") }
            if (!result.passed) {
                throw GradleException(
                    "License policy violations for $variant:\n  " +
                        result.violations.joinToString("\n  ")
                )
            }
            logger.lifecycle(
                "License policy ok for $variant " +
                    "(${result.warnings.size} warning(s), see licenses/license-policy.json)"
            )
        }
    }
}

// Aggregate gate for the CI variant; the release target gates both shipped
// flavors (their SBOMs already exist by then).
tasks.register("checkLicensePolicy") {
    group = "license"
    description =
        "Runs the license policy gate over the mobileDebug SBOM (the SBOM CI publishes)."
    dependsOn("checkLicensePolicy${sbomVariants.first().replaceFirstChar(Char::uppercaseChar)}")
}

// ── Generated license data ───────────────────────────────────────────────────
// The license screen shows the same inventory the gate validated, so the data
// is generated from the SBOM rather than from a second hand-kept list. Written
// into a per-variant generated assets root that the variant's source set
// exposes, so one variant's data can never leak into another's APK.

/** Android NDK license bundle, the source for toolchain-shipped components. */
fun ndkNoticeFile(): File? {
    val candidates = buildList {
        System.getenv("ANDROID_NDK_HOME")?.let { add(File(it)) }
        System.getenv("ANDROID_NDK_ROOT")?.let { add(File(it)) }
        val localProperties = rootProject.file("local.properties")
        val sdkDir = if (localProperties.isFile) {
            localProperties.readLines()
                .firstOrNull { it.startsWith("sdk.dir=") }
                ?.substringAfter('=')
                ?.trim()
                ?.replace("\\", "/")
        } else {
            null
        }
        sdkDir?.let { add(File("$it/ndk/${android.ndkVersion}")) }
        System.getenv("ANDROID_SDK_ROOT")?.let { add(File("$it/ndk/${android.ndkVersion}")) }
    }
    return candidates.map { File(it, "NOTICE.toolchain") }.firstOrNull { it.isFile }
        ?: candidates.map { File(it, "NOTICE") }.firstOrNull { it.isFile }
}




// ── License assets + NOTICE per variant ─────────────────────────────────────
// One task per variant writes that variant's inventory, its license texts and
// the NOTICE file. The Variant API registers the generated root as that
// variant's own assets source, so an APK carries `licenses/…` for its own
// dependency set and cannot pick up another variant's data. (AGP rejects
// Provider-based `srcDir` calls on the SourceSet API, and a single shared
// generated root leaked mobile data into the automotive build.)
androidComponents {
    onVariants { variant ->
        if (variant.name !in sbomVariants) return@onVariants
        val variantLabel = variant.name.replaceFirstChar(Char::uppercaseChar)
        val licenseTask = tasks.register(
            "generateLicenseAssets$variantLabel",
            GenerateLicenseAssets::class.java
        ) {
            group = "license"
            description = "Writes the ${variant.name} license inventory, texts and NOTICE."
            sbomFile.set(sbomOutputRoot.map { it.dir(variant.name).file("bom.json") })
            policyFile.set(licensePolicyFile)
            vendoredTextsDir.set(rootProject.layout.projectDirectory.dir("licenses/texts"))
            ndkNoticeFile()?.let { ndkNotice.set(it) }
            vcpkgRoot.set(vcpkgRootDir)
            triplets.set(vcpkgTriplets)
            outputDir.set(layout.buildDirectory.dir("generated/assets-licenses/${variant.name}"))
            noticeFile.set(sbomOutputRoot.map { it.dir(variant.name).file("NOTICE") })
            dependsOn("generateSbom$variantLabel")
        }
        variant.sources.assets?.addGeneratedSourceDirectory(
            licenseTask,
            GenerateLicenseAssets::outputDir
        )
    }
}

// The release target ships both AABs with their SBOMs and gates both flavors;
// each AAB carries its own license data through its assets source.
tasks.named("release") {
    dependsOn("generateSbomMobileRelease", "generateSbomAutomotiveRelease")
    dependsOn("checkLicensePolicyMobileRelease", "checkLicensePolicyAutomotiveRelease")
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
