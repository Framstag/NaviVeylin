package com.naviveylin.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit rule that replaces [Dispatchers.Main] with a [StandardTestDispatcher]
 * for the duration of a test.
 *
 * Pass the same dispatcher to `runTest(rule.dispatcher)` and to any injected
 * `defaultDispatcher` of the class under test so that the test body,
 * `viewModelScope`, and background work share ONE [kotlinx.coroutines.test.TestCoroutineScheduler].
 * That makes `advanceUntilIdle` / `advanceTimeBy` control all coroutine work —
 * including `debounce` timers — instead of racing real thread pools
 * ([Dispatchers.Default] / [Dispatchers.IO]), which is what made tests flaky.
 *
 * [finished] also stops the engines the case built ([EngineUnderTestRegistry]): every engine is a
 * process-lifetime scope that keeps dispatching through Main (spec `unit-test-suite-runtime` — A case leaves no
 * process-wide dispatcher state to race a neighbouring case, The teardown joins the subject before the
 * dispatcher is restored), so its queued dispatch must not race this rule's `setMain` in the next case.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        // Cancel and join the engines of the finished case, completing the work they queued on this rule's
        // scheduler *before* Main is restored — `resetMain()` on the same thread would otherwise overlap a
        // dispatch that a real thread of the previous case is still issuing.
        EngineUnderTestRegistry.shutdownAll { dispatcher.scheduler.advanceUntilIdle() }
        Dispatchers.resetMain()
    }
}
