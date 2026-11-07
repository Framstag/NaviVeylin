# Design: share the route-row policy in `:core`

Motivation and scope: see `proposal.md` "Why"/"What Changes". The contract is `specs/route-analysis` and
`specs/auto/navigation-view` in this change.

## Context

- Module direction: `:app → :auto → :core`, and `:core → :osmscout-client-java`. `:auto` cannot see `:app`, so a
  policy `:auto` must agree with has exactly one home today: `:core`.
- The row policy lives in `app/src/main/java/com/naviveylin/ui/route/RouteRows.kt` (derivation, filter, 20 m floor,
  200 m merge window, name folding) and is pinned by `RouteRowDerivationTest`, `RoutePanelLocalizedRowsTest`,
  `RouteSummaryRowsTest` and the device census `RouteRowCensusDeviceTest` (`guidelines/Build.md` §10k).
- `:core` already owns the pieces the policy is built from: `Manoeuvre`, `manoeuvreFromStep`, `ManoeuvreLabels`,
  `ManeuverSymbols`, `turnTypeFor(manoeuvre)`, `StringResolver`, `RouteStepGuidance`.
- The car consumer: `auto/.../NavigationTemplateMapper.kt#routeDescriptionRows(carContext, state)` maps
  `stepsFromCurrentStep(state)` 1:1 to `RouteDescriptionRow(turnType, title, text, isCurrent, roundaboutExitNumber)`
  and titles each row from `step.distanceFromStart`.
- Measurements from the 2026-10-30 explore session (AAOS AVD, real route, maps provisioned): the route-list
  action reaches a `ListTemplate` at the local host 19 ms after the tap; the *live* step list of that session was
  empty until a position move dispatched it (`guidance dispatch: list steps=425`), so the diagnostic reads
  `rows=0 steps=0` on a parked vehicle and the 19 ms measures the push path rather than a populated build. Each
  row carries its own 48×48 `ARGB_8888` maneuver bitmap (9 216 B, not collapsed when repeated — the parcel
  measurement). Census row counts: 330 steps → 24 phone rows; 1663 steps → 27 phone rows.

## Goals / Non-Goals

- Goal: one row-derivation policy, in one module, used by both route lists, with the phone variant's behaviour
  unchanged.
- Goal: the car list becomes the same row *set* as the phone's, with each row's own leg distance.
- Goal: the car list gains a coordinate-free row-count diagnostic so the spec scenario is measurable on device.
- Non-goal: changing the phone list's rows, wording, ordering, highlighting or leg values.
- Non-goal: claiming the projection stall is fixed — this change removes the payload growth by removing rows; the
  projection reproduction itself needs a phone AVD plus the Desktop Head Unit.
- Non-goal: capping the list at an arbitrary row count, or adding a "and N more" row.

## Decisions

### D1 — The policy moves into `:core` (chosen)

Alternatives:

1. **`:auto` depends on `:app`** — impossible without inverting the direction `:app → :auto`; the car service is
   shipped inside `:app`, but the screens are `:auto`. Rejected.
2. **Duplicate the filter in `:auto`** — the drift this change exists to remove; two filters would diverge on the
   next census finding (`informativeEarnsRow()` is a single predicate *because* policy changes must land once).
   Rejected.
3. **Move the derivation into `osmscout-client-java` / native** — wrong layer: the rule is presentation policy over
   published steps, and the bridge module is the JNI boundary (`guidelines/Design.md` §5). Rejected.
4. **`:core` next to `Manoeuvre`/`ManeuverSymbols`** — chosen: `:core` is the shared behaviour module
   (`guidelines/Design.md` §1), it already owns every input the policy reads, and both consumers already depend on
   it.

### D2 — What moves is the whole policy, not one function

Chosen: `RouteRow`, `routeRows(steps)`, `MIN_ROW_LEG_METERS`, the merge window, `isDataOnly`, `readableNameChange`,
`namesMatch`, `foldName` move together into `core/src/main/java/com/naviveylin/core/RouteRows.kt`.

Alternatives:

1. **Move only `routeRows`, leave `RouteRow` in `:app`** — then `:auto` would map core rows into app rows it cannot
   see, or `:auto` would declare a second row type. Rejected.
2. **Move only the filter predicate and let each surface keep its own loop** — two loops over one predicate still
   drift on merging, folding and leg attribution. Rejected.
3. **Expose the policy as an interface injected through Hilt** — a pure function over a list needs no DI; an
   interface would only add a seam nothing tests through. Rejected.

`RouteStepValues` stays in `:app` (only the panel publishes it); `RouteRow.values` is dropped and the panel builds
the pair from `distanceMeters`/`durationSeconds`, so `:core` gains no `:app` type.

### D3 — The duplicates are deleted, not aliased

- `app/.../RouteRowSymbols.kt#rowTurnType(manoeuvre)` is character-identical to `core.turnTypeFor(manoeuvre)`;
  its callers (`RoutePanelViewModel.toDisplay`) use the core function and the file is deleted.
- `app/.../RouteRows.kt#isDataOnly(Manoeuvre)` and `core/.../RouteStepGuidance.kt#isDataOnly(RouteStep)` are the
  same rule; `:core` declares both overloads in one place (same `INFORMATIVE`/`UNKNOWN` set).
- The 200 m window stays **two named constants** — `HINT_WINDOW_METERS` (announcement) and
  `ROW_MERGE_WINDOW_METERS` (row merge) — each KDoc naming the shared native origin
  (`osmscout::client::RouteStepBuilder::NextNextWindowMeters`) and stating that a divergence needs a measured
  reason. Alternative rejected: one neutral constant (it would silently couple an announcement bound to a layout
  rule).

### D4 — The car derives rows from the *remaining* steps

Chosen: `routeRows(stepsFromCurrentStep(state))`, and row 0 is the current row.

Alternatives:

1. **Derive rows for the whole route, then drop rows that end before the current step** — the retained first row
   would carry legs of steps already driven, so its distance would not be its own leg. Rejected.
2. **Derive for the whole route and scroll the host list to the current row** — a `ListTemplate` has no scroll
   offset; the host starts at the top. Rejected.
3. **Derive on the remaining steps** — chosen: the first row starts at the current step, every row's leg is the run
   into its own manoeuvre, and the list starts at the driving decision in hand.

Consequence to keep in the tests: when the current step is facts-only (`INFORMATIVE`), it earns no row, so row 0 is
the next real decision — that is the row the driver is approaching and the one marked current.

### D5 — A row's distance is its own leg

Chosen: `row.distanceMeters`, the run from the previous row's manoeuvre into this row's.

Alternatives:

1. **`step.distanceFromStart`** (today's car value) — cumulative from the route's start, so row 5 shows the total
   distance driven plus its own leg, and the number grows monotonically; it also has no meaning once the route is
   being navigated. Rejected.
2. **Live remaining distance (`RouteStep.remainingMeters`)** — published only for the current/next step, so every
   later row would show "now". Rejected.
3. **Phone parity value (`row.distanceMeters`)** — chosen: it is the value the phone row shows and the one the row
   merges fold; wording risk is under "Risks".

### D6 — The mapper keeps its signature

`routeDescriptionRows(carContext, state): List<RouteDescriptionRow>` stays the seam: it is testable under
Robolectric with a `CarContext` (`RouteDescriptionScreenTest` already does), the screen stays a thin template
builder, and `:auto` needs no new type. Alternative rejected: return core `RouteRow`s to the screen and localize
there (moves string resolution into the screen and away from the existing cases).

### D7 — No arbitrary cap

The derived count is bounded by the route's manoeuvres (24–34 rows measured on two real routes); a cap would hide
driving decisions the spec promises to the destination, and the payload problem the cap would "fix" is the step→row
multiplication this change removes. Rejected: `take(N)` plus a trailing "and N more" row.

### D8 — Threading and lifecycle

Unchanged and deliberate: the derivation runs synchronously inside `onGetTemplate` on the car-app main thread,
exactly where the 1:1 mapping runs today; it is O(steps) string work with no I/O, no native call (`RouteStep` is a
plain record) and no bitmap creation beyond the icons the mapper already cached per (turn type, exit ordinal). The
measured cost of today's 330-row build (which also built 330 icons) is 19–35 ms, and the derived list is an order of
magnitude smaller. `RouteDescriptionScreen`'s `observeJob`, `startObserving`/`stopObserving` and its
`scope.cancel()` in `onDestroy` are untouched; no new coroutine, dispatcher or observer is introduced, so
`auto/screen-observation` and `car-host-fault-isolation` keep holding by construction.

### D9 — Measurement the change owes

- Unit: `:app` policy cases unchanged (they now exercise `:core`), `:auto` `RouteDescriptionScreenTest` gains the
  three new cases plus the row-set case that pins D1's contract.
- Device (AAOS AVD, `guidelines/Build.md` §10a recipe): one coordinate-free line from the screen —
  `Diag/SCREEN: route description rows=<n> steps=<m>` — read with `adb logcat -s NaviVeylin` after tapping the
  route-list action on a live navigation **after the position has moved** (the live step list is dispatched only
  then; a parked vehicle yields `rows=0 steps=0`, which is not a row-policy result). Expected: `rows` far below
  `steps`, the same factor the panel's census shows; the template receipt delta
  (`CarApp.H.Tem: Host received new template`) stays in the tens of milliseconds.
- Projection: not reproducible in this environment; recorded as an open question rather than asserted.

## Risks / Trade-offs

- **The car list stops showing facts-only nodes** (max speed, lanes, way type, crossing, via) → mitigation: the
  phone variant made the same call on measured evidence (`RouteRows.kt` KDoc: 302 of 330 and 1631 of 1663 steps are
  informational) and the spec scenario states it; a driver wanting those facts reads the map, and a future
  "details" affordance is a separate change.
- **"in <distance> auf <street>" now means leg length, not distance ahead** → mitigation: the wording is reviewed
  in the tasks (a leg distance is what the phone row shows); if the label reads wrong on device it becomes a
  follow-up wording change, not a reason to keep the cumulative number.
- **Moving the policy breaks `:app` imports or a test's package-private helpers** → mitigation: the helpers become
  `internal` in `:core`; the `:app` cases keep their names and are re-pointed in one task, and the `:app` suite is
  run before the car change.
- **Deleting `rowTurnType` changes a glyph** if the two functions ever differed → mitigation: the change starts with
  a diff proving they are identical, and `NavigationTemplateMapperTest`'s icon cases plus `RouteDescriptionScreenTest`
  must stay green.
- **Deriving on the remaining steps can start the list at a facts-only step's successor** → accepted and pinned by
  the "current step highlighted" behaviour plus a case that the marked row is the next decision.
- **The projection stall is not proven fixed** → mitigation: proposal and design both say so; the change's evidence
  is the row count and the payload reduction, and the projection reproduction is an open question with its own rig.

## Migration Plan

No persisted state, no schema, no wire format, no native symbol. Deploy order inside one commit set: move the policy
to `:core` (phone unchanged, suite green) → re-point `:app` → change `:auto` → specs/guidelines. Rollback: revert the
commit set; the car list returns to one row per step with the old distances.

## Open Questions

- Does the projection stall disappear with the reduced row set? Needs a phone AVD plus the Desktop Head Unit; not
  answerable in this environment.
- Should a car row also show its leg's duration (phone rows do)? Deferrable, no spec or approach change.
