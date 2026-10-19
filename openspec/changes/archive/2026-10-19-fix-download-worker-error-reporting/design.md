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

The first thing the worker does that can fail is a native call (`nativePrepareMapDirectory`), and a linker
or class-initialisation failure is an `Error`, not an `Exception`. So the one place that could turn a dying
worker into a reported failure is bypassed: the throwable leaves the thread, reaches the process's
uncaught-exception handler, and is printed. Nothing awaits the thread (the handle `downloadMap` returns is
a UUID string; `ActiveDownload.worker` is private), so no case can observe it either. That is the whole
symptom tracker entry #117 records: a `map-download-*` thread dying with a printed throwable and no failing
case. Red evidence: `evidence/red-test-head.xml` (tests=1 failures=1, system-err carries
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

with `failureMessage(Throwable)` returning `getMessage()` when it is non-null (so an `Exception`'s message is
byte-identical to today's) and `toString()` otherwise (so an `Error` names its class):

```java
private static String failureMessage(Throwable failure) {
    String message = failure.getMessage();
    return message != null ? message : failure.toString();
}
```

The listener contract is already the completion seam: `MapManagerViewModel.downloadMap` turns `onError`
into the entry's error state and stops the foreground service, so the user sees the failure and the wake
lock is released. No API, signature or threading change; the worker stays a daemon thread and the caller
still never waits for it.

## Blast radius

- One production file, one method's catch clauses, plus one small private helper:
  `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/MapDownloadManager.java`.
- The submodule copy (`app/src/main/cpp/libosmscout/libosmscout-client-java/.../MapDownloadManager.java`) is
  **not** compiled into the app — the `:osmscout-client-java` module overrides it — and is left untouched
  (`guidelines/Design.md` §5: patch in one place, never both).
- `BasemapManager.downloadBasemap` has its own worker and its own catch clauses; it is out of scope, and its
  tests are unaffected.
- Consumers: `MapManagerViewModel`'s listener (`publishError`), which only gains cases it previously never
  saw. `MapDownloadService` stops on the same path as any other error.
- Behaviour on the `Exception` path is unchanged (same message string), so no existing case's expectation
  moves.

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
  not what the evidence supports: a chain of that shape overflows the default stack only at ≳4 000 links
  (measured 5 of 5 at 4 000 with a link body that does a little work before delegating, 0 of 5 at 1 000 and
  2 000 — `evidence/handler-chain-probe.txt`), and a `:app` fork installs ≈1 000 (2 059 cases over
  2 forks). It also cannot explain why the print appeared in an aggregate run and not in a per-module run:
  both install the same number of handlers. Recorded, not relied on.

## What this change does not claim

The throwable class in the aggregate run was a `StackOverflowError`; the tree reproduces the *mechanism* with
`UnsatisfiedLinkError`, and the origin of that particular overflow (the download loop, Robolectric's shadow
stack, or the handler) is not attributable from the tree — the tracker entry says so itself. What the fix
removes is the escape: whatever an `Error` on the download worker is, it is now reported through the
listener instead of reaching the process-wide handler unowned.

## Verification

- Red case (`MapDownloadManagerWorkerFailureTest.aWorkerThatDiesOfAnErrorIsReportedThroughTheListener`) fails
  on HEAD: `tests=1 failures=1` (2026-10-19T06:22:24.266Z) and passes after the fix.
- The `Exception`-message case (`aWorkerThatDiesOfAnExceptionKeepsItsMessage`) is green before and after.
- The gate: `./gradlew test -PforceTests --rerun-tasks` — HEAD baseline green (5 672 cases, 0 failures,
  0 errors, no `map-download-`/`StackOverflow` text in any module's XML).
