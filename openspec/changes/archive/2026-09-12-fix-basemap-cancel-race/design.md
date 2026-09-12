## Context

See proposal.md - Why. `downloadBasemap` spawns a worker thread; the loop checks `ad.isCancelled()` between chunk reads and throws `InterruptedException("Download cancelled")`; `cancel()` sets the flag, interrupts the worker, and closes the active stream. When the cancel lands while `read()` is blocked, the close produces `IOException` which the worker's generic catch reports via `e.getMessage()` — not `"Download cancelled"`. CI hit this once; local 15/15 passed.

## Goals / Non-Goals

**Goals:**
- Cancel intent always wins: any abort path that follows a `cancelDownload` call reports `"Download cancelled"`.
- A late cancel (bytes complete but extraction not started) still aborts without installing.
- No behavior change for non-cancelled downloads (progress/error/complete semantics identical).

**Non-Goals:**
- No change to the cancel API, the listener interface, or the test's expectations.
- No change to the submodule (BasemapManager is one of the 6 local-override files patched in `:osmscout-client-java` only).

## Decisions

### Decision 1: Cancel flag wins in the generic error catch

In the worker's `catch (Exception e)` branch, prefer `"Download cancelled"` when `ad.isCancelled()` is true; otherwise keep `e.getMessage()` (preserves real error messages for non-cancel failures, e.g. HTTP status, network errors).

**Alternatives considered:**

1. **Match on exception type** (`SocketException`/`IOException` with "closed" in the message). Rejected — brittle string matching; other I/O errors could be misreported as cancelled.
2. **Track a dedicated "cancelled" exception type thrown by `cancel()`**. Rejected — `cancel()` runs on the caller thread; it cannot throw into the worker. The flag + post-abort check is simpler.
3. **Re-throw from `read()` via a wrapper stream that checks the flag**. Rejected — more machinery than needed; the flag check in the catch covers all abort paths uniformly.

### Decision 2: Post-loop cancel check before extraction

After the read loop ends normally (EOF), check `ad.isCancelled()` once more and abort with `InterruptedException("Download cancelled")` if set. This closes the window where a cancel lands between the final chunk and the start of extraction, so a "fully downloaded" payload is never installed after a cancel.

**Alternative:** leave extraction to run and ignore late cancels. Rejected — violates the spec scenario "Late cancel does not install a completed download"; a cancelled download must not install.

## Risks / Trade-offs

- **Cancel during extraction** — `cancel()`'s interrupt cannot abort tar extraction; a cancel issued mid-extraction may still install. → Mitigation: extraction of a basemap is bounded (~seconds); the post-loop check plus stream-close aborts cover the reported race windows. If it ever matters, a flag check before the atomic swap is the follow-up point.
- **Double error reporting** — `onError` may fire for a cancel, and the flag is checked again later. → Mitigation: `activeDownloads.remove(ad)` in `finally` and the worker exits after one `onError`; no second callback is possible.

## Migration Plan

1. Apply the two edits to `BasemapManager.java` (generic-catch cancel check; post-loop check).
2. Verify locally: run `:osmscout-client-java:test` repeatedly (20×) plus the full unit suite.
3. Push; CI run must pass `:osmscout-client-java:test` (22/22) and the whole build green.
4. Rollback: revert the commit.

## Open Questions

None.
