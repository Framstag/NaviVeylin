package com.naviveylin.test

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.robolectric.RobolectricTestRunner

/**
 * Guards the sandbox repair every Compose test class depends on
 * ([ComposeSandboxDispatcher] holds the defect, the measurement and the repair).
 *
 * The three steps run in one Robolectric sandbox, which is what makes the case meaningful: step 2
 * writes a Compose snapshot state the way a non-Compose test does through a ViewModel, the paused
 * main looper never runs the dispatch that write posts, and Robolectric's per-test reset drops the
 * posted callbacks. Without [ComposeSandboxRule] on this class, step 3's `setContent` then cannot
 * idle: `RobolectricIdlingStrategy` spends Espresso's whole 60 s budget and fails with
 * `AppNotIdleException: Compose did not get idle …` — measured on the pre-fix tree, where the same
 * shape is also what made 31 `:app` cases burn an hour of the gate.
 *
 * A green run is seconds: the repair leaves the dispatcher exactly as a fresh sandbox's.
 */
@RunWith(RobolectricTestRunner::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ComposeSandboxIsolationTest {

    @get:Rule(order = 0)
    val composeSandbox = ComposeSandboxRule()

    @get:Rule
    val composeRule = createComposeRule()

    /** Step 1: the first Compose case starts Compose's sandbox-wide snapshot pipeline. */
    @Test
    fun step1ComposeStartsTheSnapshotPipeline() {
        composeRule.setContent { Text("step-1") }
        composeRule.waitForIdle()
    }

    /**
     * Step 2: a state write outside a composition, from a case that never composes anything. This is
     * the shape of every non-Compose `:app` test that mutates a ViewModel's Compose state.
     */
    @Test
    fun step2APlainWriteArmsTheDispatcher() {
        sharedState.value += 1
    }

    /** Step 3: the next Compose case still idles — the assertion this guard exists for. */
    @Test
    fun step3TheNextComposeCaseStillIdles() {
        composeRule.setContent { Text("step-3 ${sharedState.value}") }
        composeRule.waitForIdle()
        // The state the plain case wrote is visible to this composition, so the two steps really
        // shared the sandbox's snapshot machinery.
        composeRule.onNodeWithText("step-3 1").assertExists()
    }

    private companion object {
        /** Sandbox-wide (the companion lives as long as the class loader), like a ViewModel's state. */
        val sharedState = mutableStateOf(0)
    }
}
