package com.framstag.libosmscout.client;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manager for downloading maps from configured map providers.
 * <p>
 * Wraps native C++ {@code MapDownloadService} methods via JNI.
 * Thread-safe for concurrent access.
 * <p>
 * Uses {@link HttpURLConnection} instead of {@code java.net.http.HttpClient}
 * for Android compatibility (no desugaring dependency).
 */
public class MapDownloadManager {

    /** Reference to the OSMScoutClient that owns this manager. */
    private final OSMScoutClient client;
    /** List of currently active downloads. */
    private final List<ActiveDownload> activeDownloads = new CopyOnWriteArrayList<>();

    /**
     * Package-private constructor, called from OSMScoutClient.
     *
     * @param client the owning OSMScoutClient
     */
    MapDownloadManager(OSMScoutClient client) {
        this.client = client;
    }

    /**
     * Fetch the list of available maps from a provider.
     *
     * @param provider the map provider to query
     * @return list of top-level entries (directories may have children)
     */
    public List<AvailableMapEntry> fetchAvailableMaps(MapProvider provider) {
        try {
            String urlStr = provider.getListUri()
                .replace("%1", "27")
                .replace("%2", "27")
                .replace("%3", "en");

            HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            int status = conn.getResponseCode();
            if (status / 100 != 2) {
                System.err.println("[MapDownloadManager] HTTP error: " + status);
                return Collections.emptyList();
            }

            String json = new String(conn.getInputStream().readAllBytes());
            conn.disconnect();
            return nativeParseMapList(json, provider);
        } catch (Exception e) {
            System.err.println("[MapDownloadManager] HTTP error: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Download a map to the specified directory.
     *
     * @param entry    the map entry to download
     * @param targetDir local directory to download into
     * @param listener callback for progress and completion events
     * @return a handle that can be used to cancel the download
     */
    public synchronized String downloadMap(AvailableMapEntry entry,
                                            Path targetDir,
                                            MapDownloadListener listener) {
        String handle = java.util.UUID.randomUUID().toString();
        ActiveDownload ad = new ActiveDownload(handle, entry.getName(), listener);
        activeDownloads.add(ad);

        Thread worker = new Thread(() -> {
            try {
                downloadMapInJava(ad, entry, targetDir, listener);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                listener.onError(entry.getName(), "Download cancelled");
            } catch (Exception e) {
                listener.onError(entry.getName(), e.getMessage());
            } finally {
                activeDownloads.remove(ad);
            }
        }, "map-download-" + entry.getName());
        ad.setWorker(worker);
        worker.setDaemon(true);
        worker.start();

        return handle;
    }

    /**
     * Synchronous map download orchestration done entirely in Java.
     * Called on the background worker thread.
     */
    private void downloadMapInJava(ActiveDownload ad,
                                    AvailableMapEntry entry,
                                    Path targetDir,
                                    MapDownloadListener listener) throws Exception {
        String mapName = entry.getName();
        if (entry.isDirectory()) {
            throw new IllegalArgumentException("Cannot download a directory entry: " + mapName);
        }

        listener.onProgress(mapName, 0, entry.getSize());

        if (!nativePrepareMapDirectory(entry, targetDir.toString())) {
            throw new RuntimeException("Failed to prepare map directory");
        }

        String[] fileNames = nativeGetMapFileNames();
        String serverBase = entry.getProvider().getUri() + "/" + entry.getServerDirectory();
        long totalBytes = entry.getSize();
        long downloadedBytes = 0;

        for (String fileName : fileNames) {
            if (ad.isCancelled()) {
                cleanupDirectory(targetDir);
                listener.onError(mapName, "Download cancelled");
                return;
            }

            String fileUrl = serverBase + "/" + fileName;
            Path tempFile = targetDir.resolve(fileName + ".download");
            Path finalFile = targetDir.resolve(fileName);

            boolean ok = downloadSingleFile(ad, fileUrl, tempFile, mapName, listener,
                                            downloadedBytes, totalBytes);
            if (!ok) {
                cleanupDirectory(targetDir);
                listener.onError(mapName, ad.isCancelled() ? "Download cancelled" : "Failed to download " + fileName);
                return;
            }

            Files.move(tempFile, finalFile, StandardCopyOption.REPLACE_EXISTING);

            long size = 0;
            try {
                size = Files.size(finalFile);
            } catch (Exception ignored) {
            }
            downloadedBytes += size;
        }

        if (!nativeRegisterMapDirectory(targetDir.toString())) {
            cleanupDirectory(targetDir);
            listener.onError(mapName, "Failed to register map directory");
            return;
        }

        listener.onComplete(mapName, targetDir.toString());
    }

    /**
     * Download a single file using {@link HttpURLConnection}.
     */
    private boolean downloadSingleFile(ActiveDownload ad,
                                        String url,
                                        Path dest,
                                        String mapName,
                                        MapDownloadListener listener,
                                        long currentBase,
                                        long totalBytes) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);

            int status = conn.getResponseCode();
            if (status / 100 != 2) {
                return false;
            }

            Files.createDirectories(dest.getParent());

            long fileSize = 0;
            byte[] buffer = new byte[8192];
            try (InputStream in = conn.getInputStream();
                 OutputStream out = Files.newOutputStream(dest)) {
                ad.setCurrentStream(in);
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    if (ad.isCancelled()) {
                        throw new InterruptedException("Download cancelled");
                    }
                    out.write(buffer, 0, read);
                    fileSize += read;
                    listener.onProgress(mapName, currentBase + fileSize, totalBytes);
                }
            } finally {
                ad.setCurrentStream(null);
            }

            listener.onProgress(mapName, currentBase + fileSize, totalBytes);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            if (!ad.isCancelled()) {
                System.err.println("[MapDownloadManager] download failed for " + url + ": " + e.getMessage());
            }
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Remove a partially downloaded directory.
     */
    private void cleanupDirectory(Path dir) {
        try {
            Files.walk(dir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                    }
                });
        } catch (Exception e) {
            System.err.println("[MapDownloadManager] cleanup failed: " + e.getMessage());
        }
    }

    /**
     * Cancel an active download by handle.
     */
    public void cancelDownload(String handle) {
        for (ActiveDownload ad : activeDownloads) {
            if (ad.handle.equals(handle)) {
                ad.cancel();
                return;
            }
        }
    }

    /**
     * Get the list of installed map directories.
     *
     * @return list of directory paths
     */
    public List<String> getInstalledMaps() {
        return nativeGetInstalledMaps();
    }

    /**
     * Delete an installed map by directory path.
     *
     * @param path the directory path of the map to delete
     * @return true if deleted successfully
     */
    public boolean deleteMap(String path) {
        return nativeDeleteMap(path);
    }

    // ---- Native methods ----

    private native List<AvailableMapEntry> nativeParseMapList(String json, MapProvider provider);
    private native String[] nativeGetMapFileNames();
    private native boolean nativePrepareMapDirectory(AvailableMapEntry entry, String targetDir);
    private native boolean nativeRegisterMapDirectory(String targetDir);
    private native void nativeCancelDownload(String handle);
    private native List<String> nativeGetInstalledMaps();
    private native boolean nativeDeleteMap(String path);

    // ---- Internal helper ----

    /** Tracks an active download. */
    private static class ActiveDownload {
        final String handle;
        final String mapName;
        final MapDownloadListener listener;
        private Thread worker;
        private volatile InputStream currentStream;
        private volatile boolean cancelled;

        ActiveDownload(String handle, String mapName, MapDownloadListener listener) {
            this.handle = handle;
            this.mapName = mapName;
            this.listener = listener;
        }

        void setWorker(Thread worker) {
            this.worker = worker;
        }

        void setCurrentStream(InputStream stream) {
            this.currentStream = stream;
        }

        void cancel() {
            cancelled = true;
            if (worker != null) {
                worker.interrupt();
            }
            InputStream s = currentStream;
            if (s != null) {
                try {
                    s.close();
                } catch (Exception ignored) {
                }
            }
        }

        boolean isCancelled() {
            return cancelled;
        }
    }
}
