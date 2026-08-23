package com.naviveylin

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Automotive detection for the AAOS trampoline. The default Robolectric
 * device is not automotive; the automotive case is enabled via the
 * PackageManager shadow.
 */
@RunWith(RobolectricTestRunner::class)
class AutomotiveDeviceTest {

    @Test
    fun phoneDeviceIsNotAutomotive() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertFalse(AutomotiveDevice.isAutomotive(context))
    }

    @Test
    fun automotiveDeviceIsDetected() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_AUTOMOTIVE, true)
        assertTrue(AutomotiveDevice.isAutomotive(context))
    }
}
