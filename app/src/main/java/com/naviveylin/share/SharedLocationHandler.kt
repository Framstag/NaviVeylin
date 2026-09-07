package com.naviveylin.share

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Carries a shared location from [MainActivity] (which receives the intent) to
 * the map screen ([MapCanvasViewModel], which processes it).
 *
 * Singleton so the request survives activity recreation and queues until the
 * map screen is composed (e.g. no maps installed → the map-manager entry
 * screen shows first). Consume-once: [consume] returns the pending request and
 * clears the slot so a recreated view model does not reprocess it.
 */
@Singleton
class SharedLocationHandler @Inject constructor() {

    private val _request = MutableStateFlow<SharedLocationRequest?>(null)

    /** The pending shared location, or null when none is pending. */
    val request: StateFlow<SharedLocationRequest?> = _request.asStateFlow()

    /** Queue a shared location; a later share replaces an earlier pending one. */
    fun submit(request: SharedLocationRequest) {
        _request.value = request
    }

    /** Return the pending request and clear the slot, or null when none. */
    fun consume(): SharedLocationRequest? {
        val current = _request.value
        if (current != null) _request.value = null
        return current
    }
}
