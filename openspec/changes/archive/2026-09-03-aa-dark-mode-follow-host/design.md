# Design — AA dark mode follows host

## Context

See proposal.md — Why. The AA map surface is app-drawn (`SurfaceCallback` + `AutoMapRenderer`) and always loads the stylesheet with `daylight` set; templates are host-rendered and already follow the head unit. Verified in the car lib 1.7.0 AAR: `CarContext.isDarkMode()` reads the host-attached `Configuration.uiMode`; `Session.onCarConfigurationChanged(Configuration)` delivers live updates. Native side: `client.setStyleSheetFlag("daylight", !dark)` → `DBThread.SetStyleFlag` → `LoadStyleInternal(stylesheet, flags)` reloads the variant on the DB thread; a subsequent `AutoMapRenderer.requestRender()` produces the new variant.

## Goals / Non-Goals

**Goals**
- Map surface on both AA map screens follows the host's day/night, initial state and live changes.
- One mechanism for Android Auto projection and AAOS (same `:auto` module).

**Non-Goals**
- No change to stylesheet selection, render pipeline, or phone-side `DarkModeController`.
- No phone-side sensor work (separate change: `phone-ambient-light-dark-mode`).
- No template theming (host-owned).

## Decisions

### D1: Host dark state as a flow owned by the session

`NavigationSession` holds `MutableStateFlow<Boolean> hostDark`, initialized from `carContext.isDarkMode()` at session start, updated in `onCarConfigurationChanged(Configuration)` (read `configuration.uiMode & UI_MODE_NIGHT_MASK == UI_MODE_NIGHT_YES`). Screens receive the flow via constructor (third param, defaulted for tests).

- **Alternative A (screens read `carContext.isDarkMode()` on demand)**: no push channel for live changes; screens would need polling. Rejected — `onCarConfigurationChanged` is the OS-level notification, use it.
- **Alternative B (shared singleton holder)**: global mutable state, harder to test, no lifecycle tie. Rejected — session-scoped flow matches the existing `NavigationViewModel` observation pattern.

### D2: Deduped daylight applier

New `CarDaylightApplier` mirroring `CarStyleApplier`: `apply(dark: Boolean)` pushes `setStyleSheetFlag("daylight", !dark)` only when the value changed; on native failure the marker resets so the next push retries. Screens call it from the flow collection, then `mapRenderer.requestRender()`.

- **Alternative**: fold the flag into `CarStyleApplier` (reload stylesheet with flag). Rejected — `loadStyleSheet` and `setStyleSheetFlag` are separate native ops with separate failure modes; keeping them separate preserves the existing style dedupe.

### D3: Threading

`setStyleSheetFlag` is a JNI call; the native side posts the stylesheet reload to the DB thread (`SetStyleFlag`), so calling from the main thread is safe (same pattern as phone `pushDarkPresentation`). `requestRender()` is main-safe. Flow collection on `Dispatchers.Main` (screens already use `CoroutineScope(SupervisorJob() + Dispatchers.Main)`).

### D4: Initial unknown state

`CarContext.isDarkMode()` returns false when the host has not yet sent configuration (`UI_MODE_UNKNOWN`). The flow starts at that value (daylight default) and corrects on the first `onCarConfigurationChanged`. Acceptable — the host sends configuration at session start; the correction is one re-render.

## Risks / Trade-offs

- [Host config arrives late → brief light map at night] → Mitigation: first `onCarConfigurationChanged` re-pushes; one extra render, no user-visible flicker beyond the normal startup render.
- [Flag push fails natively (style reload error)] → Mitigation: applier resets marker, retries on next change; previous variant stays (same contract as `CarStyleApplier`).
- [Overlay colors (route, pins, GPS marker) drawn with fixed colors in `AutoMapRenderer`] → Mitigation: contrast check on device in dark variant; if insufficient, follow-up change for variant-aware overlay colors (out of scope here).

## Migration Plan

Additive. Ship, verify on emulator/head unit (day/night toggle in AA developer settings), rollback = revert commit.

## Verification

- Unit: `CarDaylightApplierTest` (dedupe, retry-on-failure), session flow test (init from `isDarkMode`, update on `onCarConfigurationChanged`).
- On-device: AA emulator with host day/night toggle — map surface switches live; AAOS head unit at night — dark map from start.
- Logcat: `NaviVeylin` tag shows flag pushes.

## Open Questions

None — decisions above are sufficient; thresholds/UX live in the phone change.
