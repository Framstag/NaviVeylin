package com.naviveylin.core.mapsource

import com.framstag.libosmscout.client.MapDownloadManager
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/** Which kind of repository data an installation holds. */
enum class MapUpdateKind {

    /** A regional database installed from one index leaf. */
    REGION,

    /** The world basemap slot. */
    BASEMAP
}

/** What the comparison found for one installation. */
sealed interface UpdateState {

    /** The repository publishes nothing newer than what is installed. */
    data object UpToDate : UpdateState

    /**
     * The repository publishes something newer.
     *
     * @param downloadBytes the size of the published version's files as its metadata declares them
     */
    data class UpdateAvailable(val downloadBytes: Long) : UpdateState

    /**
     * The repository no longer publishes the version slot this client reads.
     *
     * The installation stays installed and usable — a missing slot is not a reason to remove data.
     */
    data object NoLongerOffered : UpdateState

    /**
     * The comparison could not be made.
     *
     * @param failure what the request or document reported, or null when the installation could not be
     *        matched to a single index leaf, so there was nothing to compare it with
     */
    data class Unknown(val failure: RepositoryFailure? = null) : UpdateState
}

/**
 * One installation the check compared.
 *
 * @param kind which kind of repository data this is
 * @param directoryName the installed directory's name, the installation's identity in a log line
 * @param displayName the name the region index gives this installation, or null for a basemap (which no
 *        index names — the surface that shows it owns its label, `:core` owns no wording)
 * @param idPath the index identifier path of the leaf this installation came from, or null when it could
 *        not be matched to exactly one leaf
 * @param state what the comparison found
 */
data class MapUpdateItem(
    val kind: MapUpdateKind,
    val directoryName: String,
    val displayName: String?,
    val idPath: List<String>?,
    val state: UpdateState
)

/**
 * Outcome of comparing an installation with its repository.
 */
sealed interface MapUpdateCheckResult {

    /**
     * The comparison ran; every installation of this source carries its own state.
     *
     * @param items one entry per installation that was compared
     * @param leftoverStaging directories a previous update attempt left behind, named by their target —
     *        staging data is recognised rather than hidden, and is never compared as an installation or
     *        listed as an update (see [SlotInstall.stagingTargetOf])
     */
    data class Compared(
        val items: List<MapUpdateItem>,
        val leftoverStaging: List<String> = emptyList()
    ) : MapUpdateCheckResult

    /** The comparison could not run at all; [failure] says why. */
    data class Failed(val failure: RepositoryFailure) : MapUpdateCheckResult
}

/**
 * Compares the data installed from a repository with what that repository publishes.
 *
 * The comparison follows the repository's client update contract (`Documentation/MapRepository.md` §6):
 * a client reads the region index and the metadata of **its own** version slot, and an installation of
 * another source, of another repository or of a version this client no longer reads is never reported as
 * having an update.
 *
 * Three rules this class exists for:
 * - a comparison that cannot be made is `Unknown`, never an update — an unreachable repository must not
 *   look like a newer database;
 * - a version slot that is not published keeps the installation in place (`NoLongerOffered`), so a
 *   repository that dropped a region cannot make the app delete a usable map;
 * - an installation is matched to its leaf by the directory name the index implies
 *   ([RepositoryUrlPlanner.databaseDirectoryName]); an installation matching no leaf or more than one is
 *   never probed at a guessed path.
 *
 * The caller owns the threading: this blocks on file I/O and on network reads, so it is invoked from a
 * background dispatcher (`guidelines/Design.md` §4).
 *
 * Spec: `map-updates` — "An installed repository database is compared with its own version slot", "Each
 * compared item carries one update state", "The check covers the active repository source only";
 * `basemap-discovery` — "Report basemap version for updates".
 *
 * @param fetcher the network access to use
 * @param databaseFormatVersion the database format version this client reads, which names the version slot
 *        of both a regional database and a basemap
 */
class MapUpdateChecker(
    private val fetcher: RepositoryFetcher,
    val databaseFormatVersion: Int = MapDownloadManager.DATABASE_FORMAT_VERSION
) {

    /**
     * Compare [installedDirectories] and [basemapDirectory] with what [source] publishes.
     *
     * @param source the repository source to compare against; must be a repository source
     * @param installedDirectories every installed database directory the app found, of any source
     * @param basemapDirectory the installed basemap directory, or null when none is installed
     * @param language the user's language tag, e.g. `de` or `de-AT`, for the names of the index
     * @return the per-installation states, or the reason the comparison could not run
     */
    suspend fun check(
        source: MapSource,
        installedDirectories: List<Path>,
        basemapDirectory: Path?,
        language: String
    ): MapUpdateCheckResult {
        require(source.isRepository) { "an update check needs a repository source" }

        val index = when (val listed = RepositorySource(fetcher, source).availableRegions(language)) {
            is RegionIndexResult.Loaded -> listed
            is RegionIndexResult.Unusable -> return MapUpdateCheckResult.Failed(listed.failure)
        }
        val leavesByDirectoryName = index.regions
            .flatMap { it.leaves() }
            .groupBy { RepositoryUrlPlanner.databaseDirectoryName(it.idPath) }

        val items = buildList {
            installedDirectories.forEach { directory ->
                if (SlotInstall.isStaging(directory.fileName.toString())) {
                    return@forEach
                }
                if (isFrom(source, directory)) {
                    add(compareRegion(directory, leavesByDirectoryName, source))
                }
            }
            if (basemapDirectory != null && isFrom(source, basemapDirectory)) {
                compareBasemap(basemapDirectory, source)?.let { add(it) }
            }
        }
        return MapUpdateCheckResult.Compared(items, leftoverStaging = discoveredStaging(installedDirectories))
    }

    /**
     * The staging directories among [installedDirectories], as the names of the installations they stage
     * for.
     *
     * A transfer that never reached its swap, and a swap whose cleanup failed, leave a directory that
     * carries a complete `types.dat` — which is exactly what an installed-map scan looks for. Recognising
     * them is what keeps such a leftover out of the update list and out of the comparison; removing them is
     * the app-start path's job (`SlotInstall.sweep`).
     */
    private fun discoveredStaging(installedDirectories: List<Path>): List<String> =
        installedDirectories.mapNotNull { directory ->
            SlotInstall.stagingTargetOf(directory.fileName.toString())
        }

    /**
     * Whether [directory] holds data this check may compare: it must carry a marker naming [source], so a
     * map the built-in provider installed — or one installed from another repository — is neither
     * compared nor requested (spec `map-updates` — "The check covers the active repository source only").
     */
    private fun isFrom(source: MapSource, directory: Path): Boolean {
        val record = MapSourceMarker.sourceOf(directory)
        return record.sourceKind == MapSourceKind.REPOSITORY &&
            MapSource.repository(record.baseUrl.orEmpty()).baseUrl == source.baseUrl
    }

    /** Compare one installed regional database with its own leaf's version slot. */
    private suspend fun compareRegion(
        directory: Path,
        leavesByDirectoryName: Map<String, List<IndexRegion>>,
        source: MapSource
    ): MapUpdateItem {
        val directoryName = directory.fileName.toString()
        val leaves = leavesByDirectoryName[directoryName].orEmpty()
        val leaf = leaves.singleOrNull()
        val installed = DatabaseMetadataParser.readInstalled(directory)
        val installedTime = installed?.generatedAt?.toInstantOrNull()

        if (leaf == null || installed == null || installedTime == null) {
            return regionItem(directoryName, leaf, UpdateState.Unknown())
        }

        return when (val fetched = fetchMetadata(source, leaf.idPath, databaseFormatVersion)) {
            MetadataFetch.Missing -> regionItem(directoryName, leaf, UpdateState.NoLongerOffered)
            is MetadataFetch.Failed -> regionItem(directoryName, leaf, UpdateState.Unknown(fetched.failure))
            is MetadataFetch.Loaded -> {
                val serverTime = fetched.metadata.generatedAt?.toInstantOrNull()
                val state = when {
                    serverTime == null -> UpdateState.Unknown()
                    serverTime.isAfter(installedTime) ->
                        UpdateState.UpdateAvailable(fetched.metadata.totalSizeBytes)
                    else -> UpdateState.UpToDate
                }
                regionItem(directoryName, leaf, state)
            }
        }
    }

    private fun regionItem(directoryName: String, leaf: IndexRegion?, state: UpdateState): MapUpdateItem =
        MapUpdateItem(
            kind = MapUpdateKind.REGION,
            directoryName = directoryName,
            displayName = leaf?.displayName,
            idPath = leaf?.idPath,
            state = state
        )

    /**
     * Compare the installed basemap with the repository's availability manifest.
     *
     * @return the basemap's item, or null when the repository reports no comparable basemap version — the
     *         basemap is optional and a repository that publishes none (or none this client can read)
     *         reports no update state rather than an error (spec `basemap-discovery` — "Source publishes no
     *         comparable version")
     */
    private suspend fun compareBasemap(directory: Path, source: MapSource): MapUpdateItem? {
        val directoryName = directory.fileName.toString()
        val installed = DatabaseMetadataParser.readInstalled(directory)
        val offered = when (
            val manifest = fetcher.text(RepositoryUrlPlanner.basemapManifestUrl(source.baseUrl))
        ) {
            is TextFetch.Loaded ->
                (BasemapManifestParser.parse(manifest.body, databaseFormatVersion)
                    as? BasemapManifestResult.Loaded)
                    ?.versions
                    ?.firstOrNull()
            is TextFetch.Failed -> null
        }
        if (offered == null) {
            // No comparable version is published: the basemap is optional and this source has no update
            // state to report at all, rather than an unknown one.
            return null
        }
        if (installed == null) {
            return basemapItem(directoryName, UpdateState.Unknown())
        }

        if (!BasemapUpdate.isNewer(offered, installed)) {
            return basemapItem(directoryName, UpdateState.UpToDate)
        }

        // The manifest names no sizes, so an available update reads the slot's own metadata for them.
        val idPath = listOf(RepositoryUrlPlanner.BASEMAP_DIRECTORY_NAME)
        return when (val slot = fetchMetadata(source, idPath, offered.typeConfigVersion)) {
            is MetadataFetch.Loaded ->
                basemapItem(directoryName, UpdateState.UpdateAvailable(slot.metadata.totalSizeBytes))
            MetadataFetch.Missing -> basemapItem(directoryName, UpdateState.Unknown())
            is MetadataFetch.Failed -> basemapItem(directoryName, UpdateState.Unknown(slot.failure))
        }
    }

    private fun basemapItem(directoryName: String, state: UpdateState): MapUpdateItem =
        MapUpdateItem(
            kind = MapUpdateKind.BASEMAP,
            directoryName = directoryName,
            displayName = null,
            idPath = null,
            state = state
        )

    /**
     * Read one version slot's metadata.
     *
     * The distinction between [MetadataFetch.Missing] and [MetadataFetch.Failed] is the point: a slot the
     * repository does not publish is "no longer offered", while any other answer — a server error, an
     * unusable document, a broken transport — is a comparison that could not be made
     * (spec `map-updates` — "an absent slot is no longer offered, a comparison that cannot be made is
     * unknown").
     */
    private suspend fun fetchMetadata(
        source: MapSource,
        idPath: List<String>,
        slotVersion: Int
    ): MetadataFetch {
        val url = RepositoryUrlPlanner.metadataUrl(source.baseUrl, idPath, slotVersion)
        return when (val fetched = fetcher.text(url)) {
            is TextFetch.Loaded ->
                (DatabaseMetadataParser.parse(fetched.body, slotVersion)
                    as? DatabaseMetadataResult.Loaded)
                    ?.let { MetadataFetch.Loaded(it.metadata) }
                    ?: MetadataFetch.Failed(RepositoryFailure.MalformedDocument)
            is TextFetch.Failed ->
                if (isSlotMissing(fetched.failure)) {
                    MetadataFetch.Missing
                } else {
                    MetadataFetch.Failed(fetched.failure)
                }
        }
    }

    /** Whether [failure] means the repository does not publish that slot, rather than a failed request. */
    private fun isSlotMissing(failure: RepositoryFailure): Boolean =
        failure is RepositoryFailure.HttpStatus && failure.status in SLOT_MISSING_STATUSES

    private fun String.toInstantOrNull(): Instant? = runCatching { Instant.parse(this) }.getOrNull()

    /** Outcome of reading one version slot's metadata. */
    private sealed interface MetadataFetch {

        data class Loaded(val metadata: DatabaseMetadata) : MetadataFetch

        /** The repository does not publish that slot. */
        data object Missing : MetadataFetch

        data class Failed(val failure: RepositoryFailure) : MetadataFetch
    }

    private companion object {

        /** Statuses that mean "this slot is not published here", not "the request failed". */
        val SLOT_MISSING_STATUSES = setOf(404, 410)
    }
}
