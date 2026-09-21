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
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Android Auto overspeed-warning delta picker (spec: auto-map-layout —
 * "Overspeed delta presented as a value picker"): lists every whole km/h
 * from 0 to 30 inclusive, marks the currently configured delta, and persists
 * the selection through [AutoSettingsProvider] to the same shared settings
 * storage the phone app reads — the delta is global for Android Auto and the
 * phone (default 5, both surfaces warn at `current >= max + delta`).
 */
class OverspeedDeltaPickerScreen(
    carContext: CarContext,
    /**
     * Shared-settings provider; defaults to the Hilt-resolved instance.
     * Tests inject a fake via the primary constructor.
     */
    private val settingsProvider: AutoSettingsProvider = settingsProviderFor(carContext)
) : Screen(carContext) {

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

    override fun onGetTemplate(): ListTemplate = carListTemplate(carContext, ::buildTemplate)

    /** Template body; guarded by [carListTemplate] (spec: car-host-fault-isolation). */
    private fun buildTemplate(): ListTemplate {
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
            val builder = ItemList.Builder()
            (0..30).forEach { delta ->
                builder.addItem(
                    Row.Builder()
                        .setTitle(deltaRowTitle(delta, delta == current.overspeedWarningDeltaKmh))
                        .setOnClickListener { onSelect(delta) }
                        .build()
                )
            }
            builder.build()
        }

        return ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.overspeed_warning))
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .setSingleList(itemList)
            .build()
    }

    /** True while a persist is in flight — rejects a second tap (double-pop guard). */
    private var saveInFlight = false

    /**
     * Persist the chosen delta to the shared settings, then return to the
     * preferences screen — the pop runs only AFTER the write completed, so
     * dismissing the picker can never drop the selection (spec: auto-map-layout
     * — "Anchor selection survives immediate dismissal"). A failed save keeps
     * the picker open with the guard's error/retry row instead of silently
     * losing the value.
     */
    internal fun onSelect(delta: Int) {
        if (saveInFlight) return
        saveInFlight = true
        scope.launch {
            val saved = runCatching { persistSelection(delta) }.isSuccess
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
     * Persist [delta] to the shared settings through [AutoSettingsProvider];
     * split from [onSelect]'s screen stack pop so the selection can be
     * verified in unit tests, where the screen is not attached to a host.
     */
    internal suspend fun persistSelection(delta: Int) {
        val current = settings ?: return
        settings = current.copy(overspeedWarningDeltaKmh = delta)
        settingsProvider.save(settings!!)
    }

    /** Row title: the delta value plus a "(current)" marker on the active one. */
    internal fun deltaRowTitle(delta: Int, selected: Boolean): String =
        "$delta km/h" + if (selected) " (current)" else ""

    private companion object {
        private const val TAG = "OverspeedDeltaPickerScreen"

        fun settingsProviderFor(carContext: CarContext): AutoSettingsProvider =
            EntryPointAccessors.fromApplication(
                carContext.applicationContext,
                AutoEntryPoint::class.java
            ).autoSettingsProvider()
    }
}
