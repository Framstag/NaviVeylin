package com.naviveylin.navigation

import android.content.Context
import com.naviveylin.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns the native voice sample sequence into one localized sentence and speaks it.
 *
 * The native `int[]` carries ordinal values of `SampleVoiceInstructionMessage::VoiceSample`
 * (`libosmscout/.../navigation/VoiceInstructionAgent.h`). The agent emits an optional distance block
 * (`After`, a distance sample, a unit sample), the manoeuvre, and optionally `Then` plus a second
 * manoeuvre. Unknown identifiers are ignored, so a future native sample cannot crash navigation.
 */
@Singleton
class VoiceGuidance @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val output: VoiceOutput
) {

    /** Announce [samples], or stay silent when the sequence carries no recognised instruction. */
    fun announce(samples: IntArray) {
        val sentence = sentenceFor(samples)
        if (sentence.isNotEmpty()) {
            output.speak(sentence)
        }
    }

    /** Stop the current utterance (navigation ended). */
    fun stop() {
        output.stop()
    }

    /**
     * The GPS state a sequence announces, or `null` when it carries none (spec: `voice-guidance` —
     * GPS announcements are one shared switch, off by default). The engine holds a GPS state before
     * speaking it and gates it on the shared switch, so it has to tell the two kinds apart without
     * parsing the sample sequence a second time.
     */
    internal fun gpsStateOf(samples: IntArray): Int? =
        samples.firstOrNull { it == GPS_FOUND || it == GPS_LOST }

    internal fun sentenceFor(samples: IntArray): String {
        val parts = mutableListOf<String>()
        var index = 0
        while (index < samples.size) {
            when (samples[index]) {
                AFTER -> {
                    val distance = distanceMeters(samples.getOrNull(index + 1))
                    val unit = unitName(samples.getOrNull(index + 2))
                    if (distance != null && unit != null) {
                        parts += context.getString(distanceString(unit), distance)
                        index += 3
                    } else {
                        index += 1
                    }
                }

                in DISTANCE_METERS -> {
                    val distance = DISTANCE_METERS.getValue(samples[index])
                    val unit = unitName(samples.getOrNull(index + 1))
                    if (unit != null) {
                        parts += context.getString(distanceString(unit), distance)
                        index += 2
                    } else {
                        index += 1
                    }
                }

                METERS, YARDS -> index += 1

                THEN -> {
                    parts += context.getString(R.string.voice_then)
                    index += 1
                }

                TURN_LEFT -> {
                    parts += context.getString(R.string.voice_turn_left)
                    index += 1
                }

                TURN_RIGHT -> {
                    parts += context.getString(R.string.voice_turn_right)
                    index += 1
                }

                SHARP_LEFT -> {
                    parts += context.getString(R.string.voice_sharp_left)
                    index += 1
                }

                SHARP_RIGHT -> {
                    parts += context.getString(R.string.voice_sharp_right)
                    index += 1
                }

                STRAIGHT -> {
                    parts += context.getString(R.string.voice_straight)
                    index += 1
                }

                ROUNDABOUT -> {
                    parts += context.getString(R.string.voice_roundabout)
                    index += 1
                }

                in ROUNDABOUT_EXITS -> {
                    parts += context.getString(
                        R.string.voice_roundabout_exit,
                        ROUNDABOUT_EXITS.getValue(samples[index])
                    )
                    index += 1
                }

                MOTORWAY_EXIT -> {
                    parts += context.getString(R.string.voice_motorway_exit)
                    index += 1
                }

                MOTORWAY_EXIT_LEFT -> {
                    parts += context.getString(R.string.voice_motorway_exit_left)
                    index += 1
                }

                MOTORWAY_EXIT_RIGHT -> {
                    parts += context.getString(R.string.voice_motorway_exit_right)
                    index += 1
                }

                ARRIVE -> {
                    parts += context.getString(R.string.voice_arrive)
                    index += 1
                }

                GPS_FOUND -> {
                    parts += context.getString(R.string.voice_gps_found)
                    index += 1
                }

                GPS_LOST -> {
                    parts += context.getString(R.string.voice_gps_lost)
                    index += 1
                }

                else -> index += 1
            }
        }
        if (parts.isEmpty()) return ""
        return parts.joinToString(" ").replaceFirstChar { it.titlecase() }
    }

    private fun distanceMeters(sample: Int?): Int? = sample?.let { DISTANCE_METERS[it] }

    private fun unitName(sample: Int?): VoiceUnit? = when (sample) {
        METERS -> VoiceUnit.Meters
        YARDS -> VoiceUnit.Yards
        else -> null
    }

    private fun distanceString(unit: VoiceUnit): Int = when (unit) {
        VoiceUnit.Meters -> R.string.voice_after_distance_meters
        VoiceUnit.Yards -> R.string.voice_after_distance_yards
    }

    private enum class VoiceUnit { Meters, Yards }

    internal companion object {
        // Ordinals of SampleVoiceInstructionMessage::VoiceSample in
        // app/src/main/cpp/libosmscout/libosmscout/include/osmscout/navigation/VoiceInstructionAgent.h.
        const val AFTER = 0
        const val ARRIVE = 10
        const val GPS_FOUND = 14
        const val GPS_LOST = 15
        const val METERS = 22
        const val MOTORWAY_EXIT_LEFT = 24
        const val MOTORWAY_EXIT = 25
        const val MOTORWAY_EXIT_RIGHT = 26
        const val ROUNDABOUT = 28
        const val ROUNDABOUT_EXIT_1 = 29
        const val ROUNDABOUT_EXIT_6 = 34
        const val SHARP_LEFT = 40
        const val SHARP_RIGHT = 41
        const val STRAIGHT = 42
        const val THEN = 44
        const val TURN_LEFT = 46
        const val TURN_RIGHT = 47
        const val YARDS = 49

        val DISTANCE_METERS: Map<Int, Int> = mapOf(
            54 to 50,
            55 to 80,
            56 to 100,
            57 to 200,
            58 to 300,
            59 to 400,
            60 to 500,
            61 to 600,
            62 to 700,
            63 to 800
        )

        val ROUNDABOUT_EXITS: Map<Int, Int> = (ROUNDABOUT_EXIT_1..ROUNDABOUT_EXIT_6)
            .associateWith { it - ROUNDABOUT_EXIT_1 + 1 }
    }
}
