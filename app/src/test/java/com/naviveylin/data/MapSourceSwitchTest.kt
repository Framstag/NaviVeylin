package com.naviveylin.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.naviveylin.core.BasemapReloadNotifier
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.core.mapsource.MapSource
import com.naviveylin.core.mapsource.MapSourceKind
import com.naviveylin.core.mapsource.MapSourceMarker
import com.naviveylin.core.mapsource.MapSourceRecord
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for the confirmed source switch.
 *
 * Specs: map-source-selection — "A source switch unregisters, reloads and records what it deletes" /
 * "Deleted map is unregistered" / "Deleted basemap stops rendering in the running process" / "Switch is
 * diagnosable without coordinates" / "Partly failed deletion is reported and does not block the switch".
 */
@RunWith(RobolectricTestRunner::class)
class MapSourceSwitchTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val repository = MapSource(MapSourceKind.REPOSITORY, "https://maps.example.org/repo")
    private lateinit var diagnosticsFile: File

    @After
    fun tearDown() {
        DiagnosticsLog.reset()
    }

    @Test
    fun deletesAndUnregistersEveryOtherSourceDirectory() = runTest {
        val fixture = fixture()
        fixture.markMap("berlin", MapSourceKind.BUILT_IN_PROVIDER)
        fixture.markMap("iceland", MapSourceKind.BUILT_IN_PROVIDER)
        fixture.markMap("europe-germany-berlin", MapSourceKind.REPOSITORY, baseUrl = repository.baseUrl)

        val plan = fixture.switcher.plan(repository)
        val failed = fixture.switchTo(repository, plan)

        assertEquals(2, plan.mapCount)
        assertTrue(failed.isEmpty())
        assertFalse(Files.exists(fixture.mapsRoot.resolve("berlin")))
        assertFalse(Files.exists(fixture.mapsRoot.resolve("iceland")))
        assertTrue(
            "the target source's own map stays",
            Files.isDirectory(fixture.mapsRoot.resolve("europe-germany-berlin"))
        )
    }

    @Test
    fun deleteMapIsCalledPerDirectory() = runTest {
        val fixture = fixture()
        fixture.markMap("berlin", MapSourceKind.BUILT_IN_PROVIDER)
        fixture.markMap("iceland", MapSourceKind.BUILT_IN_PROVIDER)

        fixture.switchTo(repository, fixture.switcher.plan(repository))

        assertEquals(
            "every deleted directory is unregistered from the map manager, not only removed from disk",
            listOf("berlin", "iceland"),
            fixture.unregistered.map { it.fileName.toString() }.sorted()
        )
    }

    @Test
    fun basemapIsDeletedAndReloadSignalled() = runTest {
        val fixture = fixture()
        fixture.markBasemap(MapSourceKind.BUILT_IN_PROVIDER)

        fixture.switchTo(repository, fixture.switcher.plan(repository))

        assertFalse(Files.exists(fixture.mapsRoot.resolve("basemap")))
        assertEquals("the deleted basemap must leave every renderer", 1L, fixture.notifier.revision.value)
    }

    @Test
    fun basemapOfTheTargetSourceIsKeptAndDoesNotSignalReload() = runTest {
        val fixture = fixture()
        fixture.markBasemap(MapSourceKind.REPOSITORY, baseUrl = repository.baseUrl)

        fixture.switchTo(repository, fixture.switcher.plan(repository))

        assertTrue(Files.isDirectory(fixture.mapsRoot.resolve("basemap")))
        assertEquals(0L, fixture.notifier.revision.value)
    }

    @Test
    fun selectionPersistedOnlyAfterDeletion() = runTest {
        val fixture = fixture()
        fixture.markMap("berlin", MapSourceKind.BUILT_IN_PROVIDER)
        fixture.markBasemap(MapSourceKind.BUILT_IN_PROVIDER)

        fixture.switchTo(repository, fixture.switcher.plan(repository))

        // Observed at the moment the selection is stored: nothing of the other source may be left, so a
        // crash cannot leave data deleted while the old source is still the stored one.
        assertTrue(
            "the other source's maps were gone before the selection was stored",
            fixture.mapsGoneAtPersist
        )
        assertTrue(
            "the other source's basemap was gone before the selection was stored",
            fixture.basemapGoneAtPersist
        )
        assertEquals(repository, fixture.persisted)
    }

    @Test
    fun diagnosticsLineCarriesCountsAndNoCoordinates() = runTest {
        val fixture = fixture()
        startDiagnosticsLog()
        fixture.markMap("berlin", MapSourceKind.BUILT_IN_PROVIDER)
        fixture.markMap("iceland", MapSourceKind.BUILT_IN_PROVIDER)

        fixture.switchTo(repository, fixture.switcher.plan(repository))
        DiagnosticsLog.awaitDrained()

        val line = diagnosticsFile.readText().lines().first { it.contains("map source switched") }
        assertTrue(
            "the line names both sources",
            line.contains("from=BUILT_IN_PROVIDER") && line.contains("to=REPOSITORY")
        )
        assertTrue("it names how many directories went away", line.contains("deletedDirectories=2"))
        assertTrue("it names the bytes reclaimed", line.contains("bytesReclaimed="))
        assertFalse(
            "no log line may carry a coordinate (the timestamp's milliseconds are not one)",
            Regex("""\d\.\d{4,}""").containsMatchIn(line)
        )
    }

    @Test
    fun anUnregisterThatAlreadyDeletedTheDirectoryIsNotAFailure() = runTest {
        // The real map manager's delete removes the directory as it unregisters it, so the switch's own
        // deletion runs on a path that is already gone. That must count as deleted, not as a failure
        // (found on device 2026-10-09: every switch reported `failed=1` while the data was in fact gone).
        val fixture = fixture(deletingUnregister = true)
        fixture.markMap("berlin", MapSourceKind.BUILT_IN_PROVIDER)
        fixture.markBasemap(MapSourceKind.BUILT_IN_PROVIDER)

        val failed = fixture.switchTo(repository, fixture.switcher.plan(repository))

        assertTrue("nothing is reported as undeletable: $failed", failed.isEmpty())
        assertEquals(repository, fixture.persisted)
    }

    @Test
    fun undeletableDirectoryIsReportedByName() = runTest {
        val fixture = fixture()
        fixture.markMap("berlin", MapSourceKind.BUILT_IN_PROVIDER, undeletable = true)

        val failed = fixture.switchTo(repository, fixture.switcher.plan(repository))

        assertEquals(1, failed.size)
        assertEquals("berlin", failed.single().fileName.toString())
    }

    @Test
    fun switchStillApplies() = runTest {
        val fixture = fixture()
        fixture.markMap("berlin", MapSourceKind.BUILT_IN_PROVIDER, undeletable = true)

        fixture.switchTo(repository, fixture.switcher.plan(repository))

        assertEquals("the selection is applied even when a directory refuses to go", repository, fixture.persisted)
    }

    @Test
    fun remainingDirectoriesKeepTheirOwnSource() = runTest {
        val fixture = fixture()
        fixture.markMap("berlin", MapSourceKind.BUILT_IN_PROVIDER, undeletable = true)
        fixture.markMap("iceland", MapSourceKind.BUILT_IN_PROVIDER)

        fixture.switchTo(repository, fixture.switcher.plan(repository))

        // The undeletable directory is still there and still attributed to its own source, so a later
        // switch back to the built-in provider recognises it instead of orphaning it.
        assertEquals(
            MapSourceKind.BUILT_IN_PROVIDER,
            MapSourceMarker.sourceOf(fixture.mapsRoot.resolve("berlin")).sourceKind
        )
    }

    /** Point [DiagnosticsLog] at a file of this test's own, so the switch line can be read back. */
    private fun startDiagnosticsLog() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        diagnosticsFile = File(context.filesDir, "diagnostics/switch-${System.nanoTime()}.log")
        diagnosticsFile.parentFile?.mkdirs()
        diagnosticsFile.delete()
        DiagnosticsLog.reset()
        DiagnosticsLog.initForTest(diagnosticsFile)
    }

    /** The switch's moving parts, with every effect recorded instead of executed natively. */
    private class Fixture(
        val mapsRoot: Path,
        val switcher: MapSourceSwitcher,
        val notifier: BasemapReloadNotifier,
        val deletingUnregister: Boolean
    ) {
        val unregistered = mutableListOf<Path>()

        var persisted: MapSource? = null
        var mapsGoneAtPersist = false
        var basemapGoneAtPersist = false

        fun markMap(
            name: String,
            kind: MapSourceKind,
            baseUrl: String? = null,
            undeletable: Boolean = false
        ) {
            val directory = mapsRoot.resolve(name)
            Files.createDirectories(directory)
            Files.write(directory.resolve("map.lib"), ByteArray(64))
            MapSourceMarker.write(directory, MapSourceRecord(kind, baseUrl, 27))
            if (undeletable) {
                // A read-only directory cannot be removed, so its entries stay behind.
                directory.toFile().setWritable(false)
            }
        }

        fun markBasemap(kind: MapSourceKind, baseUrl: String? = null) {
            val directory = mapsRoot.resolve("basemap")
            Files.createDirectories(directory)
            Files.write(directory.resolve("types.dat"), ByteArray(64))
            MapSourceMarker.write(directory, MapSourceRecord(kind, baseUrl, 27))
        }

        suspend fun switchTo(target: MapSource, plan: MapSourceSwitchPlan): List<Path> =
            MapSourceSwitchUseCase(
                switcher = switcher,
                unregister = { directory ->
                    unregistered.add(directory)
                    if (deletingUnregister) {
                        // Mirror the map manager: it removes the directory as it unregisters it.
                        Files.walk(directory).sorted(Comparator.reverseOrder()).forEach { path ->
                            Files.deleteIfExists(path)
                        }
                    }
                },
                persist = { source ->
                    persisted = source
                    mapsGoneAtPersist = plan.mapDirectories.none { Files.exists(it) }
                    basemapGoneAtPersist = plan.basemapDirectory?.let { !Files.exists(it) } ?: true
                },
                reloadNotifier = notifier
            ).switchTo(target, MapSource.BuiltInProvider, plan)
    }

    private fun fixture(deletingUnregister: Boolean = false): Fixture {
        val mapsRoot = temporaryFolder.newFolder().toPath().resolve("maps")
        Files.createDirectories(mapsRoot)
        return Fixture(mapsRoot, MapSourceSwitcher(mapsRoot), BasemapReloadNotifier(), deletingUnregister)
    }
}
