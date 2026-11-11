# The owner's decision that landed this change

Round 2 failed on one blocking finding — the gate had measured a tree the round was not reading — and the run
handed the item to the owner. The owner's answer, quoted from the item (#845, 2026-11-11T06:07:13Z):

> Decision: A — a person lands the branch, re-based onto current main. Mechanism: rebase the 8 commits of
> fix/issue-845-a-park-is-a-decision-with-no-writer-answ (base 95efb903) onto origin/main 6cf7ba50 (merge-tree
> clean at 5e7ae7ed; the earlier base d5d2e668 is now an ancestor), apply the reviewer's eight notes from
> .pi/issue-loop/items/845/reviewer-report-2.md — the four stale design.md pointers to `tools/issues/answer.sh:34-37`
> / `:110-124` / `:111-114` / `:106`, the unproduced openspec message in tasks.md 4.3, tasks.md 5.2's 33 PASS lines
> re-attributed to the round-2 tree, evidence/gate.log + gate-attribution.md + the loop-gate tallies replaced by the
> round-2 records (gate-round2.log, gate-tally-round2/), the park/parked wording in guidelines/Backlog.md section 5,
> the ADDED-vs-MODIFIED delta note against openspec/specs/decision-queue/spec.md:58, and the over-wide 'one home'
> claim narrowed to the two scripts it names (`tools/issues/create.sh:399-400` still writes the two literals) — then
> run ./tools/loop/gate.sh --base 6cf7ba50 from the shell over the final tree so the gate's LOOP-GATE-TREE diff
> equals the round's .identity digest, merge with that record as the change's gate, and close #845 with it. Edit
> nothing after the gate.

## What the landing did, and where it differs from the letter of that answer

- **The rebase was onto `8361c0fe`, not `6cf7ba50`.** `origin/main` had moved twice more since the answer was
  written (#839 and #850 merged); `6cf7ba50` is an ancestor of `8361c0fe`, and the eight commits replayed without a
  conflict.
- **The four `design.md` pointers were re-read against the landing tree, not copied.** Two of them had moved with
  the rebase: the transition block is `:109-123` and the `park)` arm is `:110-113` on this tree (`:110-124` and
  `:111-114` in the round-2 tree the answer quotes); the flag block stayed `:36-39` and the write call is `:106`.
- **The gate is over the landing tree.** `base 8361c0fe`, `HEAD 0246b5ed`, `LOOP-GATE-TREE … diff=d2255f7c45599879`
  (`evidence/gate.log:4`), `tests=243 failures=0 errors=0` (`evidence/gate.log:487`). The round-2 logs were not
  carried over: `evidence/gate.log`, `evidence/gate-attribution.md` and `evidence/loop-gate/` are this run's alone,
  because a round-2 record in the archive is what the reviewer's first note objected to in the first place.
- **The revert-check's failing reason is captured.** Note 2 of the review is not in the answer's list; it is closed
  anyway, because a record that shows the mutation and not the assertion is the weaker half of a falsification
  (`evidence/revert-check.log`, its captured block).
- **Nothing was edited after the gate**, except under `evidence/` — the pathspec
  `LOOP_REVIEW_EXCLUDE_PATHSPEC` (the profile's `:(exclude,glob)**/evidence/**`) leaves those out of the tree's
  identity, so the gate's digest still names this tree. Owed and not done here: the two UNPROVEN findings the notes
  do not settle (`tools/issues/create.sh:399-400` as a second home of the answer shape, and the predicate that
  cannot tell an answer from a park) — filed as its own item after the landing.
