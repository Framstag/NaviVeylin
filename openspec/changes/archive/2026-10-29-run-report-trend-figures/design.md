# Design — run-report-trend-figures

## Context

`tools/loop/report.sh` prints four sections from two sources: the state file (a JSON document `state.sh` writes)
and `git`. Its header states the rule that makes it trustworthy — every number is read from the state file or from
git, never from a report a child wrote (D11), and never from the tracker. The report is what a caller reads
*instead of* the tracker (`guidelines/Backlog.md`, "the report a run prints at its end"; spec `agent-issue-loop`).

The state already holds the per-item facts a trend needs (`tools/loop/state.sh:494`):

- `metrics.wallSeconds` — the item's wall time, `null` where none was measured;
- `metrics.reviewRounds` — the **last** round the item's review loop attempted (`tools/loop/run.sh:1680`,
  `review_rounds=$round` inside `while [ "$round" -le "$round_cap" ]`), `0` where no round ran at all.

Nothing in the state, and nothing in git, holds whether a landing reddened `main`: `state.sh done` records a
landing's branch, its commit **count** and its evidence (`tools/loop/state.sh:492`), and the `broken-main`
sensor identifies its condition by `{job, case}` (`tools/sensors/conditions/broken-main.sh:24-32`).

No thread, no lifecycle, no dispatcher: this is one `bash` script printing lines, single-threaded, and the change
adds no component.

## D1 — The block is printed in the third section, beside the actions

| option | cost | why not / chosen |
|---|---|---|
| **a. third section, beside the actions** | 3 lines before `actions the caller owes:` | **chosen** — the section a caller reads last is where a "should we keep investing" question is asked, and the two earlier sections stay exactly what they are: one run's rows and one run's spend |
| b. after the `stop:` line, with the budget summary | none | the summary answers *what this run spent*, and mixing a cross-run question into it makes a reader compare a rate against a budget left; also the table and the spend are then further away from the trend that is about them |
| c. a `report.sh --trend` mode | a second invocation | a figure nobody runs is a figure nobody reads; the caller's one command must carry it |

## D2 — The wall figures are computed over recorded walls only, and the count is printed beside them

An item with `wallSeconds: null` is an item nobody measured — a run killed mid-item, an item the pre-screen
dropped. Counting it as `0` would drag the median down and print a number that is not a measurement, which is the
rule the spend columns already keep (`-` for an unreported figure, spec "Nothing reported is printed as zero").

So: median, minimum and maximum over the items that recorded a wall, and the number of those items printed beside
them, so a reader can see whether the median is over 1 item or 30. An even count takes the mean of the two middle
values, rounded to whole seconds — the state records fractional seconds (`174.4935`), and a report that prints them
would imply a precision the loop does not have.

## D3 — The rate is read from the round's verdict, and the state now records it

**The first cut was wrong, and review round 1 caught it.** It counted the numerator as `reviewRounds == 1`, on the
reading that the count identifies the round the loop *ended* on. `run.sh:1680` sets `review_rounds=$round` before
the reviewer runs, so the count says the round was **attempted**: an item whose round 1 refused and then blocked
keeps `1`, exactly like one whose round 1 passed. Measured on the tree's own state: `#16` and `#197` are
`blocked` with `reviewRounds: 1` against `VERDICT: FAIL` reports, so the printed `9/12` was really `6/12`. The
count could not tell a refusal from a round nobody answered either: `#51` carries count 1 and its round 1 was
**cut** at the 250-turn bound (`LOOP-TIMEOUT`, no `VERDICT:`), which the run read as a refusal — review round 2's
finding, and the reason `run.sh` now records that path as `cut-short`.

| option | cost | verdict |
|---|---|---|
| **a. record the round's verdict, count from it** | `state.sh done --review-verdict` (one option, one metric key), `run.sh` passes the value it already computes at its five landing sites, the report classifies | **chosen** — `run.sh` already decides `PASS`/`NOTES`/`FAIL`/`round2-ended`/`cut-short`/`deadline`/`reviewer-wrote`/`outside-write`/`off`, and `worktree.sh record-review-round` already writes the same fact on the branch; the state was the one place it was lost |
| b. derive it: `passed = rounds == 1 and result == closed` | no schema change | rejected: it drops a round 1 that passed before a merge refusal, and it cannot tell a refused round from a falsified one — a smaller lie than the first cut, still a lie |
| c. narrow the claim to "the review loop ended in round 1" | one wording pass | rejected by the owner: the item exists because a person had to compute the loop's correctness by hand, and this figure would then answer a different question |

**The three buckets the line prints**, and why each is a distinct fact:

- *first-round pass* — `reviewVerdict` in `PASS`, `NOTES` **and** `reviewRounds == 1`. A pass at round 2 is a pass
  but not a first-round one, so it is in the denominator and not the numerator.
- *undecided* — a round ran and ended without a verdict (`cut-short`, `deadline`, `reviewer-wrote`,
  `outside-write`), or the state was written before the verdict was recorded. It is neither a pass nor a refusal,
  so it is named apart and is in no rate.
- *never reviewed* — `reviewRounds == 0`: the run blocked the item before the review phase, or review was off.
  Counting these as refusals would make the rate fall when the loop blocks more items, the opposite of what it
  measures.

**A state written before the field** prints `-` with its reason instead of a rate derived from a count that cannot
tell a pass from a refusal. **The vocabulary is closed** and validated by `state.sh` against the values `run.sh`
actually sets, so a value it does not hold is a programming fault and fails loudly rather than becoming data.

## D4 — `landings` is a pointer now; recording it is its own item

| option | cost | verdict |
|---|---|---|
| **a. name where it lives, read nothing** | 1 line; the caller types one `gh` command | **chosen** — the two-source rule survives, the report stays offline, the item stops costing a worker phase |
| b. `gh issue list --label broken-main` inside `report.sh` | network read + its failure and latency mode in every report; `gh` becomes a second source of truth | rejected: it reverses the rule for a count that still cannot attribute — the tracker knows *that* `main` was red, not *which* landing did it |
| c. record the merge sha at landing, score it once per landing | `state.sh:432` gains `--merged-sha`; `run.sh:1925,1950` pass it; a scorer reads `gh run list --branch main --commit <sha>` and writes `landedReddenedMain` back | deferred to its own item (#709): three components and a state-schema addition, and today's `landings:` line is exactly where its number will be printed |
| d. a sensor that files "the loop is too slow" | a registry row | rejected: a rate never turns green inside one run, so the condition can never clear itself and files on every tick while it holds; the threshold is a person's decision, so the number belongs where a person reads it (issue #247) |

The pointer names the command, not an issue id: the report may not invent an id it has not read.

## Risks

- **A printed line's wording becomes a contract** (the cases assert prefixes `review:`, `wall:`, `landings:`).
  Accepted: the report's lines are already asserted this way (`branches:`, `landed, removable:`,
  `over the item token bound`), and a case that reads a prefix fails loudly when the prefix moves.
- **The figures are the run's own items, so they are noisy** — a 3-item run's median is one item's wall. Mitigated
  by printing the header sentence "this run's own items; no rate is green or red in one run" and the item count on
  the wall line, so nobody reads a single run as a verdict on the loop.
- **`reviewRounds` semantics could drift** if `run.sh` ever records the rounds *after* the passing one. The rate
  does not depend on that reading any more — the verdict does — and `72.1` states the old defect as a mutation so
  a numerator taken from the count is noticed rather than argued about.
- **A state written before this change** has no `reviewVerdict`: it prints `-` with its reason, and an archived
  state (`--state <file>`) does the same. No migration, no backfill, no guessed verdict.
- **A mixed state** (a run upgraded mid-flight) carries verdicts for its later items and none for its earlier
  ones: the earlier ones read as *undecided*, which is what they are, and are in no rate.
- **A recorded verdict is a schema key the loop must keep writing.** A later change that stops passing
  `--review-verdict` turns every item into *undecided*; the figure would then print `-` rather than a wrong rate,
  and `72.8` covers the shape.

## Verification

No compiled file is touched by the report's figures, so the gate's own map selects the shell suite; the state
field does touch `tools/loop/state.sh` and `tools/loop/run.sh`, whose cases the same suite holds.

1. `report#trendFiguresFromState` — a fixture of eight items and five recorded walls: verdicts `PASS`, `NOTES`,
   `PASS` (round 2), `FAIL`, `round2-ended`, `cut-short`, `off`, and one with no verdict at all → asserts
   `review: 2/5 first rounds passed · 1 undecided · 2 never reviewed`, `wall:   median 403 s (min 41, max 1644) ·
   5 items`, and that `5/5` is absent — the refused round 1, the round that was cut and the pass at round 2 are
   not first-round passes, and `5/5` is what the count-based numerator prints on that fixture (measured).
2. `report#noRoundOrWallPrintsDashNotZero` — a state whose items carry no verdict prints `-` with its reason; the
   same run once they carry `off`/`deadline` prints `- · no round of this run ended in a verdict · 1 undecided ·
   1 never reviewed`; no wall prints `wall:   -`; nothing reads `0/0` or `median 0`.
3. `report#landingsNamedNotInvented` — the pointer line names `gh issue list --label broken-main`, and the report
   makes no tracker call: a stub `gh` on `PATH` records its argv and the case asserts the record stayed empty.
4. `tools/loop-mutations.sh` — nine records, one per invariant: `72.1` the verdict the numerator is read from
   (review round 1's finding), `72.2` the median's middle index, `72.3` the denominator, `72.4`/`72.5` the two
   dashes, `72.6` the report's offline-ness, `72.7` the undecided bucket, `72.8` the state that predates the field,
   and `73.1` the run recording a turn-bound cut as `cut-short` instead of a refusal (§D3, review round 2).
5. `state.sh done --review-verdict` refuses a value outside the closed vocabulary, so a typo in `run.sh` fails the
   run rather than becoming data. **Not asserted by a case**: no fixture passes an invalid value, so this is a
   guard in the reader's way rather than a tested rule (review round 3, NOTE).
6. Falsification: each record goes `RED` on the change and `GREEN` after the restore
   (`bash tools/loop-mutations.sh --only <ids>`).
