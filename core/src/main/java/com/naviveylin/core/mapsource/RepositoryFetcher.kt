package com.naviveylin.core.mapsource

/** Outcome of fetching a text document. */
sealed interface TextFetch {

    /** The document body. */
    data class Loaded(val body: String) : TextFetch

    /** The document could not be fetched; [failure] says why. */
    data class Failed(val failure: RepositoryFailure) : TextFetch
}

/**
 * The network access the repository protocol needs, implemented by the platform so `:core` stays a
 * pure protocol layer that a JVM test can drive with a fake (design D2; the Android implementation
 * uses `HttpURLConnection`, spec `map-download-infrastructure`).
 */
interface RepositoryFetcher {

    /**
     * Fetch [url] as text.
     *
     * @return the body, or the reason the fetch failed
     */
    suspend fun text(url: String): TextFetch

    /**
     * Stream [url], handing every chunk to [onBytes] (buffer, length) so the caller can write and
     * checksum it in one pass.
     *
     * @return null on success — the caller verifies the content — or the reason the fetch failed
     */
    suspend fun download(url: String, onBytes: (ByteArray, Int) -> Unit): RepositoryFailure?
}

/**
 * Thrown by a byte consumer that wants the transfer aborted, i.e. a cancellation.
 *
 * A [RepositoryFetcher] implementation must let this through instead of reporting it as a transfer
 * failure, so a cancelled download stays a cancellation and not an error (spec
 * `map-repository-source` — "Cancel during download").
 */
class RepositoryTransferCancelled : RuntimeException()

/** Outcome of a database download. */
sealed interface DownloadOutcome {

    /** Every planned file was fetched and verified. */
    data object Completed : DownloadOutcome

    /** The download was cancelled; the target directory was removed. */
    data object Cancelled : DownloadOutcome

    /** The download failed; the target directory was removed. */
    data class Failed(val failure: RepositoryFailure) : DownloadOutcome
}
