# A phase's report is the phase's answer, and a silence is that phase's failure

## Why

Issue **#810**. Measured on item #755 of the run `2026-11-06T21:19:06Z`: the gate child **exited 0** and left
`worker-gate-report.md` **0 bytes**, and the item was recorded `blocked` with

```
reported success but no archived change appears in the run's diff
```

over a branch that carries a commit, after 11 m 49 s and 13.0 Mtok — the two figures #755's tracker text and the run's
state record carry; no command in this change's record prints them. The work was real and kept; its record named the
wrong cause, so a person reading it inspects the change instead of a phase whose child said nothing.

**Verified in the tree today.** `tools/look.sh 'no archived change appears'` → 10 matches in 5 files, and the arm
that writes it is `tools/loop/run.sh:2541`. The path a 0-byte gate report takes is three sites, all of them reading
that empty file as an answer:

- `tail_after_gate` (`tools/loop/run.sh:1734`) reads `LOOP-GATE` out of the gate report and returns 1 when the line is
  absent, so an empty report starts no tail — the same return a *skip* takes, and nothing asks whether the phase
  answered at all.
- the outcome block then decides the item from `final_report`, which is still that empty gate report: `verdict_claim`
  is empty (neither `skipped` nor `blocked`), `change_claim` is empty, and `head_now` differs from the base because the
  change child's work was committed — so every arm above `run.sh:2541` declines and the one that fires names the item's
  tree.
- the phase's own record is written from the exit status alone — on the base, `ok` with no reading of the report — at
  the step (`run.sh:2339`–`:2341`), which is the reading the
  item says is not the test (#742, one requirement earlier).

**Root cause, in one sentence:** the run reads a phase's report as optional text rather than as the phase's answer, so
a child that exits 0 and writes nothing is recorded `ok`, starts no tail, and leaves the empty file as the item's final
report — whose absent change claim reaches the arm that blames the item's own tree.

**The red case, on the change root's base `8280d8aa`** — `run#aPhaseThatAnswersNothingIsThatPhasesFailure`, one
`bash tools/loop-selftest.sh --case 'run#aPhaseThatAnswersNothingIsThatPhasesFailure'` run at 2026-11-07T23:06:36Z:

```
run#aPhaseThatAnswersNothingIsThatPhasesFailure|   and is asked once more when its answer is not an answer: expected '1', got '0'
--- run#aPhaseThatAnswersNothingIsThatPhasesFailure[0] (exit 1)
FAIL run#aPhaseThatAnswersNothingIsThatPhasesFailure
loop-selftest: 1 of 1 cases failed
```

`evidence/red-on-HEAD.log` is that run and `evidence/red-on-HEAD.xml` its tally (`tests=1 failures=1 errors=0`), kept
beside it because the shell runner writes no JUnit XML of its own. The case's first-line declaration is accepted
before that run: `bash tools/loop-selftest.sh --case 'driver#everyCaseDeclaresTheFilesItCanReach'` → `PASS`
(`evidence/green-declaration.log`).

**Owning requirement:** `openspec/specs/agent-issue-loop/spec.md`, "The run verifies rather than trusts" (`:544`), whose
body reads the tree, the archive and the gate's evidence as artifacts the run decides from, and whose scenario "A claim
without a commit is blocked" is the sibling reading of the same silence. The delta adds the scenario that makes a
phase's own answer one of those artifacts, and carries the four scenarios the requirement already has with the case
each already names.

**Owning guideline section:** `guidelines/Backlog.recipes.md:719` "Refusals, records and the landing", the paragraph
"What it does with a child that never started" (`:721`) — the same reading, one phase further along: that paragraph owns
the worker's 0-byte answer, and this change gives the gate phase the rule the paragraph already states.

surface: 1 case added, 0 rewritten, 0 fixtures realigned, 5 engine sites in one file (`run.sh`: the `gate_answered`
helper, `phase_failure`'s declaration, the phase's retry, the tail's guard, the outcome block's arm), 1 requirement
modified (one added scenario, four existing scenarios given the case each already has), 1 guideline paragraph, 2 records
of `tools/loop-mutations.sh` (101.1 added, 76.1 re-pointed at the guarded line) — counted with
`bash tools/loop-selftest.sh --list | wc -l` (370 on the base, 371 with this change's case) and
`bash tools/loop-mutations.sh --check` (287 records, all applying); the two readings `tools/look.sh` was asked for at
plan time — `'no archived change appears'` (10 matches in 5 files) and `'answer_complete'` (29 matches in 11 files) —
are in `evidence/surface-counts.txt`, taken on the base before this change's own prose carried either phrase.

## What Changes

1. **The phase's answer is read for completeness.** `tools/loop/run.sh`: `gate_answered` — the report, the newline the
   adapter prints after every answer, and the closing line the gate prompt asks for last (`LOOP-GATE`, or the
   `LOOP-VERDICT` a report written against the older one-child contract carries). A child the run ended at a bound says
   so in its report and is read as it stands, never retried.
2. **A silence is asked once more, and the retry decides the phase.** The retry is the same prompt contract, rendered
   from the one value list into `worker-gate-prompt-retry.md` and answered into `worker-gate-report-retry.md` — the
   shape the review rounds already take (#224).
3. **The second silence is the phase's failure.** `worker-gate` is recorded `fail` with the note `exit 0 and no
   report`, `phase_failure` carries the reading, the tail is guarded on it, and the outcome block's new arm
   (`run.sh:2532`) records the item `blocked` with a reason that names the phase — never the item's tree.
4. **One case, two mutation records, one paragraph.** `run#aPhaseThatAnswersNothingIsThatPhasesFailure` asserts the two
   gate invocations, the `fail` step and its note, the recorded block, the reason naming the phase and not the archive
   or the base, that no tail ran, and that nothing was closed; `tools/loop-mutations.sh` 101.1 mutates the completeness
   reading to `if false`, and 76.1 is re-pointed at the tail guard it now quotes with `phase_failure` in it.

## What this does not do

- It does not change the run's stop for a child that never started (`run.sh:2452`, #742): an answer of nothing beside a
  tree at the base is still the run's own fault and still no item record.
- It does not retry a phase the run itself ended, nor one whose report carries `LOOP-TIMEOUT` or `LOOP-DEADLINE`: those
  reports say what ended them and a retry would spend the same bound again.
- It does not touch the tail's own answer. A tail that answers nothing is still read by the arms below it, and its
  `LOOP-VERDICT` absence is a shape no case in this tree meets; naming a phase's failure there is a second delta and is
  not claimed here.
