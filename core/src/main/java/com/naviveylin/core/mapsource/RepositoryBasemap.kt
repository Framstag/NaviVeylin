package com.naviveylin.core.mapsource

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * The basemap of a libosmscout mapgen repository: reads the availability manifest, and installs the
 * published basemap version slot through the same verified download as a regional database.
 *
 * The repository's basemap is laid out like a database — `basemap/v<version>/` with a `db.json` and the
 * data files — so it reuses [DatabaseDownloader] with `basemap` as the identifier path
 * (spec `map-repository-source`; `Documentation/MapRepository.md` §2). A tar.gz archive at the same
 * location is not read: this source's basemap is its version slot.
 *
 * @param fetcher the network access to use
 * @param source the repository source; must be a repository source
 * @param readableDatabaseVersion the newest database format version this client can read, i.e.
 *        `MapDownloadManager.DATABASE_FORMAT_VERSION`
 */
class RepositoryBasemap(
    private val fetcher: RepositoryFetcher,
    private val source: MapSource,
    /** The newest database format version this client can read, i.e. `MapDownloadManager.DATABASE_FORMAT_VERSION`. */
    val readableDatabaseVersion: Int = com.framstag.libosmscout.client.MapDownloadManager
        .DATABASE_FORMAT_VERSION
) {

    init {
        require(source.isRepository) { "a repository basemap needs a repository source" }
    }

    /**
     * Read the basemap availability manifest.
     *
     * A repository without a manifest (or without a version this client can read) reports
     * [BasemapAvailability.Unavailable], never an error: the basemap is optional.
     */
    suspend fun availableVersion(): BasemapAvailability {
        val url = RepositoryUrlPlanner.basemapManifestUrl(source.baseUrl)
        return when (val fetched = fetcher.text(url)) {
            is TextFetch.Loaded -> when (
                val parsed = BasemapManifestParser.parse(fetched.body, readableDatabaseVersion)
            ) {
                is BasemapManifestResult.Loaded ->
                    parsed.versions.firstOrNull()?.let { BasemapAvailability.Available(it) }
                        ?: BasemapAvailability.Unavailable
                is BasemapManifestResult.Unusable ->
                    BasemapAvailability.Failed(parsed.failure)
            }
            is TextFetch.Failed -> when (fetched.failure) {
                is RepositoryFailure.HttpStatus -> BasemapAvailability.Unavailable
                else -> BasemapAvailability.Failed(fetched.failure)
            }
        }
    }

    /**
     * Install [version]'s slot as the basemap, replacing an installed one only after the new slot is
     * complete and verified.
     *
     * @param version the version to install, from [availableVersion]
     * @param basemapDirectory the basemap directory to install into
     * @param isCancelled polled between chunks and between files
     * @param onProgress reported with (bytes done, bytes planned)
     * @return whether the slot is installed, was cancelled, or failed — an existing basemap survives
     *         both of the latter
     */
    suspend fun install(
        version: BasemapVersion,
        basemapDirectory: Path,
        isCancelled: () -> Boolean = { false },
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): DownloadOutcome {
        val idPath = listOf(RepositoryUrlPlanner.BASEMAP_DIRECTORY_NAME)
        val plan = when (
            val result = planSlot(version, idPath)
        ) {
            is RepositoryDownloadPlanResult.Planned -> result
            is RepositoryDownloadPlanResult.Refused -> return DownloadOutcome.Failed(result.failure)
        }

        val incoming = basemapDirectory.resolveSibling(".${basemapDirectory.fileName}.incoming")
        val outcome = DatabaseDownloader(fetcher).download(
            plan.plan, plan.metadataDocument, incoming, isCancelled, onProgress
        )
        if (outcome != DownloadOutcome.Completed) {
            // The previously installed basemap is untouched.
            return outcome
        }

        swap(incoming, basemapDirectory)
        return DownloadOutcome.Completed
    }

    /** Read a slot's metadata and plan its download, addressed by the slot's own version. */
    private suspend fun planSlot(
        version: BasemapVersion,
        idPath: List<String>
    ): RepositoryDownloadPlanResult {
        val slotVersion = version.typeConfigVersion
        val metadataUrl = RepositoryUrlPlanner.basemapMetadataUrl(source.baseUrl, slotVersion)
        val document = when (val fetched = fetcher.text(metadataUrl)) {
            is TextFetch.Loaded -> fetched.body
            is TextFetch.Failed -> return RepositoryDownloadPlanResult.Refused(
                if (fetched.failure is RepositoryFailure.HttpStatus) {
                    RepositoryFailure.DatabaseNotPublished
                } else {
                    fetched.failure
                }
            )
        }
        val metadata = when (
            val parsed = DatabaseMetadataParser.parse(document, slotVersion)
        ) {
            is DatabaseMetadataResult.Loaded -> parsed.metadata
            is DatabaseMetadataResult.Unusable -> return RepositoryDownloadPlanResult.Refused(parsed.failure)
        }
        return when (
            val plan = DownloadPlan.forDatabase(source.baseUrl, idPath, metadata, slotVersion)
        ) {
            is DownloadPlanResult.Planned -> RepositoryDownloadPlanResult.Planned(plan, document, metadata)
            is DownloadPlanResult.Refused -> RepositoryDownloadPlanResult.Refused(plan.failure)
        }
    }

    /**
     * Replace [target] with [incoming], keeping the installed basemap until the new one is in place.
     * The previous basemap is restored when the swap fails.
     */
    private fun swap(incoming: Path, target: Path) {
        val backup = target.resolveSibling(".${target.fileName}.backup")
        deleteRecursively(backup)

        val hadInstalled = Files.exists(target)
        if (hadInstalled) {
            Files.move(target, backup, StandardCopyOption.REPLACE_EXISTING)
        }
        try {
            Files.createDirectories(target.parent)
            try {
                Files.move(incoming, target, StandardCopyOption.ATOMIC_MOVE)
            } catch (notSupported: java.nio.file.AtomicMoveNotSupportedException) {
                copyRecursively(incoming, target)
                deleteRecursively(incoming)
            }
        } catch (failure: Exception) {
            if (hadInstalled) {
                deleteRecursively(target)
                Files.move(backup, target, StandardCopyOption.REPLACE_EXISTING)
            }
            deleteRecursively(incoming)
            throw failure
        }
        deleteRecursively(backup)
    }

    private fun copyRecursively(source: Path, target: Path) {
        Files.walk(source).forEach { path ->
            val destination = target.resolve(source.relativize(path).toString())
            if (Files.isDirectory(path)) {
                Files.createDirectories(destination)
            } else {
                Files.createDirectories(destination.parent)
                Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private fun deleteRecursively(directory: Path) {
        if (!Files.exists(directory)) {
            return
        }
        Files.walk(directory).sorted(Comparator.reverseOrder()).forEach { path ->
            runCatching { Files.deleteIfExists(path) }
        }
    }
}
