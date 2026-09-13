package com.naviveylin.build.licensing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseExpressionsTest {

    @Test
    fun `splits top level conjunctions`() {
        assertEquals(
            listOf("MIT", "BSD-2-Clause"),
            LicenseExpressions.conjuncts("MIT AND BSD-2-Clause")
        )
    }

    @Test
    fun `splits top level alternatives`() {
        assertEquals(
            listOf("LGPL-2.1-only", "MPL-1.1"),
            LicenseExpressions.disjuncts("LGPL-2.1-only OR MPL-1.1")
        )
    }

    @Test
    fun `alternatives nested inside a conjunction are not top level`() {
        val expression = "LGPL-2.1-or-later AND (LGPL-2.1-only OR MPL-1.1)"
        assertEquals(
            listOf("LGPL-2.1-or-later", "LGPL-2.1-only OR MPL-1.1"),
            LicenseExpressions.conjuncts(expression)
        )
        assertEquals(listOf(expression), LicenseExpressions.disjuncts(expression))
    }

    @Test
    fun `with operator stays part of the identifier`() {
        val expression = "Apache-2.0 WITH LLVM-exception"
        assertEquals(listOf(expression), LicenseExpressions.conjuncts(expression))
        assertEquals(listOf(expression), LicenseExpressions.disjuncts(expression))
    }

    @Test
    fun `redundant parentheses are stripped`() {
        assertEquals("MIT", LicenseExpressions.stripParentheses("((MIT))"))
        assertEquals("MIT AND BSD-2-Clause", LicenseExpressions.stripParentheses("(MIT AND BSD-2-Clause)"))
    }

    @Test
    fun `parentheses that do not enclose the whole expression are kept`() {
        assertEquals(
            "(MIT) AND BSD-2-Clause",
            LicenseExpressions.stripParentheses("(MIT) AND BSD-2-Clause")
        )
    }
}

class LicenseResolverTest {

    private val policy = LicensePolicy(
        permittedShipped = setOf("MIT", "MPL-1.1", "Apache-2.0", "LGPL-2.1-or-later"),
        permittedBuildTimeOnly = setOf("MIT", "GPL-3.0-only", "LGPL-2.1-or-later", "LGPL-2.1-only"),
        elections = mapOf(
            "cairo" to Election("LGPL-2.1-only OR MPL-1.1", "MPL-1.1"),
            "glib" to Election(
                "LGPL-2.1-or-later AND (LGPL-2.1-only OR MPL-1.1)",
                "LGPL-2.1-or-later AND LGPL-2.1-only"
            )
        ),
        licenseRefs = mapOf(
            "LicenseRef-AndroidSDK" to LicenseRefDeclaration(
                id = "LicenseRef-AndroidSDK",
                name = "Android Software Development Kit License",
                source = "https://developer.android.com/studio/terms.html",
                textDistributed = false
            ),
            "LicenseRef-NaviVeylin" to LicenseRefDeclaration(
                id = "LicenseRef-NaviVeylin",
                name = "NaviVeylin project license",
                source = "LICENSE",
                textDistributed = false
            )
        )
    )

    private val resolver = LicenseResolver(policy)

    @Test
    fun `spdx identifier resolves to itself`() {
        val resolution = resolver.resolve(spdx("zlib", "Zlib"))
        assertEquals(
            LicenseResolution.Resolved("Zlib", listOf("Zlib")),
            resolution
        )
    }

    @Test
    fun `alternative without an election needs one`() {
        val resolution = resolver.resolve(spdx("some-lib", "MIT OR Apache-2.0"))
        assertEquals(
            LicenseResolution.NeedsElection("MIT OR Apache-2.0", listOf("MIT", "Apache-2.0")),
            resolution
        )
    }

    @Test
    fun `recorded election is applied`() {
        val resolution = resolver.resolve(spdx("cairo", "LGPL-2.1-only OR MPL-1.1", Scope.SHIPPED))
        assertEquals(LicenseResolution.Resolved("MPL-1.1", listOf("MPL-1.1")), resolution)
    }

    @Test
    fun `election covering one branch of a nested alternative is applied`() {
        val resolution = resolver.resolve(
            spdx("glib", "LGPL-2.1-or-later AND (LGPL-2.1-only OR MPL-1.1)", Scope.BUILD_TIME_ONLY)
        )
        assertEquals(
            LicenseResolution.Resolved(
                "LGPL-2.1-or-later AND LGPL-2.1-only",
                listOf("LGPL-2.1-or-later", "LGPL-2.1-only")
            ),
            resolution
        )
    }

    @Test
    fun `election that the declaration does not offer is rejected`() {
        val wrong = LicensePolicy(
            permittedShipped = setOf("MIT"),
            permittedBuildTimeOnly = setOf("MIT"),
            elections = mapOf("cairo" to Election("GPL-3.0-only", "MIT"))
        )
        val resolution = LicenseResolver(wrong).resolve(spdx("cairo", "GPL-3.0-only"))
        assertTrue(resolution is LicenseResolution.Unresolved)
    }

    @Test
    fun `declared license ref identifier resolves when declared in the policy`() {
        val resolution = resolver.resolve(spdx("core", "LicenseRef-NaviVeylin", Scope.SHIPPED))
        assertEquals(
            LicenseResolution.Resolved("LicenseRef-NaviVeylin", listOf("LicenseRef-NaviVeylin")),
            resolution
        )
    }

    @Test
    fun `declared license ref identifier that the policy does not define is unresolved`() {
        val resolution = resolver.resolve(spdx("core", "LicenseRef-Unknown", Scope.SHIPPED))
        assertEquals(
            LicenseResolution.Unresolved(
                "LicenseRef identifier 'LicenseRef-Unknown' is not declared in licenses/license-policy.json"
            ),
            resolution
        )
    }

    @Test
    fun `non spdx name resolves through a declared license ref`() {
        val resolution = resolver.resolve(
            nameOnly("com.google.android.gms:play-services-location", "Android Software Development Kit License")
        )
        assertEquals(LicenseResolution.Resolved("LicenseRef-AndroidSDK", listOf("LicenseRef-AndroidSDK")), resolution)
    }

    @Test
    fun `license ref name matching ignores case and surrounding whitespace`() {
        val resolution = resolver.resolve(
            nameOnly("com.google.android.gms:play-services-base", "  android software development KIT license ")
        )
        assertEquals(LicenseResolution.Resolved("LicenseRef-AndroidSDK", listOf("LicenseRef-AndroidSDK")), resolution)
    }

    @Test
    fun `non spdx name without a declaration is unresolved`() {
        val resolution = resolver.resolve(nameOnly("vendor:lib", "Some Proprietary License"))
        assertEquals(
            LicenseResolution.Unresolved(
                "license 'Some Proprietary License' has no SPDX identifier and no LicenseRef declaration"
            ),
            resolution
        )
    }

    @Test
    fun `missing license value is unresolved`() {
        val resolution = resolver.resolve(spdx("vendor:lib", null))
        assertEquals(LicenseResolution.Unresolved("no license value declared"), resolution)
    }

    private fun spdx(
        component: String,
        declared: String?,
        scope: Scope = Scope.SHIPPED
    ) = ComponentLicense(component, declared, declaredIsSpdx = true, scope = scope)

    private fun nameOnly(component: String, declared: String?) =
        ComponentLicense(component, declared, declaredIsSpdx = false, scope = Scope.SHIPPED)
}
