package com.naviveylin.build.testing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The JVM-only test toggle's rule (change `fix-jvm-test-run-needs-no-native-tree`, spec `build-sbom` — "A
 * JVM-only test run does not require the native tree"): the property is read like every other build switch,
 * a test-only invocation is never refused, and an invocation that packages is refused by name — an artifact
 * built with the property would ship without its `licenses/` assets (`guidelines/Build.md` §9).
 */
class JvmOnlyTestRunTest {

    private fun refusal(value: String): String? = try {
        JvmOnlyTestRun.isRequested(value)
        null
    } catch (e: IllegalArgumentException) {
        e.message
    }

    @Test
    fun absentPropertyIsNotAJvmOnlyRun() {
        assertFalse(JvmOnlyTestRun.isRequested(null))
        assertFalse(JvmOnlyTestRun.isRequested("false"))
    }

    @Test
    fun thePropertyRequestsIt() {
        assertTrue(JvmOnlyTestRun.isRequested(""))
        assertTrue(JvmOnlyTestRun.isRequested("true"))
    }

    @Test
    fun anUnreadableValueIsRefusedWithActionableText() {
        val message = refusal("yes")
        requireNotNull(message) { "a value the toggle cannot read must not be accepted silently" }
        assertTrue(message, message.contains("-PjvmTestsOnly=yes"))
        assertTrue(message, message.contains("SBOM and licence chain"))
    }

    @Test
    fun aTestOnlyInvocationIsNeverRefused() {
        assertNull(JvmOnlyTestRun.refusalFor(listOf("test")))
        assertNull(JvmOnlyTestRun.refusalFor(listOf(":app:testMobileDebugUnitTest", "--tests", "com.x.*")))
        assertNull(JvmOnlyTestRun.refusalFor(emptyList()))
    }

    @Test
    fun everyPackagingEntryPointIsRefused() {
        listOf(
            "assembleMobileDebug",
            ":app:bundleMobileRelease",
            "release",
            "build",
            ":app:generateSbomMobileDebug",
            ":app:mergeNativeSbom",
            ":app:generateLicenseAssetsMobileDebug",
            "checkLicensePolicy",
            ":app:installMobileDebug",
        ).forEach { task ->
            assertFalse("$task must be refused", JvmOnlyTestRun.refusalFor(listOf(task)).isNullOrEmpty())
        }
    }

    @Test
    fun theRefusalNamesTheTaskAndTheProperty() {
        val message = requireNotNull(JvmOnlyTestRun.refusalFor(listOf("test", ":app:assembleMobileDebug")))
        assertTrue(message, message.contains("-PjvmTestsOnly was requested"))
        assertTrue(message, message.contains(":app:assembleMobileDebug"))
        assertTrue(message, message.contains("guidelines/Build.md §9"))
    }

    @Test
    fun packagingTasksInKeepsTheRequestedNames() {
        assertEquals(
            listOf(":app:assembleMobileDebug", "bundleMobileRelease"),
            JvmOnlyTestRun.packagingTasksIn(
                listOf("test", ":app:assembleMobileDebug", "bundleMobileRelease"),
            ),
        )
    }
}
