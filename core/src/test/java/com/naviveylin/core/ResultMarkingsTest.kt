package com.naviveylin.core

import android.graphics.Bitmap
import android.graphics.Canvas
import com.naviveylin.core.search.ResultMarking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for the shared marking artwork (spec: search-result-ranking —
 * "Perfect-match marking and cross-surface parity"; task 4.1). Robolectric is
 * needed because the artwork is drawn with android.graphics on a Bitmap.
 *
 * Deliberately in the default Robolectric sandbox (no `@Config`, no
 * `@GraphicsMode`) per AGENTS.md. Consequence, shared with the existing
 * [ManeuverSymbolsTest]: the shadowed canvas discards the draws, so these tests
 * pin the bitmap contract — size, one distinct bitmap per marking, caching,
 * drawing does not throw — and on-device/emulator review covers how the artwork
 * actually looks. The leading slot's geometry ([rowSlotGeometry]) is asserted as
 * arithmetic for the same reason; the pixels of the composite are the on-device
 * check (change `search-result-row-content`, task 9.3).
 */
@RunWith(RobolectricTestRunner::class)
class ResultMarkingsTest {

    @Test
    fun everyMarkingRendersABitmapOfTheDocumentedSizeAndType() {
        for (marking in ResultMarking.entries) {
            val bitmap = ResultMarkings.bitmapFor(marking)
            assertEquals(ResultMarkings.BITMAP_SIZE, bitmap.width)
            assertEquals(ResultMarkings.BITMAP_SIZE, bitmap.height)
            assertEquals(Bitmap.Config.ARGB_8888, bitmap.config)
        }
    }

    @Test
    fun eachMarkingHasItsOwnBitmap() {
        val bitmaps = ResultMarking.entries.map { ResultMarkings.bitmapFor(it) }
        assertEquals(
            "one distinct bitmap per marking",
            ResultMarking.entries.size,
            bitmaps.distinctBy { System.identityHashCode(it) }.size
        )
    }

    @Test
    fun bitmapsAreCachedPerMarking() {
        assertSame(
            ResultMarkings.bitmapFor(ResultMarking.FAVORITE_AND_PERFECT),
            ResultMarkings.bitmapFor(ResultMarking.FAVORITE_AND_PERFECT)
        )
        assertNotSame(
            ResultMarkings.bitmapFor(ResultMarking.FAVORITE),
            ResultMarkings.bitmapFor(ResultMarking.PERFECT)
        )
    }

    @Test
    fun drawingEveryMarkingOntoACanvasDoesNotThrow() {
        for (marking in ResultMarking.entries) {
            val target = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(target)
            val markingBitmap = ResultMarkings.bitmapFor(marking)
            assertNotNull(markingBitmap)
            // Same call the phone row and the car template row make.
            canvas.drawBitmap(markingBitmap, 0f, 0f, null)
        }
    }

    // ── The leading slot: marking and type icon in one bitmap ────────────────
    // spec: search-result-row-icon — One leading slot carries icon and marking together;
    // spec: search-result-ranking — Marking stands beside a type icon.

    private fun icon(size: Int): Bitmap =
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

    @Test
    fun markingOnlyRowIsUnchanged() {
        // A row whose type has no artwork gets the bitmap it always got, the same instance.
        val markingOnly = ResultMarkings.bitmapFor(ResultMarking.FAVORITE)
        assertSame(markingOnly, ResultMarkings.bitmapFor(ResultMarking.FAVORITE, null))
        assertEquals(ResultMarkings.BITMAP_SIZE, markingOnly.width)
        assertEquals(ResultMarkings.BITMAP_SIZE, markingOnly.height)
    }

    @Test
    fun theMarkingKeepsItsPosition() {
        // A 20 dp icon on a 3x display, the size the result rows request.
        val geometry = rowSlotGeometry(iconWidth = 60, iconHeight = 60)
        assertEquals(0, geometry.markingX)
        assertEquals((geometry.height - ResultMarkings.BITMAP_SIZE) / 2, geometry.markingY)
        // The marking is at its own size, inside the slot and never clipped by it.
        assertEquals(ResultMarkings.BITMAP_SIZE, 48)
        assertTrue(geometry.markingX + ResultMarkings.BITMAP_SIZE <= geometry.width)
        assertTrue(geometry.markingY + ResultMarkings.BITMAP_SIZE <= geometry.height)
        // And at the same offset on screen as a row without an icon: the row centres the
        // slot vertically, so the taller slot moves the marking up by exactly the amount
        // the slot grew by, and the two sums are equal.
        for (rowContentHeight in intArrayOf(72, 80, 96)) {
            val withIcon = (rowContentHeight - geometry.height) / 2 + geometry.markingY
            val withoutIcon = (rowContentHeight - ResultMarkings.BITMAP_SIZE) / 2
            assertEquals(withoutIcon, withIcon)
        }
    }

    @Test
    fun theIconIsPlacedBesideTheMarkingAndIsNotClipped() {
        val geometry = rowSlotGeometry(iconWidth = 60, iconHeight = 60)
        // The gap scales with the icon: one fifth of its height, 4 dp at a 20 dp icon.
        assertEquals(60 / 5, geometry.iconX - ResultMarkings.BITMAP_SIZE)
        assertEquals(geometry.iconX + 60, geometry.width)
        assertEquals(0, geometry.iconY)
        // A slot shorter than the marking is never produced: the icon makes it taller.
        assertEquals(60, rowSlotGeometry(60, 60).height)
        assertEquals(ResultMarkings.BITMAP_SIZE, rowSlotGeometry(20, 20).height)
    }

    @Test
    fun iconAndMarkingShareOneBitmapOfTheSlotGeometry() {
        val typeIcon = icon(60)
        val composite = ResultMarkings.bitmapFor(ResultMarking.FAVORITE_AND_PERFECT, typeIcon)
        val geometry = rowSlotGeometry(typeIcon.width, typeIcon.height)
        assertEquals(geometry.width, composite.width)
        assertEquals(geometry.height, composite.height)
        assertEquals(Bitmap.Config.ARGB_8888, composite.config)
        assertNotSame(ResultMarkings.bitmapFor(ResultMarking.FAVORITE_AND_PERFECT), composite)
        // The same call the phone row and the car template row make.
        Canvas(Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)).drawBitmap(composite, 0f, 0f, null)
    }

    @Test
    fun aRowWithAnIconAndNoMarkingSpendsNoWidthOnTheTransparentMarking() {
        val typeIcon = icon(60)
        assertSame(typeIcon, ResultMarkings.bitmapFor(ResultMarking.NONE, typeIcon))
    }

    @Test
    fun compositesAreCachedPerIconAndClearedWithTheMarkingArtwork() {
        val first = icon(60)
        assertSame(
            ResultMarkings.bitmapFor(ResultMarking.FAVORITE, first),
            ResultMarkings.bitmapFor(ResultMarking.FAVORITE, first)
        )
        assertNotSame(
            ResultMarkings.bitmapFor(ResultMarking.FAVORITE, first),
            ResultMarkings.bitmapFor(ResultMarking.FAVORITE, icon(60))
        )
        val compositeBeforeClear = ResultMarkings.bitmapFor(ResultMarking.FAVORITE, first)
        val markingBeforeClear = ResultMarkings.bitmapFor(ResultMarking.FAVORITE)
        ResultMarkings.clear()
        // No composite outlives the icon it was drawn with, and the marking artwork is
        // rebuilt with it.
        val compositeAfterClear = ResultMarkings.bitmapFor(ResultMarking.FAVORITE, first)
        assertNotSame(compositeBeforeClear, compositeAfterClear)
        assertNotSame(markingBeforeClear, ResultMarkings.bitmapFor(ResultMarking.FAVORITE))
        assertSame(compositeAfterClear, ResultMarkings.bitmapFor(ResultMarking.FAVORITE, first))
    }
}
