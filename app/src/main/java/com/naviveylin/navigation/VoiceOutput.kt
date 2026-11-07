package com.naviveylin.navigation

/**
 * Where a spoken navigation instruction goes.
 *
 * The production implementation is [AndroidVoiceOutput]. A test substitutes a recorder so it can
 * assert the sentence without starting a platform TTS engine.
 */
interface VoiceOutput {

    /** Speak [text], replacing any utterance still in progress. */
    fun speak(text: String)

    /** Stop the current utterance, if any. */
    fun stop()

    companion object {
        /** A seam for a caller that does not want audio (tests, and a surface that never speaks). */
        val NoOp: VoiceOutput = object : VoiceOutput {
            override fun speak(text: String) = Unit
            override fun stop() = Unit
        }
    }
}
