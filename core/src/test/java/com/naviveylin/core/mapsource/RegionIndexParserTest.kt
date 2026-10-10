package com.naviveylin.core.mapsource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [RegionIndexParser].
 *
 * Spec: map-repository-source — "Region index drives the available-maps tree".
 */
class RegionIndexParserTest {

    private val nestedIndex = """
        {
          "schema": 1,
          "regions": [
            {
              "id": "europe",
              "names": {"en": "Europe", "de": "Europa"},
              "children": [
                {
                  "id": "germany",
                  "names": {"en": "Germany", "de": "Deutschland"},
                  "children": [
                    {"id": "berlin", "names": {"en": "Berlin", "de": "Berlin"}},
                    {"id": "brandenburg", "names": {"en": "Brandenburg"}}
                  ]
                },
                {"id": "iceland", "names": {"en": "Iceland", "de": "Island"}}
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun nestedTreeIsPreserved() {
        val result = RegionIndexParser.parse(nestedIndex, "de")

        assertTrue(result is RegionIndexResult.Loaded)
        val europe = (result as RegionIndexResult.Loaded).regions.single()
        assertEquals("europe", europe.id)
        assertEquals(listOf("europe"), europe.idPath)
        assertEquals(listOf("germany", "iceland"), europe.children.map { it.id })
        val germany = europe.children.first()
        assertEquals(listOf("berlin", "brandenburg"), germany.children.map { it.id })
        assertTrue(germany.children.all { it.isLeaf })
    }

    @Test
    fun nameUsesPreferredLanguage() {
        val result = RegionIndexParser.parse(nestedIndex, "de") as RegionIndexResult.Loaded

        assertEquals("Europa", result.regions.single().displayName)
        assertEquals("Deutschland", result.regions.single().children.first().displayName)
    }

    @Test
    fun nameUsesPrimarySubtagForRegionTag() {
        val result = RegionIndexParser.parse(nestedIndex, "de-AT") as RegionIndexResult.Loaded

        assertEquals("Europa", result.regions.single().displayName)
    }

    @Test
    fun fallsBackToEnglishWhenPresent() {
        val result = RegionIndexParser.parse(nestedIndex, "fr") as RegionIndexResult.Loaded

        assertEquals("Europe", result.regions.single().displayName)
    }

    @Test
    fun fallsBackToFirstEntryOtherwise() {
        val result = RegionIndexParser.parse(nestedIndex, "fr") as RegionIndexResult.Loaded

        // Brandenburg carries English only; Berlin carries German only.
        val germany = result.regions.single().children.first()
        assertEquals("Berlin", germany.children.first { it.id == "berlin" }.displayName)
        assertEquals("Brandenburg", germany.children.first { it.id == "brandenburg" }.displayName)
    }

    @Test
    fun nodeWithoutNamesIsNotHidden() {
        val index = """{"schema": 1, "regions": [{"id": "nameless"}]}"""

        val result = RegionIndexParser.parse(index, "de") as RegionIndexResult.Loaded

        assertEquals("nameless", result.regions.single().displayName)
    }

    @Test
    fun leavesExposeTheIdPathAndTheCount() {
        val result = RegionIndexParser.parse(nestedIndex, "en") as RegionIndexResult.Loaded

        assertEquals(3, result.leafCount)
        val leaves = result.regions.flatMap { it.leaves() }
        assertEquals(
            listOf(
                listOf("europe", "germany", "berlin"),
                listOf("europe", "germany", "brandenburg"),
                listOf("europe", "iceland")
            ),
            leaves.map { it.idPath }
        )
    }

    @Test
    fun rejectsUnsupportedSchema() {
        val index = """{"schema": 2, "regions": []}"""

        val result = RegionIndexParser.parse(index, "en")

        assertEquals(RegionIndexResult.Unusable(RepositoryFailure.UnsupportedSchema(2)), result)
    }

    @Test
    fun rejectsNonIndexDocument() {
        val array = RegionIndexParser.parse("[1, 2, 3]", "en")
        val otherObject = RegionIndexParser.parse("""{"foo": 1}""", "en")

        assertEquals(RegionIndexResult.Unusable(RepositoryFailure.WrongDocumentKind), array)
        assertEquals(RegionIndexResult.Unusable(RepositoryFailure.WrongDocumentKind), otherObject)
    }

    @Test
    fun rejectsMalformedDocument() {
        val result = RegionIndexParser.parse("{\"schema\": 1, \"regions\": [", "en")

        assertEquals(RegionIndexResult.Unusable(RepositoryFailure.MalformedDocument), result)
    }

    @Test
    fun resolveNamePrefersTheRequestedLanguageThenEnglishThenAlphabetical() {
        assertEquals("Europa", RegionIndexParser.resolveName(mapOf("en" to "Europe", "de" to "Europa"), "de"))
        assertEquals("Europe", RegionIndexParser.resolveName(mapOf("en" to "Europe", "de" to "Europa"), "fr"))
        assertEquals(
            "Aaa",
            RegionIndexParser.resolveName(mapOf("zz" to "Zzz", "aa" to "Aaa"), "fr")
        )
        assertNull(RegionIndexParser.resolveName(emptyMap(), "de"))
    }
}
