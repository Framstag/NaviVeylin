package com.naviveylin.navigation

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The sentence [VoiceGuidance] composes from the native sample ordinals (spec: `voice-guidance` — The
 * motorway manoeuvre names its signposted destination).
 *
 * Default Robolectric sandbox (no `@Config`, no `@GraphicsMode`): the class only resolves string
 * resources, per `guidelines/Build.md` §6a.
 */
@RunWith(RobolectricTestRunner::class)
class VoiceGuidanceTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private val guidance = VoiceGuidance(
        context,
        object : VoiceOutput {
            override fun speak(text: String) = Unit
            override fun stop() = Unit
        }
    )

    /** The samples `osmscout::VoiceInstructionAgent` emits for a motorway change (`OnMotorwayChange`). */
    private val motorwayChangeSequence = intArrayOf(
        VoiceGuidance.AFTER,
        58,
        VoiceGuidance.METERS,
        VoiceGuidance.MOTORWAY_EXIT_RIGHT
    )

    @Test
    fun aMotorwayDestinationJoinsTheSentence() {
        assertEquals(
            "After 300 meters leave the motorway to the right towards München",
            guidance.sentenceFor(motorwayChangeSequence, "München")
        )
    }

    /**
     * Spec scenario: A motorway manoeuvre without a destination record keeps today's sentence.
     */
    @Test
    fun aSentenceWithoutADestinationKeepsTodaysWording() {
        assertEquals(
            "After 300 meters leave the motorway to the right",
            guidance.sentenceFor(motorwayChangeSequence, null)
        )
    }

    @Test
    fun aBlankDestinationAddsNoClause() {
        assertEquals(
            "After 300 meters leave the motorway to the right",
            guidance.sentenceFor(motorwayChangeSequence, "  ")
        )
    }

    @Test
    fun aDestinationWithNoRecognisedSampleIsStillSpoken() {
        assertEquals(
            "Towards München",
            guidance.sentenceFor(intArrayOf(VoiceGuidance.METERS), "München")
        )
    }
}
