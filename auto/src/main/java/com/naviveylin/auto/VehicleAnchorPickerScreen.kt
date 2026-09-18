package com.naviveylin.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.AutoSettings
import com.naviveylin.core.AutoSettingsProvider
import com.naviveylin.core.VehicleAnchorPosition
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Android Auto vehicle anchor picker (spec: auto-map-layout — "Anchor picker
 * shows the 5×3 grid"): lists all 15 presets of the 5×3 grid
 * ([VehicleAnchorPosition], horizontal 10/30/50/70/90% × vertical 10/50/90%),
 * marks the currently configured anchor, and persists the selection through
 * [AutoSettingsProvider] to the same shared settings storage the phone app
 * reads — the anchor is global for Android Auto and the phone.
 *
 * [mode] selects which of the two per-mode anchors (routing vs free driving)
 * the screen edits; the labels come from the shared enum so phone and car
 * pickers stay in label parity (guidelines/UI.md).
 */
class VehicleAnchorPickerScreen(
    carContext: CarContext,
    /**
     * Shared-settings provider; defaults to the Hilt-resolved instance.
     * Tests inject a fake via the primary constructor.
     */
    private val settingsProvider: AutoSettingsProvider = settingsProviderFor(carContext),
    /** Which per-mode anchor this picker edits. */
    private val mode: Mode = Mode.ROUTING
) : Screen(carContext) {

    /** The anchor being edited: routing or free driving. */
    enum class Mode { ROUTING, FREE_DRIVING }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var settings: AutoSettings? = null

    /**
     * Render-recovery for the async settings load (spec: auto/preferences —
     * "Settings screens never wedge on a loading placeholder"): watchdog
     * re-invalidate, guarded invalidate, capped recovery ending in the error
     * row, re-render on re-visibility.
     */
    private val guard = SettingsLoadGuard(
        scope = scope,
        invalidate = { invalidate() },
        contentLoaded = { settings != null }
    )

    init {
        enableBackNavigation()
        loadSettings()
        observeSettingsLifecycle(scope, guard)
    }

    private fun loadSettings() {
        guard.onLoadStarted()
        scope.launch {
            val result = runCatching { settingsProvider.load() }
            settings = result.getOrNull()
            if (result.isFailure) {
                Log.w(TAG, "settings load failed", result.exceptionOrNull())
                guard.onLoadFailed()
            } else {
                guard.onLoadSucceeded()
            }
        }
    }

    /** Error-row Retry action: start a fresh load cycle (guard resets its budget). */
    internal fun onRetry() = loadSettings()

    override fun onGetTemplate(): ListTemplate {
        val current = settings
        val itemList = if (guard.failed) {
            // Recovery exhausted (or the load itself failed): explicit error
            // row with Retry instead of an inert infinite placeholder.
            ItemList.Builder()
                .addItem(Row.Builder().setTitle(carContext.getString(R.string.settings_unavailable)).build())
                .addItem(
                    Row.Builder()
                        .setTitle(carContext.getString(R.string.retry))
                        .setOnClickListener { onRetry() }
                        .build()
                )
                .build()
        } else if (current == null) {
            ItemList.Builder()
                .addItem(Row.Builder().setTitle(carContext.getString(R.string.loading)).build())
                .build()
        } else {
            val currentId = currentId(current)
            val builder = ItemList.Builder()
            VehicleAnchorPosition.entries.forEach { anchor ->
                builder.addItem(
                    Row.Builder()
                        .setTitle(anchorRowTitle(anchor, anchor.id == currentId))
                        .setOnClickListener { onSelect(anchor.id) }
                        .build()
                )
            }
            builder.build()
        }

        return ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(headerTitleRes()))
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .setSingleList(itemList)
            .build()
    }

    private fun currentId(settings: AutoSettings): String =
        if (mode == Mode.ROUTING) settings.routingAnchorId else settings.freeDrivingAnchorId

    private fun headerTitleRes(): Int =
        if (mode == Mode.ROUTING) R.string.vehicle_position_routing else R.string.vehicle_position_free_driving

    /** True while a persist is in flight — rejects a second tap (double-pop guard). */
    private var saveInFlight = false

    /**
     * Persist the chosen anchor id to the shared settings, then return to the
     * preferences screen — the pop runs only AFTER the write completed, so
     * dismissing the picker can never drop the selection (spec: auto-map-layout
     * — "Anchor selection survives immediate dismissal"). A failed save keeps
     * the picker open with the guard's error/retry row instead of silently
     * losing the value.
     */
    internal fun onSelect(anchorId: String) {
        if (saveInFlight) return
        saveInFlight = true
        scope.launch {
            val saved = runCatching { persistSelection(anchorId) }.isSuccess
            if (saved) {
                finishSelection()
            } else {
                saveInFlight = false
                Log.w(TAG, "persist failed — keeping picker open")
                guard.onLoadFailed()
            }
        }
    }

    /** Pop back to the preferences screen after the selection is persisted. */
    internal fun finishSelection() {
        screenManager.pop()
    }

    /**
     * Persist [anchorId] to the shared settings through [AutoSettingsProvider];
     * split from [onSelect]'s screen stack pop so the selection can be
     * verified in unit tests, where the screen is not attached to a host.
     */
    internal suspend fun persistSelection(anchorId: String) {
        // Freeze the CAR's own value (does not touch the phone's anchors): the car
        // inherits the phone's anchor only until the driver picks one here
        // (spec: auto-map-layout — "Anchor selection persisted for Android Auto").
        if (mode == Mode.ROUTING) {
            settingsProvider.saveCarAnchor(routingAnchorId = anchorId)
        } else {
            settingsProvider.saveCarAnchor(freeDrivingAnchorId = anchorId)
        }
        settings = settings?.let { current ->
            if (mode == Mode.ROUTING) current.copy(routingAnchorId = anchorId)
            else current.copy(freeDrivingAnchorId = anchorId)
        }
    }

    /** Row title: the preset label plus a "(current)" marker on the active one. */
    internal fun anchorRowTitle(anchor: VehicleAnchorPosition, selected: Boolean): String =
        anchor.label + if (selected) " (current)" else ""

    private companion object {
        private const val TAG = "VehicleAnchorPickerScreen"

        fun settingsProviderFor(carContext: CarContext): AutoSettingsProvider =
            EntryPointAccessors.fromApplication(
                carContext.applicationContext,
                AutoEntryPoint::class.java
            ).autoSettingsProvider()
    }
}
