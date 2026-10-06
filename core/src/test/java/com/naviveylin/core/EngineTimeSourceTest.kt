package com.naviveylin.core

import org.junit.Assert.assertTrue
import org.junit.Test

/** The time-source seam's production value reads the wall clock (spec: `navigation-engine`). */
class EngineTimeSourceTest {

    @Test
    fun systemSourceReadsTheWallClock() {
        val before = java.lang.System.currentTimeMillis()
        val read = EngineTimeSource.System.nowMillis()
        val after = java.lang.System.currentTimeMillis()

        assertTrue("read $read must lie in [$before, $after]", read in before..after)
    }
}
