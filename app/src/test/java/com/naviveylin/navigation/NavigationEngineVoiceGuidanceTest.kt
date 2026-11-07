package com.naviveylin.navigation

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.RouteEntry
import com.framstag.libosmscout.client.Vehicle
import com.naviveylin.core.EngineDispatchers
import com.naviveylin.core.SurfaceOrigin
import com.naviveylin.data.AppSettings
import com.naviveylin.data.SettingsStorage
import com.naviveylin.location.GpsFix
import com.naviveylin.location.LocationService
import com.naviveylin.test.engineUnderTest
import java.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
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

    /** A storage whose persisted settings are the ones a case needs before the engine reads them. */
    private fun storageWith(context: android.content.Context, settings: AppSettings): SettingsStorage {
        // Saved through a storage on the real IO dispatcher: `runBlocking` cannot drive a test
        // dispatcher, so the write has to happen before the case's dispatcher is installed.
        val storage = SettingsStorage(context)
        runBlocking { storage.save(settings) }
        storage.ioDispatcher = ioDispatcher
        return storage
    }

    /**
     * A storage whose file IO runs on the case's own test dispatcher, so the engine's per-instruction
     * read is driven by the drain instead of a real IO thread (`guidelines/Build.md` §6e).
     */
    private fun testStorage(context: android.content.Context): SettingsStorage =
        SettingsStorage(context).also { it.ioDispatcher = ioDispatcher }

    private fun voiceEngine(
        client: FakeOSMScoutClient,
        output: VoiceOutput,
        context: android.content.Context,
        storage: SettingsStorage = testStorage(context)
    ): NavigationEngine = engineUnderTest(
        { client },
        LocationService(context),
        context,
        dispatchers = engineDispatchers,
        settingsStorage = storage,
        voiceGuidance = VoiceGuidance(context, output)
    )

    /** Grant the location permissions a `navigateTo` checks before it leases the feed. */
    private fun grantPreciseLocation() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        shadowOf(app).grantPermissions(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    /** Inject a fix so a `navigateTo` can resolve its start position (test only). */
    private fun injectGpsFix(locationService: LocationService, lat: Double, lon: Double) {
        val field = LocationService::class.java.getDeclaredField("_location")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(locationService) as MutableStateFlow<GpsFix?>
        flow.value = GpsFix(
            lat = lat,
            lon = lon,
            accuracy = 5.0,
            speedKmH = Double.NaN,
            smoothedBearing = Double.NaN,
            markerBearing = Double.NaN,
            time = System.currentTimeMillis()
        )
    }

    /**
     * Let the announce coroutine finish its settings hop, then advance the main looper's clock past
     * [millis] so a hold window expires (no wall-clock wait, `guidelines/Build.md` §6e).
     */
    private fun advanceHold(millis: Long) {
        drain()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
        drain()
    }

    private val turnSequence = intArrayOf(
        VoiceGuidance.AFTER,
        58,
        VoiceGuidance.METERS,
        VoiceGuidance.TURN_LEFT
    )

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
            settingsStorage = testStorage(context),
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
            settingsStorage = testStorage(context),
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

    // --- Per-surface voice switch (spec: voice-guidance — The voice switch is per surface) ---

    /**
     * Spec scenario: A turn on a surface whose voice switch is off is silent.
     */
    @Test
    fun voiceSwitchOffKeepsATurnSilent() {
        val client = FakeOSMScoutClient().apply { routeToDeliver = routeEntry() }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val output = RecordingVoiceOutput()
        val engine = voiceEngine(
            client,
            output,
            context,
            storageWith(context, AppSettings(voiceGuidanceEnabled = false))
        )

        engine.acquire(52.5200, 13.4050, 52.5230, 13.4080, Vehicle.CAR)
        await { engine.state.value.isNavigating }

        client.navigationListener!!.onVoiceInstruction(turnSequence)
        advanceHold(NavigationEngine.FLAP_WINDOW_MILLIS)

        assertTrue("the phone's switch is off, so no turn is spoken", output.spoken.isEmpty())
        assertTrue("and the session is otherwise untouched", engine.state.value.isNavigating)
    }

    /**
     * Spec scenario: The phone's switch governs a phone session.
     */
    @Test
    fun thePhoneSessionReadsThePhoneSwitch() {
        val client = FakeOSMScoutClient().apply { routeToDeliver = routeEntry() }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val output = RecordingVoiceOutput()
        val locationService = LocationService(context)
        grantPreciseLocation()
        injectGpsFix(locationService, 52.5200, 13.4050)
        val engine = engineUnderTest(
            { client },
            locationService,
            context,
            dispatchers = engineDispatchers,
            settingsStorage = storageWith(
                context,
                AppSettings(voiceGuidanceEnabled = true, autoVoiceGuidanceEnabled = false)
            ),
            voiceGuidance = VoiceGuidance(context, output)
        )

        engine.navigateTo(52.5230, 13.4080, null, SurfaceOrigin.PHONE)
        await { engine.state.value.isNavigating }

        client.navigationListener!!.onVoiceInstruction(turnSequence)
        await { output.spoken.isNotEmpty() }

        assertEquals(
            "a phone session speaks by the phone's switch, not the car's",
            listOf("After 300 meters turn left"),
            output.spoken
        )
    }

    /**
     * Spec scenario: The car's switch governs a car session.
     */
    @Test
    fun theCarSessionReadsTheCarSwitch() {
        val client = FakeOSMScoutClient().apply { routeToDeliver = routeEntry() }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val output = RecordingVoiceOutput()
        val locationService = LocationService(context)
        grantPreciseLocation()
        injectGpsFix(locationService, 52.5200, 13.4050)
        val engine = engineUnderTest(
            { client },
            locationService,
            context,
            dispatchers = engineDispatchers,
            settingsStorage = storageWith(
                context,
                AppSettings(voiceGuidanceEnabled = false, autoVoiceGuidanceEnabled = true)
            ),
            voiceGuidance = VoiceGuidance(context, output)
        )

        engine.navigateTo(52.5230, 13.4080, null, SurfaceOrigin.CAR)
        await { engine.state.value.isNavigating }

        client.navigationListener!!.onVoiceInstruction(turnSequence)
        await { output.spoken.isNotEmpty() }

        assertEquals(
            "a car session speaks by the car's switch although the phone's is off",
            listOf("After 300 meters turn left"),
            output.spoken
        )
    }

    /**
     * Spec scenario: A change applies to the next instruction.
     */
    @Test
    fun voiceSwitchChangeAppliesToTheNextInstruction() {
        val client = FakeOSMScoutClient().apply { routeToDeliver = routeEntry() }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val output = RecordingVoiceOutput()
        val storage = testStorage(context)
        val engine = voiceEngine(client, output, context, storage)

        engine.acquire(52.5200, 13.4050, 52.5230, 13.4080, Vehicle.CAR)
        await { engine.state.value.isNavigating }

        client.navigationListener!!.onVoiceInstruction(turnSequence)
        await { output.spoken.isNotEmpty() }
        assertEquals(1, output.spoken.size)

        // The switch is turned off during the session: the next instruction is silent. Written through
        // a second storage on the real IO dispatcher, which is what a settings change from another
        // surface is in production.
        runBlocking { SettingsStorage(context).update { it.copy(voiceGuidanceEnabled = false) } }
        client.navigationListener!!.onVoiceInstruction(turnSequence)
        advanceHold(NavigationEngine.FLAP_WINDOW_MILLIS)

        assertEquals("no instruction after the change is spoken", 1, output.spoken.size)
    }

    // --- GPS announcements (spec: voice-guidance — GPS announcements, hold window) ---

    /**
     * Spec scenario: GPS is silent by default.
     */
    @Test
    fun gpsAnnouncementIsSilentByDefault() {
        val client = FakeOSMScoutClient().apply { routeToDeliver = routeEntry() }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val output = RecordingVoiceOutput()
        val engine = voiceEngine(client, output, context)

        engine.acquire(52.5200, 13.4050, 52.5230, 13.4080, Vehicle.CAR)
        await { engine.state.value.isNavigating }

        client.navigationListener!!.onVoiceInstruction(intArrayOf(VoiceGuidance.GPS_LOST))
        advanceHold(NavigationEngine.FLAP_WINDOW_MILLIS * 2)

        assertTrue("GPS announcements are off by default", output.spoken.isEmpty())
        assertTrue("and navigation continues", engine.state.value.isNavigating)
    }

    /**
     * Spec scenario: A flapping signal speaks only the state that held.
     */
    @Test
    fun gpsFlapSpeaksOnlyTheStateThatHeld() {
        val client = FakeOSMScoutClient().apply { routeToDeliver = routeEntry() }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val output = RecordingVoiceOutput()
        val engine = voiceEngine(
            client,
            output,
            context,
            storageWith(context, AppSettings(announceGpsState = true))
        )

        engine.acquire(52.5200, 13.4050, 52.5230, 13.4080, Vehicle.CAR)
        await { engine.state.value.isNavigating }

        client.navigationListener!!.onVoiceInstruction(intArrayOf(VoiceGuidance.GPS_LOST))
        advanceHold(NavigationEngine.FLAP_WINDOW_MILLIS / 2)
        client.navigationListener!!.onVoiceInstruction(intArrayOf(VoiceGuidance.GPS_FOUND))
        advanceHold(NavigationEngine.FLAP_WINDOW_MILLIS * 2)

        assertEquals(
            "the replaced loss is not spoken, the recovery that held is",
            listOf("GPS signal restored"),
            output.spoken
        )
    }

    /**
     * Spec scenario: A state that holds is announced once.
     */
    @Test
    fun gpsStateThatHoldsIsAnnouncedOnce() {
        val client = FakeOSMScoutClient().apply { routeToDeliver = routeEntry() }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val output = RecordingVoiceOutput()
        val engine = voiceEngine(
            client,
            output,
            context,
            storageWith(context, AppSettings(announceGpsState = true))
        )

        engine.acquire(52.5200, 13.4050, 52.5230, 13.4080, Vehicle.CAR)
        await { engine.state.value.isNavigating }

        client.navigationListener!!.onVoiceInstruction(intArrayOf(VoiceGuidance.GPS_LOST))
        advanceHold(NavigationEngine.FLAP_WINDOW_MILLIS)

        assertEquals(listOf("GPS signal lost"), output.spoken)

        // A repeated sample of the same state neither restarts the hold nor adds an utterance.
        client.navigationListener!!.onVoiceInstruction(intArrayOf(VoiceGuidance.GPS_LOST))
        advanceHold(NavigationEngine.FLAP_WINDOW_MILLIS * 2)

        assertEquals("the held state is spoken once", 1, output.spoken.size)
    }

    /**
     * Spec scenario: Stopping navigation cancels a pending announcement.
     */
    @Test
    fun stopCancelsAPendingGpsAnnouncement() {
        val client = FakeOSMScoutClient().apply { routeToDeliver = routeEntry() }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val output = RecordingVoiceOutput()
        val engine = voiceEngine(
            client,
            output,
            context,
            storageWith(context, AppSettings(announceGpsState = true))
        )

        engine.acquire(52.5200, 13.4050, 52.5230, 13.4080, Vehicle.CAR)
        await { engine.state.value.isNavigating }

        client.navigationListener!!.onVoiceInstruction(intArrayOf(VoiceGuidance.GPS_LOST))
        advanceHold(NavigationEngine.FLAP_WINDOW_MILLIS / 2)
        engine.stopNavigation()
        advanceHold(NavigationEngine.FLAP_WINDOW_MILLIS * 2)

        assertTrue("a pending announcement must not outlive its session", output.spoken.isEmpty())
    }

    /**
     * Spec scenario: A car session with its voice switch off still speaks a held GPS state.
     */
    @Test
    fun gpsIgnoresTheSurfaceVoiceSwitch() {
        val client = FakeOSMScoutClient().apply { routeToDeliver = routeEntry() }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val output = RecordingVoiceOutput()
        val locationService = LocationService(context)
        grantPreciseLocation()
        injectGpsFix(locationService, 52.5200, 13.4050)
        val engine = engineUnderTest(
            { client },
            locationService,
            context,
            dispatchers = engineDispatchers,
            settingsStorage = storageWith(
                context,
                AppSettings(autoVoiceGuidanceEnabled = false, announceGpsState = true)
            ),
            voiceGuidance = VoiceGuidance(context, output)
        )

        engine.navigateTo(52.5230, 13.4080, null, SurfaceOrigin.CAR)
        await { engine.state.value.isNavigating }

        client.navigationListener!!.onVoiceInstruction(intArrayOf(VoiceGuidance.GPS_LOST))
        advanceHold(NavigationEngine.FLAP_WINDOW_MILLIS)

        assertEquals(
            "the shared GPS switch decides, not the surface's voice switch",
            listOf("GPS signal lost"),
            output.spoken
        )
    }
}
