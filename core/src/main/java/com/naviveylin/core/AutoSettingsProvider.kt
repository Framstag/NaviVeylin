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

    /**
     * Persist vehicle anchor values chosen ON THE CAR. This is the only write that
     * freezes the car's own values (spec: auto-map-layout — "Car falls back to the
     * phone value until configured on the car"); a generic [save] never does, so an
     * inherited value keeps following the phone until the driver picks one here.
     * A `null` component leaves that mode's car value unchanged.
     */
    suspend fun saveCarAnchor(routingAnchorId: String? = null, freeDrivingAnchorId: String? = null)
}
