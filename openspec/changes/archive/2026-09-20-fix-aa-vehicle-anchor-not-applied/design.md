# Design

## Context

See proposal.md — Why. The persisted anchor fields (`autoRoutingAnchorId`/`autoFreeDrivingAnchorId`, written by `VehicleAnchorPickerScreen.persistSelection` → `AutoSettingsProvider.saveCarAnchor`) are correct; what breaks is reading them back:

- `PreferencesScreen` loads `AutoSettings` once in `init`. The `SettingsLoadGuard` lifecycle wiring re-renders the screen on re-visibility, but with the **stale in-memory snapshot** — no re-read. So after an anchor picker pops back, the row keeps the pre-selection label.
- `FreeDrivingScreen` reads settings once at screen init (`rendererGate.setFollowAnchor` applied once) — the anchor freezes for the whole session.
- `NavigationScreen` re-reads settings only inside the `hasStateChanged` gate (fires on maneuver/distance/speed deltas) — anchor changes apply eventually while driving, but only as a side effect of nav-state churn, and never on a calm stretch.
- Both pickers `pop()` the screen immediately after `scope.launch { persist }`; the persist coroutine runs on the screen-scoped `SupervisorJob`, which `onDestroy` cancels — a latent write-loss race (usually lands: file IO ≲ ms vs pop roundtrip, but racy by construction).

## Goals / Non-Goals

**Goals:**
- Anchor selection visible on the settings row immediately after pop-back; anchor selection applies to the follow map during the active session without a screen restart.
- Survives immediate picker dismissal (write not lost).
- Keep the existing guard contract (loading placeholder recovery, error row + Retry) intact.

**Non-Goals:**
- No renderer changes — `AutoMapRenderer.anchorCenterFor`/`setFollowAnchor` already frame follow mode at the anchor.
- No storage/schema changes, no `AutoSettingsMapping` changes, no native/JNI.
- Not converting phone-side anchor handling (`MapCanvasViewModel` already live-sets + persists on selection).
- Not building the phone-visible AA-settings surface (separate change; this design's observable-store alternative is the natural foundation for it).

## Decisions

### D1: Pull-based re-read on re-visibility (not an observable settings store)

Each screen already owns a `loadSettings()` + `SettingsLoadGuard` pattern. On `onStart`, settings-bearing screens re-run that load and then re-render. The map screens do the same: reload, diff the anchor id against the applied one, and only on change call `RendererGate.setFollowAnchor` + `requestRender`.

- Alternative considered: a central observable settings store (`StateFlow<AutoSettings>` in `:core`, screens collect). Rejected for this change: bigger refactor (app-process singleton wiring, cache/refresh semantics, invalidation on every write), and the settings screens are the only writers on the car — a read happens exactly when a screen becomes visible, so push buys nothing here yet. Revisit when multiple consumers need live settings (e.g. the phone-visible AA-settings feature).
- Alternative considered: periodic polling (timer reload every N s on map screens). Rejected: wasted file IO on every interval for a setting that only changes while the driver is inside a settings screen (a resume reload covers it exactly).

### D2: Re-read hook lives in the shared lifecycle observer factory

`observeSettingsLifecycle(scope, guard)` gains an optional `onReVisible: (() -> Unit)? = null` — called from the observer's `onStart` before `guard.onLifecycleStart()`. Screens pass their existing `::loadSettings`; the guard then re-renders once the fresh data arrives. The guard itself stays render-recovery-only (no load logic added — single responsibility).

- Alternative considered: each screen registers its own extra `DefaultLifecycleObserver`. Rejected: duplicates the onStart/onDestroy wiring in three screens and risks divergent behaviour; the factory already exists and is unit-tested.
- Alternative considered: make the guard own the reload lambda. Rejected: mixes load (screen's job) with render recovery (guard's job).

### D3: NavigationScreen re-reads at resume unconditionally

`startObserving` runs on every `onStart`. The settings re-read moves to the top of `startObserving` (unconditional), keeping the diff-and-apply for anchor + lane hints + orientation + auto-zoom + overspeed. The existing `hasStateChanged`-gated reload stays for live state-driven re-application, but the anchor is no longer dependent on it. Reloads are cheap (one small JSON read via the provider's IO dispatcher) and occur only on resume/maneuver boundaries.

- Alternative considered: single unconditional reload inside the state `collect`, every emission. Rejected: runs at GPS tick frequency; no benefit since settings only change inside pushed screens.
- Alternative considered: keep the status quo (anchor applied only via the changed-gate). Rejected — this is the reported defect (spec: "Routing anchor applies when the setting changes during navigation").

### D4: Pickers persist before pop

`onSelect` becomes: launch persist on the screen scope; on success `screenManager.pop()`; on failure keep the picker visible (log + `guard` error/retry path), so a dropped write is visible instead of silent. The screen stays alive until the write completes, so the screen-scoped coroutine is safe by construction — no app-scoped scope needed.

- Alternative considered: app-scoped `CoroutineScope` fire-and-forget with immediate pop (keeps the instant UX). Rejected: loses error visibility and defeats the existing test seam (`persistSelection` is the tested entry); a silent lost write is exactly the failure mode in the report.
- Alternative considered: synchronous file write on the click path. Rejected: violates Design.md §4 (no blocking IO on the main path; the provider already dispatches to IO).
- Trade-off acknowledged: pop now waits for a small local file write (≲ a few ms) — imperceptible; acceptable for correctness.

## Risks / Trade-offs

- [Resume re-render storm on the map screens (reload + guard invalidate + state collect all fire on onStart)] → the guard caps recovery invalidates; reload produces one `setFollowAnchor` + `requestRender` (no-op when the anchor id is unchanged); follow render requests are already coalesced by the renderer.
- [Double-load race in NavigationScreen (resume load + changed-gated load)] → both read the same JSON; the diff applies only on id change; last-writer-wins with identical values; idempotent.
- [Free-driving street-label placement depends on the anchor (`StreetNameLabel.placementFor`)] → placement is derived from the same anchor field and updated in the same reload; the in-flight `street-name-overlay-avoid-bottom-anchor` change also touches this helper — rebase the free-driving task onto that change's contract if it lands first (same merge-order rule as the renderer task in `vehicle-position-presets`).
- [Persist-then-pop adds a small delay before closing the picker] → file write is sub-ms to a few ms; if the host drops the popped template, the guard's recovery re-delivers.
- [Archive-order constraint: the anchor requirements this change touches live in in-flight `vehicle-position-presets` deltas] → archive `vehicle-position-presets` (after its `fix-phone-vehicle-anchor-framing` prerequisite, per its own tasks 8.1/8.2) before archiving this change; `anchor-per-surface-visible-area` archives after. Specs use ADDED requirements here where the base capability lacks the header, and MODIFIED on `auto/preferences` (base headers exist), so `openspec validate` stays clean independently of that order.

## Migration Plan

1. `:auto` only, additive: observer-factory hook (D2) → PreferencesScreen re-read → pickers persist-then-pop (D4) → FreeDrivingScreen resume reload (D1) → NavigationScreen resume reload (D3).
2. No stored-data migration; `AppSettings`/`AutoSettings` untouched.
3. Rollback: revert the reload-on-resume callbacks (behavior returns to applying anchors at screen start). Spec-visible behavior change rolls back with it.

## Verification

- Unit (Robolectric, per screen): `PreferencesScreenTest` — re-visibility triggers `settingsProvider.load()` again and the row renders the new value; `VehicleAnchorPickerScreenTest`/`OverspeedDeltaPickerScreenTest` — persist-before-pop (pop only after save, keep-open on save failure); `FreeDrivingScreenTest`/`NavigationScreenTest` — resume reload calls `setFollowAnchor` with a new anchor + `requestRender`, and skips it when the id is unchanged.
- On-device (head unit / car-connected phone + GPX replay): open Preferences → pick a bottom-right preset → return: the row shows the new label and the follow map re-frames at the preset without leaving the session; repeat during navigation and free driving; logcat `adb logcat -s NaviVeylin` shows the settings reload + follow anchor lines; pick-and-immediately-back also survives a session/process restart (persisted value still correct).

## Open Questions

- Whether the persist-during-pop race actually lost a write on the user's device (the reported symptom is fully explained by the stale reads alone; the AA-restart check is still pending). This does not change the approach — D4 ships regardless, and the spec scenario "Anchor selection survives immediate dismissal" covers both outcomes.
