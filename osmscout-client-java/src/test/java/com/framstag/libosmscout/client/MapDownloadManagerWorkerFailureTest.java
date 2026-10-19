package com.framstag.libosmscout.client;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A provider download whose worker thread dies SHALL report the failure through the listener.
 * <p>
 * Spec: map-download-infrastructure — "Error handling for download failures" / "A worker that dies
 * reports the failure". The worker runs on its own thread, so anything it throws without the
 * manager's knowledge reaches the process-wide uncaught-exception handler: the user keeps an entry
 * stuck at "Starting", the foreground service is never told the download ended, and a run prints a
 * stack trace that no case owns.
 * <p>
 * The failure used here is an {@link Error}, not an {@link Exception}: this module carries no JNI
 * library, so the worker's first native call dies with {@code UnsatisfiedLinkError} — the same shape
 * a missing symbol has on a real device, and the same class of failure an overflow has.
 */
class MapDownloadManagerWorkerFailureTest {

    @Test
    void aWorkerThatDiesOfAnErrorIsReportedThroughTheListener() throws Exception {
        MapDownloadManager manager = new MapDownloadManager(null);
        Path targetDir = Files.createTempDirectory("map-download-worker");
        AvailableMapEntry entry = new AvailableMapEntry(
            "Berlin", List.of("europe", "germany"), "", null, 0L, "europe/germany/berlin", 0L, -1);

        CountDownLatch reported = new CountDownLatch(1);
        AtomicReference<String> errorMessage = new AtomicReference<>();
        MapDownloadListener listener = new MapDownloadListener() {
            @Override
            public void onError(String mapName, String message) {
                errorMessage.set(message);
                reported.countDown();
            }
        };

        manager.downloadMap(entry, targetDir, listener);

        assertTrue(reported.await(10, TimeUnit.SECONDS),
                   "a worker that dies must report the failure instead of dying silently");
        assertNotNull(errorMessage.get());
        assertTrue(errorMessage.get().contains("UnsatisfiedLinkError"),
                   "the report names what went wrong: " + errorMessage.get());
    }

    @Test
    void aWorkerThatDiesOfAnExceptionKeepsItsMessage() throws Exception {
        MapDownloadManager manager = new MapDownloadManager(null);
        Path targetDir = Files.createTempDirectory("map-download-worker");
        // The three-argument constructor builds a directory entry; the worker refuses it with a message.
        AvailableMapEntry directoryEntry = new AvailableMapEntry("Europe", List.of(), "a continent");

        CountDownLatch reported = new CountDownLatch(1);
        AtomicReference<String> errorMessage = new AtomicReference<>();
        MapDownloadListener listener = new MapDownloadListener() {
            @Override
            public void onError(String mapName, String message) {
                errorMessage.set(message);
                reported.countDown();
            }
        };

        manager.downloadMap(directoryEntry, targetDir, listener);

        assertTrue(reported.await(10, TimeUnit.SECONDS), "the worker reports its failure");
        assertEquals("Cannot download a directory entry: Europe", errorMessage.get(),
                     "an Exception's own message stays the report, word for word");
    }
}
