package com.naviveylin.auto

/**
 * Applies the host's day/night state to the native client's stylesheet
 * `daylight` flag, once per distinct value. The host pushes configuration
 * changes (tunnel entry, dusk) and both car screens re-apply on every pass,
 * so the dedupe avoids reloading the same variant on every collection. On a
 * failed push (native keeps the previous variant) the marker resets so the
 * next apply retries. A stylesheet load needs no re-application afterwards:
 * the native load installs the enabled flags with the newly selected style
 * (spec: map-styles — A style switch loads the stylesheet set once). A caller
 * that cannot tell whether the native side would drop the push defers its first
 * one instead of forcing a repeat (see `MapScreen`/`NavigationScreen`). Callers
 * run [apply] off the main thread — the native side reloads the stylesheet on
 * its DB thread.
 */
internal class CarDaylightApplier(
    private val setDaylight: (Boolean) -> Boolean
) {
    @Volatile
    private var lastApplied: Boolean? = null

    /**
     * @return true when the variant is active (already applied, or just pushed)
     */
    fun apply(dark: Boolean): Boolean {
        if (lastApplied == dark) return true
        val ok = setDaylight(dark)
        lastApplied = if (ok) dark else null
        return ok
    }

    /**
     * Whether [apply] with these arguments would push. Pure, so a caller can decide
     * before invalidating the rendered frame.
     */
    fun needsPush(dark: Boolean): Boolean = lastApplied != dark
}
