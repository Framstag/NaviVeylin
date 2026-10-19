# Proposal

## Why

A provider download runs on its own daemon thread — `MapDownloadManager.downloadMap`, whose worker body
is `downloadMapInJava` — and that worker's failure handling catches **`Exception` only**
(`osmscout-client-java/src/main/java/com/framstag/libosmscout/client/MapDownloadManager.java:95`). An
`Error` therefore leaves the only place that could report it: `nativePrepareMapDirectory` is a native
call, so a missing symbol (`UnsatisfiedLinkError` — what the host test stub produces, `guidelines/Build.md`
§6) or a stack overflow dies as an uncaught throwable on a thread nobody awaits. The user's entry stays at
"Starting", the foreground service and its wake lock are never told the download ended, and the process's
uncaught-exception handler prints a stack trace that no case owns — which is exactly the aggregate-run
symptom tracker entry #117 (nvid 183) records.

Red test, measured on HEAD (`7ddf7ab`, `./gradlew :osmscout-client-java:test -PforceTests --tests
"com.framstag.libosmscout.client.MapDownloadManagerWorkerFailureTest"`,
`evidence/red-test-head.xml`: `tests=1 skipped=0 failures=1 errors=0`, timestamp `2026-10-19T06:22:24.266Z`):

- case `MapDownloadManagerWorkerFailureTest.aWorkerThatDiesOfAnErrorIsReportedThroughTheListener` fails at
  `MapDownloadManagerWorkerFailureTest.java:49` — "a worker that dies must report the failure instead of
  dying silently => expected: <true> but was: <false>" (the listener's `onError` latch never opens);
- the same XML's `system-err` carries the death itself:
  `Exception in thread "map-download-Berlin" java.lang.UnsatisfiedLinkError: 'boolean
  com.framstag.libosmscout.client.MapDownloadManager.nativePrepareMapDirectory(...)'` at
  `MapDownloadManager.java:145`, called from `MapDownloadManager.java:113`.

Owning requirement: `map-download-infrastructure` — "Error handling for download failures" ("The system
SHALL handle download errors gracefully and report them to the user"). Owning guideline section:
`guidelines/Design.md` §4 "Threading", whose MUST-clause already requires a confined native boundary for
any throwable, not only exceptions, and names `UnsatisfiedLinkError` as the reason.

## What Changes

- The provider-download worker reports **any** throwable through the listener it was given, not only an
  `Exception`: an `Error` becomes a download error the view model publishes ("Error: …") and a test's
  listener observes, instead of an uncaught throwable on a leaked daemon thread.
- The message a failure reports keeps its shape: an `Exception`'s own message when it has one, and the
  throwable's class plus message otherwise, so an `Error` names itself.
- No API change: `downloadMap` still returns its handle, the listener contract is unchanged, and nothing
  new is awaited on the caller's thread.

Additive. Rollback: revert the two files; nothing depends on the new behaviour.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `map-download-infrastructure`: "Error handling for download failures" gains one scenario — a worker
  that dies of an `Error` reports it through the listener. The requirement's text is unchanged.

## Impact

- `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/MapDownloadManager.java` — the
  worker's catch clauses in `downloadMap` (the local override module; the submodule copy
  `app/src/main/cpp/libosmscout/libosmscout-client-java/...` is not the compiled source and is left
  untouched, per `guidelines/Design.md` §5 "patch in one place, never both").
- `osmscout-client-java/src/test/java/com/framstag/libosmscout/client/MapDownloadManagerWorkerFailureTest.java`
  — new case.
- No consumer change: `MapManagerViewModel.downloadMap`'s listener already turns `onError` into the entry's
  error state and stops the foreground service.
- Spec `map-download-infrastructure` gains the scenario; no other spec's behaviour moves.
- `BasemapManager.downloadBasemap` keeps its own catch clauses — this change is bounded to the map
  download worker.
