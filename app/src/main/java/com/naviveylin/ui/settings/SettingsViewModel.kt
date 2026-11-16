package com.naviveylin.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * The start screen's handle on the settings surface (design D2; spec: `settings-surface`).
 *
 * The start screen shares no state with the map screen, and handing it a map ViewModel to reach the
 * settings would be a map-sized dependency for one list. It takes [SettingsController] instead — the
 * one writer both hosts read — and reads the persisted document once on its start path, so a value
 * the map screen wrote is what the surface shows.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    val controller: SettingsController
) : ViewModel() {

    init {
        viewModelScope.launch { controller.load() }
    }
}
