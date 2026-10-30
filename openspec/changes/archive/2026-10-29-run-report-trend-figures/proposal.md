# run-report-trend-figures

## Why

**The gap.** A run's report answers three questions about **one run** (#208): which items were handled and how
each ended, what each one cost, and what the caller must now do. None of them answers the question a person asks
next — *is the loop getting faster and more often right?* A run that cost what the last one cost prints exactly
the shapes a healthy one prints. On 2026-10-21 a person answered that question by hand from
`.pi/issue-loop/run.json` and `items/*/reviewer-report-1.md` — median item wall 403 s over 9 items, round 1
refused for 29 of 40 items, two landings reddened `main` — which is a measurement nobody repeats (issue #247).

**Two of the three figures are already in the state the report reads.** `state.sh done` records
`metrics.wallSeconds` (`tools/loop/state.sh:494`) and `metrics.reviewRounds`, and `run.sh:1680` sets
`review_rounds=$round` **before the reviewer runs** — so the count is the round the loop *attempted*, not the
round it ended on (review round 1 of this change measured that, and §"Review round 1's finding" below is what
came of it). `state.sh report` prints both per item and never totalled (`tools/loop/state.sh:263`), and
`report.sh` derives no run-level figure from them.

**The third figure is in neither the state nor git.** `state.sh done` records `merged.branch`,
`merged.commits` (a *count*) and `merged.evidence`, and no commit (`tools/loop/state.sh:492`, called from
`tools/loop/run.sh:1925` and `:1950`); `git` records no verdict about `main`; and the `broken-main` sensor's
condition identity is `{job, case}`, not a commit (`tools/sensors/conditions/broken-main.sh:24-32`), so even a
tracker read could not attribute a red `main` to the landing that caused it — only name that main is red.

**The decision the owner took (#247, 2026-10-31).** Print the two figures the state holds as numbers, and name
**where** the third one lives instead of reading the tracker from the report. The attribution is its own item.

- *Rejected — a `gh` read inside `report.sh`.* It reverses "the report is read instead of the tracker"
  (`guidelines/Backlog.md` "the report a run prints at its end", spec `agent-issue-loop`, `tools/loop/report.sh`
  header), adds a network failure and latency mode to every report, and buys a count nobody can attribute — one
  number that cannot be checked.
- *Rejected — a sensor that files "the loop is slow".* A rate never turns green inside one run, so such a
  condition can never clear itself and would file on every tick while it holds; the threshold of "fast enough"
  is a person's decision, and the number belongs where a person reads it.
- *Deferred — record the attribution at landing time* (`--merged-sha`, plus one `gh run list --commit` per landed
  item writing `landedReddenedMain` back into the state, so the report prints a stored number and the
  two-source rule survives). That is a change to `state.sh`, `run.sh` and a scorer of its own: its own item, which
  this change files as #709 — this change's `landings:` line is where its number will be printed.

## What changes

`tools/loop/report.sh` gains one block in its third section, beside the actions — three lines, the first two
read from the state file it already loads and the third a pointer:

```
trend — this run's own items; no rate is green or red in one run:
  review: 2/5 first rounds passed · 1 undecided · 2 never reviewed
  wall:   median 403 s (min 41, max 1644) · 5 items
  landings: not in the run's state — nothing records the commit a landing merged, so a `main` it reddened is
            the tracker's answer: gh issue list --label broken-main
```

The rate is counted from the **verdict** a round ended on, and the state had to learn to record it: `run.sh:1680`
sets `review_rounds=$round` before the reviewer runs, so the count is the round the loop *attempted*. `state.sh`
therefore gains `--review-verdict`, `run.sh` passes the value it already computes, and the report tells three
buckets apart — a round 1 that passed, a round that ended without a verdict, and an item no round ran for.

Rules it keeps, both of them rules the report already follows for the spend columns:

- a figure nobody measured prints `-` with its reason, never `0` that reads as a measurement, never `0/0`, and
  never a rate derived from a count that cannot tell a pass from a refusal;
- a round that ended without a verdict is neither a pass nor a refusal, so it is named apart and is in no rate;
  an item the run blocked before any review is "never reviewed", not "round 1 refused";
- the report still reads only the state file and git — its `gh` lines are pointers for the caller, and no tracker
  call is made.

## Impact

| file | change |
|---|---|
| `tools/loop/report.sh` | the `trend:` block, and its header comment naming #247 and the two sources |
| `tools/loop/state.sh` | `done --review-verdict`, a closed vocabulary validated against the values `run.sh` sets, recorded as `metrics.reviewVerdict` |
| `tools/loop/run.sh` | passes its own `review_verdict` at the five sites that record an item — landing, closed, falsified, skipped, blocked |
| `tools/loop-cases.sh` | `report#trendFiguresFromState`, `report#noRoundOrWallPrintsDashNotZero`, `report#landingsNamedNotInvented` |
| `tools/loop-mutations.sh` | eight records, one per invariant: the verdict the rate is read from, the wall median, the denominator, the two dashes, the undecided bucket, the pre-field state, the absence of a tracker read |
| `openspec/specs/agent-issue-loop/spec.md` | one ADDED requirement, "The report states whether the loop is getting better", with six scenarios |
| `guidelines/Backlog.md` | one paragraph: the report answers a fourth question, how the rate is counted, and which figure it only names |

**Previous specification changed.** `agent-issue-loop` — the report's contract ("A run is recorded", "The report
names the actions the caller owes") is extended by a new requirement; no existing requirement is rewritten.

**Guideline affected.** `guidelines/Backlog.md`, "the report a run prints at its end" paragraph, which states the
report answers three questions.

**Additive, not breaking.** One new state metric key (`reviewVerdict`, `null` where a state predates it), no
configuration key, no new dependency, no network call, no phone/car/AAOS behaviour — this is the loop's tooling
only. A state written before the field prints `-` for the rate rather than a wrong one. Rollback is the revert of
one block in `report.sh` plus the option in `state.sh` and its five recording sites; nothing else reads the lines.

**Not in scope.** Making `landings` a number (its own item, filed by this change); any change to a sensor's
bound; the loop's thresholds.

## Review round 1's finding, and what it changed

Review round 1 refused the change (`LOOP-BLOCKING: 1`, `VERDICT: FAIL`, `LOOP-CORRECTIONS-ONLY: no`) on one finding:
the pass rate was counted from `reviewRounds`, and `run.sh:1680` sets that count before the reviewer runs, so an
item whose round 1 refused and then blocked was totalled as a pass. The reviewer measured it on the tree's own
state — `#16` and `#197` are `blocked` with `reviewRounds: 1` against `VERDICT: FAIL` reports, so the printed
`9/12` was `6/12`.

Round 2 refused it again on one narrower finding of the same shape: `#51` carries count 1 as well, but its round 1
was **cut** at the 250-turn bound — `LOOP-TIMEOUT` and no `VERDICT:` in its transcript — and the run read that
silence at `run.sh:1772` as a refusal, which would have recorded `FAIL` for a reviewer that never answered. So a
round the item's turn bound ends is now recorded as `cut-short` (the value the twice-cut path already used), with
its own case `run#aReviewerEndedAtItsTurnBoundIsNotARefusal` and mutation `73.1`.

The owner chose the first of the reviewer's options on 2026-10-29: record the verdict and keep the figure, rather
than narrow the claim to what the count proves. So `state.sh done` gained `--review-verdict`, `run.sh` passes the
value it already computes, the report reads the rate from it, and the reviewer's defect is now mutation `72.1` —
the numerator taken from the count — which `report#trendFiguresFromState` notices.

## Red case on HEAD

The change is improvement-shaped and the report prints none of these lines today:

```
$ grep -n "first round\|round 1\|reddened\|landings:\|trend" tools/loop/report.sh
(no match, exit 1)
```

`.pi/issue-loop/items/247/absence-probe.log`, taken 2026-10-29T09:33:28Z. The cases below are the assertions that
make the change falsifiable; each of them fails on HEAD for the absence that proves the gap.
