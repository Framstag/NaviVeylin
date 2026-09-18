package com.naviveylin.build.licensing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseGateTest {

    private val policy = LicensePolicy(
        permittedShipped = setOf("MIT", "Apache-2.0", "MPL-1.1", "Zlib", "LGPL-2.1-or-later", "LicenseRef-AndroidSDK"),
        permittedBuildTimeOnly = setOf("MIT", "Apache-2.0", "GPL-3.0-only", "LGPL-2.1-or-later"),
        elections = mapOf("cairo" to Election("LGPL-2.1-only OR MPL-1.1", "MPL-1.1")),
        licenseRefs = mapOf(
            "LicenseRef-AndroidSDK" to LicenseRefDeclaration(
                id = "LicenseRef-AndroidSDK",
                name = "Android Software Development Kit License",
                source = "https://developer.android.com/studio/terms.html",
                textDistributed = false
            )
        ),
        reviewRequired = listOf(ReviewRequiredEntry("LGPL-2.1-or-later"))
    )

    private val gate = LicenseGate(policy)

    @Test
    fun `permitted license passes`() {
        val result = gate.evaluate(listOf(shipped("zlib", "Zlib")))
        assertTrue(result.violations.toString(), result.passed)
    }

    @Test
    fun `non permitted license fails naming component and license`() {
        val result = gate.evaluate(listOf(shipped("vendor:lib", "GPL-3.0-only")))
        assertFalse(result.passed)
        assertEquals(
            listOf("vendor:lib: license 'GPL-3.0-only' is not permitted for scope 'shipped'"),
            result.violations
        )
    }

    @Test
    fun `missing license value fails`() {
        val result = gate.evaluate(listOf(shipped("vendor:lib", null)))
        assertFalse(result.passed)
        assertEquals(listOf("vendor:lib: no license value declared"), result.violations)
    }

    @Test
    fun `unresolved non spdx license fails`() {
        val result = gate.evaluate(
            listOf(ComponentLicense("vendor:lib", "Some Proprietary License", declaredIsSpdx = false, scope = Scope.SHIPPED))
        )
        assertFalse(result.passed)
        assertTrue(result.violations.single().contains("no LicenseRef declaration"))
    }

    @Test
    fun `choice without election fails listing the alternatives`() {
        val result = gate.evaluate(listOf(shipped("cairo-lookalike", "MIT OR Apache-2.0")))
        assertFalse(result.passed)
        assertTrue(result.violations.single().contains("offers alternatives (MIT, Apache-2.0)"))
        assertTrue(result.violations.single().contains("no election is recorded"))
    }

    @Test
    fun `choice satisfied by a permitted alternative passes`() {
        val result = gate.evaluate(listOf(shipped("cairo", "LGPL-2.1-only OR MPL-1.1")))
        assertTrue(result.violations.toString(), result.passed)
    }

    @Test
    fun `declared license ref passes`() {
        val result = gate.evaluate(
            listOf(
                ComponentLicense(
                    "com.google.android.gms:play-services-location",
                    "Android Software Development Kit License",
                    declaredIsSpdx = false,
                    scope = Scope.SHIPPED
                )
            )
        )
        assertTrue(result.violations.toString(), result.passed)
    }

    @Test
    fun `license ref that is not declared fails`() {
        val undeclared = LicensePolicy(
            permittedShipped = setOf("LicenseRef-Unknown"),
            permittedBuildTimeOnly = setOf("MIT")
        )
        val result = LicenseGate(undeclared).evaluate(
            listOf(shipped("vendor:lib", "LicenseRef-Unknown"))
        )
        assertFalse(result.passed)
        assertTrue(result.violations.single().contains("is not declared"))
    }

    @Test
    fun `build time license is not permitted for a shipped component`() {
        val result = gate.evaluate(
            listOf(ComponentLicense("gettext", "GPL-3.0-only", declaredIsSpdx = true, scope = Scope.SHIPPED))
        )
        assertFalse(result.passed)
        assertTrue(result.violations.single().contains("scope 'shipped'"))
    }

    @Test
    fun `shipped license is not permitted for a build time component`() {
        val result = gate.evaluate(
            listOf(ComponentLicense("zlib", "Zlib", declaredIsSpdx = true, scope = Scope.BUILD_TIME_ONLY))
        )
        assertFalse(result.passed)
        assertTrue(result.violations.single().contains("scope 'buildTimeOnly'"))
    }

    @Test
    fun `every identifier of a conjunction must be permitted`() {
        val result = gate.evaluate(listOf(shipped("pixman", "MIT AND GPL-3.0-only")))
        assertFalse(result.passed)
        assertEquals(
            listOf("pixman: license 'GPL-3.0-only' is not permitted for scope 'shipped'"),
            result.violations
        )
    }

    @Test
    fun `conjunction with all identifiers permitted passes`() {
        val result = gate.evaluate(listOf(shipped("pixman", "MIT AND Apache-2.0")))
        assertTrue(result.violations.toString(), result.passed)
    }

    @Test
    fun `caveat is reported as a warning not a violation`() {
        val result = gate.evaluate(
            listOf(
                ComponentLicense(
                    "libosmscout",
                    "LGPL-2.1-or-later",
                    declaredIsSpdx = true,
                    scope = Scope.SHIPPED,
                    caveat = "upstream states LGPL without a version; LGPL-2.1-or-later is the conservative mapping"
                )
            )
        )
        assertTrue(result.violations.toString(), result.passed)
        assertTrue(result.warnings.any { it.contains("conservative mapping") })
    }

    @Test
    fun `application license not permitted for shipped scope fails the gate`() {
        // This fixture's permittedShipped does not list GPL-3.0-or-later: the
        // pre-decision state, in which the application license would fail.
        val result = gate.evaluate(listOf(shipped("NaviVeylin", "GPL-3.0-or-later")))
        assertFalse(result.passed)
        assertTrue(
            result.violations.any {
                it.contains("NaviVeylin") && it.contains("not permitted for scope 'shipped'")
            }
        )
    }

    @Test
    fun `application license passes for shipped components once permitted`() {
        val policyWithGpl = LicensePolicy(
            permittedShipped = setOf("GPL-3.0-or-later", "MIT", "Apache-2.0"),
            permittedBuildTimeOnly = setOf("MIT", "Apache-2.0"),
            reviewRequired = listOf(ReviewRequiredEntry("LGPL-2.1-or-later"))
        )
        val result = LicenseGate(policyWithGpl)
            .evaluate(listOf(shipped("NaviVeylin", "GPL-3.0-or-later"), shipped("core", "GPL-3.0-or-later")))
        assertTrue(result.violations.toString(), result.passed)
    }

    @Test
    fun `review required license is reported as a warning`() {
        val result = gate.evaluate(listOf(shipped("libosmscout", "LGPL-2.1-or-later")))
        assertTrue(result.passed)
        assertTrue(result.warnings.any { it.contains("needs the application license decision") })
    }

    @Test
    fun `scoped review required entry warns only the named component`() {
        val scoped = LicensePolicy(
            permittedShipped = setOf("LGPL-2.1-or-later"),
            permittedBuildTimeOnly = setOf("LGPL-2.1-or-later"),
            reviewRequired = listOf(ReviewRequiredEntry("LGPL-2.1-or-later", setOf("libosmscout")))
        )
        val result = LicenseGate(scoped).evaluate(
            listOf(
                shipped("Framstag:libosmscout", "LGPL-2.1-or-later"),
                shipped("fribidi", "LGPL-2.1-or-later")
            )
        )
        assertTrue(result.violations.toString(), result.passed)
        val decisionWarnings =
            result.warnings.filter { it.contains("needs the application license decision") }
        assertEquals(1, decisionWarnings.size)
        assertTrue(decisionWarnings.single().contains("Framstag:libosmscout"))
    }

    @Test
    fun `review required entry scoping matches the plain component name`() {
        // The policy scopes by the plain map name ("libosmscout") while the gate
        // key is qualified ("Framstag:libosmscout"): both forms must be honoured.
        val scoped = LicensePolicy(
            permittedShipped = setOf("LGPL-2.1-or-later"),
            permittedBuildTimeOnly = setOf("LGPL-2.1-or-later"),
            reviewRequired = listOf(ReviewRequiredEntry("LGPL-2.1-or-later", setOf("libosmscout")))
        )
        val result = LicenseGate(scoped).evaluate(
            listOf(shipped("Framstag:libosmscout", "LGPL-2.1-or-later"))
        )
        assertEquals(1, result.warnings.size)
        assertTrue(result.warnings.single().contains("Framstag:libosmscout"))
    }

    @Test
    fun `unscoped review required entry warns every carrier`() {
        val unscoped = LicensePolicy(
            permittedShipped = setOf("LGPL-2.1-or-later"),
            permittedBuildTimeOnly = setOf("LGPL-2.1-or-later"),
            reviewRequired = listOf(ReviewRequiredEntry("LGPL-2.1-or-later"))
        )
        val result = LicenseGate(unscoped).evaluate(
            listOf(
                shipped("libosmscout", "LGPL-2.1-or-later"),
                shipped("fribidi", "LGPL-2.1-or-later")
            )
        )
        assertEquals(
            2,
            result.warnings.filter { it.contains("needs the application license decision") }.size
        )
    }

    @Test
    fun `empty review required list yields no decision warnings`() {
        val noReview = LicensePolicy(
            permittedShipped = setOf("LGPL-2.1-or-later"),
            permittedBuildTimeOnly = setOf("LGPL-2.1-or-later"),
            reviewRequired = emptyList()
        )
        val result = LicenseGate(noReview).evaluate(
            listOf(
                shipped("libosmscout", "LGPL-2.1-or-later"),
                shipped("fribidi", "LGPL-2.1-or-later")
            )
        )
        assertTrue(result.passed)
        assertFalse(result.warnings.any { it.contains("needs the application license decision") })
    }

    @Test
    fun `multiple violations are all reported`() {
        val result = gate.evaluate(
            listOf(
                shipped("a", null),
                shipped("b", "GPL-3.0-only"),
                shipped("c", "MIT OR Apache-2.0")
            )
        )
        assertEquals(3, result.violations.size)
    }

    private fun shipped(component: String, declared: String?) =
        ComponentLicense(component, declared, declaredIsSpdx = true, scope = Scope.SHIPPED)
}
