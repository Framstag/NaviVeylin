# Design — the answer is written where the round is read, and the sweep reads the last one

## Root cause

The review loop knows a round's answer in one arm and writes it in another. `tools/loop/run.sh` records the *pass*
beside the round it answers (`run.sh:3978`, `record-review-round … passed`), and the *refusal* only after the loop
(`run.sh:4106` decides the last round refused, `run.sh:4114` calls `record-review-failure`) — so a round that
refused and was then repaired left its announcement and no answer at all. The archive is the measurement: 156
`review-round-1-requested.md`, 76 `review-round-1-passed.md`, 62 `review-round-2-requested.md` and 2
`review-failed-round-1.md`; the 40 % round-1 refusal rate was derivable only by subtracting two markers, and only
where both survived.

Two consequences follow from the same mechanism, and both are this change's:

- `record-review-failure` (`worktree.sh:838`) commits unconditionally, so writing it in the answer's arm *and* in
  the exit arm would stage a file git already holds and die on "nothing to commit" — the exit arm has to be the
  same record, not a second write.
- `branch_carries_refused_review` (`worktree.sh:911`) read the branch for **any** `review-failed-round-*.md`.
  That was sound only while one refusal per branch was written: once every round's answer is recorded, a landed
  branch that refused round 1 and passed round 2 would be kept for ever and
  named refused, though the base carries its work and the branch's own last answer says it passed.

## The single fix

One rule — a round's answer is written when the answer arrives, and read as the round's last answer — in three
hunks:

1. **`run.sh:3982-3985`.** The arm that reads the answer gains an `elif [ -n "$change" ]` branch beside the pass,
   calling `record-review-failure` for every round, not only the last. The pass arm and the exit arm are untouched
   in kind; the exit arm (`run.sh:4114`) stays as the last-round write.
2. **`worktree.sh:848-851`.** `record-review-failure` returns early, printing `(already recorded)`, when the
   round's file is already on the branch. The exit arm therefore finds the answer the answer's own arm wrote and
   adds no commit — no second record, and no death on git's "nothing to commit".
3. **`worktree.sh:911-925`.** `branch_carries_refused_review` reads the change's carried state through
   `carried_review_state` (`worktree.sh:517`) and keeps the branch only when that state is `failed`, so the answer
   that counts is the round's **last** one. `rework-cut` keeps its own meaning (`worktree.sh:531`): a rework a
   bound ended is a phase the next attempt resumes, and its branch is not landed, so the sweep's work arm keeps it
   without being told to.

## Blast radius

- **Callers of `record-review-failure`:** two, and both are in this diff — the new answer arm and the run's exit arm
  (`run.sh:4114`). Both are idempotent after hunk 2, which is what lets the second one stay.
- **Readers of the sweep's refusal:** the local arm and the remote arm of `cmd_prune_landed`
  (`worktree.sh:982`, `worktree.sh:1025`), through one function. The refusal's own reader for adoption is
  `branch-state`'s, which already went through `carried_review_state` and is not edited here.
- **The branch's own record:** `rework-cut` and `cut` keep their precedence over `failed` in
  `carried_review_state`; no round's cut semantics move, and a cut round still writes no refusal.
- **The guideline:** section 7d of `guidelines/Backlog.recipes.md` (`guidelines/Backlog.recipes.md:1471`) states
  the sweep's refusal reading and gains the **last** round's word; section 7e
  (`guidelines/Backlog.recipes.md:1617`) states that a refusal whose rework the bound ended keeps the announcement
  and the refusal, which was untrue for a round-1 refusal before this change and is true after it.
- **Nothing else reads the marker as a count.** `report.sh`'s review line reads the run's state, not the archive, so
  the refused-round count the item's "measure after" asks for is a separate surface and is not touched here; the
  markers the archive now carries are what such a count reads.

## Rollback

Revert the three hunks; the loop returns to writing a refusal only in the exit arm, and the sweep to reading any
refusal. No state, archive or branch is written by this change, so a revert loses nothing but the markers the new
arm had already written on branches worked in the meantime — each one a true record of a round that answered no.

## Alternatives eliminated

- **Reconstruct a repaired refusal from the announcements.** `review-round-1-requested.md` with no
  `review-round-1-passed.md` and a `review-round-2-requested.md` beside it *looks* like a refusal, and it is also
  what a cut round, an unanswered round and a lost marker look like — the measurement this item came from could
  not tell those apart, which is the defect. Eliminated by the archive's own 62-versus-2 reading.
- **Keep the sweep's any-refusal reading.** With every round recorded, a landed branch whose round 1 refused and
  whose round 2 passed would be kept for ever and printed as refused — the second red case,
  `worktree#aRepairedRefusalDoesNotKeepALandedBranch`, is that branch.
- **Make `record-review-failure` refuse an existing file.** The exit arm would then die on every last-round
  refusal, which is the arm the feature already worked in. The early return is the same record written once.
- **Write a refusal for a cut round.** A cut round withdrew its announcement and no reviewer answered it
  (`carried_review_state`, issue #785); a refusal there would name a verdict nobody gave.

## Open decisions

none
