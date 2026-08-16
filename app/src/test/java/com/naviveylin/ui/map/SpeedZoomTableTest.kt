package com.naviveylin.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies the speed-to-magnification table: exact breakpoints, linear
 * interpolation between them, and clamping outside the table range.
 * Plain JUnit — no Robolectric, no JNI stub involved.
 */
class SpeedZoomTableTest {

    @Test
    fun `exact breakpoints map to their magnification`() {
        assertEquals(18.0, SpeedZoomTable.compute(0.0), 0.001)
        assertEquals(17.5, SpeedZoomTable.compute(6.0), 0.001)
        assertEquals(16.0, SpeedZoomTable.compute(15.0), 0.001)
        assertEquals(16.0, SpeedZoomTable.compute(30.0), 0.001)
        assertEquals(16.0, SpeedZoomTable.compute(60.0), 0.001)
        assertEquals(13.0, SpeedZoomTable.compute(90.0), 0.001)
        assertEquals(12.0, SpeedZoomTable.compute(130.0), 0.001)
    }

    @Test
    fun `walking speed interpolates between 0 and 6`() {
        // 5 km/h: 18.0 + (5/6) * (17.5 - 18.0) = 17.5833
        assertEquals(17.5833, SpeedZoomTable.compute(5.0), 0.001)
    }

    @Test
    fun `suburban to highway interpolates between 60 and 90`() {
        // 75 km/h: 16.0 + (15/30) * (13.0 - 16.0) = 14.5
        assertEquals(14.5, SpeedZoomTable.compute(75.0), 0.001)
    }

    @Test
    fun `highway speed interpolates between 90 and 130`() {
        // 100 km/h: 13.0 + (10/40) * (12.0 - 13.0) = 12.75
        assertEquals(12.75, SpeedZoomTable.compute(100.0), 0.001)
    }

    @Test
    fun `speeds below and above the table clamp to the ends`() {
        assertEquals(18.0, SpeedZoomTable.compute(-5.0), 0.001)
        assertEquals(12.0, SpeedZoomTable.compute(200.0), 0.001)
    }

    @Test
    fun `band index matches the speed band`() {
        assertEquals(0, SpeedZoomTable.bandIndex(0.0))
        assertEquals(0, SpeedZoomTable.bandIndex(5.0))
        assertEquals(1, SpeedZoomTable.bandIndex(10.0))
        assertEquals(3, SpeedZoomTable.bandIndex(45.0))
        assertEquals(4, SpeedZoomTable.bandIndex(75.0))
        assertEquals(6, SpeedZoomTable.bandIndex(200.0))
    }
}
