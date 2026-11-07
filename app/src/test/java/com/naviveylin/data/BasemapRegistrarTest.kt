package com.naviveylin.data

import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.MapDownloadManager
import com.naviveylin.core.BasemapReloadNotifier
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The basemap the running session registers: only data this build's reader can open reaches the native
 * client.
 *
 * The defect this pins (issue #23, `§99`): a basemap installed before a data-format bump was handed to the
 * client unconditionally, and the only trace of the rejection was the native reader's own error line at
 * every start — the basemap silently degraded car and phone surfaces that depend on it.
 *
 * JNI/classloader rule: [FakeOSMScoutClient] extends the native-bearing client class, so this class runs
 * under Robolectric with the DEFAULT sandbox — no `@Config(sdk=…)`.
 */
@RunWith(RobolectricTestRunner::class)
class BasemapRegistrarTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val client = FakeOSMScoutClient()
    private val notifier = BasemapReloadNotifier()

    @Test
    fun aBasemapThatCarriesAnotherBuildsDataFormatIsNotHandedToTheClient() {
        val root = basemapRoot(version = MapDownloadManager.DATABASE_FORMAT_VERSION - 1)

        BasemapRegistrar(client, notifier).apply(root.absolutePath)

        assertEquals(
            "a basemap whose types.dat names another data format cannot be opened by this build, so the " +
                "unreadable directory must never reach the client (only the unload stays)",
            listOf(""),
            client.basemapLookupDirectories
        )
        assertEquals("the unload still asks the client to reload", 1, client.reloadBasemapCount)
        assertEquals("consumers still re-render, without the basemap", 1L, notifier.revision.value)
    }

    @Test
    fun aBasemapOfThisBuildsDataFormatIsHandedToTheClient() {
        val root = basemapRoot(version = MapDownloadManager.DATABASE_FORMAT_VERSION)

        BasemapRegistrar(client, notifier).apply(root.absolutePath)

        assertEquals(listOf(root.absolutePath), client.basemapLookupDirectories)
        assertEquals(1, client.reloadBasemapCount)
    }

    @Test
    fun aSlotNestedBasemapIsHandedToTheClient() {
        val root = basemapRoot(version = MapDownloadManager.DATABASE_FORMAT_VERSION, nested = true)

        BasemapRegistrar(client, notifier).apply(root.absolutePath)

        assertEquals(listOf(root.absolutePath), client.basemapLookupDirectories)
        assertEquals(1, client.reloadBasemapCount)
    }

    @Test
    fun anEmptyDirectoryUnloadsTheBasemap() {
        BasemapRegistrar(client, notifier).apply("")

        assertEquals(listOf(""), client.basemapLookupDirectories)
        assertEquals(1, client.reloadBasemapCount)
        assertEquals(1L, notifier.revision.value)
    }

    /** A basemap directory whose `types.dat` names [version], laid out flat unless [nested]. */
    private fun basemapRoot(version: Int, nested: Boolean = false): File {
        val root = temporaryFolder.newFolder("basemap")
        val databaseDirectory = if (nested) File(root, "basemap").apply { mkdirs() } else root
        File(databaseDirectory, "types.dat").writeBytes(
            ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(version).array()
        )
        File(databaseDirectory, "water.idx").writeBytes(byteArrayOf(1, 2, 3))
        return root
    }
}
