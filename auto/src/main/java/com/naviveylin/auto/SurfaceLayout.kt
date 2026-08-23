package com.naviveylin.auto

/**
 * Shared surface geometry for the Auto map/navigation layout, mirroring the
 * phone UI's inset/reserve columns (see `MapCanvasScreen`: `ActionColumnInset`
 * = 64.dp and `ViewColumnReserve` = 64.dp).
 *
 * The left-edge action strip occupies [ACTION_STRIP_INSET_DP]; the right-edge
 * visualisation strip occupies [VIEW_STRIP_RESERVE_DP]. Overlay content drawn
 * on the map surface (e.g. the navigation hint panel) must stay within
 * [insetLeftPx] .. (surfaceWidth - [reserveRightPx]) so it never overlaps
 * either strip.
 */
object SurfaceLayout {

    /** Left-edge action strip width in dp (phone `ActionColumnInset`). */
    const val ACTION_STRIP_INSET_DP = 64f

    /** Right-edge visualisation strip width in dp (phone `ViewColumnReserve`). */
    const val VIEW_STRIP_RESERVE_DP = 64f

    /** Left strip width in surface pixels at the given density (dp * density). */
    fun insetLeftPx(density: Float): Int = (ACTION_STRIP_INSET_DP * density).toInt()

    /** Right strip width in surface pixels at the given density (dp * density). */
    fun reserveRightPx(density: Float): Int = (VIEW_STRIP_RESERVE_DP * density).toInt()

    /**
     * Horizontal pixel range available for surface overlays between the two
     * strips. Returns an empty range when the surface is too narrow for both
     * strips to coexist.
     */
    fun overlayRangePx(surfaceWidth: Int, density: Float): IntRange {
        val left = insetLeftPx(density)
        val rightExclusive = surfaceWidth - reserveRightPx(density)
        return if (rightExclusive > left) left until rightExclusive else IntRange.EMPTY
    }
}
