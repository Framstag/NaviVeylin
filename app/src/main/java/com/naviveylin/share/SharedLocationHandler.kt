package com.naviveylin.share

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Carries a shared location from [com.naviveylin.MainActivity] (which receives the intent) to
 * the map screen ([com.naviveylin.ui.map.MapCanvasViewModel], which processes it).
 *
 * Singleton so the outcome survives activity recreation and queues until the map screen is
 * composed (e.g. no maps installed → the map-manager entry screen shows first). Consume-once:
 * [consume] returns the pending outcome and clears the slot so a recreated view model does not
 * reprocess it.
 */
@Singleton
class SharedLocationHandler @Inject constructor() {

    private val _outcome = MutableStateFlow<SharedLocationOutcome?>(null)

    /** The pending share outcome, or null when none is pending. */
    val outcome: StateFlow<SharedLocationOutcome?> = _outcome.asStateFlow()

    /** Queue a share outcome; a later share replaces an earlier pending one. */
    fun submit(outcome: SharedLocationOutcome) {
        _outcome.value = outcome
    }

    /** Return the pending outcome and clear the slot, or null when none. */
    fun consume(): SharedLocationOutcome? {
        val current = _outcome.value
        if (current != null) _outcome.value = null
        return current
    }
}
