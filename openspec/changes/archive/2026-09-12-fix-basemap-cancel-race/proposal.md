## Why

CI's `:osmscout-client-java:test` fails 1 of 22 tests: `BasemapManagerTest.testCancelDownloadCleansUp()` asserts `onError("Download cancelled")` after `cancelDownload`, but `BasemapManager.cancel()` closes the active HTTP stream to abort a blocked `read()`. When the cancel lands while the read is blocked (common: the test server sleeps between chunks), the download thread surfaces `IOException("Stream closed")` instead of the cancellation path, so the listener gets the wrong message and the assertion fails. Locally the race rarely wins (15/15 passes); CI scheduling broadens the window.

## What Changes

- `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/BasemapManager.java` (a local-override file — no submodule patch):
  - In the download worker's generic exception catch, report `"Download cancelled"` whenever the active download has been cancelled, regardless of which exception the aborted I/O produced.
  - After the read loop ends, before extraction, abort with `InterruptedException("Download cancelled")` if a cancel landed after the last chunk — so a late cancel never installs a "complete" basemap.
- No test change: `testCancelDownloadCleansUp` already encodes the correct contract.

## Capabilities

### New Capabilities

- `basemap-cancel`: Basemap download cancellation — a cancelled download always reports `"Download cancelled"` to the listener and never installs a basemap, regardless of which abort path (flag between reads, interrupted read, or stream close) wins the race.

### Modified Capabilities

- None.

## Impact

- `osmscout-client-java/src/main/java/.../BasemapManager.java` — two small edits in the download worker (catch-all error reporting + post-loop cancel check).
- OpenSpec change artifacts.
- CI: unblocks the last failing unit test; `:osmscout-client-java:test` then runs green in the build.
- Rollback: revert the commit — the race returns and the test becomes flaky again (as today).
