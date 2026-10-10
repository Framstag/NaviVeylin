package com.naviveylin.build.transport

/** One violation of the cleartext repository transport policy. */
data class CleartextTransportPolicyFinding(
    /** Path as reported to the build (repo-relative). */
    val path: String,
    /** What is wrong, phrased as the edit that fixes it. */
    val reason: String
)

/**
 * Build gate for the map repository's transport policy (change `allow-lan-http-map-repository`,
 * spec `map-download-infrastructure` — "Shipped builds permit cleartext repository transport").
 *
 * Four facts are asserted, because any one of them alone leaves the permission ineffective while a
 * build stays green:
 *
 *  1. the network security config exists at the shipping path;
 *  2. it sits in the **shared** source set (`src/main/res`), so both distribution flavours — mobile
 *     and automotive — inherit it instead of one flavour permitting cleartext and the other not;
 *  3. its permission is on a `<base-config>`, not on a `<domain-config>`: a domain entry names hosts
 *     known when the APK is built, and the host here is whatever LAN address the user typed;
 *  4. the main manifest references the config. Without `android:networkSecurityConfig` no config
 *     applies at all, and `android:usesCleartextTraffic` (which the debug overlay used to set) is
 *     ignored once a config *is* referenced.
 *
 * There is **no allowlist**, deliberately: a site that wants an exception asks for a policy change,
 * not for a name in a gate. The scanner proves the *sources*; the merged release manifest is a
 * separate, build-output question that the change's on-device/evidence task inspects once.
 *
 * Pure and dependency-free, so it is unit-tested in `buildSrc` alongside the license, i18n and
 * coordinate logic; the Gradle task only points it at the two files.
 */
object CleartextTransportPolicyScanner {

    /** Repo-relative path of the shipping network security config. */
    const val CONFIG_PATH = "app/src/main/res/xml/network_security_config.xml"

    /** Repo-relative path of the main manifest that must reference the config. */
    const val MANIFEST_PATH = "app/src/main/AndroidManifest.xml"

    /** The manifest attribute that makes the config apply, exactly as it must read. */
    const val MANIFEST_REFERENCE =
        "android:networkSecurityConfig=\"@xml/network_security_config\""

    /** A `<base-config …>` opener; XML attribute values cannot contain `>`, so `[^>]*` is the body. */
    private val BASE_CONFIG = Regex("""<base-config\b([^>]*)>""")

    private val PERMITTED = Regex("""cleartextTrafficPermitted\s*=\s*['\"]true['\"]""")

    private val DENIED = Regex("""cleartextTrafficPermitted\s*=\s*['\"]false['\"]""")

    /** Source-set fragment every shipping resource path carries. */
    private const val SHARED_SOURCE_SET = "/src/main/res/"

    /**
     * Check the policy against the two files.
     *
     * @param manifestSource the main manifest's text
     * @param configSource the config's text, or null when the file is absent
     * @param configPath the config's path as read, so a flavour-local copy is refused
     * @param manifestPath the manifest's path as read
     */
    fun check(
        manifestSource: String,
        configSource: String?,
        configPath: String = CONFIG_PATH,
        manifestPath: String = MANIFEST_PATH
    ): List<CleartextTransportPolicyFinding> {
        val findings = mutableListOf<CleartextTransportPolicyFinding>()
        if (!configPath.replace('\\', '/').contains(SHARED_SOURCE_SET)) {
            findings += CleartextTransportPolicyFinding(
                configPath,
                "the config is not in the shared source set: move it under $SHARED_SOURCE_SET so " +
                    "every distribution flavour inherits the same transport policy"
            )
        }
        if (!manifestSource.contains(MANIFEST_REFERENCE)) {
            findings += CleartextTransportPolicyFinding(
                manifestPath,
                "the application element does not reference the network security config, so no " +
                    "policy applies: add $MANIFEST_REFERENCE"
            )
        }
        if (configSource == null) {
            findings += CleartextTransportPolicyFinding(
                configPath,
                "the shipping network security config is missing"
            )
            return findings
        }
        val baseConfig = BASE_CONFIG.find(configSource)?.groupValues?.get(1)
        when {
            baseConfig == null -> findings += CleartextTransportPolicyFinding(
                configPath,
                "no <base-config> element: a permission carried anywhere else is not app-wide"
            )
            PERMITTED.containsMatchIn(baseConfig) -> Unit
            DENIED.containsMatchIn(baseConfig) -> findings += CleartextTransportPolicyFinding(
                configPath,
                "the <base-config> denies cleartext " +
                    "(cleartextTrafficPermitted=\"false\"), so a plain-HTTP repository is " +
                    "unreachable in a shipped build"
            )
            PERMITTED.containsMatchIn(configSource) -> findings += CleartextTransportPolicyFinding(
                configPath,
                "the permission is not on the <base-config>: a <domain-config> names only hosts " +
                    "known when the APK is built, and this host is whatever the user types"
            )
            else -> findings += CleartextTransportPolicyFinding(
                configPath,
                "the <base-config> does not permit cleartext: set " +
                    "cleartextTrafficPermitted=\"true\""
            )
        }
        return findings
    }

    /** Human-readable report for a Gradle failure. */
    fun report(findings: List<CleartextTransportPolicyFinding>): String = buildString {
        append("The shipping build must permit cleartext for the map repository transport\n")
        append("spec: map-download-infrastructure — \"Shipped builds permit cleartext repository transport\";\n")
        append("change allow-lan-http-map-repository.\n")
        append("A self-hosted libosmscout mapgen repository is served over plain HTTP on a LAN host\n")
        append("the user types, and Android's static policy cannot name that host in advance.\n")
        append("If this failure is intentional, change the decision in the spec as well.\n\n")
        findings.forEach { append("${it.path}: ${it.reason}\n") }
    }
}
