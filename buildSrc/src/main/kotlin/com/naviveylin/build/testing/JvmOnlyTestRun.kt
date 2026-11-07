package com.naviveylin.build.testing

/**
 * The JVM-only test toggle (change `fix-jvm-test-run-needs-no-native-tree`, spec `build-sbom` — "A
 * JVM-only test run does not require the native tree").
 *
 * The license inventory is a variant asset source, so the unit-test graph carries
 * `generateLicenseAssets` → `generateSbom` → `mergeNativeSbom`, and a tree without a vcpkg checkout
 * could not run a single test: `mergeNativeSbom` read vcpkg's installed-status file while it was
 * being *created*, i.e. at configuration time of every invocation whose graph includes it.
 * `-PjvmTestsOnly` takes that chain out of a test-only invocation.
 *
 * The toggle is refused for an invocation that packages. An APK or AAB built with it would carry no
 * `licenses/` assets, and the artefact guarantee (`guidelines/Build.md` §9: the inventory is a
 * variant asset source, so each APK ships its own) is strict. That refusal is a pure decision on the
 * requested task names, so it is unit-tested here instead of being measured by running a build.
 */
object JvmOnlyTestRun {

    const val PROPERTY = "jvmTestsOnly"

    /** `-PjvmTestsOnly` (empty), `-PjvmTestsOnly=true`; `false` or absent means "not a JVM-only run". */
    fun isRequested(propertyValue: String?): Boolean = BuildFlags.isSet(
        PROPERTY,
        propertyValue,
        "to run the unit tests without the SBOM and licence chain",
    )

    /**
     * Task-name prefixes whose graph produces an APK/AAB or the inventory that ships inside it. A
     * requested task is matched by its own name, without the module path, so `:app:assembleMobileDebug`
     * is caught like `assembleMobileDebug`.
     */
    private val PACKAGING_TASK_PREFIXES = listOf(
        "assemble",
        "bundle",
        "package",
        "install",
        "release",
        "build",
        "generateSbom",
        "mergeNativeSbom",
        "generateLicenseAssets",
        "checkLicensePolicy",
    )

    /** The requested tasks that packaging owns; empty for an invocation that only runs tests. */
    fun packagingTasksIn(requestedTasks: List<String>): List<String> = requestedTasks.filter { task ->
        val name = task.substringAfterLast(':')
        PACKAGING_TASK_PREFIXES.any { prefix -> name.startsWith(prefix) }
    }

    /** The refusal the build states when the toggle is combined with packaging, or null when it is not. */
    fun refusalFor(requestedTasks: List<String>): String? {
        val packaging = packagingTasksIn(requestedTasks)
        if (packaging.isEmpty()) return null
        return "-P$PROPERTY was requested together with a packaging task (" +
            packaging.joinToString(", ") +
            "). The licence inventory is a variant asset source, so an artifact built in this " +
            "invocation would ship without its licenses/ assets — the guarantee guidelines/Build.md " +
            "§9 makes strict. Run the test suite and the packaging as two invocations, or drop " +
            "-P$PROPERTY."
    }
}
