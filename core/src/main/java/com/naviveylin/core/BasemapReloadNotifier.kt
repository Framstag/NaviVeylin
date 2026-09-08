package com.naviveylin.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Signals that the set of loaded map data changed while the app runs — e.g.
 * the basemap was downloaded, updated, or deleted. Consumers invalidate their
 * rendered output on [revision] change (tile-cache clear + forced re-render),
 * mirroring the renderer epoch semantics.
 *
 * A revision counter (not a boolean) coalesces rapid successive events and
 * never misses the latest state: collectors observe the newest revision.
 * App-scoped ([Singleton]); collectors cancel with their own scopes.
 *
 * Spec: basemap-loading — the current view SHALL re-render with the basemap
 * overlay active (or without it, after delete) without an app restart.
 */
@Singleton
class BasemapReloadNotifier @Inject constructor() {

    private val _revision = MutableStateFlow(0L)

    /** Monotonic revision; consumers re-render whenever it changes. */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    /** Notify consumers that map data changed and must be re-rendered. */
    fun bump() {
        _revision.update { it + 1 }
    }
}
