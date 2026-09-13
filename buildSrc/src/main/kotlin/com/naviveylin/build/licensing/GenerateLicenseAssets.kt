package com.naviveylin.build.licensing

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Generates one variant's license data from its SBOM: the inventory and license
 * texts under [outputDir] (packaged into the APK as assets) and the NOTICE file
 * next to the SBOM.
 *
 * The variant's assets source wires [outputDir] through
 * `variant.sources.assets.addGeneratedSourceDirectory`, so the APK carries
 * `licenses/dependencies.json` and `licenses/texts/`.
 *
 * Reading the artifact (the SBOM) rather than the build's in-memory state means
 * the screen shows exactly what the gate validated.
 */
abstract class GenerateLicenseAssets : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sbomFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val policyFile: RegularFileProperty

    /** Canonical texts under `licenses/texts/`. */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val vendoredTextsDir: DirectoryProperty

    /** The Android NDK license bundle, present when a `androidNdk` source exists. */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val ndkNotice: RegularFileProperty

    @get:Input
    abstract val vcpkgRoot: Property<String>

    @get:Input
    abstract val triplets: ListProperty<String>

    /** Generated assets root for this variant. */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    /** NOTICE emitted next to the SBOM. */
    @get:OutputFile
    abstract val noticeFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val policy = LicenseData.policy(policyFile.get().asFile)
        val textSources = LicenseData.textSources(policyFile.get().asFile)
        val textsDir = vendoredTextsDir.get().asFile
        val vcpkg = File(vcpkgRoot.get())
        val ndk = ndkNotice.orNull?.asFile

        val problems = LicenseData.writeAssets(
            bomFile = sbomFile.get().asFile,
            outputDir = File(outputDir.get().asFile, "licenses"),
            policy = policy,
            textSources = textSources,
            vendoredTexts = textsDir,
            vcpkgRoot = vcpkg,
            triplets = triplets.get(),
            ndkNotice = ndk,
        )
        if (problems.isNotEmpty()) {
            throw GradleException(
                "License data is incomplete:\n  " + problems.distinct().joinToString("\n  ")
            )
        }

        val notice = LicenseData.notice(
            bomFile = sbomFile.get().asFile,
            policy = policy,
            textSources = textSources,
            vendoredTexts = textsDir,
            vcpkgRoot = vcpkg,
            triplets = triplets.get(),
            ndkNotice = ndk,
        )
        val noticeOut = noticeFile.get().asFile
        noticeOut.parentFile.mkdirs()
        noticeOut.writeText(notice)
        logger.lifecycle(
            "License data written for ${sbomFile.get().asFile.parentFile.name}: " +
                "${policy.permittedShipped.size} permitted shipped licenses, NOTICE ${noticeOut.name}"
        )
    }
}
