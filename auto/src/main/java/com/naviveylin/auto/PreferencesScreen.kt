package com.naviveylin.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import com.naviveylin.auto.R
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.AutoSettings
import com.naviveylin.core.AutoSettingsProvider
import com.naviveylin.core.BundledMapStyles
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Android Auto preferences screen: lists the shared navigation settings with
 * their current values and lets the driver toggle them. Changes are persisted
 * through [AutoSettingsProvider] to the same settings storage the phone app
 * uses.
 *
 * Uses [ListTemplate] (not [PaneTemplate]): PaneTemplate rows do not support
 * click listeners (car-app constraint `ROW_CONSTRAINTS_PANE`). ListTemplate is
 * the most broadly supported list template across hosts (projection and AAOS).
 */
class PreferencesScreen private constructor(
    carContext: CarContext,
    private val settingsProvider: AutoSettingsProvider,
    private val stylesLoader: () -> List<String>,
    private val onDarkModeChanged: (String) -> Unit = {}
) : Screen(carContext) {

    /** Production path: resolve the provider and the live style list via Hilt. */
    constructor(carContext: CarContext, onDarkModeChanged: (String) -> Unit = {}) : this(
        carContext,
        settingsProviderFor(carContext),
        stylesLoaderFor(carContext),
        onDarkModeChanged
    )

    /** Test path: injected provider and the bundled fallback style list. */
    constructor(
        carContext: CarContext,
        settingsProvider: AutoSettingsProvider,
        onDarkModeChanged: (String) -> Unit = {}
    ) : this(
        carContext,
        settingsProvider,
        { BundledMapStyles.USER_SELECTABLE },
        onDarkModeChanged
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var settings: AutoSettings? = null
    private var loaded = false

    /**
     * Render-recovery for the async settings load (spec: auto/preferences —
     * "Settings screens never wedge on a loading placeholder"): watchdog
     * re-invalidate, guarded invalidate, capped recovery ending in the error
     * row, re-render on re-visibility.
     */
    private val guard = SettingsLoadGuard(
        scope = scope,
        invalidate = { invalidate() },
        contentLoaded = { loaded && settings != null }
    )

    init {
        enableBackNavigation()
        loadSettings()
        // Re-read on re-visibility: a value changed in a pushed picker must
        // appear on the rows when the picker pops back (spec: auto/preferences
        // — "Re-read on re-visibility", auto-map-layout — "Anchor settings
        // reflect the persisted value on re-visibility"), not a stale snapshot.
        observeSettingsLifecycle(scope, guard, onReVisible = ::loadSettings)
    }

    private fun loadSettings() {
        guard.onLoadStarted()
        scope.launch {
            val result = runCatching { settingsProvider.load() }
            settings = result.getOrNull()
            loaded = result.isSuccess
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
        } else if (!loaded || current == null) {
            ItemList.Builder()
                .addItem(Row.Builder().setTitle(carContext.getString(R.string.loading)).build())
                .build()
        } else {
            val builder = ItemList.Builder()
            PreferencesScreenMapper.rows(current).forEach { row ->
                builder.addItem(
                    Row.Builder()
                        .setTitle(row.title)
                        .addText(row.valueText)
                        .setOnClickListener {
                            // The overspeed delta row opens the value picker
                            // (0-30) instead of toggling (spec: auto-map-layout
                            // — Overspeed delta presented as a value picker);
                            // the vehicle position rows open the 5×3 anchor
                            // picker for their mode.
                            if (row.key == PreferencesScreenMapper.KEY_OVERSPEED_DELTA) {
                                screenManager.push(
                                    OverspeedDeltaPickerScreen(carContext, settingsProvider)
                                )
                            } else if (row.key == PreferencesScreenMapper.KEY_ROUTING_ANCHOR) {
                                screenManager.push(
                                    VehicleAnchorPickerScreen(
                                        carContext, settingsProvider,
                                        VehicleAnchorPickerScreen.Mode.ROUTING
                                    )
                                )
                            } else if (row.key == PreferencesScreenMapper.KEY_FREE_DRIVING_ANCHOR) {
                                screenManager.push(
                                    VehicleAnchorPickerScreen(
                                        carContext, settingsProvider,
                                        VehicleAnchorPickerScreen.Mode.FREE_DRIVING
                                    )
                                )
                            } else {
                                onToggle(row.key)
                            }
                        }
                        .build()
                )
            }
            builder.build()
        }

        return ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.preferences))
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .setSingleList(itemList)
            .build()
    }

    internal fun onToggle(key: String) {
        val current = settings ?: return
        val updated = PreferencesScreenMapper.toggle(current, key, stylesLoader())
        settings = updated
        scope.launch {
            settingsProvider.save(updated)
            // Notify the session so the car map re-resolves dark presentation
            // (spec: auto/preferences — dark mode preference applies to car
            // rendering, "Preference change applies live").
            if (key == PreferencesScreenMapper.KEY_DARK_MODE) {
                onDarkModeChanged(updated.darkMode)
            }
            // Guarded refresh: a host failure here must not lose the toggle.
            guard.refreshContent()
        }
    }

    private companion object {
        private const val TAG = "PreferencesScreen"

        fun settingsProviderFor(carContext: CarContext): AutoSettingsProvider =
            EntryPointAccessors.fromApplication(
                carContext.applicationContext,
                AutoEntryPoint::class.java
            ).autoSettingsProvider()

        /** Live style list from the device stylesheet dir; bundled set on failure. */
        fun stylesLoaderFor(carContext: CarContext): () -> List<String> = {
            val entryPoint = EntryPointAccessors.fromApplication(
                carContext.applicationContext,
                AutoEntryPoint::class.java
            )
            runCatching {
                entryPoint.autoClientProvider().client().getAvailableStyleSheets()
            }.getOrDefault(BundledMapStyles.USER_SELECTABLE)
        }
    }
}
