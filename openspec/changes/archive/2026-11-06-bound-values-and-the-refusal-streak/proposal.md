# The turn bounds sit above the population they bound, and only a refusal holds a stop pair

## What Changes

- `tools/loop/loop.conf`: the five `LOOP_CHILD_TURNS_MAX_<ROLE>` values re-derived to the archive's p95 per role
  (worker 110 → 170, gate 60 → 80, tail 45 → 65, reviewer 55 → 75, pre-gate 55 → 75), and the derivation comment
  replaced by the rule those numbers follow.
- A block the run's own bound caused records `stopReason: bound` — the third value of the closed vocabulary — writes
  no decision label, and says in its note that the attempt waits for the next run.
- `state.sh next` reads its stop pair from refusals only, so two bound blocks hold no pair and the run continues.
- `report.sh` names a `bound` block on the line that already names the items cut short by the run's own bound.

## Why

The run at `.pi/issue-loop/run.json` (2026-11-05T21:37:13Z, 480 min, 80 items) stopped after **156 minutes and 6
items** on `two consecutive items ended blocked`. Three of its six items had their worker ended at **exactly 110
turns** — `#788` after 415 s, `#778` after 697 s, `#759` after 522 s — each leaving a 4–6 k-line patch behind
(`#788` `run.sh` + `loop-cases.sh`, `#778` nine files, `#759` `report.sh` + `state.sh`). That run was the first to
carry the per-role turn bounds, and it is the run that shows both defects at once.

**Root cause 1, the values.** `loop.conf` derived each role's bound from the **p75** of a 12-item, 12-session
sample, and a stop bound at p75 cuts a quarter of the population by construction. Re-read over the whole archive
(`evidence/role-census.py` from change `fix-role-turn-bounds`, 245 items, 2026-11-06): **77 of 346 worker sessions
(22 %) sat at or over the worker's 110**, and the sample the number came from is *censored* — the sessions the
bound ended can never report the length they would have had, so p75 is biased low. `worker-gate 60` and
`worker-tail 45` sat **below** sessions the archive shows finishing (67–71 and 56–60).

**Root cause 2, the streak.** A block the run's own bound caused was recorded `blocked` exactly as a refusal is, so
it counted in the pair `state.sh next` stops a run on, and it was escalated — `agent/needs-decision` +
`status/on-hold` — although nobody owes an answer for a phase the loop ran out of spend on. Measured across the
runs the archive kept: **five of the last six runs stopped on that streak, 758 of 3 211 budgeted minutes used
across them**, and six of that day's eight blocks were bound cuts. The four bound-blocked items of the last run
(`#788`, `#778`, `#759`, and `#786` a genuinely red gate) are all excluded from the next run as decisions.

**Root cause, one sentence:** a bound meant to end a runaway child was set where a quarter of the work sits and its
verdict was written as a refusal, so the loop's own bookkeeping became the leading stop reason of every run.

surface: 5 configuration values re-derived (one per role, none of them new), 1 mechanism value added to a closed
vocabulary (`bound`), 2 requirements modified (`A block records the mechanism that stopped it`, `An item the run's
own bound cut is recorded cut, not blocked`), 1 scenario added to each, 1 case added to the `state` group, 3 cases
extended (one of them repointed at the new contract), 2 mutation records added and 1 repointed, 1 report row
extended, 2 guideline sections repointed, 4 tracker items un-held, 1 filed (`#790`).

## What this change does not do

- It does not record a bound-ended item as `cut` (issue #786's remaining half) — the block keeps its `blocked`
  result and its tree, and only its *mechanism* is `bound`.
- It does not hand the kept patch of a cut item to the next run (issue #754).
- It does not bound a child by tokens rather than turns (issue #760).
