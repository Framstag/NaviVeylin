package com.naviveylin.core

import android.graphics.Bitmap
import android.graphics.Canvas
import com.naviveylin.core.search.ResultMarking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
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
 * actually looks.
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
}
