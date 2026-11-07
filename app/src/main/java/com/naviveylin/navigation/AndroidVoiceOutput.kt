package com.naviveylin.navigation

import android.content.Context
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The device's text-to-speech engine behind [VoiceOutput].
 *
 * `TextToSpeech` initialises asynchronously. A sentence that arrives before the engine is ready is
 * held in [pending] and spoken by the init callback; a later sentence replaces it. Every call happens
 * on the main dispatcher (the navigation engine marshals its native callback there), so no lock is
 * needed around this state.
 */
@Singleton
class AndroidVoiceOutput @Inject constructor(
    @param:ApplicationContext private val context: Context
) : VoiceOutput {

    private var engine: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    override fun speak(text: String) {
        if (text.isBlank()) return
        val tts = engine ?: createEngine()
        if (ready) {
            speakNow(tts, text)
        } else {
            pending = text
        }
    }

    override fun stop() {
        pending = null
        engine?.stop()
    }

    private fun createEngine(): TextToSpeech {
        val created = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                engine?.language = Locale.getDefault()
                pending?.let { text ->
                    engine?.let { speakNow(it, text) }
                }
                pending = null
            }
        }
        engine = created
        return created
    }

    private fun speakNow(tts: TextToSpeech, text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    private companion object {
        const val UTTERANCE_ID = "naviveylin-guidance"
    }
}
