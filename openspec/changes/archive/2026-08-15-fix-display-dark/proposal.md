# Fix Keep Screen On

## Why

The app should keep the device screen on while it is running (with the "Keep screen on" setting enabled), but the screen sometimes dims anyway — typically after switching between applications. The current implementation applies `FLAG_KEEP_SCREEN_ON` only inside a `DisposableEffect` keyed on `navState.isNavigating` and `state.keepScreenOn`: the flag is set once when those keys change and never re-applied on activity resume. When the user switches to another app and back, the window flag is gone (cleared by the system on stop/window detach on many devices) and nothing re-applies it, so the screen falls back to normal power management and dims mid-use. The effect is also gated on turn-by-turn navigation, so the screen is allowed to dim while the app is simply running (browsing the map, settings open).

## What Changes

- **Keep the screen on while the app is in the foreground** (not only during turn-by-turn navigation): apply `FLAG_KEEP_SCREEN_ON` whenever the app is resumed and the "Keep screen on" setting is enabled.
- **Re-apply the flag on every resume**: a lifecycle-aware mechanism (lifecycle observer, not a keyed `DisposableEffect`) re-adds the flag on `ON_RESUME` and clears it on `ON_PAUSE`, so returning from another app always restores keep-screen-on.
- **Clear the flag on backgrounding and when disabled**: `ON_PAUSE` releases the flag so the screen can dim when the app is not visible; disabling the setting clears it immediately.
- The persisted "Keep screen on" setting and its location-options toggle stay unchanged.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `always-on-display`: Change the core requirement from "screen stays on during navigation" to "screen stays on while the app is in the foreground". Add a scenario for app-switch return (flag re-applied on resume) and adjust the turn-off scenarios to backgrounding / setting-disabled instead of navigation-stop.

## Impact

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — replace the `DisposableEffect(navState.isNavigating, state.keepScreenOn)` block (~L204-215) with a lifecycle-aware observer: `ON_RESUME` → add flag when `state.keepScreenOn`; `ON_PAUSE` → clear flag; gate on the setting only, drop the `isNavigating` condition; remove the keyed-effect cleanup
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — no change expected (`keepScreenOn` already in `MapCanvasUiState`, driven by persisted setting)
- Tests: new/extended Robolectric Compose test for `MapCanvasScreen` keep-screen-on behavior (flag set when composed with setting enabled; re-applied on `ON_RESUME` after `ON_PAUSE`; cleared when setting disabled and on dispose)
- Docs: `openspec/specs/always-on-display/spec.md` updated via delta; no README/AGENTS change expected
- No new dependencies; no native/JNI changes
