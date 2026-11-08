# Proposal — the car's navigation view can change its destination and end the session

## Why

On 2026-11-07 an AAOS AVD pass tried to change the destination of a live car session and to end it: neither is
possible. At the base commit `b114dc61` the navigation template's action strip is built from two actions only —

`auto/src/main/java/com/naviveylin/auto/NavigationScreen.kt:575–577` (base) —
`.addAction(NavigationScreenActions.zoomInAction …)` and `.addAction(NavigationScreenActions.zoomOutAction …)`,
with the *map* action strip holding the route description. So a driver whose route is wrong has to stop the car,
reach the phone and change the destination there — the phone's navigation overlay carries a stop control
(`app/src/main/java/com/naviveylin/ui/navigation/NavigationStateOverlay.kt:267`) and the map screen reaches route
planning through its panel (`app/src/main/java/com/naviveylin/ui/route/RoutePanel.kt:152`).

The pass that found it could not reproduce a failing calculation while navigating for exactly this reason
(#739, task 7.3): there is no host UI that reaches a new destination from the navigation view.

Red case: against the base, `NavigationScreenActionsTest#navigationActionStripCarriesTheFourActionsAndTheirTapsReachTheirCallbacks`
cannot compile — `NavigationScreenActions.navigationActionStrip` does not exist
(`NavigationScreenActionsTest.kt:131: Unresolved reference 'navigationActionStrip'`), so the capability is absent;
retained as `red-on-HEAD.log`. With this change applied the case and its class pass:
`TEST-NavigationScreenActionsTest-green-after-restore.xml` — XML `tests="13" skipped="0" failures="0" errors="0"`
(`timestamp="2026-11-08T14:33:50.851Z"`). Falsified by dropping *end navigation* from the strip factory:
`TEST-NavigationScreenActionsTest-revert-check-end-action-mutation.xml` — `tests="13" failures="1"`,
`java.lang.AssertionError: expected:<4> but was:<3>`. All three are in this item's evidence directory
(`.pi/issue-loop/items/820/`); the tail carries them forward from there.

Owning requirement: this change's delta on `auto/navigation-view` — "The car's navigation view offers a way to
change the destination and to end the session". Guideline: `guidelines/UICar.md` §3 "Car-host template constraints
(platform knowledge)" owns the car-host constraints and owes no edit (see design.md).

## What changes

1. The navigation template's action strip gains **change destination** (opens the car's own search, the screen the
   root list already reaches as `suchen`, pushed over the navigation view) and **end navigation** (calls
   `navigationViewModel.stopNavigation()` — the call `NavigationScreen`'s back callback already makes — so the
   session's state collector calls `onNavigationEnded()` and `showRootScreen()`).
2. Both actions carry their titles from the module's string tables (`values`, `values-de`), resolved by the screen
   and passed to the pure strip factory, and neither is offered while the session is not started:
   `buildTemplate` returns the bare template before it builds any strip when `state.isNavigating` is false.

## What this does not change

The phone UI, the search surface, the routing engine, the notification, and the template
rebuild rate: the strip carries actions, not new templates.

## Blast radius

`auto/src/main/java/com/naviveylin/auto/NavigationScreenActions.kt` (two action builders and the strip factory),
`NavigationScreen.kt` (the strip's caller and the two click handlers: the search push and `stopNavigation()`),
`auto/src/test/java/com/naviveylin/auto/NavigationScreenActionsTest.kt` (the case), and the two string tables
(`values`, `values-de`).

surface: the strip factory's four `addAction` call sites, the two new string resources in each string table
(`values`, `values-de`), and the named case — the deriving commands and their output are in `evidence/surface.txt`.
