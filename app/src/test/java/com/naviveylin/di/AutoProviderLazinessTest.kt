package com.naviveylin.di

import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.OSMScoutClient
import com.naviveylin.data.FavoriteRepository
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Provider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Car providers are resolved from a car screen's constructor and from host callbacks,
 * which run on the car-app host thread (spec: auto-map-renderer — Renderer
 * initialization off the car-app main thread). Building the native client there syncs
 * the stylesheets, dlopens the native library and runs the client build, so resolving a
 * provider must not construct it.
 */
@RunWith(RobolectricTestRunner::class)
class AutoProviderLazinessTest {

    private val builds = AtomicInteger()

    /** Counts each construction: the fake client is only built when the lambda runs. */
    private fun countingClient(): Provider<OSMScoutClient> = Provider {
        builds.incrementAndGet()
        FakeOSMScoutClient()
    }

    @Test
    fun resolvingTheClientProviderBuildsNothing() {
        val provider = AutoServiceModule.provideAutoClientProvider(countingClient())

        assertEquals("resolving the provider must not build the client", 0, builds.get())

        provider.client()

        assertEquals("the first use builds it once", 1, builds.get())
    }

    @Test
    fun resolvingTheSearchProviderBuildsNothing() {
        AutoServiceModule.provideAutoSearchProvider(countingClient())

        assertEquals(0, builds.get())
    }

    @Test
    fun resolvingTheFavoritesProviderBuildsNothing() {
        val repository = dagger.Lazy {
            builds.incrementAndGet()
            FavoriteRepository()
        }

        val provider = AutoServiceModule.provideAutoFavoritesProvider(repository)

        assertEquals("resolving the provider must not build the repository/client", 0, builds.get())

        // The first use is what builds it (and, in production, the client with it).
        provider.favoriteLocations()

        assertEquals(1, builds.get())
    }
}
