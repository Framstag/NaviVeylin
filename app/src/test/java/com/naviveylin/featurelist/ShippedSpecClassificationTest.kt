package com.naviveylin.featurelist

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Holds the repository's own shipped-spec classification to `spec-feature-index`'s requirement
 * "An unclassified spec id stops the run" (scenario "A classified spec id passes"): every spec id
 * present in `openspec/specs/` must be named by `tools/feature-list/specs.json`, so the project's
 * classification gate (`bash tools/gen-feature-list.sh --check-classification`) reports no
 * unclassified id.
 *
 * The case reads the two artefacts the gate reads — the shipped-spec directory and the
 * classification file — rather than invoking the script, so it needs no shell and no `jq` and a
 * failure names exactly the ids the gate reports. The working directory of an `:app` unit test is
 * the module directory, the way `FavAutoZoomClampRangeTest` reads its spec file. Both counts and
 * the unclassified ids are printed before the assertion, so the JUnit XML's `system-out` carries
 * the measurement of the run that made it.
 */
class ShippedSpecClassificationTest {

    private val repoRoot = File("..").canonicalFile
    private val specsDir = File(repoRoot, "openspec/specs")
    private val classificationFile = File(repoRoot, "tools/feature-list/specs.json")

    @Test
    fun `every shipped spec id is classified`() {
        assertTrue("the shipped-spec directory must exist at ${specsDir.absolutePath}", specsDir.isDirectory)
        assertTrue("the classification must exist at ${classificationFile.absolutePath}", classificationFile.isFile)

        val shipped = shippedSpecIds()
        val classified = classifiedSpecIds()
        val unclassified = shipped.filterNot { it in classified }
        println("shipped=${shipped.size} classified=${classified.size} unclassified=$unclassified")

        assertEquals(
            "every shipped spec id must be named by tools/feature-list/specs.json",
            emptyList<String>(),
            unclassified
        )
    }

    /** Every shipped spec id: each directory under the shipped set that holds a `spec.md`, nested ids included. */
    private fun shippedSpecIds(): List<String> =
        specsDir.walkTopDown()
            .filter { it.isFile && it.name == "spec.md" }
            .map { it.parentFile.relativeTo(specsDir).path }
            .sorted()
            .toList()

    /** The keys of the classification's `specs` object. */
    private fun classifiedSpecIds(): Set<String> =
        Json.parseToJsonElement(classificationFile.readText())
            .jsonObject.getValue("specs")
            .jsonObject.keys
}
