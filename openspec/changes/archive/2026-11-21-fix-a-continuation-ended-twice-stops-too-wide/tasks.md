# Tasks — a continuation the same ceiling ends stops the item too wide

Spec: `agent-issue-loop`, the requirement "The bound on a child is priced in what it spends, not in how many turns it
takes" (delta `specs/agent-issue-loop/spec.md`). Scope: no compiled file changes, so no Gradle build and no
`gate.sh` from this session — the structural checks of the files edited stand in for it (`guidelines/Build.md` §2, the
scope block), and the gate is run once by the child after this one.

## 1. The arm that records the shape

- [x] 1.1 `tools/loop/run.sh`: `continuation_listing <report>` beside `child_ended_by_context_bound` — the last line
  the child itself wrote, with the run's own `LOOP-*` lines filtered out and trailing blank lines dropped.
  *Verified*: `bash -n tools/loop/run.sh`, and the new case's note assertion reads the line the stub child appended.
- [x] 1.2 `tools/loop/run.sh`: `continuation_too_wide` and `continuation_phases` reset per item, set where the
  continuation is started when its exit status is the child timeout and `child_ended_by_context_bound` holds for its
  report, and one outcome arm before the `last_exit` guard recording `skipped` with the verdict `too-wide`.
  *Verified*: `run#aContinuationEndedOnTheSameCeilingStopsTooWide` asserts the outcome, the verdict, the note and the
  absent stop reason; `run#aContextCutWorkerIsContinuedByItsOwnRun` still holds the arm above it.

## 2. The cases

- [x] 2.1 `tools/loop/tests/loop-cases.sh`: the case `run#aContinuationEndedOnTheSameCeilingStopsTooWide` — the first
  worker stops at the checkpoint of 143 124 with work in the tree, the continuation crosses the stop of 200 000 after
  writing the phases it could not fit, and the item is `skipped` with `too-wide`, the shaping label and the hold,
  no decision label, no closure and no third child. *Verified*: red on the base
  `8e3861c4c0c8bff22a36ff291e30385b7497f420` — `expected 'skipped', got 'cut'`
  (`evidence/red-on-HEAD-8e3861c4.xml`, `tests=1 failures=1`), green on the change
  (`evidence/after-change.xml`, `tests=1 failures=0`).
- [x] 2.2 `tools/loop/tests/loop-cases.sh`: `run#childPastItsContextBoundIsCut` rewritten — its tail asserted the
  second cut where the item now records its shape, and its note assertion names what the second ceiling measured.
  *Verified*: `evidence/affected-run_childPastItsContextBoundIsCut.xml`, `tests=1 failures=0`.
- [x] 2.3 `tools/loop/tests/loop-cases.sh`: the new case declared in `LOOP_SHELL_CASES` and dispatched by name.
  *Verified*: `driver#everyCaseDeclaresTheFilesItCanReach` passes, which is the gate plan's first line for a diff that
  writes a case.

## 3. The record a mutation reads

- [x] 3.1 `tools/loop/tests/loop-mutations.sh`: the record `956.1` puts the guards back in charge of the arm
  (`elif false; then`), which is the tree the change was filed on. *Verified*: the record is one `MUTATIONS` entry
  whose case is the one above; the gate runs `bash tools/loop/tests/loop-mutations.sh --check` and the harness itself.

## 4. The guideline paragraph

- [x] 4.1 `guidelines/Backlog.recipes.md:1690`: the "Three limits make it safe" paragraph realigned — one
  continuation per item, a continuation a context ceiling ends stops the item on `too-wide`, and the non-apply still
  leaves it `cut`. *Verified*: `tools/check-doc-routes.sh` and `tools/check-doc-values.sh` (the gate's path for a
  `guidelines/**` edit); the paragraph names the issue, the run's figures and the profile key.

## 5. The spec delta

- [x] 5.1 `openspec/changes/fix-a-continuation-ended-twice-stops-too-wide/specs/agent-issue-loop/spec.md`: the
  requirement MODIFIED with its body's second-cut sentence replaced, every other scenario carried verbatim, one
  scenario added (`aContinuationEndedOnTheSameCeilingStopsTooWide`), and one clause of
  `childPastItsContextBoundIsCut`'s scenario corrected. *Verified*: `openspec validate
  fix-a-continuation-ended-twice-stops-too-wide --strict` reports no error and no omission of a shipped scenario; its
  one warning is the requirement-length note, which `AGENTS.md` exempts for a `MODIFIED` block whose text is the
  shipped requirement kept whole.

## 6. The evidence this change cites

- [x] 6.1 `evidence/red-on-HEAD-8e3861c4.{xml,log}` — the red case on the base. *Verified*: `tests=1 failures=1`,
  2026-11-21T02:54:53Z.
- [x] 6.2 `evidence/after-change.{xml,log}` and the three `affected-run_*.{xml,log}` — the new case and the cases the
  delta reaches, copied as each was produced. *Verified*: every one reads `tests=1 failures=0`.
