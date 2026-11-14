package com.naviveylin.core.mapsource

import java.nio.file.Path

/**
 * What an installed database's type set says about the stylesheet the app ships.
 *
 * Three answers, and the third is the one that keeps the check honest: a database whose `types.dat`
 * this build cannot read is not judged at all, so a `FILE_FORMAT_VERSION` bump answers `Unknown`
 * rather than reporting every declared name as missing (design D1, the recorded decision on item
 * #880).
 *
 * Spec `map-updates` — "An installed database that predates the shipped stylesheet is reported".
 */
sealed interface InstalledTypeVerdict {

    /**
     * The database lacks declared names, so its data predates the app's stylesheet.
     *
     * @param count the number of declared names the database does not carry
     * @param sample up to a handful of the missing names, sorted, for the record and the log
     */
    data class Missing(val count: Int, val sample: List<String>) : InstalledTypeVerdict

    /** The database carries every name the stylesheet declares. */
    data object Current : InstalledTypeVerdict

    /** The database could not be judged: no readable `types.dat`, or a format version this build does not read. */
    data object Unknown : InstalledTypeVerdict
}

/**
 * Compares an installed database's type names with the names the bundled stylesheet declares.
 *
 * Both halves are reads of files that are already on disk, so the answer is computed when it is
 * asked for and nothing is persisted (design D2): a re-download changes the answer with no cache to
 * invalidate.
 */
object StaleInstallCheck {

    /** How many missing names a [InstalledTypeVerdict.Missing] carries beside its count. */
    const val SAMPLE_SIZE = 5

    /**
     * Judge the database in [databaseDirectory] against [declaredNames].
     *
     * @param databaseDirectory the installed database's directory, which holds `types.dat`
     * @param declaredNames the names the bundled stylesheet declares, or null when the stylesheet
     *        could not be read
     * @param expectedFormatVersion the data format version this build reads
     */
    fun of(
        databaseDirectory: Path,
        declaredNames: Set<String>?,
        expectedFormatVersion: Int
    ): InstalledTypeVerdict {
        if (declaredNames == null) {
            return InstalledTypeVerdict.Unknown
        }
        val carried = TypeConfigNames.readNames(
            databaseDirectory.resolve(TypeConfigNames.TYPES_FILE_NAME),
            expectedFormatVersion
        ) ?: return InstalledTypeVerdict.Unknown

        val missing = declaredNames - carried
        return if (missing.isEmpty()) {
            InstalledTypeVerdict.Current
        } else {
            InstalledTypeVerdict.Missing(missing.size, missing.sorted().take(SAMPLE_SIZE))
        }
    }
}
