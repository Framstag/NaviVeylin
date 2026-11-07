# Tasks — an item the tracker does not answer for ends no run

## 1. The red case first

- [x] 1.1 Write `run#oneUnreadableItemDoesNotEndTheRun` into `tools/loop-cases.sh` **before** any engine code: the
  case body, its entry in `LOOP_SHELL_CASES` and its arm in the dispatch table. It drives the `two` scenario with
  `GH_STUB_READ_FAIL="21 --json number,title,body,labels,author,state,comments"` — that candidate's number and the
  one field only the run's read asks for — and asserts the run's exit, its absent stop sentence, that its stop is
  `item cap reached (1/1)` rather than the failed read, the `unreadable` exclusion with the CLI's own words, the
  emptied queue entry, that `#22` is the one item the run spent, and that a second pre-screen pass answers for both
  candidates without putting the unreadable one back in the queue.
  - verified: `bash tools/loop-selftest.sh --case run#oneUnreadableItemDoesNotEndTheRun` with the base's engine
    restored into the tree (`git show fc9e0eb012dcfc781bf744d128acfac1cd9138f8:tools/loop/{run,state}.sh`, written
    back byte-identically after the run), 2026-11-07T21:13:59Z → **1 of 1 cases failed**, on
    `stop: gh could not read #21 — the tracker did not answer` (`evidence/red-on-HEAD.log`,
    `evidence/red-on-HEAD.xml`, `tests=1 failures=1`).

## 2. The record the run can write, and the pass that must not erase it

- [x] 2.1 `tools/loop/state.sh`: `cmd_unreadable` — refuse an issue that is not a candidate of this run's queue,
  remove it from `.queue`, and append it to `.excluded` as
  `{issue, class:"", verdict:"unreadable", reason, evidence:"", alreadyEscalated:false}` — with its dispatch arm
  beside `adoption-refused`.
  - verified: `bash -n tools/loop/state.sh` → no output, and the record's half of the case in 1.1 reads the entry
    this command writes.
- [x] 2.2 `tools/loop/state.sh`'s `cmd_queue`: carry the run's `unreadable` records over the next pre-screen's
  rewrite of `.excluded` (`kept`, which already holds the adoption's `unmergeable`) and keep their issues out of the
  queue beside the items the run has already recorded.
  - verified: the case's last assertion — the run's second pre-screen answers `2 eligible` for the same two
    candidates and `#21` is still absent from `.queue` while its `unreadable` record stands.

## 3. The arm that takes the next candidate

- [x] 3.1 `tools/loop/run.sh:1878-1882`: read the candidate through a temporary stderr file, and on failure print
  the line the pre-screen prints one level up, record the candidate with
  `loop_state unreadable "$issue" --reason …`, and `continue` the item loop instead of `stop_run` + `break`.
  - verified: `bash tools/loop-selftest.sh --case run#oneUnreadableItemDoesNotEndTheRun`, 2026-11-07T21:14:11Z →
    **PASS** (`evidence/green-on-change.log`, `evidence/green-on-change.xml`).

## 4. The guideline section that owns the rule

- [x] 4.1 `guidelines/Backlog.recipes.md` "Refusals, records and the landing" (section 7d): a paragraph stating what the run
  does with a candidate the tracker would not answer for — recorded `unreadable` in `.excluded` with the CLI's own
  words, out of the queue, no item spent, no verdict and no escalation, the next candidate worked, and the record
  kept across the next pre-screen pass — with the case that holds it and the measurement behind it.
  - verified: `tools/check-doc-routes.sh` → `all 329 section references resolve, all 7 skill references resolve`.

## 5. The delta's own declarations and records

- [x] 5.1 The case's `# files:` declaration is read by the driver before any suite runs, and the delta's own
  selection is checked against it — the gate's plan puts that step first for a delta that writes a case.
  - verified: `bash tools/loop/gate.sh --plan --base fc9e0eb012dcfc781bf744d128acfac1cd9138f8` printed
    `tools/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach` and
    `tools/loop-selftest.sh --from-git fc9e0eb012dcfc781bf744d128acfac1cd9138f8` as its two steps;
    the first printed **PASS** (`evidence/declaration-check.log`), the second
    **all 370 cases passed … — 375.29 s** at 2026-11-07T21:27:30Z (`evidence/delta-cases.log`). The suite was 369
    cases before this change, so the case added here is the only one added.
- [x] 5.2 The evidence this change cites is copied beside it as it is produced, and the change reads
  `LOOP-CHANGE-READY: ok`.
  - verified: `bash tools/check-change-readiness.sh
    openspec/changes/fix-an-unreadable-item-does-not-end-the-run` → `LOOP-CHANGE-READY: ok`;
    `openspec validate fix-an-unreadable-item-does-not-end-the-run --strict` → `is valid`;
    `tools/check-openspec-hygiene.sh` → `OpenSpec artifacts are consistent: 9 open change(s) …`;
    `evidence/` holds `red-on-HEAD.log`, `red-on-HEAD.xml`, `green-on-change.log`, `green-on-change.xml`,
    `declaration-check.log` and `delta-cases.log`.

- No compiled file is touched, so no build is owed and none is run: this delta is two engine scripts, one case and
  one guideline section, and the checks that stand in for the build are the case-declaration driver's own case and
  the shell suite's selection over the delta's declared paths (5.1). The item's gate is the gate child's, deferred
  to a fresh process after a fresh reviewer has read the diff.
