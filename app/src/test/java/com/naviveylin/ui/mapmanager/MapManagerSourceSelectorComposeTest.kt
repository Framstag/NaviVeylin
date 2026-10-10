package com.naviveylin.ui.mapmanager

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.naviveylin.core.mapsource.MapSource
import com.naviveylin.core.mapsource.MapSourceKind
import com.naviveylin.core.mapsource.RepositoryFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose tests for the map-source selector row.
 *
 * Spec: map-download-ui — "Provider selection and refresh" / "Repository source exposes its base URL" /
 * "Test action reports its outcome in the row" / "URL field remembers the last value per source";
 * map-source-selection — "Test succeeds" / "Test fails on transport or status" / "An unencrypted
 * repository source is marked as such" / "The repository URL field is presented as URL input with its
 * format shown";
 * map-download-infrastructure — "A denied cleartext request reports the denial itself" / "A base URL
 * that cannot be parsed is reported as an unusable URL".
 *
 * The composable is driven with a state, so the row's contract is asserted without a view model or a
 * server; the view model's own cases are in `MapManagerRepositoryTreeTest`.
 */
@RunWith(RobolectricTestRunner::class)
class MapManagerSourceSelectorComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val repository = MapSource(MapSourceKind.REPOSITORY, "https://maps.example.org/repo")

    @Test
    fun selectorListsBothSources() {
        show(state(activeSource = MapSource.BuiltInProvider, draft = ""))

        composeRule.onNodeWithText("karry.cz").assertIsDisplayed()
        composeRule.onNodeWithText("Repository").assertIsDisplayed()
    }

    @Test
    fun repositorySelectionRevealsUrlFieldAndTestAction() {
        show(state(activeSource = repository, draft = repository.baseUrl))

        composeRule.onNodeWithTag("repository-url-field").assertIsDisplayed()
        composeRule.onNodeWithTag("repository-url-test").assertIsDisplayed()
    }

    @Test
    fun noUrlFieldWhileTheProviderSourceIsActiveWithoutAStoredUrl() {
        show(state(activeSource = MapSource.BuiltInProvider, draft = ""))

        composeRule.onNodeWithTag("repository-url-field").assertDoesNotExist()
    }

    @Test
    fun testSuccessShowsRegionAndLeafCounts() {
        show(
            state(
                activeSource = repository,
                draft = repository.baseUrl,
                outcome = SourceTestOutcome.Success("${repository.baseUrl}/names.json", 3, 42)
            )
        )

        composeRule.onNodeWithText("Repository OK — 3 regions, 42 maps").assertIsDisplayed()
    }

    @Test
    fun testFailureNamesTheUrlAndTheReason() {
        val failed = "${repository.baseUrl}/names.json"
        show(
            state(
                activeSource = repository,
                draft = repository.baseUrl,
                outcome = SourceTestOutcome.Failure(failed, RepositoryFailure.HttpStatus(503))
            )
        )

        composeRule.onNodeWithText("$failed could not be reached (503)").assertIsDisplayed()
    }

    @Test
    fun anUnsupportedIndexIsReportedPerReason() {
        val tested = "${repository.baseUrl}/names.json"
        show(
            state(
                activeSource = repository,
                draft = repository.baseUrl,
                outcome = SourceTestOutcome.Failure(tested, RepositoryFailure.UnsupportedSchema(7))
            )
        )

        composeRule.onNodeWithText("$tested serves index version 7, which is not supported").assertIsDisplayed()
    }

    @Test
    fun aCleartextRefusalIsExplainedAsAPolicyDecision() {
        val tested = "http://truenas.home.framstag.com:30123/names.json"
        show(
            state(
                activeSource = repository,
                draft = repository.baseUrl,
                outcome = SourceTestOutcome.Failure(tested, RepositoryFailure.CleartextBlocked)
            )
        )

        val shown = "$tested is unencrypted HTTP, which this build refuses — use an https URL instead"
        composeRule.onNodeWithText(shown).assertIsDisplayed()
        // The platform's own sentence is not what the user is shown (spec map-download-infrastructure
        // — "A denied cleartext request reports the denial itself").
        assertTrue("no platform wording", !shown.contains("Cleartext HTTP traffic"))
    }

    @Test
    fun anUnusableUrlIsExplainedAsAUrlProblem() {
        val tested = "truenas.home.framstag.com:30123/names.json"
        show(
            state(
                activeSource = repository,
                draft = repository.baseUrl,
                outcome = SourceTestOutcome.Failure(tested, RepositoryFailure.MalformedUrl)
            )
        )

        val shown = "$tested is not a usable address — enter it as https://host or http://host:port"
        composeRule.onNodeWithText(shown).assertIsDisplayed()
        assertTrue("not a connection failure", !shown.contains("could not be reached"))
        assertTrue("no parser wording", !shown.contains("Illegal character"))
    }

    @Test
    fun anHttpBaseUrlIsMarkedUnencrypted() {
        val plain = MapSource.repository("http://10.0.2.2:30123")
        show(state(activeSource = plain, draft = plain.baseUrl))

        composeRule.onNodeWithTag("repository-url-unencrypted-notice").assertIsDisplayed()
        composeRule.onNodeWithText(
            "http://10.0.2.2:30123 is unencrypted HTTP — use it only for a host you trust"
        ).assertIsDisplayed()
        // Informational only: the field and its test action stay usable.
        composeRule.onNodeWithTag("repository-url-field").assertIsEnabled()
        composeRule.onNodeWithTag("repository-url-test").assertIsEnabled()
    }

    @Test
    fun anHttpsBaseUrlCarriesNoNotice() {
        show(state(activeSource = repository, draft = repository.baseUrl))

        composeRule.onNodeWithTag("repository-url-unencrypted-notice").assertDoesNotExist()
    }

    @Test
    fun theNoticeDoesNotReplaceTheTestOutcome() {
        val plain = MapSource.repository("http://10.0.2.2:30123")
        show(
            state(
                activeSource = plain,
                draft = plain.baseUrl,
                outcome = SourceTestOutcome.Success("${plain.baseUrl}/names.json", 1, 3)
            )
        )

        composeRule.onNodeWithTag("repository-url-unencrypted-notice").assertIsDisplayed()
        composeRule.onNodeWithText("Repository OK — 1 regions, 3 maps").assertIsDisplayed()
    }

    @Test
    fun anUnencryptedActiveSourceIsMarkedWhenTheFieldIsEmpty() {
        val plain = MapSource.repository("http://10.0.2.2:30123")
        show(state(activeSource = plain, draft = ""))

        composeRule.onNodeWithText(
            "http://10.0.2.2:30123 is unencrypted HTTP — use it only for a host you trust"
        ).assertIsDisplayed()
    }

    @Test
    fun theUrlFieldDeclaresUrlInputAndShowsItsFormat() {
        show(state(activeSource = repository, draft = repository.baseUrl))

        assertEquals(KeyboardType.Uri, repositoryUrlKeyboardOptions.keyboardType)
        composeRule.onNodeWithText("http://host or https://host:port").assertIsDisplayed()
    }

    @Test
    fun urlFieldShowsTheRememberedValue() {
        show(state(activeSource = MapSource.BuiltInProvider, draft = "https://maps.example.org/other"))

        composeRule.onNodeWithTag("repository-url-field").assertIsDisplayed()
        composeRule.onNodeWithText("https://maps.example.org/other").assertIsDisplayed()
    }

    @Test
    fun urlFieldAndTestActionAreDisjointAndTappable() {
        show(state(activeSource = repository, draft = repository.baseUrl))

        val field = composeRule.onNodeWithTag("repository-url-field").getBoundsInRoot()
        val test = composeRule.onNodeWithTag("repository-url-test").getBoundsInRoot()

        assertTrue("the field and the test action do not overlap", field.right <= test.left)
        assertTrue("the test action is a tap target", test.bottom - test.top >= 48.dp)
    }

    private fun show(state: MapManagerUiState) {
        composeRule.setContent {
            SourceSelector(
                state = state,
                onSelect = {},
                onUrlChange = {},
                onTest = {},
                onRefresh = {}
            )
        }
    }

    private fun state(
        activeSource: MapSource,
        draft: String,
        outcome: SourceTestOutcome? = null
    ): MapManagerUiState = MapManagerUiState(
        sources = listOf(MapSource.BuiltInProvider, repository),
        activeSource = activeSource,
        repositoryUrlDraft = draft,
        sourceTestOutcome = outcome
    )
}
