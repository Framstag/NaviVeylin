package com.framstag.libosmscout.client

/**
 * A [MapDownloadManager] whose installed-map list comes from a test, so a view model that reads it can
 * be exercised without a native map manager.
 *
 * The class lives in the bridge's package because [MapDownloadManager]'s constructor is package-private;
 * its JNI-backed methods are never called here.
 *
 * @param installedDirs answers [getInstalledMaps]
 * @param deleteSucceeds answers [deleteMap]; the default accepts every delete. A test that exercises
 *        what the app does when the native delete reports failure passes the native rule itself —
 *        `MapDirectory::DeleteDatabase()` succeeds only when the directory is gone afterwards
 *        (`MapDirectory.cpp:144-174`, and `RemoveFile` treats a missing file as removed,
 *        `File.cpp:42-52`), so `{ path -> !Files.exists(Path.of(path)) }` mirrors it
 */
class FakeMapDownloadManager(
    private val installedDirs: () -> List<String>,
    private val availableMaps: List<AvailableMapEntry> = emptyList(),
    private val deleteSucceeds: (String) -> Boolean = { true }
) : MapDownloadManager(null) {

    /** Directories this fake was asked to delete, in call order. */
    val deleted = mutableListOf<String>()

    /** Directories this fake was asked to register, in call order. */
    val registered = mutableListOf<String>()

    override fun getInstalledMaps(): List<String> = installedDirs()

    /** No provider is contacted from a unit test. */
    override fun fetchAvailableMaps(provider: MapProvider): List<AvailableMapEntry> = availableMaps

    override fun deleteMap(path: String): Boolean {
        deleted += path
        return deleteSucceeds(path)
    }

    override fun registerMapDirectory(path: String): Boolean {
        registered += path
        return true
    }
}
