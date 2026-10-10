package com.naviveylin.data

import com.framstag.libosmscout.client.MapDownloadManager
import com.framstag.libosmscout.client.MapProvider
import com.naviveylin.core.mapsource.DatabaseDownloader
import com.naviveylin.core.mapsource.MapSource
import com.naviveylin.core.mapsource.MapSourceKind
import com.naviveylin.core.mapsource.RepositoryBasemap
import com.naviveylin.core.mapsource.RepositorySource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's map sources: the provider it ships with and, beside it, a libosmscout mapgen repository
 * at the URL the user stored.
 *
 * One seam decides which source is active and which protocol implementation serves it, so a third
 * source would be a new implementation and not a new branch in the view model (design D1; spec
 * `map-download-infrastructure` — "Default map provider configured", `map-source-selection` — "Map
 * source is a registered, persisted choice").
 *
 * The registry reads the selection; changing it (including the deletion a switch performs) is the
 * switch use case's job.
 */
@Singleton
class MapSourceRegistry @Inject constructor(
    private val settings: SettingsStorage,
    private val provider: MapProvider,
    private val fetcher: HttpUrlFetcher
) {

    /** The provider the app ships with: its name, base URI and listing template. */
    val builtInProvider: MapProvider
        get() = provider

    /**
     * The database format version this client reads.
     *
     * The bridge's single constant, so a repository's version slot and the provider listing's version
     * bounds cannot drift apart (spec `map-download-infrastructure` — "Database format version has one
     * source of truth").
     */
    val databaseFormatVersion: Int
        get() = MapDownloadManager.DATABASE_FORMAT_VERSION

    /**
     * The sources the selector offers, the built-in provider first.
     *
     * The repository is offered even before a URL was entered — that is how a user reaches the URL
     * field and its test action; an empty URL is never an *active* source (see [activeSource]).
     */
    suspend fun availableSources(): List<MapSource> {
        val stored = settings.load()
        return listOf(
            MapSource.BuiltInProvider,
            MapSource(MapSourceKind.REPOSITORY, stored.mapRepositoryUrl)
        )
    }

    /** The persisted selection, with the fallback rule for an unusable stored value applied. */
    suspend fun activeSource(): MapSource = settings.load().selectedMapSource()

    /**
     * The region lister for [source].
     *
     * @param source a repository source; a built-in provider source is listed through the provider
     *        listing instead, which the native layer parses
     */
    fun repositoryLister(source: MapSource): RepositorySource = RepositorySource(fetcher, source)

    /**
     * The basemap of [source], read from the repository's availability manifest and version slot.
     *
     * @param source a repository source
     */
    fun repositoryBasemap(source: MapSource): RepositoryBasemap =
        RepositoryBasemap(fetcher, source, databaseFormatVersion)

    /**
     * The executor for a regional database download from a repository: the same verified path the
     * basemap slot uses, so one implementation writes, checks and installs both
     * (spec `map-repository-source`).
     */
    fun databaseDownloader(): DatabaseDownloader = DatabaseDownloader(fetcher)
}
