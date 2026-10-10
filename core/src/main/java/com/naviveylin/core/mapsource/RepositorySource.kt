package com.naviveylin.core.mapsource

/**
 * Lists what a source offers, and reads one leaf's database metadata from its own version slot.
 *
 * One implementation per source family that speaks the repository protocol; the built-in provider's
 * listing is read through the provider listing the native layer parses, so it has no implementation of
 * this class (design D7, D1).
 */
class RepositorySource(
    private val fetcher: RepositoryFetcher,
    /** The repository source, the same one the metadata was fetched from. */
    val source: MapSource
) {

    init {
        require(source.isRepository) { "a repository source needs a base URL" }
    }

    /**
     * @param language the user's language tag, e.g. `de` or `de-AT`
     * @return the regions the source offers, or the reason they could not be listed
     */
    suspend fun availableRegions(language: String): RegionIndexResult {
        val url = RepositoryUrlPlanner.regionIndexUrl(source.baseUrl)
        return when (val fetched = fetcher.text(url)) {
            is TextFetch.Loaded -> RegionIndexParser.parse(fetched.body, language)
            is TextFetch.Failed -> RegionIndexResult.Unusable(fetched.failure)
        }
    }

    /**
     * Read one leaf's database metadata from its own version slot.
     *
     * @param idPath the leaf's index identifier path
     * @param databaseFormatVersion the version this client reads
     * @return the metadata, or the reason it is unusable — including
     *         [RepositoryFailure.DatabaseNotPublished] when the slot does not exist
     */
    suspend fun metadataOf(
        idPath: List<String>,
        databaseFormatVersion: Int
    ): DatabaseMetadataResult {
        val url = RepositoryUrlPlanner.metadataUrl(source.baseUrl, idPath, databaseFormatVersion)
        return when (val fetched = fetcher.text(url)) {
            is TextFetch.Loaded -> DatabaseMetadataParser.parse(fetched.body, databaseFormatVersion)
            is TextFetch.Failed -> when (fetched.failure) {
                is RepositoryFailure.HttpStatus ->
                    DatabaseMetadataResult.Unusable(RepositoryFailure.DatabaseNotPublished)
                else -> DatabaseMetadataResult.Unusable(fetched.failure)
            }
        }
    }

    /**
     * Read a database's metadata document and plan its download in one step, so the plan and the
     * document it was built from cannot diverge.
     *
     * @return the plan with the document to store, or the reason the database must not be installed
     */
    suspend fun planDownload(
        idPath: List<String>,
        databaseFormatVersion: Int
    ): RepositoryDownloadPlanResult {
        val url = RepositoryUrlPlanner.metadataUrl(source.baseUrl, idPath, databaseFormatVersion)
        val document = when (val fetched = fetcher.text(url)) {
            is TextFetch.Loaded -> fetched.body
            is TextFetch.Failed -> return RepositoryDownloadPlanResult.Refused(
                if (fetched.failure is RepositoryFailure.HttpStatus) {
                    RepositoryFailure.DatabaseNotPublished
                } else {
                    fetched.failure
                }
            )
        }
        val metadata = when (val parsed = DatabaseMetadataParser.parse(document, databaseFormatVersion)) {
            is DatabaseMetadataResult.Loaded -> parsed.metadata
            is DatabaseMetadataResult.Unusable -> return RepositoryDownloadPlanResult.Refused(parsed.failure)
        }
        return when (val plan = DownloadPlan.forDatabase(
            source.baseUrl, idPath, metadata, databaseFormatVersion
        )) {
            is DownloadPlanResult.Planned -> RepositoryDownloadPlanResult.Planned(
                plan = plan,
                metadataDocument = document,
                metadata = metadata
            )
            is DownloadPlanResult.Refused -> RepositoryDownloadPlanResult.Refused(plan.failure)
        }
    }
}

/** Outcome of reading a database's metadata and planning its download. */
sealed interface RepositoryDownloadPlanResult {

    /** Everything needed to run the download. */
    data class Planned(
        val plan: DownloadPlanResult.Planned,
        val metadataDocument: String,
        val metadata: DatabaseMetadata
    ) : RepositoryDownloadPlanResult

    /** The database must not be installed; [failure] says why. */
    data class Refused(val failure: RepositoryFailure) : RepositoryDownloadPlanResult
}
