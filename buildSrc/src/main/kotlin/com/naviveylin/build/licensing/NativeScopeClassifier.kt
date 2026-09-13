package com.naviveylin.build.licensing

/**
 * Symbol probes that identify a component's code inside a packaged shared object.
 *
 * @param exact fully qualified symbol names to look for
 * @param prefix symbol prefixes to look for (used where the component's symbols
 *   are C++ mangled, so no stable full name exists)
 */
data class ComponentProbes(
    val exact: List<String> = emptyList(),
    val prefix: List<String> = emptyList(),
)

/**
 * Why a component was classified the way it was.
 *
 * @param scope the derived distribution scope
 * @param evidence human-readable reason, recorded in the SBOM
 * @param ambiguous true when the component is shipped but no symbol of its own
 *   was found in a packaged object — the link configuration is then the only
 *   evidence, which is exactly the case a reviewer should look at
 */
data class ScopeEvidence(
    val scope: Scope,
    val evidence: String,
    val ambiguous: Boolean = false,
)

/**
 * Derives the distribution scope of native components from the built artifact.
 *
 * A component is shipped when one of these holds, in order:
 * 1. a packaged shared object carries its name (`libosmscout` → `libosmscoutd.so`),
 * 2. one of its probe symbols appears in a packaged object (its code is there),
 * 3. one of its static archives reaches a native link line.
 *
 * Everything else is build-time only: installed on the build machine, absent
 * from the application.
 */
object NativeScopeClassifier {

    fun classify(
        components: List<String>,
        probes: Map<String, ComponentProbes>,
        packagedObjects: Map<String, Set<String>>,
        archivesByComponent: Map<String, Set<String>>,
        linkedArchives: Set<String>,
    ): Map<String, ScopeEvidence> {
        val result = linkedMapOf<String, ScopeEvidence>()
        components.forEach { name ->
            val packagedObject = packagedObjects.keys.firstOrNull { matches(name, it) }
            val componentProbes = probes[name] ?: ComponentProbes()
            val probeHit = packagedObjects.values.any { symbols ->
                componentProbes.exact.any { it in symbols } ||
                    componentProbes.prefix.any { prefix -> symbols.any { it.startsWith(prefix) } }
            }
            val archives = archivesByComponent[name].orEmpty()
            val linked = archives.filter { it in linkedArchives }

            result[name] = when {
                packagedObject != null -> ScopeEvidence(
                    scope = Scope.SHIPPED,
                    evidence = "packaged as $packagedObject.so"
                )
                probeHit -> ScopeEvidence(
                    scope = Scope.SHIPPED,
                    evidence = "probe symbol found in a packaged shared object"
                )
                linked.isNotEmpty() -> ScopeEvidence(
                    scope = Scope.SHIPPED,
                    evidence = "static archives (${linked.sorted().joinToString(", ")}) reach a " +
                        "native link line; no symbol of the component itself was found in a " +
                        "packaged object (hidden or inlined symbols)",
                    ambiguous = true
                )
                else -> ScopeEvidence(
                    scope = Scope.BUILD_TIME_ONLY,
                    evidence = "no packaged object, no probe symbol, no linked archive"
                )
            }
        }
        return result
    }

    /**
     * True when packaged object [base] (without its `.so` suffix) is the same
     * library as component [name]. Native debug builds and the map library add
     * suffixes: `libosmscout` → `libosmscoutd`, `libosmscout_map_cairo`.
     */
    fun matches(name: String, base: String): Boolean =
        base == name ||
            base == "${name}d" ||
            base.startsWith("$name-") ||
            base.startsWith("${name}d-") ||
            base.startsWith("${name}_")
}
