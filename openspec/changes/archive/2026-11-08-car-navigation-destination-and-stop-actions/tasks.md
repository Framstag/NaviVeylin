# Tasks

## 1. The actions

- [x] 1.1 `NavigationScreenActions`: a `changeDestinationAction` and an `endNavigationAction` builder, the shape
      `zoomInAction` already has (each takes its title, because the label is a string resource).
- [x] 1.2 `NavigationScreen`: compose the template's `actionStrip` through
      `NavigationScreenActions.navigationActionStrip` — zoom first, *end navigation* last — so the strip's shape is
      testable without a host.
- [x] 1.3 Strings: `navigation_change_destination` and `navigation_end` in `values/strings.xml` and
      `values-de/strings.xml`.

## 2. The behaviour

- [x] 2.1 *Change destination* opens the car's search (the screen the root list's `suchen` row reaches), without
      popping the navigation screen behind it — the case's tap assertion, and the screen binds it to the
      `SearchScreen` push.
- [x] 2.2 *End navigation* calls `navigationViewModel.stopNavigation()` — the call the screen's back callback
      already makes — so the session's collector emits `onNavigationEnded` and `Pop to RootScreen`.

## 3. Verification

- [x] 3.1 `NavigationScreenActionsTest#navigationActionStripCarriesTheFourActionsAndTheirTapsReachTheirCallbacks`
      — the strip's four titles in order, and a tap on each new action reaching its callback:
      `TEST-NavigationScreenActionsTest-green-after-restore.xml` (`tests="13" failures="0"`), falsified by dropping
      *end navigation* from the factory (`TEST-NavigationScreenActionsTest-revert-check-end-action-mutation.xml`,
      `failures="1"`: `expected:<4> but was:<3>`); the case cannot compile at the base (`red-on-HEAD.log`).
- [ ] 3.2 On the AAOS AVD: plan a route, change its destination from the strip, then end the session — the host
      shows the root screen and the trip updates stop. **NOT TAKEN in this pass: no device is attached**
      (`adb devices` lists none), so this change is host-verified only; recorded as the blocker rather than faked.
- [x] 3.3 The gate the delta owes — run by the gate child that follows this diff: the worker's Phase D defers the
      gate, so this worker ran none and the plan's one step is
      `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache`. That child ran it and it passed; its full
      output is `evidence/gate.log` in this change directory.
