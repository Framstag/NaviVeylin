package com.naviveylin.auto

import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.PanModeListener
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.TravelEstimate

/**
 * Pure factory for the navigation screen template (extracted for testability —
 * the strip split, the ETA card and the host instruction panel content are
 * behavior the spec pins down).
 *
 * The host instruction panel is populated via [RoutingInfo] (spec:
 * auto/navigation-view): current step maneuver + distance, next-next step and
 * lane guidance. The compass rose and speed badge stay drawn on the map
 * surface by [SurfaceIndicators], the street name by [StreetNameLabel].
 */
object NavigationTemplateFactory {

    /**
     * Build the [NavigationTemplate]. When not navigating, a plain template
     * with no strips/estimate is returned.
     */
    fun buildNavigationTemplate(
        isNavigating: Boolean,
        travelEstimate: TravelEstimate?,
        mapActionStrip: ActionStrip?,
        actionStrip: ActionStrip?,
        routingInfo: RoutingInfo? = null,
        panModeListener: PanModeListener? = null
    ): NavigationTemplate {
        val builder = NavigationTemplate.Builder()
        if (!isNavigating) {
            // The builder requires at least one strip even when not navigating.
            return builder
                .setBackgroundColor(androidx.car.app.model.CarColor.DEFAULT)
                .setActionStrip(ActionStrip.Builder().addAction(Action.BACK).build())
                .build()
        }
        travelEstimate?.let { builder.setDestinationTravelEstimate(it) }
        mapActionStrip?.let { builder.setMapActionStrip(it) }
        actionStrip?.let { builder.setActionStrip(it) }
        // Host pan affordance (spec: auto/map-pan): the host renders a pan
        // button and forwards pan gestures to the surface while pan mode is
        // active (RequiresCarApi 2).
        panModeListener?.let { builder.setPanModeListener(it) }
        // Host instruction panel (spec: auto/navigation-view).
        routingInfo?.let { builder.setNavigationInfo(it) }
        return builder.build()
    }
}
