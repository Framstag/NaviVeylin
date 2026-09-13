package com.naviveylin.navigation

import com.naviveylin.core.DrivingModeProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-module singleton implementation of [DrivingModeProvider]: OR-combined
 * per-surface free-driving flags with retain-on-death semantics. See the
 * interface contract.
 */
@Singleton
class DrivingModeProviderImpl @Inject constructor() : DrivingModeProvider {

    private val _freeDrivingActive = MutableStateFlow(false)
    override val freeDrivingActive: StateFlow<Boolean> = _freeDrivingActive.asStateFlow()

    /** Surfaces currently reporting free-driving, guarded by [setFreeDriving]'s lock. */
    private val activeDrivers = mutableSetOf<String>()

    @Synchronized
    override fun setFreeDriving(surface: String, active: Boolean) {
        if (active) {
            activeDrivers.add(surface)
        } else {
            activeDrivers.remove(surface)
        }
        val combined = activeDrivers.isNotEmpty()
        if (_freeDrivingActive.value != combined) {
            _freeDrivingActive.value = combined
        }
    }
}
