# The gate's one red step is not this change's

Claim: `bash tools/gen-feature-list.sh --check-classification` was red before this change and stays red after it,
so it does not block this change. The gate ran once (`evidence/gate.log`); this file attributes its red step
rather than re-running it.

## What the red step printed

`evidence/bash_tools_gen-feature-list.sh_--check-classification.xml` is the step's own XML (`errors="1"`), and
`evidence/gate.log` carries its report:

- unclassified spec ids `auto/off-route-indicator` and `tooling-test-layout`
- stale entries `search-name-matching`, `search-result-row-icon`, `start-map-selection`

## Why that verdict cannot be this change's

- This change's tree delta is three test sources and nothing else:
  `app/src/test/java/com/naviveylin/navigation/NavigationEngineCalculationStateTest.kt`,
  `app/src/test/java/com/naviveylin/navigation/NavigationEngineRerouteTest.kt`,
  `app/src/test/java/com/naviveylin/test/DiagnosticsRecords.kt`.
- `git status --porcelain -- openspec tools/feature-list` is empty, so every input the classification check
  reads (`openspec/specs/`, the open changes' deltas, `tools/feature-list/specs.json`) is byte-identical to the
  base commit. The step's verdict is therefore the base commit's verdict: red before this work, red after it.
- The unclassified ids belong to two other open changes in this inherited worktree:
  `openspec/changes/car-reroute-without-losing-the-map/specs/auto/off-route-indicator/spec.md` and
  `openspec/changes/separate-tooling-test-code/specs/tooling-test-layout/spec.md`. The step's own message says
  where each is to be classified: in that change, before it archives.
- The stale entries are classification rows whose specs no longer exist under `openspec/specs/`:
  `tools/feature-list/specs.json:166` (`search-name-matching`), `tools/feature-list/specs.json:169`
  (`search-result-row-icon`), `tools/feature-list/specs.json:178` (`start-map-selection`). This change adds no
  spec and removes none, so it neither introduced nor could clear them.
- The capability this change extends, `unit-test-suite-runtime`, is already classified:
  `tools/feature-list/specs.json:186`.

## What this change answers for

The affected test cases and the red test the item names
(`NavigationEngineCalculationStateTest.theEndOfACalculationIsRecordedWithoutCoordinates`, red on HEAD under
revert-check A: `evidence/red-on-HEAD-NavigationEngineCalculationStateTest.xml`, and green after the fix:
`evidence/green-NavigationEngineCalculationStateTest.xml`). The classification step's report names no id of this
change.
