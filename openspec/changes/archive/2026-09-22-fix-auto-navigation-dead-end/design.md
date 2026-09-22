# Design: Fix Android Auto navigation dead end

See `proposal.md` — Why. Spec: `specs/auto/navigation-view/spec.md` ("Leave navigation at any time").

## Context

Three cooperating defects produce the stuck "map + dead X" view:

1. `NavigationTemplateFactory.buildNavigationTemplate(false, …)` returns a bare `NavigationTemplate` whose only chrome is one `Action.BACK` in the action strip (top-right). The map surface still renders, so the user sees a full-screen map + lone X.
2. `NavigationScreen`'s back callback calls `stopNavigation()` unconditionally — a no-op when `isNavigating` is already false. So the X (routed via the back dispatcher) does nothing.
3. `NavigationSession.initialScreen()` returns `NavigationScreen` (or `FreeDrivingScreen`) as the **session root** when that mode is active at session start. The car-app `ScreenManager` cannot pop the root; when navigation ends the only recovery (`popToRoot`) therefore cannot remove the dead screen, and the single X event is already consumed by the no-op callback. Restart required.

The in-flight `background-navigation-notification` change deliberately keeps the process alive across head-unit app switches (session restore mid-navigation) — exactly the path that reaches defect 3.

## Goals / Non-Goals

Goals:
- No user-visible un-leaveable navigation view; every visible back affordance leaves.
- Session root is always leaveable (`MapScreen`).
- Restored modes (navigation, free driving) are pushed on top, never rooted.
- No duplicate navigation screens on the stack.

Non-Goals:
- Changing host-side pan-mode or ETA-card behavior.
- Reworking the navigation surface renderer.
- Touching the phone app navigation state machine (only its consumption in the car session changes).

## Decisions

### D1: Session root is always the browse map

`initialScreen()` drops the `isNavigating -> getNavigationScreen()` branch and the `freeDrivingActive -> FreeDrivingScreen` branch; it always returns `MapScreen`. Restored modes are pushed afterwards:

- Navigation active at session start: the existing observer (started before any restore work in `onCreateScreen` / `onWarmupComplete`) emits its first value (`true`) and `showNavigationScreen()` pushes — now cleanly on top of a leaveable root.
- Free driving active at session start: explicit `push(FreeDrivingScreen(carContext))` in the restore path, only when `!isNavigating` (modes are mutually exclusive).

Rationale: the root-pop restriction makes *any* transient-mode root un-recoverable. MapScreen is the only permanent root. This also fixes a latent sibling bug: `FreeDrivingScreen` restore-as-root makes its own "Exit free driving" `screenManager.pop()` pop the root (throw or silent no-op depending on library version).

### D2: NavigationScreen self-exits when navigation is inactive

In its state collector, when `isNavigating` flips to `false`, the screen calls `screenManager.popToRoot()` (idempotent; no-op when stack already at the MapScreen root). This covers the window where the session observer is not yet collecting (warmup), which was the missed-recovery path. The session observer keeps its own `showRootScreen()` — both firing is safe (`popToRoot` twice = same result).

### D3: Back semantics branch on navigation state

`NavigationScreen`'s back callback becomes:

```
if (navigationViewModel.state.value.isNavigating) stopNavigation()
else screenManager.popToRoot()   // leave the (transient) dead-end view
```

While navigating, back still stops navigation (spec behavior unchanged: stop → observer pops to root). When already inactive, back leaves the view instead of no-op. This makes the fallback template's lone `Action.BACK` functional in every state.

### D4: No duplicate navigation screen pushes

The session tracks a `navScreenPushed` flag: `showNavigationScreen()` returns early if the cached instance is already on the stack; `showRootScreen()` clears it. Rationale: `NavigationScreen` collects state continuously, so a navigation restart while the screen is up needs no re-push — the screen re-renders in place (its collector invalidates). The flag replaces a non-existent public "top screen" getter on `ScreenManager` (car-app 1.7 has none) and removes the previous same-instance-push-on-root hazard.

### D5: Template factory keeps the degenerate branch but it is now always transient

`buildNavigationTemplate(false, …)` still returns the constrained bare template (the builder requires at least one strip; a message pane would flash). Its lifetime is bounded: D2 exits the screen promptly, D3 makes the X leave even during the flash, D1 prevents it from ever being the root. **Decision (task 4.1): keep the strip `Action.BACK` as-is.** The strip BACK dispatches through `OnBackPressedDispatcher` to the screen's callback — confirmed empirically by the dead-X report itself (the X had no direct pop effect, it was routed into the app's no-op handler). D3 now makes that callback leave in every state; replacing a host-standard back affordance with a custom action would add cross-host rendering variance for no functional gain.

## Risks / Trade-offs

- [Double `popToRoot` races the pushed free-driving restore] → Restore push happens only when `!isNavigating`; `popToRoot` and push both run on the session Main-thread scope, no interleave.
- [Nav restart while screen up leaves stale route visuals] → Existing collector re-renders route/state; unchanged behavior.
- [Host renders strip `Action.BACK` as direct pop (not dispatch)] → Then the transient X pops the screen directly, which also leaves; either path satisfies the invariant.
- [Free-driving restore push fights the observer's navigation push on a flickering state] → Modes are mutually exclusive by provider design (`setFreeDriving` cleared on navigation start); restore push is gated on `!isNavigating` at push time.

## Migration Plan

No data migration. Rollback: revert the session/screen changes; the previous build's defect returns but nothing new breaks. Tests must pass before merge: `:auto:test` (NavigationNavigationScreenTest, RootScreenTest, NavigationTemplateFactoryTest, FreeDrivingScreenTest, new session-restore coverage).

## Open Questions

None — the root-pop uncertainty is design-neutralized by D1 (the code no longer depends on the library's pop-of-root behavior).
