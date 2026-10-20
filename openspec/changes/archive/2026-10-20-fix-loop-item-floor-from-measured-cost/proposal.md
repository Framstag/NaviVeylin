# fix-loop-item-floor-from-measured-cost

## Why

The loop's last stop condition — "less time remains than the next item can cost" — is one constant:
`LOOP_ITEM_FLOOR_MINUTES=45` (`tools/loop/loop.conf`), compared against the remaining minutes in
`cmd_next` of `tools/loop/state.sh`:

```sh
if [ "$minutes_left" -lt "$FLOOR_MINUTES" ]; then
  reason="only ${minutes_left} min left, below the ${FLOOR_MINUTES} min floor for the next item"
fi
```

The run, meanwhile, measures what every item actually cost and writes it into its own state
(`state.sh done --wall SECONDS` → `.items[].metrics.wallSeconds`, and the report's `spent` column). The floor
never read it. Measured 2026-10-19:

- #126 cost **100 minutes** (worker 83 min, three review rounds) and the run's clock had the whole budget
  ahead of it — a 45 min floor admits that item with 45 min left, which is the half-done branch and the
  `blocked` verdict the loop exists to avoid;
- the follow-up run then stopped with **28 minutes left** —
  `only 28 min left, below the 45 min floor for the next item` — although the documented cost of a
  `gated-out` skip is 10–15 min and a one-function self-repair change is not much more than that
  (`.pi/skills/fix-loop/REFERENCE.md`, "Measured costs": change 40–65 min, skip 10–15 min, #126 100 min).

So the same constant is too low to pay for a large item and too high to spend the tail of a run. The defect is
the missing measurement, not the number: **the floor was a project constant where the state already held the
project's own data.**

**Root cause and its evidence:** `tools/loop/state.sh` `cmd_next` compared `minutes_left` against
`FLOOR_MINUTES` alone (before this change, lines 188-214); the state it read in the same function already
carried `.items[].metrics.wallSeconds`, written by `cmd_done --wall` (lines 302-351). The red case
`state#floorIsDerivedFromTheFinishedItemsWallTimes` fails on the tree before this change with
`the floor is the measured one, not the constant: '90 min floor' not found in: only 27 min left, below the
45 min floor for the next item` (`evidence/red/floorIsDerivedFromTheFinishedItemsWallTimes.txt`).

**The single fix:** `next` derives the floor from the wall times the run recorded for its finished items —
their **median** minutes times the **1.5** spread the measurements show, rounded up to the whole minute — and
`LOOP_ITEM_FLOOR_MINUTES` stays as its **lower bound**, so a run with no history (or with a cheap one, which
a run of skips is) keeps 45. The refusal names both numbers, e.g.
`only 28 min left, below the 90 min floor for the next item (median 60 min over 3 finished items, configured
minimum 45)`. Why the median with that margin, and why not the constant, the mean, the third quartile, p90 or
a per-verdict floor, is `design.md` — the evidence there eliminates each of them.

**Spec requirement the defect contradicts:** `agent-issue-loop`, "One command starts a budgeted run, with no
dialogue" — its scenario "The run stops on its own authority" promises that the run stops when the remaining
time cannot pay for another item, and a constant that neither the run's measurements nor its own items inform
could not tell a 100-minute item from a 12-minute one.

## Cases and evidence

Two cases in `tools/loop-cases.sh`, registered in **both** the `--list` array and the dispatch map, each
driving `state.sh` directly (`init`, then three `done --wall` calls, then `next`):

- `state#floorIsDerivedFromTheFinishedItemsWallTimes` — three finished 60 min items put the floor at 90 (not
  45) and the refusal names `90 min floor`, `median 60 min over 3 finished items` and `configured minimum 45`;
  the same floor does not block a run with 180 min left; three 10 min skips keep the configured 45 as the
  lower bound; and one 100 min item among cheap ones does not raise it (the p90 failure the design eliminates).
- `state#floorWithNoHistoryIsTheConfiguredMinimum` — a run with nothing finished, and a run whose only
  finished item carries no `--wall`, both keep the configured 45, and both refusals say
  `no finished item records a wall time, configured minimum 45`.

Red on HEAD `10a832f`: `evidence/red/`. Green on the fix: `evidence/green/`, which also carries
`floor-derivation-numbers.txt`, the five histories the rule was checked against driven through `state.sh`.
The two existing cases on this surface (`state#nextHonoursEveryStopCondition`,
`run#stopReasonIsPrintedAndReported`) are re-run green in `evidence/green/existing-surface-cases.txt`. The whole
offline suite is **not** run by this change: the orchestrator runs it once on the merged tree.

## What Changes

- `tools/loop/state.sh` `cmd_next`: the floor is computed from `[.items[].metrics.wallSeconds]` — the median of
  the recorded wall times, times 1.5, rounded up, lower-bounded by `LOOP_ITEM_FLOOR_MINUTES` — and the refusal
  names the floor, the median, the item count and the configured minimum. The header comment of the file says
  so.
- `tools/loop/loop.conf`: `LOOP_ITEM_FLOOR_MINUTES` is documented as the floor's **lower bound** rather than
  the floor, with the derivation and the measurements behind the 1.5 beside it.
- `tools/loop-cases.sh`: the two cases, registered in both places.
- `.pi/skills/fix-loop/SKILL.md`: the stop condition states the measured floor and quotes the two-number
  refusal, and the "never start an item you cannot finish" rule is stated in both directions (never stop with
  time a cheap item could have used).
- `.pi/skills/fix-loop/REFERENCE.md`: "Measured costs" gains the #126 measurement (100 min) and the floor
  arithmetic with the spread it is derived with.

## Impact

Affected files, all of them the loop's own harness — no app, native or Gradle file is touched:

| file | module / area |
|---|---|
| `tools/loop/state.sh` | the run's state, budget and lock; `next` is the authority on whether an item may start |
| `tools/loop/loop.conf` | the loop's recorded configuration (`lib.sh` reads it; `state_resolve` reads the key) |
| `tools/loop-cases.sh` | the loop's offline self-test cases (`tools/loop-selftest.sh`) |
| `.pi/skills/fix-loop/SKILL.md` | the `fix-loop` skill's stop conditions and budget rules |
| `.pi/skills/fix-loop/REFERENCE.md` | the measured costs a run is planned with |

No Android module, manifest, resource, native source or JNI bridge is touched: this is not a phone, car or AAOS
change, so the parity rules, the native-build rules and the `submodule patch` / `local override` decision do
not arise. The portability seam is unchanged in kind — the one recorded key stays in `loop.conf`, and
`tools/check-portability.sh` still finds 42 engines naming no project value.

## Previous specifications changed

- `openspec/specs/agent-issue-loop/spec.md` — **MODIFIED** "One command starts a budgeted run, with no
  dialogue": two scenarios added (the floor is derived from the run's own finished items; a run with no
  measurement keeps the configured minimum). No other scenario of that requirement is re-worded.

No other capability owns the loop's budget.

## Additive or breaking, and rollback

**Additive.** One stop condition becomes stricter where the run's own numbers say items are expensive and
looser where they say items are cheap; the recorded configuration keeps its meaning as a lower bound, and no
key, argument, state field or exit code changes. A run started before this change and one started after differ
only in the floor they compare against — and both name it.

Rollback: revert this commit. The floor returns to the constant and to naming one number; no state file,
branch, label or archived change is affected, because the change writes none of them. A run in flight during a
revert is stopped first — `state.sh next` and the lock refuse a second run anyway.
