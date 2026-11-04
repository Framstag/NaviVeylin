package com.naviveylin.ui.map

import com.framstag.libosmscout.client.FakeOSMScoutClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The phone's one app-side record of the stylesheet `daylight` flag
 * (`guidelines/MapStyling.md` §15 — "Load budget and dedupe key"), which the main map's
 * `ensureMapStyle` and the mini map widget both write.
 *
 * The cases pin what the record exists for: a value the client already carries costs no native call,
 * so mounting the mini map does not reload the stylesheet set; a rejected reload is not remembered as
 * applied; and only the reasons `MapStyleApplyReason` names force a repeat.
 *
 * Robolectric with the default sandbox: instantiating the double triggers the guarded
 * `System.loadLibrary` that `app/src/test/jniLibs` stubs (`guidelines/BuildTests.md` §6a).
 */
@RunWith(RobolectricTestRunner::class)
class MapDaylightApplierTest {

    private lateinit var client: FakeOSMScoutClient

    @Before
    fun reset() {
        MapDaylightApplier.reset()
        client = FakeOSMScoutClient()
    }

    @Test
    fun firstPushAppliesAndRecordsTheValue() {
        assertEquals(MapDaylightPush.APPLIED, MapDaylightApplier.push(client, false))

        assertEquals(
            "the light variant is the set daylight flag",
            listOf(MapCanvasViewModel.STYLE_FLAG_DAYLIGHT to true),
            client.styleFlags.toList()
        )
        assertFalse("the record holds the applied value", MapDaylightApplier.needsPush(client, false))
    }

    @Test
    fun aValueTheClientCarriesIsNotPushedAgain() {
        MapDaylightApplier.push(client, true)

        assertEquals(MapDaylightPush.UNCHANGED, MapDaylightApplier.push(client, true))
        assertEquals("one native call for one changed value", 1, client.styleFlags.size)
    }

    @Test
    fun theSecondWriterPaysNothingForAValueTheFirstApplied() {
        // The main map's load path records the pair it installed without pushing; the widget then
        // sees the same value. This is the mini-map-mount cost the record exists to remove.
        MapDaylightApplier.record(client, true)

        assertEquals(MapDaylightPush.UNCHANGED, MapDaylightApplier.push(client, true))
        assertTrue("no native call", client.styleFlags.isEmpty())
    }

    @Test
    fun forceRePushesAValueTheRecordAlreadyHolds() {
        MapDaylightApplier.push(client, false)

        assertEquals(
            "a database open may have dropped earlier work (STARTUP_DB_READY)",
            MapDaylightPush.APPLIED,
            MapDaylightApplier.push(client, false, force = true)
        )
        assertEquals(2, client.styleFlags.size)
    }

    @Test
    fun aRejectedReloadIsNotRecordedSoTheNextWriterRetries() {
        client.styleLoadSuccessful = false

        assertEquals(MapDaylightPush.FAILED, MapDaylightApplier.push(client, true))
        assertTrue(
            "the client kept the other variant, so the value is not recorded",
            MapDaylightApplier.needsPush(client, true)
        )

        client.styleLoadSuccessful = true
        assertEquals(MapDaylightPush.APPLIED, MapDaylightApplier.push(client, true))
    }

    @Test
    fun twoClientsKeepTheirOwnRecord() {
        val other = FakeOSMScoutClient()
        MapDaylightApplier.push(client, false)

        assertEquals(
            "the record is per client — a push on one says nothing about the other",
            MapDaylightPush.APPLIED,
            MapDaylightApplier.push(other, false)
        )
    }
}
