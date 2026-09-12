package com.naviveylin.auto

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
 * for the duration of a test (same rule as the app module's
 * `com.naviveylin.test.MainDispatcherRule`).
 *
 * Pass the same dispatcher to `runTest(rule.dispatcher)` and to the
 * `mainDispatcher` / `ioDispatcher` constructor parameters of the class under
 * test so that the test body and background work share ONE
 * [kotlinx.coroutines.test.TestCoroutineScheduler]. That makes
 * `advanceUntilIdle` / `advanceTimeBy` control all coroutine work — including
 * `debounce` timers — instead of racing real thread pools.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
