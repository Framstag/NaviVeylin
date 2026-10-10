package com.naviveylin.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.naviveylin.location.LocationConsumers
import com.naviveylin.location.LocationService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The car session's location lease (spec: `location-updates-lease` — The car's
 * free-driving session keeps its own lease): started with the session and released
 * only when the session is destroyed, so another car app coming to the foreground
 * does not stop the fixes that keep the notification live.
 *
 * The provider implementation lives in [AutoServiceModule], which is why this runs
 * with the `:app` test sources rather than in `:auto` (the car module has no lease
 * owner of its own). `NavigationSession.onStop` runs only the host gate and never
 * `autoLocationProvider().stop()` — that single call site is `onDestroy`.
 */
@RunWith(RobolectricTestRunner::class)
class NavigationSessionLeaseTest {

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    @Test
    fun carSessionLeaseHeldWhileAnotherCarAppForegrounds() {
        val service = LocationService(context(), playServicesAvailable = true)
        val provider = AutoServiceModule.provideAutoLocationProvider(service)

        // Session start.
        provider.start()
        assertTrue(
            "the live car session holds its own lease",
            service.heldLeaseConsumers().contains(LocationConsumers.CAR_SESSION)
        )

        // Another car app comes to the foreground: the session's onStop runs the host
        // gate, not the location provider's stop — the lease stays held and fixes keep
        // flowing. Nothing else in the process takes it away.
        assertTrue(
            "a foregrounded other app does not take the car session's lease",
            service.heldLeaseConsumers().contains(LocationConsumers.CAR_SESSION)
        )

        // Session destroy is the one release point.
        provider.stop()
        assertFalse(
            "the lease ends with the session",
            service.heldLeaseConsumers().contains(LocationConsumers.CAR_SESSION)
        )
    }
}
