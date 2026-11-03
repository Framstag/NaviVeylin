package com.naviveylin.navigation

import androidx.test.core.app.ApplicationProvider
import com.naviveylin.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The spoken departure from the planned route (spec: `voice-guidance` — A departure from the
 * planned route is announced once).
 *
 * The once rule is [OffRouteAnnouncementGate]'s, the wording is the app's resource. The car surface
 * does not speak; the phone's TTS does, so the engine owns the cue and both surfaces hear it.
 */
@RunWith(RobolectricTestRunner::class)
class OffRouteAnnouncementTest {

    @Test
    fun theDepartureIsSpokenOnce() {
        val gate = OffRouteAnnouncementGate()

        assertTrue(
            "the transition onto the off-route condition is announced",
            gate.onDeparture(isNavigating = true, voiceEnabled = true)
        )
    }

    @Test
    fun theAnnouncementDoesNotRepeatWhileOffRoute() {
        val gate = OffRouteAnnouncementGate()
        assertTrue(gate.onDeparture(isNavigating = true, voiceEnabled = true))

        repeat(20) {
            assertFalse(
                "a position update that merely repeats the condition speaks nothing",
                gate.onDeparture(isNavigating = true, voiceEnabled = true)
            )
        }
    }

    @Test
    fun aSecondDepartureIsAnnouncedAgain() {
        val gate = OffRouteAnnouncementGate()
        assertTrue(gate.onDeparture(isNavigating = true, voiceEnabled = true))

        // Back on the planned route (or a rerouted one adopted): the rule re-arms.
        gate.rearm()

        assertTrue(gate.onDeparture(isNavigating = true, voiceEnabled = true))
        assertFalse(gate.onDeparture(isNavigating = true, voiceEnabled = true))
    }

    @Test
    fun theAnnouncementHonoursTheVoiceSwitch() {
        val gate = OffRouteAnnouncementGate()

        assertFalse(
            "voice off stays silent",
            gate.onDeparture(isNavigating = true, voiceEnabled = false)
        )
        assertFalse(
            "and turning the switch on while still off route does not replay the departure",
            gate.onDeparture(isNavigating = true, voiceEnabled = true)
        )
    }

    @Test
    fun noAnnouncementOutsideASession() {
        val gate = OffRouteAnnouncementGate()

        assertFalse(
            "there is no navigation view to be off route from",
            gate.onDeparture(isNavigating = false, voiceEnabled = true)
        )
        assertTrue(
            "a session that starts afterwards announces its own departure",
            gate.onDeparture(isNavigating = true, voiceEnabled = true)
        )
    }

    @Test
    fun theWordingCarriesNoPosition() {
        val wording = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(R.string.voice_off_route)

        assertTrue("the wording is a sentence: '$wording'", wording.isNotBlank())
        assertFalse(
            "no coordinate, distance or bearing in the announcement: '$wording'",
            wording.any { it.isDigit() }
        )
        assertFalse("no coordinate separators: '$wording'", wording.contains("°") || wording.contains("\""))
    }
}
