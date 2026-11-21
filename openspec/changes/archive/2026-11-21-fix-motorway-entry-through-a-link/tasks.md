# Tasks

## 1. Write the case the issue owes, and record it red on the pin (submodule)

- [x] 1.1 Add the fixture `MotorwayEnterThroughLinkRoute` and the case "A motorway enter through a link is the
      entry, not the turn" to `app/src/main/cpp/libosmscout/Tests/src/RouteStepModelTest.cpp`: the node at 2.5 km
      carries a `TurnDescription`, a `DirectionDescription`, a `MotorwayEnterDescription` onto `B 236`, a
      `DestinationDescription("Dortmund;Schwerte")` and the ramp's `TypeNameDescription` — the shape
      `InstructionPostprocessor::HandleMotorwayLink` produces. Verify: red on submodule `c89db28f7` —
      `REQUIRE( enter.kind==RouteStep::Kind::motorwayEnter )` → `3 == 6`, tally `152 assertions | 151 passed | 1
      failed`, suite `Ok: 0 / Fail: 1` (`evidence/red-on-pin.log`, 2026-11-21T12:08:16Z). (Spec scenario: *An entry
      through a link is the entry, not the turn beside it*.)
- [x] 1.2 Confirm the case pins the destination too, not only the kind: the same case requires
      `enter.destination == "Dortmund;Schwerte"`, so a fix that changed the kind but dropped the record would fail.
      Verify: the assertion is the case's last `REQUIRE`, and it is green only after 2.1–2.3. (Spec scenario: *The
      entry keeps the signposted destination its node carries*.)
- [x] 1.3 Confirm the neighbouring cases the change must not move stay green on the pin: "A description converts
      into one step per described node, in route order" (`steps[1].kind==turn`, the plain turn),
      "A motorway change through a link carries the destination of the way it leads onto" and "A motorway step
      keeps its own destination when its node carries one" (#877's pair). Verify: the four are green in
      `evidence/focused-cases.log` before and after. (Spec scenario: *A plain turn is still a turn*.)

## 2. The fix: rank the manoeuvres in the model (design D1, D2)

- [x] 2.1 Add `Precedence(RouteStep::Kind)` and change `NoteKind` to keep the stronger of two kinds reported for
      one node (`RouteStepCollector.cpp`, doc comment on `NoteKind` in the header). Verify: 1.1 turns green and 1.3's
      cases stay green. (Spec: `osmscout-jni` — A motorway manoeuvre outranks a turn on its node.)
- [x] 2.2 Order `DeriveKind`'s tests the way the ranks order the kinds — the motorway descriptions before
      `TurnDescription` — so the fallback and the hooks agree for the same node. Verify: the submodule's
      `Check route step model` is green, 156 assertions in 18 test cases
      (`evidence/route-step-model-green.log`, `evidence/focused-cases.log`). (Spec scenario: *An entry through a
      link is the entry, not the turn beside it*.)
- [x] 2.3 Revert-check the introduced rank: `NoteKind` mutated back to first-hook-wins with the case in place, the
      case fails (`3 == 6`, `152 | 151 passed | 1 failed`), restore, green again. Verify: red run and restored run
      quoted with timestamps in `evidence/revert-check.log`. (`guidelines/Build.md` §2.)

## 3. Land the submodule patch

- [x] 3.1 Commit the patch and the case in the submodule as one commit, `d1fefc13e`, authored with the branch's
      noreply identity — the parent repository's `tim@framstag.com` is refused by GitHub with `GH007: Your push
      would publish a private email address` (`guidelines/BuildTests.md` §6g's pitfall). Verify: `git log -1`
      shows `Framstag <Framstag@users.noreply.github.com>` and `origin/naviveylin-local` equals the local tip.
- [x] 3.2 Bump the gitlink in the parent to `d1fefc13e`, as the whole of that commit's content — parent commit
      `2db7087e`. Verify: `git show --stat` names only `app/src/main/cpp/libosmscout`, and
      `tools/check-submodule-pin.sh` reports `d1fefc13ea8d is on refs/remotes/origin/naviveylin-local`; both quoted in
      `evidence/pin-bump.log`.

## 4. Verify the tree

- [x] 4.1 The five suites of the submodule that own the collector's neighbours, on the patched tree: `Check route
      postprocessor` (138/10), `Check route step time` (19/5), `Check route step model` (156/18), `Check route
      failure kind` (24/3), `Check route start` (20/5) — 5/5 `Ok: 5 / Fail: 0`. Verify: `evidence/focused-cases.log`
      (2026-11-21T12:15:54Z). Exclusion: the submodule's whole suite is not run; the five are the ones that read
      `RouteStepCollector` or the postprocessor it ranks, and `guidelines/BuildTests.md` §6g holds the per-test
      timeout caveat.
- [x] 4.2 The device ABI compiles the patch: the arm64-v8a object of `RouteStepCollector.cpp` removed first, then
      `./gradlew :app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a` — `BUILD SUCCESSFUL`, and
      `:app:buildCMakeDebug[arm64-v8a]` ran rather than being up to date. Verify:
      `evidence/assemble-mobile-debug.log`. Exclusion: one ABI, not three — the change is a switch over an enum with
      no platform branch, and the parent-side pins (`:app:checkNativeParity`,
      `:app:checkSubmoduleFeatureHeaders`) ran in the same invocation.
- [x] 4.3 The gate the change owes by its own pathspec, `tools/loop/gate.sh --base 66d7618f`: `tests=0
      failures=2 errors=0` — both failures are the base's, measured in a clean tree at `66d7618f`
      (`evidence/base-red.log`, 2026-11-21T12:47:10Z): `SymbolIconPreferenceTest#aChangeReachesTheClientOnceAndIsPersisted`
      fails there with the same `expected:<[true]> but was:<[true, false, true]>`, and
      `SettingsSurfaceTest#aChangePersistsWithoutASaveAction` fails there too (it passes when re-run alone on this
      tree, so it is a flake as well as a base red; the alone re-run is `evidence/settings-surface-alone.log`,
      `tests="17" failures="0"` for the class). The residual is filed as issue #998. Verify:
      `evidence/gate.log` (2026-11-21, `BUILD FAILED in 8m 36s`, `2321 tests completed, 2 failed`) and
      `evidence/gate-verdict.log` — item 4.5, not this one, carries the zero-failure claim.
- [ ] 4.5 The gate reports zero failures. **Unchecked on purpose**: two cases remain, one a base red reproducible on
      both trees (issue #998) and one a base red that is also a flake; `guidelines/BuildEvidence.md` §4d files the
      residual rather than talking the run green. Evidence: `evidence/base-red.log`, `evidence/evidence.md` §5.
- [ ] 4.4 The route census on the issue's route, before and after the pin: plan `51.5726589 7.4649125 →
      51.4918664 7.5045441` and read the `route census:` line by `guidelines/BuildNative.md` §10k — the entry must
      move from the `turn` count to `motorway` and add `motorway_dest=1`. **Owed**: no device was attached
      (`adb devices` empty) and no map database suffices locally (the repository holds no `.osm.pbf`), so the line
      is a driven run's, not this session's. Verify: the line quoted verbatim, both builds.

## 5. Review and archive

- [x] 5.1 An independent read-only reviewer confirms each spec scenario against the diff and the gate XMLs
      (`guidelines/BuildEvidence.md` §4a); the writer may not be the reviewer. Verify:
      `evidence/review-round-1.md` — the three scenarios PASS and the overall verdict is PASS; its findings F1–F7 are
      applied in this artifact set (F2's one corrected clause: base case 2 fails at a neighbouring assertion, so it
      is a base red plus a flake, not the same assertion).
- [ ] 5.2 `openspec validate fix-motorway-entry-through-a-link --strict` passes, the change archives with this
      evidence, and #993 is closed naming the archived change and the parent commit that carries the pin.