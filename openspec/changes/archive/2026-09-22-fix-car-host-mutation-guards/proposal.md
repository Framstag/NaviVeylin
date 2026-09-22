# Proposal

## Why

While a car session runs, the app and the templates host are **different processes**, and a fault that
reaches the app's main thread kills the app — the host's queued template work then runs against an
invalidated `CarHost`, which is a `FATAL EXCEPTION` in the host (`TODO.md` §51). From the driver's
seat that reads as "Android Auto crashed, the app was fine", which is exactly the report this change
is opened from.

The 2026-09-21 host-safety work (`fix-aaos-host-crash`, `fix-host-crash-residual-paths`,
`fix-car-surface-ownership-and-host-callbacks`) guarded host *callbacks* (the `SessionCarSurfaceHost`
dispatch wrapper), every template build, the trip/notification paths, `NavigationManagerController`
and the surface lifetime. Three host-mutation paths are still unguarded, and each of them is a
process death:

1. **Template-row click listeners** — `ScreenManager.push` runs inside `setOnClickListener`. The
   car-app library (`RemoteUtils.dispatchCallFromHost`) answers the host with a `FailureResponse` and
   then **rethrows on the app's main thread**, so a throw here kills the process *and* reports the
   failure to the host.
2. **The session's own coroutines** — `showNavigationScreen`, `showRootScreen`,
   `restoreDrivingMode`, the `showError` push and its 4-second-delayed `popToRoot` call
   `carContext.getCarService(ScreenManager::class.java)` with no guard, on a
   `CoroutineScope(SupervisorJob() + Dispatchers.Main)` that carries **no**
   `CoroutineExceptionHandler`. The delayed one runs four seconds after the error, when the session
   (or the whole host connection) may already be gone.
3. **The session lifecycle callbacks** — `onStart`'s host sync is not wrapped, so a throw escapes the
   lifecycle observer into the library's dispatch.

The per-screen observation seam (`CarScreenObservations`) got a fault handler; the session scope and
the screens' own scopes did not — the guarantee is asymmetric in exactly the places that mutate the
host.

Separately, the error overlay makes one host mutation too many: `NavigationSession.showError` pushes
the overlay and then calls `popToRoot()` four seconds later, which **pops the navigation screen** off
the stack while `navScreenPushed` stays `true` — navigation guidance silently disappears and
`showNavigationScreen` early-returns forever after, while the host's navigation session is still
active.

## What Changes

- **Add one guarded host-mutation seam** and route every `ScreenManager` push / pop / `popToRoot`
  call through it: the seven template-row click sites, the session's push/pop sites, and the session
  lifecycle callbacks. A rejection degrades to a logged no-op — never a process death.
- **Put a fault handler on the scopes that can mutate the host**: the session scope and the car
  screens' own scopes, mirroring what `CarScreenObservations` already does, so a fault is confined to
  the coroutine that raised it.
- **Stop mutating the host for a session that is gone**: check the session's state (destroyed / not
  started) immediately before each host mutation, and discard a mutation that was scheduled while the
  session was alive but would run after it ended — in particular the error overlay's delayed
  auto-dismiss.
- **Make the error overlay pop only itself**: the overlay is scoped to the screen the session pushed,
  the navigation view survives an error, and the session's screen bookkeeping (`navScreenPushed`,
  `navigationScreen`) is refreshed from what the session actually did rather than assumed.
- **Take screen construction out of the host callback**: the details screen's "Show on map" action
  currently does `popToRoot()` + `push(MapScreen(...))` inside the click listener, constructing a
  whole screen (entry-point resolution, renderer gate, provider first-touch) on the host's answering
  path. The mutation is deferred to the main thread outside the callback and the construction is
  guarded.
- **Cover it with tests** that a throwing host mutation from a click path, from a session observer
  and from `onStart` leaves the process alive and the app usable, plus a test that an error while
  navigating leaves the navigation view on the stack (task-level: existing suites stay green).

## Capabilities

### New Capabilities

<!-- none: this change only tightens an existing (in-flight) capability -->

### Modified Capabilities

- `car-host-fault-isolation`: three requirements change — "No fault escapes into the host path"
  (extends from the enumerated trip/notification/template/frame paths to **every** host mutation,
  wherever it runs, plus a fault handler on the scopes that own them), "Host callbacks answer
  promptly" (extends to template-row actions that lead to a screen push: no screen construction and no
  provider first-touch on the host's answering path) and "Bounded host-facing traffic while not
  visible" (a mutation scheduled while the session was started SHALL be discarded when it would run
  after the session stopped or ended). One requirement is added: the session's screen-stack mutations
  are balanced — the session pops only what it pushed and the transient error overlay pops only
  itself.

`car-host-fault-isolation` is introduced by the still-unarchived changes `fix-aaos-host-crash`,
`fix-host-crash-residual-paths` and `fix-car-surface-ownership-and-host-callbacks`; this change
extends the same capability rather than opening a near-duplicate one. Archive ordering is recorded in
`tasks.md` (the capability must exist in `openspec/specs/` before this change is archived, or
`openspec validate` reports "target spec does not exist; only ADDED requirements are allowed" for the
MODIFIED deltas).

## Impact

**Scope: Android Auto / Android Automotive OS only.** The phone surface never touches the car-app
library; nothing in this change alters phone behaviour, the native layer, or the map rendering
pipeline. No native/JNI change: no submodule patch and no bridge-module override.

Affected code:

- `auto/src/main/java/com/naviveylin/auto/NavigationSession.kt` — guarded push/pop/popToRoot in
  `showNavigationScreen`, `showRootScreen`, `restoreDrivingMode`, `showError`, `retryStartup`,
  `syncHostWithCurrentState`, `onStart`/`onDestroy`; a fault handler on the session scope; the error
  overlay pops only itself; the delayed auto-dismiss is discarded when the session is gone.
- `auto/src/main/java/com/naviveylin/auto/SafeScreen.kt` (or a sibling file next to it) — the new
  guarded host-mutation seam and its logging.
- `auto/src/main/java/com/naviveylin/auto/AddressBookScreen.kt`,
  `AddressBookAddressPickerScreen.kt`, `PoiSearchScreen.kt`, `PoiResultsScreen.kt`,
  `SearchScreen.kt`, `DetailsScreen.kt` — the click-path pushes go through the seam; `DetailsScreen`'s
  "Show on map" no longer constructs a screen inside the callback.
- `auto/src/main/java/com/naviveylin/auto/CandidatePickerScreen.kt` — its `onPick` callback pushes a
  screen; route it through the seam too.
- `auto/src/main/java/com/naviveylin/auto/MapScreen.kt` — the deferred push after a surface tap goes
  through the seam and re-checks the session state.
- `auto/src/main/java/com/naviveylin/auto/NavigationScreen.kt` — the fault handler on its own scope
  (its `popToRoot()` on a non-navigating state goes through the seam).
- Tests: `auto/src/test/java/com/naviveylin/auto/` — a seam test (a throwing mutation yields a
  logged no-op), a session test (an error while navigating keeps the navigation view on the stack;
  the delayed dismiss mutates nothing after the session ended), and the existing click-path/session
  tests updated only where the seam changes the call shape.

Affected guidelines:

- `guidelines/Design.md` — the car-host section: "every host mutation goes through the guarded seam,
  and the session/screen scopes carry a fault handler" (the existing rule covers template builds and
  host callbacks only).
- `guidelines/Build.md` §10 — the on-device evidence recipe: the `HOST` tag now also carries the
  rejected mutations, and the error-path scenario (error while navigating) joins the baseline checks.

Additive, not breaking: no public API, no data or settings format, no spec removal. Rollback path:
revert the seam and the session changes (the guards are pure fault confinement; the only
behaviour-visible fix is that an error no longer removes the navigation view, which reverting simply
restores).

## Open Questions

1. **Should a confined fault also be visible to the user?** The overlay is the natural surface, but
   that risks an error loop. Default: log it under the diagnostics `HOST`/`TEMPLATE` tag and stay
   silent, matching the existing rule for template builds — revisit with the on-device run.
2. **Does the error overlay still need to auto-dismiss with the navigation view underneath?** The
   4-second dismiss stays, but it pops only the overlay; if the host's own back gesture is the
   expected way out, the delay can be dropped in a follow-up.
