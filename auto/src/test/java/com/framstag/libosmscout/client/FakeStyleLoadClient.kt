package com.framstag.libosmscout.client

/**
 * [OSMScoutClient] test double for the car-side stylesheet-failure report
 * (change `fix-stylesheet-load-crash`): answers the load outcome and the active
 * style from plain fields, so the shared reporting seam can be exercised
 * without the native library. Lives in the client package to access the
 * package-private constructor, like [FakeClientWithBbox].
 */
class FakeStyleLoadClient(
    var loadSuccessful: Boolean = true,
    var activeStyle: String = "standard.oss"
) : OSMScoutClient() {
    override fun wasLastStyleLoadSuccessful(): Boolean = loadSuccessful

    override fun getActiveStyleSheet(): String = activeStyle
}
