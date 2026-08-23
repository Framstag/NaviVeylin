package com.naviveylin.auto

import androidx.car.app.model.Distance
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.TravelEstimate
import com.framstag.libosmscout.client.RouteInstruction
import com.framstag.libosmscout.client.TurnType
import com.naviveylin.core.NavigationState
import java.util.Date
import java.time.ZonedDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Smoke tests for [NavigationTemplateFactory] (spec: auto-map-layout — action
 * strip on the left edge, visualisation strip on the right edge; spec:
 * auto/navigation-view — host instruction panel carries maneuver, steps, lanes).
 */
@RunWith(RobolectricTestRunner::class)
class NavigationTemplateFactoryTest {

    private val routeList = NavigationScreenActions.routeListAction {}
    private val stop = NavigationScreenActions.stopAction {}
    private val zoomIn = NavigationScreenActions.zoomInAction {}
    private val zoomOut = NavigationScreenActions.zoomOutAction {}

    private val leftStrip = androidx.car.app.model.ActionStrip.Builder()
        .addAction(stop)
        .addAction(routeList)
        .build()

    private val rightStrip = androidx.car.app.model.ActionStrip.Builder()
        .addAction(zoomIn)
        .addAction(zoomOut)
        .build()

    private val estimate = TravelEstimate.Builder(
        Distance.create(120.0, Distance.UNIT_KILOMETERS),
        ZonedDateTime.ofInstant(Date(System.currentTimeMillis() + 3600_000L).toInstant(), ZoneId.systemDefault())
    ).build()

    @Test
    fun navigatingSetsBothStrips() {
        val template = NavigationTemplateFactory.buildNavigationTemplate(
            isNavigating = true, travelEstimate = estimate,
            mapActionStrip = leftStrip, actionStrip = rightStrip
        )
        // Map action strip: icon-only stop (X) + route-description actions —
        // no BACK (spec: auto/navigation-view "Leave navigation at any time"
        // — stop action + system back, no back button).
        assertEquals(2, template.mapActionStrip!!.actions.size)
        assertTrue(template.mapActionStrip!!.actions.all { it.title == null })
        val titles = template.actionStrip!!.actions.map { it.title.toString() }
        assertEquals(listOf("+", "-"), titles)
    }

    @Test
    fun navigatingKeepsTravelEstimate() {
        val template = NavigationTemplateFactory.buildNavigationTemplate(
            isNavigating = true, travelEstimate = estimate,
            mapActionStrip = leftStrip, actionStrip = rightStrip
        )
        assertEquals(120.0, template.destinationTravelEstimate!!.remainingDistance!!.displayDistance, 0.01)
    }

    @Test
    fun noHostInstructionPanel() {
        val template = NavigationTemplateFactory.buildNavigationTemplate(
            isNavigating = true, travelEstimate = estimate,
            mapActionStrip = leftStrip, actionStrip = rightStrip
        )
        // Without routing info the host instruction panel stays unset.
        assertNull("host instruction panel must not be set", template.navigationInfo)
    }

    @Test
    fun navigatingSetsHostRoutingInfo() {
        val state = NavigationState(
            isNavigating = true,
            currentStepIndex = 0,
            instructions = listOf(
                RouteInstruction(
                    350.0, TurnType.LEFT, "Main St", "Turn left into Main St", "Turn left"
                )
            )
        )
        val template = NavigationTemplateFactory.buildNavigationTemplate(
            isNavigating = true, travelEstimate = estimate,
            mapActionStrip = leftStrip, actionStrip = rightStrip,
            routingInfo = NavigationTemplateMapper.routingInfoFromState(
                state, ManeuverGlyphs::forTurnType, includeLanes = false
            )
        )
        val info = template.navigationInfo as? androidx.car.app.navigation.model.RoutingInfo
        assertNotNull("host instruction panel via RoutingInfo", info)
        assertEquals(350.0, info!!.currentDistance!!.displayDistance, 0.01)
    }

    @Test
    fun notNavigatingHasNoStrips() {
        val template = NavigationTemplateFactory.buildNavigationTemplate(false, null, null, null)
        assertNull(template.mapActionStrip)
        assertNull(template.destinationTravelEstimate)
        // Builder requires at least one strip even when not navigating.
        assertTrue(template.actionStrip != null)
    }

    @Test
    fun zoomActionsAreNotParkedOnly() {
        assertTrue(zoomIn.onClickDelegate?.isParkedOnly != true)
        assertTrue(zoomOut.onClickDelegate?.isParkedOnly != true)
    }
}
