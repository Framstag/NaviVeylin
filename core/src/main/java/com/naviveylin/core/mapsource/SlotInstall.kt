package com.naviveylin.core.mapsource

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * The three moves that install a verified slot beside the data it replaces, shared by a regional
 * database and the basemap so one implementation writes, checks and swaps both.
 *
 * ```
 *   <target>.incoming   the transferred slot, verified against its own metadata
 *   <target>            the installation the app renders and lists
 *   <target>.backup     the previous installation, kept only for the duration of the swap
 * ```
 *
 * The point is ordering: the installed directory is never written to, so it stays complete and usable
 * while a transfer runs, and a transfer that is interrupted, cancelled or fails leaves it exactly as it
 * was (spec `map-updates` — "An update replaces the installation only when it is complete").
 *
 * Staging lives beside its target on purpose: the swap is then a rename on one filesystem, which is what
 * makes it atomic (`RepositoryBasemap` did this first; design D5).
 */
object SlotInstall {

    /** Suffix of the directory a transfer writes into. */
    const val INCOMING_SUFFIX = ".incoming"

    /** Suffix of the directory holding the previous installation during a swap. */
    const val BACKUP_SUFFIX = ".backup"

    /**
     * The installation a staging directory belongs to, or null when [directoryName] is not staging data.
     *
     * Staging data is *recognised*, never hidden: it is a directory inside the maps tree whose name is a
     * dot, the name of the installation it belongs to, and [INCOMING_SUFFIX] or [BACKUP_SUFFIX]. Anything
     * consuming the maps tree can therefore tell "an installation" from "what an update attempt left
     * behind", so a leftover is never mistaken for a map to open, list or compare.
     *
     * @param directoryName the name of a directory under the maps root
     * @return the name of the installation it stages for, or null when it is not staging data
     */
    fun stagingTargetOf(directoryName: String): String? {
        if (!directoryName.startsWith(STAGING_PREFIX)) {
            return null
        }
        val body = directoryName.removePrefix(STAGING_PREFIX)
        for (suffix in STAGING_SUFFIXES) {
            val target = body.removeSuffix(suffix)
            if (target != body && target.isNotEmpty()) {
                return target
            }
        }
        return null
    }

    /** Whether [directoryName] is data a previous update attempt left behind. */
    fun isStaging(directoryName: String): Boolean = stagingTargetOf(directoryName) != null

    /** The directory a transfer for [target] writes into. */
    fun incomingFor(target: Path): Path = sibling(target, INCOMING_SUFFIX)

    /** The directory holding [target]'s previous contents during a swap. */
    fun backupFor(target: Path): Path = sibling(target, BACKUP_SUFFIX)

    private fun sibling(target: Path, suffix: String): Path =
        target.resolveSibling(".${target.fileName}$suffix")

    /**
     * Remove what a previous attempt left behind.
     *
     * A completed transfer that never reached its swap, or a swap whose backup could not be deleted,
     * would otherwise sit in the maps tree with a `types.dat` in it — which is what the app's own
     * installed-map scan looks for.
     */
    fun sweep(target: Path) {
        deleteRecursively(incomingFor(target))
        deleteRecursively(backupFor(target))
    }

    /**
     * Transfer [plan] into [incoming] and verify every file against it.
     *
     * @return whether the slot is complete, was cancelled, or failed — the installed data is untouched in
     *         all three cases, and [incoming] is removed by the downloader unless it completed
     */
    suspend fun downloadInto(
        incoming: Path,
        fetcher: RepositoryFetcher,
        plan: DownloadPlanResult.Planned,
        metadataDocument: String,
        isCancelled: () -> Boolean,
        onProgress: (Long, Long) -> Unit
    ): DownloadOutcome =
        DatabaseDownloader(fetcher).download(plan, metadataDocument, incoming, isCancelled, onProgress)

    /**
     * Replace [target] with the completed [incoming] slot, keeping the installed data until the new one
     * is in place and restoring it when the swap fails.
     *
     * @throws Exception when neither the swap nor the restore could be completed; the caller reports it
     */
    fun replace(incoming: Path, target: Path) {
        val backup = backupFor(target)
        deleteRecursively(backup)

        val hadInstalled = Files.exists(target)
        if (hadInstalled) {
            Files.move(target, backup, StandardCopyOption.REPLACE_EXISTING)
        }
        try {
            Files.createDirectories(target.parent)
            try {
                Files.move(incoming, target, StandardCopyOption.ATOMIC_MOVE)
            } catch (notSupported: java.nio.file.AtomicMoveNotSupportedException) {
                copyRecursively(incoming, target)
                deleteRecursively(incoming)
            }
        } catch (failure: Exception) {
            if (hadInstalled) {
                deleteRecursively(target)
                Files.move(backup, target, StandardCopyOption.REPLACE_EXISTING)
            }
            deleteRecursively(incoming)
            throw failure
        }
        deleteRecursively(backup)
    }

    /** Remove [directory] and everything below it; a missing directory is not an error. */
    fun deleteRecursively(directory: Path) {
        if (!Files.exists(directory)) {
            return
        }
        Files.walk(directory).sorted(Comparator.reverseOrder()).forEach { path ->
            runCatching { Files.deleteIfExists(path) }
        }
    }

    private fun copyRecursively(source: Path, target: Path) {
        Files.walk(source).forEach { path ->
            val destination = target.resolve(source.relativize(path).toString())
            if (Files.isDirectory(path)) {
                Files.createDirectories(destination)
            } else {
                Files.createDirectories(destination.parent)
                Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    /** Staging directories are dot-prefixed, so they never collide with an installation. */
    private const val STAGING_PREFIX = "."

    private val STAGING_SUFFIXES = listOf(INCOMING_SUFFIX, BACKUP_SUFFIX)
}
