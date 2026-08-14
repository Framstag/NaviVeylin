package com.naviveylin.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolve the dark presentation state of the app from the three-state preference
 * and the environment.
 *
 * Single extension point for future environment sources (e.g. car environment
 * dimming via CarContext.isDarkMode()): feed them through [setEnvironmentDark].
 */
@Singleton
class DarkModeController @Inject constructor(
    private val settingsStorage: SettingsStorage
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _preference = MutableStateFlow(DarkModePreference.AUTOMATIC)
    /** Current dark mode preference (On / Off / Automatic). */
    val preference: StateFlow<DarkModePreference> = _preference.asStateFlow()

    private val _environmentDark = MutableStateFlow(false)

    /** Resolved dark presentation (preference × environment). */
    val isDarkPresentation: StateFlow<Boolean> =
        combine(_preference, _environmentDark, ::resolveDarkPresentation)
            .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)

    /**
     * Feed the current environment dimming signal (system night mode today;
     * car environment later). Called from composition.
     */
    fun setEnvironmentDark(dark: Boolean) {
        _environmentDark.value = dark
    }

    /** Restore the persisted preference (e.g. after settings load). */
    fun restorePreference(preference: DarkModePreference) {
        _preference.value = preference
    }

    /** Change the preference and persist it. */
    fun setPreference(preference: DarkModePreference) {
        if (_preference.value == preference) return
        _preference.value = preference
        scope.launch {
            val current = settingsStorage.load()
            settingsStorage.save(current.copy(darkMode = preference))
        }
    }
}

/**
 * Pure resolution: ON always dark, OFF always light, AUTOMATIC follows environment.
 */
fun resolveDarkPresentation(
    preference: DarkModePreference,
    environmentDark: Boolean
): Boolean = when (preference) {
    DarkModePreference.ON -> true
    DarkModePreference.OFF -> false
    DarkModePreference.AUTOMATIC -> environmentDark
}
