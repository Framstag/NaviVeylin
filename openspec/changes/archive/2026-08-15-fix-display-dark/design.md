# Design — fix-display-dark

## Context

See proposal.md — Why. Current code applies `FLAG_KEEP_SCREEN_ON` in `MapCanvasScreen` via `DisposableEffect(navState.isNavigating, state.keepScreenOn)`: set once on key change, cleared in `onDispose`. It never re-runs on activity resume (keys unchanged), so the flag is not restored after background/foreground switches, and it is gated on turn-by-turn navigation. The persisted setting already flows through `MapCanvasUiState.keepScreenOn` (default true, toggle in location options).

## Goals / Non-Goals

- **Goals**: keep-screen-on active for the whole time the app is resumed (setting enabled); re-applied on every resume; released on background/disable.
- **Non-Goals**: no changes to the setting persistence or toggle UI; no changes to turn-by-turn navigation behavior; no wake-lock rework (window flag is sufficient and correct for foreground-only keep-on).

## Decisions

### D1: Where the keep-screen-on lifecycle logic lives

### Alternative A: Lifecycle-aware observer in MapCanvasScreen (chosen)

Replace the keyed `DisposableEffect` with a `LifecycleEventObserver` registered on the screen's `lifecycleOwner`, inside a `DisposableEffect(Unit)` that only manages observer registration:

- `ON_RESUME` → `window.addFlags(FLAG_KEEP_SCREEN_ON)` when `state.keepScreenOn`
- `ON_PAUSE` → `window.clearFlags(FLAG_KEEP_SCREEN_ON)`
- Setting changes while resumed → observer re-reads `state.keepScreenOn` on next lifecycle event; additionally the setting gate is applied immediately via a separate small effect or by keying the flag application on `state.keepScreenOn` (see D2)

NaviVeylin is a single-activity app; `MapCanvasScreen` is the root destination and stays composed on the back stack while other screens (settings, map manager) are on top, so screen-on coverage equals "app in foreground". Window access: `context as ComponentActivity`.

- **Pros**: minimal change, one file; flag lifecycle tied to real activity lifecycle events, not recomposition; survives activity recreation/process death (observer re-registers, `ON_RESUME` fires again)
- **Cons**: if a future screen replaced the map as the app's root, coverage would depend on the map screen staying composed
- **Risk**: low

### Alternative B: Hoist to MainActivity

Apply the flag in `MainActivity` lifecycle callbacks, reading the setting via Hilt-injected `SettingsStorage`.

- **Pros**: app-wide by construction, independent of composition
- **Cons**: activity code grows; setting changes mid-foreground need a flow subscription; more plumbing for the same effect in a single-activity app
- **Risk**: low; rejected as over-engineered for current architecture

### Decision

D1-A: lifecycle observer in `MapCanvasScreen`.

### D2: Reacting to setting changes while foregrounded

The observer fires on lifecycle events only; a setting toggle while the app stays resumed would otherwise wait for the next pause/resume.

### Alternative A: Keyed flag application on the setting (chosen)

Keep a small `DisposableEffect(state.keepScreenOn)` (no `isNavigating`) that adds/clears the flag per current lifecycle state, plus the lifecycle observer that re-applies on resume and clears on pause. Both read the same two inputs (window flag state, `state.keepScreenOn`), so they stay consistent.

- **Pros**: toggle takes effect immediately (existing UX expectation from the spec's "Screen turns off when setting is disabled" scenario); small, readable
- **Cons**: two small blocks instead of one; must keep both in sync (both are two-liners)
- **Risk**: low

### Alternative B: Observer holds latest setting in a mutable ref

Observer reads a `rememberUpdatedState(state.keepScreenOn)` ref on every lifecycle event; a separate effect updates the ref.

- **Pros**: single mechanism
- **Cons**: indirection (`rememberUpdatedState`) for no behavior gain
- **Risk**: low; rejected for readability

### Decision

D2-A: keyed effect for immediate toggle response + lifecycle observer for resume/pause.

## Risks / Trade-offs

- [OEMs that clear window flags aggressively on stop] → Mitigation: flag is re-applied on every `ON_RESUME`, which is exactly the reported failure mode ("switching between applications").
- [Flag applied/cleared race between effect and observer on the same window] → Mitigation: both blocks are idempotent (`addFlags`/`clearFlags`); last writer wins deterministically per lifecycle state; no interleaving possible on the main thread.
- [Screen stays on while another app is in a dialog overlay (partial visibility)] → Accept: `ON_PAUSE` semantics cover the common background case; split-screen multi-resume is out of scope.
- [Compose test asserting window flags under Robolectric] → Mitigation: use `createAndroidComposeRule<ComponentActivity>` and assert `activity.window.attributes.flags`; drive `ON_RESUME`/`ON_PAUSE` via `LifecycleRegistry` manipulation (Robolectric supports this; RoutePanelComposeTest establishes the pattern).

## Migration Plan

Single commit-sized change; no rollout/rollback concerns. Regression path: device manual verification (foreground stay-on, app-switch restore, disable/enable toggle).

## Open Questions

None — the reported behavior and the spec delta fully determine the approach.
