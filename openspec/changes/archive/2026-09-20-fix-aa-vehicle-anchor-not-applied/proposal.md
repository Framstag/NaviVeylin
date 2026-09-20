# Proposal

## Why

On Android Auto, picking a vehicle-position preset (routing or free driving) appears to do nothing: after the picker pops, the settings row still shows the old anchor label, and the follow map keeps framing the vehicle at the old position during the session. The persistence layer is intact (the selector writes the car's own `autoRoutingAnchorId`/`autoFreeDrivingAnchorId` fields through `AutoSettingsProvider.saveCarAnchor` into the shared JSON), but every car screen caches `AutoSettings` locally and refreshes at unreliable points: settings screens re-render a stale in-memory snapshot on re-visibility instead of re-reading, and the map screens read the anchor once at screen start (free driving) or opportunistically on navigation-state changes. The selected value therefore never reaches the UI or the renderer until a screen restart.

## What Changes

- Settings screens reload the persisted settings on re-visibility: `PreferencesScreen` (and both pickers' lifecycle wiring) re-runs the settings load when the screen becomes visible again, instead of the `SettingsLoadGuard` merely re-delivering the stale template. The vehicle-position rows then always display the currently configured anchor after a picker pop-back.
- Follow-map screens apply the anchor live during the session:
  - `FreeDrivingScreen` reloads settings on re-visibility and pushes the (new) anchor into `RendererGate.setFollowAnchor` + `requestRender`.
  - `NavigationScreen` reloads settings at the top of every `startObserving` cycle (on resume), no longer gated behind `hasStateChanged`; the anchor applies without waiting for the next maneuver change.
- Picker persistence hardening (latent race): `VehicleAnchorPickerScreen` and `OverspeedDeltaPickerScreen` currently `pop()` the screen immediately after launching the persist coroutine on the screen-scoped `SupervisorJob`; a pop that destroys the screen cancels the scope and can drop the write mid-flight. The pickers await the persist (or run it on an application-scoped scope) before popping.
- Additive behavior fix. No renderer changes (`AutoMapRenderer` already anchors follow frames), no storage/schema changes, no native/JNI changes.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `auto-map-layout`: the Android Auto settings dialog rows for the vehicle anchors SHALL display the currently persisted anchor whenever the screen becomes visible (not a stale snapshot from the previous visit), and a picker selection SHALL survive immediate dismissal (pop-back right after selecting must not lose the persisted value).
- `auto/preferences`: the car settings screen load pattern gains reload-on-re-visibility — re-joining a settings screen re-reads the shared settings before rendering, extending the existing guard rule (`SettingsLoadGuard` re-delivers, but must not re-render stale content).
- `auto/free-driving`: the configured free-driving anchor SHALL apply to the follow-mode map during the active session when the setting changes (currently frozen at screen start).
- `auto/navigation-view`: the configured routing anchor SHALL apply to the navigation surface during the active session when the setting changes (currently applied only on navigation-state changes or screen restart).

## Impact

- **Code** (all `:auto` module):
  - `auto/src/main/java/com/naviveylin/auto/PreferencesScreen.kt` — reload settings on re-visibility (lifecycle), keep the guard's error/retry contract.
  - `auto/src/main/java/com/naviveylin/auto/VehicleAnchorPickerScreen.kt` + `OverspeedDeltaPickerScreen.kt` — await persist before pop (or app-scoped write); keep unit-test seam (`persistSelection`).
  - `auto/src/main/java/com/naviveylin/auto/FreeDrivingScreen.kt` — settings reload on re-visibility → `setFollowAnchor` + `requestRender`.
  - `auto/src/main/java/com/naviveylin/auto/NavigationScreen.kt` — `startObserving` reloads settings unconditionally per resume.
  - `auto/src/main/java/com/naviveylin/auto/SettingsLoadGuard.kt` — optionally gains a "reload" lifecycle hook (guarded re-load alongside re-invalidate).
- **Tests**: reload-on-resume unit tests for PreferencesScreen/free-driving/nav, persist-before-pop test for both pickers (Robolectric), on-device verification on head unit (select preset → row updates on pop-back, map re-frames during session).
- **Interplay / sequencing**: this change modifies requirements added by the in-flight `vehicle-position-presets` change (`auto-map-layout` anchor rows, `auto/free-driving` "Follow mode activated", `auto/navigation-view` "Vehicle anchor during navigation"). Per the established archive-order rule (see `vehicle-position-presets` tasks 8.1/8.2), `vehicle-position-presets` (and the phone-anchor change feeding it) must archive BEFORE this change so the delta headers exist; `anchor-per-surface-visible-area` archives after. `fix-aa-settings-stuck-loading` is archived (2026-09-17), so `auto/preferences` carries the guard requirement already.
- **Guidelines**: `guidelines/UI.md` — extend the Android Auto car-screen rule from "re-render on resume" to "re-read settings on re-visibility" for settings-bearing screens (same change, per guideline-supersede rule).
- **Scope**: Android Auto surfaces only (`:auto`); phone anchor handling (`MapCanvasViewModel` live-sets + persists on selection) is unaffected. Additive; rollback = revert reload-on-resume so anchors apply at screen start (pre-bug behavior). No breaking changes.
