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
import com.naviveylin.R
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.BasemapRegistrar
import com.naviveylin.data.HttpUrlFetcher
import com.naviveylin.data.MapSourceRegistry
import com.naviveylin.data.MapStorageManager
import com.naviveylin.data.SettingsStorage
import com.naviveylin.test.ComposeSandboxRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Compose tests for the notice an installed map row carries when its data predates the app's
 * stylesheet.
 *
 * Spec: map-updates — "An installed database that predates the shipped stylesheet is reported"
 * / "Every declared name is carried" (change `report-a-stale-map-install`, task 2.2).
 *
 * The row is rendered through the whole screen, because the notice is wired at the call site: a case
 * that only asserted the view model's state (as `MapManagerStaleRowTest` does) would pass while the
 * row rendered nothing.
 */
@RunWith(RobolectricTestRunner::class)
class MapManagerStaleNoticeComposeTest {

    @get:Rule(order = 0)
    val composeSandbox = ComposeSandboxRule()

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theInstalledRowNamesTheShortfallAndItsCount() {
        val notice = showScreenWithInstalledMap(
            directoryName = "andorra-stale",
            declared = arrayOf(
                "highway_motorway",
                "natural_peak",
                "place_city",
                "amenity_theatre",
                "shop_bakery"
            )
        )

        waitForText(notice)
        composeRule.onNodeWithText(notice).assertIsDisplayed()
        assertTrue(
            "the offered action is the download that replaces the stale data",
            composeRule.onAllNodesWithText(
                ApplicationProvider.getApplicationContext<Application>().getString(R.string.download)
            ).fetchSemanticsNodes().isNotEmpty()
        )
    }

    @Test
    fun aCurrentInstallationRowCarriesNoNotice() {
        val notice = showScreenWithInstalledMap(
            directoryName = "germany-current",
            declared = arrayOf("highway_motorway", "natural_peak", "place_city")
        )

        waitForText("from karry.cz")
        assertTrue(
            "a database carrying every declared name shows no notice",
            composeRule.onAllNodesWithText(notice).fetchSemanticsNodes().isEmpty()
        )
    }

    /**
     * Show the map manager with one installed map whose `types.dat` is the golden fixture and whose
     * declared set is [declared].
     *
     * @return the notice the screen is expected to carry for this installation
     */
    private fun showScreenWithInstalledMap(directoryName: String, declared: Array<String>): String {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val storageManager = MapStorageManager(context)
        val directory = installMap(storageManager, directoryName)
        writeDeclaredTypes(context, declared)

        val provider = MapProvider("karry.cz", "http://127.0.0.1:1", "")
        val settings = SettingsStorage(context)
        val registry = MapSourceRegistry(settings, provider, HttpUrlFetcher())
        val viewModel = mapManagerViewModelForTest(
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
        return context.getString(R.string.installed_types_outdated, 2)
    }

    /** An installed database directory holding the golden `types.dat`. */
    private fun installMap(storageManager: MapStorageManager, name: String): Path {
        val directory = storageManager.mapsRootDir.resolve(name)
        Files.createDirectories(directory)
        Files.write(directory.resolve("map.lib"), ByteArray(32))
        Files.copy(
            Paths.get("src/main/cpp/libosmscout/Tests/data/testregion/types.dat"),
            directory.resolve("types.dat")
        )
        return directory
    }

    /** Write the mirrored stylesheet the detector reads its declared names from. */
    private fun writeDeclaredTypes(context: Application, declared: Array<String>) {
        val stylesheet = AssetCopier(context).stylesheetsDirectory().toPath().resolve("map.ost")
        Files.createDirectories(stylesheet.parent)
        Files.write(stylesheet, declared.joinToString("\n") { "TYPE $it" }.toByteArray())
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
