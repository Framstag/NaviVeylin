package com.framstag.libosmscout.client

/**
 * [OSMScoutClient] test double for the car map screen's host-callback paths
 * (spec: car-host-fault-isolation — Host callbacks answer promptly): answers the
 * calls the map screen's init and tap path make from plain fields, so a screen can be
 * built and tapped without the native library. Lives in the client package to access
 * the package-private constructor, like [FakeAutoRenderClient]/[FakeClientWithBbox].
 *
 * Only the non-render paths are stubbed; the renderer never draws in these tests (no
 * car surface is ever delivered).
 */
class FakeMapScreenClient(
    /** Candidates the tap path returns; empty means "details screen with coordinates". */
    var candidates: List<ObjectDescription> = emptyList(),
    var styleSheetLoadResult: Boolean = true,
    var boundingBox: DoubleArray? = null
) : OSMScoutClient() {

    /** Style names passed to [loadStyleSheet] in call order. */
    val styleSheetLoads = mutableListOf<String>()

    /** True once [getDescriptionCandidates] was reached. */
    var candidateLookups: Int = 0

    /** Stylesheet flags pushed to the native side, in call order. */
    val styleSheetFlags = mutableListOf<Pair<String, Boolean>>()

    override fun loadStyleSheet(name: String): Boolean {
        styleSheetLoads.add(name)
        return styleSheetLoadResult
    }

    override fun setStyleSheetFlag(key: String, value: Boolean) {
        styleSheetFlags.add(key to value)
    }

    override fun setMapDpi(dpi: Double) = Unit

    override fun getStyleSheetDirectory(): String = ""

    override fun getActiveStyleSheet(): String = "standard.oss"

    override fun wasLastStyleLoadSuccessful(): Boolean = true

    override fun getDatabaseBoundingBox(path: String): DoubleArray? = boundingBox

    override fun getDescriptionCandidates(
        lat: Double,
        lon: Double,
        magnification: Int
    ): List<ObjectDescription> {
        candidateLookups++
        return candidates
    }

    /** Reverse geocode of the tapped point (details screen init): no address in these tests. */
    override fun getAddressAt(lat: Double, lon: Double): Array<String>? = null

    /** Selected-object description (details screen init): none in these tests. */
    override fun getDescription(lat: Double, lon: Double, magnification: Int): ObjectDescription? = null

    override fun getRoadAt(lat: Double, lon: Double, bearing: Double): RoadInfo? = null

    override fun isInitialized(): Boolean = true
}
