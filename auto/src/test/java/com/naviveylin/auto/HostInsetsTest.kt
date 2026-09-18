package com.naviveylin.auto

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [HostInsets] (design D8, street-name-host-views): derivation of
 * the host/system chrome bands from the stable area — the input the street
 * pill (and, as follow-up, a top-row follow anchor) uses to stay inside the
 * guaranteed-visible map region. Spec: auto/free-driving + auto/browse —
 * "Street name clear of the host/system chrome bands".
 */
@RunWith(RobolectricTestRunner::class)
class HostInsetsTest {

    @Test
    fun emptyStableAreaMeansNoInsets() {
        // No stable area delivered yet -> the guaranteed-visible band is the
        // whole surface; both insets are 0 (real-edge fallback, design D8).
        assertEquals(0 to 0, HostInsets.fromStableArea(600, Rect()))
    }

    @Test
    fun fullSurfaceStableAreaMeansNoInsets() {
        // Host with no chrome reports a stable area spanning the full surface
        // -> same real-edge behavior as a host that delivers nothing.
        assertEquals(0 to 0, HostInsets.fromStableArea(600, Rect(0, 0, 1080, 600)))
    }

    @Test
    fun chromeBandsDeriveFromTheStableAreaEdges() {
        // AAOS emulator shape: top status band 56 px + bottom task band 70 px
        // on a 1080x600 surface (on-device finding, street-name-host-views).
        val (top, bottom) = HostInsets.fromStableArea(600, Rect(0, 56, 1080, 530))
        assertEquals(56, top)
        assertEquals(70, bottom)
    }

    @Test
    fun stableAreaTouchingTheTopEdgeYieldsNoTopInset() {
        val (top, bottom) = HostInsets.fromStableArea(600, Rect(0, 0, 1080, 520))
        assertEquals(0, top)
        assertEquals(80, bottom)
    }

    @Test
    fun topInsetPrefersTheVisibleTopOverTheStableTop() {
        // The stable area can over-reserve a top band the host never draws
        // (e.g. 131 px on the AAOS emulator vs a ~56 px real status bar): the
        // pill's top edge must follow the CURRENTLY-VISIBLE top — the real
        // coverage — and fall back to the stable top only when the visible
        // rect is unknown.
        val stable = Rect(0, 131, 1080, 530)
        assertEquals(56, HostInsets.topInset(Rect(0, 56, 1080, 530), stable))
        // Empty visible rect -> stable top.
        assertEquals(131, HostInsets.topInset(Rect(), stable))
        // Neither known -> 0 (real-edge fallback).
        assertEquals(0, HostInsets.topInset(Rect(), Rect()))
        // Visible spanning the full top -> 0.
        assertEquals(0, HostInsets.topInset(Rect(0, 0, 1080, 530), stable))
    }
}
