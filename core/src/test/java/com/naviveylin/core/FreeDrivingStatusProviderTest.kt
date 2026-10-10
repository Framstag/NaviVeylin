package com.naviveylin.core

import com.framstag.libosmscout.client.RoadInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Per-surface publication of the shared free-driving status (spec:
 * `current-road-info` — One free-driving road/speed status feeds every surface and
 * the notification; `location-updates-lease` — an invisible, non-navigating mode
 * keeps the last known road and speed). Robolectric because the provider mirrors
 * each publish into the diagnostics log, which reaches `android.util.Log`.
 */
@RunWith(RobolectricTestRunner::class)
class FreeDrivingStatusProviderTest {

    private val provider = FreeDrivingStatusProvider()

    private fun road(ref: String, name: String) = RoadInfo(name, ref, "highway_primary", 50.0)

    @Test
    fun emptyBeforeFirstFix() {
        assertNull("no fix yet -> no status", provider.status.value)
    }

    @Test
    fun publishPerSurface() {
        provider.publish(FreeDrivingStatusProvider.SURFACE_PHONE, road("B 1", "Hauptstrasse"), 50.0)

        assertEquals("phone entry is exposed", "B 1", provider.status.value?.roadRef)

        provider.publish(FreeDrivingStatusProvider.SURFACE_CAR, road("A 44", "Autobahn"), 100.0)

        assertEquals("the most recent publish is exposed", "A 44", provider.status.value?.roadRef)
        assertEquals(100.0, provider.status.value?.speedKmH ?: Double.NaN, 1e-9)
    }

    @Test
    fun lastResolutionWins() {
        provider.publish(FreeDrivingStatusProvider.SURFACE_PHONE, road("B 1", "Hauptstrasse"), 50.0)
        provider.publish(FreeDrivingStatusProvider.SURFACE_CAR, road("A 44", "Autobahn"), 100.0)
        provider.publish(FreeDrivingStatusProvider.SURFACE_PHONE, road("B 7", "Bundesstrasse"), 30.0)

        assertEquals("the latest publisher wins", "B 7", provider.status.value?.roadRef)
    }

    @Test
    fun clearRemovesOneSurface() {
        provider.publish(FreeDrivingStatusProvider.SURFACE_PHONE, road("B 1", "Hauptstrasse"), 50.0)
        provider.publish(FreeDrivingStatusProvider.SURFACE_CAR, road("A 44", "Autobahn"), 100.0)

        provider.clear(FreeDrivingStatusProvider.SURFACE_CAR)

        assertEquals(
            "clearing the latest surface falls back to the other",
            "B 1",
            provider.status.value?.roadRef
        )

        provider.clear(FreeDrivingStatusProvider.SURFACE_PHONE)

        assertNull("clearing the last surface empties the status", provider.status.value)
    }

    @Test
    fun retainOnSurfaceDeathIsNotBlanked() {
        // A surface that dies publishes nothing more; the other surface's clear must
        // not blank the value the dead surface left behind (spec: `DrivingModeProvider`
        // retention semantics).
        provider.publish(FreeDrivingStatusProvider.SURFACE_PHONE, road("B 1", "Hauptstrasse"), 50.0)
        provider.publish(FreeDrivingStatusProvider.SURFACE_CAR, road("A 44", "Autobahn"), 100.0)

        provider.clear(FreeDrivingStatusProvider.SURFACE_CAR)

        assertEquals(
            "the phone's retained value is not blanked by the car's death",
            "B 1",
            provider.status.value?.roadRef
        )
    }

    @Test
    fun noRoadIsAPublishNotAClear() {
        // A known fix with no road there is a real status: the label disappears
        // (hasRoad false) while the notification keeps the fallback text.
        provider.publish(FreeDrivingStatusProvider.SURFACE_PHONE, road("B 1", "Hauptstrasse"), 50.0)
        provider.publish(FreeDrivingStatusProvider.SURFACE_PHONE, null, 50.0)

        assertTrue("a status exists", provider.status.value != null)
        assertFalse("but it carries no road", provider.status.value?.hasRoad ?: true)
        assertNull(provider.status.value?.roadText)
    }

    @Test
    fun unknownSpeedKeepsTheRoad() {
        provider.publish(FreeDrivingStatusProvider.SURFACE_PHONE, road("B 1", "Hauptstrasse"), Double.NaN)

        assertEquals("B 1 Hauptstrasse", provider.status.value?.roadText)
        assertTrue(provider.status.value?.speedKmH?.isNaN() ?: false)
    }
}
