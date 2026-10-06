package com.naviveylin.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Contract of the in-flight route calculation in the shared state
 * (spec: `route-calculation-feedback` — In-flight route calculation is part of the
 * shared navigation state).
 */
class RouteCalculationTest {

    @Test
    fun `a fresh navigation state reports no calculation in progress`() {
        assertNull(NavigationState().calculation)
    }

    @Test
    fun `an unknown percentage is null, never zero`() {
        val calculation = RouteCalculation(token = 1L, destLat = 52.5, destLon = 13.4)
        assertNull(calculation.percent)
    }

    @Test
    fun `a calculation carries its token, destination and percentage`() {
        val calculation = RouteCalculation(
            token = 7L,
            destLat = 52.5,
            destLon = 13.4,
            percent = 42
        )
        assertEquals(7L, calculation.token)
        assertEquals(52.5, calculation.destLat, 0.0)
        assertEquals(13.4, calculation.destLon, 0.0)
        assertEquals(42, calculation.percent)
    }

    @Test
    fun `state copies keep the calculation they were given`() {
        val calculation = RouteCalculation(token = 2L, destLat = 1.0, destLon = 2.0)
        assertEquals(calculation, NavigationState(calculation = calculation).calculation)
        assertNull(NavigationState(calculation = calculation).copy(calculation = null).calculation)
    }
}
