package com.naviveylin.core.mapsource

import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Executes a database download: writes each planned file, checks it against its declared size and
 * CRC-32 while the bytes arrive, and leaves the directory either complete or absent (spec
 * `map-repository-source` — "Verified download completes" / "A corrupt file fails the download" /
 * "Cancelling or failing a repository download leaves no partial data").
 *
 * The caller owns the threading: this is a suspend function that blocks on file I/O, so it is
 * invoked from a background dispatcher (`guidelines/Design.md` §4), never from the main thread.
 */
class DatabaseDownloader(private val fetcher: RepositoryFetcher) {

    /**
     * Download [plan] into [targetDirectory], replacing whatever is there.
     *
     * @param plan the files to fetch, in write order (the type configuration last)
     * @param metadataDocument the database's metadata document, stored into the installed directory
     * @param targetDirectory the directory to install into
     * @param isCancelled polled between chunks and between files
     * @param onProgress reported with (bytes done, bytes planned)
     * @return whether the directory is complete, cancelled, or was removed after a failure
     */
    suspend fun download(
        plan: DownloadPlanResult.Planned,
        metadataDocument: String,
        targetDirectory: Path,
        isCancelled: () -> Boolean = { false },
        onProgress: (bytesDone: Long, bytesPlanned: Long) -> Unit = { _, _ -> }
    ): DownloadOutcome {
        deleteRecursively(targetDirectory)
        Files.createDirectories(targetDirectory)

        var bytesDone = 0L
        onProgress(bytesDone, plan.totalBytes)

        for (file in plan.files) {
            if (isCancelled()) {
                deleteRecursively(targetDirectory)
                return DownloadOutcome.Cancelled
            }

            val tempFile = targetDirectory.resolve(file.name + TEMP_SUFFIX)
            val finalFile = targetDirectory.resolve(file.name)
            Files.createDirectories(finalFile.parent ?: targetDirectory)

            val verifier = FileVerifier(DatabaseFile(file.name, file.sizeBytes, file.crc32))
            val failure = try {
                Files.newOutputStream(
                    tempFile,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
                ).use { out ->
                    fetcher.download(file.url) { buffer, length ->
                        if (isCancelled()) {
                            throw RepositoryTransferCancelled()
                        }
                        out.write(buffer, 0, length)
                        verifier.update(buffer, length)
                        onProgress(bytesDone + verifier.writtenBytes(), plan.totalBytes)
                    }
                }
            } catch (cancelled: RepositoryTransferCancelled) {
                deleteRecursively(targetDirectory)
                return DownloadOutcome.Cancelled
            }

            if (failure != null) {
                deleteRecursively(targetDirectory)
                return DownloadOutcome.Failed(failure)
            }
            verifier.verify()?.let { verificationFailure ->
                deleteRecursively(targetDirectory)
                return DownloadOutcome.Failed(verificationFailure)
            }

            Files.move(tempFile, finalFile, StandardCopyOption.REPLACE_EXISTING)
            bytesDone += verifier.writtenBytes()
            onProgress(bytesDone, plan.totalBytes)
        }

        if (isCancelled()) {
            deleteRecursively(targetDirectory)
            return DownloadOutcome.Cancelled
        }

        writeMetadata(targetDirectory, metadataDocument)
        return DownloadOutcome.Completed
    }

    /**
     * Store the metadata document inside the installed directory, so the installation is
     * self-describing (which database version it is) without another request.
     */
    private fun writeMetadata(targetDirectory: Path, metadataDocument: String) {
        val metadataFile = targetDirectory.resolve(DatabaseMetadataParser.METADATA_FILE_NAME)
        Files.newOutputStream(
            metadataFile,
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
        ).use { out: OutputStream -> out.write(metadataDocument.toByteArray()) }
    }

    /** Remove [directory] and everything below it; a missing directory is not an error. */
    private fun deleteRecursively(directory: Path) {
        if (!Files.exists(directory)) {
            return
        }
        Files.walk(directory).sorted(Comparator.reverseOrder()).forEach { path ->
            runCatching { Files.deleteIfExists(path) }
        }
    }

    /** Thrown inside the byte callback to abort a transfer that was cancelled mid-file. */
    private companion object {
        /** Suffix of a file that is still being written, so a partial file is never mistaken for one. */
        const val TEMP_SUFFIX = ".download"
    }
}
