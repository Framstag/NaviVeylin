package com.framstag.libosmscout.client

/**
 * [OSMScoutClient] test double whose bounding-box query answers from a lambda
 * (initial-viewport resolution tests). Lives in the client package to access
 * the package-private constructor, like [FakeAutoRenderClient]. Only the bbox
 * path is stubbed — the other native methods are never called by the resolver.
 */
class FakeClientWithBbox(private val bbox: (String) -> DoubleArray?) : OSMScoutClient() {
    override fun getDatabaseBoundingBox(path: String): DoubleArray? = bbox(path)

    // The car path never pushes the symbol/icon preference; the override keeps the
    // native method out of the host stub's reach for a screen that renders after the
    // phone changed it.
    override fun setPreferSymbolIcons(preferSymbolIcons: Boolean) = Unit
}
