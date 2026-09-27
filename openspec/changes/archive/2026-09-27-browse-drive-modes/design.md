# browse-drive-modes — Design

## Context

See proposal.md — Why. Current state: the phone map screen has no explicit mode; "mode" is derived ad hoc from `followMode` (`MapCanvasUiState`, app module) and `isNavigating` (`NavigationState`, core module, shared with Android Auto). Consumers re-derive it independently (`compassNorthUp = if (isNavigating) navNorthUp else freeFormNorthUp` at `MapCanvasScreen.kt:621`, `shouldShowReCenterButton = !followMode || (autoZoomPaused && isNavigating)` at `MapCanvasViewModel.kt:2546`). `followMode` is persisted in `SettingsStorage` and restored on start. The location-options sheet (`LocationOptionsOverlay.kt`) exposes a "Map follows position" toggle, a mode-adaptive orientation control, and an auto-zoom toggle that is only reachable outside navigation (the nav right column has no `locationOptions` slot).

Constraints: `NavigationState` lives in `core` and is the single source for navigation state (phone + AA). State lives in immutable UiState data classes in single StateFlows (Design.md §3). Native calls never on the main thread (Design.md §4) — this change is pure Kotlin state, no native boundary impact.

## Goals / Non-Goals

**Goals:**
- One derived `MapMode` enum as the single source of truth for the map's driving state
- Always start in BROWSE; FREE_DRIVE as a one-tap preset; NAVIGATION derived from `isNavigating`
- Drift-triggered re-center with mode-dependent action; drive suspension on any manual interaction
- Config sheet shows the active state's options only, no mode switch; reachable mid-route

**Non-Goals:**
- No Android Auto behavior change (AA already separates browse/free-driving/navigation via screens)
- No change to the persisted settings file format (fields stay, semantics shift)
- No new native/JNI work

## Decisions

### D1: Mode representation — derived enum, not a stored field

`MapMode` enum (`BROWSE`, `FREE_DRIVE`, `NAVIGATION`) computed from the two existing sources of truth:

```kotlin
val mode: MapMode
    get() = when {
        navState.isNavigating -> MapMode.NAVIGATION
        uiState.followMode    -> MapMode.FREE_DRIVE
        else                  -> MapMode.BROWSE
    }
```

- **Alternative A (stored field in `MapCanvasUiState`)**: a `mode` field kept in sync with `followMode`/`isNavigating`. Rejected: creates two sources of truth for "am I navigating" — every transition (nav start/stop, follow toggle) must update both, and a missed sync silently diverges. Violates Design.md §4 "one source of truth per data signal".
- **Alternative B (sealed class with per-mode data)**: overkill — the modes carry no distinct data payloads beyond the existing boolean flags.
- **Chosen**: derived property. The enum is a *view* over existing state; consumers stop re-deriving ad hoc and read `mode` instead. `MapMode` lives in `MapCanvasViewModel.kt` next to `MapCanvasUiState`.

### D2: Mode switch — dedicated Drive button in the right column

A dedicated Drive button (car icon in BROWSE, exit icon in FREE_DRIVE) sits in the right-side widget column directly below the compass: BROWSE → tap → FREE_DRIVE (follow on, auto-zoom on, heading-up, center on position); FREE_DRIVE → tap → BROWSE (follow off, north-up, stay at position). Hidden during NAVIGATION. The compass button keeps its orientation/re-center role (short press re-centers via the mode-dependent `reCenterAction`).

- **Alternative A (compass center action as the toggle)**: zero new UI, but the compass reads as an orientation indicator — users do not discover that short-press starts free driving (reported on-device). Rejected.
- **Alternative B (menu entry)**: buried — fails the "easy to activate" requirement.
- **Alternative C (FAB, bottom-center)**: more prominent but competes with the re-center button and attribution for bottom space; the right column already hosts the location controls.
- **Chosen**: a dedicated icon button in the right column — unambiguous affordance, mode feedback via icon swap, consistent with the column's icon-button style.

### D3: Drive suspension — `driveSuspended` replaces `autoZoomPaused`

`autoZoomPaused` (zoom-only suspension) generalizes to `driveSuspended: Boolean`, a runtime (non-persisted) flag set when any manual interaction — pan, zoom, or rotate — occurs in FREE_DRIVE. Set at the existing gesture call sites that already call `disengageFollowMode()` (`onManualRotationStart`, pan/zoom handlers in `MapCanvasScreen.kt`). Cleared by the re-center action.

- **Alternative A (keep `autoZoomPaused`)**: misses pan/rotate suspension — the user's model requires all three.
- **Alternative B (derive suspension from viewport-vs-GPS drift)**: no explicit flag, but needs drift tracking and re-derivation everywhere; more complex, more failure modes.
- **Chosen**: explicit flag, set/cleared at known call sites, mirrors the existing `autoZoomPaused` pattern.

### D4: Re-center action is mode-dependent

One button, two actions:
- BROWSE (drifted): center on GPS position, stay in BROWSE
- FREE_DRIVE (suspended): reset to standard drive values (follow on, auto-zoom on, heading-up, driving zoom)

Visibility: `shouldShowReCenterButton` becomes `mode == FREE_DRIVE && driveSuspended || mode == BROWSE && viewportDrifted`. The browse "drifted" condition needs a lightweight drift signal — a `browseDrifted: Boolean` runtime flag set on manual pan/zoom in BROWSE, cleared on re-center (see Open Questions for the alternative of comparing viewport to GPS projection).

- **Alternative A (single "re-center on position" action)**: doesn't restore the drive preset — the driver would re-enter drive manually, defeating the reset affordance.
- **Alternative B (two separate buttons)**: clutter; the user explicitly wants one drift-triggered button.
- **Chosen**: one button, context-aware action, hidden at start (clean start screen).

### D5: Config sheet — mode header + active-state section, no mode switch

`LocationOptionsOverlay` restructures: header names the current state ("Browse" / "Free drive" / "Navigation"); below it the active state's section; then General. No mode control in the sheet. The "Map follows position" toggle is removed.

- **Alternative A (mode radio in sheet)**: rejected by the user — mode switching belongs in the UI (the center button), not config.
- **Alternative B (keep "Map follows position" toggle)**: duplicates the mode control and permits inconsistent states (follow on in BROWSE).
- **Chosen**: config is config, mode is mode. The sheet's section content:
  - BROWSE: orientation — "North up" / "Free rotation" (`freeFormNorthUp`)
  - FREE_DRIVE / NAVIGATION: auto-zoom toggle (`autoZoomEnabled`) + orientation — "Follow direction" / "North up" (`navNorthUp`)
  - General (all states): keep screen on, lane hints, dark mode, ambient light, rendering, map style

### D6: Sheet reachable during navigation

The nav right column (`MapCanvasScreen.kt:1675` branch) gains the `locationOptions` slot (currently absent), so the sheet opens mid-route.

- **Alternative A (pre-nav only)**: driving config set in FREE_DRIVE carries into NAVIGATION. Rejected: mid-route adjustment (e.g., north-up on a highway) is a real need, and the user chose to expose it.
- **Chosen**: add the slot; the sheet's Driving section is identical in FREE_DRIVE and NAVIGATION, so no new sheet logic — only the header text differs.

### D7: `followMode` no longer restored from settings

`initMap` stops restoring `followMode` from `SettingsStorage` — the app always starts in BROWSE. The settings field stays in the JSON (lenient decode, Design.md §3) for backward compatibility; it is simply not applied at startup. `autoZoomEnabled`, `freeFormNorthUp`, `navNorthUp` remain persisted per-mode sub-options.

- **Alternative A (drop the field)**: breaks lenient decode of existing settings files.
- **Chosen**: keep the field, ignore at restore. Drive is per-session intent.

### D8: Pre-navigation mode restoration

`startNavigation` forces follow mode (`forceFollowMode`), so after `stopNavigation` the raw `followMode` may be true even if the user was browsing before. To honor "navigation end restores prior mode" (spec: map-modes), `MapCanvasViewModel` snapshots the mode when `isNavigating` transitions to true and restores it when it returns to false.

- **Alternative A (rely on raw `followMode` after nav)**: a browse-before-nav user would land in FREE_DRIVE after stopping — violates the spec.
- **Alternative B (NavigationViewModel owns the snapshot)**: couples the core nav state to phone-only mode semantics; AA doesn't need it.
- **Chosen**: snapshot in `MapCanvasViewModel` by observing `navState.isNavigating` transitions (a `LaunchedEffect`/collect in the ViewModel scope, main-thread StateFlow updates per Design.md §4).

## Threading & lifecycle

- All mode logic is pure Kotlin state in `MapCanvasViewModel` (main-thread `StateFlow` updates). No native calls, no new dispatchers.
- Mode transitions (`enterFreeDrive`/`exitFreeDrive`/`resetDrivePreset`) update state and trigger `renderMap()` exactly like today's follow-mode toggles — the existing render pipeline (background render, atomic bitmap+viewport emission) is untouched.
- `driveSuspended`/`browseDrifted` are set in the existing gesture handlers (main thread) and cleared by re-center; no lifecycle hooks needed — they are viewport-state, not platform-object state (Design.md §3: platform side effects stay in `DisposableEffect`; none added here).
- The pre-navigation mode snapshot lives in the ViewModel scope, cancelled with `viewModelScope` on ViewModel clear.

## Risks / Trade-offs

- **Mode derivation divergence** (a code path flips `followMode` or `isNavigating` outside the mode entry points) → Mitigation: all transitions go through `enterFreeDrive`/`exitFreeDrive`/`resetDrivePreset`; nav transitions are observed centrally for the snapshot. Unit tests cover the derivation matrix.
- **Browse "drifted" signal accuracy** (flag-based drift may miss edge cases like programmatic viewport moves) → Mitigation: flag set only in user-gesture handlers; programmatic moves (search result selection, POI centering) intentionally do not set it. If a false positive/negative appears on-device, the alternative (viewport-vs-GPS projection comparison) is a contained follow-up.
- **Nav right column space** (adding the gear mid-route competes with compass/speed/zoom) → Mitigation: the column is bottom-anchored and scrollable; the gear replaces no existing control, it is inserted in the existing `locationOptions` slot position.
- **Behavior change for existing users** (follow mode no longer restored on start) → Mitigation: this is the intended spec behavior ("always browse on start"); the re-center button and one-tap drive entry make re-entering drive immediate.

## Migration Plan

1. Implement in `MapCanvasViewModel` (mode enum, presets, suspension, snapshot) — pure state, no UI dependency.
2. Wire `MapCanvasScreen` (center button as toggle, re-center wiring, nav column gear).
3. Restructure `LocationOptionsOverlay` (header, sections, remove follow toggle).
4. Update `SettingsStorage` restore logic (ignore `followMode` at startup).
5. Update `guidelines/UI.md` (mode model, switch placement, re-center semantics).
6. Rollback: revert the change; the map returns to today's single-config behavior. No data migration needed (settings fields unchanged).

## Verification

- **Unit tests** (`MapCanvasViewModelTest`): mode derivation matrix (browse/free-drive/navigation × follow/isNavigating), preset application on entry/exit, `shouldShowReCenterButton` per mode × suspension/drift, suspension set on pan/zoom/rotate, pre-navigation mode restoration.
- **Compose tests**: sheet header + section content per state; no mode switch present; nav column shows the gear.
- **On-device**: start in browse (no re-center button); one-tap drive entry (follow+auto-zoom+heading-up+centered); pan/zoom/rotate in drive → re-center appears → press → standard drive values restored; browse drift → re-center → centers on GPS, stays browse; sheet shows correct section per state; sheet opens mid-route and auto-zoom/orientation changes apply immediately.

## Open Questions

- **Mode toggle iconography**: distinct icon for FREE_DRIVE (car/active) vs BROWSE (location)? Deferrable UI detail — the spec only requires the toggle behavior.
- **Speed widget in BROWSE**: today the speed widget shows in follow mode; BROWSE has no follow, so it hides naturally. Confirm no explicit change needed on-device.
