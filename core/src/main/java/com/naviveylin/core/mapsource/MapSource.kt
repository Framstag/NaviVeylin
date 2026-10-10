package com.naviveylin.core.mapsource

/** The family a map source belongs to. */
enum class MapSourceKind {

    /** The provider the app ships with (its listing is a provider listing, not a repository index). */
    BUILT_IN_PROVIDER,

    /** A libosmscout mapgen repository, addressed by its base URL. */
    REPOSITORY
}

/**
 * A source map data can be downloaded from.
 *
 * The built-in provider's own configuration (its base URI and listing template) belongs to the app;
 * this type is only the identity the app, the storage layer and the marker file agree on
 * (spec `map-source-selection`).
 */data class MapSource(
    val kind: MapSourceKind,
    /** Base URL of a repository source; empty for the built-in provider. */
    val baseUrl: String = ""
) {

    /** True when this source is a repository, whose listing is a region index. */
    val isRepository: Boolean
        get() = kind == MapSourceKind.REPOSITORY

    companion object {

        /** The provider the app ships with. */
        val BuiltInProvider = MapSource(MapSourceKind.BUILT_IN_PROVIDER)

        /**
         * A repository source.
         *
         * @param baseUrl the repository's base URL; a trailing slash is not significant
         */
        fun repository(baseUrl: String): MapSource =
            MapSource(MapSourceKind.REPOSITORY, RepositoryUrlPlanner.normaliseBaseUrl(baseUrl))
    }
}
