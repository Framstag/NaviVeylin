package com.naviveylin.core

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Tests for the shared accuracy rule (spec: `location-permissions` — The granted
 * accuracy class governs provider updates): the class follows the two grants, an
 * approximate-only grant is a working state, and a grant change is visible on the
 * next read (no cached value).
 */
@RunWith(RobolectricTestRunner::class)
class LocationGrantTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    @After
    fun clearGrants() {
        shadowOf(app).denyPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    @Test
    fun preciseAndCoarseGrantIsPrecise() {
        shadowOf(app).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        assertEquals(AccuracyClass.PRECISE, LocationGrant.accuracyClass(app))
        assertTrue(LocationGrant.hasPrecise(app))
        assertTrue(LocationGrant.isGranted(app))
    }

    @Test
    fun coarseOnlyGrantIsApproximateAndStillGranted() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)

        assertEquals(AccuracyClass.APPROXIMATE, LocationGrant.accuracyClass(app))
        assertFalse("approximate must not pass the navigation gate", LocationGrant.hasPrecise(app))
        assertTrue("approximate is a working state, not a failure", LocationGrant.isGranted(app))
    }

    @Test
    fun fineWithoutCoarseIsStillPrecise() {
        // Possible below API 31, where the two grants are independent.
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)

        assertEquals(AccuracyClass.PRECISE, LocationGrant.accuracyClass(app))
        assertTrue(LocationGrant.hasPrecise(app))
    }

    @Test
    fun noGrantIsNone() {
        assertEquals(AccuracyClass.NONE, LocationGrant.accuracyClass(app))
        assertFalse(LocationGrant.hasPrecise(app))
        assertFalse(LocationGrant.isGranted(app))
    }

    @Test
    fun grantChangeIsVisibleOnTheNextRead() {
        assertEquals(AccuracyClass.NONE, LocationGrant.accuracyClass(app))

        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        assertEquals(AccuracyClass.APPROXIMATE, LocationGrant.accuracyClass(app))

        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        assertEquals(AccuracyClass.PRECISE, LocationGrant.accuracyClass(app))

        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        assertEquals(AccuracyClass.APPROXIMATE, LocationGrant.accuracyClass(app))
    }
}
