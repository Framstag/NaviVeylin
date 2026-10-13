package com.naviveylin.core

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The shared short-link resolver (change `read-shared-google-maps-links`, task 1.6).
 *
 * The transport is one injectable hop, so no case touches the network: the redirect walk, the
 * relative `Location`, the non-terminating chain and the failure path are all driven hop by hop. The
 * chain of the last case is the *real* target of a `maps.app.goo.gl` share link measured on this
 * machine on 2026-10-12 — the shape the device run then had to reproduce.
 *
 * Robolectric: the last case reads the resolved URL through `android.net.Uri`.
 */
@RunWith(RobolectricTestRunner::class)
class ShortLinkResolverTest {

    private val shortLink = "https://maps.app.goo.gl/abc123"

    @Test
    fun followsRedirectToTheTarget() = runTest {
        val target = "https://www.google.com/maps/place/Eiffel+Tower/@48.8584,2.2945,17z"
        val resolver = ShortLinkResolver { url ->
            if (url == shortLink) LinkHop(302, target) else LinkHop(200)
        }

        assertEquals(target, resolver.resolve(shortLink))
    }

    @Test
    fun noRedirectReturnsNull() = runTest {
        val resolver = ShortLinkResolver { LinkHop(200) }

        assertNull(resolver.resolve(shortLink))
    }

    @Test
    fun relativeLocationIsResolvedAgainstTheCurrentUrl() = runTest {
        val resolver = ShortLinkResolver { url ->
            if (url == shortLink) LinkHop(302, "/maps/place/Eiffel+Tower") else LinkHop(200)
        }

        assertEquals("https://maps.app.goo.gl/maps/place/Eiffel+Tower", resolver.resolve(shortLink))
    }

    @Test
    fun nonTerminatingChainReturnsNull() = runTest {
        var hops = 0
        val resolver = ShortLinkResolver { hops++; LinkHop(302, "https://maps.app.goo.gl/hop$hops") }

        assertNull(resolver.resolve(shortLink))
    }

    @Test
    fun transportFailureReturnsNull() = runTest {
        val resolver = ShortLinkResolver { null }

        assertNull(resolver.resolve(shortLink))
    }

    @Test
    fun blankLinkReturnsNull() = runTest {
        val resolver = ShortLinkResolver { LinkHop(200) }

        assertNull(resolver.resolve(null))
        assertNull(resolver.resolve("   "))
    }

    @Test
    fun unparseableUrlNeverThrows() {
        assertNull(ShortLinkResolver.httpHop("not a url"))
    }

    /**
     * The real share link (2026-10-12): `maps.app.goo.gl/WLcJAn72nT6AYxE16` redirects to this place
     * URL, whose place pin is `51.6233854, 7.6687379` while its viewport centre is `…, 7.666163`.
     */
    @Test
    fun realShareLinkChainResolvesToItsPlaceUrl() = runTest {
        val target = "https://www.google.com/maps/place/Rosenweg+20,+59192+Bergkamen/" +
            "@51.6233854,7.666163,17z/data=!3m1!4b1!4m6!3m5!1s0x47b90d0ccc71b16d:0xf3a4a65350d7282a" +
            "!8m2!3d51.6233854!4d7.6687379!16s%2Fg%2F11c26ftynl?entry=tts"
        val resolver = ShortLinkResolver { url ->
            if (url == "https://maps.app.goo.gl/WLcJAn72nT6AYxE16") LinkHop(302, target)
            else LinkHop(200)
        }

        val resolved = resolver.resolve("https://maps.app.goo.gl/WLcJAn72nT6AYxE16")

        assertEquals(target, resolved)
        // What the shared parser must read out of it: the place pin, not the viewport centre.
        val destination = DeepLinkParser.parseUri(resolved).destination
        assertEquals(51.6233854, destination!!.lat!!, 1e-7)
        assertEquals(7.6687379, destination.lon!!, 1e-7)
        assertEquals("Rosenweg 20, 59192 Bergkamen", destination.query)
    }
}
