# Tasks — a start the worktree refuses mid-run names the item whose work the tree holds

## 1. The red case

- [x] `tools/loop-cases.sh`: the case `run#aDirtyStartNamesTheItemWhoseWorkTheTreeHolds` (registered in
  `LOOP_SHELL_CASES` and in the dispatch), a two-item run whose first item ends with its work still in the tree (the
  stub installs a `pre-commit` hook that refuses both of `end-item`'s commits), so the second item's start is
  refused.
  Verify: `bash tools/loop-selftest.sh --case 'run#aDirtyStartNamesTheItemWhoseWorkTheTreeHolds'` — red on HEAD,
  for the reason the case names; the transcript and the tally are `evidence/red-HEAD.log` and
  `evidence/red-HEAD.xml` (`tests="1" failures="1"`).

## 2. The fix

- [x] `tools/loop/run.sh`: one arm for the refusal that reads `not clean` — read the worktree's branch marker
  (`owner_of_marker`, the pre-flight's reading, defined once), record the leftovers under the item it names and
  escalate it as the pre-flight does, name the item and the files in the stop reason, and stop. The catch-all arm
  and the two arms beside it are untouched.
  Verify: the case from task 1 goes green (`bash tools/loop-selftest.sh --case …`, `PASS`).

- [x] `tools/loop/state.sh`: `cmd_done` keeps one record per item — a second account joins the note the item already
  has and writes its escalation label instead of appending a second entry.
  Verify: the same case's `.items | length` and note assertions (one record carrying both the reason the item ended
  and the leftovers), and `bash tools/loop-selftest.sh --group state` (14 cases) still green.

## 3. The invariants' own falsification

- [x] `tools/loop-mutations.sh`: three records, one per invariant, declared inside `MUTATIONS=( … )` — `53.1` (the
  attribution and its escalation), `53.2` (one record per item) and `53.3` (the stop reason naming the item and the
  files).
  Verify: `bash tools/loop-mutations.sh --only 53.1` (and `53.2`, `53.3`) — `RED` for the case, an exact restore,
  green again: `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0` each, `evidence/revert-check-53.log`.

## 4. The guideline

- [x] `guidelines/Backlog.md`, section "7. The loop": one paragraph beside "What it does with a child that never
  started" — what the run does with a start the worktree refuses, why it stops rather than taking the next
  candidate, the one-record rule, and the measurement behind it (#284).
  Verify: `tools/check-doc-routes.sh` (the gate's own check for a `*.md` path), plus the structural read that the
  paragraph still sits under `## 7. The loop`.

## 5. The gate, once

- [x] `./tools/loop/gate.sh` from the change root — no compiled file changed, so the profile owes the three shell
  suites (`tools/loop-selftest.sh`, `bash tools/loop-mutations.sh --check`, `tools/loop-mutations-selftest.sh`) and
  the documentation check (`tools/check-doc-routes.sh`); `openspec/changes/**` is untracked at this point, so
  `gen-feature-list.sh --check-classification` was not owed.
  Verify: the run's own `LOOP-GATE` line, green — 340 cases across the four suites, 0 failures, 0 errors —
  `evidence/gate.log`, with each suite's own tally beside it: `tools/loop-selftest.sh` (`tests="192"
  failures="0" errors="0"`), `bash tools/loop-mutations.sh --check` (`tests="114" failures="0" errors="0"`),
  `tools/loop-mutations-selftest.sh` (`tests="34" failures="0" errors="0"`) and `tools/check-doc-routes.sh`
  (`tests="0" failures="0" errors="0"`, all 152 section references resolve).

## 6. Evidence and archive

- [x] Copy every cited XML into `openspec/changes/fix-loop-dirty-start-names-the-leftover/evidence/` and into
  `.pi/issue-loop/items/284`.
- [x] `bash .pi/skills/fix-loop/scripts/evidence-check.sh openspec/changes/fix-loop-dirty-start-names-the-leftover --root .`
- [x] `openspec validate fix-loop-dirty-start-names-the-leftover --strict`, then archive the change and confirm the
  delta merged into `openspec/specs/agent-issue-loop/spec.md`.
