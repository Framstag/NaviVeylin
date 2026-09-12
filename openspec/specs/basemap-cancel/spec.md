# basemap-cancel Specification

## Purpose
Guarantees that cancelling a basemap download always reports `"Download cancelled"` to the listener and never installs a basemap, no matter which internal abort path wins the race with the I/O thread.

## Requirements

### Requirement: Cancelled download reports "Download cancelled"

When a basemap download is cancelled, the `MapDownloadListener.onError` callback SHALL report `"Download cancelled"` even if the abort surfaced through a stream-close or I/O exception rather than the explicit cancel flag.

#### Scenario: Cancel lands while the download thread is blocked in a read

- **WHEN** `cancelDownload` closes the active HTTP stream while the download thread is blocked inside `read()`
- **THEN** the listener receives `onError(mapName, "Download cancelled")` and no basemap is installed

#### Scenario: Cancel lands between chunk reads

- **WHEN** the download thread observes the cancel flag between chunks
- **THEN** the listener receives `onError(mapName, "Download cancelled")` (unchanged behavior)

### Requirement: Late cancel does not install a completed download

A cancel that arrives after all bytes have been read but before extraction/install SHALL abort the operation; the downloaded content SHALL NOT be installed.

#### Scenario: Cancel lands right after the final chunk

- **WHEN** all payload bytes are read and a cancel is issued before extraction starts
- **THEN** the download reports `"Download cancelled"` and the basemap is not installed

### Requirement: Cancel aborts without leaving partial state

On any cancel path the temporary download and extract directories SHALL be cleaned up and the download SHALL NOT leave the basemap directory behind.

#### Scenario: Cancelled download leaves no basemap

- **WHEN** a cancelled download aborts
- **THEN** `isBasemapInstalled()` returns false and the basemap directory does not exist
