package com.naviveylin.core.mapsource

/**
 * Builds the URLs and directory names of a libosmscout mapgen repository.
 *
 * Layout (repository format: `Documentation/MapRepository.md` §2):
 * ```
 * <base>/names.json                        region index
 * <base>/<id path>/v<version>/db.json      a database's metadata
 * <base>/<id path>/v<version>/<file>       a database's data files
 * <base>/basemap/index.json                basemap availability manifest
 * <base>/basemap/v<version>/...            basemap slot, shaped like a database
 * ```
 *
 * Two rules this class exists for:
 * - the version slot is addressed with the *caller's* database format version, which is the one
 *   value the app keeps (`MapDownloadManager.DATABASE_FORMAT_VERSION`, spec
 *   `map-download-infrastructure` — "Database format version has one source of truth");
 * - an installed database's directory name comes from the index identifier path, never from the
 *   localized display name (spec `map-repository-source` — "A repository database's directory name
 *   comes from the index, not the display name").
 */
object RepositoryUrlPlanner {

    /** File name of the region index at the repository's root. */
    const val REGION_INDEX_FILE_NAME = "names.json"

    /** Directory name of the basemap at the repository's root. */
    const val BASEMAP_DIRECTORY_NAME = "basemap"

    /** File name of the basemap availability manifest. */
    const val BASEMAP_MANIFEST_FILE_NAME = "index.json"

    /** Separator between the identifier segments of an installed database's directory name. */
    private const val DIRECTORY_SEPARATOR = "-"

    /**
     * Remove whitespace so a URL is concatenated without doubling slashes.
     *
     * Whitespace is removed *everywhere*, not merely trimmed: a phone keyboard inserts a space after
     * a period, so a hand-typed address can reach the field as `10.0. 2. 2:30123`, and a paste can
     * carry any padding (spec `map-source-selection` — "A repository base URL is normalised before it
     * is used"). A URL that genuinely contains a space carries it as `%20`, so removal cannot turn a
     * usable URL into a wrong one.
     *
     * @param baseUrl the repository's base URL as the user typed it
     * @return the URL without whitespace and without a trailing slash
     */
    fun normaliseBaseUrl(baseUrl: String): String =
        baseUrl.filterNot(Char::isWhitespace).trimEnd('/')

    /**
     * Whether a repository base URL is served unencrypted.
     *
     * The transport policy permits cleartext for the repository (spec `map-download-infrastructure`),
     * so an `http` source is usable — this is what tells the map manager to mark it as unencrypted
     * (spec `map-source-selection` — "An unencrypted repository source is marked as such"). A
     * scheme-less or otherwise unusable value is *not* marked: an unusable URL reports itself as
     * unusable, which is a different statement.
     *
     * @param baseUrl the repository's base URL as the user typed it
     */
    fun isUnencrypted(baseUrl: String): Boolean =
        normaliseBaseUrl(baseUrl).startsWith("http://", ignoreCase = true)

    /** @return the region index URL of the repository at [baseUrl] */
    fun regionIndexUrl(baseUrl: String): String =
        "${normaliseBaseUrl(baseUrl)}/$REGION_INDEX_FILE_NAME"

    /** @return the version slot directory name for [databaseFormatVersion], e.g. `v27` */
    fun versionSlotName(databaseFormatVersion: Int): String = "v$databaseFormatVersion"

    /**
     * The server-side path of a database, from its index identifier path.
     *
     * @param idPath identifiers from the index root down to the leaf
     * @return the path below the repository root, e.g. `europe/germany/berlin`
     */
    fun slotPath(idPath: List<String>): String = idPath.joinToString("/")

    /**
     * The version slot URL of a database.
     *
     * @param baseUrl repository base URL
     * @param idPath identifiers from the index root down to the leaf
     * @param databaseFormatVersion the version this client reads
     * @return the slot URL, with a trailing slash
     */
    fun slotUrl(baseUrl: String, idPath: List<String>, databaseFormatVersion: Int): String =
        "${normaliseBaseUrl(baseUrl)}/${slotPath(idPath)}/${versionSlotName(databaseFormatVersion)}/"

    /**
     * The URL of a database's metadata inside its own version slot.
     *
     * @return the `db.json` URL
     */
    fun metadataUrl(baseUrl: String, idPath: List<String>, databaseFormatVersion: Int): String =
        slotUrl(baseUrl, idPath, databaseFormatVersion) + DatabaseMetadataParser.METADATA_FILE_NAME

    /**
     * The URL of one data file inside a database's version slot.
     *
     * @param fileName the file name the database's metadata names
     * @return the file URL
     */
    fun fileUrl(
        baseUrl: String,
        idPath: List<String>,
        databaseFormatVersion: Int,
        fileName: String
    ): String = slotUrl(baseUrl, idPath, databaseFormatVersion) + fileName

    /**
     * The local directory name of an installed database, derived from its identifier path so it
     * does not change with the user's language or with a localized display name.
     *
     * @return the directory name, e.g. `europe-germany-berlin`
     */
    fun databaseDirectoryName(idPath: List<String>): String =
        idPath.joinToString(DIRECTORY_SEPARATOR)

    /** @return the basemap availability manifest URL of the repository at [baseUrl] */
    fun basemapManifestUrl(baseUrl: String): String =
        "${normaliseBaseUrl(baseUrl)}/$BASEMAP_DIRECTORY_NAME/$BASEMAP_MANIFEST_FILE_NAME"

    /** @return the basemap version slot URL for [databaseFormatVersion], with a trailing slash */
    fun basemapSlotUrl(baseUrl: String, databaseFormatVersion: Int): String =
        "${normaliseBaseUrl(baseUrl)}/$BASEMAP_DIRECTORY_NAME/" +
            "${versionSlotName(databaseFormatVersion)}/"

    /** @return the basemap slot's `db.json` URL */
    fun basemapMetadataUrl(baseUrl: String, databaseFormatVersion: Int): String =
        basemapSlotUrl(baseUrl, databaseFormatVersion) + DatabaseMetadataParser.METADATA_FILE_NAME

    /** @return the URL of one basemap data file inside its version slot */
    fun basemapFileUrl(baseUrl: String, databaseFormatVersion: Int, fileName: String): String =
        basemapSlotUrl(baseUrl, databaseFormatVersion) + fileName
}
