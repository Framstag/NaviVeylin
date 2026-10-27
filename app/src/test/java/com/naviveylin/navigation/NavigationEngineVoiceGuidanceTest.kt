package com.naviveylin.navigation

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.Vehicle
import com.naviveylin.core.EngineDispatchers
import com.naviveylin.location.LocationService
import com.naviveylin.test.engineUnderTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Spoken turn-by-turn guidance (spec: `voice-guidance`).
 *
 * Default Robolectric sandbox (no `@Config`, no `@GraphicsMode`) because this class builds
 * [FakeOSMScoutClient] and [NavigationEngine], per `guidelines/Build.md` §6a.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NavigationEngineVoiceGuidanceTest {

    private val computeDispatcher = StandardTestDispatcher()
    private val ioDispatcher = StandardTestDispatcher()
    private val engineDispatchers = EngineDispatchers(computeDispatcher, ioDispatcher)

    private class RecordingVoiceOutput : VoiceOutput {
        val spoken = mutableListOf<String>()
        var stops = 0

        override fun speak(text: String) {
            spoken += text
        }

        override fun stop() {
            stops += 1
        }
    }

    private fun routeEntry(): RouteEntry = RouteEntry().apply {
        routeHandle = 1L
        latitudes = doubleArrayOf(52.5200, 52.5230)
        longitudes = doubleArrayOf(13.4050, 13.4080)
        distance = 1000.0
    }

    private fun drain() {
        repeat(2) {
            computeDispatcher.scheduler.advanceUntilIdle()
            ioDispatcher.scheduler.advanceUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun await(condition: () -> Boolean) {
        drain()
        if (!condition()) {
            throw AssertionError("Condition not met after draining the test scheduler")
        }
    }

    /**
     * Spec scenario: A native voice sequence is spoken while navigating.
     */
    @Test
    fun voiceSampleSequenceIsSpokenWhileNavigating() {
        val client = FakeOSMScoutClient().apply { routeToDeliver = routeEntry() }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val output = RecordingVoiceOutput()
        val engine = engineUnderTest(
            { client },
            LocationService(context),
            context,
            dispatchers = engineDispatchers,
            voiceGuidance = VoiceGuidance(context, output)
        )

        engine.acquire(52.5200, 13.4050, 52.5230, 13.4080, Vehicle.CAR)
        await { engine.state.value.isNavigating }

        client.navigationListener!!.onVoiceInstruction(
            intArrayOf(
                VoiceGuidance.AFTER,
                58,
                VoiceGuidance.METERS,
                VoiceGuidance.TURN_LEFT
            )
        )
        await { output.spoken.isNotEmpty() }

        assertEquals(listOf("After 300 meters turn left"), output.spoken)
    }

    /**
     * Spec scenario: A voice instruction after navigation stops is silent.
     */
    @Test
    fun voiceInstructionAfterStopIsSilent() {
        val client = FakeOSMScoutClient().apply { routeToDeliver = routeEntry() }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val output = RecordingVoiceOutput()
        val engine = engineUnderTest(
            { client },
            LocationService(context),
            context,
            dispatchers = engineDispatchers,
            voiceGuidance = VoiceGuidance(context, output)
        )

        engine.acquire(52.5200, 13.4050, 52.5230, 13.4080, Vehicle.CAR)
        await { engine.state.value.isNavigating }

        engine.stopNavigation()
        drain()

        client.navigationListener!!.onVoiceInstruction(intArrayOf(VoiceGuidance.ARRIVE))
        drain()

        assertTrue("a stopped session must not speak", output.spoken.isEmpty())
        assertEquals("stopping navigation must stop the current utterance", 1, output.stops)
    }
}
