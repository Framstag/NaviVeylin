package com.naviveylin.core.mapsource

import com.framstag.libosmscout.client.MapDownloadManager
import java.nio.file.Path

/**
 * Installs a newer version of a regional database over the installation it replaces.
 *
 * The transfer goes into a sibling staging directory ([SlotInstall]), every file is verified against the
 * metadata it was transferred from, and only a complete slot replaces the installation — so the map the
 * user has stays installed and usable while an update runs, and an interrupted, cancelled or failed update
 * leaves it usable and leaves nothing partial behind (spec `map-updates` — "An update replaces the
 * installation only when it is complete").
 *
 * The installed directory keeps its name: the directory name is the installation's identity, and the saved
 * viewport and the last-opened-map setting are keyed by it.
 *
 * The caller owns the threading: this blocks on network and file I/O, so it runs on a background dispatcher
 * (`guidelines/Design.md` §4), and it keeps the app's download lifecycle (foreground service, wake lock)
 * alive around the transfer.
 *
 * @param fetcher the network access to use
 * @param databaseFormatVersion the database format version this client reads, which names the version slot
 *        the transfer is planned from
 */
class RepositoryDatabaseInstaller(
    private val fetcher: RepositoryFetcher,
    val databaseFormatVersion: Int = MapDownloadManager.DATABASE_FORMAT_VERSION
) {

    /**
     * Install the published version of the database at [idPath] into [targetDirectory].
     *
     * @param source the repository source; must be a repository source
     * @param idPath the leaf's index identifier path, i.e. the address of its version slot
     * @param targetDirectory the installed directory to replace; it need not exist yet
     * @param isCancelled polled between chunks and between files
     * @param onProgress reported with (bytes done, bytes planned)
     * @return whether the installation was replaced, was cancelled, or failed with a reported reason
     */
    suspend fun install(
        source: MapSource,
        idPath: List<String>,
        targetDirectory: Path,
        isCancelled: () -> Boolean = { false },
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): DownloadOutcome {
        require(source.isRepository) { "installing from a repository needs a repository source" }

        val planned = when (
            val result = RepositorySource(fetcher, source).planDownload(idPath, databaseFormatVersion)
        ) {
            is RepositoryDownloadPlanResult.Planned -> result
            is RepositoryDownloadPlanResult.Refused -> return DownloadOutcome.Failed(result.failure)
        }

        val incoming = SlotInstall.incomingFor(targetDirectory)
        SlotInstall.sweep(targetDirectory)

        val outcome = SlotInstall.downloadInto(
            incoming = incoming,
            fetcher = fetcher,
            plan = planned.plan,
            metadataDocument = planned.metadataDocument,
            isCancelled = isCancelled,
            onProgress = onProgress
        )
        if (outcome != DownloadOutcome.Completed) {
            // The installed database was never touched.
            return outcome
        }

        // The installation records which source it came from. The slot's inventory names only the slot's own
        // data files, so the swap would otherwise take the marker away with the directory it replaces and the
        // app would read the installation as the built-in provider's — losing both its source label and its
        // place in the update check (spec `map-source-selection` — "Every map directory the app installs
        // contains a marker recording the source it was downloaded from"; spec `map-updates` — "The map keeps
        // its identity").
        MapSourceMarker.write(
            incoming,
            MapSourceRecord(
                sourceKind = MapSourceKind.REPOSITORY,
                baseUrl = source.baseUrl,
                databaseVersion = databaseFormatVersion
            )
        )

        return try {
            SlotInstall.replace(incoming, targetDirectory)
            DownloadOutcome.Completed
        } catch (failure: Exception) {
            // The previous installation is in place or restored; nothing is installed.
            DownloadOutcome.Failed(RepositoryFailure.InstallFailed)
        }
    }
}
