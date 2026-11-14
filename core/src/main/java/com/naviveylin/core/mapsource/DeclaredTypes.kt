package com.naviveylin.core.mapsource

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * The type names a stylesheet declares.
 *
 * A stylesheet's `TYPE <name>` lines are the type configuration the native importer and the renderer
 * work from; the app ships the set in `assets/stylesheets/map.ost`, and `AssetCopier` mirrors that
 * tree to internal storage, so the declared set is on disk beside the installed databases with no
 * network and no new packaging.
 *
 * A `TYPE` line inside a block comment is not a declaration (the shipped `map.ost` documents the
 * format that way), and neither is the `TYPES` block header, so the parse strips block comments and
 * matches the keyword only when a name follows it.
 *
 * Spec `map-updates` — "An installed database that predates the shipped stylesheet is reported"
 * (design D1 of change `report-a-stale-map-install`).
 */
object DeclaredTypes {

    /** The stylesheet file whose `TYPE` lines declare the type set the app ships. */
    const val DECLARED_FILE_NAME = "map.ost"

    /** A declaration: the keyword, whitespace, then the name. */
    private val TYPE_DECLARATION = Regex("""^TYPE\s+(\S+)""")

    /** A block comment, which the shipped stylesheets use for their documentation. */
    private val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)

    /**
     * The names [stylesheet] declares.
     *
     * @param stylesheet the stylesheet to read (`map.ost` of the mirrored stylesheet tree)
     * @return the declared names, or null when the file is absent or unreadable — a comparison
     *         against a set that could not be read is `Unknown`, never a shortfall
     */
    fun readDeclaredNames(stylesheet: Path): Set<String>? =
        try {
            if (!Files.isRegularFile(stylesheet)) {
                null
            } else {
                parse(String(Files.readAllBytes(stylesheet), StandardCharsets.UTF_8))
            }
        } catch (_: Exception) {
            null
        }

    /** Parse a stylesheet's text into the set of names it declares. */
    internal fun parse(text: String): Set<String> {
        val declarations = LinkedHashSet<String>()
        BLOCK_COMMENT.replace(text, " ")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .forEach { line ->
                TYPE_DECLARATION.find(line)?.let { declarations.add(it.groupValues[1]) }
            }
        return declarations
    }
}
