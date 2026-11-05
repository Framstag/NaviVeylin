package com.naviveylin.ui.map

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.framstag.libosmscout.client.DescriptionEntry
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.ObjectDescription
import com.naviveylin.R
import com.naviveylin.core.details.ContactLink
import com.naviveylin.test.ComposeSandboxRule
import com.naviveylin.ui.attribution.launchContactLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.util.Locale

/**
 * Compose tests for the full-screen details dialog (spec: enhanced-details-sheet):
 * name → mini map → list layout, area as list entry, full address (street +
 * house number) from the native description, title = name ?: address, the
 * empty-description case, and back-gesture close.
 * No @Config — default Robolectric sandbox so the FakeOSMScoutClient JNI
 * stub loads correctly.
 */
@RunWith(RobolectricTestRunner::class)
class LocationDetailsDialogComposeTest {

    @get:Rule(order = 0)
    val composeSandbox = ComposeSandboxRule()

    @get:Rule
    // createAndroidComposeRule so the test can dispatch system back via the
    // activity's OnBackPressedDispatcher.
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun entry(
        label: String = "Hotel Central",
        admin: String? = "Eving/Dortmund/Dortmund"
    ) = LocationEntry().apply {
        this.label = label
        this.lat = 51.5
        this.lon = 7.4
        this.adminRegionHierarchy = admin
    }

    private fun launch(
        entry: LocationEntry,
        description: ObjectDescription? = null,
        client: FakeOSMScoutClient = FakeOSMScoutClient(),
        onShowOnMap: (() -> Unit)? = null,
        onRouteToLocation: (() -> Unit)? = null,
        onDismiss: () -> Unit = {},
        onLaunch: ((ContactLink) -> Boolean)? = null
    ) {
        composeRule.setContent {
            LocationDetailsDialog(
                entry = entry,
                client = client,
                initialMag = 12.0,
                objectDescription = description,
                isFavorite = false,
                groupNames = emptyList(),
                onAddToFavorites = { _, _, _ -> },
                onRemoveFromFavorites = {},
                onRouteToLocation = onRouteToLocation,
                onShowOnMap = onShowOnMap,
                isDarkPresentation = false,
                onDismiss = onDismiss,
                // Production default when the case does not inject its own
                // launcher: the real browser/dialer launch into the activity.
                onLaunch = onLaunch ?: { link -> launchContactLink(composeRule.activity, link) }
            )
        }
        composeRule.waitForIdle()
    }

    private fun locationEntry(sectionKey: String, labelKey: String, value: String) =
        DescriptionEntry().apply {
            this.sectionKey = sectionKey
            this.subsectionKey = ""
            this.labelKey = labelKey
            this.value = value
        }

    @Test
    fun fullScreenDialogShowsNameMiniMapCoordinatesAndArea() {
        launch(entry())

        composeRule.onNodeWithText("Hotel Central").assertIsDisplayed()
        // Interactive mini map embedded below the name
        composeRule.onNodeWithContentDescription("Zoom in").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Zoom out").assertIsDisplayed()
        composeRule.onNodeWithText("51.50000, 7.40000").assertIsDisplayed()
        // Area as a structured list entry
        composeRule.onNodeWithText("Area:").assertIsDisplayed()
        composeRule.onNodeWithText("Eving/Dortmund/Dortmund").assertIsDisplayed()
    }

    @Test
    fun noAreaEntryWhenHierarchyAndIsInMissing() {
        launch(entry(admin = null))

        composeRule.onNodeWithText("Area:").assertDoesNotExist()
    }

    @Test
    fun areaFallsBackToDescriptionIsIn() {
        val description = ObjectDescription(
            listOf(
                locationEntry("Location", "Address", "12"),
                locationEntry("Location", "Location", "Hauptstraße"),
                DescriptionEntry().apply {
                    sectionKey = "Location"
                    subsectionKey = "AdminLevel"
                    labelKey = "IsIn"
                    value = "Dortmund, Dortmund, Nordrhein-Westfalen"
                }
            ),
            51.5,
            7.4
        )
        launch(entry(admin = null), description)

        // Area row + the description's AdminLevel IsIn row both show the value
        composeRule.onAllNodesWithText("Dortmund, Dortmund, Nordrhein-Westfalen").assertCountEquals(2)
    }

    @Test
    fun fullAddressShownWhenDescriptionHasStreetAndHouseNumber() {
        val description = ObjectDescription(
            listOf(
                locationEntry("Location", "Address", "12"),
                locationEntry("Location", "Location", "Hauptstraße"),
                locationEntry("General", "Type", "hotel")
            ),
            51.5,
            7.4
        )
        launch(entry(), description)

        // Merged full-address row: street + house number + city
        // (title also shows the address here, since the object has no name);
        // the Area row shows the full region tree.
        composeRule.onAllNodesWithText("Hauptstraße 12, Dortmund").assertCountEquals(2)
        composeRule.onNodeWithText("Address:").assertIsDisplayed()
        composeRule.onNodeWithText("Location:").assertDoesNotExist()
        composeRule.onNodeWithText("Eving/Dortmund/Dortmund").assertIsDisplayed()
        composeRule.onNodeWithText("General").assertIsDisplayed()
    }

    @Test
    fun addressIncludesCityWhenOnlyHouseNumberPresent() {
        val description = ObjectDescription(
            listOf(locationEntry("Location", "Address", "12")),
            51.5,
            7.4
        )
        launch(entry(), description)

        composeRule.onAllNodesWithText("12, Dortmund").assertCountEquals(2)
    }

    @Test
    fun streetFromReverseLookupWhenDescriptionHasNoStreet() {
        // The location index knows the street the house number belongs to,
        // even though the object's own tags lack addr:street.
        val client = FakeOSMScoutClient().apply {
            addressAt = arrayOf("Hauptstraße", "12", "Dortmund", "44339")
        }
        val description = ObjectDescription(
            listOf(locationEntry("Location", "Address", "12")),
            51.5,
            7.4
        )
        launch(entry(), description, client)

        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithText("Hauptstraße 12, 44339 Dortmund")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Hauptstraße 12, 44339 Dortmund").assertCountEquals(2)
    }

    @Test
    fun streetFallsBackToSearchLabelWhenDescriptionHasNoStreet() {
        // Address search result: label carries the street, the object's
        // description only has the house number (no addr:street tag).
        val description = ObjectDescription(
            listOf(locationEntry("Location", "Address", "12")),
            51.5,
            7.4
        )
        launch(entry(label = "Hauptstraße 12"), description)

        composeRule.onAllNodesWithText("Hauptstraße 12, Dortmund").assertCountEquals(2)
    }

    @Test
    fun noAddressEntryWithoutHouseNumber() {
        val description = ObjectDescription(
            listOf(locationEntry("General", "Type", "hotel")),
            51.5,
            7.4
        )
        launch(entry(), description)

        composeRule.onNodeWithText("Address:").assertDoesNotExist()
    }

    @Test
    fun titleShowsObjectNameWhenPresent() {
        val description = ObjectDescription(
            listOf(
                locationEntry("General", "Name", "Mario's"),
                locationEntry("General", "Type", "restaurant")
            ),
            51.5,
            7.4
        )
        launch(entry(label = "51.50000, 7.40000"), description)

        // Title + the General section's "Name" row both show the name
        composeRule.onAllNodesWithText("Mario's").assertCountEquals(2)
    }

    @Test
    fun titleFallsBackToAddressWhenNoName() {
        val description = ObjectDescription(
            listOf(
                locationEntry("Location", "Address", "12"),
                locationEntry("Location", "Location", "Hauptstraße")
            ),
            51.5,
            7.4
        )
        launch(entry(label = "51.50000, 7.40000", admin = null), description)

        // Title and the address list row both show the full address
        composeRule.onAllNodesWithText("Hauptstraße 12").assertCountEquals(2)
    }

    @Test
    fun emptyDescriptionShowsNoSectionHeaders() {
        launch(entry(), ObjectDescription(emptyList(), 51.5, 7.4))

        composeRule.onNodeWithText("Hotel Central").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Zoom in").assertIsDisplayed()
        composeRule.onNodeWithText("Add to Favorites").assertIsDisplayed()
        composeRule.onNodeWithText("General").assertDoesNotExist()
    }

    @Test
    fun websiteValueStartsTheBrowser() {
        val description = ObjectDescription(
            listOf(locationEntry("Contact", "Website", "https://cafe.de")),
            51.5,
            7.4
        )
        launch(entry(), description)

        composeRule.onNodeWithText("https://cafe.de").performScrollTo().performClick()
        composeRule.waitForIdle()

        val started = shadowOf(composeRule.activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started?.action)
        assertEquals("https://cafe.de", started?.data?.toString())
        // The details dialog stays open after the launch.
        composeRule.onNodeWithText("Website:").assertIsDisplayed()
    }

    @Test
    fun phoneValueOpensTheDialer() {
        // Spec: a phone value opens the dialer prefilled — ACTION_DIAL, never
        // ACTION_CALL (no CALL_PHONE permission, no call placed by a row tap).
        val description = ObjectDescription(
            listOf(locationEntry("Contact", "Phone", "+49 30 1234567")),
            51.5,
            7.4
        )
        launch(entry(), description)

        composeRule.onNodeWithText("+49 30 1234567").performScrollTo().performClick()
        composeRule.waitForIdle()

        val started = shadowOf(composeRule.activity).nextStartedActivity
        assertEquals(Intent.ACTION_DIAL, started?.action)
        assertEquals("tel:+49301234567", started?.data?.toString())
        composeRule.onNodeWithText("Phone:").assertIsDisplayed()
    }

    @Test
    fun aNonContactEntryStaysInert() {
        // Spec: an entry outside the Contact section stays inert — the URL in a
        // General/Name value is the title here, and the row stays plain text.
        val description = ObjectDescription(
            listOf(locationEntry("General", "Name", "https://cafe.de")),
            51.5,
            7.4
        )
        launch(entry(), description)

        // Title + row carry the same text; the row is the second node.
        composeRule.onAllNodesWithText("https://cafe.de").assertCountEquals(2)
        composeRule.onAllNodesWithText("https://cafe.de")[1].performScrollTo().performClick()
        composeRule.waitForIdle()

        assertNull(shadowOf(composeRule.activity).nextStartedActivity)
        composeRule.onNodeWithText("Name:").assertIsDisplayed()
    }

    @Test
    fun anUnhandledLinkShowsTheHint() {
        // Spec: a contact link no app can handle shows the hint and keeps the
        // dialog open. Robolectric records an intent even with no handler, so
        // the failure comes through the injected launcher (design D6).
        val description = ObjectDescription(
            listOf(locationEntry("Contact", "Website", "https://cafe.de")),
            51.5,
            7.4
        )
        val hint = composeRule.activity.getString(R.string.details_contact_link_unhandled)
        launch(entry(), description, onLaunch = { false })

        composeRule.onNodeWithText("https://cafe.de").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithText(hint).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText(hint).assertIsDisplayed()
        composeRule.onNodeWithText("Website:").assertIsDisplayed()
    }

    @Test
    fun aHandledLinkShowsNoHint() {
        // Spec: a handled contact link shows no hint.
        val description = ObjectDescription(
            listOf(locationEntry("Contact", "Website", "https://cafe.de")),
            51.5,
            7.4
        )
        val hint = composeRule.activity.getString(R.string.details_contact_link_unhandled)
        launch(entry(), description, onLaunch = { true })

        composeRule.onNodeWithText("https://cafe.de").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(hint).assertDoesNotExist()
    }

    @Test
    fun backGestureClosesTheDialog() {
        var dismissed = false
        launch(entry(), onDismiss = { dismissed = true })

        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()

        assertTrue("back gesture must dismiss the details dialog", dismissed)
    }

    @Test
    fun navigateToButtonVisibleAndFires() {
        var navigated = false
        launch(entry(), onRouteToLocation = { navigated = true })

        composeRule.onNodeWithText("Calculate route").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Calculate route").performClick()
        assertTrue("Calculate route must fire", navigated)
    }

    @Test
    fun showOnMapButtonVisibleFromLongPress() {
        // Long-press entry: "Show on map" must be available (spec:
        // enhanced-details-sheet — Show on map always available).
        var shown = false
        launch(entry(), onShowOnMap = { shown = true })

        composeRule.onNodeWithText("Show").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Show").performClick()
        assertTrue("Show on map must fire", shown)
    }

    @Test
    fun coordinatesStayLocaleStableOnACommaDecimalDevice() {
        // German device: the pair must not read "51,50000, 7,40000" (spec:
        // enhanced-details-sheet — Coordinates stay unambiguous in a
        // comma-decimal locale; i18n-l10n — Coordinate string is locale-stable).
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)

            launch(entry())

            composeRule.onNodeWithText("51.50000, 7.40000").assertIsDisplayed()
            composeRule.onNodeWithText("51,50000, 7,40000").assertDoesNotExist()
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun coordinateLabelTitleShowsGenericLocation() {
        // Long-press label is a coordinate pair — not a title (spec:
        // enhanced-details-sheet — coordinate label falls back to generic
        // title). The word comes from the surface's resource, not from :core.
        launch(entry(label = "51.50000, 7.40000", admin = null))

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.location_title_generic)
        ).assertIsDisplayed()
        // The coordinates row still shows the pair — but only once (no title).
        composeRule.onAllNodesWithText("51.50000, 7.40000").assertCountEquals(1)
    }

    @Test
    fun coordinateLabelTitleRendersInTheDeviceLanguage() {
        // Spec: enhanced-details-sheet — Generic title renders in the device
        // language. A German device must not read the English word "Location".
        RuntimeEnvironment.setQualifiers("de")
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val german = RuntimeEnvironment.getApplication().getString(R.string.location_title_generic)
            assertTrue("values-de must translate the generic title", german != "Location")

            launch(entry(label = "51.50000, 7.40000", admin = null))

            composeRule.onNodeWithText(german).assertIsDisplayed()
            composeRule.onNodeWithText("Location").assertDoesNotExist()
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun germanDeviceTranslatesDescriptionSectionsAndLabels() {
        // Spec: i18n-l10n — Native description labels are localized at the
        // frontend. The native vocabulary crosses the boundary as values only
        // ("Contact", "Phone", "Website", "Lanes", "MaxSpeed"), so a German
        // device must read the German words and never the raw keys.
        val description = ObjectDescription(
            listOf(
                locationEntry("Way", "MaxSpeed", "50").apply { subsectionKey = "Lanes" },
                locationEntry("Contact", "Phone", "+49 30 1234567"),
                locationEntry("Contact", "Website", "https://cafe.de")
            ),
            51.5,
            7.4
        )
        RuntimeEnvironment.setQualifiers("de")
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)

            launch(entry(), description)

            // Section header, subsection header and label row
            composeRule.onNodeWithText("Kontakt").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Spuren").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Telefon:").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Webseite:").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Höchstgeschwindigkeit:").performScrollTo()
                .assertIsDisplayed()
            // The raw native keys are gone
            composeRule.onNodeWithText("Contact").assertDoesNotExist()
            composeRule.onNodeWithText("Lanes").assertDoesNotExist()
            composeRule.onNodeWithText("Phone:").assertDoesNotExist()
            composeRule.onNodeWithText("Website:").assertDoesNotExist()
            composeRule.onNodeWithText("MaxSpeed:").assertDoesNotExist()
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun anUnmappedDescriptionKeyKeepsTheNativeKey() {
        // Spec: i18n-l10n — Native description labels are localized at the
        // frontend, scenario "A key the table does not carry". The surface owns
        // no wording of its own for a key it does not know, so it renders the
        // key — it never drops the row and never invents a translation.
        val description = ObjectDescription(
            listOf(locationEntry("MysterySection", "UnmappedKey", "some value")),
            51.5,
            7.4
        )
        launch(entry(), description)

        composeRule.onNodeWithText("MysterySection").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("UnmappedKey:").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("some value").performScrollTo().assertIsDisplayed()
    }
}
