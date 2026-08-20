package com.naviveylin.core

/**
 * Read/write access to the shared navigation settings for the Android Auto
 * process. Implemented in `:app` on top of `SettingsStorage`, so changes made
 * in the car persist to the same settings file the phone app reads.
 */
interface AutoSettingsProvider {

    /** Load the current shared settings. */
    suspend fun load(): AutoSettings

    /** Persist the given settings, preserving fields the car does not edit. */
    suspend fun save(settings: AutoSettings)
}
