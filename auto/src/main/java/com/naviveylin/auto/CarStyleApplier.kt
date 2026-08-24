package com.naviveylin.auto

/**
 * Applies the shared map style to the renderer's native client, once per
 * distinct value. Settings are re-read periodically on both car screens, so
 * the dedupe avoids reloading the same stylesheet on every pass. On a failed
 * load (native keeps the previous style) the marker resets so the next apply
 * retries. Callers run [apply] off the main thread — `loadStyleSheet` blocks
 * on the native DB thread.
 */
internal class CarStyleApplier(
    private val loadStyle: (String) -> Boolean
) {
    private var lastApplied: String? = null

    /** @return true when the style is active (already applied or just loaded). */
    fun apply(styleSheet: String): Boolean {
        if (lastApplied == styleSheet) return true
        val ok = loadStyle(styleSheet)
        lastApplied = if (ok) styleSheet else null
        return ok
    }
}
