package com.naviveylin

import android.content.Intent
import android.net.Uri
import com.naviveylin.share.SharedLocationOutcome
import com.naviveylin.share.SharedLocationRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLog

/**
 * The deep-link/share entry points carry identity, not the received value (spec:
 * auto-diagnostics — Diagnostics carry no coordinates / Shared location and deep link
 * log identity, not the URI / Received share text is not logged verbatim).
 *
 * The deep-link path is driven through the real [DeepLinkActivity] under `ShadowLog`;
 * `MainActivity.handleSharedIntent` is a private Hilt-activity path that no test can
 * construct (no Hilt test application in this module), so the message it logs is
 * asserted through its pure seam — the same pattern the car session uses for its
 * inaccessible callbacks.
 */
@RunWith(RobolectricTestRunner::class)
class DeepLinkPrivacyTest {

    /** A coordinate-shaped token: 1-3 integer digits followed by 4+ decimals. */
    private val coordinateShaped = Regex("""-?\b\d{1,3}\.\d{4,}\b""")

    @Test
    fun aGeoDeepLinkLogsActionAndSchemeInsteadOfTheUri() {
        ShadowLog.clear()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:51.5142,7.4653"))

        Robolectric.buildActivity(DeepLinkActivity::class.java, intent).create()

        val lines = ShadowLog.getLogs().map { "${it.tag}: ${it.msg}" }
        val received = lines.firstOrNull { it.contains("Deep link received") }
        assertTrue("the line is logged: $lines", received != null)
        assertTrue("it names the action: $received", received!!.contains("action=android.intent.action.VIEW"))
        assertTrue("it names the URI scheme: $received", received.contains("scheme=geo"))
        assertFalse("the URI must not appear: $received", received.contains("51.5142"))
        assertFalse(
            "no captured line may carry a coordinate-shaped token: $lines",
            lines.any { coordinateShaped.containsMatchIn(it) }
        )
    }

    /**
     * A Google Maps share carries the place name in the subject extra; the forward to the phone
     * surface keeps it, because that is where the name hint comes from (spec: auto-deep-links —
     * Share subject survives the forward).
     */
    @Test
    fun shareSubjectSurvivesTheForward() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "https://maps.app.goo.gl/abc123")
            putExtra(Intent.EXTRA_SUBJECT, "Eiffel Tower")
        }

        val activity = Robolectric.buildActivity(DeepLinkActivity::class.java, intent).create().get()

        val forwarded = shadowOf(activity).nextStartedActivity
        assertEquals(MainActivity::class.java.name, forwarded.component?.className)
        assertEquals("Eiffel Tower", forwarded.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals("https://maps.app.goo.gl/abc123", forwarded.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun theParsedShareMessageCarriesShapeAndLabelOriginOnly() {
        assertEquals(
            "Shared location parsed: shape=coordinates labelHint=present",
            sharedLocationOutcomeMessage(
                SharedLocationOutcome.Request(
                    SharedLocationRequest(lat = 51.5142, lon = 7.4653, label = "Eiffel Tower")
                )
            )
        )
        assertEquals(
            "Shared location parsed: shape=coordinates labelHint=none",
            sharedLocationOutcomeMessage(
                SharedLocationOutcome.Request(SharedLocationRequest(lat = 51.5142, lon = 7.4653))
            )
        )
        assertEquals(
            "Shared location parsed: shape=query labelHint=none",
            sharedLocationOutcomeMessage(
                SharedLocationOutcome.Request(SharedLocationRequest(query = "Ruhrallee 1"))
            )
        )
    }

    /**
     * An unreadable map link logs its shape only — the link text is exactly what the flow
     * refuses to search for, and it must not reach the diagnostics either (spec:
     * share/location-receiving — An unreadable shared map link is reported, never searched).
     */
    @Test
    fun unusableShareLogsShapeOnly() {
        val message = sharedLocationOutcomeMessage(SharedLocationOutcome.UnusableLink)

        assertEquals("Shared location parsed: shape=unusable", message)
        assertFalse("no link text and no coordinate: $message", coordinateShaped.containsMatchIn(message))
        assertFalse("not the link itself: $message", message.contains("http"))
    }

    @Test
    fun aLabelThatIsItselfACoordinatePairNeverReachesTheMessage() {
        val message = sharedLocationOutcomeMessage(
            SharedLocationOutcome.Request(
                SharedLocationRequest(lat = 51.5142, lon = 7.4653, label = "51.51420, 7.46530")
            )
        )

        assertTrue("the label's origin is still named: $message", message.contains("labelHint=present"))
        assertFalse("but never its text: $message", coordinateShaped.containsMatchIn(message))
    }
}
