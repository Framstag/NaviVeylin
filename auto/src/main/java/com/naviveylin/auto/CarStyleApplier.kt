package com.naviveylin.auto

/**
 * Applies the shared map style to the renderer's native client, once per
 * distinct value. Settings are re-read periodically on both car screens, so
 * the dedupe avoids reloading the same stylesheet on every pass. On a failed
 * load (native keeps the previous style) the marker resets so the next apply
 * retries, and [onLoadFailed] is called once per failed attempt so the screen
 * can surface the failure (spec: map-styles — "Car reports the failure";
 * change `fix-stylesheet-load-crash`, tasks 3.1/3.2). Callers run [apply] off
 * the main thread — `loadStyleSheet` blocks on the native DB thread.
 */
internal class CarStyleApplier(
    /**
     * Called with the requested style name after a load that did not succeed,
     * exactly once per attempt. The message text comes from the shared
     * `MapStyleLoadReporter` seam, so the car shows the same wording as the
     * phone.
     */
    private val onLoadFailed: (requestedStyle: String) -> Unit = {},
    private val loadStyle: (String) -> Boolean
) {
    private var lastApplied: String? = null

    /** @return true when the style is active (already applied or just loaded). */
    fun apply(styleSheet: String): Boolean {
        if (lastApplied == styleSheet) return true
        val ok = loadStyle(styleSheet)
        lastApplied = if (ok) styleSheet else null
        if (!ok) {
            onLoadFailed(styleSheet)
        }
        return ok
    }
}
