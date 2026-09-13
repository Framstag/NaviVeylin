package com.naviveylin.ui.about

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naviveylin.core.DiagnosticsLog
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What the license screen shows.
 *
 * The inventory is immutable for the lifetime of the build, so it is read once
 * per screen and never refreshed; only the selected component's text changes.
 */
sealed interface LicensesUiState {
    data object Loading : LicensesUiState
    data class Content(
        val appVersion: String?,
        val components: List<LicenseComponent>,
        val selected: SelectedLicense? = null,
    ) : LicensesUiState

    data class Error(val message: String) : LicensesUiState
}

/**
 * The component whose license the user opened.
 *
 * Exactly one of [text] and [linkUrl] is set: a license whose terms are not
 * distributed with the application is offered as a link rather than as an empty
 * text pane.
 */
data class SelectedLicense(
    val component: LicenseComponent,
    val text: String? = null,
    val linkUrl: String? = null,
)

/**
 * Loads the generated license inventory for the About dialog's license screen.
 *
 * Reads happen on the source's IO dispatcher; this class only owns state and
 * cancellation via [viewModelScope].
 */
@HiltViewModel
class LicensesViewModel @Inject constructor(
    private val source: LicenseInventorySource,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LicensesUiState>(LicensesUiState.Loading)
    val uiState: StateFlow<LicensesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = try {
                val inventory = source.loadInventory()
                LicensesUiState.Content(
                    appVersion = inventory.appVersion,
                    components = inventory.components,
                )
            } catch (e: Exception) {
                DiagnosticsLog.log(TAG, "License inventory unavailable: ${e.message}")
                LicensesUiState.Error(e.message ?: "unknown error")
            }
        }
    }

    /** Opens one component: its license text, or the link to its terms. */
    fun select(component: LicenseComponent) {
        val current = _uiState.value as? LicensesUiState.Content ?: return
        val textFile = component.textFile
        if (textFile == null) {
            _uiState.value = current.copy(
                selected = SelectedLicense(component = component, linkUrl = component.licenseUrl)
            )
            return
        }
        _uiState.value = current.copy(selected = SelectedLicense(component = component, text = null))
        viewModelScope.launch {
            val loaded = try {
                source.loadText(textFile)
            } catch (e: Exception) {
                DiagnosticsLog.log(TAG, "License text $textFile unavailable: ${e.message}")
                null
            }
            val state = _uiState.value as? LicensesUiState.Content ?: return@launch
            _uiState.value = state.copy(
                selected = SelectedLicense(component = component, text = loaded)
            )
        }
    }

    /** Closes the license detail view, keeping the list. */
    fun clearSelection() {
        val current = _uiState.value as? LicensesUiState.Content ?: return
        _uiState.value = current.copy(selected = null)
    }

    private companion object {
        const val TAG = "LicensesViewModel"
    }
}
