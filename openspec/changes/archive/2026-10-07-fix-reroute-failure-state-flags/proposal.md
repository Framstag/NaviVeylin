# Proposal

## Why

A reroute that **fails** or is **cancelled** leaves the attempt's own flags set
(`NavigationEngine.kt:908` sets `isRerouting`/`isOffRoute`; the only writer that clears them is
`onRouteInstructions`, `NavigationEngine.kt:803-804`, i.e. a *successful* reroute whose new
instruction list arrives). On a stretch with no further instructions nothing clears them. The car
navigation template keys on that flag — `NavigationTemplateMapper.kt:288` returns
`loadingTrip()`, a `Trip` with `setLoading(true)` and no turn guidance — so a failed reroute can
leave the head unit showing a permanent loading screen while guidance runs from the old route. The
phone keeps the off-route red tint (`NavigationStateOverlay.kt:153`).

Found 2026-10-06 while landing `fix-reroute-lease-release`, which fixed the *lease* half of the same
failure path and deliberately left the state half out of scope (`TODO.md` §144). The spec already
states the intended behavior ("`true` while a reroute is being calculated and `false` otherwise")
but has no scenario for an attempt that does not succeed, so the defect has no case behind it.

## What Changes

- Clear `isRerouting` on **every terminal outcome of the attempt that set it**: success (existing,
  `onRouteInstructions`), failure (`onError` and the synchronous `catch` in `calculateAndStart`),
  and cancel (`cancelAcquisition` → `endCalculation`). One rule instead of three ad-hoc sites.
- On failure, **`isOffRoute` stays as it is** — the vehicle is genuinely still off route, and the
  red tint is the honest signal. It keeps being cleared by the next instruction list; a later
  change may re-derive it from the native position state if that proves insufficient.
- Add the missing scenarios to the rerouting-state requirement (failure, cancel) and state
  explicitly that a failed attempt does not clear the off-route state.
- No change to `onRouteInstructions` and `stopNavigation` (`:648-661` already resets the whole
  state, which satisfies the existing "navigation stops during rerouting" scenario).
- Additive. No behavior change on any path that succeeds today.

## Capabilities

### New Capabilities

None. The behavior belongs to an existing capability; a second capability owning the same two
flags would split one requirement across two specs.

### Modified Capabilities

- `rerouting-visual-feedback`: requirement **"Rerouting state is exposed in navigation state"**
  gains the terminal-outcome rule plus one scenario per outcome (failure, cancel); requirement
  **"Off-route state is exposed in navigation state"** gains the clarification that a failed or
  cancelled reroute attempt does not clear `isOffRoute`. No other requirement of that spec
  changes — the tint and its alpha stay as they are.

Read but deliberately **not** modified:
- `navigation-engine` — its requirement "A failed route attempt releases only the lease it took"
  already governs the sibling half of this failure path (landed by `fix-reroute-lease-release`); the
  flags are display state and belong to `rerouting-visual-feedback`.
- `off-route-indicator` — it describes the tint for a *true* off-route state, which this change
  stops claiming falsely on the `isRerouting` side only; its scenarios stay valid.
- `auto-navigation-hints` / `auto` — the car renders `loadingTrip()` correctly *given* the flag;
  the bug is the flag, not the template.

## Impact

Scope: **shared** — the navigation engine is process-scoped and serves both surfaces
(`guidelines/Design.md` §3, §8). Phone, Android Auto projection and AAOS all observe the same
`NavigationState`; no surface-specific behavior is introduced and no parity deviation arises.

| Area | File | Role |
|---|---|---|
| Engine | `app/src/main/java/com/naviveylin/navigation/NavigationEngine.kt` | `confirmReroute` (`:908`, sets), `calculateAndStart` failure handlers (`:488`, `:514`), `cancelAcquisition` (`:550`) / `endCalculation` (`:623`), `onRouteInstructions` (`:803-804`, unchanged) |
| State | `core/src/main/java/com/naviveylin/core/NavigationState.kt` (`:61-62`) | read-only; no field added |
| Phone consumer | `app/src/main/java/com/naviveylin/ui/navigation/NavigationStateOverlay.kt` (`:153`), `ui/map/MapCanvasScreen.kt` (`:2426-2427`) | read-only |
| Car consumer | `auto/src/main/java/com/naviveylin/auto/NavigationTemplateMapper.kt` (`:247`, `:288`, `:364`) | read-only; the stuck-loading symptom |
| Tests | `app/src/test/java/com/naviveylin/navigation/NavigationEngineRerouteTest.kt`, `NavigationEngineCalculationCancelTest.kt` | extended |

- **No native/JNI change**: neither a libosmscout submodule patch nor an `:osmscout-client-java`
  override. No CMake, ABI or NDK impact; no submodule SHA bump.
- No new dependency, permission, manifest or resource change; no persistence or settings change.
- **Breaking**: no. Every terminal outcome ends with `isRerouting == false`, which is what callers
  already assume.
- **Rollback**: single-file revert of `NavigationEngine.kt` plus the two test files and the spec
  delta; no state that a rollback would leave inconsistent (the flags are in-memory only).
- Guidelines touched: `guidelines/Design.md` §3 (one owner per state field), §4 (every flag write
  stays on the main dispatcher), §11 (revert-check discipline). `guidelines/UI.md` §3c is the rule
  the fix restores — no guideline text changes in this change.
