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
import com.naviveylin.core.AutoSettingsProvider
import com.naviveylin.core.BundledMapStyles
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

/**
 * The options of one finite-value settings row, as a list that marks the value in force
 * (spec: `auto/preferences` — "A value is chosen, never only cycled"; design D6). Reuses the
 * pattern of [VehicleAnchorPickerScreen]: every permitted value is a row, the current one is
 * marked, and the pick is persisted before the screen pops so dismissing it cannot drop the
 * selection.
 *
 * A failed save keeps the screen open with the recovery row and its Retry action, which re-runs
 * the same pick instead of losing it.
 */
class PreferenceChoiceScreen(
    carContext: CarContext,
    /** The registry key of the row being edited. */
    internal val key: String,
    /** The row's title, shown as this screen's header so the driver knows which value they edit. */
    private val title: String,
    /** Every permitted value, in the row's declared order. */
    internal val choices: List<PreferenceChoice>,
    /** The value in force when the screen opened, marked in the list. */
    internal val currentValue: String,
    private val settingsProvider: AutoSettingsProvider = settingsProviderFor(carContext),
    private val stylesLoader: () -> List<String> = { BundledMapStyles.USER_SELECTABLE },
    private val onDarkModeChanged: (String) -> Unit = {}
) : Screen(carContext) {

    private val scope = carScreenScope("PreferenceChoiceScreen")

    /** True while a persist is in flight — rejects a second tap (double-pop guard). */
    private var saveInFlight = false

    /** The value a failed save was carrying, so its Retry re-runs that pick. */
    private var pending: String? = null

    /**
     * Recovery for the persist (spec: auto/preferences — "Settings screens never wedge on a loading
     * placeholder"): the options are already in hand, so the guard only renders the failure state
     * and the Retry row.
     */
    private val guard = SettingsLoadGuard(
        scope = scope,
        invalidate = { invalidate() },
        contentLoaded = { true }
    )

    init {
        enableBackNavigation()
    }

    override fun onGetTemplate(): ListTemplate = carListTemplate(carContext, ::buildTemplate)

    /** Template body; guarded by [carListTemplate] (spec: car-host-fault-isolation). */
    private fun buildTemplate(): ListTemplate {
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
        } else {
            val builder = ItemList.Builder()
            choices.forEach { choice ->
                builder.addItem(
                    Row.Builder()
                        .setTitle(choiceRowTitle(choice, choice.value == currentValue))
                        .setOnClickListener { onSelect(choice.value) }
                        .build()
                )
            }
            builder.build()
        }

        return ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle(title)
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .setSingleList(itemList)
            .build()
    }

    /** Row title: the option's own label plus the marker on the value in force. */
    internal fun choiceRowTitle(choice: PreferenceChoice, selected: Boolean): String =
        choice.label + if (selected) " ${carContext.getString(R.string.choice_current)}" else ""

    /** Error-row Retry action: re-run the pick that failed, or just redraw with none pending. */
    internal fun onRetry() {
        val value = pending
        if (value == null) invalidate() else onSelect(value)
    }

    /**
     * Persist the picked [value] and pop back to the topic screen, the pop running only after the
     * write completed (spec: auto/preferences — "A value is chosen, never only cycled"). The
     * settings are read fresh at the write, so a value changed on the phone while this screen was
     * open is carried forward instead of being overwritten from a stale snapshot.
     */
    internal fun onSelect(value: String) {
        if (saveInFlight) return
        saveInFlight = true
        pending = value
        scope.launch {
            val saved = runCatching { persistSelection(value) }
            if (saved.isSuccess) {
                pending = null
                finishSelection()
            } else {
                saveInFlight = false
                Log.w(TAG, "persist failed — keeping the options open", saved.exceptionOrNull())
                guard.onLoadFailed()
            }
        }
    }

    /**
     * Write [value] through the settings provider; split from [onSelect]'s pop so the pick can be
     * verified in unit tests, where the screen is not attached to a host.
     */
    internal suspend fun persistSelection(value: String) {
        val current = settingsProvider.load()
        val updated = PreferencesScreenMapper.choose(current, key, value, stylesLoader())
        if (updated == current) return
        settingsProvider.save(updated)
        if (key == PreferencesScreenMapper.KEY_DARK_MODE) {
            onDarkModeChanged(updated.darkMode)
        }
    }

    /** Pop back to the topic screen after the pick is persisted. */
    internal fun finishSelection() {
        guardedHostCall("pop (preference options)") { screenManager.pop() }
    }

    private companion object {
        private const val TAG = "PreferenceChoiceScreen"

        fun settingsProviderFor(carContext: CarContext): AutoSettingsProvider =
            EntryPointAccessors.fromApplication(
                carContext.applicationContext,
                AutoEntryPoint::class.java
            ).autoSettingsProvider()
    }
}