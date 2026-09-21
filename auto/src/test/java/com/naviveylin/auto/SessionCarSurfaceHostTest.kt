package com.naviveylin.auto

import android.graphics.Rect
import android.view.Surface
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.SurfaceContainer
import com.naviveylin.core.CarSurfaceOwner
import com.naviveylin.core.DiagnosticsLog
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [SessionCarSurfaceHost] (spec: car-host-fault-isolation — Single-owner
 * car surface; design D1): one registration per session, one owner at a time, and a
 * surface released exactly once on the host's signal — never because a screen stopped.
 */
@RunWith(RobolectricTestRunner::class)
class SessionCarSurfaceHostTest {

    private val host = SessionCarSurfaceHost()
    private val appManager = mockk<AppManager>(relaxed = true)
    private val carContext = mockk<CarContext>(relaxed = true).apply {
        every { getCarService(AppManager::class.java) } returns appManager
    }

    /** Records what the owner received, so the dispatch/ownership rules are observable. */
    private class RecordingOwner : CarSurfaceOwner {
        var adopted: Surface? = null
        var width = 0
        var height = 0
        var dpi = 0.0
        var revokedCount = 0
        var destroyedCount = 0
        var visible: Rect? = null
        var stable: Rect? = null
        var scrolls = 0
        var flings = 0
        var scales = 0
        var clicks = 0

        override fun onCarSurfaceAvailable(surface: Surface, width: Int, height: Int, dpi: Double) {
            adopted = surface
            this.width = width
            this.height = height
            this.dpi = dpi
        }

        override fun onCarSurfaceRevoked() {
            revokedCount++
            adopted = null
        }

        override fun onCarSurfaceDestroyed() {
            destroyedCount++
        }

        override fun onCarVisibleAreaChanged(visible: Rect) {
            this.visible = visible
        }

        override fun onCarStableAreaChanged(stable: Rect) {
            this.stable = stable
        }

        override fun onCarScroll(distanceX: Float, distanceY: Float) {
            scrolls++
        }

        override fun onCarFling(velocityX: Float, velocityY: Float) {
            flings++
        }

        override fun onCarScale(focusX: Float, focusY: Float, scaleFactor: Float) {
            scales++
        }

        override fun onCarClick(x: Float, y: Float) {
            clicks++
        }
    }

    /** An owner whose every callback throws (a screen fault). */
    private class ThrowingOwner : CarSurfaceOwner {
        override fun onCarSurfaceAvailable(surface: Surface, width: Int, height: Int, dpi: Double) =
            throw IllegalStateException("available")

        override fun onCarSurfaceDestroyed() = throw IllegalStateException("destroyed")
        override fun onCarSurfaceRevoked() = throw IllegalStateException("revoked")
        override fun onCarVisibleAreaChanged(visible: Rect) = throw IllegalStateException("visible")
        override fun onCarStableAreaChanged(stable: Rect) = throw IllegalStateException("stable")
        override fun onCarScroll(distanceX: Float, distanceY: Float) = throw IllegalStateException("scroll")
        override fun onCarFling(velocityX: Float, velocityY: Float) = throw IllegalStateException("fling")
        override fun onCarScale(focusX: Float, focusY: Float, scaleFactor: Float) = throw IllegalStateException("scale")
        override fun onCarClick(x: Float, y: Float) = throw IllegalStateException("click")
    }

    private fun surface(): Surface = mockk(relaxed = true)

    private fun container(surface: Surface, w: Int = 1920, h: Int = 720, dpi: Int = 240): SurfaceContainer =
        mockk<SurfaceContainer>(relaxed = true).apply {
            every { this@apply.surface } returns surface
            every { width } returns w
            every { height } returns h
            every { this@apply.dpi } returns dpi
        }

    // ── session registration ──

    @Test
    fun startSessionRegistersExactlyOnce() {
        host.startSession(carContext)
        host.startSession(carContext)

        verify(exactly = 1) { appManager.setSurfaceCallback(host) }
    }

    @Test
    fun endSessionClearsRegistrationAndIsIdempotent() {
        host.startSession(carContext)

        host.endSession()
        host.endSession()

        verify(exactly = 1) { appManager.setSurfaceCallback(null) }
    }

    @Test
    fun sessionRestartRegistersAgain() {
        host.startSession(carContext)
        host.endSession()

        host.startSession(carContext)

        verify(exactly = 2) { appManager.setSurfaceCallback(host) }
    }

    @Test
    fun sessionStartsAfterADroppedConnection() {
        // Spec: car-host-fault-isolation — Session registration follows the host session.
        // The host dropped the connection, so the previous session never ran its end and
        // its registration is still in place. The new session must register with ITS context:
        // otherwise the app keeps no callback (no surface, black map) and calls host APIs
        // through a CarContext whose host session is gone.
        val newManager = mockk<AppManager>(relaxed = true)
        val newContext = mockk<CarContext>(relaxed = true).apply {
            every { getCarService(AppManager::class.java) } returns newManager
        }
        host.startSession(carContext)

        host.startSession(newContext)

        verify(exactly = 1) { appManager.setSurfaceCallback(null) }
        verify(exactly = 1) { newManager.setSurfaceCallback(host) }

        // The host's delivery for the new session reaches the app.
        host.onSurfaceAvailable(container(surface()))
        assertTrue(host.hasSurface())
    }

    @Test
    fun registrationIsNotDuplicatedWithinOneSession() {
        // Spec: car-host-fault-isolation — Session registration is not duplicated within
        // one session. A lifecycle re-entry with the same context must not register twice.
        host.startSession(carContext)
        host.startSession(carContext)
        host.startSession(carContext)

        verify(exactly = 1) { appManager.setSurfaceCallback(host) }
        verify(exactly = 0) { appManager.setSurfaceCallback(null) }
    }

    @Test
    fun sessionEndClearsTheRegistrationForTheNextSession() {
        // Spec: car-host-fault-isolation — Session ends: the registration is cleared, and a
        // later session registers with its own context.
        val newManager = mockk<AppManager>(relaxed = true)
        val newContext = mockk<CarContext>(relaxed = true).apply {
            every { getCarService(AppManager::class.java) } returns newManager
        }
        host.startSession(carContext)

        host.endSession()
        host.startSession(newContext)

        verify(exactly = 1) { appManager.setSurfaceCallback(null) }
        verify(exactly = 1) { newManager.setSurfaceCallback(host) }
    }

    // ── owner dispatch ──

    @Test
    fun surfaceDeliveredWhileNoScreenOwnsItIsAdoptedByTheNextOwner() {
        val s = surface()
        host.startSession(carContext)
        host.onSurfaceAvailable(container(s))

        val owner = RecordingOwner()
        host.attach(owner)

        assertSame(s, owner.adopted)
        assertEquals(1920, owner.width)
        assertEquals(720, owner.height)
        assertEquals(240.0, owner.dpi, 0.0)
        verify(exactly = 0) { s.release() }
    }

    @Test
    fun surfaceLifetimeIsRecordedForDiagnosis() {
        // Spec: car-host-fault-isolation — Host interaction is diagnosable: a host failure
        // needs the surface lifetime (delivered, adopted, released) in the app's own log.
        val file = java.io.File.createTempFile("diag", ".log")
        DiagnosticsLog.initForTest(file)
        try {
            val s = surface()
            host.startSession(carContext)
            host.onSurfaceAvailable(container(s))
            host.onSurfaceDestroyed(container(s))
            // A duplicate destroy must not add a second release entry either.
            host.onSurfaceDestroyed(container(s))

            val entries = DiagnosticsLog.readEntries()
            assertEquals("one adopt, one release", 2, entries.size)
            assertTrue(entries[0].contains("HOST surface adopt"))
            assertTrue(entries[1].contains("HOST surface release"))
        } finally {
            DiagnosticsLog.reset()
            file.delete()
        }
    }

    @Test
    fun eventsGoToTheCurrentOwner() {
        val s = surface()
        val owner = RecordingOwner()
        host.startSession(carContext)
        host.attach(owner)
        host.onSurfaceAvailable(container(s))

        val visible = mockk<Rect>(relaxed = true)
        val stable = mockk<Rect>(relaxed = true)
        host.onVisibleAreaChanged(visible)
        host.onStableAreaChanged(stable)
        host.onScroll(1f, 2f)
        host.onFling(3f, 4f)
        host.onScale(5f, 6f, 2f)
        host.onClick(7f, 8f)

        assertSame(visible, owner.visible)
        assertSame(stable, owner.stable)
        assertEquals(1, owner.scrolls)
        assertEquals(1, owner.flings)
        assertEquals(1, owner.scales)
        assertEquals(1, owner.clicks)
    }

    @Test
    fun eventsWithoutOwnerAreDropped() {
        host.startSession(carContext)
        // No owner: nothing to forward to, and nothing throws.
        host.onVisibleAreaChanged(mockk<Rect>(relaxed = true))
        host.onScroll(1f, 1f)
        host.onScale(1f, 1f, 2f)
        host.onClick(1f, 1f)
    }

    // ── supersession (the screen-transition defect) ──

    @Test
    fun aThrowingScreenFaultIsConfinedToItsCallback() {
        // Spec: car-host-fault-isolation — No fault escapes into the host path. The
        // car-app library rethrows an app exception from a host callback on the main
        // thread, which would kill the process, so a screen fault must degrade here.
        val s = surface()
        host.startSession(carContext)
        host.attach(ThrowingOwner())

        host.onSurfaceAvailable(container(s))
        host.onVisibleAreaChanged(mockk<Rect>(relaxed = true))
        host.onStableAreaChanged(mockk<Rect>(relaxed = true))
        host.onScroll(1f, 1f)
        host.onFling(1f, 1f)
        host.onScale(1f, 1f, 2f)
        host.onClick(1f, 1f)
        // Superseding a throwing owner must not escape either (the outgoing screen's
        // stop-drawing step runs through the same guard).
        host.attach(RecordingOwner())
        host.onSurfaceDestroyed(container(s))
        host.endSession()

        // Reaching this line is the assertion: no callback fault escaped. The
        // session's own work still ran — the surface was released once.
        verify(exactly = 1) { s.release() }
    }

    @Test
    fun detachOfASupersededOwnerDoesNotClearTheNewOwner() {
        val s = surface()
        val outgoing = RecordingOwner()
        val incoming = RecordingOwner()
        host.startSession(carContext)
        host.attach(outgoing)
        host.onSurfaceAvailable(container(s))

        // pushInternal: the incoming screen attaches (ON_START) before the
        // outgoing screen stops (ON_STOP).
        host.attach(incoming)
        host.detach(outgoing)

        verify(exactly = 0) { s.release() }
        assertTrue(host.hasSurface())
        assertSame(s, incoming.adopted)
        // The outgoing screen is told to stop drawing (revoked) but the surface it
        // drew through is neither destroyed nor released.
        assertEquals(1, outgoing.revokedCount)
        assertEquals(0, outgoing.destroyedCount)
        assertNull("the superseded owner no longer holds the surface", outgoing.adopted)
    }

    @Test
    fun incomingScreenStartsBeforeTheOutgoingOneStops() {
        // Spec: car-host-fault-isolation — Single-owner car surface. The car-app contract
        // starts the incoming screen before it stops the outgoing one, so during that window
        // the outgoing screen is still started: the session revokes its surface at the moment
        // the incoming owner attaches, so at most one renderer draws the one session surface.
        val s = surface()
        val outgoing = RecordingOwner()
        val incoming = RecordingOwner()
        host.startSession(carContext)
        host.attach(outgoing)
        host.onSurfaceAvailable(container(s))

        host.attach(incoming)

        // Revoked first, then handed over: the outgoing owner stops drawing before the
        // incoming one is told it may draw.
        assertEquals(1, outgoing.revokedCount)
        assertNull(outgoing.adopted)
        assertSame(s, incoming.adopted)
        verify(exactly = 0) { s.release() }
    }

    @Test
    fun reAttachingTheSameOwnerDoesNotRevokeIt() {
        // The same screen returning from a background round trip keeps its ownership.
        val s = surface()
        val owner = RecordingOwner()
        host.startSession(carContext)
        host.attach(owner)
        host.onSurfaceAvailable(container(s))

        host.detach(owner)
        host.attach(owner)

        assertEquals(0, owner.revokedCount)
        assertSame(s, owner.adopted)
    }

    @Test
    fun reDeliveredSurfaceInstanceIsAdoptedAndReleasedAgain() {
        // Spec: car-host-fault-isolation — Single-owner car surface. The contract is per
        // delivery: an instance delivered again after its destroy is a new delivery, so it
        // must be drawable (and released once more when it is destroyed again).
        val s = surface()
        val owner = RecordingOwner()
        host.startSession(carContext)
        host.attach(owner)
        host.onSurfaceAvailable(container(s))

        host.onSurfaceDestroyed(container(s))
        verify(exactly = 1) { s.release() }

        host.onSurfaceAvailable(container(s))
        assertSame("the re-delivered instance is drawable again", s, owner.adopted)
        assertTrue(host.hasSurface())

        host.onSurfaceDestroyed(container(s))
        verify(exactly = 2) { s.release() }
        assertFalse(host.hasSurface())
    }

    @Test
    fun reDeliveryIsRecordedForDiagnosis() {
        val file = java.io.File.createTempFile("diag", ".log")
        DiagnosticsLog.initForTest(file)
        try {
            val s = surface()
            host.startSession(carContext)
            host.onSurfaceAvailable(container(s))
            host.onSurfaceDestroyed(container(s))
            host.onSurfaceAvailable(container(s))

            val entries = DiagnosticsLog.readEntries()
            assertTrue(
                "the re-delivery is visible in the app's own log: $entries",
                entries.any { it.contains("re-delivered") }
            )
        } finally {
            DiagnosticsLog.reset()
            file.delete()
        }
    }

    @Test
    fun stoppedScreenDoesNotReleaseTheSurface() {
        val s = surface()
        val owner = RecordingOwner()
        host.startSession(carContext)
        host.attach(owner)
        host.onSurfaceAvailable(container(s))

        // onStop: detach + pause, no release anywhere on the app side.
        host.detach(owner)

        verify(exactly = 0) { s.release() }
        assertTrue(host.hasSurface())
        assertEquals(0, owner.destroyedCount)
    }

    @Test
    fun backgroundRoundTripKeepsTheSameSurfaceUsable() {
        val s = surface()
        val owner = RecordingOwner()
        host.startSession(carContext)
        host.attach(owner)
        host.onSurfaceAvailable(container(s))

        host.detach(owner)   // backgrounded
        host.attach(owner)   // foregrounded again

        verify(exactly = 0) { s.release() }
        assertSame(s, owner.adopted)
        assertEquals(0, owner.destroyedCount)
    }

    // ── release: exactly once, on the host's signal ──

    @Test
    fun hostDestroyReleasesOnceAndTellsTheOwnerToStopDrawing() {
        val s = surface()
        val owner = RecordingOwner()
        host.startSession(carContext)
        host.attach(owner)
        host.onSurfaceAvailable(container(s))

        host.onSurfaceDestroyed(container(s))
        // A duplicate destroy callback (host restart, double delivery) must not
        // release the same surface twice.
        host.onSurfaceDestroyed(container(s))

        verify(exactly = 1) { s.release() }
        assertEquals(1, owner.destroyedCount)
        assertFalse(host.hasSurface())
    }

    @Test
    fun replacedSurfaceReleasesThePreviousOneOnce() {
        val first = surface()
        val second = surface()
        val owner = RecordingOwner()
        host.startSession(carContext)
        host.attach(owner)
        host.onSurfaceAvailable(container(first))

        host.onSurfaceAvailable(container(second))

        verify(exactly = 1) { first.release() }
        verify(exactly = 0) { second.release() }
        assertSame(second, owner.adopted)
    }

    @Test
    fun destroyOfAnUnadoptedSurfaceStillReleasesIt() {
        // The host delivered a surface while no screen owned it, then destroyed
        // it: the session still holds (and releases) that surface exactly once.
        val s = surface()
        host.startSession(carContext)
        host.onSurfaceAvailable(container(s))

        host.onSurfaceDestroyed(container(s))

        verify(exactly = 1) { s.release() }
    }

    @Test
    fun endSessionReleasesAHeldSurface() {
        val s = surface()
        val owner = RecordingOwner()
        host.startSession(carContext)
        host.attach(owner)
        host.onSurfaceAvailable(container(s))

        host.endSession()

        verify(exactly = 1) { s.release() }
        assertEquals(1, owner.destroyedCount)
        assertFalse(host.hasSurface())
    }

    @Test
    fun endSessionWithoutSurfaceReleasesNothing() {
        val s = surface()
        host.startSession(carContext)

        host.endSession()

        verify(exactly = 0) { s.release() }
    }

    // ── release: scoped to the destroyed instance ──

    @Test
    fun destroyOfASupersededSurfaceKeepsTheLiveSurface() {
        // Spec: car-host-fault-isolation — Single-owner car surface. The host delivered a
        // newer surface and then reports the destroy of the one it delivered before: the
        // session must keep the newer surface (clearing it would leave every later screen
        // without a surface, and releasing it would disconnect the buffer queue the host is
        // still compositing through).
        val first = surface()
        val second = surface()
        val owner = RecordingOwner()
        host.startSession(carContext)
        host.attach(owner)
        host.onSurfaceAvailable(container(first))
        host.onSurfaceAvailable(container(second))

        host.onSurfaceDestroyed(container(first))

        verify(exactly = 1) { first.release() }
        verify(exactly = 0) { second.release() }
        assertTrue("the live surface is still held", host.hasSurface())
        assertEquals("the owner is not told to stop drawing", 0, owner.destroyedCount)
    }

    @Test
    fun liveSurfaceSurvivesASupersededDestroyAndIsStillUsable() {
        val first = surface()
        val second = surface()
        host.startSession(carContext)
        host.onSurfaceAvailable(container(first))
        host.onSurfaceAvailable(container(second))

        host.onSurfaceDestroyed(container(first))

        // A screen that attaches afterwards is handed the live surface.
        val incoming = RecordingOwner()
        host.attach(incoming)
        assertSame(second, incoming.adopted)

        // Its own destroy still releases it once and tells the owner.
        host.onSurfaceDestroyed(container(second))
        verify(exactly = 1) { second.release() }
        assertEquals(1, incoming.destroyedCount)
        assertFalse(host.hasSurface())
    }

    @Test
    fun repeatedDestroyOfASupersededSurfaceReleasesItOnce() {
        val first = surface()
        val second = surface()
        host.startSession(carContext)
        host.onSurfaceAvailable(container(first))
        host.onSurfaceAvailable(container(second))

        host.onSurfaceDestroyed(container(first))
        host.onSurfaceDestroyed(container(first))

        verify(exactly = 1) { first.release() }
        verify(exactly = 0) { second.release() }
        assertTrue(host.hasSurface())
    }

    @Test
    fun destroyOfANeverAdoptedSurfaceKeepsTheLiveSurface() {
        // A destroy for an instance this session never adopted (delivered elsewhere, or
        // already released) must not touch the surface it holds now.
        val live = surface()
        val stranger = surface()
        host.startSession(carContext)
        host.onSurfaceAvailable(container(live))

        host.onSurfaceDestroyed(container(stranger))

        verify(exactly = 1) { stranger.release() }
        verify(exactly = 0) { live.release() }
        assertTrue(host.hasSurface())
    }
}
