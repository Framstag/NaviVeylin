## 1. Red cases first (submodule)

- [x] 1.1 `Tests/src/RouteStepModelTest.cpp`: add `AddFactsOnlyDescriptions`, `DescribedNodesRoute` (start, facts-only nodes at 300/350 m, turn at 400 m, facts-only at 430 m, turn at 560 m, target at 900 m) and `LongLegRoute` (facts-only nodes, motorway leave at 5 km) — the shapes real data produces (`steps=501 rows=34 info=466`). Owes spec `osmscout-jni` — Live guidance publishes route steps.
- [x] 1.2 Add "The step ahead is the next manoeuvre, not the next described node": step is the turn at 400 m, its hint the turn at 560 m — red on HEAD, where the 300 m node is published and the 350 m node is its hint.
- [x] 1.3 Add "The following manoeuvre is announced before a roundabout is entered": published entry, `nextNext` the leave with `exitCountTaken=2` — red on HEAD (`hintExit` reads 0; the fallback announces the target).
- [x] 1.4 Add "A manoeuvre beyond the collect window is still the step ahead": the 5 km motorway leave, red on HEAD (facts-only node / arrival).
- [x] 1.5 Add "A passed manoeuvre is no longer the step ahead": position at the 400 m turn, next step the 560 m turn with 160 m remaining, no hint.
- [x] 1.6 Record the red run: `evidence/native-tests.log` (four `REQUIRE` failures, selector reverted on the same tree).

## 2. The selector (submodule `libosmscout-client`)

- [x] 2.1 `RouteStepBuilder.cpp`: `IsManoeuvre` (every `Kind` but `informative`/`unknown`) and `FirstManoeuvreAfter` (strictly after the position) — owes spec `osmscout-jni` — Live guidance publishes route steps.
- [x] 2.2 `GenerateNextRouteInstruction`: first look `CollectWindowMeters`, then double the look-ahead until a manoeuvre is found or the route's remainder (`std::prev(last)->GetDistance()`) is covered; keep the arrival fallback for a route with no manoeuvre left.
- [x] 2.3 After selecting the step, extend the collection to `step.distanceFromStart + NextNextWindowMeters` when needed, and take the first manoeuvre strictly after the step as `nextNext`.
- [x] 2.4 `RouteStepBuilder.h`: `CollectWindowMeters` documented as the first look-ahead, extended to the route end — owes spec `next-turn-overlay` — "Next next" hint.

## 3. App consumers (`:core`, `:app`, `:auto`)

- [x] 3.1 `core/RouteStepGuidance.kt`: `guidanceStep(published)` returns `copyForGuidance(published)`; delete `nextManoeuvreAfter` and `HINT_WINDOW_METERS` — the native `NextNextWindowMeters` is the window's one owner (spec `next-turn-overlay` — "Next next" hint).
- [x] 3.2 `core/RouteRows.kt`: `ROW_MERGE_WINDOW_METERS = 200.0` names its own number and cites the native constant instead of aliasing the removed one.
- [x] 3.3 `NavigationEngine` calls `guidanceStep(step)` and `guidanceStep(list[0])`.
- [x] 3.4 Keep `nextManoeuvreFrom` for the car's unbounded next row (spec `auto/navigation-view` — Next-next turn shown when present); its unit case moves into `ManoeuvreAnnouncementTest`.
- [x] 3.5 `RoundaboutInstructionDiagnostics` KDoc: `hintExit` is the native hint's ordinal, not an app derivation.
- [x] 3.6 Rewrite `core: ManoeuvreAnnouncementTest` to the record contract: the copy, the native hint unchanged (roundabout ordinal), and no invented hint.
- [x] 3.7 Device cases `NextNextHintDeviceTest` and `NextTurnHintRowDeviceTest`: drive the published record whose `nextNext` is the leave; assert the native step is the entry and the native hint carries the exit.

## 4. Documents

- [x] 4.1 `guidelines/UIPhoneMap.md` §7 "Rule: live guidance uses the same step model and the same wording": one owner of the window, both rows from the record.
- [x] 4.2 Spec deltas `osmscout-jni` (two scenarios added) and `next-turn-overlay` ("Next next" hint re-provenanced); `auto/navigation-view` unchanged.
- [x] 4.3 This change's `proposal.md`, `design.md`, `tasks.md`, `evidence/native-tests.log`; `openspec validate fix-native-next-next-manoeuvre --strict` green.

## 5. Verification

- [x] 5.1 `libosmscout: RouteStepModelTest` — green (11 cases, `evidence/native-tests.log`).
- [x] 5.2 `:core:testDebugUnitTest` and `:auto:testDebugUnitTest`, plus `:app:testMobileDebugUnitTest` — green (3 m 16 s).
- [x] 5.3 The gate for the changed paths (`tools/loop/gate.sh --base e190d872`) — `LOOP-GATE: tests=34 failures=0 errors=0`, exit 0.
- [x] 5.4 Falsification: `IsManoeuvre` mutated to `return true || …` — 5 `REQUIRE` failures in `RouteStepModelTest`, including "The step ahead is the next manoeuvre…"; restored, 100 % passed (`evidence/native-tests.log`).
- [x] 5.5 Submodule commit `7c449bf66` on `naviveylin-local`, then the gitlink bump in the same main-repo commit.
- [ ] 5.6 Device (owed, not run here): `adb logcat -s NaviVeylin | grep 'rbt:'` on a roundabout approach shows `exit=N` and `hintExit=N` from the record.
