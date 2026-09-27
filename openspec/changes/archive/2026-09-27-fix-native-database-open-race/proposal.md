# Proposal

## Why

Two app paths open map databases concurrently in one process — the car session warmup
(`AutoServiceModule.provideAutoClientProvider`) and the phone map path
(`MapCanvasViewModel.initMap`, plus a map scan or a download completion) — and the JNI bridge
mutates its database-path list without any lock:

```
OSMScoutClient.cpp:691-697   find/push_back on data->knownPaths      (no mutex)
ClientData::knownPaths :439  has no mutex, unlike routingMutex :444,
                             routeDescriptionMutex :447, gpsMarkerMutex :453,
                             adminRegionMutex :462 right beside it
OSMScoutClient.java:41       bare `public native boolean openDatabase` — no Java-side lock
```

The same vector is then handed to `DBThread::OnDatabaseListChanged`, whose lambda **copies it on
the calling thread while another opener may be reallocating it** (`DBThread.cpp:173-184`). Two
concurrent openers therefore race a reallocation against a read: use-after-free, heap corruption,
native SIGSEGV. Under Android Auto projection the phone UI and the car session share one process,
which is exactly when both openers run.

Second-order cost in the same lines: every `openDatabase` call triggers
`OnDatabaseListChanged`, which closes and reopens **all** databases under the DB write lock
(`DBThread.cpp:179-184`). The warmup's per-directory loop is therefore O(N²) database opens, and
every render waits on the read lock while it runs — a multi-second stall at session start.

Per `guidelines/Build.md` §10 and the host-crash mechanism the project already established, an app
process that dies while a car host is bound takes the host's renderer service down with it. This
bug is also a standing counterexample to `auto-startup-hardening` ("Session startup never crashes
the process"), even though that requirement itself does not change.

## What Changes

- **Native client (submodule patch):** guard the database-path list with its own mutex; hand the
  DB thread a value copy taken under that lock; add a batch entry point
  `openDatabases(String[] paths)` that registers the whole set and triggers **one**
  `OnDatabaseListChanged` for it. `openDatabase(String)` stays, now lock-safe.
- **Java/JNI side:** declare the new native in the `:osmscout-client-java` override (and in the
  submodule's Java copy for upstream parity, since the override replaces that file at compile
  time).
- **Callers:** the car warmup (`AutoServiceModule`) and the phone maps-directory loop
  (`MapCanvasViewModel`) hand over the directory list in one batch call instead of looping over
  single opens. The single-database open for the selected map path is unchanged.
- **Tests:** a native test for the batch and concurrent semantics (registered in `Tests/CMakeLists.txt`
  and `Tests/meson.build`), plus unit tests for both callers' batching through the existing fakes.
- No manifest, resource, Gradle-dependency or user-visible behaviour change. Additive API.

## Capabilities

### New Capabilities

- `native-database-open`: how the app registers and opens map databases through the JNI bridge —
  concurrent openers cannot corrupt the client's database set, one call can register a whole set of
  directories, and a set change reaches the render path as one coordinated step instead of one
  close/reopen cycle per directory.

### Modified Capabilities

None. The new contract is additive: no existing requirement's text changes. The capability the bug
contradicts is `auto-startup-hardening` (Requirement "Session startup never crashes the process"),
which the fix makes true for this path without editing it; `osmscout-jni` ("JNI bridge to native
library") already covers exposing bridge APIs and is not re-stated here. If the specs phase decides
the car-specific scenario belongs in `auto-startup-hardening` as an added scenario, that is a
deliberate delta to add then, not a requirement change made now.

## Impact

**Native (submodule patch — minimal, upstreamable; `naviveylin-local`, gitlink bumped after):**

- `app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp` — `knownPathsMutex`
  in `ClientData`, lock-protected registration, value copy to the DB thread, new
  `Java_..._openDatabases` entry point
- `app/src/main/cpp/libosmscout/libosmscout-client-java/java/com/framstag/libosmscout/client/OSMScoutClient.java`
  — `openDatabases` declaration (upstream parity)
- `app/src/main/cpp/libosmscout/Tests/src/DatabaseOpenTest.cpp` (+ `Tests/CMakeLists.txt`,
  `Tests/meson.build`) — batch semantics, concurrent-opener stress
- No change to `DBThread`/`DBInstance`/`libosmscout` core: the batch call reuses
  `OnDatabaseListChanged` once, the close/reopen logic itself is untouched

**Java bridge module (`:osmscout-client-java`, local override — the file that actually compiles):**

- `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java` —
  `openDatabases` native declaration

**App module (`:app`, both distribution flavors — no flavor-specific code):**

- `app/src/main/java/com/naviveylin/di/AutoServiceModule.kt` — warmup loop → one batch call
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` (`initMap` maps-directory loop) →
  one batch call
- `app/src/test/java/com/framstag/libosmscout/client/FakeOSMScoutClient.kt` — override the new
  native for JVM tests
- New tests for both callers next to their existing test classes

**Specs / docs:**

- `openspec/specs/native-database-open/spec.md` (new, from this change); no existing spec file is edited
- `guidelines/Design.md` — native boundary / threading rules referenced by the fix; updated only if
  the design phase finds the concurrency rule is not already stated there
- `AGENTS.md` — the JNI Java-side override list is unchanged (the file is still overridden, only a
  declaration is added inside it)
- No `UI.md` or `MapRendering.md` impact (no rendering, no visible behaviour)

**Scope:** all surfaces, not Android-Auto-specific. The race is in the shared JNI bridge, and the
phone path is one of the two openers; the fix is in the native client and both callers.

**Additive vs breaking:** additive. `openDatabase(String)` keeps its signature and semantics (now
lock-safe); `openDatabases(String[])` is new. No data migration, no on-device state change.

**Rollback path:** revert the app-side commits (callers back to the per-directory loop) and bump the
submodule gitlink back; the submodule commit is additive, so no submodule history rewrite is needed.
After a revert the race is open again, so a revert must be paired with the rollback of the
follow-up crash report.

**Verification (details in design.md):**

- Native: batch call → exactly one close/reopen cycle for the whole set; two threads opening in a
  loop → no memory error and a consistent set (stress test run under sanitizers where the host
  build allows it); single-open regression test unchanged
- Kotlin: the car warmup issues one batch call with every installed database directory; the phone
  path issues one batch call; a failing directory does not drop the others
- Build: `:osmscout-client-java:jar`, `:app:assembleMobileDebug` + `:app:assembleAutomotiveDebug`
  (all three ABIs for the native side), existing suites still green
- On device: warmup render stall (the O(N²) window) disappears from the start of an Android Auto
  session; no native tombstone when the phone UI opens while the car session warms up
