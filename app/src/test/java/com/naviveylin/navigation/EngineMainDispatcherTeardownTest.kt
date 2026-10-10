package com.naviveylin.navigation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.location.LocationService
import com.naviveylin.test.EngineUnderTestRegistry
import com.naviveylin.test.MainDispatcherRule
import com.naviveylin.test.engineUnderTest
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runners.MethodSorters
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The pair that holds the rule's teardown contract (spec `unit-test-suite-runtime` — A case leaves no
 * process-wide dispatcher state to race a neighbouring case, The teardown joins the subject before the
 * dispatcher is restored).
 *
 * Case `a…` ends the way `MapCanvasViewModelModeTest` did when it reddened in the aggregate run: it builds an
 * engine and returns while that engine is still dispatching through `Dispatchers.Main` from a real thread —
 * the shape of every engine publish that lands on Main (`NavigationEngine`'s native start at `startWithRoute`,
 * the calculation outcomes, the fault publication). Case `b…` is the neighbour that used to fail in its own
 * `MainDispatcherRule.starting` with
 * `IllegalStateException: Dispatchers.Main is used concurrently with setting it`.
 *
 * The guard asserts the *invariant* both cases depend on rather than the microsecond overlap itself: the
 * finished case's engine must no longer be running when the next case installs Main, and the teardown must have
 * *waited* for it (the delta's "a cancel that returns immediately is not enough"). Falsifications (task 6.3):
 * remove `EngineUnderTestRegistry.shutdownAll` from `MainDispatcherRule.finished` — case `b…` then finds the
 * engine still active; or reduce `NavigationEngine.shutdownForTest` to `scope.cancel()` — that variant returns at
 * once and leaves the case's uninterruptible tail running, so case `b…` fails on the join assertion. A first
 * version of this pair asserted only that case `b…` ran, and stayed green under both mutations, because the
 * overlap window is microseconds wide and a 2 ms dispatch cadence misses it; the measured mechanism is in
 * `ki_processing_failures.log` (2026-10-10).
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class EngineMainDispatcherTeardownTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun aCaseThatEndsWithEngineWorkStillDispatchingThroughMain() = runTest(mainDispatcherRule.dispatcher) {
        val engine = engineUnderTest({ FakeOSMScoutClient() }, LocationService(context), context)
        engineBuiltByTheFirstCase = engine
        // A real thread reading `Dispatchers.Main` and dispatching to it, for as long as the engine is allowed
        // to run (`Dispatchers.Main` is null for the duration of `setMain`, so any dispatch in that window is
        // the crash this contract is about). `delay` is the suspension point that lets the rule's cancel stop
        // the loop — without it the loop would outlive even a cancelled scope, and the mutation run's cleanup
        // would not bound it.
        val leaked: Job = engine.engineScope.launch(Dispatchers.Default) {
            while (true) {
                Dispatchers.Main.dispatch(EmptyCoroutineContext, Runnable { })
                delay(2)
            }
        }
        // A tail cancellation cannot interrupt. It is what makes "the teardown *waits*" observable: a
        // `scope.cancel()` that returns at once leaves this running into the next case, while a teardown that
        // joins cannot return before the tail finished (spec `unit-test-suite-runtime` — The teardown joins the
        // subject before the dispatcher is restored: a cancel that returns immediately is not enough).
        // Its length must outlast the gap between the case body and the rule's `finished` — measured on this host
        // as longer than 50 ms, hence 150 ms here, still far inside the seam's 1000 ms join bound.
        // `delay` under `NonCancellable` is a real wait that cancellation does not shorten; it is not a wall-clock
        // wait in the sense the `checkNoWallClockWaits` gate refuses (no `Thread.sleep`, no clock deadline loop).
        val uninterruptibleTail: Job = engine.engineScope.launch(Dispatchers.Default) {
            withContext(NonCancellable) { delay(150) }
        }
        engineBuiltByTheFirstCase = engine
        tailOfTheFirstCase = uninterruptibleTail
        assertTrue("the case ends with the engine's Main work in flight", leaked.isActive)
        assertFalse("the case ends before its uninterruptible tail finished", uninterruptibleTail.isCompleted)
    }

    @Test
    fun bTheFinishedCasesEngineIsStoppedBeforeMainIsRestored() = runTest(mainDispatcherRule.dispatcher) {
        assertNotNull("the first case must have built an engine", engineBuiltByTheFirstCase)
        val engine = engineBuiltByTheFirstCase!!
        assertFalse(
            "the finished case's engine must be stopped before the next case installs Main",
            engine.engineScope.coroutineContext[Job]?.isActive ?: true
        )
        assertTrue(
            "the teardown must wait for the case's work — a cancel alone returns before the tail finished",
            tailOfTheFirstCase?.isCompleted == true
        )
        assertEquals(
            "no engine of the finished case may outlive its teardown (the join must actually wait)",
            0,
            EngineUnderTestRegistry.enginesThatOutlivedTheirTeardownForTest()
        )
        var ranOnMain = false
        withContext(Dispatchers.Main) { ranOnMain = true }
        assertTrue("Main is usable again in the neighbouring case", ranOnMain)
    }

    private companion object {
        /** The engine the first case builds; read by the second, which is the guard. */
        var engineBuiltByTheFirstCase: NavigationEngine? = null

        /** The uninterruptible tail of that engine's work, whose completion is the proof of the join. */
        var tailOfTheFirstCase: Job? = null
    }
}
