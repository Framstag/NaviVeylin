package com.naviveylin.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.core.BasemapReloadNotifier
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for [MapDataRegistrar] — the seam that makes a replaced database render without an app restart.
 *
 * Spec: map-updates — "An installed update is rendered without an app restart"; spec `native-database-open` —
 * "A whole database set is registered in one coordinated change"; `guidelines/Design.md` §4 — the native call
 * is confined and reported, never allowed to escape.
 */
@RunWith(RobolectricTestRunner::class)
class MapDataRegistrarTest {

    private lateinit var storageManager: MapStorageManager

    @Before
    fun setUp() {
        storageManager = MapStorageManager(ApplicationProvider.getApplicationContext<Application>())
        storageManager.mapsRootDir.toFile().deleteRecursively()
    }

    @Test
    fun theUpdateReopensTheSetAndBumpsTheNotifier() {
        installMap("europe-germany-berlin")
        installMap("europe-germany-hamburg")
        val client = FakeOSMScoutClient()
        val notifier = BasemapReloadNotifier()

        MapDataRegistrar(client, storageManager, notifier).apply()

        assertEquals(
            "the whole installed set is re-opened in one batch",
            listOf(
                listOf(
                    storageManager.mapsRootDir.resolve("europe-germany-berlin").toString(),
                    storageManager.mapsRootDir.resolve("europe-germany-hamburg").toString()
                ).sorted()
            ),
            client.openedDatabaseBatches.map { it.sorted() }
        )
        assertEquals("the renderers are told the data changed", 1L, notifier.revision.value)
    }

    @Test
    fun noInstalledMapIsANoOp() {
        val client = FakeOSMScoutClient()
        val notifier = BasemapReloadNotifier()

        MapDataRegistrar(client, storageManager, notifier).apply()

        assertTrue("an empty set would close what the client has open", client.openedDatabaseBatches.isEmpty())
        assertEquals("and nothing re-renders for it", 0L, notifier.revision.value)
    }

    @Test
    fun aNativeFailureIsConfinedAndTheProcessSurvives() {
        installMap("europe-germany-berlin")
        val client = FakeOSMScoutClient().apply {
            openDatabasesResult = { throw IllegalStateException("native re-open failed") }
        }
        val notifier = BasemapReloadNotifier()

        MapDataRegistrar(client, storageManager, notifier).apply()

        assertTrue("the set was attempted", client.openedDatabaseBatches.isNotEmpty())
        assertEquals("and no renderer was told about a change that did not happen", 0L, notifier.revision.value)
    }

    private fun installMap(directoryName: String) {
        val directory = storageManager.mapsRootDir.resolve(directoryName)
        Files.createDirectories(directory)
        Files.write(directory.resolve("types.dat"), "types".toByteArray())
    }
}
