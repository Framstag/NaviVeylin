package com.naviveylin.ui.map

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.framstag.libosmscout.client.DescriptionEntry
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.ObjectDescription
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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
        onDismiss: () -> Unit = {}
    ) {
        composeRule.setContent {
            LocationDetailsDialog(
                entry = entry,
                client = client,
                initialMag = 12,
                objectDescription = description,
                isFavorite = false,
                groupNames = emptyList(),
                onAddToFavorites = { _, _, _ -> },
                onRemoveFromFavorites = {},
                onRouteToLocation = null,
                onShowOnMap = null,
                onDismiss = onDismiss
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
    fun backGestureClosesTheDialog() {
        var dismissed = false
        launch(entry(), onDismiss = { dismissed = true })

        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()

        assertTrue("back gesture must dismiss the details dialog", dismissed)
    }
}
