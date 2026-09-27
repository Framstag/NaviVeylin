# Design

## Context

See `proposal.md` — Why for the defect and its impact. Current state that shapes the approach:

- `ClientData::knownPaths` (`libosmscout-client-java/src/OSMScoutClient.cpp:439`) is a plain
  `std::vector<std::filesystem::path>` with no mutex, while the four locks directly beside it
  (`routingMutex:444`, `routeDescriptionMutex:447`, `gpsMarkerMutex:453`, `adminRegionMutex:462`)
  guard their own state. `getClientData` (`:481-486`) does **not** take `initMutex` — only
  `OSMScoutClientBuilder.build()` (`:501`) and `close()` (`:1017`) do — so the find/push at
  `:691-697` is genuinely unguarded, and the vector is passed by reference straight into
  `DBThread::OnDatabaseListChanged` (`:697`).
- `DBThread::OnDatabaseListChanged` (`libosmscout-client/src/osmscoutclient/DBThread.cpp:173-184`)
  copies the vector in its lambda capture **on the calling thread**, then takes the DB write lock
  and closes + clears + reopens the entire list. Two consequences: a torn read against a concurrent
  reallocation, and one full close/reopen cycle per caller.
- Two in-process openers: `AutoServiceModule.provideAutoClientProvider` → `openMapDatabases`
  (loop over `InstalledMaps.findDatabaseDirectories`, on `Dispatchers.Default`) and
  `MapCanvasViewModel.initMap` (`:1744` single selected path, `:1780-1790` loop over the installed
  set). Under Android Auto projection both run in one process.
- Precedent for the shape: `fix-favorite-store-write-race` moved the shared mutable state into a
  JNI-free `osmscout::FavoriteStore` in `libosmscout-client` behind one mutex and tested it
  natively without a `JNIEnv`.
- Constraints: `guidelines/Design.md` §5 — a defect inside the library is fixed as a minimal,
  upstreamable submodule patch, mirroring upstream APIs, with Android-specific deviations kept in
  the bridge override module; §4 — no native call on the main thread; §11/§12 — tests and
  revert-checks. `AGENTS.md` — C++ side belongs in the submodule, the Java side in the
  `:osmscout-client-java` override, never both for one file.

## Goals / Non-Goals

**Goals**

- Concurrent database registration from app threads cannot corrupt the client's path list or kill
  the process, and cannot produce a database set that a render or a query observes half-applied.
- Registering the installed set is one coordinated database-set change, so startup registration is
  no longer O(N²) in the number of installed maps.
- The fix is unit-testable without a `JNIEnv` and is upstreamable to `Framstag/libosmscout`.

**Non-Goals**

- No change to `DBThread`/`DBInstance` close/reopen semantics, the tile cache or the render path.
- Not repairing anything else named in `TODO.md` §48: the entry's defect **is** the path list and the
  reopen cost (the neighbouring `routingMutex`/`routeDescriptionMutex`/`gpsMarkerMutex`/
  `adminRegionMutex` are existing locks, not missing ones), so this change closes §48 completely and
  leaves no part of it open.
- No map-removal or set-replacement semantics: registration stays add-only, as today.
- No new dependency, no Android-specific code in the submodule, no manifest/resource change.
- Not wiring ASan/TSan into the submodule build (no sanitizer configuration exists today); the
  concurrency test is behavioural (see Verification).

## Decisions

### D1 — A JNI-free registry class owns the path list (chosen)

Extract the list into `osmscout::DatabasePathRegistry`
(`libosmscout-client/include/osmscoutclient/DatabasePathRegistry.h`,
`libosmscout-client/src/osmscoutclient/DatabasePathRegistry.cpp`) with `Register(path)`,
`RegisterAll(paths)` and `Snapshot()`, all serialised by one internal mutex. `ClientData` holds one
instance instead of the raw vector; the JNI entry points call it and pass the returned snapshot (a
value copy) to `OnDatabaseListChanged`.

Alternatives considered:

1. **A mutex inline in the JNI translation unit** (lock around `:691-697`, copy before `:697`).
   Minimal diff, but not testable: exercising it needs a `JNIEnv`, so the race and the batch
   semantics could only be asserted on a device, and the 7947-line single TU gains platform-free
   logic. Rejected.
2. **Java-side `synchronized` in the override** (`osmscout-client-java/.../OSMScoutClient.java`).
   Protects callers of one `OSMScoutClient` instance only, leaves the native copy torn (the race is
   inside `OnDatabaseListChanged`'s capture), and duplicates a threading rule the bridge cannot
   enforce for other callers (JavaScout, tests, future call sites). Rejected.
3. **Copy-on-write snapshot with an atomic pointer swap.** Readers become lock-free, but
   read-modify-write (find + push) still needs mutual exclusion, so a mutex remains; it adds an
   allocation per write and a shared-ownership type for no measurable gain at 1-3 writers.
   Rejected.
4. **Reuse `initMutex`.** It already serialises `build()`/`close()`, so registration would contend
   with client construction and teardown, and a registration could hold it while the DB thread is
   being torn down. Rejected.

Rationale for the chosen option: it matches the existing per-state-lock pattern in `ClientData`, it
keeps the platform-free logic out of the JNI glue, it is unit-testable on the host, and it follows
the favorite-store precedent (which the project already accepted as an upstreamable patch shape).

### D2 — Batch registration is add-only and notifies once (chosen)

`RegisterAll(paths)` adds every path not yet present (deduplicating against the current set and
within the batch) and returns one snapshot. The JNI call issues exactly one
`OnDatabaseListChanged(snapshot)` for the whole batch, so the DB thread's database set — the set a
render, search or routing call observes — changes once. A render in flight sees either the set
before the batch or the set after it, never a partially updated one.

Alternatives considered:

1. **Replace-set semantics** (`SetDatabases(paths)`): clears the list first. Rejected — the current
   behavior is add-only, and a caller that discovers a subset (the phone excludes the selected map
   path; a future scan may see fewer directories) would silently drop the other databases.
2. **Keep per-directory opens behind begin/end "batch" markers** (defer the notification until
   `endBatch()`). Fewer API changes, but the path list and the DB thread's set are then explicitly
   inconsistent between begin and end, and a render during that window observes a partial set —
   exactly what the spec forbids. Rejected.
3. **Serialize the callers instead of adding an API** (a Kotlin-side mutex around both loops).
   Rejected: it hides the defect instead of fixing it (other callers — JavaScout, tests, a future map
   scan — would still race), and it leaves the O(N²) reopen storm untouched.

### D3 — The batch call reports per-directory disposition (chosen)

`boolean[] openDatabases(String[] paths)`, index-aligned with the input: `true` when the directory is
in the registered set after the call (newly added or already present), `false` when the client is
unusable (`ClientData`/`DBThread` null, i.e. the same condition under which `openDatabase` returns
`JNI_FALSE` today). Empty array for a null/empty input.

Alternatives considered:

1. **A single `boolean`** — loses the per-directory outcome, which the car warmup currently logs
   (`openDatabase($dir) -> $success`) and which the startup diagnostics (`Diag/WARMUP`) rely on.
   Rejected.
2. **An `int` count of registered directories** — cheaper to log, but it cannot name the directory
   that failed, and a sentinel for "client unusable" is a magic value. Rejected.

The allocation is one `jbooleanArray` per batch call (a handful of calls per process), so the array
shape costs nothing measurable.

### D4 — Callers hand over their list once; single open stays

- `app/src/main/java/com/naviveylin/di/AutoServiceModule.kt` (`openMapDatabases`): replace the
  per-directory loop with one `client.openDatabases(databases.toTypedArray())`, keep the existing
  `Installed map databases: N under …` diagnostic line and log the registered count (and any `false`
  entry) from the returned flags. The work stays on `Dispatchers.Default`.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` (`initMap`): keep the single
  `client.openDatabase(mapPath)` (that call is the map-selection contract of the `map-render` spec)
  and replace the `:1780-1790` loop with one batch call for `installed.filter { it != mapPath }`,
  preserving today's effective behavior exactly.
- `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java`: add
  `public native boolean[] openDatabases(String[] paths);` — the override is the file that compiles
  for the app, so the declaration must live here.
- Submodule `libosmscout-client-java/java/com/framstag/libosmscout/client/OSMScoutClient.java`: the
  same declaration, so the upstream copy and the override stay in step (the override list in
  `AGENTS.md` is unchanged — the file is still overridden).
- Test fakes: `app/src/test/java/com/framstag/libosmscout/client/FakeOSMScoutClient.kt` gains an
  override that records batch paths and returns a configurable array; any `:auto` fake that can reach
  the warmup path gains the same override (a fake that inherits the native would throw
  `UnsatisfiedLinkError` if reached).

Alternative considered: **only the car path batches, the phone keeps its loop.** Rejected — the
phone loop is the second opener in the race and produces the same reopen storm.

### D5 — Threading model and lifecycle

- Registration is called from the app's background dispatchers (`Dispatchers.Default` in the car
  warmup; the phone's init coroutine). No path touches the main thread, per `guidelines/Design.md` §4.
- The registry mutex is held only for the in-memory list operation (find + insert + copy), i.e.
  microseconds; the notification is **issued after the lock is released**, with the snapshot by
  value, so the app-facing lock is never held while the DB thread does its reopen work. The mutex
  therefore has no lock-order relationship with the DB latch's write lock and cannot deadlock.
- No new thread, no dispatcher change, no new scope: the DB thread performs the set change
  asynchronously exactly as it does today.
- Lifecycle: the registry lives inside `ClientData`, which is created in `build()` and destroyed in
  `close()` — both still guarded by `initMutex`. Each JNI entry point keeps the existing
  null-checks (`data == nullptr || data->dbThread == nullptr`) and returns the documented failure
  result instead of touching the registry.

### D6 — One testable seam, no JNI test harness

The registry is the seam: the native test drives it directly (as `Tests/src/FavoriteStoreTest.cpp`
drives `osmscout::FavoriteStore`), so the concurrency and batch behavior are asserted without a
`JNIEnv`. The JNI entry point is then thin enough to be covered by the Kotlin caller tests plus the
on-device run.

## Risks / Trade-offs

- **A torn read remains possible for other state** touched from the same two app paths (`settings`,
  `fontSizeMm`, the basemap pointer, the tile-cache size). → Out of scope by design; they have no
  mutex today either, but they are not what §48 reported and no concurrent-write defect is observed
  for them (the tile-cache size is written once per phone map start). If one of them is implicated in
  a crash, it needs its own change.
- **Add-only registration keeps a deleted map's path registered** (its DB open then fails on the DB
  thread and is logged). → Unchanged from today's behavior; a future remove/replace API is a separate
  change (see Open Questions).
- **One reopen instead of N changes the timing of the DB set update** relative to a render that was
  already queued. → The render lock behavior is unchanged (one write-lock section in either case);
  verify on device that all regions still render and cross-database search still resolves after a
  batch (the existing multi-map scenarios).
- **The 7947-line single translation unit invites merge conflicts.** → Keep the JNI diff to the new
  entry point plus the field swap; all logic goes into the new class.
- **A behavioural concurrency test can pass on a machine that happens not to interleave.** →
  Revert-check: temporarily remove the mutex from the registry and the same test must fail (or the
  sanitizer-free stress must at least corrupt the set); document the revert-check result in the task
  evidence rather than claiming sanitizer coverage.
- **Upstream parity**: the submodule carries both the C++ and the Java declaration, while the app
  compiles the override. → Keep the two Java declarations textually identical, and re-check the
  override list in `AGENTS.md` after the submodule sync.

## Migration Plan

1. Submodule commit on `naviveylin-local`: registry class + JNI entry point + Java declaration +
   native test registration (CMake and meson).
2. `:osmscout-client-java` override declaration; `:app` callers switched to the batch call.
3. Bump the submodule gitlink in the main repo in the same change; build all three ABIs.
4. Rollback: revert the app-side commit (callers back to the per-directory loop) and the gitlink
   bump. The submodule commit is additive and can stay. After a rollback the race is open again, so a
   rollback is only acceptable together with reverting the dependent verification.

## Verification

- **Native (`Tests/src/DatabaseOpenTest.cpp`, registered in `Tests/CMakeLists.txt` and
  `Tests/meson.build`)**: register-all → snapshot contains every path once; batch containing an
  already-registered path → no duplicate; single `Register` and a one-element `RegisterAll` leave the
  same set; N threads × M iterations of `Register`/`RegisterAll`/`Snapshot` → final set complete and
  every snapshot internally consistent (no duplicate, no missing path); revert-check by removing the
  mutex (must fail).
- **Kotlin**: `FakeOSMScoutClient` records batch calls; new tests assert the car warmup issues one
  batch call with every discovered directory (and none when the maps dir is absent) and the phone
  path issues one batch call with every installed directory except the selected map path; a `false`
  entry is logged and does not abort the remaining registration. Revert-checked against the
  per-directory loop.
- **Host/CI**: `./gradlew -p buildSrc test` unaffected; `:osmscout-client-java:jar` builds; native
  host tests via `meson test -C hostbuild "Tests/DatabaseOpenTest"` (or `ctest` in the CMake host
  build) per `guidelines/Build.md`.
- **Build**: `:app:assembleMobileDebug` and `:app:assembleAutomotiveDebug` for all three ABIs
  (`arm64-v8a`, `armeabi-v7a`, `x86_64`), no new compiler warnings.
- **On device (Android Auto projection or the automotive AVD)**: start a car session while the phone
  UI opens the map — no native tombstone, one `Diag/WARMUP` batch line, and the registration stall
  window (previously one close/reopen cycle per directory) gone from the start of the session; map
  render and cross-database search work with the installed set.

## Open Questions

- Should the registry later expose a remove/replace operation for a map scan that discovers fewer
  directories (deleted maps)? Not needed by this change; the add-only contract matches today's
  behavior.
- Is an ASan/TSan host build of the submodule tests worth wiring (no sanitizer configuration exists
  today)? Independent of this fix; the revert-check covers the regression.
