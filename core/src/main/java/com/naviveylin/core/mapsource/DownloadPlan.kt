package com.naviveylin.core.mapsource

/** One file a download will fetch, with everything needed to verify it. */
data class PlannedFile(
    /** File name inside the version slot. */
    val name: String,
    /** Absolute URL to fetch. */
    val url: String,
    /** Declared size in bytes. */
    val sizeBytes: Long,
    /** Declared CRC-32. */
    val crc32: Long
)

/** Outcome of planning a database download. */
sealed interface DownloadPlanResult {

    /**
     * The files to fetch, in the order to write them: every data file first, the database's type
     * configuration last, so an interrupted download never leaves a directory that is complete
     * apart from the type configuration — that directory would be recognised as a map (spec
     * `map-repository-source` — "The type configuration is installed last").
     */
    data class Planned(val files: List<PlannedFile>) : DownloadPlanResult {

        /** Sum of the declared sizes of every planned file. */
        val totalBytes: Long
            get() = files.sumOf { it.sizeBytes }
    }

    /** The inventory cannot be installed; [failure] says why. */
    data class Refused(val failure: RepositoryFailure) : DownloadPlanResult
}

/**
 * Builds the file list of a database download from the database's own metadata — never from a fixed
 * file list (spec `map-repository-source` — "Only the files the metadata names are fetched").
 */
object DownloadPlan {

    /**
     * @param baseUrl the repository's base URL, the same one the metadata was fetched from
     * @param idPath the leaf's index identifier path
     * @param metadata the database's metadata
     * @param databaseFormatVersion the version this client reads, which names the version slot
     * @return the plan, or the reason the database must not be installed
     */
    fun forDatabase(
        baseUrl: String,
        idPath: List<String>,
        metadata: DatabaseMetadata,
        databaseFormatVersion: Int
    ): DownloadPlanResult {
        val unverifiable = metadata.files.firstOrNull { !it.isVerifiable }
        if (unverifiable != null) {
            return DownloadPlanResult.Refused(RepositoryFailure.VerificationFailed(unverifiable.name))
        }

        val planned = metadata.files.map { file ->
            PlannedFile(
                name = file.name,
                url = RepositoryUrlPlanner.fileUrl(baseUrl, idPath, databaseFormatVersion, file.name),
                sizeBytes = file.sizeBytes!!,
                crc32 = file.crc32!!
            )
        }
        return DownloadPlanResult.Planned(planned.sortedWith(typeConfigurationLast))
    }

    /** Data files keep their metadata order; the type configuration moves to the end. */
    private val typeConfigurationLast = compareBy<PlannedFile> {
        if (it.name == DatabaseMetadataParser.TYPE_CONFIGURATION_FILE_NAME) 1 else 0
    }
}
