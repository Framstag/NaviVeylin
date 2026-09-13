package com.naviveylin.navigation

import com.naviveylin.core.DrivingModeProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the OR-combined, retain-on-death semantics of the shared
 * free-driving flag (design D4): any active surface ⇒ true; a surface that
 * stops publishing (dies) keeps its vote; only an explicit set(false) clears
 * it. (Task 1.1 verify criterion.)
 */
class DrivingModeProviderImplTest {

    private val provider = DrivingModeProviderImpl()

    @Test
    fun initiallyInactive() {
        assertFalse(provider.freeDrivingActive.value)
    }

    @Test
    fun oneActiveSurfaceActivates() {
        provider.setFreeDriving(DrivingModeProvider.SURFACE_PHONE, true)
        assertTrue(provider.freeDrivingActive.value)
    }

    @Test
    fun orCombinedAnySurface() {
        provider.setFreeDriving(DrivingModeProvider.SURFACE_PHONE, true)
        assertTrue(provider.freeDrivingActive.value)

        provider.setFreeDriving(DrivingModeProvider.SURFACE_PHONE, false)
        assertFalse("flag must follow the only surface", provider.freeDrivingActive.value)

        provider.setFreeDriving(DrivingModeProvider.SURFACE_AUTO, true)
        assertTrue("car surface alone activates", provider.freeDrivingActive.value)

        provider.setFreeDriving(DrivingModeProvider.SURFACE_PHONE, true)
        provider.setFreeDriving(DrivingModeProvider.SURFACE_AUTO, false)
        assertTrue("phone still driving keeps it active", provider.freeDrivingActive.value)

        provider.setFreeDriving(DrivingModeProvider.SURFACE_PHONE, false)
        assertFalse("last surface cleared", provider.freeDrivingActive.value)
    }

    @Test
    fun surfaceDeathRetainsVote() {
        // Surface death = no further set(false) calls, exactly what happens
        // when an Activity is cleared or a car session is destroyed mid-drive.
        provider.setFreeDriving(DrivingModeProvider.SURFACE_AUTO, true)
        // ... nothing more from that surface ...
        assertTrue("retained on death", provider.freeDrivingActive.value)
    }

    @Test
    fun explicitExitClears() {
        provider.setFreeDriving(DrivingModeProvider.SURFACE_AUTO, true)
        provider.setFreeDriving(DrivingModeProvider.SURFACE_AUTO, false)
        assertFalse(provider.freeDrivingActive.value)
    }

    @Test
    fun repeatedSetsDoNotChangeEmission() {
        provider.setFreeDriving(DrivingModeProvider.SURFACE_PHONE, true)
        provider.setFreeDriving(DrivingModeProvider.SURFACE_PHONE, true)
        assertTrue(provider.freeDrivingActive.value)
    }
}
