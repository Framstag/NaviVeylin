package com.naviveylin.auto

import android.os.Handler
import android.os.Looper

/**
 * Runs a car template refresh on the main thread (spec: car-host-fault-isolation —
 * Template invalidation is main-thread only; design D4).
 *
 * The car-app library requires `Screen.invalidate()` on the main thread (it races
 * `onGetTemplate` otherwise), but the condition that needs a refresh is observed
 * off the main thread in one important case: the map renderer reports a surface
 * failure from its render thread. The screen owns the marshalling — it already lives
 * on `Dispatchers.Main` — so every screen routes those refreshes through here
 * instead of calling `invalidate()` directly from the reporting thread.
 */
internal fun postTemplateRefresh(refresh: () -> Unit) {
    if (Looper.myLooper() === Looper.getMainLooper()) {
        refresh()
        return
    }
    Handler(Looper.getMainLooper()).post(refresh)
}
