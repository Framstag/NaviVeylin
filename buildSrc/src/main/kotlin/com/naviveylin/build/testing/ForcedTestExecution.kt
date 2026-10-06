package com.naviveylin.build.testing

/**
 * Forced test execution (change `speed-up-build-test-gate`, spec `build-test-gate` —
 * "A gate run proves that its tests executed").
 *
 * `-PforceTests` makes the JVM unit-test tasks not up to date, so a gate run is evidence of what
 * executed without forcing every compile, package and native task the way `--rerun-tasks` does.
 * The invocation also needs `--no-build-cache`: a task that is not up to date may still have its
 * outputs restored from the build cache, which is how a "forced" run has come back with the
 * previous run's result XML (`guidelines/Build.md` §4).
 */
object ForcedTestExecution {

    const val PROPERTY = "forceTests"

    /** `-PforceTests` (empty), `-PforceTests=true`; a `false` or absent value means "do not force". */
    fun isRequested(propertyValue: String?): Boolean =
        BuildFlags.isSet(PROPERTY, propertyValue, "to force test execution")
}
