# Review round 1 — `fix-motorway-entry-through-a-link` (#993)

Independent, read-only. Read: `proposal.md`, `design.md`, `tasks.md`, `specs/osmscout-jni/spec.md`, all ten files of
`evidence/`, the submodule tree at `d1fefc13e`, the shipped requirement (`openspec/specs/osmscout-jni/spec.md:598-603`)
and the Kotlin readers above the bridge. No build, no test, no edit except this file.

## The defect's mechanism

`InstructionPostprocessor::HandleMotorwayLink`'s enter branch
(`libosmscout/src/osmscout/routing/RoutePostprocessor.cpp:829-839`) calls `HandleDirectionChange` (`:832`, which lands
a `TurnDescription`) and then adds the `MotorwayEnterDescription` (`:834-836`) **on the same node** — the turn of the
link and the entry share one node. `RouteDescriptionPostprocessor::GenerateDescription` fires `OnTurn` at `:219`
(inside the `else if` of the *roundabout* pair, `:218`) and, in a block that is not an `else` of it, `OnMotorwayEnter`
at `:226-229`, so both hooks report for that node. `NoteKind` was first-hook-wins, and `AfterNode` stamped the step
with it (`RouteStepCollector.cpp:513-515`), so the node published `turn` (enum 3, `RouteStep.h:63-75`; `motorwayEnter`
is 6) — and `DeriveKind` also tested `TurnDescription` first. The weaker kind then carried the entry's wording, arrow
and destination away with it; `red-on-pin.log` prints exactly that as `3 == 6`.

## Scenario verdicts

**S1 "An entry through a link is the entry, not the turn beside it" — PASS.** `RouteStepModelTest.cpp:965-988` builds
the shape the enter branch produces (`:590-596`, compare `RoutePostprocessor.cpp:829-839`) and runs the real
`RouteDescriptionPostprocessor` via `Collect`, so the shipping hook path is what is tested. `red-on-pin.log` stamps
what `evidence.md` §1 says: `RouteStepModelTest.cpp:980: FAILED … 3 == 6`, `test cases: 18 | 17 passed | 1 failed`,
`assertions: 152 | 151 passed | 1 failed`, `Ok: 0 / Fail: 1`, head `submodule c89db28f7, only
Tests/src/RouteStepModelTest.cpp from d1fefc13e`. `route-step-model-green.log` stamps `Check route step model OK`, `Ok: 1 / Fail: 0`, capture
`[4/6]`–`[6/6]`, and carries no tally — `evidence.md` §1 says so and points at `focused-cases.log` instead. Arithmetic coheres: 152 on the pin + the four `REQUIRE`s after `:980` (`:981`, `:982`, `:986`, `:987`) = 156, the
tally in `focused-cases.log:3/5`. The fix is `RouteStepCollector.cpp:176` with
`Precedence` at `:87-109` and `DeriveKind`'s reordered tests at `:138-152`.

**S2 "The entry keeps the signposted destination its node carries" — PASS.** The destination is asserted on the same
step as the kind: `RouteStepModelTest.cpp:986-987` reads `enter.destination` of the same `steps.steps[1]` whose kind
`:980` pins. The mechanism claimed is true and it is `InstructionPostprocessor::CarryDestination`
(`RoutePostprocessor.cpp:751`, called at `:838`), not `DestinationPostprocessor`; it early-returns when the node
already carries a record (`:756-758`), so the ramp's own tag wins and the decided-at node's record is the fallback
(`:760-764`). `BuildNodeStep` reads `DestinationDescription` whatever the kind (`RouteStepCollector.cpp:359-361`). Above the bridge nothing needs changing: `carriesDestination` admits
`MOTORWAY_ENTER`/`MOTORWAY_CHANGE` (`ManoeuvreLabels.kt:83-88`), `leadsOnto` composes "B 236 towards Dortmund"
(`:71-76`), `description` selects `nav_enter_onto` (`:52`, `core/src/main/res/values/strings.xml:32,35`),
`Manoeuvre.motorwayTo` reads the enter's `to` (`Manoeuvre.kt:109-113`) and `destination` takes the first block
(`:129`).

**S3 "A plain turn is still a turn" — PASS.** `RouteStepModelTest.cpp:689` asserts `steps.steps[1].kind==turn` on a
node with a `TurnDescription` and no motorway description; green in `focused-cases.log:3/5` (156 assertions, 18
cases). `Precedence` preserves every other kind: the ranks (`RouteStepCollector.cpp:87-109`) are monotone in
`GenerateDescription`'s hook order (`:194`, `:200`, `:204`, `:208-217`, `:218`, `:226-242`, `:243`), the only strict
inversion is motorway(3) over turn(2), and `NoteKind`'s `>` keeps first-wins where ranks tie — which is why start and
target may share rank 6. Roundabout-vs-turn and motorway-vs-nameChange are `else if` exclusive (`:208`, `:243`), so
their rank order is unobservable.

## Checks

**4. Additivity — PASS.** "The ranks are the hook order, except motorway-before-turn" is true against
`RouteDescriptionPostprocessor.cpp:192-255`: `OnMaxSpeed`/`OnPOIAtRoute` note nothing
(`RouteStepCollector.cpp:478-486`), and the eight manoeuvre hooks fire in rank order with that one swap; `DeriveKind`
(`:118-158`) matches. Kotlin readers of `RouteStep.Kind` checked: `Manoeuvre.kt:71,93-98,104-113`,
`ManoeuvreLabels.kt:22-37,43-58,83-88,95-101`, `ManoeuvreTurnTypes.kt:21-22`,
`RouteStepValues.kt:106,112-116,122-126,180`, `NavigationTemplateMapper.kt:362,495`,
`ui/navigation/NextTurnOverlay.kt:139,146,270-276`, `service/NavigationNotificationContent.kt:167,188,200`,
`ManeuverGlyphs.kt:61-63`. None needs a new kind; the look-ahead exclusion is real (`RouteStepBuilder.cpp:99-103`), and the device cases filter
by kind rather than pinning a route's kinds (`RouteStepModelBridgeTest.kt:132-226`).

**5. Gate — PASS on the verdict, one wrong clause (F2).** `gate.log:268` `2321 tests completed, 2 failed`, `:281`
`BUILD FAILED in 8m 36s`, `gate-verdict.log:299` `tests=0 failures=2 errors=0` — quoted verbatim by `tasks.md` 4.3.
Same case names at base: `gate.log:218`/`:257` vs
the JUnit `<testcase>` names in `base-red.log`, same task and flavor (`2 tests completed, 2 failed`), clean tree at
`66d7618f` (`base-red.log:1`, reports under `.base-tree/…`), `-PjvmTestsOnly` stated (`base-red.log:4`).
Nothing claimed green that is not: the whole submodule suite is an explicit exclusion (`tasks.md` 4.1), one ABI is
stated (4.2), five suites are `Ok: 5 / Fail: 0`. The loop's own subtraction failed and is reported
(`gate-verdict.log:298` `LOOP-GATE-BASERED-UNMEASURED`).

**6. Honesty — 4.4 PASS, 4.3 FAIL (F3).** 4.4 is unchecked, headed `**Owed**`, and named owed in `evidence.md` §4,
`design.md` Verification and `proposal.md`. 4.3 is `- [x]` while its prose and `evidence.md` §5 both say "the
zero-failure item stays unchecked" — no such item exists, so the list reads as a completed gate task sitting on
`failures=2`.

## Overall verdict

**PASS.** No scenario fails; the mechanism is proven by the diff and the pin's own red run; the rank is falsified
once (`revert-check.log`) and restored; the census is owed rather than claimed. F1–F7 are documentation findings;
F3 is the one I would not archive without.

## Findings

- **F1 — P2 — the collector's compile is attributed to the wrong log.** `evidence.md` §1 sends the reader to "the
  `[1/4] Compiling C++ object … RouteStepCollector.cpp.o` line of `evidence/revert-check.log`, taken on the same
  tree". That log's tree line reads `c89db28f7 + the change's working-tree patch, **mutated** at
  RouteStepCollector::NoteKind`, and its warning `…Precedence… definiert, aber nicht verwendet`
  (`RouteStepCollector.cpp:87`) proves the object built there is the **mutant**, not `d1fefc13e`. The green run is
  still sound (`:980` cannot pass on a first-hook-wins object), but the artifact is wrong; cite
  `route-step-model-green.log`'s `[6/6] Linking` instead.
- **F2 — P2 — "the same assertions" is false for the second base case.** `evidence.md` §5: "Both are the base's: the
  same assertions." Case 1 matches exactly (`gate.log:219` = `base-red.log`), case 2 does not: the gate fails at
  `SettingsSurfaceTest.kt:757` (`the map's own state follows the surface as it is made`, `gate.log:258,262`), the
  base earlier at `:750` (`assertIsOn`, `ToggleableState = 'Off'`), different line and assertion inside one function
  (`SettingsSurfaceTest.kt:735-760`). Right wording: case 2 is a flake carrying a base red of its own. The alone-rerun
  number that carries it (`tests="17" failures="0"`, 12:28:25Z) exists in prose only, contradicting `evidence.md:3`
  ("Every number below is quoted from the log beside it"); save that rerun's output in `evidence/`.
- **F3 — P2, fix before archive — the unchecked "zero failures" item does not exist.** `tasks.md` 4.3 is `[x]`; its
  text and `evidence.md` §5 both claim a zero-failure item "stays unchecked" per `guidelines/BuildEvidence.md` §4d.
  None exists. Fix: keep 4.3 checked for "ran and attributed", add `- [ ] 4.5 The gate reports zero failures (2
  base/flake cases remain; issue #998)`.
- **F4 — P2 — three Kotlin paths are wrong.** `proposal.md` Impact lists `ui/map/NextTurnOverlay.kt` (actual
  `ui/navigation/`), `ui/route/NavigationNotificationContent.kt` (actual `service/`); `design.md` Non-goals puts
  `ManeuverSymbols.kt` under `app/...` where it is `core/src/main/java/com/naviveylin/core/ManeuverSymbols.kt`.
- **F5 — P2 — the device-ABI log is overclaimed.** design.md: "the log carries the recompile and the link".
  `assemble-mobile-debug.log:153` `BUILD SUCCESSFUL in 4s` shows `:app:buildCMakeDebug[arm64-v8a]` (`:131`) ran, with
  no compile or link line and every consumer after it `UP-TO-DATE` (`:133-149`). Say the task ran.- **F6 — P2 — the S2 case pins retention, not placement.** The fixture injects `DestinationDescription` by hand
  (`RouteStepModelTest.cpp:595`) and never runs `InstructionPostprocessor`, unlike `:878-880`; the enter branch's
  `CarryDestination` (`RoutePostprocessor.cpp:838`) is covered by no case — `RoutePostprocessorTest.cpp` drives a link
  only as a *leave* (`:1368`, `:1411-1414`). Pre-existing; follow-up, not a blocker.
- **F7 — P2 — task 3.2's verification has no artifact.** 3.2 names `git show --stat` and
  `tools/check-submodule-pin.sh`; neither output is in `evidence/`. The pin bump rests on the supervisor's word
  (`2db7087e`, gitlink only).