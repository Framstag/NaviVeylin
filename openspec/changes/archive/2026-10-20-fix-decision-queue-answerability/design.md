# Design

## Context

The decision queue is one label query; the run's ceiling reads the same query. Both mechanisms below add an
entry without a question, and each is a two-line change at the place that already owns the write.

## Decisions

### D1 — Where the block's question comes from

**Alternatives.** (a) The reviewer writes it always: impossible — a block can end before a review round
(child timeout, claim without commit, dirty worktree). (b) The worker writes it: it does when it *reaches* a
decision (`LOOP-DECISION`), and that path already exists and is unchanged. (c) The run states the choice the
block leaves when neither wrote one — **chosen**.

**Rationale.** (c) is the only option available in every block, and the choice it states is not invented: a
block means the item was not done and was not disproved, which leaves exactly retry or abandon. The reason
the run holds is printed as the ground for either.

**Risk.** A stated question can read as if the run recommends one answer. Mitigation: the wording names both
options, says what each costs, and says that the reason is the run's finding rather than a question the item
asked — a block can be a review refusal, and then the run *did* find something wrong with the change.

### D2 — Where the closure drops the label

**Alternatives.** (a) In `tools/loop/labels.sh close`, the loop's own path — leaves every other closure
(`close.sh` called by a person, by a sensor, by `answer.sh --retire`) carrying the label. (b) In
`tools/issues/close.sh` — **chosen**: it is the one place every closure passes through, so the invariant holds
however the item was ended.

**Risk.** A write on an item that does not need it. Mitigation: the item's own labels are read first, and
`--remove-label` is passed only when the decision label is present, so an ordinary closure writes exactly what
it wrote before.

### D3 — The ceiling is not changed

The ceiling counts labels; with the label gone the count is right. Lowering `LOOP_MAX_DECISIONS_DEFAULT`
back from 10 is a separate decision: it was raised the same day because falsified items accumulated, and the
change that stops escalating them (`falsified-items-close-in-the-loop`) still leaves genuine decisions — this
change only makes them answerable.

## Files

| file | change |
|---|---|
| `tools/loop/run.sh` | `else` branch beside the reviewer decision: the stated question |
| `tools/issues/close.sh` | `remove_args` computed from the item's labels, passed to the `update.sh --status` write |
| `tools/loop-cases.sh` | the two cases |
| `tools/loop-mutations.sh` | `29.1`, `29.2` (the second reaches `close.sh` through `../issues/`) |
| `guidelines/Backlog.md` §5 | one clause on the closure's label write |

## Verification

Red on HEAD: `run#blockedItemAsksItsOwnQuestion` (the escalation body holds no `The decision:`), and
`labels#closeRemovesTheDecisionLabel` (the stub `gh` log holds no `--remove-label agent/needs-decision`).
Green after the two hunks. Falsification: `tools/loop-mutations.sh --only "29.1 29.2"` → red-when-mutated,
restore green. Full gate: `tools/loop-selftest.sh` 120/120, `tools/issue-tooling-selftest.sh` 91/91 (it owns
`close.sh`), `tools/check-portability.sh --quiet`. No device, no model, no token.
