package com.naviveylin.build.jni

/** One violation of the JNI bridge's native parity rule. */
data class NativeParityFinding(
    /** Path as reported to the build (repo-relative). */
    val path: String,
    /** What is wrong, phrased as the edit that fixes it. */
    val reason: String
)

/** One recorded, intentional divergence between the submodule and its Android override. */
data class RecordedDeviation(
    /** The overridden bridge file the divergence belongs to, e.g. `OSMScoutClient.java`. */
    val file: String,
    /** `submodule-only` (the override does not carry it) or `override-only` (the override adds it). */
    val direction: String,
    /** `name(type,type)` — the same shape [NativeParityScanner.nativesIn] produces. */
    val signature: String,
    /** Why the divergence is intended; a blank reason is a malformed line. */
    val reason: String
)

/**
 * Build gate for the two Java sides of the JNI bridge (change `fix-jni-native-parity-guard`, spec
 * `osmscout-jni` — "The override mirrors the submodule's natives, except the recorded deviations").
 *
 * `:osmscout-client-java` compiles the libosmscout submodule's `libosmscout-client-java/java` sources
 * **except five overridden files** (`guidelines/Design.md` §5). A native the submodule declares and the
 * override does not reach is a `NoSuchMethodError` at the first call — no compiler error, no failing test —
 * and the reverse holds when the submodule renames or drops a native the override still declares (issue #174,
 * the drift class #119 fixed by hand).
 *
 * A blanket "submodule natives ⊆ override natives" check is **false**, which is why no gate existed: the
 * override diverges on purpose in *both* directions — it adds Android natives (`reloadBasemap`,
 * `getAddressAt`, `getMaxSpeedAt`, `getDatabaseBoundingBox`, `searchLocationByForm`) and it drops submodule
 * natives the Android surfaces do not carry (`getRegion`, `setGpsMarker`, the region-name search,
 * `calculateRouteWithObjectsAsync`, and `startNavigation` which it re-implements in Java on top of
 * `startNavigationWithVehicle`). So the rule this scanner proves is the *pair*: the two sides differ by
 * exactly the recorded list, no more and no less.
 *
 * Both halves matter, in both directions:
 *
 *  1. a divergence nobody recorded is a finding — the next submodule API addition fails the build and names
 *     itself instead of waiting for the call site;
 *  2. a recorded divergence that no longer exists is a finding — the list cannot rot into an allowlist that
 *     no longer describes the trees.
 *
 * Signatures are compared with package qualifiers stripped, because the same declaration reads
 * `ByteBuffer` in one file and `java.nio.ByteBuffer` in the other; that is an import, not a divergence.
 *
 * Pure and dependency-free, so it is unit-tested in `buildSrc` alongside the license, i18n, coordinate and
 * transport logic; the Gradle task only points it at the files.
 */
object NativeParityScanner {

    /** Repo-relative directory of the submodule's Java client sources. */
    const val SUBMODULE_DIR =
        "app/src/main/cpp/libosmscout/libosmscout-client-java/java/com/framstag/libosmscout/client"

    /** Repo-relative directory of the Android overrides. */
    const val OVERRIDE_DIR = "osmscout-client-java/src/main/java/com/framstag/libosmscout/client"

    /** Repo-relative path of the recorded deviation list. */
    const val DEVIATION_LIST_PATH = "osmscout-client-java/native-parity.txt"

    /**
     * The five files `:osmscout-client-java` takes from its own sources instead of the submodule
     * (`guidelines/Design.md` §5, "The bridge's two sides").
     */
    val OVERRIDDEN_FILES = listOf(
        "OSMScoutClient.java",
        "OSMScoutClientBuilder.java",
        "BasemapManager.java",
        "MapDownloadManager.java",
        "AvailableMapEntry.java"
    )

    /** The two directions a difference can have; also the list's vocabulary. */
    const val SUBMODULE_ONLY = "submodule-only"
    const val OVERRIDE_ONLY = "override-only"

    private val BLOCK_COMMENT = Regex("""/\*.*?\*/""", setOf(RegexOption.DOT_MATCHES_ALL))
    private val LINE_COMMENT = Regex("""//[^\n]*""")
    private val NATIVE_DECLARATION =
        Regex("""\bnative\s+[\w\[\].<>, ]+?\s+(\w+)\s*\(([^)]*)\)""", setOf(RegexOption.DOT_MATCHES_ALL))

    /**
     * The native methods a Java source declares, as `name(type,type)` with package qualifiers stripped.
     *
     * Comments are removed first, so a javadoc that shows a signature is not a declaration (the
     * coordinate gate's lesson in the other direction: prose about a symbol is not the symbol).
     */
    fun nativesIn(source: String): Set<String> {
        val body = LINE_COMMENT.replace(BLOCK_COMMENT.replace(source, ""), "")
        return NATIVE_DECLARATION.findAll(body)
            .map { match -> signature(match.groupValues[1], match.groupValues[2]) }
            .toSet()
    }

    /** `name(String,int)` from a method name and its parameter list, normalized for comparison. */
    fun signature(name: String, parameters: String): String {
        val types = parameters.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { parameter ->
                // A declaration names its parameter after the type; the type is every preceding token, minus a
                // `final`. The package qualifier is an import difference, not a divergence.
                parameter.split(' ')
                    .filter { it.isNotEmpty() && it != "final" }
                    .let { tokens -> if (tokens.size > 1) tokens.dropLast(1) else tokens }
                    .joinToString("")
                    .substringAfterLast('.')
                    .replace("...", "[]")
            }
        return "$name(${types.joinToString(",")})"
    }

    /**
     * Parse the recorded deviation list.
     *
     * One entry per line: `file | submodule-only|override-only | name(types) | reason`. Blank lines and lines
     * beginning with `#` are comments. A line whose shape is wrong yields a finding rather than a silently
     * dropped allowance.
     */
    fun deviationsIn(
        listSource: String,
        listPath: String = DEVIATION_LIST_PATH
    ): Pair<List<RecordedDeviation>, List<NativeParityFinding>> {
        val deviations = mutableListOf<RecordedDeviation>()
        val findings = mutableListOf<NativeParityFinding>()
        listSource.lines().forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            val fields = line.split('|').map { it.trim() }
            val shape = "$listPath:${index + 1}"
            when {
                fields.size != 4 -> findings += NativeParityFinding(
                    shape,
                    "a deviation line reads `file | $SUBMODULE_ONLY|$OVERRIDE_ONLY | name(types) | reason` " +
                        "— found ${fields.size} field(s): $line"
                )
                fields[0] !in OVERRIDDEN_FILES -> findings += NativeParityFinding(
                    shape,
                    "`${fields[0]}` is not one of the five overridden bridge files " +
                        "(${OVERRIDDEN_FILES.joinToString(", ")})"
                )
                fields[1] != SUBMODULE_ONLY && fields[1] != OVERRIDE_ONLY -> findings += NativeParityFinding(
                    shape,
                    "`${fields[1]}` is not a direction: use $SUBMODULE_ONLY or $OVERRIDE_ONLY"
                )
                !fields[2].contains('(') -> findings += NativeParityFinding(
                    shape,
                    "`${fields[2]}` is not a signature: write `name(type,type)`"
                )
                fields[3].isEmpty() -> findings += NativeParityFinding(
                    shape,
                    "a recorded deviation carries no reason — a blank entry is an allowlist nobody can review"
                )
                else -> deviations += RecordedDeviation(fields[0], fields[1], fields[2], fields[3])
            }
        }
        return deviations to findings
    }

    /**
     * Check the two sides of every overridden file against the recorded deviations.
     *
     * @param submoduleSources the submodule's copy of each overridden file, by file name
     * @param overrideSources the Android override of each file, by file name
     * @param deviationList the recorded deviation list, or null when the file is absent
     * @param submoduleDir reported path prefix for the submodule side
     * @param overrideDir reported path prefix for the override side
     * @param listPath reported path of the deviation list
     */
    fun check(
        submoduleSources: Map<String, String>,
        overrideSources: Map<String, String>,
        deviationList: String?,
        submoduleDir: String = SUBMODULE_DIR,
        overrideDir: String = OVERRIDE_DIR,
        listPath: String = DEVIATION_LIST_PATH
    ): List<NativeParityFinding> {
        val findings = mutableListOf<NativeParityFinding>()
        if (deviationList == null) {
            findings += NativeParityFinding(
                listPath,
                "the recorded deviation list is missing: the two Java sides of the JNI bridge diverge on " +
                    "purpose, so the divergences are recorded here with a reason each instead of being " +
                    "guessed by a subset check"
            )
        }
        val (recorded, listFindings) = deviationsIn(deviationList ?: "", listPath)
        findings += listFindings

        for (file in OVERRIDDEN_FILES) {
            val submoduleSource = submoduleSources[file]
            val overrideSource = overrideSources[file]
            if (submoduleSource == null) {
                findings += NativeParityFinding(
                    "$submoduleDir/$file",
                    "the submodule's copy is missing, so the parity of the override cannot be proven — " +
                        "initialise the submodule (`git submodule update --init`)"
                )
                continue
            }
            if (overrideSource == null) {
                findings += NativeParityFinding(
                    "$overrideDir/$file",
                    "the override is missing, so the submodule's natives reach no Java caller"
                )
                continue
            }
            val submoduleNatives = nativesIn(submoduleSource)
            val overrideNatives = nativesIn(overrideSource)

            for (signature in (submoduleNatives - overrideNatives).sorted()) {
                findings += unrecorded(
                    file, SUBMODULE_ONLY, signature, recorded,
                    "$overrideDir/$file",
                    "the submodule declares `$signature` and the override does not reach it — declare it in " +
                        "the override, or record that the Android port drops it"
                )
            }
            for (signature in (overrideNatives - submoduleNatives).sorted()) {
                findings += unrecorded(
                    file, OVERRIDE_ONLY, signature, recorded,
                    "$submoduleDir/$file",
                    "the override declares `$signature` and the submodule does not, so the next submodule " +
                        "sync drops it — record the Android addition"
                )
            }
            for (deviation in recorded.filter { it.file == file }) {
                val actual = when (deviation.direction) {
                    SUBMODULE_ONLY -> submoduleNatives - overrideNatives
                    else -> overrideNatives - submoduleNatives
                }
                if (deviation.signature !in actual) {
                    findings += NativeParityFinding(
                        listPath,
                        "the recorded ${deviation.direction} deviation `${deviation.signature}` of $file no " +
                            "longer exists — the two sides now agree on it, so remove the line " +
                            "(\"${deviation.reason}\")"
                    )
                }
            }
        }
        return findings
    }

    private fun unrecorded(
        file: String,
        direction: String,
        signature: String,
        recorded: List<RecordedDeviation>,
        path: String,
        reason: String
    ): List<NativeParityFinding> =
        if (recorded.any { it.file == file && it.direction == direction && it.signature == signature }) {
            emptyList()
        } else {
            listOf(
                NativeParityFinding(
                    path,
                    "unrecorded native drift: $reason (add `$file | $direction | $signature | <reason>` to " +
                        NativeParityScanner.DEVIATION_LIST_PATH + " if the divergence is intended)"
                )
            )
        }

    /** Human-readable report for a Gradle failure. */
    fun report(findings: List<NativeParityFinding>): String = buildString {
        append("The Android JNI override must mirror the submodule's natives, except the recorded deviations\n")
        append("spec: osmscout-jni — \"The override mirrors the submodule's natives, except the recorded deviations\";\n")
        append("change fix-jni-native-parity-guard, guidelines/Design.md §5 (\"The bridge's two sides\").\n")
        append(":osmscout-client-java compiles the submodule's Java client sources except five overridden files,\n")
        append("so a native on one side only is a NoSuchMethodError at the first call — no compiler error.\n")
        append("If a divergence is intended, record it in ${NativeParityScanner.DEVIATION_LIST_PATH} with a reason;\n")
        append("if it is not, mirror the native. Both are one line.\n\n")
        findings.forEach { append("${it.path}: ${it.reason}\n") }
    }
}
