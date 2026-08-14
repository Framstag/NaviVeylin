package com.naviveylin.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationViewModelTest {

    @Test
    fun computeRouteDistance_emptyArrays() {
        assertEquals(0.0, NavigationViewModel.computeRouteDistance(
            DoubleArray(0), DoubleArray(0)), 0.001)
    }

    @Test
    fun computeRouteDistance_singlePoint() {
        assertEquals(0.0, NavigationViewModel.computeRouteDistance(
            doubleArrayOf(48.8566), doubleArrayOf(2.3522)), 0.001)
    }

    @Test
    fun computeRouteDistance_twoPoints() {
        // Paris to Versailles ~18km
        val dist = NavigationViewModel.computeRouteDistance(
            doubleArrayOf(48.8566, 48.8049),
            doubleArrayOf(2.3522, 2.1204)
        )
        assertEquals(18000.0, dist, 3000.0)
    }

    @Test
    fun computeRouteDistance_threePoints() {
        // Paris to Versailles to Chartres ~68km
        val dist = NavigationViewModel.computeRouteDistance(
            doubleArrayOf(48.8566, 48.8049, 48.4439),
            doubleArrayOf(2.3522, 2.1204, 1.4892)
        )
        assertEquals(79000.0, dist, 5000.0)
    }

    @Test
    fun computeRouteDistance_zeroDistance() {
        val dist = NavigationViewModel.computeRouteDistance(
            doubleArrayOf(48.8566, 48.8566),
            doubleArrayOf(2.3522, 2.3522)
        )
        assertEquals(0.0, dist, 1.0)
    }

    @Test
    fun computeRouteDistance_knownDistance() {
        // 1 degree of latitude ~ 111km
        val dist = NavigationViewModel.computeRouteDistance(
            doubleArrayOf(0.0, 1.0),
            doubleArrayOf(0.0, 0.0)
        )
        assertEquals(111000.0, dist, 2000.0)
    }
}
