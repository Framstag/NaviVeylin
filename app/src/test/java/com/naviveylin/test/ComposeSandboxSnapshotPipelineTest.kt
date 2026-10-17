package com.naviveylin.test

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Guards the notification half of the sandbox repair ([ComposeSandboxDispatcher] holds the defect,
 * the measurement and the repair).
 *
 * The case builds the sandbox state measured at a `:app` failure (`TODO.md` §195, 2026-10-17) and
 * then does what the failing case did: write a Compose snapshot state with the paused main looper
 * never draining the write, then wait for idle. `GlobalSnapshotManager` carries the write's
 * notification in `sent`; when Robolectric's per-test reset drops its consumer's dispatch, `sent`
 * stays `true`, the manager never calls `Snapshot.sendApplyNotifications()` again, and the runtime —
 * which learns about snapshot changes only from apply notifications — never applies the write. The
 * state stays modified, `ComposeIdlingResource.isIdleNow` reads `hadSnapshotChanges=true` in every
 * iteration, and `setContent` burns Espresso's whole 60 s budget with
 * `AppNotIdleException: Compose did not get idle …`.
 *
 * `sent` is the wedge under test, so the case sets it: it is the *measured* sandbox state
 * (`started=true sent=true` with the dispatcher clean, the looper idle and no thread-local or pinned
 * snapshot), and setting it is the only way to reproduce the residue in one sandbox instead of in
 * the 937-case slice the entry records. Without the repair this case fails at its own `setContent`
 * after 60 s; with it, the write posts a kick, the kick applies the write, and the composition
 * settles in seconds — `ComposeSandboxSnapshotPipelineTest` is red on a tree whose
 * `ComposeSandboxDispatcher.repair()` does not restore the notification.
 */
@RunWith(RobolectricTestRunner::class)
class ComposeSandboxSnapshotPipelineTest {

    @get:Rule(order = 0)
    val composeSandbox = ComposeSandboxRule()

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aWriteWithAWedgedNotificationPipelineStillIdles() {
        wedgeTheNotificationPipeline()
        val counter = mutableStateOf(0)
        // The write the sandbox's manager cannot notify about — the case's own `setContent` is
        // where the failing case made it (its view attach), and the write precedes the idle wait.
        counter.value += 1

        composeRule.setContent { Text("wedged ${counter.value}") }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("wedged 1").assertExists()
    }

    /** The measured failure state: the manager's once-per-write latch stuck, so it never sends. */
    private fun wedgeTheNotificationPipeline() {
        val manager = Class.forName("androidx.compose.ui.platform.GlobalSnapshotManager")
        val instance = manager.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)
        val sent = manager.getDeclaredField("sent").apply { isAccessible = true }.get(instance)
        (sent as AtomicBoolean).set(true)
    }
}
