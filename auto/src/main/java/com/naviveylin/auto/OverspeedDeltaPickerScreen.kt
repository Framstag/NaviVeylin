package com.naviveylin.auto

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

    init {
        enableBackNavigation()
        scope.launch {
            settings = settingsProvider.load()
            invalidate()
        }
    }

    override fun onGetTemplate(): ListTemplate {
        val current = settings
        val itemList = if (current == null) {
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

    /**
     * Persist the chosen delta to the shared settings and return to the
     * preferences screen. The value keeps its identity on the phone.
     */
    internal fun onSelect(delta: Int) {
        scope.launch { persistSelection(delta) }
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
        fun settingsProviderFor(carContext: CarContext): AutoSettingsProvider =
            EntryPointAccessors.fromApplication(
                carContext.applicationContext,
                AutoEntryPoint::class.java
            ).autoSettingsProvider()
    }
}
