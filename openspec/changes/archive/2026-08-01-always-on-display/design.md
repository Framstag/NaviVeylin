## Context

See `proposal.md` — Why for motivation. See `specs/always-on-display/spec.md` for requirements.

Current state: `AppSettings` data class in `SettingsStorage.kt` persists follow mode, auto-zoom, and orientation booleans to a JSON file. `MapCanvasViewModel` loads settings on init and exposes them via `MapCanvasUiState`. `LocationOptionsOverlay` renders a bottom sheet with toggles gated on `isNavigating`. `MapCanvasScreen` observes `navState.isNavigating` from `NavigationViewModel`.

No screen-on management exists today — device follows system screen timeout.

## Goals / Non-Goals

**Goals:**
- Keep screen on during active navigation when setting enabled
- Add persistent user toggle (default on) in location options bottom sheet
- Remove screen-on flag immediately when navigation stops or toggle disabled
- Survive app restart via existing JSON settings persistence

**Non-Goals:**
- Wake lock / partial wake lock (only `FLAG_KEEP_SCREEN_ON` on window — no CPU wake lock needed)
- Per-route or per-session override (setting is global)
- Android Auto screen-on (deferred)
- Aggressive dimming prevention beyond `FLAG_KEEP_SCREEN_ON`

## Decisions

### Decision: `FLAG_KEEP_SCREEN_ON` on activity window

Use `activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)` / `clearFlags()`.

**Rationale:** Simplest Android API for keeping screen on. No extra permissions needed. Automatically lets system dim when flag is removed. No wake lock acquisition/release lifecycle to manage.

**Alternatives considered:**
- **PowerManager.WakeLock** — Requires `WAKE_LOCK` permission, must be acquired/released manually, easy to leak. Overkill — we only need screen-on, not CPU wake.
- **`keepScreenOn` on Compose `AndroidView`** — Only works for that view, not the whole screen. Window flag covers all content including overlays.

### Decision: Reactive flag management via `DisposableEffect` in `MapCanvasScreen`

`MapCanvasScreen` already uses `DisposableEffect` for lifecycle observation. Add a second `DisposableEffect` keyed on `navState.isNavigating` and `uiState.keepScreenOn`. When both are true, add flag. On dispose or when condition becomes false, clear flag.

**Rationale:** Co-locates side effect with the composable that owns the window reference. No ViewModel involvement needed for the flag itself — ViewModel only manages the setting state.

**Alternatives considered:**
- **ViewModel managing flag** — ViewModel shouldn't hold `Window` reference (context leak risk). Compose `DisposableEffect` is the idiomatic pattern.

### Decision: Toggle always visible in bottom sheet

The "Keep screen on" toggle appears in the bottom sheet at all times, not gated on navigation state.

**Rationale:** User may want to configure the setting before starting navigation. Unlike auto-zoom (which only makes sense during navigation), screen-on behavior is a general preference the user should be able to set at any time.

### Decision: Setting persisted in existing `AppSettings` JSON

Add `keepScreenOn: Boolean = true` to the existing `AppSettings` data class.

**Rationale:** Zero new dependencies. `SettingsStorage` already handles serialization. Follows established pattern (see `autoZoomEnabled`, `freeFormNorthUp`, `navNorthUp`).

## Risks / Trade-offs

- **[Risk] Flag not removed on crash** — `FLAG_KEEP_SCREEN_ON` is per-window; when activity is destroyed, the flag is gone. No leak risk.
- **[Risk] Toggle visible only during navigation** — User must start navigation to change setting. Mitigation: default is on, so most users won't need to change it. If user wants to disable, they can do so after starting navigation.
- **[Trade-off] No wake lock** — `FLAG_KEEP_SCREEN_ON` only prevents screen timeout. CPU may still sleep if app is backgrounded. This is correct behavior — we only need screen-on while app is in foreground.
