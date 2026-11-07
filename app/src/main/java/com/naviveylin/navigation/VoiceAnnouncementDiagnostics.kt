package com.naviveylin.navigation

import com.naviveylin.core.SurfaceOrigin

/**
 * Coordinate-free diagnostics for spoken guidance (spec: `voice-guidance`; spec: `auto-diagnostics` —
 * no coordinates in diagnostics).
 *
 * The line exists because speech is the one output a device run cannot otherwise read: a sentence is
 * audio, and a suppressed one leaves no trace at all, so "the switch turned it off" and "the engine
 * never saw the instruction" looked identical in logcat. Every announcement therefore reports what it
 * was and what happened to it — identity only (the kind of sample, the surface that owns the session,
 * the outcome and its reason), never a coordinate, which the `checkNoCoordinatesInLogs` gate enforces.
 */
internal fun voiceAnnouncementLine(
    kind: VoiceAnnouncementKind,
    surface: SurfaceOrigin,
    outcome: VoiceAnnouncementOutcome,
    reason: String = ""
): String = buildString {
    append("voice: kind=").append(kind.entryValue)
    append(" surface=").append(surface.name.lowercase())
    append(" outcome=").append(outcome.entryValue)
    if (reason.isNotEmpty()) append(" reason=").append(reason)
}

/** What the native sample sequence carried: a turn instruction, or a GPS state. */
internal enum class VoiceAnnouncementKind(val entryValue: String) {
    TURN("turn"),
    GPS("gps")
}

/** What the engine did with it. */
internal enum class VoiceAnnouncementOutcome(val entryValue: String) {
    /** Handed to the voice seam, i.e. spoken (or spoken once the TTS engine finished initialising). */
    SPOKEN("spoken"),

    /** Deliberately not spoken; [voiceAnnouncementLine]'s `reason` names which rule stopped it. */
    SUPPRESSED("suppressed"),

    /** A GPS state is being held for the flap window before it is spoken. */
    HELD("held")
}

/**
 * The reasons a sample is not spoken, as the diagnostics line names them: the session surface's voice
 * switch is off, the shared GPS switch is off, or the state was replaced inside the hold window.
 */
internal object VoiceAnnouncementReason {
    const val VOICE_SWITCH_OFF = "voice-switch-off"
    const val GPS_SWITCH_OFF = "gps-switch-off"
    const val FLAP_REPLACED = "flap-replaced"
    const val SESSION_ENDED = "session-ended"
}
