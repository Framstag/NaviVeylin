package com.naviveylin.navigation

import com.naviveylin.core.CarSessionPresence
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-scoped [CarSessionPresence] (spec: `car-session-presence`).
 *
 * Published by the car session's start/destroy (through
 * [com.naviveylin.core.AutoEntryPoint.carSessionPresence]) and consumed by the
 * phone map UI, which resolves the same singleton. In-memory only: nothing is
 * written to disk, so a restarted process always begins inactive.
 *
 * Diagnostics: the transition is recorded by the car session that publishes it
 * (`SessionLog`, the session's own stream) — this seam deliberately has no Android
 * or logging dependency, the same shape as `NativeTileDataCache`, so it is testable
 * as a plain JVM class with no Robolectric sandbox.
 */
@Singleton
class CarSessionPresenceImpl @Inject constructor() : CarSessionPresence {

    private val _active = MutableStateFlow(false)
    override val active: StateFlow<Boolean> = _active.asStateFlow()

    override fun setActive(active: Boolean) {
        if (_active.value == active) return
        _active.value = active
    }
}
