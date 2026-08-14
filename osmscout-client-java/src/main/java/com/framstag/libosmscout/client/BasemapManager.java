package com.framstag.libosmscout.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manager for discovering, downloading, and managing the world basemap.
 * <p>
 * The basemap is a special world-wide map (borders, country names, coastlines)
 * hosted on the map provider's server at {@code {baseUri}/basemap/}. It is not
 * listed in the standard map JSON listing and is distributed as tar.gz archives.
 * <p>
 * Android port of the upstream JavaScout {@code BasemapManager}: uses
 * {@link HttpURLConnection} instead of {@code java.net.http.HttpClient}
 * (no desugaring dependency, see map-download-infrastructure spec) and tracks
 * active downloads for real cancellation.
 */
public class BasemapManager {

    /** Well-known basemap path segment on the provider server. */
    public static final String BASEMAP_PATH = "basemap";

    /** Subdirectory name under the maps directory where basemap is stored. */
    public static final String BASEMAP_DIR_NAME = "basemap";

    /** Pattern to extract date from Apache-style listing (e.g., "2026-02-23 00:15"). */
    private static final DateTimeFormatter APACHE_DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US);

    /** The map provider whose server we probe for basemap archives. */
    private final MapProvider provider;

    /** Directory where downloaded maps (including basemap) are stored. */
    private final Path mapsDirectory;

    /** Active basemap downloads, keyed by cancellation handle. */
    private final List<ActiveDownload> activeDownloads = new CopyOnWriteArrayList<>();

    /**
     * Represents an available basemap archive on the server.
     */
    public static class BasemapArchive {
        /** Archive file name (e.g., "BaseMap-2026-02-23.tar.gz"). */
        private final String fileName;
        /** Archive size in bytes. */
        private final long sizeBytes;
        /** Last modified date from server. */
        private final LocalDateTime lastModified;

        /**
         * @param fileName     archive file name
         * @param sizeBytes    archive size in bytes
         * @param lastModified last modified date from server
         */
        BasemapArchive(String fileName, long sizeBytes, LocalDateTime lastModified) {
            this.fileName = fileName;
            this.sizeBytes = sizeBytes;
            this.lastModified = lastModified;
        }

        /**
         * @return archive file name (e.g., "BaseMap-2026-02-23.tar.gz")
         */
        public String getFileName() {
            return fileName;
        }

        /**
         * @return archive size in bytes
         */
        public long getSizeBytes() {
            return sizeBytes;
        }

        /**
         * @return last modified date from server
         */
        public LocalDateTime getLastModified() {
            return lastModified;
        }

        /**
         * @return human-readable size string
         */
        public String getSizeHuman() {
            if (sizeBytes < 1024) return sizeBytes + " B";
            if (sizeBytes < 1024 * 1024) return String.format(Locale.US, "%.0f KB", sizeBytes / 1024.0);
            if (sizeBytes < 1024 * 1024 * 1024) return String.format(Locale.US, "%.1f MB", sizeBytes / (1024.0 * 1024.0));
            return String.format(Locale.US, "%.2f GB", sizeBytes / (1024.0 * 1024.0 * 1024.0));
        }

        /**
         * @return true if this archive appears to be a "minimal" variant
         */
        public boolean isMinimal() {
            return fileName.toLowerCase().contains("minimal");
        }

        /**
         * @return a display label for this archive (variant, file name, size)
         */
        public String getLabel() {
            String variant = isMinimal() ? "Minimal" : "Full";
            return variant + " — " + fileName + " (" + getSizeHuman() + ")";
        }

        /**
         * @return version date encoded in the archive file name, or null if none
         */
        public LocalDate getVersionDate() {
            return parseVersionDate(fileName);
        }

        @Override
        public String toString() {
            return "BasemapArchive{name='" + fileName + "', size=" + getSizeHuman() + "}";
        }
    }

    /**
     * @param provider      the map provider whose server to probe
     * @param mapsDirectory the local directory where maps (including basemap) are stored
     */
    public BasemapManager(MapProvider provider, Path mapsDirectory) {
        this.provider = provider;
        this.mapsDirectory = mapsDirectory;
    }

    /**
     * @return the base URL for basemap archives on the provider server
     */
    public String getBasemapBaseUrl() {
        return provider.getUri() + "/" + BASEMAP_PATH + "/";
    }

    /**
     * @return the local directory where the basemap would be stored
     */
    public Path getBasemapDirectory() {
        return mapsDirectory.resolve(BASEMAP_DIR_NAME);
    }

    /**
     * Parse the version date encoded in an archive file name.
     * <p>
     * Supported naming patterns:
     * <ul>
     *   <li>new: {@code basemap-YYMMDD-dist.tar.gz} / {@code basemap-YYMMDD-minimal.tar.gz}</li>
     *   <li>legacy: {@code BaseMap-YYYY-MM-DD.tar.gz}</li>
     * </ul>
     *
     * @param fileName archive file name
     * @return version date, or null if the name encodes no date
     */
    public static LocalDate parseVersionDate(String fileName) {
        // New naming: basemap-YYMMDD[-variant].tar.gz
        Matcher yyMmDd = Pattern.compile("-(\\d{2})(\\d{2})(\\d{2})(?:[-.]|$)").matcher(fileName);
        if (yyMmDd.find()) {
            try {
                int year = 2000 + Integer.parseInt(yyMmDd.group(1));
                int month = Integer.parseInt(yyMmDd.group(2));
                int day = Integer.parseInt(yyMmDd.group(3));
                return LocalDate.of(year, month, day);
            } catch (java.time.DateTimeException e) {
                // fall through to legacy pattern
            }
        }
        // Legacy naming: BaseMap-YYYY-MM-DD.tar.gz
        Matcher yyyy = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})").matcher(fileName);
        if (yyyy.find()) {
            try {
                return LocalDate.of(
                    Integer.parseInt(yyyy.group(1)),
                    Integer.parseInt(yyyy.group(2)),
                    Integer.parseInt(yyyy.group(3)));
            } catch (java.time.DateTimeException e) {
                // ignore
            }
        }
        return null;
    }

    /**
     * Probe the provider server for available basemap archives.
     * <p>
     * Fetches the HTML directory listing at {@code {baseUri}/basemap/} and
     * parses it for tar.gz file entries. Failures are silent (basemap is
     * optional) but logged for debugging.
     *
     * @return list of available basemap archives, empty if none found or error
     */
    public List<BasemapArchive> fetchAvailableBasemaps() {
        HttpURLConnection conn = null;
        try {
            String urlStr = getBasemapBaseUrl();
            conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            int status = conn.getResponseCode();
            if (status / 100 != 2) {
                System.err.println("[BasemapManager] Server returned " + status + " for " + urlStr);
                return List.of();
            }

            String body = new String(conn.getInputStream().readAllBytes());
            return parseDirectoryListing(body);
        } catch (Exception e) {
            System.err.println("[BasemapManager] Error probing basemap at " + getBasemapBaseUrl() + ": " + e.getMessage());
            return List.of();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Parse an Apache-style HTML directory listing for tar.gz entries.
     * <p>
     * Supports both table-based format (Apache 2.x) and pre-formatted format.
     * <p>
     * Table row example:
     * {@code <tr><td>...<a href="BaseMap-2026-02-23.tar.gz">name</a></td><td align="right">2026-02-24 00:16</td><td align="right">39M</td></tr>}
     *
     * @param html the HTML directory listing
     * @return parsed archive entries
     */
    static List<BasemapArchive> parseDirectoryListing(String html) {
        List<BasemapArchive> archives = new ArrayList<>();

        // Try table-based format first (Apache 2.x)
        // Find all <tr> elements and extract href, date, size from <td> children
        Matcher rowMatcher = Pattern.compile(
            "<tr[^>]*>(.*?)</tr>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        ).matcher(html);

        while (rowMatcher.find()) {
            String row = rowMatcher.group(1);

            // Find href with .tar.gz
            Matcher hrefMatcher = Pattern.compile(
                "<a\\s+href=\"([^\"]+\\.tar\\.gz)\"",
                Pattern.CASE_INSENSITIVE
            ).matcher(row);
            if (!hrefMatcher.find()) continue;

            String fileName = hrefMatcher.group(1);

            // Extract all <td> content
            List<String> cells = new ArrayList<>();
            Matcher tdMatcher = Pattern.compile(
                "<td[^>]*>(.*?)</td>",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
            ).matcher(row);
            while (tdMatcher.find()) {
                cells.add(tdMatcher.group(1).trim());
            }

            // cells: [0]=icon, [1]=name+link, [2]=date, [3]=size, [4]=description
            LocalDateTime lastModified = null;
            long sizeBytes = 0;

            if (cells.size() >= 3) {
                String dateStr = cells.get(2).replaceAll("\\s+", " ").trim();
                Matcher dateMatcher = Pattern.compile(
                    "(\\d{4}-\\d{2}-\\d{2})\\s+(\\d{2}:\\d{2})"
                ).matcher(dateStr);
                if (dateMatcher.find()) {
                    try {
                        lastModified = LocalDateTime.parse(
                            dateMatcher.group(1) + " " + dateMatcher.group(2),
                            APACHE_DATE_FORMAT);
                    } catch (DateTimeParseException e) {
                        // ignore
                    }
                }
            }

            if (cells.size() >= 4) {
                String sizeStr = cells.get(3).trim();
                Matcher sizeMatcher = Pattern.compile(
                    "([\\d.]+)\\s*([KMG])"
                ).matcher(sizeStr);
                if (sizeMatcher.find()) {
                    try {
                        double value = Double.parseDouble(sizeMatcher.group(1));
                        String unit = sizeMatcher.group(2).toUpperCase();
                        switch (unit) {
                            case "K": sizeBytes = (long) (value * 1024); break;
                            case "M": sizeBytes = (long) (value * 1024 * 1024); break;
                            case "G": sizeBytes = (long) (value * 1024 * 1024 * 1024); break;
                        }
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }
            }

            archives.add(new BasemapArchive(fileName, sizeBytes, lastModified));
        }

        // If table-based parsing found nothing, try pre-formatted format
        if (archives.isEmpty()) {
            archives.addAll(parsePreformattedListing(html));
        }

        // Sort by date descending (newest first)
        archives.sort((a, b) -> {
            if (a.getLastModified() != null && b.getLastModified() != null) {
                return b.getLastModified().compareTo(a.getLastModified());
            }
            return a.getFileName().compareTo(b.getFileName());
        });

        return archives;
    }

    /**
     * Parse pre-formatted Apache directory listing (older format).
     *
     * @param html the HTML directory listing
     * @return parsed archive entries
     */
    private static List<BasemapArchive> parsePreformattedListing(String html) {
        List<BasemapArchive> archives = new ArrayList<>();
        String[] lines = html.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            int hrefStart = line.toLowerCase().indexOf("<a href=\"");
            if (hrefStart < 0) continue;

            int quoteStart = line.indexOf('"', hrefStart + 9);
            if (quoteStart < 0) continue;
            int quoteEnd = line.indexOf('"', quoteStart + 1);
            if (quoteEnd < 0) continue;

            String href = line.substring(quoteStart + 1, quoteEnd);
            if (!href.endsWith(".tar.gz")) continue;

            String fileName = href;
            String rest = line.substring(quoteEnd + 1);

            LocalDateTime lastModified = null;
            Matcher dateMatcher = Pattern.compile(
                "(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2})"
            ).matcher(rest);
            if (dateMatcher.find()) {
                try {
                    lastModified = LocalDateTime.parse(dateMatcher.group(1), APACHE_DATE_FORMAT);
                } catch (DateTimeParseException e) {
                    // ignore
                }
            }

            long sizeBytes = 0;
            Matcher sizeMatcher = Pattern.compile(
                "([\\d.]+)\\s*([KMG])"
            ).matcher(rest);
            if (sizeMatcher.find()) {
                try {
                    double value = Double.parseDouble(sizeMatcher.group(1));
                    String unit = sizeMatcher.group(2).toUpperCase();
                    switch (unit) {
                        case "K": sizeBytes = (long) (value * 1024); break;
                        case "M": sizeBytes = (long) (value * 1024 * 1024); break;
                        case "G": sizeBytes = (long) (value * 1024 * 1024 * 1024); break;
                    }
                } catch (NumberFormatException e) {
                    // ignore
                }
            }

            archives.add(new BasemapArchive(fileName, sizeBytes, lastModified));
        }
        return archives;
    }

    /**
     * Get the latest (newest) basemap archive from the server.
     *
     * @return the newest archive, or null if none available
     */
    public BasemapArchive getLatestBasemap() {
        List<BasemapArchive> available = fetchAvailableBasemaps();
        return available.isEmpty() ? null : available.get(0);
    }

    /**
     * Information about an installed basemap.
     */
    public static class BasemapInfo {
        /** Total size of all files in bytes. */
        private final long sizeBytes;
        /** Number of files in the basemap directory. */
        private final int fileCount;
        /** Last modified time of the basemap directory. */
        private final LocalDateTime lastModified;
        /** Absolute path to the basemap directory. */
        private final String path;

        /**
         * @param sizeBytes    total size of all files in bytes
         * @param fileCount    number of files in the basemap directory
         * @param lastModified last modified time of the basemap directory
         * @param path         absolute path to the basemap directory
         */
        BasemapInfo(long sizeBytes, int fileCount, LocalDateTime lastModified, String path) {
            this.sizeBytes = sizeBytes;
            this.fileCount = fileCount;
            this.lastModified = lastModified;
            this.path = path;
        }

        /** @return total size of all files in bytes */
        public long getSizeBytes() { return sizeBytes; }
        /** @return number of files in the basemap directory */
        public int getFileCount() { return fileCount; }
        /** @return last modified time of the basemap directory */
        public LocalDateTime getLastModified() { return lastModified; }
        /** @return absolute path to the basemap directory */
        public String getPath() { return path; }

        /** @return human-readable size string */
        public String getSizeHuman() {
            if (sizeBytes < 1024) return sizeBytes + " B";
            if (sizeBytes < 1024 * 1024) return String.format(Locale.US, "%.0f KB", sizeBytes / 1024.0);
            if (sizeBytes < 1024 * 1024 * 1024) return String.format(Locale.US, "%.1f MB", sizeBytes / (1024.0 * 1024.0));
            return String.format(Locale.US, "%.2f GB", sizeBytes / (1024.0 * 1024.0 * 1024.0));
        }

        @Override
        public String toString() {
            return "BasemapInfo{size=" + getSizeHuman() + ", files=" + fileCount + "}";
        }
    }

    /**
     * Get information about the installed basemap.
     *
     * @return basemap info, or null if not installed
     */
    public BasemapInfo getInstalledBasemapInfo() {
        Path basemapDir = getBasemapDirectory();
        if (!Files.isDirectory(basemapDir)) {
            return null;
        }
        try {
            long size = Files.walk(basemapDir)
                .filter(Files::isRegularFile)
                .mapToLong(p -> {
                    try { return Files.size(p); }
                    catch (IOException e) { return 0; }
                })
                .sum();

            int fileCount = (int) Files.walk(basemapDir)
                .filter(Files::isRegularFile)
                .count();

            LocalDateTime modified = LocalDateTime.ofInstant(
                Files.getLastModifiedTime(basemapDir).toInstant(),
                ZoneId.systemDefault());

            return new BasemapInfo(size, fileCount, modified, basemapDir.toAbsolutePath().toString());
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Check if a basemap is installed locally.
     *
     * @return true if the basemap directory exists and contains map data
     */
    public boolean isBasemapInstalled() {
        Path basemapDir = getBasemapDirectory();
        if (!Files.isDirectory(basemapDir)) {
            return false;
        }
        // Check for at least one .osmscout file or water.idx
        try {
            return Files.list(basemapDir).anyMatch(p ->
                p.toString().endsWith(".osmscout") ||
                p.toString().endsWith(".idx") ||
                Files.isDirectory(p));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Get the size of the installed basemap directory.
     *
     * @return size in bytes, or 0 if not installed
     */
    public long getInstalledBasemapSize() {
        Path basemapDir = getBasemapDirectory();
        if (!Files.isDirectory(basemapDir)) {
            return 0;
        }
        try {
            return Files.walk(basemapDir)
                .filter(Files::isRegularFile)
                .mapToLong(p -> {
                    try { return Files.size(p); }
                    catch (IOException e) { return 0; }
                })
                .sum();
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * Download a basemap archive and extract it to the basemap directory.
     * <p>
     * Downloads the tar.gz archive to a temporary file, extracts it to a
     * temporary directory, then atomically swaps the temp directory into
     * place (old directory kept as backup until the swap succeeds).
     *
     * @param archive  the basemap archive to download
     * @param listener callback for progress and completion events
     * @return a handle that can be used to cancel the download
     */
    public String downloadBasemap(BasemapArchive archive, MapDownloadListener listener) {
        String handle = java.util.UUID.randomUUID().toString();
        String mapName = "World Basemap";
        ActiveDownload ad = new ActiveDownload(handle, mapName, listener);
        activeDownloads.add(ad);

        Thread worker = new Thread(() -> {
            try {
                downloadAndExtract(ad, archive, listener);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                listener.onError(mapName, "Download cancelled");
            } catch (Exception e) {
                listener.onError(mapName, e.getMessage());
            } finally {
                activeDownloads.remove(ad);
            }
        }, "basemap-download");
        ad.setWorker(worker);
        worker.setDaemon(true);
        worker.start();

        return handle;
    }

    /**
     * Download tar.gz archive and extract to basemap directory.
     *
     * @param ad       active download record (cancellation + progress)
     * @param archive  the basemap archive to download
     * @param listener progress and completion callback
     * @throws Exception on any error during download or extraction
     */
    private void downloadAndExtract(ActiveDownload ad, BasemapArchive archive,
                                    MapDownloadListener listener) throws Exception {
        String mapName = ad.mapName;
        String url = getBasemapBaseUrl() + archive.getFileName();
        Path tempFile = Files.createTempFile("basemap-", ".tar.gz");
        Path tempDir = Files.createTempDirectory("basemap-extract-");
        Path finalDir = getBasemapDirectory();

        System.err.println("[BasemapManager] Starting download: " + url);
        System.err.println("[BasemapManager] Temp file: " + tempFile);
        System.err.println("[BasemapManager] Final dir: " + finalDir);

        try {
            // Download
            listener.onProgress(mapName, 0, archive.getSizeBytes());

            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);

                int status = conn.getResponseCode();
                if (status / 100 != 2) {
                    String msg = "Server returned HTTP " + status + " for " + url;
                    System.err.println("[BasemapManager] " + msg);
                    throw new IOException(msg);
                }

                long downloaded = 0;
                byte[] buffer = new byte[8192];
                try (InputStream in = conn.getInputStream();
                     OutputStream out = Files.newOutputStream(tempFile)) {
                    ad.setCurrentStream(in);
                    int read;
                    while ((read = in.read(buffer)) >= 0) {
                        if (ad.isCancelled()) {
                            System.err.println("[BasemapManager] Download cancelled");
                            throw new InterruptedException("Download cancelled");
                        }
                        out.write(buffer, 0, read);
                        downloaded += read;
                        listener.onProgress(mapName, downloaded, archive.getSizeBytes());
                    }
                } finally {
                    ad.setCurrentStream(null);
                }

                System.err.println("[BasemapManager] Download complete: " + downloaded + " bytes");
                listener.onProgress(mapName, archive.getSizeBytes(), archive.getSizeBytes());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }

            // Extract
            System.err.println("[BasemapManager] Extracting to: " + tempDir);
            extractTarGz(tempFile, tempDir);
            System.err.println("[BasemapManager] Extraction complete");

            // Flatten single-subdirectory archives (e.g., BaseMap-2026-02-23/)
            flattenSingleSubdir(tempDir);

            // Atomic swap: keep old basemap as backup until the new one is in place
            Path backupDir = null;
            if (Files.exists(finalDir)) {
                backupDir = finalDir.resolveSibling(BASEMAP_DIR_NAME + ".backup");
                deleteDirectory(backupDir);
                Files.move(finalDir, backupDir, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.createDirectories(finalDir.getParent());
                try {
                    Files.move(tempDir, finalDir, StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    // Fall back to recursive copy (cross-filesystem)
                    System.err.println("[BasemapManager] Atomic move not supported, using recursive copy: " + e.getMessage());
                    copyDirectory(tempDir, finalDir);
                    deleteDirectory(tempDir);
                }
            } catch (Exception e) {
                // Rollback: restore the previous basemap
                if (backupDir != null && Files.exists(backupDir)) {
                    deleteDirectory(finalDir);
                    Files.move(backupDir, finalDir, StandardCopyOption.REPLACE_EXISTING);
                }
                throw e;
            }
            // Success: drop the backup
            if (backupDir != null) {
                deleteDirectory(backupDir);
            }

            listener.onComplete(mapName, finalDir.toAbsolutePath().toString());
            System.err.println("[BasemapManager] Basemap installed at: " + finalDir);

        } catch (Exception e) {
            System.err.println("[BasemapManager] Download failed: " + e.getMessage());
            // Clean up on failure
            deleteDirectory(tempDir);
            try { Files.deleteIfExists(tempFile); } catch (Exception ignored) {}
            throw e;
        } finally {
            try { Files.deleteIfExists(tempFile); } catch (Exception ignored) {}
        }
    }

    /**
     * Extract a tar.gz file to a target directory.
     * Implements simple tar extraction using GZIPInputStream and manual tar header parsing.
     *
     * @param tarGzFile path to the tar.gz file
     * @param targetDir directory to extract into
     * @throws IOException on I/O error or invalid tar format
     */
    static void extractTarGz(Path tarGzFile, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);

        try (InputStream fis = Files.newInputStream(tarGzFile);
             java.util.zip.GZIPInputStream gzis = new java.util.zip.GZIPInputStream(fis)) {

            byte[] header = new byte[512];
            while (true) {
                int read = readFully(gzis, header, 0, 512);
                if (read < 512) break; // End of archive
                if (read == 0) break;

                // Check if we've hit the end-of-archive marker (two zero blocks)
                boolean allZero = true;
                for (int i = 0; i < 512; i++) {
                    if (header[i] != 0) { allZero = false; break; }
                }
                if (allZero) {
                    // Skip the second zero block
                    readFully(gzis, header, 0, 512);
                    break;
                }

                // Parse tar header
                // name: bytes 0-99
                // size: bytes 124-135 (octal)
                // typeflag: byte 156
                String name = parseTarName(header);
                long size = parseTarSize(header);
                int typeFlag = header[156] & 0xFF;

                // Sanitize path (prevent directory traversal)
                Path entryPath = targetDir.resolve(name).normalize();
                if (!entryPath.startsWith(targetDir)) {
                    throw new IOException("Invalid tar entry path: " + name);
                }

                if (typeFlag == '5' || name.endsWith("/")) {
                    // Directory
                    Files.createDirectories(entryPath);
                } else {
                    // File
                    Files.createDirectories(entryPath.getParent());
                    try (OutputStream out = Files.newOutputStream(entryPath)) {
                        long remaining = size;
                        byte[] dataBuf = new byte[8192];
                        while (remaining > 0) {
                            int toRead = (int) Math.min(dataBuf.length, remaining);
                            int dataRead = readFully(gzis, dataBuf, 0, toRead);
                            if (dataRead <= 0) break;
                            out.write(dataBuf, 0, dataRead);
                            remaining -= dataRead;
                        }
                    }
                }

                // Skip padding to 512-byte boundary
                long padding = (512 - (size % 512)) % 512;
                while (padding > 0) {
                    long skipped = gzis.skip(padding);
                    if (skipped <= 0) break;
                    padding -= skipped;
                }
            }
        }
    }

    /**
     * Parse file name from tar header (prefix + name fields).
     *
     * @param header 512-byte tar header block
     * @return file name extracted from the header
     */
    private static String parseTarName(byte[] header) {
        // Prefix (bytes 345-500) + name (bytes 0-99)
        StringBuilder sb = new StringBuilder();
        boolean hasPrefix = false;
        for (int i = 345; i < 500 && i < header.length; i++) {
            if (header[i] == 0) break;
            sb.append((char) (header[i] & 0xFF));
            hasPrefix = true;
        }
        if (hasPrefix) {
            sb.append('/');
        }
        for (int i = 0; i < 100; i++) {
            if (header[i] == 0) break;
            sb.append((char) (header[i] & 0xFF));
        }
        return sb.toString();
    }

    /**
     * Parse file size from tar header (bytes 124-135, octal).
     *
     * @param header 512-byte tar header block
     * @return file size in bytes
     */
    private static long parseTarSize(byte[] header) {
        // Size field: bytes 124-135, octal ASCII
        long size = 0;
        for (int i = 124; i < 136 && i < header.length; i++) {
            byte b = header[i];
            if (b == 0 || b == ' ') break;
            if (b >= '0' && b <= '7') {
                size = (size << 3) | (b - '0');
            }
        }
        return size;
    }

    /**
     * Read exactly len bytes from stream, or fewer at EOF.
     *
     * @param in     input stream to read from
     * @param buf    destination buffer
     * @param offset offset into buffer
     * @param len    number of bytes to read
     * @return number of bytes actually read, or 0 at EOF
     * @throws IOException on I/O error
     */
    private static int readFully(InputStream in, byte[] buf, int offset, int len) throws IOException {
        int total = 0;
        while (total < len) {
            int read = in.read(buf, offset + total, len - total);
            if (read < 0) break;
            total += read;
        }
        return total;
    }

    /**
     * Delete the installed basemap directory.
     *
     * @return true if deleted successfully, false if not installed or error
     */
    public boolean deleteBasemap() {
        Path basemapDir = getBasemapDirectory();
        if (!Files.exists(basemapDir)) {
            return false;
        }
        return deleteDirectory(basemapDir);
    }

    /**
     * Recursively delete a directory.
     *
     * @param dir directory to delete
     * @return true if deleted successfully, false on error
     */
    private static boolean deleteDirectory(Path dir) {
        try {
            Files.walk(dir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.deleteIfExists(p); }
                    catch (IOException ignored) {}
                });
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Recursively copy a directory tree.
     * Used as fallback when atomic move across filesystems is not supported.
     *
     * @param source source directory
     * @param target target directory (must not exist)
     * @throws IOException on I/O error
     */
    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        try (java.util.stream.Stream<Path> stream = Files.walk(source)) {
            stream.forEach(sourcePath -> {
                try {
                    Path relative = source.relativize(sourcePath);
                    Path targetPath = target.resolve(relative);
                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(targetPath);
                    } else {
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        }
    }

    /**
     * If the extracted directory contains exactly one subdirectory, move its
     * contents up one level. This handles archives that wrap content in a
     * top-level directory (e.g., {@code BaseMap-2026-02-23/}).
     *
     * @param dir extracted directory to flatten
     * @throws IOException on I/O error
     */
    private static void flattenSingleSubdir(Path dir) throws IOException {
        try (java.util.stream.Stream<Path> entries = Files.list(dir)) {
            List<Path> children = entries.collect(java.util.stream.Collectors.toList());
            if (children.size() == 1 && Files.isDirectory(children.get(0))) {
                Path subdir = children.get(0);
                System.err.println("[BasemapManager] Flattening single subdirectory: " + subdir.getFileName());
                try (java.util.stream.Stream<Path> subEntries = Files.list(subdir)) {
                    for (Path entry : (Iterable<Path>) subEntries::iterator) {
                        Path target = dir.resolve(entry.getFileName());
                        Files.move(entry, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                // Remove the now-empty subdirectory
                Files.delete(subdir);
            }
        }
    }

    /**
     * Cancel an active basemap download.
     *
     * @param handle the handle returned by {@link #downloadBasemap}
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
     * Check if a basemap update is available.
     *
     * @return true if a newer basemap exists on the server
     */
    public boolean isUpdateAvailable() {
        if (!isBasemapInstalled()) {
            return false;
        }
        List<BasemapArchive> available = fetchAvailableBasemaps();
        if (available.isEmpty()) {
            return false;
        }
        // Simple heuristic: if the newest server archive is newer than
        // the installed basemap's modification time, an update is available.
        BasemapArchive latest = available.get(0);
        if (latest.getLastModified() == null) {
            return false;
        }
        Path basemapDir = getBasemapDirectory();
        try {
            long installedTime = Files.getLastModifiedTime(basemapDir).toMillis();
            long serverTime = latest.getLastModified()
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
            return serverTime > installedTime;
        } catch (IOException e) {
            return false;
        }
    }

    /** Tracks an active basemap download. */
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
