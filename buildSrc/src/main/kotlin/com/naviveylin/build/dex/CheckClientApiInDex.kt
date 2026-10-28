package com.naviveylin.build.dex

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * `:app:checkClientApiInDex<Variant>` — the packaged dex defines every class the JNI bridge's Java side compiles.
 *
 * The scanned inputs are the variant's merged dex directories (`mergeProjectDex*`, `mergeLibDex*`, `mergeExtDex*`),
 * which is what the APK's `classes*.dex` entries are built from — no APK is unpacked, so the gate costs a few
 * hundred milliseconds beside the packaging step it guards. The required descriptors come from the two source
 * roots, not from a compiled artifact: a set read from the jar or the classes directory the app already receives
 * would shrink with the defect (see [ClientApiClasses]).
 *
 * The task is typed and carries proper inputs — the project's script-level gates cost configuration-cache
 * problems, which `guidelines/Build.md` §7 records — and it has no outputs, so it runs whenever it is in the graph.
 */
abstract class CheckClientApiInDex : DefaultTask() {

    /** The variant's merged dex directories; each one holds `*.dex` archives. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dexArchives: ConfigurableFileCollection

    /** The submodule's Java client source root (`.../libosmscout-client-java/java`); absent in a fresh clone. */
    @get:Optional
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val submoduleSourceRoot: DirectoryProperty

    /** The module's own override source root (`:osmscout-client-java`'s `src/main/java`). */
    @get:Optional
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val overrideSourceRoot: DirectoryProperty

    init {
        group = "verification"
        description = "Fails when the packaged dex defines no class for one of the JNI bridge's Java classes"
    }

    @TaskAction
    fun check() {
        val roots = listOfNotNull(submoduleSourceRoot.orNull, overrideSourceRoot.orNull).map { it.asFile }
        val required = roots.flatMap { ClientApiClasses.descriptorsIn(it) }.toSet()
        if (required.isEmpty()) {
            throw GradleException(
                "No Java client sources were found under ${roots.joinToString(", ") { it.path }}. " +
                    "Initialize the submodule first: git submodule update --init --recursive"
            )
        }

        val dexFiles = dexArchives.asFileTree.files.filter { it.isFile && it.name.endsWith(".dex") }
        if (dexFiles.isEmpty()) {
            throw GradleException(
                "No dex archives to scan under ${dexArchives.files.joinToString(", ") { it.path }} — " +
                    "the gate reads the variant's merged dex directories and they hold no `*.dex` file."
            )
        }

        val missing = ClientApiDexGate.missing(required, DexClassDescriptors.definedIn(dexFiles))
        if (missing.isNotEmpty()) {
            val root = project.rootDir.absoluteFile
            val paths = dexFiles.map { it.absoluteFile.relativeTo(root).invariantSeparatorsPath }.sorted()
            throw GradleException(ClientApiDexGate.report(missing, paths))
        }
    }
}
