package com.naviveylin.build.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the cleartext repository transport gate (change `allow-lan-http-map-repository`, spec
 * `map-download-infrastructure` — "Shipped builds permit cleartext repository transport").
 *
 * The fixtures are the real shapes of the two files, plus the four ways the permission can silently
 * stop being effective: the config moving out of the shared source set, the manifest reference
 * disappearing, the permission moving to a `<domain-config>`, and the value flipping to `false`
 * (the mutation the change's revert-check performs).
 */
class CleartextTransportPolicyScannerTest {

    private val manifest = """
        <manifest xmlns:android="http://schemas.android.com/apk/res/android">
            <application
                android:name=".NaviVeylinApp"
                android:networkSecurityConfig="@xml/network_security_config"
                android:theme="@style/Theme.NaviVeylin">
            </application>
        </manifest>
    """.trimIndent()

    private val config = """
        <network-security-config>
            <base-config cleartextTrafficPermitted="true">
                <trust-anchors>
                    <certificates src="system" />
                </trust-anchors>
            </base-config>
        </network-security-config>
    """.trimIndent()

    @Test
    fun acceptsTheShippedPolicy() {
        assertEquals(emptyList<CleartextTransportPolicyFinding>(), check())

        assertEquals(
            emptyList<CleartextTransportPolicyFinding>(),
            CleartextTransportPolicyScanner.check(
                manifestSource = manifest,
                configSource = config.replace(
                    "cleartextTrafficPermitted=\"true\"",
                    "cleartextTrafficPermitted = 'true'"
                )
            )
        )
    }

    @Test
    fun refusesADeniedBaseConfiguration() {
        val findings = check(config = config.replace("true", "false"))

        assertEquals(1, findings.size)
        assertTrue(findings.single().reason.contains("denies cleartext"))
        assertEquals(CleartextTransportPolicyScanner.CONFIG_PATH, findings.single().path)
    }

    @Test
    fun refusesABaseConfigurationWithoutTheAttribute() {
        val findings = check(config = config.replace(" cleartextTrafficPermitted=\"true\"", ""))

        assertEquals(1, findings.size)
        assertTrue(findings.single().reason.contains("does not permit cleartext"))
    }

    @Test
    fun refusesAPermissionOnADomainInsteadOfTheBaseConfig() {
        val findings = check(
            config = """
                <network-security-config>
                    <base-config />
                    <domain-config cleartextTrafficPermitted="true">
                        <domain includeSubdomains="true">10.0.2.2</domain>
                    </domain-config>
                </network-security-config>
            """.trimIndent()
        )

        assertEquals(1, findings.size)
        assertTrue(findings.single().reason.contains("not on the <base-config>"))
    }

    @Test
    fun refusesABaseConfigurationCarryingNothing() {
        val findings = check(
            config = """
                <network-security-config>
                    <base-config />
                </network-security-config>
            """.trimIndent()
        )

        assertEquals(1, findings.size)
        assertTrue(findings.single().reason.contains("does not permit cleartext"))
    }

    @Test
    fun refusesAMissingConfig() {
        val findings = check(config = null)

        assertEquals(1, findings.size)
        assertTrue(findings.single().reason.contains("is missing"))
    }

    @Test
    fun refusesAManifestWithoutTheReference() {
        val findings = check(
            manifest = manifest.replace(
                CleartextTransportPolicyScanner.MANIFEST_REFERENCE,
                "android:theme=\"@style/Theme.NaviVeylin\""
            )
        )

        assertEquals(1, findings.size)
        assertTrue(findings.single().reason.contains("does not reference the network security config"))
    }

    @Test
    fun refusesAFlavourLocalConfig() {
        val findings = check(configPath = "app/src/automotive/res/xml/network_security_config.xml")

        assertEquals(1, findings.size)
        assertTrue(findings.single().reason.contains("shared source set"))
    }

    @Test
    fun reportNamesEveryViolationAndTheRule() {
        val report = CleartextTransportPolicyScanner.report(check(config = config.replace("true", "false")))

        assertTrue(report.contains("Shipped builds permit cleartext repository transport"))
        assertTrue(report.contains(CleartextTransportPolicyScanner.CONFIG_PATH))
        assertTrue(report.contains("denies cleartext"))
    }

    private fun check(
        manifest: String = this.manifest,
        config: String? = this.config,
        configPath: String = CleartextTransportPolicyScanner.CONFIG_PATH
    ): List<CleartextTransportPolicyFinding> = CleartextTransportPolicyScanner.check(
        manifestSource = manifest,
        configSource = config,
        configPath = configPath
    )
}
