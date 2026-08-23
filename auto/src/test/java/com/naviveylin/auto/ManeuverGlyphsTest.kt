package com.naviveylin.auto

import com.framstag.libosmscout.client.TurnType
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [ManeuverGlyphs] (spec: auto/navigation-view — host maneuver
 * requires a CarIcon per turn type).
 */
@RunWith(RobolectricTestRunner::class)
class ManeuverGlyphsTest {

    @Test
    fun providesIconForEveryTurnType() {
        for (type in TurnType.values()) {
            assertNotNull("maneuver icon for $type", ManeuverGlyphs.forTurnType(type))
        }
    }

    @Test
    fun iconsAreCachedPerTurnType() {
        assertSame(
            ManeuverGlyphs.forTurnType(TurnType.LEFT),
            ManeuverGlyphs.forTurnType(TurnType.LEFT)
        )
    }
}
