# Proposal: share the route-row policy in `:core`

## Why

The phone route list does not show route *steps*: `app/ui/route/RouteRows.kt` derives **rows** from them —
informational steps never earn a row, a 20 m leg floor drops node artifacts, straight-on runs collapse, a
street-name change restated by the next manoeuvre is folded into it. The car's route description skipped that
policy entirely and mapped `state.steps` 1:1 (`auto/NavigationTemplateMapper.routeDescriptionRows` →
`stepsFromCurrentStep`), so the same route became a different list on the car:

| route | published steps | phone rows (`RouteRows`) | car rows before this change |
|---|---|---|---|
| Andorra la Vella → Escaldes-Engordany | 330 | 24 | 330 |
| Dortmund → Cologne | 1663 | 27 | 1663 |

Measured 2026-10-30 on the AAOS AVD: a tap on the route-list action reaches a `ListTemplate` at the local host
in 19–35 ms, and each row carries its own 48×48 `ARGB_8888` maneuver bitmap (9 216 B; a repeated `CarIcon` is
not collapsed — measured `two_icons=440 B` metadata + 2 × 9 216 B pixels), so the payload a car route
description is built from is ~3.1 MB for a 330-row list and ~15.8 MB for a 1663-row one, against ~0.23 MB for
the same route as the phone lists it. The 19–35 ms figure is the tap-to-template latency of that session, whose
live step list was still empty at that moment (steps are dispatched only once the position moves); it measures
the push path, not a 330-row build. The payload figures are the parcel measurement times the census's row
counts. On projection — the configuration the reported "NaviVeylin isn't responding" comes from, with a short
city route — that payload is the only structural difference from the phone variant that is proven by
measurement. The stall itself is not reproduced on the AAOS AVD (it needs a phone AVD plus the Desktop Head
Unit) and is recorded here as an open verification, not as a claim.

The policy also exists in two variants of itself: `rowTurnType(manoeuvre)` in `:app` is character-for-character
`core.turnTypeFor(manoeuvre)`, app `isDataOnly(Manoeuvre)` duplicates `core.isDataOnly(RouteStep)`, and the 200 m
close-manoeuvre window is the same native constant as `core.HINT_WINDOW_METERS` under a second name. Three
surfaces (`:app` list, `:auto` list, and the guidance helpers in `:core`) read the same rule from three places.

## What Changes

- **Move the row policy into `:core`** — `RouteRow`, `routeRows(steps)`, `MIN_ROW_LEG_METERS`, the row-merge
  window, `isDataOnly`/`readableNameChange`/`namesMatch`/`foldName` leave `app/ui/route/RouteRows.kt` and live
  next to `Manoeuvre`/`ManeuverSymbols`/`RouteStepGuidance` in `:core` (package `com.naviveylin.core`).
- **Delete the duplicates** — `app/ui/route/RouteRowSymbols.kt#rowTurnType` is removed in favour of
  `core.turnTypeFor(manoeuvre)`; the two `isDataOnly` overloads are declared once in `:core`.
- **The car route description derives rows** — `NavigationTemplateMapper.routeDescriptionRows` derives
  `routeRows(stepsFromCurrentStep(state))` instead of dropping the first N steps, so the car list is the same row
  set as the phone's; the current row is the one containing the current step, not blindly the first.
- **A car row states its own leg** — the row's distance becomes `row.distanceMeters` (the run into its manoeuvre)
  instead of `step.distanceFromStart` (cumulative from the route's start), which is what the phone row shows.
- **Spec deltas** — `route-analysis` "Rows are derived from the route's steps" becomes the one policy for both
  surfaces; `auto/navigation-view` "Route description screen" lists the derived decisions, states each row's own
  leg and no longer promises one row per published step.
- **Not breaking at the API level.** The behaviour of the car route description changes visibly (fewer rows, a
  different per-row distance); nothing in the phone variant's behaviour changes. Rollback is reverting the commit:
  no persisted state, no schema, no native symbol is touched.

Scope: both variants. The change is shared-code plus the car consumer; the phone variant only has its function
relocated.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `route-analysis`: "Rows are derived from the route's steps" — the rows are derived by one policy shared by the
  phone route list and the car route description, and a new scenario pins that both surfaces list the same row
  set.
- `auto/navigation-view`: "Route description screen" — the screen lists the route's remaining driving decisions
  (the shared policy's rows) with each row's own leg distance, and a new scenario pins that informational steps
  earn no car row either.
- `cross-variant-ui-parity`: no delta. The change moves the car *toward* the existing parity requirement (same
  element, same content); the requirement's text does not change.

## Impact

Modules and files:

- `:core` — new `core/src/main/java/com/naviveylin/core/RouteRows.kt`; `ManoeuvreTurnTypes.kt` and
  `RouteStepGuidance.kt` lose their `:app` twins' counterparts' duplication note.
- `:app` — `app/src/main/java/com/naviveylin/ui/route/RouteRows.kt` (moved), `RouteRowSymbols.kt` (`rowTurnType`
  deleted), `RoutePanelViewModel.kt` (`buildRows` imports the shared function), `RouteStepValues.kt` (unchanged
  pair, stays in `:app` because only the panel publishes it).
- `:auto` — `auto/src/main/java/com/naviveylin/auto/NavigationTemplateMapper.kt` (`routeDescriptionRows` derives
  rows), `NavigationScreen.kt` (unchanged push path), `RouteDescriptionScreen.kt` (unchanged host template).
- Tests — `:app` `RouteRowDerivationTest`, `RoutePanelLocalizedRowsTest`, `RouteSummaryRowsTest`,
  `RouteRowCensusDeviceTest` keep pinning the policy from its new module; `:auto` `RouteDescriptionScreenTest`
  gains the row-set, leg-distance and informational-step cases; `:core` gains the moved policy's cases if the
  `:app` cases cannot see it.

Guidelines affected: `guidelines/Design.md` §1 (module layering — the policy is shared behaviour, so it belongs in
`:core`), §2 (adding to the stack), `guidelines/UI.md` §1/§6 (parity between the two variants; the car list must
equal the phone list, presentation may differ) and the `route-analysis`-owning text in `guidelines/Build.md` §10k
(the census recipe now explains the car list's row count too).

Platform components: no manifest, no resource, no native symbol, no Gradle dependency changes; `:auto` and `:core`
already depend on `com.framstag.libosmscout.client`. No JNI/submodule patch is needed — the change is Kotlin only.

Verification this change owes: unit cases on the moved policy (existing), the three new car cases, one AAOS device
check that the car list shows the derived row count (not one row per step) with a bounded template time, and an
explicit note that the projection-link reproduction stays open (no DHU in the loop's reach).
