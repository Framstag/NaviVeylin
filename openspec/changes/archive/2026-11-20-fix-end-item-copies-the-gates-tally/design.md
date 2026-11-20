# Design — the tally travels in the archive's own commit

## Root cause

`cmd_end_item` (`tools/loop/worktree.sh:426`) stages everything but the archive, commits the fix, stages the rest and
commits the archive. The gate's tally lives outside the change (`app/build/test-results/loop-gate`, the profile's
`LOOP_GATE_TALLY_DIR`) and is gitignored, so the archive commit carries only the XML a worker chose to copy by hand.
`carried_verification` (`tools/loop/worktree.sh:483`) reads the archived change's `evidence/` alone — the predicate is
right, it is the producer that is incomplete.

## The single fix

One helper beside `carried_verification`, and one call in `end-item` **before** the fix commit is staged:

1. the change this item was asked for is the run's own record — `state.sh get current.change`, the value
   `run.sh:2952` writes when it starts the item, which is also the archive directory's suffix (the archive prefixes
   the date, `openspec/changes/archive/<date>-<change>`);
2. when that directory's `evidence/` records no passing run by `carried_verification`'s own predicate, every `*.xml`
   under `LOOP_GATE_TALLY_DIR` is copied in, and a file of the same name already present is left as it stands;
3. a copy that cannot be made is a `loop_die`, not a silently thinner record — the run already treats a refused
   `end-item` as `uncommitted` and says so, so the failure is named where it happens.

The copy is a no-op when the change already carries a green record, and when no state names a change (a project whose
item never started) or no tally directory exists. Both are the `nothing to copy` case, not a reason to change the
item's result: the fix adds evidence, it does not judge the item.

## Blast radius

- `tools/loop/worktree.sh`: the helper and the call. No other caller of `carried_verification` changes, and
  `adopt-item`, `merge-item`, `branch-state` and the pre-screen keep reading the branch exactly as today.
- `tools/loop/tests/loop-cases.sh`: one new case (`worktree#endItemCopiesTheGatesTallyIntoTheEvidence`) and its two
  registrations; the existing leg for a red-only change
  (`run#doesNotMergeAnAttemptWithoutAGreenVerification`) is untouched and still refuses it.
- `guidelines/Backlog.recipes.md:315` section 7b: the one sentence that stated the convention names the step that keeps it.
- `openspec/specs/agent-issue-loop/spec.md`: one ADDED requirement with one scenario.

## Rollback

Revert the change: `end-item` returns to committing the archive alone, and every change whose evidence a worker filled
by hand behaves as it does today. Nothing reads the copied XML other than `carried_verification`, so a branch archived
with the copy is still readable by a tree without this change.

## Alternatives the evidence eliminated

- **Widen `carried_verification` to accept a green `LOOP-GATE:` line in an evidence log.** It is the predicate
  adoption trusts (item text, "Rejected as the fix"): a log line is easier to carry without the run that wrote it,
  and it would make a branch green on prose rather than on a testsuite.
- **Have the worker copy the tally.** That is today's convention and its failure mode is this item: a worker that
  forgets parks its own item, and the harness's own fixtures copy it by hand
  (`tools/loop/tests/loop-cases.sh:1618`, the shared `stub_write`, and eight fixtures beside it) — a rule only the
  fixtures keep is not a rule of the run.
- **Have the tail or the gate child copy it after the gate.** The archive is written by the step before the gate
  (issue #837): a change directory written after the gate is a tree the gate never measured, and the copy has to be
  inside the commit `adopt-item` reads, which only `end-item` takes.
