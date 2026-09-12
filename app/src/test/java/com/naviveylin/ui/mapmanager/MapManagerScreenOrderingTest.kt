package com.naviveylin.ui.mapmanager

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.framstag.libosmscout.client.AvailableMapEntry
import com.framstag.libosmscout.client.MapProvider
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Compose UI tests for the map manager screen section ordering and state
 * handling (map-download-ui spec: Section ordering, Loading indicator
 * placement, Error banner placement, Search/filter available maps,
 * Active downloads section).
 *
 * ViewModels are mocked (no FakeOSMScoutClient, no native calls) so the
 * active-downloads / loading / error states can be driven directly — the
 * real download path starts a Hilt foreground service whose component build
 * would call native OSMScoutClientBuilder.build() and crash on the host stub .so.
 */
@RunWith(RobolectricTestRunner::class)
class MapManagerScreenOrderingTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val provider = MapProvider("test", "http://127.0.0.1:1", "")

    private fun entry(name: String) =
        AvailableMapEntry(name, emptyList(), "desc", provider, 0L, "", 0L, -1)

    private fun mapManagerViewModel(
        available: List<AvailableMapEntry> = emptyList(),
        installedPaths: Set<String> = emptySet(),
        activeDownloads: List<MapEntryState> = emptyList(),
        isLoading: Boolean = false,
        error: String? = null
    ): MapManagerViewModel {
        val vm = mock<MapManagerViewModel>()
        val uiState = MapManagerUiState(
            availableEntries = available,
            installedMapPaths = installedPaths,
            activeDownloads = activeDownloads,
            downloadingNames = activeDownloads.map { it.entry.name }.toSet(),
            progressMap = activeDownloads.associate { it.entry.name to it.progress },
            isLoading = isLoading,
            error = error
        )
        whenever(vm.uiState).thenReturn(MutableStateFlow(uiState))
        available.forEach {
            whenever(vm.getMapPath(it.name)).thenReturn("/maps/${it.name.lowercase()}")
        }
        whenever(vm.isMapInstalled(any())).thenReturn(false)
        whenever(vm.isMapDownloading(any())).thenReturn(false)
        whenever(vm.getDownloadState(any())).thenReturn(null)
        return vm
    }

    private fun basemapViewModel(
        isDownloading: Boolean = false,
        progress: Int = 0
    ): BasemapViewModel {
        val bm = mock<BasemapViewModel>()
        whenever(bm.uiState).thenReturn(
            MutableStateFlow(BasemapUiState(isDownloading = isDownloading, progress = progress))
        )
        return bm
    }

    private fun setTallDisplay() {
        // Default Robolectric display (320x470dp) cannot fit all six sections;
        // use a phone-sized display so every section is composed by LazyColumn.
        RuntimeEnvironment.setQualifiers("w411dp-h891dp")
    }

    @Test
    fun sectionsRenderInFixedOrder() {
        setTallDisplay()
        val germany = entry("Germany")
        val france = entry("France")
        val vm = mapManagerViewModel(
            available = listOf(germany, france),
            installedPaths = setOf("/maps/germany"),
            activeDownloads = listOf(
                MapEntryState(germany, DownloadState.Downloading, 42, "42%", "h")
            )
        )
        composeRule.setContent {
            MapManagerScreen(onBack = {}, viewModel = vm, basemapViewModel = basemapViewModel())
        }
        composeRule.waitForIdle()

        val providerY = composeRule.onNodeWithText("Provider:").getBoundsInRoot().top
        val searchY = composeRule.onNodeWithText("Search maps…").getBoundsInRoot().top
        // Regression: plural must be formatted ("1 active download", not raw "%d active download")
        composeRule.onNodeWithText("1 active download").assertExists()
        val downloadsY =
            composeRule.onNodeWithText("active download", substring = true).getBoundsInRoot().top
        val basemapY = composeRule.onNodeWithText("World Basemap").getBoundsInRoot().top
        val installedY = composeRule.onNodeWithText("Installed Maps").getBoundsInRoot().top
        val availableY = composeRule.onNodeWithText("Available Maps").getBoundsInRoot().top

        assertTrue("provider row above search field", providerY < searchY)
        assertTrue("search field above active downloads", searchY < downloadsY)
        assertTrue("active downloads above basemap section", downloadsY < basemapY)
        assertTrue("basemap section above installed maps", basemapY < installedY)
        assertTrue("installed maps above available maps", installedY < availableY)
    }

    @Test
    fun installedSectionHiddenWhileSearching() {
        setTallDisplay()
        val germany = entry("Germany")
        val france = entry("France")
        val vm = mapManagerViewModel(
            available = listOf(germany, france),
            installedPaths = setOf("/maps/germany"),
            activeDownloads = emptyList()
        )
        composeRule.setContent {
            MapManagerScreen(onBack = {}, viewModel = vm, basemapViewModel = basemapViewModel())
        }

        composeRule.onNodeWithText("Installed Maps").assertIsDisplayed()

        composeRule.onNode(hasSetTextAction()).performTextInput("ger")
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Installed Maps").assertCountEquals(0)

        composeRule.onNode(hasSetTextAction()).performTextClearance()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Installed Maps").assertIsDisplayed()
    }

    @Test
    fun searchFiltersAvailableTree() {
        setTallDisplay()
        val germany = entry("Germany")
        val france = entry("France")
        val vm = mapManagerViewModel(
            available = listOf(germany, france),
            activeDownloads = emptyList()
        )
        composeRule.setContent {
            MapManagerScreen(onBack = {}, viewModel = vm, basemapViewModel = basemapViewModel())
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Germany").assertExists()
        composeRule.onNodeWithText("France").assertExists()

        composeRule.onNode(hasSetTextAction()).performTextInput("ger")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Germany").assertExists()
        composeRule.onAllNodesWithText("France").assertCountEquals(0)
    }

    @Test
    fun loadingIndicatorShownAtTopWhenEmpty() {
        setTallDisplay()
        val vm = mapManagerViewModel(isLoading = true)
        composeRule.setContent {
            MapManagerScreen(onBack = {}, viewModel = vm, basemapViewModel = basemapViewModel())
        }
        composeRule.waitForIdle()

        // No partially loaded map-list sections while loading with nothing loaded
        composeRule.onAllNodesWithText("Installed Maps").assertCountEquals(0)
        composeRule.onAllNodesWithText("Available Maps").assertCountEquals(0)
        composeRule.onAllNodesWithText("active download", substring = true).assertCountEquals(0)

        // Loading spinner present at the top of the content area (above search field)
        val spinnerY = composeRule
            .onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))[0]
            .getBoundsInRoot().top
        val searchY = composeRule.onNodeWithText("Search maps…").getBoundsInRoot().top
        assertTrue("loading spinner above search field", spinnerY < searchY)
    }

    @Test
    fun errorBannerShownBelowSearchField() {
        setTallDisplay()
        val vm = mapManagerViewModel(error = "Fetch failed")
        composeRule.setContent {
            MapManagerScreen(onBack = {}, viewModel = vm, basemapViewModel = basemapViewModel())
        }
        composeRule.waitForIdle()

        val searchY = composeRule.onNodeWithText("Search maps…").getBoundsInRoot().top
        val errorY = composeRule.onNodeWithText("Fetch failed").getBoundsInRoot().top
        val basemapY = composeRule.onNodeWithText("World Basemap").getBoundsInRoot().top
        assertTrue("error below search field", searchY < errorY)
        assertTrue("error above basemap section", errorY < basemapY)
    }

    @Test
    fun emptySectionsOmitted() {
        setTallDisplay()
        val vm = mapManagerViewModel()
        composeRule.setContent {
            MapManagerScreen(onBack = {}, viewModel = vm, basemapViewModel = basemapViewModel())
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Provider:").assertExists()
        composeRule.onNodeWithText("Search maps…").assertExists()
        composeRule.onNodeWithText("World Basemap").assertExists()
        composeRule.onNodeWithText("Tap Refresh to load available maps").assertExists()
        composeRule.onAllNodesWithText("Installed Maps").assertCountEquals(0)
        composeRule.onAllNodesWithText("Available Maps").assertCountEquals(0)
        composeRule.onAllNodesWithText("active download", substring = true).assertCountEquals(0)
    }

    @Test
    fun basemapDownloadShowsProgressInActiveDownloads() {
        setTallDisplay()
        val vm = mapManagerViewModel()
        composeRule.setContent {
            MapManagerScreen(
                onBack = {},
                viewModel = vm,
                basemapViewModel = basemapViewModel(isDownloading = true, progress = 42)
            )
        }
        composeRule.waitForIdle()

        // Active downloads section shows the basemap row with progress + cancel
        composeRule.onNodeWithText("1 active download").assertExists()
        composeRule.onAllNodesWithText("World Basemap").assertCountEquals(2) // row + section header
        composeRule.onNodeWithText("42%").assertExists()

        // Basemap section shows the compact status line, no duplicate percentage
        composeRule.onNodeWithText("Downloading world basemap…").assertExists()
        composeRule.onAllNodesWithText("42%").assertCountEquals(1)
    }
}
