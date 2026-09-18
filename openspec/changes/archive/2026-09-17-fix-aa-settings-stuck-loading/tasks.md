## 1. Shared settings-loading guard (spec: auto/preferences)

- [x] 1.1 Implement `SettingsLoadGuard` in `:auto`: a host-free helper that owns the recover-from-loading logic — one-shot watchdog decision (still loading after N ms → re-invalidate), capped re-invalidate attempts per screen start (MapScreen `surfaceRefreshAttempts` precedent), guarded `invalidate()` (any throw must not kill the load coroutine), and the error-state transition (load failure or repeated watchdog expiry → error row with retry). Pure Kotlin, no `CarContext`/host dependency.
- [x] 1.2 Add a screen-lifecycle observer factory alongside the guard: `onStart` → re-invalidate (fresh render on re-visibility), `onDestroy` → cancel the screen coroutine scope (prevent post-destroy observer fires).
- [x] 1.3 Unit tests for the guard and lifecycle factory (plain JVM / Robolectric): watchdog fires only when still loading; attempt cap prevents hot invalidate loops; `invalidate()` throwing is swallowed and triggers the capped retry; repeated expiry transitions to error state; `onStart` triggers re-invalidate; `onDestroy` cancels the scope.

## 2. Settings screens (spec: auto/preferences)

- [x] 2.1 Wire `SettingsLoadGuard` into `PreferencesScreen`: replace the single-shot `init { load(); invalidate() }` with a guarded load — placeholder only while the first load is in flight, watchdog re-invalidate, guarded invalidate, error row with Retry on failure, lifecycle observer (onStart re-invalidate, onDestroy scope cancel).
- [x] 2.2 Update/extend `PreferencesScreenTest`: existing toggle tests keep passing; new tests cover watchdog re-invalidate decision, error-row rendering with Retry, retry re-loads, and guarded invalidate (no crash when the screen is not attached to a host).
- [x] 2.3 Wire the guard into `VehicleAnchorPickerScreen` (same loading pattern, spec: auto/preferences covering the picker screens).
- [x] 2.4 Wire the guard into `OverspeedDeltaPickerScreen` (same).
- [x] 2.5 Add unit tests for both picker screens' guarded loading (Robolectric): loading → content, watchdog path, error row with Retry.

## 3. Guideline (supersedes note in guidelines/UI.md)

- [x] 3.1 Add the car-screen loading rule to `guidelines/UI.md` (Android Auto section): settings/loading screens must self-recover — never trust a single `invalidate()`; watchdog + guarded invalidate + re-render on resume is the required pattern.

## 4. Verification (spec: auto/preferences)

- [x] 4.1 Build `:auto` (and full app) for the mobile flavor and run the `:auto` unit tests — compile clean, all tests pass (use the run-tests / build-app skills).
- [x] 4.2 On-device verification on a real head unit / car-connected phone (emulator fallback): open Preferences and both pickers during a cold-start AA session; confirm rows appear within the recovery window even when the first refresh is dropped; confirm the error row + Retry path via a load-failure injection (or logcat verification of the watchdog firing); check `adb logcat -s NaviVeylin SESSION` for the guard's log lines. — **verified 2026-09-17 on head unit: Preferences and both pickers open reliably after cold start; no stuck loading observed.**
