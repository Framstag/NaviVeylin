package com.naviveylin.auto

/**
 * Applies the host's day/night state to the native client's stylesheet
 * `daylight` flag, once per distinct value. The host pushes configuration
 * changes (tunnel entry, dusk) and both car screens re-apply on every pass,
 * so the dedupe avoids reloading the same variant on every collection. On a
 * failed push (native keeps the previous variant) the marker resets so the
 * next apply retries. A caller that knows the earlier push may have been
 * dropped (warmup race, stylesheet reload) passes `force` instead of
 * defeating the dedupe for good. Callers run [apply] off the main thread —
 * the native side reloads the stylesheet on its DB thread.
 */
internal class CarDaylightApplier(
    private val setDaylight: (Boolean) -> Boolean
) {
    @Volatile
    private var lastApplied: Boolean? = null

    /**
     * @param force re-push a value the native side may have dropped (a stylesheet was
     *   just (re)loaded, or the DB became ready after an earlier push). The periodic
     *   settings re-read must NOT force: forcing there reloaded the variant and forced
     *   a full render every re-read (spec: car-host-fault-isolation — Bounded periodic
     *   render work; design D5)
     * @return true when the variant is active (already applied, or just pushed)
     */
    fun apply(dark: Boolean, force: Boolean = false): Boolean {
        if (!force && lastApplied == dark) return true
        val ok = setDaylight(dark)
        lastApplied = if (ok) dark else null
        return ok
    }

    /**
     * Whether [apply] with these arguments would push. Pure, so a caller can decide
     * before invalidating the rendered frame.
     */
    fun needsPush(dark: Boolean, force: Boolean = false): Boolean = force || lastApplied != dark
}
