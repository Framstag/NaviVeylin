# Proposal: fix-the-refused-landing-is-driven-through-the-run

## Why

**Root cause.** The run's refused landing is two mechanisms and only the reading halves were pinned: `run.sh`'s
`merge_committed_branch` records the branch it asked the merge for and the paths the refusal printed
(`tools/loop/run.sh:516`, `:520`, `:544`), the block hands both to the state (`tools/loop/run.sh:4343-4347`,
`tools/loop/state.sh:994-1000` writes `mergeRefused: {branch, paths}`, `tools/loop/report.sh:875-917` reads it), and
no case drives that arm through `run.sh` at all. The one case that reads a refusal builds it by calling
`run_state done --merge-refused-branch` itself (`tools/loop/tests/loop-cases.sh:13918`, the call at `:13932`): it asserts the record's
*reader* against a record the run never wrote.

**Evidence** ⊨:

- `tools/look.sh 'mergeRefused|merge-refused|merge_refused_branch' --path tools/loop` at `7ecab8e6` — **36 matches in
  5 files**: `run.sh` 9, `state.sh` 12, `report.sh` 8, `loop-cases.sh` 6, `loop-mutations.sh` 1. Every match in
  `loop-cases.sh` is inside `case_report_theRefusedLandingIsReadFromTheRecord` or its comment, and that case calls
  `run_state done` (`:13932-13936`) instead of running the run.
- The selection list (`tools/loop/tests/loop-cases.sh`, top of the file) holds no `run#` case for a landing the base
  cannot take: `tools/look.sh 'run#doesNotCloseOnAMergeTheBaseCannotTake' --path tools/loop` at `7ecab8e6` answers
  **one file**, and it is `tools/loop/tests/loop-mutations.sh` — the record, naming a case that is not in the tree.
- **Red on HEAD, measured:** `./tools/loop/tests/loop-mutations.sh --only 58.2` on a clean clone of `7ecab8e6`,
  2026-11-17T12:30:45Z —
  `RED 58.2 run#doesNotCloseOnAMergeTheBaseCannotTake` then `RESTORE-FAILED … stayed red after the restore`,
  `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 1`. The record is not noticing anything: the case it names
  was deleted by `a30a3305` (#862), so the arm 58.2 mutates — `run.sh:4099`'s `block unmergeable` — is asserted by no
  case in the tree, and the harness reads the missing case as a red that its own restore cannot clear.
  (`evidence/head-58-2.txt`.)
- The arm itself is reachable and does its work: reading it, `merge-item` refuses with
  `the project tree is not clean — refusing to merge onto the base:` and the `git status --porcelain` lines beneath
  it (`tools/loop/worktree.sh:665`), which is exactly the shape `run.sh:519-521` reads after `onto the base:`.

**Consequence** ⊨: the run can stop recording a refused landing — take the wrong exit out of `merge_committed_branch`,
or clear `merge_refused_branch`/`merge_refused_paths` before `run.sh:4343` reads them — and every case stays green
while the report prints a landing the run never saw, which is the defect #883's own writer field was added for. The
records that do exist (`883.2`, `883.3`) pin the note's reading and the state's writing; the production of the
refusal is pinned by none, and the one record that names it names a case the tree does not hold.

**The one fix** ➜: one case, `run#doesNotCloseOnAMergeTheBaseCannotTake`, that drives a real refusal through
`run.sh`'s merge attempt to the record: it holds the item's mechanism `unmergeable`, the branch the arm asked the
merge for and **every** path the refusal named, and it restores the name record `58.2` already carries, so that record
notices again. Plus its own record for the half 58.2 does not mutate (the branch and the paths travelling into the
state). No engine change: the arm is correct today, and nothing here changes it.

**Owning capability and guideline.** `agent-issue-loop`, "An item a merge refused names its landing";
`guidelines/Backlog.recipes.md` section 7d "Refusals, records and the landing", the paragraph *A refused landing is a record,
and no report re-reads the note to find it*.

**Requirement this makes true.** `openspec/specs/agent-issue-loop/spec.md`, "An item a merge refused names its
landing": "the run SHALL write the branch it could not merge and every path the refusal named as `mergeRefused` on the
item's record" — a requirement whose own producer was driven by no case.

surface: 1 case added, 0 rewritten, 1 mutation record added — `tools/look.sh 'mergeRefused|merge-refused|merge_refused_branch' --path tools/loop` (36 matches in 5 files at `7ecab8e6`)

## What Changes

1. **`tools/loop/tests/loop-cases.sh`**: `run#doesNotCloseOnAMergeTheBaseCannotTake` — a two-item run over the `two`
   fixture, the first item's child dirtying the project's own tree, the second item's landing refused by the paths the
   first item's landing merged over. Asserts the block, `stopReason: unmergeable`, `mergeRefused.branch` and every
   path in `mergeRefused.paths`, that nothing landed and that the item is escalated rather than closed.
2. **`tools/loop/tests/loop-mutations.sh`**: `883.4` mutates the guard that hands the refusal to `state.sh`
   (`tools/loop/run.sh:4343`) and requires that case to go red; `58.2` stops being a record of nothing, because the
   case it names is in the tree again.
3. **`guidelines/Backlog.recipes.md`** section 7d: the refused-landing paragraph names the case that drives the arm and the
   record that pins it.
4. **The delta spec** adds one scenario under the requirement above, naming that case, so the scenario the change
   ships with and the case it names are one line apart.
