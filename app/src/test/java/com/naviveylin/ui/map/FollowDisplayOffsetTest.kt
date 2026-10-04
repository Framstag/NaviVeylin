package com.naviveylin.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The display offset the map frame is drawn with (owner finding, 2026-10-03: the analysed leg was
 * centred in the fit's model but sat off-centre and clipped on the device — the follow drift offset
 * of up to the overrun margin was still applied under a session whose fits know nothing about it).
 */
class FollowDisplayOffsetTest {

    @Test
    fun `follow drift moves the frame while follow mode owns the viewport`() {
        assertEquals(
            42f,
            followDisplayOffset(
                followActive = true, sessionOwnsViewport = false, followOffset = 42f, panOffset = 7f
            ),
            0f
        )
    }

    @Test
    fun `a pan window moves the frame while follow is off`() {
        assertEquals(
            7f,
            followDisplayOffset(
                followActive = false, sessionOwnsViewport = false, followOffset = 42f, panOffset = 7f
            ),
            0f
        )
    }

    @Test
    fun `a session that holds the camera draws with no offset at all`() {
        assertEquals(
            "the session's fit placed the content by the viewport alone",
            0f,
            followDisplayOffset(
                followActive = true, sessionOwnsViewport = true, followOffset = 240f, panOffset = 0f
            ),
            0f
        )
    }
}
