package com.naviveylin.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.Row
import androidx.car.app.model.RowSection
import androidx.car.app.model.SectionedItemTemplate
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.AutoSettings
import com.naviveylin.core.AutoSettingsProvider
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
 * Uses [SectionedItemTemplate] (not [PaneTemplate]): PaneTemplate rows do not
 * support click listeners (car-app constraint `ROW_CONSTRAINTS_PANE`).
 */
class PreferencesScreen(
    carContext: CarContext,
    private val settingsProvider: AutoSettingsProvider
) : Screen(carContext) {

    /** Production path: resolve the provider via the Hilt entry point. */
    constructor(carContext: CarContext) : this(
        carContext,
        EntryPointAccessors.fromApplication(
            carContext.applicationContext,
            AutoEntryPoint::class.java
        ).autoSettingsProvider()
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var settings: AutoSettings? = null
    private var loaded = false

    init {
        enableBackNavigation()
        scope.launch {
            settings = settingsProvider.load()
            loaded = true
            invalidate()
        }
    }

    override fun onGetTemplate(): SectionedItemTemplate {
        val current = settings
        val section = if (!loaded || current == null) {
            RowSection.Builder()
                .addItem(Row.Builder().setTitle("Loading...").build())
                .build()
        } else {
            val builder = RowSection.Builder()
            PreferencesScreenMapper.rows(current).forEach { row ->
                builder.addItem(
                    Row.Builder()
                        .setTitle(row.title)
                        .addText(row.valueText)
                        .setOnClickListener { onToggle(row.key) }
                        .build()
                )
            }
            builder.build()
        }

        return SectionedItemTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle("Preferences")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .addSection(section)
            .build()
    }

    internal fun onToggle(key: String) {
        val current = settings ?: return
        val updated = PreferencesScreenMapper.toggle(current, key)
        settings = updated
        scope.launch {
            settingsProvider.save(updated)
            invalidate()
        }
    }
}
