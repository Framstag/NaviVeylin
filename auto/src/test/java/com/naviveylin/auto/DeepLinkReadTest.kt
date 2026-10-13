package com.naviveylin.auto

import com.naviveylin.core.DeepLinkResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The one network hop of the car deep-link path (spec: auto-deep-links — Short link resolves to a
 * map target, Short link does not resolve): a short link is resolved, then its target parsed; every
 * other payload — a `geo:` URI, a full place link, plain text — is read without any I/O.
 *
 * The host-thread property of the call site (the resolution runs inside the session's coroutine,
 * never on the host path, `guidelines/Design.md` §4/§8) is structural and is measured on the car run,
 * not asserted here; what a unit case can prove is that the resolver is consulted exactly when it
 * should be.
 *
 * Robolectric: reading the resolved target goes through `android.net.Uri`.
 */
@RunWith(RobolectricTestRunner::class)
class DeepLinkReadTest {

    private val shortLink = "https://maps.app.goo.gl/abc123"

    @Test
    fun resolverIsConsultedOnlyForAShortLink() = runTest {
        var calls = 0
        val resolver: suspend (String) -> String? = { calls++; null }

        readDeepLinkPayload(
            payload = "geo:48.8566,2.3522",
            parseIntent = { DeepLinkResult.Destination(com.naviveylin.core.DeepLinkDestination(48.8566, 2.3522, null)) },
            resolver = resolver
        )
        readDeepLinkPayload(
            payload = "https://www.google.com/maps/place/Eiffel+Tower",
            parseIntent = { DeepLinkResult.None },
            resolver = resolver
        )
        readDeepLinkPayload(payload = null, parseIntent = { DeepLinkResult.None }, resolver = resolver)

        assertEquals("no network hop for a geo URI, a place link or no payload", 0, calls)
    }

    @Test
    fun resolvedShortLinkTargetIsParsed() = runTest {
        val result = readDeepLinkPayload(
            payload = shortLink,
            parseIntent = { DeepLinkResult.None },
            resolver = { "https://www.google.com/maps/place/Eiffel+Tower/@48.8584,2.2945,17z" }
        )

        val destination = (result as DeepLinkResult.Destination).destination
        assertEquals(48.8584, destination.lat!!, 1e-6)
        assertEquals("Eiffel Tower", destination.query)
    }

    @Test
    fun unresolvableShortLinkIsUnusable() = runTest {
        val result = readDeepLinkPayload(
            payload = shortLink,
            parseIntent = { DeepLinkResult.None },
            resolver = { null }
        )

        assertEquals(DeepLinkResult.UnusableLink, result)
    }

    @Test
    fun shortLinkResolvingToANonMapTargetIsUnusable() = runTest {
        val result = readDeepLinkPayload(
            payload = shortLink,
            parseIntent = { DeepLinkResult.None },
            resolver = { "https://example.com/not-a-map" }
        )

        assertEquals(DeepLinkResult.UnusableLink, result)
    }
}
