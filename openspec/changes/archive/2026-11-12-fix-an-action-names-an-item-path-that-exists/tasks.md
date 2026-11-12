# Tasks

Reference: `proposal.md` for scope and the red run, `design.md` for why, `specs/agent-issue-loop/spec.md` for the
contract.

## 1. The red case

Requirement: "An action's item path is the one the loop root holds", scenario "An action printed from an archived state
names an item path that exists".

- [x] 1.1 `tools/loop/tests/loop-cases.sh`: add `report#anActionNamesAnItemPathThatExists` — a run whose item 21 is
      `cut` at a bound with `--leftover` (its note carrying `run.sh`'s own `its work is kept at <patch>` sentence, the
      shape #847 and #839 carry) and whose item 22 recorded no gate result, with both item directories and their
      transcripts present where the tree keeps them, the report printed from the state in place and from the state a
      later `init` archived under `runs/`. It is declared `# files: tools/loop/report.sh tools/loop/state.sh
      tools/pi-child-events.sh tools/tooling.json` and registered in the suite's case list and its dispatcher.
      Verify: on HEAD `ff3a402a` the case fails first on the in-place report — the duplicated `its work is kept at`
      sentence, `expected '1', got '2'` — `tests=1 failures=1`, 2026-11-12 (`evidence/red-on-HEAD.log`,
      `evidence/red-on-HEAD.xml`); the archived-path red, `…/runs/items/21` does not exist, is carried by mutation
      277.1's message (`evidence/mutations-277.log`, 2026-11-12T14:56:41Z) and the worker's pre-fix run.

## 2. The report

- [x] 2.1 `tools/loop/report.sh`: derive the item root once, from the loop root the state belongs to — `$state_dir/items`
      when that directory exists, `$(dirname "$state_dir")/items` otherwise — and name it in both arms (`:687`, `:701`,
      HEAD's numbering)
      instead of `$state_dir/items`; the comment above the derivation says which two shapes it covers and by what test.
- [x] 2.2 `tools/loop/report.sh`: the resume line appends `· its work is kept at $patch` only when the note it prints
      does not already name that path, so `run.sh:3433`'s sentence is printed once.
- [x] 2.3 `tools/loop/tests/loop-mutations.sh`: two records, `277.1` (the derivation restored to the state's own
      directory) and `277.2` (the note read twice for the phrase), each with its case. Verify:
      `red-when-mutated: 2 · not-noticed: 0 · restore-failures: 0`, 2026-11-12T14:56:41Z and 14:56:46Z
      (`evidence/mutations-277.log`).

## 3. The rule's own text

- [x] 3.1 `guidelines/Backlog.recipes.md:1410-1421` "a named action list": the paragraph states that the item path an
      action names is read from the loop root the state belongs to, and that a sentence the note already carries is not
      printed twice. Verify: `tools/check-doc-routes.sh` (`all 337 section references resolve`),
      `tools/check-doc-values.sh`, `tools/check-portability.sh` and `tools/check-openspec-hygiene.sh` all exit 0 over
      the changed files (`evidence/structural-checks.log`, 2026-11-12T14:57:45Z).
- [x] 3.2 The change's `specs/agent-issue-loop/spec.md` names exactly one case, the one added in 1.1. Verify:
      `tools/check-change-readiness.sh openspec/changes/fix-an-action-names-an-item-path-that-exists` reads
      `LOOP-CHANGE-READY: ok`, beside `LOOP-MEASURE-CHANGE fix-an-action-names-an-item-path-that-exists scenarios=1
      cases=1 unnamed=0` (`evidence/readiness.log` for the readiness line, `evidence/measure-block.log` for the
      measure line).

## 4. Verification

- [x] 4.1 The red case passes on the change, with the `report` group beside it: `report 35 cases 223.53 s`,
      `all 35 cases passed` (`evidence/green-report-group.log`, 2026-11-12T14:57Z), and the case alone `PASS
      report#anActionNamesAnItemPathThatExists` (`evidence/green-case.log`). No compiled file is touched, so no build
      and no Gradle suite is owed: the structural checks of the files this delta edits stand in, and are named in 3.1.
- [x] 4.2 Both mutation records turn the case red and green again after the restore — `277.1` on the archived report's
      `…/runs/items/21`, `277.2` on the duplicated sentence (`evidence/mutations-277.log`).
- [x] 4.3 `./tools/loop/measure.sh --change fix-an-action-names-an-item-path-that-exists` quoted into
      `evidence/measure-block.log` — `LOOP-MEASURE-SUITE cases=416`,
      `LOOP-MEASURE-CHANGE fix-an-action-names-an-item-path-that-exists scenarios=1 cases=1 unnamed=0`,
      `LOOP-MEASURE-GATE seconds=-` (the gate has not run; this item's gate is the run's to take, after this diff is
      read).
- [x] 4.4 The fix measured on the run the item came from: `./tools/loop/report.sh --state
      .pi/issue-loop/runs/2026-11-11T11:37:08Z.json` prints the `resume #847` and `#839` lines ending
      `…/.pi/issue-loop/items/847` and `…/839`, each with the patch's sentence once
      (`evidence/live-archived-report.log`).
