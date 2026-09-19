package com.naviveylin.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [MapStyleLoadReporter] — the single seam that turns a failed
 * stylesheet load into a diagnostics entry plus the user-visible message shared
 * by the phone and the car (change `fix-stylesheet-load-crash`, design D1; spec
 * `map-styles` — "Style load failure is visible on both surfaces").
 *
 * Robolectric: the seam writes a diagnostics entry, which reaches
 * `android.util.Log` even before [DiagnosticsLog] is initialized.
 */
@RunWith(RobolectricTestRunner::class)
class MapStyleLoadReporterTest {

    private val resolver = StringResolver { resId, args ->
        when (resId) {
            R.string.map_style_load_failed -> "Map style \"${args[0]}\" could not be loaded"
            R.string.map_style_load_failed_kept ->
                "Map style \"${args[0]}\" could not be loaded — still using \"${args[1]}\""
            else -> error("unexpected resource $resId")
        }
    }

    @Test
    fun keepsPreviousStyleMessageNamesBothStyles() {
        assertEquals(
            "Map style \"cycle\" could not be loaded — still using \"standard\"",
            MapStyleLoadReporter.failureMessage(resolver, "cycle", "standard")
        )
    }

    @Test
    fun noPreviousStyleMessageDoesNotNameAnActiveStyle() {
        assertEquals(
            "Map style \"cycle\" could not be loaded",
            MapStyleLoadReporter.failureMessage(resolver, "cycle", null)
        )
        assertEquals(
            "Map style \"cycle\" could not be loaded",
            MapStyleLoadReporter.failureMessage(resolver, "cycle", "")
        )
    }

    @Test
    fun styleFlagReloadOfTheActiveStyleDoesNotRepeatIt() {
        // A style-flag reload failed for the stylesheet that stays active: the
        // message must not read "…could not be loaded — still using the same".
        assertEquals(
            "Map style \"standard.oss\" could not be loaded",
            MapStyleLoadReporter.failureMessage(resolver, "standard.oss", "standard.oss")
        )
    }

    @Test
    fun failedLoadReportsAMessage() {
        assertEquals(
            "Map style \"cycle\" could not be loaded — still using \"standard\"",
            MapStyleLoadReporter.reportFailure(resolver, "cycle", "standard", loadSucceeded = false)
        )
    }

    @Test
    fun successfulLoadReportsNothing() {
        assertNull(MapStyleLoadReporter.reportFailure(resolver, "cycle", "standard", loadSucceeded = true))
    }
}
