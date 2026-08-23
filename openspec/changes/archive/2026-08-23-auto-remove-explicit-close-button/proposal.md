# Proposal: Remove explicit back button from Android Auto navigation

## Why

The Android Auto navigation screen showed two ways to finish/cancel navigation: the implicit system back affordance (hardware/steering-wheel back, or the host's back arrow on pushed screens) and an explicit `Action.BACK` in the map action strip. The explicit BACK button on the map duplicated the implicit mechanism and was removed.

On the AAOS emulator, however, the host renders no back arrow for the navigation template — an implicit-only leave model leaves **no visible affordance** to stop navigation. The ETA/summarization card itself cannot host a button (car-app 1.7.0 `NavigationTemplate` `destinationTravelEstimate` is display-only — verified against the 1.7.0 AAR: the Builder exposes only `setDestinationTravelEstimate`, `setNavigationInfo`, `setActionStrip`, `setMapActionStrip`). The resolution: a visible stop action ("x" icon) in the navigation map action strip, next to the ETA area, with system back kept as the secondary affordance.

Free driving already follows this pattern: its map action strip shows the exit "x" action (no back/menu affordance) and system back exits — both pinned by the auto/free-driving spec — and stays unchanged.

## What Changes

- Remove `Action.BACK` from the navigation screen's map action strip (target routing); no back button on the map.
- Add a stop action (`NavigationScreenActions.stopAction` — the shared X glyph free driving already uses) to the navigation map action strip, beside the route-description action, wired to `navigationViewModel.stopNavigation()`.
- Keep system back as the secondary leave affordance: `NavigationScreen.backCallback` → `stopNavigation()` stays; the session observer still pops back to the root menu when `isNavigating` flips to false.
- Free driving: unchanged (its strip already carries the shared stop/exit "x" action; nothing to remove or add).
- Update the spec contract so the leave model (stop action + system back, no back button) is pinned (see Modified Capabilities).

## Capabilities

### New Capabilities

(none — no new behavior is introduced; an existing requirement is tightened)

### Modified Capabilities

- `auto/navigation-view`: the "Leave navigation at any time" requirement changes to pin the visible stop action ("x") in the navigation map action strip as the primary leave affordance, with system back as secondary — the map action strip SHALL NOT carry an explicit back button, and both the stop action and system back SHALL stop navigation and return to the root menu. (Scenarios: "Stop action shown on navigation map", "Stop action stops navigation", "System back still leaves with the stop action shown".)

## Impact

- `auto/src/main/java/com/naviveylin/auto/NavigationScreen.kt` — `buildTemplate()`: map action strip = stop action (X, `stopNavigation()`) + route-description action; update the class KDoc.
- `auto/src/main/java/com/naviveylin/auto/NavigationScreenActions.kt` — rename `exitAction` → `stopAction` (shared "x" glyph for free-driving exit and navigation stop); update the doc comment.
- `auto/src/main/java/com/naviveylin/auto/FreeDrivingScreen.kt` — call-site rename `exitAction` → `stopAction`; behavior unchanged.
- `auto/src/main/java/com/naviveylin/auto/AutoBack.kt` — no code change; documents why emulated hosts may render no back affordance, which motivated the visible stop action.
- `auto/src/test/java/com/naviveylin/auto/NavigationTemplateFactoryTest.kt` — map action strip assertions: two icon-only actions (stop + route-description), no `Action.BACK`.
- `auto/src/test/java/com/naviveylin/auto/NavigationScreenActionsTest.kt` — rename `exitAction` tests to `stopAction`.
- `auto/src/test/java/com/naviveylin/auto/NavigationScreenTest.kt` — unchanged: `backCallback` (system back → stop) stays the pinned contract.
- Spec delta: `openspec/specs/auto/navigation-view/spec.md` — "Leave navigation at any time" updated.
- No phone-app impact: the phone ETA-card stop button and `BackHandler` are already functional and are the only phone leave affordances.
- No native/dependency changes.
