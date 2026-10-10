package com.naviveylin.core.mapsource

/**
 * Why a repository request or document could not be used.
 *
 * The protocol layer reports *what* went wrong and carries no wording: the surface that shows the
 * outcome turns a case into a translatable message (`:core` owns no wording — spec `i18n-l10n`,
 * `guidelines/UI.md` §10).
 */
sealed interface RepositoryFailure {

    /** The document is not valid JSON. */
    data object MalformedDocument : RepositoryFailure

    /** The document is JSON but not the kind of document that was requested. */
    data object WrongDocumentKind : RepositoryFailure

    /** The document carries a schema version this client does not support. */
    data class UnsupportedSchema(val found: Int?) : RepositoryFailure

    /** The server answered with a status that is not a success. */
    data class HttpStatus(val status: Int) : RepositoryFailure

    /** The request failed before a response arrived. */
    data class TransportFailed(val cause: String?) : RepositoryFailure

    /**
     * The platform's cleartext policy refused the request, so it was never sent.
     *
     * A shipped build permits cleartext for the repository transport (spec
     * `map-download-infrastructure` — "Shipped builds permit cleartext repository transport"), so
     * this case is what a *tightened* policy reports instead of a generic transport failure.
     */
    data object CleartextBlocked : RepositoryFailure

    /**
     * The base URL could not be parsed into a request URL.
     *
     * Distinct from [TransportFailed] on purpose: nothing was sent, and the fix is in the URL the
     * user typed, not in the network (spec `map-download-infrastructure` — "A base URL that cannot
     * be parsed is reported as an unusable URL").
     */
    data object MalformedUrl : RepositoryFailure

    /** The requested database is not published for this client's database format version. */
    data object DatabaseNotPublished : RepositoryFailure

    /** A downloaded file did not match the size or checksum its metadata states. */
    data class VerificationFailed(val fileName: String) : RepositoryFailure

    /** The database's metadata names a different database format version than this client reads. */
    data class DatabaseVersionMismatch(val found: Int?) : RepositoryFailure

    /** The database's metadata names no data files, so the database cannot be installed. */
    data object NoFilesPublished : RepositoryFailure
}
