# fix-loop-hold-states-its-reason

## Why

`tools/loop/labels.sh` wrote the hold state for a worker's skip verdict and nothing else: the verdict, the root
cause and the base it was taken at stayed in the run's machine-local item directory, and the next `init` replaced
the state wholesale. Measured 2026-10-19: `status/on-hold` landed on #109 at 10:15:36Z and the item carried **no
comment** until 17:24:25Z — a day later and from a different code path. A reader of the tracker could see that the
item was parked and not why, which is the half-written record the closure path already refuses to risk (#192).

## What changed

- The hold arms of `tools/loop/labels.sh` (`stale`, `unspecifiable`, `too-wide`, `not-spec-shaped`,
  `needs-target`) require `--reason` and `--evidence` and post them as one comment-only note through
  `tools/issues/close.sh --comment-only`, the same write path the escalation uses. No decision label is added.
- The requirement is checked **before** the state write, so a call that forgets the reason writes nothing at all
  rather than leaving a state nobody can account for.
- `tools/loop/run.sh` passes the worker's own `LOOP-SKIP-VERDICT`, `LOOP-ROOT-CAUSE` and `LOOP-RED-TEST` fields,
  because those are what the item's reader needs.

## Spec

`openspec/specs/agent-issue-loop/spec.md`, "A state the run writes is readable from the item" — added in this
pass, because the fix and its contract are one change.

## Cases and evidence

`labels#holdCarriesItsReason` (red on HEAD: a refused hold had already written `--add-label status/on-hold`),
`run#aSkipClearsTheInProgressMark` (extended: exactly one comment, carrying the worker's root cause).
Mutation 21.2.

- `evidence/green-on-fix.txt`, `evidence/revert-check.txt`, `evidence/gate.txt`

## Impact

`tools/loop/labels.sh`, `tools/loop/run.sh`, `tools/loop-cases.sh`, `tools/loop-mutations.sh`, the spec above, and
`tools/issue-tooling-cases.sh` (the closure case that had baked in the CLI's rejected spelling, #195).

Rollback: revert the commit; the labels and states already written stay valid, and a run writes the same holds as
before without the comment.
