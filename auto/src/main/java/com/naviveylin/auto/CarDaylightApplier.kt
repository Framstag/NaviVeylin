package com.naviveylin.auto

/**
 * Applies the host's day/night state to the native client's stylesheet
 * `daylight` flag, once per distinct value. The host pushes configuration
 * changes (tunnel entry, dusk) and both car screens re-apply on every pass,
 * so the dedupe avoids reloading the same variant on every collection. On a
 * failed push (native keeps the previous variant) the marker resets so the
 * next apply retries. Callers run [apply] off the main thread — the native
 * side reloads the stylesheet on its DB thread.
 */
internal class CarDaylightApplier(
    private val setDaylight: (Boolean) -> Boolean
) {
    @Volatile
    private var lastApplied: Boolean? = null

    /**
     * @return true when the variant is active (already applied or just pushed).
     */
    fun apply(dark: Boolean): Boolean {
        if (lastApplied == dark) return true
        val ok = setDaylight(dark)
        lastApplied = if (ok) dark else null
        return ok
    }

    /**
     * Forget the last applied value so the next [apply] pushes unconditionally.
     * Used when the DB may have dropped an earlier push (startup warmup race):
     * the style-load / surface-creation re-push must not be deduped away.
     */
    fun reset() {
        lastApplied = null
    }
}
