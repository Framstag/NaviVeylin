# Tasks

The change's own line numbers are the tree's as this change leaves it; the base is `49171faee0afa95b78925ce1e58788a0d2bc857e`.
Its `tools/loop/run.sh`, `tools/loop/worktree.sh`, `tools/loop/tests/loop-cases.sh` and
`guidelines/Backlog.recipes.md` are the tree this change edits. The red and green runs are kept in the run's
evidence directory, `.pi/issue-loop/items/975/`, beside the change root, and copied into the change's own
`evidence/`.

## 1. The red cases, before the fix

- [x] 1.1 `run#aRefusedRoundLeavesAnAnswerBesideItsAnnouncement` drives a whole run whose round 1 refuses and whose
      round 2 accepts the rework, and asserts that the archived change carries `review-round-1-requested.md` **and**
      `review-failed-round-1.md`, that the pass of round 2 is still written, and that no refusal is written for the
      round that passed. Verify, against HEAD's engine with the case as this change writes it:
      `LOOP_ENGINE=<git archive 49171fae tools/loop tools/issues> bash tools/loop/tests/loop-selftest.sh --case
      'run#aRefusedRoundLeavesAnAnswerBesideItsAnnouncement'` → `FAIL … and the round that answered no is recorded
      beside it: 'review-failed-round-1.md' not found in: …` / `loop-selftest: 1 of 1 cases failed`, exit 1
      (`.pi/issue-loop/items/975/red-on-HEAD-975.log`, 2026-11-20T00:14:25Z, transcribed as
      `evidence/red-on-HEAD-aRefusedRoundLeavesAnAnswerBesideItsAnnouncement.xml`).
- [x] 1.2 `worktree#aRepairedRefusalDoesNotKeepALandedBranch` builds the other side of the same mechanism: a branch
      whose round 1 refused, whose round 2 passed, and whose work the base carries. Verify, against HEAD's engine:
      `kept fix/issue-21-a-repaired-refusal — the reviewer refused it` / `loop-selftest: 1 of 1 cases failed`, exit 1
      (the same log, transcribed as `evidence/red-on-HEAD-aRepairedRefusalDoesNotKeepALandedBranch.xml`). The engine
      copy drives both runs, so the shared `tools/loop` is never mutated.

## 2. The answer is written when it arrives — `tools/loop/run.sh`

- [x] 2.1 The arm that reads the round's answer (`run.sh:3976`, `if [ "$review_verdict" = "PASS" ] || … = "NOTES" ]`)
      gains the refusal branch beside the pass (`run.sh:3982-3985`): `elif [ -n "$change" ]` calls
      `record-review-failure`, and its failure is printed with the same wording the pass arm uses. The exit arm
      (`run.sh:4114`) is left where it is. Verify: `run#aRefusedRoundLeavesAnAnswerBesideItsAnnouncement` and
      `run#announcesAndRecordsTheReviewRound` pass; `run#recordsAFailedReviewOnTheBranch` still records round 2's
      refusal exactly once.
- [x] 2.2 The comment above the pass arm states the new rule and why the exit arm stays (`run.sh:3970-3975`), in place
      of the sentence that stated the old one. Verify: the green run of 5.1, and the sentence no longer appears —
      `tools/look.sh 'where the run knows the round was the last one' --path tools/loop/run.sh` → no match.

## 3. The record is written once — `tools/loop/worktree.sh`

- [x] 3.1 `record-review-failure` (`worktree.sh:838`) returns early, printing
      `recorded: evidence/review-failed-round-<n>.md in <change> (already recorded)`, when the round's file is
      already on the branch (`worktree.sh:848-851`), so the run's exit arm finds the answer the answer's arm wrote
      and adds no commit. Verify: `run#recordsAFailedReviewOnTheBranch` asserts one `failed round 2` commit on the
      branch and that the run prints no `could not be recorded` for it; both green (5.1).
- [x] 3.2 The file's content and its commit message are unchanged: this hunk is a guard, not a second record.
      Verify: `git diff -U0 tools/loop/worktree.sh` shows the guard and the comment above the function, and no line
      inside the `{ … }` block that writes the file.

## 4. The sweep reads the round's last answer — `tools/loop/worktree.sh`

- [x] 4.1 `branch_carries_refused_review` (`worktree.sh:911`) asks `carried_review_state` (`worktree.sh:517`) for the
      state the branch carries and treats the branch as refused only when that state is `failed` (`worktree.sh:921`).
      Verify: `worktree#refusesABranchWhoseReviewFailed` still keeps the branch and names the round, and
      `worktree#aRepairedRefusalDoesNotKeepALandedBranch` now removes it — both green (5.1).
- [x] 4.2 `rework-cut` is unchanged as a value: `carried_review_state` still ranks it above `failed`
      (`worktree.sh:531`), so a landed branch is not this hunk's to keep or delete, and a rework the bound ended
      still reads `rework-cut`. Verify: `run#treeOfACapEndedReworkOfARefusingRoundIsNotCommitted` green, and
      `worktree#refusesABranchWhoseReviewRoundNeverAnswered` green.

## 5. The cases — `tools/loop/tests/loop-cases.sh`

- [x] 5.1 The two new cases are declared in `LOOP_SHELL_CASES` and in the driver's dispatch table, each with a
      first-line `# files:` declaration naming the files it can reach, so a delta to `run.sh` or `worktree.sh`
      selects them. Verify: `driver#everyCaseDeclaresTheFilesItCanReach` passes, and each case passes on its own in
      this tree — `bash tools/loop/tests/loop-selftest.sh --case '<name>'` (`green-on-fix-975.log`,
      2026-11-20T00:14:36Z→00:16:31Z, 12 PASS, exit 0).
- [x] 5.2 `run#recordsAFailedReviewOnTheBranch` gains the assertions the idempotent write owes — the run prints no
      `could not be recorded`, the branch carries one `failed round 2` commit, and the exit arm's own write prints
      `already recorded` — and `run#treeOfACapEndedReworkOfARefusingRoundIsNotCommitted` gains the assertion that
      the refusal the rework was repairing is on the branch beside the announcement and the rework-cut marker.
      Verify: both green (5.1).

## 6. The documents

- [x] 6.1 `guidelines/Backlog.recipes.md` section 7d (`guidelines/Backlog.recipes.md:1471`): the sweep refuses a
      branch whose change's **last** round answered `review-failed-round-*.md`, because a refusal a later round
      repaired is not the branch's verdict. Verify: the sentence is the one case 4.1 reads, green in 5.1; the
      document checks over this file (`tools/check-doc-routes.sh`, `tools/check-doc-values.sh`) are the gate's and
      are named by the profile, not run here.
- [x] 6.2 `guidelines/Backlog.recipes.md` section 7e (`guidelines/Backlog.recipes.md:1617`): a refusal whose rework
      the bound ended keeps the announcement and the refusal, and the refusal is written when the round answers, not
      only when the run ends on it. Verify: the sentence is what `run#treeOfACapEndedReworkOfARefusingRoundIsNotCommitted`
      asserts through the branch's files (5.2).
- [x] 6.3 This change's delta adds one amended requirement per surface — the round's record and the sweep's reading
      — each scenario naming exactly one case, and `.openspec.yaml` declares `x-issue: 975` so no second open change
      claims the item. Verify:
      `bash tools/check-change-readiness.sh openspec/changes/fix-a-refused-round-leaves-its-marker` →
      `LOOP-CHANGE-READY: ok`; `openspec validate fix-a-refused-round-leaves-its-marker --strict` →
      `Change 'fix-a-refused-round-leaves-its-marker' is valid`. `bash tools/check-openspec-hygiene.sh` is **not**
      green in this worktree, for a reason outside this change: `openspec/changes/run-sh-is-split-along-the-stream/`
      is an open change directory left by earlier work (created 2026-11-14) whose `.openspec.yaml` carries no
      `x-issue:` — the class the check reports, and no item of this change's.

## 7. The evidence

- [x] 7.1 The red-on-HEAD run (`red-on-HEAD-975.log`, transcribed as the two `.xml` files) and the green focused run
      (`green-on-fix-975.log`) are copied into `evidence/` at the moment they were produced, together with
      `evidence-check.log`. The tallies the proposal quotes (`tests=1 failures=1`) are the transcriptions, because
      the shell runner writes no JUnit XML of its own.
- [ ] 7.2 The gate child's: copy any further result it cites, run
      `bash tools/loop/evidence-check.sh <change dir> --root .` over `evidence/`, archive the change under
      `openspec/changes/archive/<date>-fix-a-refused-round-leaves-its-marker/`, and run `./tools/loop/gate.sh`
      over the completed tree, recording its line.
