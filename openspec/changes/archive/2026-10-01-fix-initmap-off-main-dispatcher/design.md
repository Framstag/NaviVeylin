# Design — fix-initmap-off-main-dispatcher

## Context

See `proposal.md` — Why. The facts that shape the approach, verified on the tree (2026-09-30):

- `initMap` (`app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt:1826`) launches
  `initJob = viewModelScope.launch { … }` (`:1846`). `viewModelScope` is `Dispatchers.Main.immediate`
  — documented on the ViewModel itself (`:939`).
- Inside that one coroutine body the following run inline: `assetCopier.ensureStylesheets()` (`:1850`,
  non-suspend file I/O, `app/src/main/java/com/naviveylin/data/AssetCopier.kt:37`),
  `client.openDatabase(mapPath)` (`:1855`, JNI), `NativeTileDataCache.apply(client, PHONE_TILES)`
  (`:1883`, JNI), `client.openDatabases(additional.toTypedArray())` (`:1912`, JNI, one batch),
  `favoriteRepository.init(favPath)` (`:1926`, JNI + file), `client.getDatabaseBoundingBox(mapPath)`
  (`:1937`, JNI).
- The ViewModel already has the seam this change needs: `internal var defaultDispatcher:
  CoroutineDispatcher = Dispatchers.Default` (`:363`), used by every other JNI site (`:848`, `:1489`,
  `:1736`, `:1797`, `:2106`, `:2191`, `:2346`, `:2408`, `:2537`, `:3403`).
- `favoriteRepository.init` already suspends internally (`withContext(defaultDispatcher)`, noted at
  `:1834`); the assets, the database opens and the bounding-box lookup do not.
- The car counterpart already complies: `AutoServiceModule.openMapDatabases` wraps its native open in
  `withContext(Dispatchers.Default)` (`app/src/main/java/com/naviveylin/di/AutoServiceModule.kt:107`)
  under spec `auto-map-renderer` — "Renderer initialization off the car-app main thread".
- Ordering inside the body is load-bearing and must survive the move: the viewport restore runs
  *before* `mapRenderer` is built (an early `setScreenSize`/`renderMap` would otherwise submit a
  render with the uninitialized default viewport and clobber the restore), and `MapRenderer` is
  created on its own background scope (`:1959`) so heavy JNI renders never block main.
- The cancel/re-entry guard is `initJob?.cancel()` (`:1841`) — it relies on the body suspending, which
  the existing `favoriteRepository.init` suspension already provides (the comment at `:1831-1836`
  states exactly that).
- Constraint: `MapCanvasViewModel.initMap` and `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelDatabaseOpenTest.kt`
  are the files in-flight change `fix-open-database-path-validation` (15/19) last touched.

## Goals / Non-Goals

**Goals:**

- Every JNI and filesystem call of initialization is off the main thread, with the loading/error/ready
  semantics the UI already observes unchanged.
- The dispatcher seam becomes asserted, not just available: a test fails if a native call of
  initialization is observed on the main thread.
- The change stays small and revertible: one coroutine body plus tests, no new abstraction layer.

**Non-Goals:**

- No change to what the calls do — `native-database-open`'s register/report semantics, the raised-only
  tile-cache rule (`native-tile-data-cache`) and the favorites-store contract stay as they are.
- No new "initialization service"/`UseCase` extraction: the seam and the coroutine already exist.
- No timeout wrapper or cancellation-time policy for a hanging native open (see Open Questions).
- No change to the car path — it already complies; this change only states the parity.
- No change to `AssetCopier`'s API (its calls stay non-suspend file work; the caller owns the
  dispatcher).

## Decisions

### D1 — Run the whole body on `defaultDispatcher`, not per-call wrappers

**Chosen:** launch the init coroutine body so that its JNI/file work runs on the existing
`defaultDispatcher` seam (i.e. the body executes off-main; the coroutine is still owned by
`viewModelScope`, so cancellation semantics are unchanged).

**Alternative A — wrap each JNI/file call individually (`withContext` per call):** six small wrappers
instead of one; more lines, more places to forget when a seventh call is added, and it re-reads
`_uiState` across dispatcher hops. Rejected: the block is sequential and has no main-thread
requirement anywhere inside it.

**Alternative B — move initialization into a new `@Singleton` initializer/UseCase:** would satisfy the
rule, but the state it publishes (`_uiState`, `mapReady`, `mapRenderer`) is ViewModel state, so the
extraction would need an observer seam for a purely mechanical threading change. Rejected as scope
inflation for one defect (`Design.md` §12 — do not add a layer to move three lines).

**Alternative C — leave it, document it as accepted:** rejected: `Design.md` §4 is a MUST, and the
block is the phone's cold-start path — precisely the case the rule exists for.

### D2 — Keep `_uiState` mutations on the main thread

**Chosen:** publish `isLoading`, the error state and `mapReady` from the main dispatcher (the
collector/`withContext(Dispatchers.Main)` hop), and pass the computed results (opened flag, restored
viewport, renderer) back to main for publication.

**Alternative — let the background body write `_uiState` directly:** `MutableStateFlow` is safe to
write from any thread, so this would "work". Rejected: every existing writer of `_uiState` is
main-thread, the ordering the UI observes is main-ordered, and a background write would make the
`isLoading → ready` sequence observable out of order relative to the main-thread state consumers
(the tests assert that sequence). The rule is also the one `Design.md` §4 states for state updates.

### D3 — Preserve the restore-before-renderer ordering explicitly

**Chosen:** the restored viewport is computed off-main and applied on main **before** the
`MapRenderer` is created and assigned (the existing order — `:1929-1959` — is kept, only the
dispatcher changes). A comment on that boundary records why the order matters.

**Alternative — create the renderer first, then restore:** would let a render land with the default
viewport. This is the exact defect class `fix-initmap-reentry-viewport-race` and
`MapCanvasViewModelNavEndRestoreTest` defend; rejected.

### D4 — Assert the dispatcher instead of trusting the seam

**Chosen:** extend the test double so it records the thread (or `CoroutineDispatcher`) each native
call arrives on; assert in a dedicated case that no initialization native call is observed on the
main thread, and keep the existing `initMap` cases (batch, no-batch, rejected path, failing batch) as
the behavior guard. `MapCanvasViewModel.defaultDispatcher` is already `internal var`, so a test
dispatcher can be injected without new production API.

**Alternative — rely on the existing behavior tests:** they pass on both the fixed and the unfixed
code (they assert results, not threads), so they cannot detect a regression of this rule. Rejected —
this is the "test that fails on the pre-change code" requirement (revert-check).

### D5 — Threading model and lifecycle

- **Dispatcher:** the init body's JNI/file work on `defaultDispatcher` (default `Dispatchers.Default`,
  as the seam already is — the calls are blocking native/file work, which is why the existing sites
  use `Default` rather than `IO`); state publication on `Dispatchers.Main`.
- **Owner/lifecycle:** `viewModelScope` keeps ownership; `initJob?.cancel()` (`:1841`) still
  supersedes. Cancellation must not leave a half-applied state: the off-main section performs no
  `_uiState` write, so a cancel can only discard computed results, never publish a partial one.
- **Native boundary:** unchanged — the same JNI entry points, called from a background thread instead
  of the main one. No submodule patch, no `:osmscout-client-java` override, no gitlink bump.
- **Re-entry:** unchanged — one `initJob`, one renderer, one database set per surviving
  initialization.

## Risks / Trade-offs

- **[A native open that hangs now hangs a background thread instead of the UI]** → The UI stays
  responsive and shows the loading state; a bounded timeout with a user-visible failure is *not*
  added here (Open Questions) — the existing behavior on a hanging open was a frozen UI, so this is
  strictly better, not a regression.
- **[Two writers in `initMap` while `fix-open-database-path-validation` is still in flight]** →
  Land after that change archives, or rebase onto it; the proposal's Impact section records this.
- **[A dispatcher hop could reorder `isLoading` relative to an early render request]** → The
  loading state is published before the off-main work starts and the renderer does not exist during
  the restore; the `MapCanvasViewModelDatabaseOpenTest` cases plus the new ordering scenario pin it.
- **[`defaultDispatcher` is `internal var`, so a misconfigured test could hide the rule]** → The new
  case asserts on the *observed* thread of the native calls (recorded by the double), not on the
  field value, so a test cannot pass by configuring the seam away.
- **[Revert-check subtlety]** → Removing the fix must fail the new case (native call observed on
  main) while the behavior cases stay green — one mutation, per the revert-check rule.

## Migration Plan

No data or format migration. Deploy = land, then:
1. build `:app` both flavors, confirm no new warnings;
2. run the `:app` suites for both flavors with real execution (clean `test-results`, quote executed
   counts — `guidelines/Build.md` §6/§17);
3. on-device (when a device is available): cold start on the phone, confirm the loading state is
   drawn immediately and the map appears without a main-thread stall, and that `dumpsys` shows no
   ANR/`Input dispatching timed out` line in the start window.
Rollback = revert the single commit; the block runs inline exactly as today, nothing to clean up.

## Open Questions

- Bounded timeout + user-visible failure for a hanging native database open: deliberately not part of
  this change (it would add a spec-level error path). Recorded as a follow-up candidate rather than
  answered here.
