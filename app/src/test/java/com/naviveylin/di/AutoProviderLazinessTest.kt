package com.naviveylin.di

import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.OSMScoutClient
import com.naviveylin.data.FavoriteRepository
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Provider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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

    /**
     * The group order (spec `group-ordering`) is read through the same lazy
     * repository as [AutoFavoritesProvider.favoriteLocations], so the car screen can
     * collect it in its constructor without building the native client there.
     */
    @Test
    fun theGroupOrderAccessorIsLazyLikeTheFavoritesAccessor() {
        val repository = dagger.Lazy {
            builds.incrementAndGet()
            FavoriteRepository()
        }
        val provider = AutoServiceModule.provideAutoFavoritesProvider(repository)

        assertEquals("resolving the provider must not build the repository/client", 0, builds.get())

        provider.groupOrder()

        assertEquals(1, builds.get())
    }

    /**
     * The starred order (spec `starred-ordering`) is read through the same lazy
     * repository, so the car screen can collect it in its constructor without building
     * the native client there, and it hands out the repository's own starred channel.
     */
    @Test
    fun theStarredOrderAccessorIsLazyLikeTheFavoritesAccessor() {
        var built: FavoriteRepository? = null
        val repository = dagger.Lazy {
            builds.incrementAndGet()
            FavoriteRepository().also { built = it }
        }
        val provider = AutoServiceModule.provideAutoFavoritesProvider(repository)

        assertEquals("resolving the provider must not build the repository/client", 0, builds.get())

        val starred = provider.starredOrder()

        assertEquals(1, builds.get())
        assertSame(
            "the car reads the repository's own starred channel",
            built?.starredOrder,
            starred
        )
    }
}
