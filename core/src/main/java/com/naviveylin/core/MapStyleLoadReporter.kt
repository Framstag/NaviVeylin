package com.naviveylin.core

/**
 * Reporting seam for map stylesheet load failures (change
 * `fix-stylesheet-load-crash`, design D1).
 *
 * One place turns the client's load outcome into the two side effects the spec
 * requires (spec `map-styles` — "Style load failure is visible on both
 * surfaces"): a diagnostics-log entry and the user-visible message. The message
 * wording lives in the `:core` resources, so the phone and the car surfaces show
 * the same text; each surface decides how to present it (phone map snackbar, car
 * non-blocking notice).
 *
 * The client reports every load path through
 * `OSMScoutClient.wasLastStyleLoadSuccessful()` / `getActiveStyleSheet()`
 * (libosmscout change `client-style-load-resilience`).
 */
object MapStyleLoadReporter {

    /** Tag of the diagnostics-log entries written by this seam. */
    const val TAG = "MapStyleLoad"

    /**
     * Message for a stylesheet that could not be loaded.
     *
     * Names the stylesheet that failed and, when a different style stays in
     * effect, the style the user is still seeing. [activeStyle] is null/empty
     * when no stylesheet ever loaded (nothing is drawn for the affected map).
     * When the active style is the requested one (a style-flag reload of the
     * active stylesheet failed) the message does not repeat it.
     */
    fun failureMessage(
        resolver: StringResolver,
        requestedStyle: String,
        activeStyle: String?
    ): String =
        if (activeStyle.isNullOrEmpty() || activeStyle == requestedStyle) {
            resolver.get(R.string.map_style_load_failed, requestedStyle)
        } else {
            resolver.get(R.string.map_style_load_failed_kept, requestedStyle, activeStyle)
        }

    /**
     * Reports one failed stylesheet load: exactly one diagnostics-log entry plus
     * the message to show. Returns null when the load succeeded, so a caller can
     * use the result directly as "is there something to show".
     *
     * Callers invoke this once per load attempt (spec `map-styles` — "Failure is
     * reported once per attempt").
     */
    fun reportFailure(
        resolver: StringResolver,
        requestedStyle: String,
        activeStyle: String?,
        loadSucceeded: Boolean
    ): String? {
        if (loadSucceeded) {
            return null
        }

        DiagnosticsLog.log(
            TAG,
            "Stylesheet '$requestedStyle' failed to load; active style is " +
                "'${activeStyle?.takeIf { it.isNotEmpty() } ?: "none"}'"
        )

        return failureMessage(resolver, requestedStyle, activeStyle)
    }
}
