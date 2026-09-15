package com.framstag.libosmscout.client

/**
 * [OSMScoutClient] test double whose bounding-box query answers from a lambda
 * (initial-viewport resolution tests). Lives in the client package to access
 * the package-private constructor, like [FakeAutoRenderClient]. Only the bbox
 * path is stubbed — the other native methods are never called by the resolver.
 */
class FakeClientWithBbox(private val bbox: (String) -> DoubleArray?) : OSMScoutClient() {
    override fun getDatabaseBoundingBox(path: String): DoubleArray? = bbox(path)
}
