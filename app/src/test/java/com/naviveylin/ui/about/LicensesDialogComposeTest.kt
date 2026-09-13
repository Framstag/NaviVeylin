package com.naviveylin.ui.about

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose tests for the license screen (spec: about-dialog — license list
 * reachable, full license text reachable per component, License without
 * distributable text links to its source, license list works offline).
 *
 * The screen is callback-driven and takes its state as a parameter, so every
 * state the ViewModel can produce is rendered here without assets or network.
 */
@RunWith(RobolectricTestRunner::class)
class LicensesDialogComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val shipped = LicenseComponent(
        name = "expat",
        version = "2.8.2",
        identifier = "MIT",
        textFile = "MIT.txt"
    )
    private val linkOnly = LicenseComponent(
        name = "play-services-location",
        group = "com.google.android.gms",
        version = "21.1.0",
        identifier = "LicenseRef-AndroidSDK",
        licenseName = "Android Software Development Kit License",
        licenseUrl = "https://developer.android.com/studio/terms.html"
    )
    private val buildTimeOnly = LicenseComponent(
        name = "protobuf",
        version = "6.33.4",
        identifier = "BSD-3-Clause",
        scope = "buildTimeOnly"
    )

    private fun renderContent(
        state: LicensesUiState,
        onSelect: (LicenseComponent) -> Unit = {},
        onBackToList: () -> Unit = {}
    ) {
        composeRule.setContent {
            LicensesDialogContent(
                state = state,
                onDismiss = {},
                onSelect = onSelect,
                onBackToList = onBackToList
            )
        }
    }

    @Test
    fun `list shows components with identifier and scope`() {
        renderContent(
            LicensesUiState.Content(
                appVersion = "2026-09-13-1",
                components = listOf(shipped, linkOnly, buildTimeOnly)
            )
        )

        composeRule.onNodeWithText("expat").assertIsDisplayed()
        composeRule.onNodeWithText("MIT · 2.8.2").assertIsDisplayed()
        composeRule.onNodeWithText("com.google.android.gms:play-services-location").assertIsDisplayed()
        composeRule.onNodeWithText("LicenseRef-AndroidSDK · 21.1.0").assertIsDisplayed()
        // Build-time-only components are marked as such.
        composeRule.onNodeWithText("BSD-3-Clause · 6.33.4 · build-time only").assertIsDisplayed()
        composeRule.onNodeWithText("2026-09-13-1", substring = true).assertIsDisplayed()
    }

    @Test
    fun `selecting a component reports it to the caller`() {
        var selected: LicenseComponent? = null
        renderContent(
            LicensesUiState.Content(appVersion = "1", components = listOf(shipped, linkOnly)),
            onSelect = { selected = it }
        )

        composeRule.onNodeWithText("com.google.android.gms:play-services-location").performClick()
        assertEquals(linkOnly, selected)
    }

    @Test
    fun `detail shows the full license text`() {
        renderContent(
            LicensesUiState.Content(
                appVersion = "1",
                components = listOf(shipped),
                selected = SelectedLicense(shipped, text = "MIT license text")
            )
        )

        composeRule.onNodeWithText("MIT license text").assertIsDisplayed()
        composeRule.onNodeWithText("MIT").assertIsDisplayed()
    }

    @Test
    fun `detail for a license without distributed text offers the link and never an empty pane`() {
        renderContent(
            LicensesUiState.Content(
                appVersion = "1",
                components = listOf(linkOnly),
                selected = SelectedLicense(
                    linkOnly,
                    linkUrl = "https://developer.android.com/studio/terms.html"
                )
            )
        )

        composeRule.onNodeWithText("Android Software Development Kit License").assertIsDisplayed()
        composeRule.onNodeWithText("https://developer.android.com/studio/terms.html").assertIsDisplayed()
        // The explanation replaces the text pane the application cannot fill.
        composeRule.onNodeWithText(
            "The terms of this license are not distributed with the application. " +
                "Open them at:"
        ).assertIsDisplayed()
    }

    @Test
    fun `error state names the reason instead of showing an empty list`() {
        renderContent(LicensesUiState.Error("no asset"))
        composeRule.onNodeWithText("no asset", substring = true).assertIsDisplayed()
    }

    @Test
    fun `loading state is shown while the inventory is read`() {
        renderContent(LicensesUiState.Loading)
        composeRule.onNodeWithText("Loading license information…").assertIsDisplayed()
    }
}
