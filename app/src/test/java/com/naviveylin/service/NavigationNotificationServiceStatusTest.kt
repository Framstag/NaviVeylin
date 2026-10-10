package com.naviveylin.service

import com.naviveylin.R
import com.naviveylin.core.FreeDrivingStatus
import com.naviveylin.core.NavigationState
import com.naviveylin.core.StringResolver
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ongoing service's re-post gate with the free-driving status in the combine
 * (spec: `navigation-ongoing-notification` — Free-driving content comes from the
 * free-driving status; `car-host-fault-isolation` — Bounded host-facing traffic
 * while not visible). The service combines the status flow into its observation;
 * the decision it makes per emission is the pure `hostVisibleContentChanged` gate
 * pinned here.
 */
class NavigationNotificationServiceStatusTest {

    private val resolver = StringResolver { resId, args ->
        when (resId) {
            R.string.navigation_notification_title_free_driving -> "Free driving"
            R.string.road_offroad -> "Offroad"
            R.string.speed_unit_kmh -> "%1${'$'}s km/h".format(args.firstOrNull() ?: 0)
            else -> "?"
        }
    }

    private fun post(status: FreeDrivingStatus?): NotificationPost {
        val content = NavigationNotificationContentFormatter.format(
            NavigationState(isNavigating = false),
            resolver = resolver,
            freeDrivingActive = true,
            freeDrivingStatus = status
        )
        return NotificationPost(content, hint = null)
    }

    @Test
    fun statusChangeRepostsContent() {
        val previous = post(FreeDrivingStatus(roadRef = "B 1", roadName = "Hauptstrasse", speedKmH = 50.0))
        val next = post(FreeDrivingStatus(roadRef = "B 1", roadName = "Hauptstrasse", speedKmH = 51.0))

        assertTrue(
            "a status the driver can see change is re-posted",
            NavigationNotificationContentFormatter.hostVisibleContentChanged(previous, next)
        )
        assertTrue(previous.content.contentText.contains("50 km/h"))
        assertTrue(next.content.contentText.contains("51 km/h"))
    }

    @Test
    fun unchangedStatusDoesNotRepost() {
        val previous = post(FreeDrivingStatus(roadRef = "B 1", roadName = "Hauptstrasse", speedKmH = 50.0))
        val same = post(FreeDrivingStatus(roadRef = "B 1", roadName = "Hauptstrasse", speedKmH = 50.0))

        assertFalse(
            "an identical status emission is deduped",
            NavigationNotificationContentFormatter.hostVisibleContentChanged(previous, same)
        )
    }
}
