package com.naviveylin.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.MapDownloadManager
import com.framstag.libosmscout.client.MapProvider
import com.naviveylin.core.EngineTimeSource
import com.naviveylin.core.mapsource.MapSourceKind
import com.naviveylin.core.mapsource.MapSourceMarker
import com.naviveylin.core.mapsource.MapSourceRecord
import com.naviveylin.core.mapsource.MapUpdateItem
import com.naviveylin.core.mapsource.MapUpdateKind
import com.naviveylin.core.mapsource.RepositoryFailure
import com.naviveylin.core.mapsource.SlotInstall
import com.naviveylin.core.mapsource.UpdateState
import java.nio.file.Files
import java.nio.file.Paths
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for [MapUpdates] — the daily throttle, the manual check, the persisted result and what the
 * check is allowed to compare.
 *
 * Spec: map-updates — "The app checks for newer repository map data once a day" / "The user can run the
 * check on demand" / "The check covers the active repository source only" / "The check result survives a
 * restart".
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MapUpdatesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val baseUrl = "https://maps.example.org/repo"
    private val version = MapDownloadManager.DATABASE_FORMAT_VERSION
    private val berlin = listOf("europe", "germany", "berlin")
    private val hamburg = listOf("europe", "germany", "hamburg")
    private val berlinDirectory = "europe-germany-berlin"
    private val hamburgDirectory = "europe-germany-hamburg"

    /** The installed versions the fixtures write; the server's are newer unless a case says otherwise. */
    private val installedGeneratedAt = "2026-09-01T00:00:00Z"

    private var now = 1_756_000_000_000L
    private val timeSource = EngineTimeSource { now }
    private val server = FakeRepositoryServer(baseUrl, version)
    private val provider = MapProvider(
        "karry.cz",
        "https://osmscout.karry.cz",
        "https://osmscout.karry.cz/latest.php?fromVersion=%1&toVersion=%2&locale=%3"
    )

    private lateinit var settings: SettingsStorage
    private lateinit var storage: MapStorageManager

    @Before
    fun setUp() {
        mapsRoot().toFile().deleteRecursively()
        settings = SettingsStorage(context)
        storage = MapStorageManager(context)
    }

    @Test
    fun theFirstAppStartOfTheDayRunsTheCheck() = runTest {
        useRepositorySource()
        installRegion(berlinDirectory)
        server.serveIndex(listOf(berlin))
        server.serveSlot(berlin)

        val updates = newUpdates()
        updates.checkIfDue()
        advanceUntilIdle()

        val state = updates.state.value
        assertEquals(1, state.updates.size)
        assertEquals(berlinDirectory, state.updates.single().directoryName)
        assertEquals(berlin, state.updates.single().idPath)
        assertEquals(UpdateState.UpdateAvailable(4096L), state.updates.single().state)
        assertEquals(now, state.lastCheckedAtEpochMs)
        assertEquals("the day's check is recorded", now, settings.load().mapUpdateCheckAt)
    }

    @Test
    fun aSecondStartInTheSameDayRunsNoCheck() = runTest {
        useRepositorySource()
        installRegion(berlinDirectory)
        server.serveIndex(listOf(berlin))
        server.serveSlot(berlin)
        newUpdates().also { it.checkIfDue(); advanceUntilIdle() }
        val requestsAfterTheFirstCheck = server.requested.toList()
        now += 3_600_000L

        // A second process: same persisted state, a new instance.
        val second = newUpdates()
        second.checkIfDue()
        advanceUntilIdle()

        assertEquals("no comparison request is issued by the start", requestsAfterTheFirstCheck, server.requested)
        assertEquals(1, second.state.value.updates.size)
    }

    @Test
    fun checkIfDueIsANoOpWhenNotDue() = runTest {
        useRepositorySource()
        installRegion(berlinDirectory)
        server.serveIndex(listOf(berlin))
        server.serveSlot(berlin)
        val updates = newUpdates()
        updates.checkIfDue()
        advanceUntilIdle()
        val checkedAt = updates.state.value.lastCheckedAtEpochMs
        val requests = server.requested.size
        now += 60_000L

        updates.checkIfDue()
        advanceUntilIdle()

        assertEquals(requests, server.requested.size)
        assertEquals(checkedAt, updates.state.value.lastCheckedAtEpochMs)
    }

    @Test
    fun checkIfDueLaunchesOnABackgroundDispatcher() = runTest {
        useRepositorySource()
        installRegion(berlinDirectory)
        server.serveIndex(listOf(berlin))
        server.serveSlot(berlin)
        val updates = newUpdates()

        updates.checkIfDue()

        assertTrue("the caller returns before any request is made", server.requested.isEmpty())
        assertEquals("and before any result is published", 0L, updates.state.value.lastCheckedAtEpochMs)

        advanceUntilIdle()

        assertEquals(1, server.requested.count { it == server.indexUrl })
    }

    @Test
    fun anUnfinishedCheckIsRetriedAtTheNextStart() = runTest {
        useRepositorySource()
        installRegion(berlinDirectory)
        server.serveIndex(listOf(berlin))
        server.serveSlot(berlin)
        server.failTextOf = server.indexUrl to RepositoryFailure.TransportFailed("offline")
        val updates = newUpdates()
        updates.checkIfDue()
        advanceUntilIdle()

        assertEquals(
            RepositoryFailure.TransportFailed("offline"),
            updates.state.value.lastAttemptFailure
        )
        assertEquals("a check that did not complete is not the day's check", 0L, settings.load().mapUpdateCheckAt)

        server.failTextOf = null
        now += 60_000L
        updates.checkIfDue()
        advanceUntilIdle()

        assertNull("the retry completed", updates.state.value.lastAttemptFailure)
        assertEquals(1, updates.state.value.updates.size)
        assertEquals(now, settings.load().mapUpdateCheckAt)
    }

    @Test
    fun aCheckNeverRunsTwiceAtOnce() = runTest {
        useRepositorySource()
        installRegion(berlinDirectory)
        server.serveIndex(listOf(berlin))
        server.serveSlot(berlin)
        val gate = CompletableDeferred<Unit>()
        server.gate = gate
        val updates = newUpdates()

        updates.checkIfDue()
        advanceUntilIdle()
        updates.checkIfDue()
        advanceUntilIdle()

        assertEquals("the second request never reached the repository", 1, server.requested.count { it == server.indexUrl })

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, updates.state.value.updates.size)
    }

    @Test
    fun aManualCheckCountsAsTheDaysCheck() = runTest {
        useRepositorySource()
        installRegion(berlinDirectory)
        server.serveIndex(listOf(berlin))
        server.serveSlot(berlin)
        val updates = newUpdates()

        updates.checkNow()
        advanceUntilIdle()

        assertEquals(now, settings.load().mapUpdateCheckAt)

        val requests = server.requested.size
        now += 3_600_000L
        updates.checkIfDue()
        advanceUntilIdle()

        assertEquals("the manual check deferred the automatic one", requests, server.requested.size)
    }

    @Test
    fun aFailingManualCheckIsReportedAsSuchAndKeepsThePreviousState() = runTest {
        useRepositorySource()
        installRegion(berlinDirectory)
        server.serveIndex(listOf(berlin))
        server.serveSlot(berlin)
        val updates = newUpdates()
        updates.checkNow()
        advanceUntilIdle()
        val checkedAt = updates.state.value.lastCheckedAtEpochMs

        server.failTextOf = server.indexUrl to RepositoryFailure.TransportFailed("offline")
        now += 1_000L
        updates.checkNow()
        advanceUntilIdle()

        assertEquals(
            RepositoryFailure.TransportFailed("offline"),
            updates.state.value.lastAttemptFailure
        )
        assertEquals("the update the last check found is still shown", 1, updates.state.value.updates.size)
        assertEquals("the reported completion time is the last completed one", checkedAt, updates.state.value.lastCheckedAtEpochMs)
        assertEquals("a failed attempt is not the day's check", checkedAt, settings.load().mapUpdateCheckAt)
    }

    @Test
    fun theReportStaysUntilTheNextCheck() = runTest {
        useRepositorySource()
        installRegion(berlinDirectory)
        server.serveIndex(listOf(berlin))
        server.serveSlot(berlin)
        val updates = newUpdates()
        updates.checkNow()
        advanceUntilIdle()
        val report = updates.state.value.report

        val second = newUpdates()
        second.checkIfDue()
        advanceUntilIdle()

        assertEquals(report, second.state.value.report)
    }

    @Test
    fun aNewCheckReplacesTheStoredResult() = runTest {
        useRepositorySource()
        installRegion(berlinDirectory)
        installRegion(hamburgDirectory)
        server.serveIndex(listOf(berlin, hamburg))
        server.serveSlot(berlin)
        server.serveSlot(hamburg)
        val updates = newUpdates()
        updates.checkNow()
        advanceUntilIdle()
        assertEquals(2, settings.load().mapUpdates.size)

        // The repository republishes Berlin as what is installed: its row must not survive.
        server.serveSlot(berlin, generatedAt = installedGeneratedAt)
        now += 1_000L
        updates.checkNow()
        advanceUntilIdle()

        assertEquals(1, updates.state.value.updates.size)
        assertEquals(listOf(hamburgDirectory), updates.state.value.updates.map { it.directoryName })
        assertEquals(1, settings.load().mapUpdates.size)
    }

    @Test
    fun theListIsTheSameAfterAColdStart() = runTest {
        useRepositorySource()
        installRegion(berlinDirectory)
        server.serveIndex(listOf(berlin))
        server.serveSlot(berlin)
        val first = newUpdates()
        first.checkNow()
        advanceUntilIdle()
        val checkedAt = first.state.value.lastCheckedAtEpochMs
        val requests = server.requested.toList()

        now += 3_600_000L
        val afterRestart = newUpdates()
        afterRestart.checkIfDue()
        advanceUntilIdle()

        assertEquals(1, afterRestart.state.value.updates.size)
        assertEquals(checkedAt, afterRestart.state.value.lastCheckedAtEpochMs)
        assertEquals("the start probes nothing", requests, server.requested)
    }

    @Test
    fun aStoredUpdateOfADeletedMapIsDropped() = runTest {
        useRepositorySource()
        installRegion(berlinDirectory)
        server.serveIndex(listOf(berlin))
        server.serveSlot(berlin)
        val first = newUpdates()
        first.checkNow()
        advanceUntilIdle()
        assertEquals(1, first.state.value.updates.size)

        // The user deletes the map; the next start must not show a row for it.
        mapsRoot().resolve(berlinDirectory).toFile().deleteRecursively()
        val afterRestart = newUpdates()
        afterRestart.checkIfDue()
        advanceUntilIdle()

        assertTrue(afterRestart.state.value.updates.isEmpty())
        assertTrue("the settings file is pruned too", settings.load().mapUpdates.isEmpty())
    }

    @Test
    fun aProviderSourceIsNeverProbed() = runTest {
        installRegion(berlinDirectory)
        server.serveIndex(listOf(berlin))
        server.serveSlot(berlin)
        val updates = newUpdates()

        updates.checkIfDue()
        advanceUntilIdle()
        updates.checkNow()
        advanceUntilIdle()

        assertTrue("the built-in provider reports no update state and issues no request", server.requested.isEmpty())
        assertTrue(updates.state.value.neverChecked)
    }

    @Test
    fun theCheckReportsOneLinePerComparedItem() = runTest {
        val items = listOf(
            MapUpdateItem(
                kind = MapUpdateKind.REGION,
                directoryName = berlinDirectory,
                displayName = "Berlin",
                idPath = berlin,
                state = UpdateState.UpdateAvailable(4096L)
            ),
            MapUpdateItem(
                kind = MapUpdateKind.REGION,
                directoryName = hamburgDirectory,
                displayName = "Hamburg",
                idPath = hamburg,
                state = UpdateState.UpToDate
            ),
            MapUpdateItem(
                kind = MapUpdateKind.BASEMAP,
                directoryName = "basemap",
                displayName = null,
                idPath = null,
                state = UpdateState.NoLongerOffered
            ),
            MapUpdateItem(
                kind = MapUpdateKind.REGION,
                directoryName = "atlantis",
                displayName = null,
                idPath = null,
                state = UpdateState.Unknown()
            )
        )

        val lines = newUpdates().updateCheckLines(
            items,
            leftoverStaging = listOf(berlinDirectory),
            failure = null
        )

        assertEquals("one line per compared installation and leftover, then the check's own outcome", 6, lines.size)
        assertTrue(lines.any { it.contains("region $berlinDirectory -> update available, 4096 bytes") })
        assertTrue(lines.any { it.contains("region $hamburgDirectory -> up to date") })
        assertTrue(lines.any { it.contains("basemap basemap -> no longer offered") })
        assertTrue(lines.any { it.contains("region atlantis -> unknown") })
        assertTrue(lines.any { it.contains("leftover staging data of $berlinDirectory") })
        assertTrue(lines.any { it.contains("update check completed: 4 compared, 1 with an update") })
    }

    @Test
    fun aCheckThatDidNotCompleteReportsWhy() = runTest {
        val failure = RepositoryFailure.TransportFailed("offline")

        val lines = newUpdates().updateCheckLines(emptyList(), emptyList(), failure)

        assertEquals(listOf("update check did not complete: $failure"), lines)
    }

    @Test
    fun theAppStartSweepsLeftoverStagingData() = runTest {
        useRepositorySource()
        installRegion(berlinDirectory)
        val leftover = mapsRoot().resolve(".$berlinDirectory${SlotInstall.BACKUP_SUFFIX}")
        Files.createDirectories(leftover)
        Files.write(leftover.resolve("types.dat"), "previous".toByteArray())
        server.serveIndex(listOf(berlin))
        server.serveSlot(berlin)

        newUpdates().also { it.checkIfDue(); advanceUntilIdle() }

        assertFalse("the app start removes what an earlier process left behind", Files.exists(leftover))
        assertTrue(
            "and the installation it belonged to is untouched",
            Files.isRegularFile(mapsRoot().resolve(berlinDirectory).resolve("types.dat"))
        )
    }

    @Test
    fun aManualCheckLeavesStagingDataAlone() = runTest {
        useRepositorySource()
        installRegion(berlinDirectory)
        val staging = mapsRoot().resolve(".$berlinDirectory${SlotInstall.INCOMING_SUFFIX}")
        Files.createDirectories(staging)
        server.serveIndex(listOf(berlin))
        server.serveSlot(berlin)

        // A manual check can run beside a transfer, so it must not remove what a transfer is writing.
        newUpdates().also { it.checkNow(); advanceUntilIdle() }

        assertTrue("the staging directory of a possible transfer survives", Files.exists(staging))
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private fun TestScope.newUpdates(): MapUpdates {
        // Both the check and the settings file work on the test scheduler, so a case advances them instead
        // of waiting for a real dispatcher.
        val scheduler = StandardTestDispatcher(testScheduler)
        settings.ioDispatcher = scheduler
        return MapUpdates(
            settingsStorage = settings,
            sourceRegistry = MapSourceRegistry(settings, provider, HttpUrlFetcher()),
            storageManager = storage,
            fetcher = server,
            timeSource = timeSource
        ).apply { ioDispatcher = scheduler }
    }

    private suspend fun useRepositorySource() {
        settings.update {
            it.copy(mapSourceKind = MapSourceKind.REPOSITORY.name, mapRepositoryUrl = baseUrl)
        }
    }

    /** Install a repository map: a complete database directory carrying the app's source marker. */
    private fun installRegion(directoryName: String, generatedAt: String = installedGeneratedAt) {
        val directory = mapsRoot().resolve(directoryName)
        Files.createDirectories(directory)
        Files.write(directory.resolve("types.dat"), "types".toByteArray())
        val document =
            """{"schema":1,"typeConfigVersion":$version,"generatedAt":"$generatedAt",""" +
                """"output":{"files":{"map.lib":{"size":10,"crc32":1}}}}"""
        Files.write(directory.resolve("db.json"), document.toByteArray())
        assertTrue(
            "the installation names the repository it came from",
            MapSourceMarker.write(
                directory,
                MapSourceRecord(MapSourceKind.REPOSITORY, baseUrl, version)
            )
        )
    }

    private fun mapsRoot() = Paths.get(context.filesDir.absolutePath, "maps")
}
