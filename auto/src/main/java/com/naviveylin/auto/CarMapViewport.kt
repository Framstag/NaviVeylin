package com.naviveylin.auto

import com.naviveylin.core.CarMapViewportProvider

/**
 * Publish the center of the frame [renderer] has drawn as the car surface's search
 * distance reference (spec: `search-result-ranking` — Distance reference for
 * ordering and display).
 *
 * Called by a car map screen as it stops drawing, while the renderer still holds its
 * last frame: the push that opened the incoming screen stops this one, so the frame
 * the driver last saw is the "current car map viewport center" a search opened from
 * *any* car surface measures from — including a search opened from the root or the
 * history list, whose screen never holds a renderer.
 *
 * The value published is [AutoMapRenderer.committedViewportCenter], i.e. the frame
 * that is actually on the surface, never the pending render target: a screen that
 * stops before its first frame landed has drawn nothing. A renderer that has
 * committed no frame at all — or no renderer — leaves the previously published
 * center in place, which is the last frame the driver actually saw.
 */
internal fun publishCarViewportCenter(
    renderer: AutoMapRenderer?,
    provider: CarMapViewportProvider
) {
    val (lat, lon) = renderer?.committedViewportCenter() ?: return
    provider.publish(lat, lon)
}
