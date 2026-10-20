# Design — fix-loop-item-floor-from-measured-cost

## The root cause, measured

`tools/loop/state.sh` `cmd_next` compared the remaining minutes against one recorded constant
(`LOOP_ITEM_FLOOR_MINUTES`, `tools/loop/loop.conf`) while the same function read a state that already held
what every finished item of *this run* cost (`.items[].metrics.wallSeconds`, written by `cmd_done --wall` and
printed as the report's `spent` column). The number the refusal used was therefore the only piece of budget
arithmetic in the loop that ignored the run's own measurements.

Two measurements of the same 2026-10-19 run bound the error from both sides:

| what happened | minutes | the constant's verdict |
|---|---|---|
| #126, worker 83 min + three review rounds | 100 | **admitted** with 45 min left → half-done branch, `blocked` |
| the follow-up run's remaining tail, an item of the skip shape | 28 | **refused** although a skip costs 10–15 min |

`.pi/skills/fix-loop/REFERENCE.md` "Measured costs" states the same range for every archived change
(40–65 min), so the constant is not wrong about an *average* item; it is simply not a measurement of *this
run's* items, and it has no way to say 90 min when the run's last items cost 60 and 28 when they cost 10.

## The fix

In `cmd_next`, after the four count/deadline conditions:

```
floor = max(LOOP_ITEM_FLOOR_MINUTES, ceil(median([.items[].metrics.wallSeconds | select(. > 0)]) / 60 * 1.5))
```

- **median**, because it is the central value a single expensive item cannot move — the run's shape is one
  expensive change followed by cheap skips, and the tail must still be spendable;
- **× 1.5**, because the median of a mixed history (12, 12, 100 → median 12) describes the *typical* item and
  not the one still to come; 1.5 is the spread the measurements themselves show: 65/45 inside the documented
  change range, and 100/65 for the #126 item against that range's top. A margin at the low end of the spread
  would keep the "too low to pay for a large item" half of the defect;
- **ceil to the whole minute**, so the refusal reads as a number an operator can compare with the reference
  table, and the comparison is never rounded in the floor's favour;
- **lower bound `LOOP_ITEM_FLOOR_MINUTES`**, so a run with no history and a run of cheap items keep the
  documented floor: a median of 10 min × 1.5 = 15 min would admit an item the reference table says costs 40–65;
- **the refusal names both numbers** — the floor and where it came from — because the issue's own expectation
  is that an operator can see whether the floor is honest. That is also why the "no measurement" branch says
  so instead of printing a bare median.

The state is read, never written: `next` stays the pure predicate it was (design D5), the `.pi/issue-loop/`
state file keeps its shape, and no field is added. Nothing about the run's threading changes — every line here
runs in the one `state.sh next` process the wrapper invokes between items; there is no JVM, no dispatcher and
no lifecycle.

## Alternatives the root-cause evidence eliminated

**A. Keep the constant and raise it (e.g. 100).** Eliminated by the tail measurement: the follow-up run had 28
minutes left and a `gated-out` skip costs 10–15 min, and a 100 min floor refuses every run whose remaining
budget is below 100 — including the run that would have closed one more small change. It fixes one half of the
defect by doubling the other.

**B. Keep the constant and lower it (e.g. 30).** Eliminated by the #126 measurement: the item cost 100 min with
the whole budget ahead of it, so a 30 min floor admits it with 30 min left — the half-done branch and the
`blocked` verdict. The constant can only be tuned for one item size; the defect names two.

**C. The mean of the recorded wall times.** Eliminated by the shape of the history: the mean is the statistic
one outlier owns. `12/12/100` has a mean of 41 min → floor 62, which refuses the tail of a run whose last two
items cost 12; `12/12/12/100` → mean 34 → floor 51, which admits a 45-65 min change with 51 left. The median of
both histories is 12, the value the run can actually pay again.

**D. The median alone, without the margin.** Eliminated by #126's own item: median 55 min for `45/55/60` admits
an item with 55 min left, and the same item shape cost 100 min once its review needed three rounds — a cost
the run cannot see before it starts. "Typical" is not "affordable".

**E. The third quartile, or p90, of the recorded wall times.** Eliminated by the same history that justifies the
median: `12/12/100` has Q3 = 56 and p90 ≈ 91, so those floors are 84 and 137 min — after one expensive item the
run refuses the cheap tail that the issue was filed about. Percentiles describe the spread of the past; the
floor has to describe the next item, and the next item is usually the cheap one.

**F. Exclude skips, so the statistic measures what a change costs.** Eliminated by the pre-screen's own
numbers: two thirds of the first run's candidates were skips (REFERENCE.md), so a run of skips would have no
measurement at all and fall back to 45 — the same answer, reached by discarding data — and the item `next` is
about to start may itself be a skip (the cheap-first rule makes a gated-out item 10–15 min of work). The floor
has to hold for the mix the run is paying for.

**G. A floor per verdict (a change gets one floor, a skip another).** Eliminated because no field in the state
predicts cost: the queue entry carries the verdict, and an `eligible` item can still be gated out in 10 minutes
(the cheap-first rule exists for exactly that). A per-verdict floor would need a second recorded constant with
no measurement behind it and would still refuse the skip-shaped tail after an expensive change.

**H. Persist the floor into the state at `init`.** Rejected: the floor is a function of the items finished *so
far*, so a stored value would be stale after every `done` and would need a second writer of the state file. The
digest drift check would report the manual edit that kept it current.

## Risk assessment

- **A run whose only finished item was expensive.** The median of one sample is that sample, so the floor is
  1.5 × it: one 100 min item puts the floor at 150 (demonstrated in
  `evidence/green/floor-derivation-numbers.txt`). A run with 149 min left then stops. This is the honest
  reading of a run whose only measurement is 100 min, and the run measures its next item as soon as it
  finishes one; the alternative that avoids it (capping the floor) is alternative A, which the evidence
  eliminated. Named, accepted, and visible in the refusal line, which prints the count (`over 1 finished item`).
- **A mixed history keeps the configured minimum.** Three skips and a change give a median of 10–12 min, so the
  floor is 45 — the documented lower end of a change. A 65 min change started with 45 min left can still
  overrun. That is the pre-existing behaviour of the constant, unchanged by this fix, and the reason the
  margin exists at all where the run does have change-shaped measurements.
- **Seconds-long items in the self-test.** A stub child finishes in seconds, so a run-level case's median
  displays as `median 0 min`; the arithmetic keeps the unrounded median and the configured minimum dominates,
  so no run-level case changes behaviour. The display rounds; the bound does not.
- **Portability.** The 1.5 is an engine mechanic, not a project value: it is derived from the run's own
  numbers, not from this project's budget, so `loop.conf` still records exactly one key for it (the lower
  bound) and `tools/check-portability.sh` reports the same 42 engines naming no project value.

## Verification

- Red on HEAD `10a832f`, both cases fail on the constant and its single number:
  `the floor is the measured one, not the constant: '90 min floor' not found in: only 27 min left, below the
  45 min floor for the next item` and `the refusal names the number it compared against: 'configured minimum
  45' not found in: only 29 min left, below the 45 min floor for the next item` — `evidence/red/`.
- Green on the fix: both cases pass, and the two cases that already own this surface
  (`state#nextHonoursEveryStopCondition`, `run#stopReasonIsPrintedAndReported`) still pass —
  `evidence/green/`.
- `evidence/green/floor-derivation-numbers.txt` is the rule driven through `state.sh` on the five histories the
  design argues from (no history · skips 10/10/10 · changes 45/55/60 · mixed 12/12/12/100 · one 100),
  each with the refusal line it produced.
- `bash tools/check-portability.sh --quiet`, `openspec validate fix-loop-item-floor-from-measured-cost
  --strict` and `openspec doctor` — clean.
- Structural check instead of a build: the delta touches no compiled file (no Kotlin, Java, C++, Gradle,
  manifest or resource), so `guidelines/Build.md` §2's scope block applies and the structural check is `bash -n`
  on the edited shell files plus the self-test cases. The whole offline suite is the orchestrator's, run once
  on the merged tree.
- On-device: not applicable. No phone or car surface, no rendering, no log line and no coordinate is involved.
