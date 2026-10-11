package com.naviveylin.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for the update state the settings file keeps.
 *
 * Spec: map-updates — "The check result survives a restart" / "A stale result is replaced, not merged".
 */
@RunWith(RobolectricTestRunner::class)
class SettingsStorageUpdateStateTest {

    private val stored = StoredMapUpdate(
        kind = "REGION",
        directoryName = "europe-germany-berlin",
        displayName = "Berlin",
        idPath = listOf("europe", "germany", "berlin"),
        downloadBytes = 4096L
    )

    @Test
    fun theUpdateStateRoundTrips() = runTest {
        val storage = storage()

        storage.update { it.copy(mapUpdateCheckAt = 1_756_000_000_000L, mapUpdates = listOf(stored)) }

        val loaded = storage().load()
        assertEquals(1_756_000_000_000L, loaded.mapUpdateCheckAt)
        assertEquals(listOf(stored), loaded.mapUpdates)
    }

    @Test
    fun aStoredUpdateBecomesAnActionResult() {
        val item = stored.toItem()

        assertEquals(
            com.naviveylin.core.mapsource.MapUpdateKind.REGION,
            item?.kind
        )
        assertEquals("Berlin", item?.displayName)
        assertEquals(
            com.naviveylin.core.mapsource.UpdateState.UpdateAvailable(4096L),
            item?.state
        )
    }

    @Test
    fun anItemWithoutAnUpdateIsNotStored() {
        val item = com.naviveylin.core.mapsource.MapUpdateItem(
            kind = com.naviveylin.core.mapsource.MapUpdateKind.REGION,
            directoryName = "europe-germany-berlin",
            displayName = "Berlin",
            idPath = listOf("europe", "germany", "berlin"),
            state = com.naviveylin.core.mapsource.UpdateState.UpToDate
        )

        assertEquals(null, item.toStored())
    }

    @Test
    fun aRecordOfAnUnknownKindIsDropped() {
        assertEquals(null, stored.copy(kind = "GALAXY").toItem())
    }

    @Test
    fun aSettingsFileFromBeforeTheFieldDecodesToNeverChecked() {
        // A settings file written before this change: no update fields at all.
        val file = File(context().filesDir, "maps/settings.json")
        file.parentFile?.mkdirs()
        file.writeText("""{"followMode":false,"darkMode":"AUTOMATIC"}""")

        val loaded = runBlocking { storage().load() }

        assertEquals(0L, loaded.mapUpdateCheckAt)
        assertTrue(loaded.mapUpdates.isEmpty())
        assertEquals("the rest of the file still decodes", false, loaded.followMode)
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun storage(): SettingsStorage = SettingsStorage(context())
}
