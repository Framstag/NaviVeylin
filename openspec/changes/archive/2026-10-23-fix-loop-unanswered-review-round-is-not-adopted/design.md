# Design — fix-loop-unanswered-review-round-is-not-adopted

## The shape of the defect

Adoption asks one question of a branch: was this attempt finished? The inputs it reads are the attempt's *output*
— its commit, its archived change, its green XML — and one piece of its *verdict*: the reviewer's refusal. The
verdict is stored as an absence, so silence and approval are the same value:

```
run.sh:989      run_child reviewer …                 → nothing is written about the round
run.sh:1027     PASS                                 → break; nothing is written about the round
run.sh:1079     FAIL → record-review-failure         → evidence/review-failed-round-<n>.md, committed
worktree.sh:323 refused_round=$(…refusal…) … refuse → a branch without that file is adoptable
```

A reviewer killed mid-answer therefore leaves a branch whose every checkable input says "finished": one archived
change of its own, one green verification record, no refusal. That is #249's branch, adopted and merged into
`main` at 2026-10-22 19:35 while its round-2 report was 0 bytes.

## D1 — the round is written on the branch twice

**Chosen: announce the round on the branch before the reviewer starts, and record its answer when the answer
arrives.** The branch then carries the round's whole life (`review-round-<n>-requested.md`,
`review-round-<n>-passed.md`, `review-failed-round-<n>.md`), and `carried_review_state` reads the highest round it
finds and what that round says.

The read is the conservative one, as `blocking_count`'s empty read already is for an old-shaped review: a round
that carries both an answer and a request reads as its answer, and a refusal outranks a pass. A branch with no
round at all stays adoptable, because a review that was never requested (`--review off`, or an attempt made
before this change) is not a review that never answered — refusing those would strand every branch the loop can
already land.

**Alternative A — require a pass marker and refuse a branch without one.** Eliminated by the evidence: a run
started with `--review off` writes no round at all, and it lands adoptable branches today (`loop.conf`'s
`LOOP_REVIEW_DEFAULT` is a value, not a constant). Refusing those would turn the run's own configuration into a
permanent decision, and no measurement asks for it.

**Alternative B — treat the unanswered round as "no finished attempt" and work the item again.** Eliminated by
the spec: a branch carrying one archived change of its own is a finished attempt, and starting over throws the
change away — the one failure the branch rule exists to prevent ("A branch that carries a previous attempt is
refused, named and recorded"). The silence of a reviewer is a question for the owner, and the existing
refusal-and-escalation path already carries every other inadmissible attempt.

**Alternative C — read the reviewer's report from the item's directory.** Eliminated: `.pi/issue-loop/items/<n>/`
is machine-local and is not what a later run reads (the item says so itself), and a run killed between the
reviewer's answer and the run's own write would still leave nothing on the branch.

## Blast radius

| file | change |
|---|---|
| `tools/loop/worktree.sh` | `carried_review_refusal` → `carried_review_state`; `branch_state_of` gains `review-unanswered`; `adopt-item` refuses it with the round named; new `record-review-round`; `record-review-failure` unchanged |
| `tools/loop/run.sh` | the announcement before the reviewer starts, the answer on `PASS`/zero-blocking, both guarded by `[ -n "$change" ]` so a falsification (no change, unchanged tree) still writes nothing |
| `tools/loop/prescreen.sh` | the comment naming the states; the routing already sends every state but `unfinished` to the owner |
| `tools/loop-cases.sh` | two cases added, one realigned for the two extra commits on the item branch |
| `tools/loop-mutations.sh` | records `18.3` (adoption reading the announcement as a pass) and `18.4` (the run announcing the round) |
| `guidelines/Backlog.md` §7 | the adoption paragraph states the round in both directions |

The two commits the run now adds per answered round land on the item's branch and travel with the merge, exactly
as the refusal commit does today; they are inside `**/evidence/**`, which `LOOP_REVIEW_EXCLUDE_PATHSPEC` already
keeps out of the diff a reviewer is handed, so a round's own record never reaches the next round's reviewer.

## Rollback

Revert the engine change: the three marker files are inert data (a later run of the old engine ignores them),
`record-review-failure` keeps its name and its file, and no state, label or archive shape changes. The archive
of this change carries the red run, so a revert reintroduces the defect the red case names.

## Verification

The change touches no compiled file. The structural checks are `tools/loop/gate.sh` — whose profile maps
`tools/loop/**` to `tools/loop-selftest.sh`, `*.md` and `openspec/*` to `tools/check-doc-routes.sh` and
`tools/gen-feature-list.sh --check-classification` — and `openspec validate --strict`.
