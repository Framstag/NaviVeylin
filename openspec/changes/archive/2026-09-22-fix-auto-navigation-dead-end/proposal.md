# Proposal: Fix Android Auto navigation dead end (bare map + dead back "X")

## Why

A randomly-observed Android Auto state shows a full-screen map with a single back button ("X", top-right corner) that does nothing: pressing it gives no reaction and the only escape is restarting the app on the connected phone. The state is reachable whenever the navigation screen's template is built while `isNavigating == false` — either a navigation ending under a session restored mid-navigation, or during the warmup window — and its lone back affordance is a no-op.

## What Changes

- **NavigationScreen never shows a non-exiting view when navigation is inactive.** The fallback template (one `Action.BACK` in the action strip over a bare map) is replaced with behavior that actually leaves the view: exiting the screen, popping to root, and guaranteeing a usable screen (map/browse) is shown.
- **The back "X" semantics become correct in all states.** While navigating, back stops navigation (unchanged). Not navigating → back leaves the navigation view instead of being a dead no-op.
- **The session root is never a `NavigationScreen`.** The root screen cannot be popped by the car-app `ScreenManager`, so a mid-navigation session restore must not make the navigation screen the root; navigation view is pushed on top of the map root (and restored the same way), leaving the session recoverable after navigation ends.
- **Session observer double-push guard.** `showNavigationScreen()` skips pushing when the cached navigation screen is already the current top/root screen (a same-instance push on the root is both a wedge and an exception risk that can kill the observer coroutine).
- **Spec update** to `auto/navigation-view` "Leave navigation at any time": the dead-end state must be covered by a scenario (no un-leavable navigation view exists at any time).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `auto/navigation-view` — "Leave navigation at any time" currently requires back/stop to return to the root menu during navigation; the spec gains the invariant that a navigation view with `isNavigating == false` never renders (or, if transiently rendered, is always self-exiting), covering the dead-end regression.

## Impact

- `auto/src/main/java/com/naviveylin/auto/NavigationSession.kt` — root screen choice, observer guards, `showRootScreen`/`showNavigationScreen` recovery behavior.
- `auto/src/main/java/com/naviveylin/auto/NavigationScreen.kt` — back callback semantics for the not-navigating state, self-exit on `isNavigating == false`.
- `auto/src/main/java/com/naviveylin/auto/NavigationTemplateFactory.kt` — the non-navigating template branch.
- Tests: `auto/src/test/java/com/naviveylin/auto/` (NavigationScreenTest, NavigationManagerControllerTest, RootScreenTest, new session-root/dead-end coverage).
- Design interaction: the in-flight `background-navigation-notification` change extends mid-navigation session restore — this fix must land coherently with that design (restored navigation view is pushed, never rooted).
