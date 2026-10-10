package com.naviveylin.core.mapsource

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * A repository's region index (`names.json`), read into the tree the available-maps UI consumes.
 *
 * The index defines both the hierarchy and the localized display names, and its leaf identifiers
 * define where the corresponding database is published (spec `map-repository-source` — "Region
 * index drives the available-maps tree", "A repository database's directory name comes from the
 * index, not the display name"; repository format: `Documentation/MapRepository.md` §1.2).
 */
data class IndexRegion(
    /** Identifier of this node, unique among its siblings. */
    val id: String,
    /** Identifiers from the root down to and including this node. */
    val idPath: List<String>,
    /** Name to show for this node, never blank. */
    val displayName: String,
    /** Child regions; empty for a leaf. */
    val children: List<IndexRegion>
) {

    /** True when this node names an import rather than grouping regions. */
    val isLeaf: Boolean
        get() = children.isEmpty()

    /** Every leaf of this subtree, in index order. */
    fun leaves(): List<IndexRegion> =
        if (isLeaf) listOf(this) else children.flatMap { it.leaves() }
}

/** Outcome of reading a region index. */
sealed interface RegionIndexResult {

    /** The index was read: its top-level regions and the number of leaves in the whole tree. */
    data class Loaded(val regions: List<IndexRegion>, val leafCount: Int) : RegionIndexResult

    /** The index could not be used; [failure] says why. */
    data class Unusable(val failure: RepositoryFailure) : RegionIndexResult
}

/**
 * Reads a region index document.
 *
 * The language is the user's, and a name is resolved in this order: the requested language, its
 * primary subtag, English when the node carries it, otherwise the node's first name in alphabetical
 * key order — deterministic, so the same index always renders the same way. A node that carries no
 * name at all is shown under its identifier rather than hidden (spec `map-repository-source` —
 * "Preferred language absent falls back").
 */
object RegionIndexParser {

    /** Schema version of the region index this client reads. */
    const val SUPPORTED_SCHEMA = 1

    private const val FALLBACK_LANGUAGE = "en"

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @param document the index document as served by the repository
     * @param language the user's language tag, e.g. `de` or `de-AT`
     * @return the loaded tree, or the reason the document is unusable
     */
    fun parse(document: String, language: String): RegionIndexResult {
        val element = runCatching { json.parseToJsonElement(document) }.getOrNull()
            ?: return RegionIndexResult.Unusable(RepositoryFailure.MalformedDocument)
        val documentObject = element as? JsonObject
            ?: return RegionIndexResult.Unusable(RepositoryFailure.WrongDocumentKind)
        val schema = (documentObject["schema"] as? JsonPrimitive)?.intOrNull
            ?: return RegionIndexResult.Unusable(RepositoryFailure.WrongDocumentKind)
        if (schema != SUPPORTED_SCHEMA) {
            return RegionIndexResult.Unusable(RepositoryFailure.UnsupportedSchema(schema))
        }

        val wire = runCatching { json.decodeFromString<RegionIndexDocument>(document) }.getOrNull()
            ?: return RegionIndexResult.Unusable(RepositoryFailure.MalformedDocument)

        val regions = wire.regions.map { toRegion(it, emptyList(), language) }
        return RegionIndexResult.Loaded(regions, regions.sumOf { it.leaves().size })
    }

    /**
     * Resolve the name to show for a node's [names] in [language].
     *
     * @return the resolved name, or null when the node carries no name at all
     */
    fun resolveName(names: Map<String, String>, language: String): String? {
        names[language]?.takeIf { it.isNotBlank() }?.let { return it }
        names[language.substringBefore('-')]?.takeIf { it.isNotBlank() }?.let { return it }
        names[FALLBACK_LANGUAGE]?.takeIf { it.isNotBlank() }?.let { return it }
        return names.entries
            .sortedBy { it.key }
            .firstOrNull { it.value.isNotBlank() }
            ?.value
    }

    private fun toRegion(node: RegionNodeDocument, parentPath: List<String>, language: String): IndexRegion {
        val idPath = parentPath + node.id
        return IndexRegion(
            id = node.id,
            idPath = idPath,
            displayName = resolveName(node.names, language) ?: node.id,
            children = node.children.map { toRegion(it, idPath, language) }
        )
    }

    @Serializable
    private data class RegionIndexDocument(
        val schema: Int,
        val regions: List<RegionNodeDocument> = emptyList()
    )

    @Serializable
    private data class RegionNodeDocument(
        val id: String,
        val names: Map<String, String> = emptyMap(),
        val children: List<RegionNodeDocument> = emptyList()
    )
}
