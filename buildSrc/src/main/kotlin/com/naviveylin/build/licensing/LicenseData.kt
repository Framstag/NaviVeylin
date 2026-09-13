package com.naviveylin.build.licensing

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.File

/**
 * One row of the license inventory the app shows.
 *
 * @param textFile name of the file inside `texts/` holding the license text, or
 *   null when the text is not distributed (a `LicenseRef-` license whose terms
 *   live at [licenseUrl])
 */
data class LicenseAssetComponent(
    val name: String,
    val group: String?,
    val version: String?,
    val identifier: String,
    val licenseName: String?,
    val licenseUrl: String?,
    val scope: String,
    val noticeRequired: Boolean,
    val textFile: String?,
    val note: String?,
)

/**
 * Reads the curated license data and turns it into the artifacts the build
 * ships: the inventory the license screen displays, the license texts, and the
 * NOTICE file.
 *
 * Pure file-in/file-out logic so it can be unit-tested (see `LicenseDataTest`);
 * the Gradle task only wires paths.
 */
object LicenseData {

    /** Parses a JSON object file, failing with a message that names the file. */
    fun parseObject(file: File, purpose: String): Map<String, Any?> {
        if (!file.isFile) {
            throw IllegalArgumentException("$purpose: $file does not exist.")
        }
        @Suppress("UNCHECKED_CAST")
        return JsonSlurper().parse(file) as Map<String, Any?>
    }

    private fun mapOfObjects(root: Map<String, Any?>, key: String): Map<String, Map<String, Any?>> {
        val raw = root[key] as? Map<*, *> ?: return emptyMap()
        return raw.entries
            .filter { it.value is Map<*, *> }
            .associate { (k, v) ->
                @Suppress("UNCHECKED_CAST")
                k.toString() to (v as Map<String, Any?>)
            }
    }

    /** The curated native license map's `components` section. */
    fun nativeMap(file: File): Map<String, Map<String, Any?>> =
        mapOfObjects(parseObject(file, "native license map"), "components")

    /** The curated symbol probes used to classify shipped components. */
    fun probes(file: File): Map<String, Map<String, Any?>> =
        mapOfObjects(parseObject(file, "native license map"), "probeSymbols")

    /** The license policy. */
    fun policy(file: File): LicensePolicy {
        val root = parseObject(file, "license policy")
        @Suppress("UNCHECKED_CAST")
        val permitted = root["permitted"] as? Map<String, Any?> ?: emptyMap()
        @Suppress("UNCHECKED_CAST")
        val elections = mapOfObjects(root, "elections")
        @Suppress("UNCHECKED_CAST")
        val refs = mapOfObjects(root, "licenseRefs")
        @Suppress("UNCHECKED_CAST")
        val reviewRequired = (root["reviewRequired"] as? List<Map<String, Any?>>).orEmpty()
        fun strings(key: String): Set<String> =
            (permitted[key] as? List<*>).orEmpty().map { it.toString() }.toSet()
        return LicensePolicy(
            permittedShipped = strings("shipped"),
            permittedBuildTimeOnly = strings("buildTimeOnly"),
            elections = elections.mapValues { (_, value) ->
                Election(value["from"].toString(), value["to"].toString())
            },
            licenseRefs = refs.mapValues { (id, value) ->
                LicenseRefDeclaration(
                    id = id,
                    name = value["name"].toString(),
                    source = value["source"].toString(),
                    textDistributed = value["textDistributed"] as? Boolean ?: false,
                )
            },
            reviewRequired = reviewRequired.mapNotNull { it["license"] as? String }.toSet(),
            noticeRequired = (root["noticeRequired"] as? List<*>).orEmpty()
                .map { it.toString() }
                .toSet(),
        )
    }

    /** Where the text of each identifier comes from, as declared in the policy. */
    fun textSources(file: File): Map<String, Map<String, Any?>> =
        mapOfObjects(parseObject(file, "license policy"), "textSources")

    /** A license text and the source it was taken from. */
    data class ResolvedText(val text: String, val source: String)

    /**
     * Resolves the license text for [identifier] from the declared source.
     *
     * @param vendoredTexts directory of canonical texts (`licenses/texts/`)
     * @param vcpkgRoot vcpkg root, for `vcpkgPort` sources
     * @param triplets triplets to look a port up in
     * @param ndkNotice Android NDK license bundle, for `androidNdk` sources
     * @return null when the identifier has no declared source or is declared
     *   `none`; callers treat that as fatal only when a distributed component
     *   depends on it
     */
    fun licenseText(
        identifier: String,
        textSources: Map<String, Map<String, Any?>>,
        vendoredTexts: File,
        vcpkgRoot: File,
        triplets: List<String>,
        ndkNotice: File?,
    ): ResolvedText? {
        val source = textSources[identifier] ?: return null
        return when (val kind = source["kind"] as? String) {
            "none" -> null
            "file" -> {
                val path = source["path"] as? String
                    ?: throw IllegalArgumentException(
                        "textSources.$identifier declares kind 'file' without a path."
                    )
                val file = File(vendoredTexts, path)
                check(file.isFile) {
                    "textSources.$identifier points at licenses/texts/$path, which does not exist."
                }
                ResolvedText(file.readText(), "licenses/texts/$path")
            }
            "vcpkgPort" -> {
                val port = source["port"] as? String
                    ?: throw IllegalArgumentException(
                        "textSources.$identifier declares kind 'vcpkgPort' without a port."
                    )
                triplets.forEach { triplet ->
                    val file = File(vcpkgRoot, "installed/$triplet/share/$port/copyright")
                    if (file.isFile) return ResolvedText(file.readText(), "vcpkg $port:$triplet copyright")
                }
                throw IllegalArgumentException(
                    "textSources.$identifier points at vcpkg port '$port', whose copyright file is " +
                        "missing for every triplet. Rebuild it: rm -rf vcpkg/buildtrees/$port && " +
                        "./setup-vcpkg.sh"
                )
            }
            "androidNdk" -> {
                val file = ndkNotice
                    ?: throw IllegalArgumentException(
                        "textSources.$identifier needs the Android NDK license bundle " +
                            "(NOTICE.toolchain), which could not be located. Set ANDROID_NDK_HOME or " +
                            "sdk.dir in local.properties."
                    )
                ResolvedText(file.readText(), "Android NDK ${file.name}")
            }
            else -> throw IllegalArgumentException(
                "textSources.$identifier declares unknown kind '$kind' " +
                    "(expected file, vcpkgPort, androidNdk or none)."
            )
        }
    }

    /** Everything the SBOM says about one component, as far as licenses go. */
    private data class SbomComponent(
        val name: String,
        val group: String?,
        val version: String?,
        val identifiers: List<String>,
        val licenseName: String?,
        val licenseUrl: String?,
        val scope: String,
        val noticeRequired: Boolean,
        val note: String?,
    )

    private fun sbomComponents(bomFile: File): Pair<String?, List<SbomComponent>> {
        val root = parseObject(bomFile, "SBOM")
        @Suppress("UNCHECKED_CAST")
        val components = (root["components"] as? List<Map<String, Any?>>).orEmpty()
        val appVersion = (root["metadata"] as? Map<*, *>)
            ?.let { (it["component"] as? Map<*, *>)?.get("version") as? String }

        val parsed = components.mapNotNull { component ->
            val name = component["name"] as? String ?: return@mapNotNull null
            @Suppress("UNCHECKED_CAST")
            val properties = (component["properties"] as? List<Map<String, Any?>>).orEmpty()
            fun property(key: String) =
                properties.firstOrNull { it["name"] == key }?.get("value") as? String
            val scope = property("naviveylin:license:scope")
                ?: throw IllegalArgumentException(
                    "Component '$name' in $bomFile carries no distribution scope. " +
                        "The SBOM was generated before license enrichment — regenerate it."
                )
            @Suppress("UNCHECKED_CAST")
            val licenses = (component["licenses"] as? List<Map<String, Any?>>).orEmpty()
            val expression = licenses.mapNotNull { it["expression"] as? String }.firstOrNull()
            val identifiers = licenses
                .mapNotNull { (it["license"] as? Map<*, *>)?.get("id") as? String }
                .ifEmpty { listOfNotNull(expression) }
            SbomComponent(
                name = name,
                group = component["group"] as? String,
                version = component["version"] as? String,
                identifiers = identifiers,
                licenseName = licenses.mapNotNull {
                    (it["license"] as? Map<*, *>)?.get("name") as? String
                }.firstOrNull(),
                licenseUrl = licenses.mapNotNull {
                    (it["license"] as? Map<*, *>)?.get("url") as? String
                }.firstOrNull(),
                scope = scope,
                noticeRequired = property("naviveylin:license:notice") == "required",
                note = property("naviveylin:license:caveat"),
            )
        }
        return appVersion to parsed
    }

    /**
     * Writes the license inventory and its texts under [outputDir].
     *
     * @return problems that must fail the build (a distributed component whose
     *   license text cannot be produced)
     */
    fun writeAssets(
        bomFile: File,
        outputDir: File,
        policy: LicensePolicy,
        textSources: Map<String, Map<String, Any?>>,
        vendoredTexts: File,
        vcpkgRoot: File,
        triplets: List<String>,
        ndkNotice: File?,
    ): List<String> {
        val (appVersion, components) = sbomComponents(bomFile)
        val problems = mutableListOf<String>()
        val texts = mutableMapOf<String, ResolvedText>()
        val rows = mutableListOf<LicenseAssetComponent>()
        val textCache = mutableMapOf<String, ResolvedText?>()

        components.forEach { component ->
            component.identifiers.forEach { identifier ->
                val ref = policy.licenseRefs[identifier]
                // Only what this application distributes needs a text: a
                // build-time-only component's license is not shipped.
                val textNeeded = ref == null &&
                    (component.scope == Scope.SHIPPED.id || component.noticeRequired)
                val textFile = if (!textNeeded) {
                    null
                } else {
                    val resolved = textCache.getOrPut(identifier) {
                        licenseText(identifier, textSources, vendoredTexts, vcpkgRoot, triplets, ndkNotice)
                    }
                    if (resolved == null) {
                        problems += "${component.name}: no license text available for " +
                            "'$identifier', which this component distributes (declare " +
                            "licenses/license-policy.json textSources.$identifier or add " +
                            "licenses/texts/$identifier.txt)"
                        null
                    } else {
                        texts[identifier] = resolved
                        "$identifier.txt"
                    }
                }
                rows += LicenseAssetComponent(
                    name = component.name,
                    group = component.group,
                    version = component.version,
                    identifier = identifier,
                    licenseName = ref?.name ?: component.licenseName,
                    licenseUrl = ref?.source ?: component.licenseUrl,
                    scope = component.scope,
                    noticeRequired = component.noticeRequired,
                    textFile = textFile,
                    note = component.note,
                )
            }
        }

        val textDir = File(outputDir, "texts")
        textDir.mkdirs()
        texts.forEach { (identifier, resolved) ->
            File(textDir, "$identifier.txt").writeText(resolved.text)
        }

        val payload = linkedMapOf<String, Any?>(
            "appVersion" to appVersion,
            "components" to rows
                .sortedWith(compareBy({ it.scope != Scope.SHIPPED.id }, { it.name }, { it.identifier }))
                .map { row ->
                    linkedMapOf(
                        "name" to row.name,
                        "group" to row.group,
                        "version" to row.version,
                        "identifier" to row.identifier,
                        "licenseName" to row.licenseName,
                        "licenseUrl" to row.licenseUrl,
                        "scope" to row.scope,
                        "noticeRequired" to row.noticeRequired,
                        "textFile" to row.textFile,
                        "note" to row.note,
                    )
                },
            "texts" to texts.map { (identifier, resolved) ->
                linkedMapOf("identifier" to identifier, "source" to resolved.source)
            }.sortedBy { it["identifier"].toString() },
            "links" to policy.licenseRefs.values
                .filter { !it.textDistributed }
                .map { linkedMapOf("identifier" to it.id, "name" to it.name, "url" to it.source) }
                .sortedBy { it["identifier"].toString() },
        )
        outputDir.mkdirs()
        File(outputDir, "dependencies.json").writeText(
            JsonOutput.prettyPrint(JsonOutput.toJson(payload))
        )
        return problems
    }

    /** A component whose license requires its notice to travel with the work. */
    private data class NoticeEntry(
        val component: String,
        val identifier: String,
        val shipped: Boolean,
        val reviewRequired: Boolean,
    )

    /**
     * The NOTICE for the components that require one, grouped by identifier and
     * followed by the license texts the application distributes.
     */
    fun notice(
        bomFile: File,
        policy: LicensePolicy,
        textSources: Map<String, Map<String, Any?>>,
        vendoredTexts: File,
        vcpkgRoot: File,
        triplets: List<String>,
        ndkNotice: File?,
    ): String {
        val (appVersion, components) = sbomComponents(bomFile)
        val entries = components
            .filter { it.noticeRequired && it.identifiers.isNotEmpty() }
            .map { component ->
                NoticeEntry(
                    component = component.group?.let { "$it:${component.name}" } ?: component.name,
                    identifier = component.identifiers.joinToString(" AND "),
                    shipped = component.scope == Scope.SHIPPED.id,
                    reviewRequired = component.identifiers.any { it in policy.reviewRequired },
                )
            }

        val lines = mutableListOf(
            "NaviVeylin NOTICE",
            "=================",
            "",
            "Version: ${appVersion ?: "unknown"}",
            "",
            "This distribution includes the third-party components listed below.",
            "Each entry names the component, the license it is used under, and the",
            "source of the license text distributed with this work. Entries marked",
            "REVIEW still need the project owner's license decision.",
            "",
            "Components",
            "----------",
        )
        entries.sortedWith(compareBy({ it.identifier }, { it.component })).forEach { entry ->
            val scope = if (entry.shipped) "distributed" else "build-time only"
            val review = if (entry.reviewRequired) "  [REVIEW]" else ""
            lines += "- ${entry.component} — ${entry.identifier} ($scope)$review"
        }

        lines += ""
        lines += "License texts"
        lines += "-------------"
        entries.map { it.identifier }.distinct().sorted().forEach { identifier ->
            val resolved = licenseText(identifier, textSources, vendoredTexts, vcpkgRoot, triplets, ndkNotice)
            lines += ""
            lines += identifier
            lines += "-".repeat(identifier.length)
            if (resolved == null) {
                lines += "No license text is distributed for this identifier. See"
                lines += "licenses/license-policy.json (licenseRefs) for its source."
            } else {
                lines += "source: ${resolved.source}"
                lines += ""
                lines += resolved.text.trim()
            }
        }
        return lines.joinToString("\n") + "\n"
    }
}
