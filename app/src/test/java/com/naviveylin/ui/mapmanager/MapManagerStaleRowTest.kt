package com.naviveylin.ui.mapmanager

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.viewModelScope
import com.framstag.libosmscout.client.FakeMapDownloadManager
import com.framstag.libosmscout.client.MapProvider
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.data.AssetCopier
import com.naviveylin.data.HttpUrlFetcher
import com.naviveylin.data.MapSourceRegistry
import com.naviveylin.data.MapStorageManager
import com.naviveylin.data.SettingsStorage
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The map manager's half of the stale-install report: an installed database whose data predates the
 * app's stylesheet carries the shortfall in the screen's state, so the row can name it.
 *
 * Spec: map-updates — "An installed database that predates the shipped stylesheet is reported"
 * (change `report-a-stale-map-install`, tasks 2.1).
 *
 * The `types.dat` fixture is a real database file (format version 27), the same one the detector's
 * own suite reads: the state under test is the shortfall the detector answers for an installation on
 * disk, not a number a double produced.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapManagerStaleRowTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var application: Application
    private lateinit var storageManager: MapStorageManager
    private lateinit var settings: SettingsStorage

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        storageManager = MapStorageManager(application)
        storageManager.mapsRootDir.toFile().deleteRecursively()
        settings = SettingsStorage(application)
        // The stylesheet tree `AssetCopier` mirrors is the detector's other half; a test that means
        // "the app ships this set" writes it where the app reads it.
        AssetCopier(application).stylesheetsDirectory().deleteRecursively()
    }

    @Test
    fun theInstalledRowNamesTheStylesheetItPredates() = runTest(mainDispatcherRule.dispatcher) {
        val directory = installMap("andorra-stale", goldenTypesDat())
        declareTypes("highway_motorway", "natural_peak", "place_city", "amenity_theatre", "shop_bakery")

        val viewModel = viewModel(listOf(directory.toString()))
        viewModel.refreshInstalledMaps()
        advanceUntilIdle()

        assertEquals(
            "the installed row carries the number of declared types its data lacks",
            2,
            viewModel.uiState.value.installedTypeShortfalls[directory.toString()]
        )
    }

    @Test
    fun aDatabaseCarryingEveryDeclaredNameCarriesNoShortfall() = runTest(mainDispatcherRule.dispatcher) {
        val directory = installMap("germany-current", goldenTypesDat())
        declareTypes("highway_motorway", "natural_peak", "place_city")

        val viewModel = viewModel(listOf(directory.toString()))
        viewModel.refreshInstalledMaps()
        advanceUntilIdle()

        assertFalse(
            "a database carrying every declared name carries no shortfall, so its row reads as before",
            viewModel.uiState.value.installedTypeShortfalls.containsKey(directory.toString())
        )
    }

    @Test
    fun aForeignFormatVersionCarriesNoShortfall() = runTest(mainDispatcherRule.dispatcher) {
        val directory = installMap("andorra-format-26", goldenTypesDat(readAsFormatVersion = 26))
        declareTypes("highway_motorway", "natural_peak", "place_city", "amenity_theatre", "shop_bakery")

        val viewModel = viewModel(listOf(directory.toString()))
        viewModel.refreshInstalledMaps()
        advanceUntilIdle()

        assertFalse(
            "a types.dat naming another format version is not judged, so no shortfall follows",
            viewModel.uiState.value.installedTypeShortfalls.containsKey(directory.toString())
        )
    }

    /** Write the mirrored stylesheet the detector reads its declared names from. */
    private fun declareTypes(vararg names: String) {
        val stylesheet = AssetCopier(application).stylesheetsDirectory()
            .toPath()
            .resolve("map.ost")
        Files.createDirectories(stylesheet.parent)
        Files.write(stylesheet, names.joinToString("\n") { "TYPE $it" }.toByteArray())
    }

    /** An installed database directory holding the fixture's `types.dat`. */
    private fun installMap(directoryName: String, typesFile: Path): Path {
        val directory = storageManager.mapsRootDir.resolve(directoryName)
        Files.createDirectories(directory)
        Files.write(directory.resolve("map.lib"), ByteArray(32))
        Files.copy(typesFile, directory.resolve("types.dat"))
        return directory
    }

    /**
     * The real database `types.dat` this suite judges.
     *
     * @param readAsFormatVersion rewritten as this version's file, for the refusal case
     */
    private fun goldenTypesDat(readAsFormatVersion: Int? = null): Path {
        val bytes = Files.readAllBytes(
            Paths.get("src/main/cpp/libosmscout/Tests/data/testregion/types.dat")
        )
        if (readAsFormatVersion == null) {
            val copy = Files.createTempFile("golden-types", ".dat")
            Files.write(copy, bytes)
            copy.toFile().deleteOnExit()
            return copy
        }
        val versioned = bytes.copyOf()
        versioned[0] = readAsFormatVersion.toByte()
        versioned[1] = (readAsFormatVersion shr 8).toByte()
        versioned[2] = (readAsFormatVersion shr 16).toByte()
        versioned[3] = (readAsFormatVersion shr 24).toByte()
        val copy = Files.createTempFile("foreign-types", ".dat")
        Files.write(copy, versioned)
        copy.toFile().deleteOnExit()
        return copy
    }

    private suspend fun viewModel(installedDirectories: List<String>): MapManagerViewModel {
        val provider = MapProvider("karry.cz", "http://127.0.0.1:1", "")
        val registry = MapSourceRegistry(settings, provider, HttpUrlFetcher())
        val viewModel = mapManagerViewModelForTest(
            application,
            FakeMapDownloadManager(installedDirs = { installedDirectories }),
            storageManager,
            provider,
            settings,
            registry,
            BasemapReloadNotifier()
        )
        // The constructor's own refresh launches on the default `Dispatchers.IO` before this test can
        // replace the dispatcher, so `advanceUntilIdle()` cannot see it: join it, then drive every
        // later read on the test dispatcher (the seam `MapManagerDeleteRepositoryMapTest` uses).
        viewModel.viewModelScope.coroutineContext[Job]?.children?.toList()?.forEach { it.join() }
        viewModel.ioDispatcher = mainDispatcherRule.dispatcher
        return viewModel
    }
}
