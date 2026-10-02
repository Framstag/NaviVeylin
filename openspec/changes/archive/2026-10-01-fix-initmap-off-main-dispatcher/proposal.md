# Proposal — fix-initmap-off-main-dispatcher

## Why

`MapCanvasViewModel.initMap` runs its whole native and file block on the main dispatcher. The body is
launched with `initJob = viewModelScope.launch { … }`
(`app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt:1846`), and `viewModelScope` is
`Dispatchers.Main.immediate` — the ViewModel documents that itself (`:939`). Inside that body:

| call | line | kind |
|---|---|---|
| `assetCopier.ensureStylesheets()` | `:1850` | file I/O (non-suspend, `data/AssetCopier.kt:37`) |
| `client.openDatabase(mapPath)` | `:1855` | JNI |
| `NativeTileDataCache.apply(client, …)` | `:1883` | JNI |
| `client.openDatabases(additional)` | `:1912` | JNI (batch, one database-set change) |
| `favoriteRepository.init(favPath)` | `:1926` | JNI + file |
| `client.getDatabaseBoundingBox(mapPath)` | `:1937` | JNI |

`guidelines/Design.md` §4's first rule is a MUST — *"never call native/JNI code on the main thread;
run it on background dispatchers with timeouts and loading UI"* — and this is the phone's own start
path, i.e. the cold-start case the rule exists for. Every other JNI site in the same ViewModel
already honors it through the `defaultDispatcher` seam (`:363`, used at `:848`, `:1489`, `:1736`,
`:1797`, `:2106`, …), and the car's counterpart path does too: `AutoServiceModule.openMapDatabases`
wraps its native open in `withContext(Dispatchers.Default)` (`di/AutoServiceModule.kt:107`) under spec
`auto-map-renderer` — *"Renderer initialization off the car-app main thread"*. The phone `initMap`
block is the one remaining outlier, so the two surfaces currently disagree on a rule both are
supposed to follow.

Why now: no in-flight change owns this path, the defect is reached on **every** startup and every map
re-entry (the same entry point `initMap` uses after a map download), and a slow or contended
filesystem on a cold start delays the main thread — the shape the host-crash work identified for
main-thread native calls, here on the phone. Found while landing `fix-open-database-path-validation`
(`TODO.md` §107), which deliberately left it alone because restructuring the block is its own change.

## What Changes

- `initMap`'s JNI and file work moves off the main dispatcher: the body of the init coroutine runs on
  the ViewModel's `defaultDispatcher` seam, so `ensureStylesheets`, `openDatabase`, the tile-cache
  configuration, the `openDatabases` batch, `favoriteRepository.init` and the bounding-box lookup no
  longer execute on the main thread. `viewModelScope` is still the owner of the job, so the existing
  cancel/re-entry guard (`initJob?.cancel()`, `:1841`) keeps working unchanged.
- Every `_uiState` read-modify-write stays on the main dispatcher (`isLoading`, the
  "Could not open map database" error state, `mapReady`), so no state is published from a background
  thread and the ordering the UI observes does not change.
- The existing sequencing inside the block is preserved deliberately, because it is load-bearing: the
  persisted-viewport restore must complete **before** `mapRenderer` exists (an early
  `setScreenSize`/`renderMap` would otherwise clobber the restore with the default viewport), and the
  renderer handoff keeps building `MapRenderer` on its own background scope (`:1959`).
- The dispatcher seam is exercised by a test: a fake client that records the thread (or dispatcher)
  it is called from, and a case that fails when a native call is observed on the main thread. Today
  the seam exists but nothing asserts a native call actually uses it.
- No behaviour change for the user other than the missing main-thread stall: same state sequence, same
  error text, same viewport restore, same database set.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `map-render`: gains a requirement that phone map initialization runs off the main thread — the
  native client calls (`openDatabase`, `openDatabases`, the tile-data-cache configuration, the
  database bounding-box lookup), the favorites-store initialization and the asset/stylesheet file
  refresh SHALL run on a background dispatcher, SHALL NOT block the main thread, and SHALL keep the
  loading/error state sequence observable to the UI unchanged. This is the phone-side counterpart of
  the existing `auto-map-renderer` requirement *"Renderer initialization off the car-app main
  thread"*; the parity requirement is the same rule on both surfaces, with only the platform
  constraint differing (the car's thread is the host-callback thread).
- No other capability changes: `native-database-open` (the registry's open/register semantics),
  `native-tile-data-cache` (the raised-only capacity rule) and the favorites store contract are
  untouched — this change moves *where* the existing calls run, not *what* they do.

## Impact

**Affected code (Kotlin only):**
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — `initMap` (`:1826-1960`) and the
  `defaultDispatcher` seam (`:363`).
- `app/src/main/java/com/naviveylin/data/AssetCopier.kt` — read only (its `ensureStylesheets()` /
  `ensureIcons()` stay non-suspend file work; the caller supplies the dispatcher).
- `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelDatabaseOpenTest.kt` — the existing
  `initMap` cases (batch call, no-batch cases, rejected path, failing batch) must keep passing.
- new test(s) beside it (a dispatcher-recording fake and the main-thread guard case) plus the fake
  client in `app/src/test/java/com/framstag/libosmscout/client/FakeOSMScoutClient.kt` if it needs to
  record the calling thread.
- Android components: none (no manifest, resource, DI-module or Gradle change; no new dependency).

**Modules / native boundary:** `:app` only. **No submodule patch and no `:osmscout-client-java`
override** — the JNI bridge, the C++ side (`OSMScoutClient.cpp`) and the gitlink are untouched; this
is a caller-side threading change.

**Guidelines:** `guidelines/Design.md` §4 (threading MUST) is the rule being conformed to, and its
threading/seam conventions apply to the new test. `guidelines/Build.md` governs the verification
recipe (per-module test tasks, real execution rather than an up-to-date hit). `UI.md` is unaffected —
no UI, label or hierarchy change.

**Previous specs changed:** `map-render` only (one added requirement + scenarios). The change is
**additive**: no existing requirement is deleted or reworded, no user-visible behaviour is removed.

**Rollback:** revert the one commit. The block runs inline again exactly as today; there is no data
migration, no persisted state and no format change, so a rollback needs no cleanup.

**Risk / coordination:** `MapCanvasViewModel.initMap` and `MapCanvasViewModelDatabaseOpenTest.kt` are
the files the in-flight change `fix-open-database-path-validation` (15/19 tasks) last touched. Land
this change after that one archives (or rebase on it), so two writers do not overlap in the same
`initMap` body.

**Scope:** phone only (`:app`) — the car path already satisfies the rule via `auto-map-renderer`; the
change states the parity rather than re-implementing it. Verification is unit-level (no device
required): the dispatcher-recording fake plus the existing `initMap` suite, with the build and the
full `:app` flavors as the compile/test gate.
