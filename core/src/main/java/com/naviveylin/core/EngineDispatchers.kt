package com.naviveylin.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * The dispatchers a component's own work runs on, named as one injectable seam instead of inline
 * `Dispatchers.X` references at the call sites, so a test runs that work on its own scheduler (spec:
 * `navigation-engine` — Engine lifecycle and threading).
 *
 * Immutable: the values are `val`, so the holder carries no state and has no lifecycle of its own.
 */
data class EngineDispatchers(
    /** Compute work that must stay off the main thread (native calls, reroute arithmetic). */
    val compute: CoroutineDispatcher,
    /** Blocking native lookups (map/road queries). */
    val io: CoroutineDispatcher
) {
    companion object {
        /** The production pair: [Dispatchers.Default] for compute, [Dispatchers.IO] for lookups. */
        val Production: EngineDispatchers =
            EngineDispatchers(compute = Dispatchers.Default, io = Dispatchers.IO)
    }
}
