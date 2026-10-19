# Design — fix-download-worker-error-reporting

## Root cause

`MapDownloadManager.downloadMap` starts one daemon thread per download and that thread's body catches
`Exception` only:

```java
// osmscout-client-java/.../MapDownloadManager.java (HEAD, 7ddf7ab)
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    listener.onError(entry.getName(), "Download cancelled");
} catch (Exception e) {
    listener.onError(entry.getName(), e.getMessage());
} finally {
    activeDownloads.remove(ad);
}
```

The first thing a real map download does that can fail is a native call (`nativePrepareMapDirectory`) — a
non-map directory entry is refused earlier, with an `IllegalArgumentException`, which is what the guard
case exercises — and a linker or class-initialisation failure is an `Error`, not an `Exception`. So the
one place that could turn a dying worker into a reported failure is bypassed: the throwable leaves the
thread, reaches the process's uncaught-exception handler, and is printed. Nothing awaits the thread (the
handle `downloadMap` returns is a UUID string; `ActiveDownload.worker` is private), so no case can observe
it either. That is the whole
symptom tracker entry #117 records: a `map-download-*` thread dying with a printed throwable and no failing
case. Red evidence: `evidence/red-test-head.xml` (tests=2 failures=1, system-err carries
`Exception in thread "map-download-Berlin" java.lang.UnsatisfiedLinkError` at
`MapDownloadManager.java:145`).

`guidelines/Design.md` §4 already states the rule this violates for the navigation engine — "the engine's
native boundary is confined for **any throwable, not only exceptions** … an `Exception` catch lets it
escape" — so this is the same rule applied at the download worker boundary.

## The single fix

Report every throwable through the listener the worker was started with, at the worker's top level:

```java
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    listener.onError(entry.getName(), "Download cancelled");
} catch (Throwable e) {
    listener.onError(entry.getName(), failureMessage(e));
} finally {
    activeDownloads.remove(ad);
}
```

with `failureMessage(Throwable)` returning an `Exception`'s own `getMessage()` when it has one (so its message
is byte-identical to today's) and `toString()` for an `Error` and for a message-less `Exception` (so an `Error`
names its class — `UnsatisfiedLinkError`'s own message is only the native signature):

```java
private static String failureMessage(Throwable failure) {
    if (failure instanceof Exception) {
        String message = failure.getMessage();
        return message != null ? message : failure.toString();
    }
    return failure.toString();
}
```

The listener contract is already the completion seam: `MapManagerViewModel.downloadMap` turns `onError`
into the entry's error state (`publishError`), so the user sees the failure. It does not end the foreground
service on this path: `publishError` keeps `DownloadState.Error` in the active set
(`MapManagerViewModel.kt:761-764`), so `updateActiveDownloads` calls `MapDownloadService.update` rather than
`stop` (`MapManagerViewModel.kt:773-779`). No API, signature or threading change; the worker stays a
daemon thread and the caller still never waits for it.

## Blast radius

- One production file, one method's catch clauses, plus one small private helper:
  `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/MapDownloadManager.java`.
- The submodule copy (`app/src/main/cpp/libosmscout/libosmscout-client-java/.../MapDownloadManager.java`) is
  **not** compiled into the app — the `:osmscout-client-java` module overrides it — and is left untouched
  (`guidelines/Design.md` §5: patch in one place, never both).
- `BasemapManager.downloadBasemap` has its own worker and its own catch clauses; it is out of scope, and its
  tests are unaffected.
- Consumers: `MapManagerViewModel`'s listener (`publishError`), which only gains cases it previously never
  saw; a reported failure takes the same branch as any other — the entry keeps its error state in the active
  set, so the service is updated rather than stopped.
- Behaviour on the `Exception` path is unchanged **when the `Exception` carries a message** (same message
  string), so no existing case's expectation moves. A message-less `Exception` reported `null` before and now
  reports `toString()`.

## Rollback

Revert the two changed files. Nothing consumes the new behaviour and no spec text is deleted, so the revert
leaves the tree at HEAD's behaviour.

## Alternatives the evidence eliminated

- **Join the worker / expose the thread to the caller** (the tracker entry's own first suggestion): it makes
  the death observable to a test that remembers to join, but leaves the production path silent — the user's
  entry still stays at "Starting" and the foreground service is still never told. The listener is already
  the completion seam, so no new seam is needed.
- **Rethrow the `Error` after reporting** — rejected: the throwable would escape again and the printed
  uncaught trace (the symptom) returns. The worker is a top-level boundary; it reports and ends.
- **Catch only `UnsatisfiedLinkError`** — rejected: it names one throwable class of the ones that can
  escape (a `StackOverflowError`, an `ExceptionInInitializerError`, an `OutOfMemoryError` would still leak),
  and `guidelines/Design.md` §4 asks for any throwable.
- **Unbounded crash-handler chain growth** — the candidate that would explain a `StackOverflowError`
  *inside* the handler: `DiagnosticsLog.installCrashHandler` composes onto whatever handler is currently
  installed, and `NaviVeylinApp.onCreate` installs one per Robolectric test, so a suite builds a chain. It is
  not what the evidence supports: in the measurement, a chain whose link does a little work before
  delegating overflowed 5 of 5 at 4 000 and 8 000 links, 3 of 5 at 16 000, 5 of 5 at 32 000, and 0 of 5 at
  1 000 and 2 000 (`evidence/handler-chain-probe.txt`); the 16 000 row is not monotonic against 4 000, so the
  probe measures no single threshold. A `:app` fork installs ≈1 000 links (2 059 cases over 2 forks). It
  also cannot explain why the print appeared in an aggregate run and not in a per-module run: both install
  the same number of handlers. Recorded, not relied on.

## What this change does not claim

The throwable class in the aggregate run was a `StackOverflowError`; the tree reproduces the *mechanism* with
`UnsatisfiedLinkError`, and the origin of that particular overflow (the download loop, Robolectric's shadow
stack, or the handler) is not attributable from the tree — the tracker entry says so itself. What the fix
removes is the escape: whatever an `Error` on the download worker is, it is now reported through the
listener instead of reaching the process-wide handler unowned.

## Verification

- Red case (`MapDownloadManagerWorkerFailureTest.aWorkerThatDiesOfAnErrorIsReportedThroughTheListener`) fails
  on HEAD: `tests=2 failures=1` (2026-10-19T06:24:08.877Z) — the class runs both cases, the guard case green
  and this one red — and passes after the fix.
- The `Exception`-message case (`aWorkerThatDiesOfAnExceptionKeepsItsMessage`) is green before and after.
- The gate: `./gradlew test -PforceTests --rerun-tasks` — after the fix, 5 674 cases, 0 failures, 0 errors,
  and no `map-download-`/`StackOverflow` text in any module's XML (`evidence/gate-after-fix.txt`, tallies
  summed from the live `build/test-results`). The baseline run's log carries no tallies (`gate-baseline.log`:
  `BUILD SUCCESSFUL in 5m 16s`, 189 tasks executed, exit 0), and its XMLs were overwritten by the gate run, so
  no baseline count is claimed.
