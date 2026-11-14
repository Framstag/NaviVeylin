# Tasks

The line numbers are the base's (`0848c35d`) where they are a claim about the defect, and this tree's as the change
leaves it where they are a claim about the fix. Every `Verify:` sentence quotes a log in `evidence/`, and each of them
was produced before the gate this change ran; nothing here claims a green wide run.

The delta compiles nothing, so no build task is owed: the structural checks of the files this change edits are what
stands in for one (`evidence/structural-checks.log`), and the loop's own gate is `evidence/gate-run.log`.

## 1. The red cases

- [x] 1.1 `run#mergesOverADifferenceItCannotPlace` (`tools/loop/tests/loop-cases.sh`): a run whose worker's write was
      recorded and placed in nothing lands its branch anyway — `result=done`, `merged.branch` set, the base moved, the
      step `outside-write-landing` note naming `demo-owned.txt`, the console line printed at the landing, and
      `?? demo-owned.txt` still uncommitted. Verify, against `run.sh` and `worktree.sh` from the base
      (`evidence/red-on-HEAD.log`, 2026-11-13T23:25:24Z): `FAIL run#mergesOverADifferenceItCannotPlace` — `a
      difference the run placed in nothing of its own does not end the item at its landing: expected 'done', got
      'blocked'`.
- [x] 1.2 `run#anAdoptionLandsOverADifferenceItCannotPlace` (`loop-cases.sh`, the case
      `run#anAdoptionTheTreeRefusesIsRecordedAsUnmergeable` rewritten in place): the pre-screen's adoption merges over a
      difference no child of the run can have placed, closes the item, and records the paths — and still refuses under
      `--strict-outside-write yes` (no `adopted:`, base unmoved, the exclusion `unmergeable`, 0 closures), which is the
      arm the old name asserted. Verify: `FAIL run#anAdoptionLandsOverADifferenceItCannotPlace` on the base
      (`evidence/red-on-HEAD.log`) and `PASS` on this tree (`evidence/cases.log`).
- [x] 1.3 `worktree#theLandingDecidesByTheReadingItIsHanded` (`loop-cases.sh`, new): `merge-item` handed
      `--outside-write-decides no` merges and prints the `LOOP-OUTSIDE-WRITE:` line; handed `yes` it exits 5, prints
      `not clean`, leaves the base where it was and the fix off it. Verify: `FAIL
      worktree#theLandingDecidesByTheReadingItIsHanded` on the base — `a difference the run placed in nothing does not
      stop the landing: expected '0', got '5'` (`evidence/red-on-HEAD.log`) — and `PASS` (`evidence/cases.log`).
- [x] 1.4 The three declarations and their dispatch entries in `loop-cases.sh`: `LOOP_SHELL_CASES` and the `case` table
      carry `run#mergesOverADifferenceItCannotPlace`, `run#anAdoptionLandsOverADifferenceItCannotPlace` and
      `worktree#theLandingDecidesByTheReadingItIsHanded`, and the two cases this change folds away
      (`run#dirtyBaseRefusesTheMergeAndNamesThePath`, `run#doesNotCloseOnAMergeTheBaseCannotTake`) are gone. Verify:
      `PASS driver#everyCaseDeclaresTheFilesItCanReach` (`evidence/structural-checks.log`).
- [x] 1.5 The two folded cases stay covered by the cases that replace them: the dirty-tree refusal by
      `worktree#refusesToMergeOverADirtyProjectTree` (no reading — still refuses) and the adoption's
      `unmergeable` record by `state#anAdoptionRefusalIsRecordedBesideTheExclusion`. Verify: both `PASS` in
      `evidence/cases.log`. NOTE for the reader: the run-level arm "a landing refused ⇒ the item is `blocked` with
      `stopReason: unmergeable`" has no case of its own after the fold; it is the behaviour the rewrite inverts, and
      what the worktree-level refusal and the record vocabulary assert is covered separately.

## 2. The landing — `tools/loop/worktree.sh`

- [x] 2.1 `landing_args()` (`:629`) and `landing_refuses_the_dirt()` (`:651`): the reading arrives as
      `--outside-write-decides yes|no` and the paths as `--outside-write-note`, a value outside `yes|no` is a usage
      error, and the refuse path is the sentence the merge printed before this change. Verify: `bash -n
      tools/loop/worktree.sh: exit 0` (`evidence/structural-checks.log`) and the refusal arm of
      `worktree#theLandingDecidesByTheReadingItIsHanded` (`evidence/cases.log`).
- [x] 2.2 Both merges call it where they read the tree — `cmd_adopt_item` at `:709`, `cmd_merge_item` at `:752` — and
      both take the two options after `shift 2`. Verify: `worktree#refusesToMergeOverADirtyProjectTree` (a call
      carrying no reading refuses exactly as it did) and `worktree#theLandingDecidesByTheReadingItIsHanded`, both
      `PASS` in `evidence/cases.log`.
- [x] 2.3 The record the merge prints is one line: the paths, bounded as every note the run prints is (10 entries at
      120 characters, then `and <n> more`), followed by the reading the run handed in. Verify: the
      `LOOP-OUTSIDE-WRITE:` assertion of `run#mergesOverADifferenceItCannotPlace` and of
      `run#anAdoptionLandsOverADifferenceItCannotPlace`, both `PASS` in `evidence/cases.log`.

## 3. The run — `tools/loop/run.sh`

- [x] 3.1 `merge_committed_branch` (`:474`–`:511`) hands `merge-item` the readings the item's phases took — the
      worker's note first, then the gate's, then the last outside-write note; `yes` where either phase placed the
      difference; `yes` under `--strict-outside-write yes`. Verify: `run#mergesOverADifferenceItCannotPlace` (the
      placed arm refuses, the unplaced arm lands) `PASS` in `evidence/cases.log`.
- [x] 3.2 The line the merge printed becomes the item's step record `outside-write-landing` (`:507`) and is printed
      on the run's progress stream, so a reader finds the difference beside the readings it was decided with. Verify:
      the step note's contents in `run#mergesOverADifferenceItCannotPlace` (`evidence/cases.log`).
- [x] 3.3 `adopt_finished_attempt` (`:410`–`:425`) hands `adopt-item` `no` with the sentence that the pre-flight read
      the tree clean and no child of this run has started since (#798), or `yes` under `--strict-outside-write yes`.
      Verify: both arms of `run#anAdoptionLandsOverADifferenceItCannotPlace` (`evidence/cases.log`).

## 4. The Records

- [x] 4.1 `loop-mutations.sh` `58.2` re-pointed from `run#doesNotCloseOnAMergeTheBaseCannotTake` (removed) to
      `worktree#theLandingDecidesByTheReadingItIsHanded`, mutating `if [ "$landing_decides" = "no" ]` → `if false`.
      Verify: `RED    58.2  worktree#theLandingDecidesByTheReadingItIsHanded`, `restore 58.2 … green again`,
      `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0` (`evidence/mutation-58.2.log`).
- [x] 4.2 `loop-mutations.sh` `86.1` re-pointed to `run#anAdoptionLandsOverADifferenceItCannotPlace` after the case
      rename, or the record would have been orphaned (`--only 86.1` gave `RESTORE-FAILED` before the re-point).
      Verify: `RED    86.1  run#anAdoptionLandsOverADifferenceItCannotPlace`, `restore 86.1 … green again`,
      `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0` (`evidence/mutation-86.1.log`).
- [x] 4.3 Every record in the registry still applies to the file it names (365 records at the time this is archived):
      Verify: `bash tools/loop/tests/loop-mutations.sh --check` — inside the gate's `LOOP-GATE` step list in
      `evidence/gate-run.log`, and re-checked by hand when this change was archived (`finalize-by-hand.md` beside the
      item).

## 5. The rule's own documents

- [x] 5.1 `openspec/specs/agent-issue-loop/spec.md`: the two MODIFIED requirements this change's delta repeats, and
      the two `**Case**:` citations outside them re-pointed (`:2532` → `state#aBlockedItemNamesTheMechanismThatStoppedIt`,
      `:3751` → `run#anAdoptionLandsOverADifferenceItCannotPlace`), because the cases they named are folded away.
      Verify: `LOOP-CHANGE-READY: ok` (the readiness checker refuses a scenario that names no case the suite carries)
      and `PASS driver#everyCaseDeclaresTheFilesItCanReach` (`evidence/structural-checks.log`).
- [x] 5.2 `guidelines/Backlog.recipes.md` section 7a (`:256`) — "What is not relaxed is the landing rule" replaced by
      the rule itself — and section 7d (`:1166`) — what the dirt is, the three case names and mutation `58.2`. Verify:
      `tools/check-doc-values.sh`, `tools/check-doc-routes.sh` and `tools/check-todo-references.sh` in the gate's step
      list (`evidence/gate-run.log`); this change's own hand run of the two the item owes is in
      `finalize-by-hand.md`.
- [x] 5.3 `.pi/skills/create-issue/SKILL.md:54`: the example `cause` payload re-pointed from the folded-away case to
      `run#mergesOverADifferenceItCannotPlace` and a live line (`worktree.sh:653`), because the skill demands "the case
      that exercises it by the name the suite knows it". Verify: the json block reads as one pair and the case name is
      in `bash tools/loop/tests/loop-selftest.sh --list`.

## 6. The change's own artifacts

- [x] 6.1 `design.md`'s `## Open decisions` declares `none`, every delta scenario carries a `**Case**:` line, and
      `proposal.md` carries a `surface:` line quoting the command its counts are re-taken from. Verify:
      `LOOP-CHANGE-READY: ok` (`evidence/structural-checks.log`), taken when the change was a delta the run restored.
- [x] 6.2 The pre-gate review (`reviewRounds: 1`) returned `FAIL` with two blocking findings, and both were applied
      before the gate: the refusal scenario's THEN narrowed to what the two worktree cases assert (the blocked-arm
      clause deleted), and the `expected 'blocked', got 'closed'` fold quote deleted from tasks, proposal, design and
      the worker report because no log carried it. Verify: `evidence/review-pre-gate.diff` is the diff that review read,
      and the delta's refusal scenario in this archive's `specs/agent-issue-loop/spec.md` carries the narrowed THEN.
- [x] 6.3 The gate ran once over the finished tree and is **red**: `LOOP-GATE: tests=1067 failures=1 errors=0`, the one
      failure being `phase#aResultOverTheBoundIsCutAndNamed` — the wide selection's pre-existing red, attributed to
      issue #862 and not to this change's paths (`evidence/gate-run.log:1272`, `:1900`; the tree the gate measured is
      `head=e3addcf8…`, and this item's head differs from it in comments and documents only). No green wide run is
      claimed here.