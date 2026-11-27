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
import kotlinx.coroutines.launch

/**
 * One settings topic of the car's Preferences (spec: `auto/preferences` — "A topic opens its
 * rows"): a single [ListTemplate] of that topic's car-usable rows with their current values.
 *
 * A boolean row flips on selection; a finite value set opens [PreferenceChoiceScreen] with its
 * options and the current one marked; the overspeed delta and the two vehicle anchors keep their
 * own pickers. No row is walked by repeated taps (spec: `auto/preferences` — "A value is chosen,
 * never only cycled").
 *
 * The screen reads the settings itself and re-reads them on re-visibility, so a value picked on a
 * pushed screen appears on the rows when it pops back (spec: `auto/preferences` — "Screen reflects
 * saved value"). It carries the same render recovery as the root screen (spec: `auto/preferences`
 * — "Settings screens never wedge on a loading placeholder"), one level deeper.
 */
class PreferencesTopicScreen(
    carContext: CarContext,
    /** Which topic this screen shows. */
    internal val topic: PreferenceTopic,
    private val settingsProvider: AutoSettingsProvider = settingsProviderFor(carContext),
    private val stylesLoader: () -> List<String> = { BundledMapStyles.USER_SELECTABLE },
    private val onDarkModeChanged: (String) -> Unit = {}
) : Screen(carContext) {

    private val scope = carScreenScope("PreferencesTopicScreen")

    private var settings: AutoSettings? = null
    private var loaded = false

    /**
     * Render-recovery for the async settings load (spec: auto/preferences — "Settings screens never
     * wedge on a loading placeholder"): watchdog re-invalidate, guarded invalidate, capped recovery
     * ending in the error row, re-render on re-visibility.
     */
    private val guard = SettingsLoadGuard(
        scope = scope,
        invalidate = { invalidate() },
        contentLoaded = { loaded && settings != null }
    )

    init {
        enableBackNavigation()
        loadSettings()
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

    override fun onGetTemplate(): ListTemplate = carListTemplate(carContext, ::buildTemplate)

    /** Template body; guarded by [carListTemplate] (spec: car-host-fault-isolation). */
    private fun buildTemplate(): ListTemplate {
        val current = settings
        val itemList = if (guard.failed) {
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
            PreferencesScreenMapper.rowsFor(topic, current, stylesLoader()).forEach { row ->
                builder.addItem(
                    Row.Builder()
                        .setTitle(row.title)
                        .addText(row.valueText)
                        .setOnClickListener { onRowSelected(row) }
                        .build()
                )
            }
            builder.build()
        }

        return ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(topic.titleRes))
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .setSingleList(itemList)
            .build()
    }

    /** Route a row's selection to the control kind it declares (design D5/D6). */
    internal fun onRowSelected(row: PreferenceRow) {
        when (row.control) {
            PreferenceControl.SWITCH -> onToggle(row.key)
            PreferenceControl.CHOICE -> armScreenPush(
                carContext, scope, "PreferenceChoiceScreen (${row.key})"
            ) {
                PreferenceChoiceScreen(
                    carContext = carContext,
                    key = row.key,
                    title = row.title,
                    choices = row.choices,
                    currentValue = row.currentValue,
                    settingsProvider = settingsProvider,
                    stylesLoader = stylesLoader,
                    onDarkModeChanged = onDarkModeChanged
                )
            }
            PreferenceControl.PICKER -> openPicker(row.key)
        }
    }

    private fun openPicker(key: String) {
        when (key) {
            PreferencesScreenMapper.KEY_OVERSPEED_DELTA ->
                armScreenPush(carContext, scope, "OverspeedDeltaPickerScreen") {
                    OverspeedDeltaPickerScreen(carContext, settingsProvider)
                }
            PreferencesScreenMapper.KEY_ROUTING_ANCHOR ->
                armScreenPush(carContext, scope, "VehicleAnchorPickerScreen (routing)") {
                    VehicleAnchorPickerScreen(
                        carContext, settingsProvider,
                        VehicleAnchorPickerScreen.Mode.ROUTING
                    )
                }
            PreferencesScreenMapper.KEY_FREE_DRIVING_ANCHOR ->
                armScreenPush(carContext, scope, "VehicleAnchorPickerScreen (free driving)") {
                    VehicleAnchorPickerScreen(
                        carContext, settingsProvider,
                        VehicleAnchorPickerScreen.Mode.FREE_DRIVING
                    )
                }
        }
    }

    /** Flip a boolean row and persist it (spec: auto/preferences — "Toggle updates preference"). */
    internal fun onToggle(key: String) {
        val current = settings ?: return
        val updated = PreferencesScreenMapper.toggle(current, key, stylesLoader())
        if (updated == current) return
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
        private const val TAG = "PreferencesTopicScreen"

        fun settingsProviderFor(carContext: CarContext): AutoSettingsProvider =
            EntryPointAccessors.fromApplication(
                carContext.applicationContext,
                AutoEntryPoint::class.java
            ).autoSettingsProvider()
    }
}