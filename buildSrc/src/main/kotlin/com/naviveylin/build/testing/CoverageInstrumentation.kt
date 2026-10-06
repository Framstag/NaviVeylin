package com.naviveylin.build.testing

/**
 * Coverage instrumentation switch (change `speed-up-build-test-gate`, spec `test-coverage` —
 * "Instrumentation is attached only when coverage is requested").
 *
 * The Kover agent is attached to every JVM unit-test run by default, so the aggregated and
 * per-module reports keep working without any invocation having to remember a flag. `-PnoCoverage`
 * detaches it for an iteration run that does not want to pay for instrumenting the whole suite;
 * the run may then be quoted as test evidence but not as coverage evidence
 * (`guidelines/Build.md` §7).
 */
object CoverageInstrumentation {

    const val PROPERTY = "noCoverage"

    /** True when the invocation asks for the suite without the coverage instrumentation agent. */
    fun isDisabled(propertyValue: String?): Boolean =
        BuildFlags.isSet(PROPERTY, propertyValue, "to run the tests without the coverage agent")
}
