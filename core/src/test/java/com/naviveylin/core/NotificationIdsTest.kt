package com.naviveylin.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Tests for [NotificationIds] (spec: navigation-ongoing-notification — Distinct
 * notification identity).
 *
 * The platform keys a notification by (tag, id) and the app posts these with a null tag from
 * one process, so a collision silently replaces the earlier notification — which for the
 * ongoing navigation notification means losing the foreground-service notification and the car
 * rail-widget turn hint.
 */
class NotificationIdsTest {

    @Test
    fun theAppNotificationIdentitiesAreDistinct() {
        val ids = listOf(
            NotificationIds.MAP_DOWNLOAD,
            NotificationIds.NAVIGATION_ONGOING,
            NotificationIds.MAP_STYLE_NOTICE
        )

        assertEquals("identities: $ids", ids.size, ids.distinct().size)
    }

    @Test
    fun theStyleNoticeDoesNotUseTheOngoingNavigationIdentity() {
        assertNotEquals(NotificationIds.NAVIGATION_ONGOING, NotificationIds.MAP_STYLE_NOTICE)
    }

    @Test
    fun theDownloadNotificationDoesNotUseTheOngoingNavigationIdentity() {
        assertNotEquals(NotificationIds.NAVIGATION_ONGOING, NotificationIds.MAP_DOWNLOAD)
    }
}
