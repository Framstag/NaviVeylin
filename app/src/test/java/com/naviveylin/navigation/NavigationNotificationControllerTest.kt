package com.naviveylin.navigation

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.naviveylin.core.DrivingModeProvider
import com.naviveylin.core.NavigationState
import com.naviveylin.core.NavigationViewModel
import com.naviveylin.service.NavigationNotificationService
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the notification controller's driving-state decision and the
 * service start/stop wiring (task 2.5; specs R1 active-driving notification,
 * R2 process survival). Navigation transitions are driven through a fake
 * [NavigationViewModel] observed by the real [NavigationStateProvider]
 * mirror; the controller's service calls are recorded via a
 * [RecordingContext].
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class NavigationNotificationControllerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val baseContext: Application = ApplicationProvider.getApplicationContext()
    private var controller: NavigationNotificationController? = null

    @After
    fun tearDown() {
        controller?.dispose()
        controller = null
    }

    // ── pure decision ──

    @Test
    fun shouldRunForDrivingModes() {
        val controller = NavigationNotificationController(
            baseContext, NavigationStateProvider(), DrivingModeProviderImpl()
        )
        assertFalse(controller.shouldRun(NavigationState(), false))
        assertTrue(controller.shouldRun(NavigationState(isNavigating = true), false))
        assertTrue(controller.shouldRun(NavigationState(), true))
        assertTrue(controller.shouldRun(NavigationState(isNavigating = true), true))
    }

    // ── wiring: navigation start → service, stop → release ──

    @Test
    fun navStartStartsServiceAndClearStops() = runTest(mainDispatcherRule.dispatcher) {
        val recording = RecordingContext(baseContext)
        val stateProvider = NavigationStateProvider()
        val driving = DrivingModeProviderImpl()
        val fakeVm = FakeNavigationViewModel()
        stateProvider.observe(fakeVm)
        controller = NavigationNotificationController(recording, stateProvider, driving)

        advanceUntilIdle()
        assertNoServiceCall("idle browse mode", recording)

        fakeVm.state.value = NavigationState(isNavigating = true)
        advanceUntilIdle()
        assertStartRequested("navigation active", recording)

        fakeVm.state.value = NavigationState()
        advanceUntilIdle()
        assertStopRequested("navigation ended", recording)
    }

    @Test
    fun freeDrivingStartsServiceAndExplicitExitStops() = runTest(mainDispatcherRule.dispatcher) {
        val recording = RecordingContext(baseContext)
        val stateProvider = NavigationStateProvider()
        val driving = DrivingModeProviderImpl()
        controller = NavigationNotificationController(recording, stateProvider, driving)

        advanceUntilIdle()
        driving.setFreeDriving(DrivingModeProvider.SURFACE_AUTO, true)
        advanceUntilIdle()
        assertStartRequested("free driving active", recording)

        driving.setFreeDriving(DrivingModeProvider.SURFACE_AUTO, false)
        advanceUntilIdle()
        assertStopRequested("free driving ended", recording)
    }

    @Test
    fun orCombinedKeepsServiceWhileAnySurfaceDrives() = runTest(mainDispatcherRule.dispatcher) {
        val recording = RecordingContext(baseContext)
        val stateProvider = NavigationStateProvider()
        val driving = DrivingModeProviderImpl()
        val fakeVm = FakeNavigationViewModel()
        stateProvider.observe(fakeVm)
        controller = NavigationNotificationController(recording, stateProvider, driving)

        fakeVm.state.value = NavigationState(isNavigating = true)
        advanceUntilIdle()
        assertStartRequested("nav active", recording)

        // Nav ends but the car surface keeps free-driving → service stays.
        fakeVm.state.value = NavigationState()
        driving.setFreeDriving(DrivingModeProvider.SURFACE_AUTO, true)
        advanceUntilIdle()
        assertServiceStillActive(recording)

        driving.setFreeDriving(DrivingModeProvider.SURFACE_AUTO, false)
        advanceUntilIdle()
        assertStopRequested("all surfaces idle", recording)
    }

    // ── helpers ──

    private fun serviceCalls(recording: RecordingContext): List<Intent> =
        recording.started.filter { it.component?.className == NavigationNotificationService::class.java.name }

    private fun assertNoServiceCall(message: String, recording: RecordingContext) {
        assertTrue("$message: no service call expected", serviceCalls(recording).isEmpty())
    }

    private fun assertStartRequested(message: String, recording: RecordingContext) {
        assertTrue(
            "$message: ACTION_START expected",
            serviceCalls(recording).any { it.action == NavigationNotificationService.ACTION_START }
        )
    }

    private fun assertStopRequested(message: String, recording: RecordingContext) {
        assertTrue(
            "$message: ACTION_STOP expected",
            serviceCalls(recording).any { it.action == NavigationNotificationService.ACTION_STOP }
        )
    }

    /** No stop and no restart while a driving surface is still active. */
    private fun assertServiceStillActive(recording: RecordingContext) {
        val calls = serviceCalls(recording)
        assertTrue("no stop while still driving", calls.none { it.action == NavigationNotificationService.ACTION_STOP })
        assertTrue("no redundant restart", calls.count { it.action == NavigationNotificationService.ACTION_START } <= 1)
    }

    /** Minimal VM so [NavigationStateProvider.observe] mirrors test state. */
    private class FakeNavigationViewModel : NavigationViewModel {
        override val state = MutableStateFlow(NavigationState())
        override fun stopNavigation() = Unit
        override fun navigateTo(destLat: Double, destLon: Double, destinationName: String?) = Unit
        override fun clearError() = Unit
        override fun reportError(message: String) = Unit
    }

    /** Records service start/stop calls, delegates everything else. */
    private class RecordingContext(base: Context) : ContextWrapper(base) {
        val started = mutableListOf<Intent>()

        override fun startService(service: Intent?): ComponentName? {
            service?.let { started += it }
            return super.startService(service)
        }

        @Suppress("DEPRECATION")
        override fun startForegroundService(service: Intent?): ComponentName? {
            service?.let { started += it }
            return super.startForegroundService(service)
        }
    }
}
