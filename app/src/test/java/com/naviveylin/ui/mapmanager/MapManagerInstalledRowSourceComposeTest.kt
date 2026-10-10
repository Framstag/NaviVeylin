package com.naviveylin.ui.mapmanager

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.BasemapManager
import com.framstag.libosmscout.client.FakeMapDownloadManager
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.MapProvider
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.mapsource.MapSourceKind
import com.naviveylin.core.mapsource.MapSourceMarker
import com.naviveylin.core.mapsource.MapSourceRecord
import com.naviveylin.data.BasemapRegistrar
import com.naviveylin.data.HttpUrlFetcher
import com.naviveylin.data.MapSourceRegistry
import com.naviveylin.data.MapStorageManager
import com.naviveylin.data.SettingsStorage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.file.Files

/**
 * Compose tests for the label an installed map row carries.
 *
 * Spec: map-download-ui — "Installed maps name their source" / "Repository map is marked in the
 * installed list" / "Pre-existing installation is marked as the built-in provider".
 *
 * The installed row is rendered through the whole screen, because the label is wired at the call site:
 * a case that only asserted the view model's naming (as `MapManagerSourceLabelTest` does) passed while
 * the row itself rendered an empty source name — found on device 2026-10-09.
 */
@RunWith(RobolectricTestRunner::class)
class MapManagerInstalledRowSourceComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val repositoryUrl = "https://maps.example.org/repo"

    @Test
    fun repositoryRowNamesTheRepositoryItCameFrom() {
        showScreenWithInstalledMap(
            "berlin",
            MapSourceRecord(MapSourceKind.REPOSITORY, repositoryUrl, 27)
        )

        waitForText("from $repositoryUrl")
        composeRule.onNodeWithText("from $repositoryUrl").assertIsDisplayed()
    }

    @Test
    fun unmarkedInstallationRowNamesTheBuiltInProvider() {
        showScreenWithInstalledMap("north-rhine-westphalia", record = null)

        waitForText("from karry.cz")
        composeRule.onNodeWithText("from karry.cz").assertIsDisplayed()
    }

    private fun showScreenWithInstalledMap(mapDirectoryName: String, record: MapSourceRecord?) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val storageManager = MapStorageManager(context)
        val directory = storageManager.mapsRootDir.resolve(mapDirectoryName)
        Files.createDirectories(directory)
        Files.write(directory.resolve("map.lib"), ByteArray(32))
        if (record != null) {
            assertTrue("the fixture writes its marker", MapSourceMarker.write(directory, record))
        }
        val provider = MapProvider("karry.cz", "http://127.0.0.1:1", "")
        val settings = SettingsStorage(context)
        val registry = MapSourceRegistry(settings, provider, HttpUrlFetcher())
        val viewModel = MapManagerViewModel(
            context,
            FakeMapDownloadManager(installedDirs = { listOf(directory.toString()) }),
            storageManager,
            provider,
            settings,
            registry,
            BasemapReloadNotifier(),
            BasemapRegistrar(FakeOSMScoutClient(), BasemapReloadNotifier())
        )
        val basemapViewModel = BasemapViewModel(
            context,
            BasemapManager(provider, storageManager.mapsRootDir),
            BasemapRegistrar(FakeOSMScoutClient(), BasemapReloadNotifier()),
            registry
        )
        composeRule.setContent {
            MapManagerScreen(
                onBack = {},
                viewModel = viewModel,
                basemapViewModel = basemapViewModel
            )
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
