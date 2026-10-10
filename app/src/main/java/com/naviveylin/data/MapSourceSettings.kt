package com.naviveylin.data

import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.core.mapsource.MapSource
import com.naviveylin.core.mapsource.MapSourceKind

/**
 * The map source the persisted settings select.
 *
 * A stored kind this build does not know, or the repository source without a base URL, falls back to
 * the built-in provider and records the stored value in the diagnostics stream (spec
 * `map-source-selection` — "Unusable stored selection falls back"). The line carries the stored value,
 * never a coordinate (`guidelines/Logging.md` §2).
 */
fun AppSettings.selectedMapSource(): MapSource {
    val storedKind = MapSourceKind.entries.firstOrNull { it.name == mapSourceKind }
    if (storedKind == null) {
        DiagnosticsLog.log(
            TAG,
            "map source unknown value=$mapSourceKind fallback=${MapSourceKind.BUILT_IN_PROVIDER.name}"
        )
        return MapSource.BuiltInProvider
    }
    if (storedKind != MapSourceKind.REPOSITORY) {
        return MapSource.BuiltInProvider
    }
    if (mapRepositoryUrl.isBlank()) {
        DiagnosticsLog.log(
            TAG,
            "repository source without a base URL fallback=${MapSourceKind.BUILT_IN_PROVIDER.name}"
        )
        return MapSource.BuiltInProvider
    }
    return MapSource.repository(mapRepositoryUrl)
}

/** The persisted field values that select [source]; the inverse of [selectedMapSource]. */
fun AppSettings.withSelectedMapSource(source: MapSource): AppSettings = copy(
    mapSourceKind = source.kind.name,
    mapRepositoryUrl = if (source.isRepository) source.baseUrl else mapRepositoryUrl
)

private const val TAG = "MapSourceSelection"
