# Evidence

Issue #993. Tree: submodule `naviveylin-local` at `d1fefc13e` (parent commit pins it), parent `66d7618f` with this
change's files uncommitted. Every number below is quoted from the log beside it; none is restated from memory.

## 1. The case, red on the pin and green after

`evidence/red-on-pin.log` (2026-11-21T12:08:16Z) — the submodule at `c89db28f7` with only
`Tests/src/RouteStepModelTest.cpp` from `d1fefc13e`:

```
  REQUIRE( enter.kind==RouteStep::Kind::motorwayEnter )
with expansion:
  3 == 6
assertions: 152 | 151 passed | 1 failed
Ok:                0
Fail:              1
```

`3` is `RouteStep::Kind::turn`, `6` is `motorwayEnter`: the pin published the entry as a turn, which is the
defect's mechanism in one line.

`evidence/route-step-model-green.log` (2026-11-21T12:11:59Z) — the same case on `d1fefc13e`: `1/1
libosmscout:Check route step model OK`, `Ok: 1 / Fail: 0`. The log's capture begins at ninja's step 4/6, so it shows
the rebuilt test object and `[6/6] Linking target Tests/RouteStepModelTest`, not the collector's own compile, and it
carries no assertion tally. Neither gap weakens it: a green `check` target cannot link against a first-hook-wins
object, since `:980` fails on one (`evidence/red-on-pin.log`), and `evidence/revert-check.log`'s own object is the
mutant, not this one. The assertion tally is in `evidence/focused-cases.log`, on a clean tree: `All tests passed (156
assertions in 18 test cases)`.

## 2. The suites around it

`evidence/focused-cases.log` (2026-11-21T12:15:54Z), submodule clean at `d1fefc13e`: `Check route postprocessor`
138 assertions / 10 cases, `Check route step time` 19 / 5, `Check route step model` 156 / 18, `Check route failure
kind` 24 / 3, `Check route start` 20 / 5 — `Ok: 5 / Fail: 0`. The plain turn (`Kind::turn`) and #877's destination
pair stay green in that run.

## 3. The falsification the rank owes

`evidence/revert-check.log` (2026-11-21T11:53:46Z) — `NoteKind` mutated back to first-hook-wins with the case in
place: the case fails (`3 == 6`, `152 | 151 passed | 1 failed`) and the mutation is visible in the log as an
unused `Precedence`; restored, the suite is green again (section 2). The invariant this pins is the rank itself:
with it removed the case cannot pass, so the case tests the fix and not the fixture.

## 4. The census: owed, not measured

The issue's own list ends with a measurement: the `route census:` line for `51.5726589 7.4649125 → 51.4918664
7.5045441`, where the entry must move out of the `turn` count into `motorway` and add `motorway_dest=1`. **It is
not measured here**: `adb devices` reports no attached device, and this repository holds no map database — no
`.osm.pbf`, no extracted region, and the host build configured for section 2 has `-DenableImport=false`, so no
import could be made from it either. The recipe the line is read by is `guidelines/BuildNative.md` §10k, and the
line belongs to a driven run.

This is the same premise the issue records as owed, so the change is not silent about it: task 4.4 stays unchecked
and the spec's second scenario rests on the code-path case, not on the census.

## 5. The gate, and what its red is

`evidence/gate.log` (2026-11-21, `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache`):
`2321 tests completed, 2 failed`, `BUILD FAILED in 8m 36s`. `evidence/gate-verdict.log` (the same selection through
the loop's gate, `tools/loop/gate.sh --base 66d7618f`): `tests=0 failures=2 errors=0`, with
`LOOP-GATE-BASERED-UNMEASURED` — the gate could not find a clean tree at the base to subtract from, because the
only trees it looks at are this worktree and the main checkout, and both carry the change. The mechanical half of
the attribution (`tools/gate-attribute.sh` over that log, `evidence/gate-attribution.log`) names the step, the
first failing case, the second, and the rerun command it derives — `file:line` and the error text are "not named by
this log", which is why the base measurement below was taken by hand.

Both failures were then measured in a clean tree at `66d7618f`, `evidence/base-red.log` (2026-11-21T12:47:10Z):

```
com.naviveylin.ui.map.SymbolIconPreferenceTest#aChangeReachesTheClientOnceAndIsPersisted
  java.lang.AssertionError: expected:<[true]> but was:<[true, false, true]>   (failures=1)
com.naviveylin.ui.settings.SettingsSurfaceTest#aChangePersistsWithoutASaveAction
  java.lang.AssertionError: Failed to assert the following: (ToggleableState = 'On')   (failures=1)
```

The first is the base's exactly: the same case, the same assertion, `expected:<[true]> but was:<[true, false, true]>`
on both trees, in a case that reads a JVM fake (`FakeOSMScoutClient`) and no native code at all. It reproduces on
both, so it is filed as issue #998.

The second is the base's too, but not at the same assertion: the gate's run stops in that case at
`SettingsSurfaceTest.kt:757` (`the map's own state follows the surface as it is made`, `evidence/gate.log`), the base's
at `:750` (`assertIsOn`, `ToggleableState = 'Off'`, `evidence/base-red.log`) — neither passing the other's line on its
own tree. The case is a flake as well as a base red: re-run alone on the patched tree (13:21Z), `tests="17"
failures="0"` for the class, `BUILD SUCCESSFUL in 25s` (`evidence/settings-surface-alone.log`) — the run that carries
it is saved there, not only asserted here.

Task 4.3 is checked for the run and its attribution; the zero-failure item that follows it (4.5) stays unchecked on
purpose, by `guidelines/BuildEvidence.md` §4d: file the residual and the flake, do not talk the run green.

The base tree needed two properties to run the step at all, both recorded in that log's head: the linked worktree
cannot own the submodule's git dir, so the submodule's working tree was copied in (`d1fefc13e` differs from the pin
in C++ only, so the JVM classpath the cases read was the base's), and `-PjvmTestsOnly` kept the SBOM and licence
chain out of a tree with no `vcpkg/` checkout.

## 6. The device ABI

`evidence/assemble-mobile-debug.log` — `RouteStepCollector.cpp`'s arm64-v8a object removed, then
`./gradlew :app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a`: `BUILD SUCCESSFUL`. The log shows
`:app:configureCMakeDebug[arm64-v8a]` and `:app:buildCMakeDebug[arm64-v8a]` **executed rather than up to date**, and
every consumer after them up to date; it carries no compile or link line of its own, so the claim is that the task
ran, not that a line in the log names the object. The parent-side pins (`:app:checkNativeParity`,
`:app:checkSubmoduleFeatureHeaders`, `:app:checkSubmoduleStylesheets`) ran green in the same invocation. One ABI,
stated as such: the patch is a switch over an enum with no platform branch.

## 7. What the change does not do

- The issue's **second, independent loss** — the motorway way's own record landing on the `NAME_CHANGE` junction
  node at a mid-way merge instead of on the enter node — is untouched. On this route the ramp carries
  `destination=Dortmund;Schwerte` itself, so the record the wording needs is on the enter node already; a ramp with
  no tag of its own is a different finding and no case here covers it.
- No wording, arrow, template, notification or census key is changed: all of them already key on a
  `motorwayEnter` kind that the model now actually publishes.
- The case pins the destination's **retention**, not its placement: the fixture carries the `DestinationDescription`
  itself, and `InstructionPostprocessor::CarryDestination`'s enter branch has no case of its own (the postprocessor
  suite drives a link as a leave only). Pre-existing and a follow-up, not a blocker — the placement this route
  depends on is `CarryDestination`'s early return on a node that already carries a record.
- The pin bump is `2db7087e`, whose whole content is the gitlink: `git show --stat` names only
  `app/src/main/cpp/libosmscout`, and `tools/check-submodule-pin.sh` reports `d1fefc13ea8d is on
  refs/remotes/origin/naviveylin-local` — both quoted in `evidence/pin-bump.log`. HEAD has since moved to another
  writer's commit, so that log reads the pin out of `2db7087e` by hash.
- The `turn` record of the ramp survives on the step (`RouteStep.turn`, `RouteStep.direction`); only the step's kind
  changed, which is the cost the issue states for option A.