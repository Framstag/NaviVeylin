# Design — fix-route-calculation-database-not-loaded

## The decision the change implements

The owner recorded it on #131 (2026-10-27T17:25:15Z): **the empty list gets a failure kind of its own**. This
document does not re-open it; it records the alternatives the root cause eliminated and where the seam sits.

## Alternatives eliminated by the root cause

**A. Wait inside `openDatabases` for the set change to complete** (the future `OnDatabaseListChanged` already
returns is what `close()` waits on). Removes the race for every caller, but blocks the caller across a full
database re-open — the same call that registers a map set while a map is drawn — and contradicts
`native-database-open`'s *Registration does not stall the render path*. It also hides the condition instead of
reporting it: a caller still cannot tell "not yet" from "never".

**B. Retry the calculation once on `ROUTING_DATA_UNAVAILABLE` in the app.** Host-testable and small, but the
surface keeps the wrong message for a retry-clearable condition, a genuine open failure pays a second attempt,
and the race stays unpublished. Rejected by the owner as the fallback, not the fix.

**C. Leave it and retire #131 as accepted risk.** Costs nothing and leaves the map manager offered for a
condition a retry clears — the defect the item reports.

**Chosen: A distinct kind.** The caller learns which of the two facts holds, the surface words each one, and
no call blocks.

## Where the seam sits

- **Native.** `libosmscout-client-java/src/route_failure_kind.h` gains
  `kRouteFailureDatabaseNotLoaded=7`, `RouteFailureCause::DatabaseNotLoaded` and its `RouteFailureKind` case.
  The two `databases.empty()` branches in `libosmscout-client-java/src/OSMScoutClient.cpp` report it; the
  `!routingService->Open(profileBuilder)` branches keep `kRouteFailureRoutingDataUnavailable`, which is what
  that kind means.
- **Java mirror.** `RouteCallback.KIND_DATABASE_NOT_LOADED = 7`. The number is what crosses the boundary, and
  the module's `Tests/src/RouteFailureKindTest.cpp` pins both sides.
- **App.** `FailureReason.DATABASE_NOT_LOADED` in `core/src/main/java/com/naviveylin/core/NavigationFailure.kt`,
  mapped in `app/src/main/java/com/naviveylin/navigation/NavigationFailureMapping.kt`, worded from
  `core/src/main/res/values{,-de}/strings.xml`. `hasMapManagerAction()` gains nothing: a retry is the whole
  fix, so no surface offers the map manager, and the car does not name the phone either — the two surfaces
  show the same label, which is the parity rule of `guidelines/UI.md` §1.

## Threading and lifecycle

No new threading: the branch runs inside `RunSynchronousJob` on the DB thread, as both existing failure
branches do, and the callback reaches Java on the JNI thread exactly as before. The app-side change is a
`when` arm over an `int` and an enum member; it starts no work and holds no state.

## Verification

- **Host cases** (run 2026-10-29, `evidence/green.log`): `RoutingFailureWordingTest` 13 cases,
  `RoutePanelViewModelFailureReasonTest` 8, `RoutingFailureNoticeTest` 5 — 26 cases, 0 failures. The red run
  of the same two app classes is `evidence/red.log` (`21 tests completed, 2 failed`).
- **Module mapping** — the header is dependency-free, so a probe with the same assertions as
  `Tests/src/RouteFailureKindTest.cpp` compiles and runs on the host; it is recorded in the module change's
  `verification.md`. The Catch2 suite itself is not run here: its dependency is absent from the project's
  vcpkg tree.
- **On device, owed:** a calculation issued immediately after `openDatabases` on
  `nordrhein-westfalen-27-20260820-0826` reports the new kind and the phone shows the retry wording with no
  map-manager action; the 416-point Dortmund sweep is the reproduction. `guidelines/Build.md` §10a/§10b are
  the recipes; the check needs a device (`emulator-5556`), which this change does not have.

## Risks

- **A stale native library.** An app built against an older `.so` never sees kind 7 — the mapping is by
  number, and the app degrades to `UNKNOWN` for a kind it does not know, so the pair cannot crash.
- **A second empty-list cause.** If a database list can be empty for a reason no retry clears (for example a
  map set whose directories were deleted after registration), it is reported as "still loading". The wording
  is true of the timing fact it reports and asks for a retry, which is what such a case needs first; the item
  records no measurement of that case.
