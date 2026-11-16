# Design — a skip is no gate to re-run

## The root cause

`tools/loop/report.sh`'s `re-run the gate for #N` arm (`:961-972` on the base commit `b865087f`) is one `jq` selection
over the run's `.items`: every item whose result is not `done`, `falsified` or `cut` and whose `metrics.gate` is empty
(`:972`). The two conditions are a claim about the gate and nothing about the item behind it, and the run records four
shapes that satisfy them. Only one of those shapes had something to gate:

| arm (`tools/loop/run.sh`) | result | `--change` recorded | gate result |
|---|---|---|---|
| closed (`:3964`) | `done` | `--change "$change"` | yes |
| blocked (`:4154`) | `blocked` | `--change "$change_claim"` | empty when the gate child left no tally |
| skip (`:4043`) | `skipped` | none | empty, by construction |
| falsified (`:3992`) | `falsified` | none | empty, by construction |

So the arm's own test was half of the predicate it needed, and the missing half is the change: it is the field that says
this item reached work a gate can measure. Measured on the run of `2026-11-13T05:47:11Z` (`.pi/issue-loop/runs/`), the
three items #860, #706 and #861 carried `skipped`, an empty `change` and an empty `metrics.gate` — the arm's own
selection names all three, and the report asked a caller to re-run a gate over a tree that never held a change.

## The single fix

`select((.change // "") != "")`, one condition beside the existing gate-result test, and the comment that names which
arm writes the field (`tools/loop/report.sh:972` is the selector after this change; its comment is `:961-968`). Nothing else
moves: no state field, no engine arm, no change to what an item's record holds, and the `falsified` and `cut`
exclusions stay where they are because they were the same defect one result over (issue #788).

## The item's own direction, and why the change names a different field

The item asks for one condition too — "that the item's state holds a worker step" — on the reading that "a skipped item
holds neither" a worker step nor a gate result. A skipped item holds a worker step: `tools/loop/run.sh:2836` records
`step A --name worker` for every worker child it starts, whatever that child answered, and the state of the run the item
was filed from carries `860 A worker ok`, `706 A worker ok` and `861 A worker ok`. A condition on the worker step would
therefore have selected the three items the item wants left out, and the change would have looked applied while
printing every line the item reported. The change keeps the item's shape — one condition beside the existing test, one
case, no state field — and names the field that discriminates, verified against the same run.

## Alternatives the evidence eliminated

- **Condition on the worker step** — eliminated above: it is present on all three items, so it excludes none of them.
- **Condition on `metrics.redTest`** — eliminated: the skip arm carries a prose red-test line, so the field is not empty
  on the items this change is about, and a predicate on its emptiness would be a predicate on a sentence.
- **Condition on the item's steps reaching phase D or the gate** — eliminated as the same rule one field further away:
  no step past `A/worker` is what a skip looks like today, but the record's steps are an audit of the run's own
  progress, not the fact a report line is about, and reading them would make the action list depend on step order.
- **A new state field** — eliminated by the item itself and by the record: `change` is already the fact, written by the
  closed and blocked arms, and a second field that repeats it is a field two writers can disagree about.
- **Excluding `result == skipped` alone** (the owner's recorded decision, option "Rejected") — eliminated: it re-counts
  one result instead of reading the fact, and leaves the blocked records that carry no change named.
  `--change` is the predicate the owner recorded as the answer, and it is the one implemented.

## Blast radius

One line of `jq` and one comment in `tools/loop/report.sh`; one case added, and two existing cases' fixtures realigned in
`tools/loop/tests/loop-cases.sh` — `run#theGateColumnNamesThisItemsGateResult`, whose skipped item no longer carries
the line, and `report#anActionNamesAnItemPathThatExists`, whose second item reads the transcript path off the line and
now records the change that line requires; one paragraph of `guidelines/Backlog.recipes.md` (section 7c, "The gate and its
suite") narrowed. No other caller of the report's action list exists: `tools/loop/report.sh` is the only writer of the
`actions the caller owes:` block, and the cases and the archived evidence are its only readers.

## Rollback

Delete the added `select((.change // "") != "")` clause and its comment; the arm is the one on the base commit again,
and `run#aSkipIsNoGateToRerun` goes red, which is what the red test records. The case and the realigned fixtures are
the second half and revert with the clause.

## The measurement, and its limits

The three items were read from a state file the tree holds (`.pi/issue-loop/runs/2026-11-13T05:47:11Z.json`), not from a
render of that state: the report was not re-run over it, and what this change claims about it is what its fields say and
what the arm's own `jq` selection takes from them (three items before the clause, none after). The red test and its green
are the case run on the base and on this tree (`evidence/red-on-HEAD.log`, `evidence/green-case.log`).

## Open decisions

none
