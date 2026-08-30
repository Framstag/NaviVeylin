package com.framstag.libosmscout.client

/**
 * Test double for [OSMScoutClient] that returns dummy pixel data.
 * Lives in the client package to access the package-private constructor.
 * Used by [com.naviveylin.auto.AutoMapRendererTest] to avoid loading the native library.
 */
class FakeAutoRenderClient : OSMScoutClient() {

    /** Style names passed to [loadStyleSheet] in call order. */
    val styleSheetLoads = mutableListOf<String>()

    /** Return value of the next [loadStyleSheet] call (true = success). */
    var styleSheetLoadResult: Boolean = true

    override fun loadStyleSheet(name: String): Boolean {
        styleSheetLoads.add(name)
        return styleSheetLoadResult
    }
    override fun render(
        width: Int, height: Int,
        lat: Double, lon: Double,
        angle: Double, magnification: Double
    ): IntArray? {
        return IntArray(width * height) { 0xFFCCCCCC.toInt() }
    }

    override fun renderWithRouteAndPois(
        width: Int, height: Int,
        lat: Double, lon: Double, angle: Double, magnification: Double,
        routeLats: DoubleArray?, routeLons: DoubleArray?,
        favoriteLats: DoubleArray?, favoriteLons: DoubleArray?,
        searchSelLat: Double, searchSelLon: Double,
        trackLats: DoubleArray?, trackLons: DoubleArray?
    ): IntArray? {
        return IntArray(width * height) { 0xFFCCCCCC.toInt() }
    }
}
