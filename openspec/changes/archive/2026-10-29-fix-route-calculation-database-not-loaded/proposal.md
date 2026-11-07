# Proposal — fix-route-calculation-database-not-loaded

## Why

**The condition.** A route calculation issued right after `openDatabases` can find the router's database
instance list still empty and is reported with the reason a genuinely unusable map database uses.

- `libosmscout-client-java/src/OSMScoutClient.cpp` (`openDatabases`) publishes the set change to the DB
  thread's job queue with `data->dbThread->OnDatabaseListChanged(registration.paths)` and returns;
- the queued job clears and repopulates `databases` under its own write lock
  (`libosmscout-client/src/osmscoutclient/DBThread.cpp`, `Async<bool>(…)` around `databases.clear()`);
- both route-calculation branches (`calculateRouteWithObjectsWithProfile`, `calculateRouteWithObjectsAsync`)
  read that list inside `RunSynchronousJob`, and on an empty list reported
  `naviveylin::kRouteFailureRoutingDataUnavailable`;
- the app maps that kind to `FailureReason.ROUTING_DATA_UNAVAILABLE`, which
  `NavigationFailure.hasMapManagerAction()` answers "yes" for and which reads "Routing data is not available
  for one of the installed maps".

**Root cause, one sentence.** The bridge reports *the list is still empty* with the kind that means *the
routing data cannot be used*, so a condition a retry clears is worded as a map-data defect and offered the
map manager.

**Evidence.** Owner's recorded decision on #131 (2026-10-27T17:25:15Z): "Option 1", a distinct native failure
kind. Measured precondition: 2026-10-13, a 416-point sweep of Dortmund's old town
(`nordrhein-westfalen-27-20260820-0826`, `emulator-5556`) failed at its first two points with
`calculateRouteAsync: no databases loaded` and at none of the 414 after them, with the sweep's own client
having called `openDatabases` immediately before the run.

**Red case** (`evidence/red.log`, 2026-10-29): with the app's mapping of the new kind removed — the pre-fix
state of that line — `RoutingFailureWordingTest.notYetLoadedRoutingDataIsNotUnusableRoutingData` fails with
`expected:<DATABASE_NOT_LOADED> but was:<UNKNOWN>` and
`RoutePanelViewModelFailureReasonTest.aCalculationThatOutrunsTheDatabaseOpenOffersNoAction` fails the same
way; the tally is `21 tests completed, 2 failed`.

**The single fix and the requirement it serves.** The empty-list branches report a kind of their own, and the
app words it as "still loading, try again" with no map-manager action. The defect contradicts
`routing-failure-reporting` — *A reason whose cause the user can act on offers the action* (the action
offered is the wrong one) and the closed-set reason of *A failed route calculation reports a reason*; the
change adds one requirement stating the distinction.

**Surface.** `RoutingFailureWordingTest` (phone wording and action), `RoutePanelViewModelFailureReasonTest`
(the panel carries the cause), `RoutingFailureNoticeTest` (the car shows the same label, no phone referral),
and the module's `Tests/src/RouteFailureKindTest.cpp` (the number that crosses the boundary). No shipped case
is rewritten; three are extended with one case each and one array grows by a row.

**Additive or breaking.** Additive: a new kind (`7`), a new `FailureReason` member and a new string resource.
An older native library never sends kind 7; a newer one against an old app reports `UNKNOWN`, which is the
unspecified-failure wording. **Rollback:** revert the superproject commit and the gitlink with it
(`5b14e475f`); nothing migrates.

**Native/JNI.** A minimal, upstreamable patch in the libosmscout submodule (kind constant, cause member,
mapping case, the two bridge branches, the Java mirror) plus the pin bump; no local override in
`:osmscout-client-java`.

## What Changes

- The module gains `kRouteFailureDatabaseNotLoaded=7` / `RouteFailureCause::DatabaseNotLoaded` and reports
  it at both empty-list branches; the branches where the routing service cannot be opened keep the
  unusable-routing-data kind.
- The app gains `FailureReason.DATABASE_NOT_LOADED`, its mapping from `RouteCallback.KIND_DATABASE_NOT_LOADED`,
  and one wording in `en`/`de` that asks for a retry; `hasMapManagerAction()` stays false for it, so neither
  the phone nor the car offers the map manager.
- The submodule pin moves to the commit carrying the module half.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `routing-failure-reporting` — one ADDED requirement: a database list that is still being populated is
  reported as not loaded yet, is not worded as unusable routing data, and offers no map-manager action.

## Impact

- `app/src/main/cpp/libosmscout` — the pin (module commit `client-java: a calculation that outruns the
  database open says so`, on `naviveylin-local`), carrying the kind, the two bridge branches, the Java
  mirror, the module's spec delta and its `Tests/src/RouteFailureKindTest.cpp` row.
- `core/src/main/java/com/naviveylin/core/NavigationFailure.kt` — the reason and its wording/action rule.
- `core/src/main/res/values/strings.xml`, `core/src/main/res/values-de/strings.xml` — the wording.
- `app/src/main/java/com/naviveylin/navigation/NavigationFailureMapping.kt` — the kind mapping.
- `app/src/test/java/com/naviveylin/ui/route/RoutingFailureWordingTest.kt`,
  `app/src/test/java/com/naviveylin/ui/route/RoutePanelViewModelFailureReasonTest.kt`,
  `auto/src/test/java/com/naviveylin/auto/RoutingFailureNoticeTest.kt` — the cases.
- `guidelines/Design.md` §5 and `MapRendering.md`/`UI.md` need no restatement: no rendering or surface
  layout changes, and the parity rule (same label on both surfaces) is what the car case asserts.
