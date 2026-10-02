package com.naviveylin.auto

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/**
 * The car map's degraded notice: a short text the app draws on the surface when the map cannot be
 * drawn any more (spec: car-host-fault-isolation — A repeatedly faulting car renderer recovers,
 * then degrades visibly; design D5).
 *
 * The app draws it on the surface because the templates of the navigation and free-driving screens
 * (`NavigationTemplate`) have no content slot for a host-rendered notice — the map fills the
 * surface there (see `MapTemplateFactory.buildFullScreenTemplate`). The two map/content-template
 * screens additionally carry the same string as a row in their content list.
 *
 * Known limitation, recorded in the change's tasks: if a screen's frame draw faults so badly that no
 * frame lands at all, the notice cannot be painted either — the surface then keeps its last frame.
 * The host templates offer no text slot to fall back to on those two screens.
 *
 * The paints are cached per surface density: the draw runs once per frame while the state holds, and
 * the frame path must not allocate per frame (spec `auto-map-renderer` — Marker drawing allocates no
 * per-frame objects).
 */
internal object MapUnavailableOverlay {

    private var cachedDensity = Float.NaN
    private var chipPaint: Paint? = null
    private var textPaint: Paint? = null

    /**
     * Draw [text] centered on the surface. [density] is the surface density
     * (`surfaceDpi / 160`), the same value the other app-drawn car overlays use.
     */
    fun draw(canvas: Canvas, surfaceWidth: Int, surfaceHeight: Int, density: Float, text: String) {
        if (text.isEmpty() || surfaceWidth <= 0 || surfaceHeight <= 0) return
        val tp = textPaintFor(density)
        val cp = chipPaint ?: Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 0, 0, 0)
        }.also { chipPaint = it }

        val centerX = surfaceWidth / 2f
        val centerY = surfaceHeight / 2f
        val halfWidth = tp.measureText(text) / 2f + 12f * density
        val halfHeight = tp.textSize / 2f + 8f * density
        canvas.drawRoundRect(
            centerX - halfWidth,
            centerY - halfHeight,
            centerX + halfWidth,
            centerY + halfHeight,
            6f * density,
            6f * density,
            cp
        )
        canvas.drawText(text, centerX, centerY + tp.textSize / 3f, tp)
    }

    private fun textPaintFor(density: Float): Paint {
        val cached = textPaint
        if (cached != null && cachedDensity == density) return cached
        val fresh = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = 14f * density
        }
        textPaint = fresh
        cachedDensity = density
        return fresh
    }

    /** Test seam: the cached paints belong to one surface density. */
    internal fun resetForTest() {
        cachedDensity = Float.NaN
        textPaint = null
        chipPaint = null
    }
}
