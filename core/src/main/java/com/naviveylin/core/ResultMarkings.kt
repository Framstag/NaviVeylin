package com.naviveylin.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.naviveylin.core.search.ResultMarking

/**
 * Canvas rendering of the search-result row markings — the single source of the
 * marking artwork (same pattern as [ManeuverSymbols]): the phone result row and
 * the Android Auto result row draw the same bitmaps, so the two surfaces cannot
 * drift.
 *
 * The marking is *composite*: a row can be a favorite and a perfect match at the
 * same time, and the car `Row` offers a single image slot, so both facts are
 * drawn into one bitmap instead of one replacing the other (spec:
 * search-result-ranking — "Perfect-match marking and cross-surface parity").
 *
 * White on transparent; the host tints per theme. Bitmaps are created once and
 * cached — car templates are rebuilt on every invalidation.
 *
 * A row whose entry has an object type the active stylesheet gives artwork for
 * carries that artwork *and* its marking in the same slot, drawn into one bitmap
 * as well ([bitmapFor] with [typeIcon]); see [rowSlotGeometry] for the geometry
 * the two share (spec: search-result-row-icon — "One leading slot carries icon
 * and marking together").
 */
object ResultMarkings {

    /** Edge length of a rendered marking bitmap, in pixels. */
    const val BITMAP_SIZE = 48

    private val cache = HashMap<ResultMarking, Bitmap>()

    /**
     * Composite bitmaps, keyed by the marking and the identity of the icon they
     * were drawn with. The icon instances are the ones [SearchRowIcons] caches, so
     * [clear] is called where that cache is cleared and no stale composite can
     * outlive its icon.
     */
    private val compositeCache = HashMap<Long, Bitmap>()

    /**
     * Marking bitmap for [marking], cached per marking. [ResultMarking.NONE]
     * yields a fully transparent bitmap so callers can draw unconditionally.
     */
    fun bitmapFor(marking: ResultMarking): Bitmap = cache.getOrPut(marking) { render(marking) }

    /**
     * The row's leading slot as one bitmap: [marking] with [typeIcon] beside it, so
     * a row can carry the entry's type and its marking without a second image slot
     * (spec: search-result-row-icon — "One leading slot carries icon and marking
     * together").
     *
     * A `null` [typeIcon] returns the marking-only bitmap unchanged, which is the
     * layout of a row whose type has no artwork. [ResultMarking.NONE] with an icon
     * returns the icon itself: the transparent marking bitmap would otherwise spend
     * its own width on nothing.
     *
     * The marking keeps the size it has in the marking-only bitmap and the geometry
     * keeps it at the same offset on screen: the composite is taller when the icon is
     * taller than the marking, and the row centres the slot vertically
     * ([rowSlotGeometry]).
     */
    fun bitmapFor(marking: ResultMarking, typeIcon: Bitmap?): Bitmap {
        if (typeIcon == null) return bitmapFor(marking)
        if (marking == ResultMarking.NONE) return typeIcon
        val key = (marking.ordinal.toLong() shl 32) or
            (System.identityHashCode(typeIcon).toLong() and 0xFFFFFFFFL)
        return compositeCache.getOrPut(key) {
            val geometry = rowSlotGeometry(typeIcon.width, typeIcon.height)
            val bitmap = Bitmap.createBitmap(geometry.width, geometry.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawBitmap(
                bitmapFor(marking),
                geometry.markingX.toFloat(),
                geometry.markingY.toFloat(),
                null
            )
            canvas.drawBitmap(typeIcon, geometry.iconX.toFloat(), geometry.iconY.toFloat(), null)
            bitmap
        }
    }

    /**
     * Drop every cached bitmap, the marking artwork and the composites. Called where
     * the row-icon cache is cleared — when the active stylesheet finishes loading and
     * when the icon-versus-symbol preference changes — so no composite can outlive the
     * icon it was drawn with (spec: search-result-row-icon — "Resolved artwork is
     * reused and follows the style").
     */
    fun clear() {
        cache.clear()
        compositeCache.clear()
    }

    private fun render(marking: ResultMarking): Bitmap {
        val bitmap = Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.FILL
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        when (marking) {
            ResultMarking.NONE -> Unit
            ResultMarking.FAVORITE -> drawHeart(canvas, fill, centerX = 24f, centerY = 24f, radius = 9f)
            ResultMarking.PERFECT -> drawCheck(canvas, stroke, centerX = 24f, centerY = 24f, size = 15f)
            ResultMarking.FAVORITE_AND_PERFECT -> {
                drawHeart(canvas, fill, centerX = 13f, centerY = 25f, radius = 7f)
                drawCheck(canvas, stroke, centerX = 34f, centerY = 24f, size = 10f)
            }
        }

        return bitmap
    }

    /** Filled heart of [radius] centered on the given point. */
    private fun drawHeart(canvas: Canvas, paint: Paint, centerX: Float, centerY: Float, radius: Float) {
        val lobe = radius * 0.55f
        canvas.drawCircle(centerX - radius * 0.5f, centerY - radius * 0.45f, lobe, paint)
        canvas.drawCircle(centerX + radius * 0.5f, centerY - radius * 0.45f, lobe, paint)
        val path = Path().apply {
            moveTo(centerX - radius, centerY - radius * 0.2f)
            lineTo(centerX + radius, centerY - radius * 0.2f)
            lineTo(centerX, centerY + radius * 1.1f)
            close()
        }
        canvas.drawPath(path, paint)
    }

    /** Stroked tick mark of [size] centered on the given point. */
    private fun drawCheck(canvas: Canvas, paint: Paint, centerX: Float, centerY: Float, size: Float) {
        paint.strokeWidth = size * 0.22f
        val path = Path().apply {
            moveTo(centerX - size * 0.5f, centerY)
            lineTo(centerX - size * 0.1f, centerY + size * 0.42f)
            lineTo(centerX + size * 0.5f, centerY - size * 0.42f)
        }
        canvas.drawPath(path, paint)
    }
}

/**
 * Where the marking and the type icon sit inside the leading slot — pure arithmetic, so
 * the layout can be asserted without a canvas (the project's Robolectric sandbox discards
 * draws).
 *
 * @param width total width of the slot, in pixels
 * @param height total height of the slot, in pixels: the taller of the marking and the icon
 * @param markingX left edge of the marking bitmap
 * @param markingY top edge of the marking bitmap
 * @param iconX left edge of the type icon
 * @param iconY top edge of the type icon
 */
data class RowSlotGeometry(
    val width: Int,
    val height: Int,
    val markingX: Int,
    val markingY: Int,
    val iconX: Int,
    val iconY: Int
)

/** Horizontal gap between the marking and the icon as a fraction of the icon's height. */
private const val ICON_GAP_FRACTION = 5

/**
 * Geometry of the leading slot holding the marking ([ResultMarkings.BITMAP_SIZE] wide) and
 * an icon of the given pixel size (spec: search-result-row-icon — the marking keeps the
 * position and size it has without an icon).
 *
 * The marking stays at the slot's leading edge and at its own size, and is vertically
 * centred, so the row centring the slot vertically puts it at the same offset on screen
 * with and without an icon:
 *
 * ```
 * (rowHeight - height) / 2 + markingY  ==  (rowHeight - BITMAP_SIZE) / 2
 * ```
 *
 * The gap scales with the icon, so the spacing follows the density-scaled icon size — one
 * fifth of its height, which is 4 dp at the 20 dp icon the result rows request.
 */
fun rowSlotGeometry(iconWidth: Int, iconHeight: Int): RowSlotGeometry {
    val w = iconWidth.coerceAtLeast(0)
    val h = iconHeight.coerceAtLeast(0)
    val gap = maxOf(1, h / ICON_GAP_FRACTION)
    val slotWidth = ResultMarkings.BITMAP_SIZE + gap + w
    val slotHeight = maxOf(ResultMarkings.BITMAP_SIZE, h)
    return RowSlotGeometry(
        width = slotWidth,
        height = slotHeight,
        markingX = 0,
        markingY = (slotHeight - ResultMarkings.BITMAP_SIZE) / 2,
        iconX = ResultMarkings.BITMAP_SIZE + gap,
        iconY = (slotHeight - h) / 2
    )
}
