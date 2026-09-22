package com.naviveylin.auto

import com.naviveylin.core.DiagnosticsLog
import java.io.File
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests the car-facing confinement seam (spec: car-host-fault-isolation — No fault
 * escapes into the host path; scenarios "Screen push from a click action throws",
 * "Screen mutation from a session observer throws"; design D1/D3).
 *
 * The car-app library rethrows an app exception from a host callback on the main thread
 * (`RemoteUtils.dispatchCallFromHost`, car-app 1.7.0), so a guarded mutation has to *not*
 * propagate — and the rejection has to be recorded, unthrottled, or a device run cannot
 * tell "the host refused this" from "this never happened".
 *
 * Robolectric (default sandbox) is required because the rejection path calls
 * `android.util.Log`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CarHostGuardsTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val diagnostics = File.createTempFile("guards", ".log")

    /**
     * Point the log at the temp file once. Re-pointing it mid-test drops the buffered
     * entries (`configureTarget` clears them), so [entries] only reads.
     */
    @Before
    fun pointTheLogAtTheTempFile() {
        DiagnosticsLog.initForTest(diagnostics)
    }

    private fun entries(): List<String> = DiagnosticsLog.readEntries()

    @After
    fun tearDown() {
        DiagnosticsLog.reset()
        diagnostics.delete()
    }

    // ── the synchronous seam (a host callback runs on the library's dispatch path) ──

    @Test
    fun aMutationThatCompletesReportsSuccessAndLogsNothing() {
        var ran = false

        val landed = guardedHostCall("push DetailsScreen") { ran = true }

        assertTrue("the mutation ran", ran)
        assertTrue("a landed mutation reports success", landed)
        assertTrue("nothing to report: ${entries()}", entries().none { it.contains("rejected") })
    }

    @Test
    fun aThrowingMutationIsConfinedAndReportedWithItsName() {
        val landed = guardedHostCall("push DetailsScreen") {
            throw IllegalStateException("Accessed the car host after it became invalidated")
        }

        assertFalse("a rejected push must not report success", landed)
        val rejected = entries().filter { it.contains("rejected") }
        assertEquals("exactly one rejection entry: $rejected", 1, rejected.size)
        assertTrue(
            "the entry names the mutation: $rejected",
            rejected.single().contains("push DetailsScreen rejected")
        )
        assertTrue(
            "the entry carries the failure: $rejected",
            rejected.single().contains("Accessed the car host after it became invalidated")
        )
        assertTrue(
            "a host mutation is recorded under the host tag: $rejected",
            rejected.single().contains("] HOST ")
        )
    }

    @Test
    fun everyRejectionIsRecordedWithoutThrottling() {
        repeat(3) {
            guardedHostCall("popToRoot") { throw IllegalStateException("host gone") }
        }
        guardedHostCall("remove ErrorOverlayScreen") { throw IllegalStateException("host gone") }

        val rejected = entries().filter { it.contains("rejected") }
        assertEquals("every rejection is visible: $rejected", 4, rejected.size)
        assertEquals(3, rejected.count { it.contains("popToRoot rejected") })
        assertEquals(1, rejected.count { it.contains("remove ErrorOverlayScreen rejected") })
    }

    @Test
    fun aRejectionIsReportedAsOneLineWithTheFirstFrame() {
        reportRejectedCarWork("push X", IllegalStateException("boom"))
        reportRejectedCarWork(
            "pop Y",
            IllegalStateException("bare").apply { stackTrace = emptyArray() }
        )

        val rejected = entries().filter { it.contains("rejected") }
        assertEquals("one entry per rejection: $rejected", 2, rejected.size)
        assertTrue("the entry carries a frame: ${rejected[0]}", rejected[0].contains(" at "))
        assertTrue(
            "a frameless throwable yields no suffix: ${rejected[1]}",
            rejected[1].endsWith("bare")
        )
    }

    @Test
    fun aSessionLevelStepIsRecordedUnderTheSessionTag() {
        guardedHostCall("stop location source", tag = SESSION_DIAG_TAG) {
            throw IllegalStateException("no location provider")
        }

        val rejected = entries().filter { it.contains("rejected") }
        assertEquals("the step is reported once: $rejected", 1, rejected.size)
        assertTrue("recorded under SESSION: $rejected", rejected.single().contains("] SESSION "))
        assertFalse(
            "a non-host step must not read as a host call: $rejected",
            rejected.single().contains("] HOST ")
        )
    }

    // ── the scope handler (a coroutine body has no host callback to guard) ──

    @Test
    fun aThrowingCoroutineBodyIsConfinedToItself() = runTest(mainDispatcher.dispatcher) {
        val scope = carSessionScope(mainDispatcher.dispatcher)
        var siblings = 0

        scope.launch { throw IllegalStateException("observer boom") }
        scope.launch { siblings++ }
        scope.launch { siblings++ }
        advanceUntilIdle()

        assertEquals("the siblings of a faulting observation still ran", 2, siblings)
    }

    @Test
    fun aFaultingSessionBodyIsLoggedWithItsKey() = runTest(mainDispatcher.dispatcher) {
        val scope = carSessionScope(mainDispatcher.dispatcher)

        scope.launch(CoroutineName("trip")) { throw IllegalStateException("trip boom") }
        advanceUntilIdle()

        val faults = entries().filter { it.contains("session work") }
        assertEquals("the fault is recorded once: $faults", 1, faults.size)
        assertTrue("the key names the failing work: $faults", faults.single().contains("'trip' failed"))
    }

    @Test
    fun theHandlerConfinesAFaultForAnyNamedScope() = runTest(mainDispatcher.dispatcher) {
        val scope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + mainDispatcher.dispatcher +
                carFaultHandler(noun = "screen work", diagTag = "SCREEN", logTag = "TestScreen")
        )
        var after = 0

        scope.launch(CoroutineName("rendererInit")) { throw IllegalStateException("boom") }
        scope.launch { after++ }
        advanceUntilIdle()

        assertEquals("the screen's other coroutines keep running", 1, after)
        val faults = entries().filter { it.contains("screen work") }
        assertEquals("named as the caller asked: $faults", 1, faults.size)
        assertTrue(faults.single().contains("'rendererInit' failed"))
        assertTrue("recorded under the caller's tag: $faults", faults.single().contains("] SCREEN "))
    }
}
