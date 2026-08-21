package com.naviveylin

import android.util.Log

/**
 * Installs the native sink that forwards libosmscout's platform-independent
 * `osmscout::log` output to Android Logcat (tag "NaviVeylin").
 *
 * The bridge lives in NaviVeylin's own NDK code (`app/src/main/cpp/`) — the
 * libosmscout submodule itself must stay free of Android dependencies.
 */
object NativeLogBridge {

    private val libraryLoaded: Boolean = try {
        System.loadLibrary("naviveylin_log_bridge")
        true
    } catch (e: UnsatisfiedLinkError) {
        Log.w(TAG, "Native log bridge library not available: ${e.message}")
        false
    }

    /**
     * Replaces the default native console logger with the Logcat sink.
     * Safe no-op if the native library could not be loaded (e.g. host tests).
     */
    fun install() {
        if (libraryLoaded) {
            try {
                installNative()
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "Native log bridge install failed: ${e.message}")
            }
        }
    }

    private external fun installNative()

    private const val TAG = "NativeLogBridge"
}
