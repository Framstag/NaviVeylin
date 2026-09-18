## Context

See proposal.md — Why. Current state: `PreferencesScreen`, `VehicleAnchorPickerScreen`, and `OverspeedDeltaPickerScreen` each render a single "Loading..." placeholder row until one async `settingsProvider.load()` completes, then call `invalidate()` exactly once. `invalidate()` is a remote host call (`IAppHost.invalidate()`, contract `@throws HostException`): on real car hardware it can fail or be dropped (busy template pipeline during cold-start native-client init — the trigger documented in the in-flight change `fix-aa-renderer-init-off-main`). None of the three screens has a timeout, retry, re-render-on-resume, error state, or coroutine-cancellation-on-destroy.

Relevant specs: `auto/preferences` (delta in this change). Constraints from guidelines/Design.md: car-app work stays off the main thread where it can block template delivery; UI state flows through the screen's `Dispatchers.Main` scope; verification = unit tests + on-device logcat.

## Goals / Non-Goals

**Goals:**
- Settings screens self-recover: a dropped/failed template refresh cannot leave a placeholder stuck; load failure shows an error with retry.
- Minimal, reviewable change confined to the three affected screens (+ a small shared helper if it deduplicates).
- Keep the existing async pattern (init → load → invalidate); do not restructure the session (that is the Layer 2 follow-up).

**Non-Goals:**
- Shared `AutoSettings` StateFlow on `NavigationSession` (Layer 2) — separate hardening change, noted in proposal.md Impact.
- Hardening sibling screens (`FavoritesScreen`, `SearchHistoryScreen`, `AddressBookScreen`, `SearchScreen`) — same pattern, out of scope; they inherit no benefit here.
- Chasing the host's dropped invalidate root cause inside the car-app library.

## Decisions

### D1: Recovery = one-shot watchdog + guarded invalidate + re-invalidate on resume
The load attempt gets a short watchdog (e.g. 2 s). If the placeholder is still showing when it fires, the screen re-invalidates once. Every `invalidate()` is wrapped in try/catch so a `HostException` cannot kill the load coroutine; a failure triggers a single delayed retry (backoff, capped attempts — same cap pattern as `surfaceRefreshAttempts` in `MapScreen`). A lifecycle `onStart` observer re-invalidates so any screen re-entry repaints from current state.

- Chosen over **D1-alt A: load synchronously inside `onGetTemplate`** — makes file IO (provider reads `settings.json`) run on the car-app main thread; violates guidelines/Design.md threading, and the loading state becomes unrepresentable (a slow FS would block template delivery itself). Rejected.
- Chosen over **D1-alt B: template-level `setLoading(true)`** (`SearchScreen` pattern) — cosmetic; the host spinner still needs a subsequent successful `invalidate()` to clear, and a dead app-side state would then show an eternal host spinner instead of an actionable error. Rejected for the placeholder, though the pickers may switch to `setLoading(true)` for the brief window — optional cosmetic, not required by spec.
- Watchdog is strictly one-shot per load attempt, and re-invalidate is capped per screen start, mirroring the existing `MAX_SURFACE_REFRESH_ATTEMPTS` precedent — no hot invalidate loop when the host is persistently busy.

### D2: Load-failure state = explicit error row with retry
On load failure (or repeated watchdog expiry): the list shows one "Settings unavailable" error row with a Retry action; back navigation remains available. Retry re-runs the same guarded load. Placeholder branch becomes a pure "first load in flight" state; it can no longer be terminal.

- Chosen over an empty list (silent failure — the driver cannot tell error from no-settings and has no recovery path) and over popping the screen automatically (loses context; Retry-on-screen is the established car pattern).

### D3: Shared helper, not per-screen duplication
Extract the watchdog/guard/re-invalidate-on-resume logic into one small helper (e.g. `SettingsLoadGuard` in `:auto`) plus a screen-lifecycle observer factory; the three screens wire it with their provider and placeholder predicate. Pure-decision logic (when to re-invalidate, when to declare failure, capped retry math) lives in functions without a host dependency so Robolectric/plain-JVM tests can drive them deterministically.

- Chosen over duplicating the guard inline in all three screens (three copies drift — exactly how the current single-shot pattern reproduced across screens).
- Helper stays in `:auto` (car-only concern); nothing in `:core` changes.

### D4: Concurrency & lifecycle
All screen-side work stays on the screen's existing `CoroutineScope(SupervisorJob() + Dispatchers.Main)`; the provider's file IO already hops to `Dispatchers.IO` inside `SettingsStorage`. The watchdog runs on the same Main scope (`withTimeoutOrNull`). Each screen gains: lifecycle `onStart` → re-invalidate; `onDestroy` → cancel scope (currently missing in `PreferencesScreen`) so observers cannot fire after destroy. No new dispatchers, no executor.

## Risks / Trade-offs

- [Repeated invalidate when the host is persistently busy] → one-shot watchdog + capped retry per screen start (MapScreen precedent); the host's own back pressure bounds cost.
- [Robolectric cannot exercise a real host, so the dropped-invalidate path stays device-only] → all decision logic extracted host-free and unit-tested (timeout math, cap, error transition); on-device verification checklist includes a forced-delay provider variant in logcat.
- [Overlap with in-flight `fix-aa-renderer-init-off-main`] → disjoint files (that change: MapScreen/FreeDriving/NavigationScreen/DetailScreen; this change: PreferencesScreen + two pickers). This change removes the *screen-side* wedge regardless of that change's outcome; no ordering dependency.
- [Scope-cancel on destroy races a save in flight] → cancel only cancels the render/watchdog work; the provider's `save` completes on its own IO dispatch; screen field writes after cancel affect only a dying instance (same guarantee as existing screens).

## Migration Plan

Additive car-side change; no persisted-format change, no ABI change. Rollback: revert the commit; screens return to today's single-shot loading.

## Open Questions

None — the Layer 2 (shared settings flow) follow-up is a deliberate non-goal, not an unresolved decision.
