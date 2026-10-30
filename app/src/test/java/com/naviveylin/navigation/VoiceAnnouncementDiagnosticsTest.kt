package com.naviveylin.navigation

import com.naviveylin.core.SurfaceOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shape of the spoken-guidance diagnostics line (spec: `voice-guidance`; spec: `auto-diagnostics` —
 * no coordinates in diagnostics). A device run reads this line to tell "the switch suppressed it" from
 * "the engine never saw it", so the fields and their names are asserted here rather than left to the
 * first logcat reading — and the line must never carry a coordinate.
 */
class VoiceAnnouncementDiagnosticsTest {

    @Test
    fun aSpokenTurnNamesItsKindAndSurface() {
        val line = voiceAnnouncementLine(
            VoiceAnnouncementKind.TURN,
            SurfaceOrigin.PHONE,
            VoiceAnnouncementOutcome.SPOKEN
        )

        assertEquals("voice: kind=turn surface=phone outcome=spoken", line)
    }

    @Test
    fun aSuppressedGpsStateNamesItsReason() {
        val line = voiceAnnouncementLine(
            VoiceAnnouncementKind.GPS,
            SurfaceOrigin.CAR,
            VoiceAnnouncementOutcome.SUPPRESSED,
            VoiceAnnouncementReason.GPS_SWITCH_OFF
        )

        assertEquals("voice: kind=gps surface=car outcome=suppressed reason=gps-switch-off", line)
    }

    @Test
    fun aHeldGpsStateNamesNoReason() {
        val held = voiceAnnouncementLine(
            VoiceAnnouncementKind.GPS,
            SurfaceOrigin.CAR,
            VoiceAnnouncementOutcome.HELD
        )

        assertEquals("voice: kind=gps surface=car outcome=held", held)
        assertFalse("a held state carries no reason", held.contains("reason="))
    }

    @Test
    fun everyReasonIsCoordinateFree() {
        // The gate `checkNoCoordinatesInLogs` reads the sources; this case reads the values a run
        // will actually print, so a reason cannot start carrying an identity it should not.
        val reasons = listOf(
            VoiceAnnouncementReason.VOICE_SWITCH_OFF,
            VoiceAnnouncementReason.GPS_SWITCH_OFF,
            VoiceAnnouncementReason.FLAP_REPLACED,
            VoiceAnnouncementReason.SESSION_ENDED
        )

        for (reason in reasons) {
            val line = voiceAnnouncementLine(
                VoiceAnnouncementKind.GPS,
                SurfaceOrigin.CAR,
                VoiceAnnouncementOutcome.SUPPRESSED,
                reason
            )
            assertTrue("$reason must not carry a digit-dot-digit coordinate", !line.contains("."))
            assertTrue(line.startsWith("voice: "))
        }
    }
}
