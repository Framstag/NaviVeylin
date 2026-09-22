# Tasks: Fix Android Auto navigation dead end

Reference: spec `specs/auto/navigation-view/spec.md`, design `design.md` (D1–D5).

## 1. Session root never a transient-mode screen

- [x] 1.1 `initialScreen()` always returns `MapScreen` (drop `isNavigating -> getNavigationScreen()` and `freeDrivingActive -> FreeDrivingScreen` branches) and verify `RootScreenTest`/session tests cover the root-is-map invariant for both idle and active states
- [x] 1.2 Add explicit free-driving restore push (when `freeDrivingActive && !isNavigating` at session start) and verify with a session test that the stack is `[MapScreen, FreeDrivingScreen]` — never a free-driving root
- [x] 1.3 Verify mid-navigation session start pushes `NavigationScreen` on top of the `MapScreen` root via the observer's first emission (extend session restore test; stack `[MapScreen, NavigationScreen]`)

## 2. NavigationScreen self-exit + correct back semantics

- [x] 2.1 Add `isNavigating == false` branch to `NavigationScreen`'s state collector: `screenManager.popToRoot()`; verify `NavigationScreenTest` covers nav-end while visible leaves to the map root
- [x] 2.2 Branch the back callback on state: navigating → `stopNavigation()`, else → `popToRoot()`; verify test that back on an inactive navigation view leaves the screen (no dead no-op)
- [x] 2.3 Verify `NavigationTemplateFactoryTest` still covers the not-navigating branch (bare template remains valid; lifetime now bounded by 2.1/2.2)

## 3. Observer double-push guard

- [x] 3.1 Track `navScreenPushed` in `NavigationSession`: `showNavigationScreen()` returns early when the cached navigation screen is already on the stack; `showRootScreen()` clears it; verify with a double-fire test that only one `NavigationScreen` ever sits on the stack
- [x] 3.2 Verify `NavigationManagerControllerTest`/session-flow tests still pass with the guard (nav restart while screen up re-renders in place, no re-push)

## 4. Option: template factory polish (D5)

- [x] 4.1 OPTIONAL — evaluate replacing the lone strip `Action.BACK` in the not-navigating template with a working "Back to map" action on target hosts; skip if host strip-BACK already dispatches (D3 makes either path leave); document decision in the change

## 5. Integration verification

- [x] 5.1 Run `./gradlew :auto:test` and verify the full auto test suite passes
- [x] 5.2 Run `./gradlew :app:assembleMobileDebug` and verify the app builds
- [x] 5.3 Manual AA check: mid-navigation head-unit app switch → let navigation end → verify browse map root is reachable and no bare map + X dead end remains; verify same for free-driving restore + exit — **done (user verification, 2026-09-22)**: manual Android Auto run after the stability change series — the browse map root is reachable after navigation ends, no dead end, free-driving restore + exit behaved correctly, no crash.
