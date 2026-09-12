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

    /** Sensor classification; null = sensor inactive or unavailable. */
    private val _sensorDark = MutableStateFlow<Boolean?>(null)

    /** Ambient light sensor sensitivity option (persisted via [SettingsStorage]). */
    private val _sensorSensitivity = MutableStateFlow(AmbientLightSensitivity.OFF)

    /** Ambient light sensor sensitivity option state. */
    val sensorSensitivity: StateFlow<AmbientLightSensitivity> = _sensorSensitivity.asStateFlow()

    /** Resolved dark presentation (preference × environment). */
    val isDarkPresentation: StateFlow<Boolean> =
        combine(_preference, _environmentDark, _sensorDark, _sensorSensitivity, ::resolveDarkPresentation)
            .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)

    /**
     * Feed the current environment dimming signal (system night mode today;
     * car environment later). Called from composition.
     */
    fun setEnvironmentDark(dark: Boolean) {
        _environmentDark.value = dark
    }

    /**
     * Feed the ambient light sensor classification; null = sensor inactive or
     * unavailable (falls back to the system signal).
     */
    fun setSensorDark(dark: Boolean?) {
        _sensorDark.value = dark
    }

    /**
     * Set the ambient light sensor sensitivity (OFF disables the sensor as the
     * environment source) and persist it.
     */
    fun setSensorSensitivity(sensitivity: AmbientLightSensitivity) {
        if (_sensorSensitivity.value == sensitivity) return
        _sensorSensitivity.value = sensitivity
        scope.launch {
            val current = settingsStorage.load()
            settingsStorage.save(current.copy(ambientLightSensitivity = sensitivity))
        }
    }

    /** Restore the persisted sensor sensitivity (e.g. after settings load). */
    fun restoreSensorSensitivity(sensitivity: AmbientLightSensitivity) {
        _sensorSensitivity.value = sensitivity
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
 * Pure resolution: ON always dark, OFF always light, AUTOMATIC follows the
 * environment. When the ambient light sensor option is enabled and a sensor
 * classification is available, the sensor wins over the system signal;
 * otherwise the system signal is used.
 */
fun resolveDarkPresentation(
    preference: DarkModePreference,
    environmentDark: Boolean,
    sensorDark: Boolean? = null,
    sensorSensitivity: AmbientLightSensitivity = AmbientLightSensitivity.OFF
): Boolean = when (preference) {
    DarkModePreference.ON -> true
    DarkModePreference.OFF -> false
    DarkModePreference.AUTOMATIC ->
        if (sensorSensitivity != AmbientLightSensitivity.OFF && sensorDark != null) sensorDark else environmentDark
}
