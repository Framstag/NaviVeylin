package com.naviveylin.architecture

/**
 * The structure declaration `architecture/structure.tsv` (change `package-structure-as-five-kinds`,
 * task 1.2): one row per package, tab-separated as `module, package, kind, feature, may_import`.
 * Comment lines start with `#` and carry the measurement the rows were taken against.
 */
const val DECLARATION_PATH: String = "architecture/structure.tsv"

/** One row of the declaration, with the line number a report can quote. */
data class DeclarationRow(
    val line: Int,
    val module: String,
    val packageName: String,
    val kind: String,
    val feature: String,
    val mayImport: List<String>,
) {
    /** The row as a reader can find it again: `architecture/structure.tsv:<line> <package> (<kind>)`. */
    val pointer: String get() = "$DECLARATION_PATH:$line $packageName ($kind)"
}

/** A declaration line that is not five tab-separated columns. It is refused rather than guessed at, so a
 *  declaration the reader cannot parse is a red run and never an empty rule set; the named fail-closed cases
 *  are task 1.11's. */
class DeclarationParseError(message: String) : IllegalArgumentException(message)

/**
 * The parsed declaration. A row is looked up by the package name alone: a package name carries its
 * module root, so two modules cannot claim the same package without claiming the same row.
 */
data class StructureDeclaration(val rows: List<DeclarationRow>) {

    fun rowFor(packageName: String): DeclarationRow? = rows.firstOrNull { it.packageName == packageName }

    /** The kind a package is declared as, or `null` when no row claims it. */
    fun kindOf(packageName: String): String? = rowFor(packageName)?.kind

    /** The packages the declaration claims, in row order. */
    fun declaredPackages(): Set<String> = rows.map { it.packageName }.toSet()

    companion object {

        fun parse(text: String): StructureDeclaration {
            val rows = mutableListOf<DeclarationRow>()
            text.lineSequence().forEachIndexed { index, raw ->
                val line = index + 1
                if (raw.isBlank() || raw.startsWith("#")) return@forEachIndexed
                val columns = raw.split('\t')
                if (columns.size != 5) {
                    throw DeclarationParseError(
                        "$DECLARATION_PATH:$line carries ${columns.size} tab-separated columns, 5 are declared"
                    )
                }
                rows += DeclarationRow(
                    line = line,
                    module = columns[0],
                    packageName = columns[1],
                    kind = columns[2],
                    feature = columns[3],
                    mayImport = columns[4].split(',').map { it.trim() }.filter { it.isNotEmpty() && it != "-" },
                )
            }
            return StructureDeclaration(rows)
        }
    }
}
