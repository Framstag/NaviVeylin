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
    private val stylesLoader: () -> List<String>
) : Screen(carContext) {

    /** Production path: resolve the provider and the live style list via Hilt. */
    constructor(carContext: CarContext) : this(
        carContext,
        settingsProviderFor(carContext),
        stylesLoaderFor(carContext)
    )

    /** Test path: injected provider and the bundled fallback style list. */
    constructor(carContext: CarContext, settingsProvider: AutoSettingsProvider) : this(
        carContext,
        settingsProvider,
        { BundledMapStyles.ALL }
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

    override fun onGetTemplate(): ListTemplate {
        val current = settings
        val itemList = if (!loaded || current == null) {
            ItemList.Builder()
                .addItem(Row.Builder().setTitle("Loading...").build())
                .build()
        } else {
            val builder = ItemList.Builder()
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

        return ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle("Preferences")
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
            invalidate()
        }
    }

    private companion object {
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
            }.getOrDefault(BundledMapStyles.ALL)
        }
    }
}
