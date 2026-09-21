package com.naviveylin.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests [runGuardedNotification] (spec: car-host-fault-isolation — No fault escapes
 * into the host path; design D3): the notification build/post and the foreground start
 * are library/platform calls that throw, and an escape from the service would kill the
 * process that carries the driving session.
 */
@RunWith(RobolectricTestRunner::class)
class NotificationFaultIsolationTest {

    @Test
    fun aCompletedActionReportsSuccess() {
        var ran = false

        val ok = runGuardedNotification("notification post") { ran = true }

        assertTrue("the action must run", ran)
        assertTrue(ok)
    }

    @Test
    fun aRefusedForegroundStartIsConfined() {
        // Android 14+: "Starting FGS with type location ... requires ... the app must be
        // in the foreground" — the service must degrade, not die.
        val ok = runGuardedNotification("startForeground") {
            throw SecurityException(
                "Starting FGS with type location requires the app to be in the foreground"
            )
        }

        assertFalse(ok)
    }

    @Test
    fun aThrowingNotificationBuildIsConfined() {
        var afterBuild = false

        val ok = runGuardedNotification("notification post") {
            throw IllegalArgumentException("Serialization failure")
        }
        afterBuild = true

        assertFalse(ok)
        assertTrue("the caller continues after a confined fault", afterBuild)
    }

    @Test
    fun anErrorIsConfinedAsWell() {
        assertFalse(runGuardedNotification("notification post") { throw OutOfMemoryError("payload") })
    }
}
