package com.naviveylin.navigation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The composed sentence's own rule (spec: `voice-guidance` — The motorway manoeuvre names its
 * signposted destination).
 *
 * Robolectric's default sandbox: the case reads the module's string resources, which is what the
 * compositor resolves the clause from (`guidelines/Build.md` §6a).
 */
@RunWith(RobolectricTestRunner::class)
class VoiceGuidanceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val guidance = VoiceGuidance(context, VoiceOutput.NoOp)

    private val turnSequence = intArrayOf(
        VoiceGuidance.AFTER,
        58,
        VoiceGuidance.METERS,
        VoiceGuidance.TURN_RIGHT
    )

    /**
     * Spec scenario: The clause never makes an unspoken sequence speak.
     */
    @Test
    fun aDestinationClauseNeedsAnInstruction() {
        assertEquals(
            "a recognised sequence gains exactly one destination clause",
            "After 300 meters turn right towards München",
            guidance.sentenceFor(turnSequence, "München")
        )
        assertEquals(
            "no destination leaves the sentence the base speaks, byte for byte",
            "After 300 meters turn right",
            guidance.sentenceFor(turnSequence, null)
        )
        assertEquals(
            "a sequence that composed nothing stays silent, destination or not",
            "",
            guidance.sentenceFor(IntArray(0), "München")
        )
    }
}
