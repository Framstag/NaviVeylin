package com.naviveylin.auto

import androidx.car.app.CarContext
import com.naviveylin.auto.R
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.navigation.model.MapController
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.PanModeListener

/**
 * Pure factory for the browsing-map template (extracted for testability).
 *
 * The host API requires a content template on [MapWithContentTemplate]; the
 * content doubles as the search control: a single clickable "Search" row
 * (ListTemplate content — PaneTemplate rows cannot take click listeners,
 * `ROW_CONSTRAINTS_PANE`). The map action strip (left edge) rides on the
 * [MapController] per the car-app 1.7.0 migration guidance.
 */
object MapTemplateFactory {

    /** Menu content in the host content slot, with app header. */
    fun buildMenuContent(
        carContext: CarContext,
        onFreeDriving: () -> Unit,
        onStarredFavorites: () -> Unit,
        onAllFavorites: () -> Unit,
        onPoiSearch: () -> Unit,
        onSearchHistory: () -> Unit,
        onDiagnostics: () -> Unit,
        onAbout: () -> Unit
    ): ListTemplate = ListTemplate.Builder()
        .setHeader(
            Header.Builder()
                .setTitle(carContext.getString(R.string.app_name))
                .setStartHeaderAction(Action.APP_ICON)
                .build()
        )
        .setSingleList(
            ItemList.Builder()
                .addItem(
                    Row.Builder()
                        .setTitle(carContext.getString(R.string.free_driving))
                        .setOnClickListener(onFreeDriving)
                        .build()
                )
                .addItem(
                    Row.Builder()
                        .setTitle(carContext.getString(R.string.starred_favorites))
                        .setOnClickListener(onStarredFavorites)
                        .build()
                )
                .addItem(
                    Row.Builder()
                        .setTitle(carContext.getString(R.string.all_favorites))
                        .setOnClickListener(onAllFavorites)
                        .build()
                )
                .addItem(
                    Row.Builder()
                        .setTitle(carContext.getString(R.string.search_pois))
                        .setOnClickListener(onPoiSearch)
                        .build()
                )
                .addItem(
                    Row.Builder()
                        .setTitle(carContext.getString(R.string.search_history))
                        .setOnClickListener(onSearchHistory)
                        .build()
                )
                .addItem(
                    Row.Builder()
                        .setTitle(carContext.getString(R.string.diagnostics))
                        .setOnClickListener(onDiagnostics)
                        .build()
                )
                .addItem(
                    Row.Builder()
                        .setTitle(carContext.getString(R.string.about))
                        .setOnClickListener(onAbout)
                        .build()
                )
                .build()
        )
        .build()

    /**
     * Assemble the map template with the given [mapController] (which carries
     * the left map action strip), the [contentTemplate] and optional right
     * [actionStrip].
     */
    fun buildTemplate(
        mapController: MapController,
        contentTemplate: androidx.car.app.model.Template,
        actionStrip: ActionStrip? = null
    ): MapWithContentTemplate {
        val builder = MapWithContentTemplate.Builder()
            .setMapController(mapController)
            .setContentTemplate(contentTemplate)
        actionStrip?.let { builder.setActionStrip(it) }
        return builder.build()
    }

    /**
     * Assemble a full-screen [NavigationTemplate] with the given [mapActionStrip]
     * (left map edge) and [actionStrip] (right edge).
     *
     * NavigationTemplate is the only template with NO content slot — the map
     * fills the surface, so no host-rendered panel/box ("Free driving" box on
     * MapWithContentTemplate, "No items" on MapTemplate) overlays it. The
     * earlier belief that the AAOS emulator's host locks the navigation
     * surface predates the renderer fixes (missing finally-unlock leaked the
     * lock — every later lockCanvas threw "already locked"); with the
     * finally/release/isValid fixes in place the surface renders like any
     * other. Free driving passes no NavigationInfo — the template renders the
     * bare map; the host shows its own compass (cosmetic overlap with the
     * app's rose, accepted).
     */
    fun buildFullScreenTemplate(
        mapActionStrip: ActionStrip,
        actionStrip: ActionStrip,
        panModeListener: PanModeListener? = null
    ): NavigationTemplate {
        val builder = NavigationTemplate.Builder()
            .setMapActionStrip(mapActionStrip)
            .setActionStrip(actionStrip)
        // Host pan affordance (spec: auto/map-pan) — see buildNavigationTemplate.
        panModeListener?.let { builder.setPanModeListener(it) }
        return builder.build()
    }
}
