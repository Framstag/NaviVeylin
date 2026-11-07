# Evidence — fix-overrun-window-shift-home (issue #73, Option 1)

Run base: `87cc95de` (main, 2026-10-29). Branch: `fix/issue-73-overrun-window-shift-home`.
All measurements below were taken on this change's tree unless a file says otherwise.

## 1. The red case on HEAD

`evidence/red-on-head.xml`, `evidence/red-on-head.log` — 2026-10-29T16:53:50Z.

`com.naviveylin.core.OverrunWindowHomeTest` holds one case per half of the rule's home. With only that
file added, and `FollowPrediction.kt` / `FollowPredictionTest.kt` restored to `87cc95de` (the helper
temporarily moved aside), the class reports what the item claims:

```
tests="2" skipped="0" failures="2" errors="0"
OverrunWindowHomeTest > the prediction class carries no overrun window shift FAILED
  the prediction class must not declare the shift helper under its old name either expected:<0> but was:<1>
OverrunWindowHomeTest > the overrun window is the shift's home FAILED
  the shift's home must exist at .../core/src/main/java/com/naviveylin/core/OverrunWindow.kt
```

## 2. Focused runs

Each selection's cases are exercised by the XMLs beside this file (see the column below); the guard's own
green run is `green-core.xml`, the second half of the red→green pair of §1.

| selection | result | XMLs |
|---|---|---|
| `:core:testDebugUnitTest --tests OverrunWindowTest --tests FollowPredictionTest --tests OverrunWindowHomeTest` | BUILD SUCCESSFUL | `TEST-com.naviveylin.core.OverrunWindowTest.xml` 10/0, `TEST-com.naviveylin.core.FollowPredictionTest.xml` 18/0, and the guard's red→green pair (`red-on-head.xml` 2/2 → `green-core.xml` 2/0) |
| `:app:testMobileDebugUnitTest --tests FollowAnchorFramingTest --tests MapPanDisplayWindowTest --tests FollowBlitCallSiteTest --tests MapCanvasViewModelVehicleAnchorTest --tests FollowDisplayOffsetTest` | BUILD SUCCESSFUL | `TEST-…FollowAnchorFramingTest.xml` 10/0, `…MapPanDisplayWindowTest.xml` 6/0, `…FollowBlitCallSiteTest.xml` 2/0, `…MapCanvasViewModelVehicleAnchorTest.xml` 16/0, `…FollowDisplayOffsetTest.xml` 3/0 |
| `:auto:testDebugUnitTest --tests AutoMapRendererBlitTest --tests AutoMapRendererFollowAnchorTest` | BUILD SUCCESSFUL | `TEST-…AutoMapRendererBlitTest.xml` 23/0, `TEST-…AutoMapRendererFollowAnchorTest.xml` 11/0 |

(`tests/failures`; every suite `errors="0"`.) `green-core.xml` is the guard's green run from the red→green
pair (16:54:38Z), not a re-run of the later selections; the nine `TEST-*.xml` files are the later
focused runs (17:04Z), where the guard case is also green. The runs' wall-clock times were printed to the
console and are not carried by any file here — the timestamps are the record.

## 3. Revert-check (the guard is the change's one new invariant)

- Mutated `FollowPrediction` by re-declaring `fun shiftPx(): Unit = Unit` →
  `evidence/revert-check-mutated.xml`: `tests="2" failures="1"` at 2026-10-29T17:03:37Z, failing
  `the prediction class carries no overrun window shift` (`expected:<0> but was:<1>`).
- Restored the file, re-ran the class with `-PforceTests --no-build-cache` →
  `evidence/revert-check-restored.xml`: `tests="2" failures="0"` at 2026-10-29T17:03:47Z.

## 4. The gate

- `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache` — BUILD SUCCESSFUL in 6 m 57 s, `FAILED`
  count 0, Kotlin `w:` warnings 0 (`evidence/gate.log`, the first run). Executed test tasks:
  `:app:testMobileDebugUnitTest`, `:app:testAutomotiveDebugUnitTest`, `:auto:testDebugUnitTest`,
  `:core:testDebugUnitTest`, `:osmscout-client-java:test`.
- Tallies from the XMLs (`evidence/gate-tallies.txt`), every suite `failures=0 errors=0`:
  `:app` mobile 2138, `:app` automotive 2138, `:auto` 887, `:core` 645, `:osmscout-client-java` 112.
  Exclusions: none — the run covered every suite the gate names.
- `bash tools/loop/gate.sh --base 87cc95de` — exit 0 (`evidence/shell-gate.log`). The profile selected and
  ran exactly six steps beside the Gradle gate, which it re-ran forced (550 s, `BUILD SUCCESSFUL`,
  `LOOP-GATE: tests=0 failures=0`): `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache`,
  `tools/check-compose-sandbox-rule.sh` + `-selftest.sh`, `tools/check-doc-routes.sh` + `-selftest.sh`,
  `tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list`,
  `tools/check-todo-references.sh`.
- Run separately, because the touched paths do not select them (`evidence/checks.txt`):
  `openspec validate fix-overrun-window-shift-home --strict` → "Change … is valid"; `openspec doctor` → ok;
  `tools/check-openspec-hygiene.sh` → exit 0; `tools/gen-feature-list.sh --check-classification` → exit 0,
  its three `stale classification entry` lines (`search-name-matching`, `search-result-row-icon`,
  `start-map-selection`) are the tree's pre-existing state and name no capability this change touches.

## 5. What is not measured, and why

No on-device run and no screenshot. The change moves a pure function and renames its call sites; no
projection, clamp value, frame cadence, thread or pixel changes, so a device run could only reproduce the
pre-change numbers. The property the item names is a code-organisation property, and the guard in §1-§3 is
its measurement. Stated, not implied.

## 6. Pre-existing tree state, untouched

`app/src/main/cpp/libosmscout` is dirty in this working tree (`Tests/meson.build` modified, two untracked
files) from work that is not part of this change; nothing here stages, reverts or commits it, and no file
under `app/src/main/cpp/` is touched by this change.
