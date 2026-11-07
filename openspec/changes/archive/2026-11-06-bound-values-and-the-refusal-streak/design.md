# Design — where a stop bound sits, and what a stop pair predicts

## D1. A stop bound is calibrated above the population, not at its middle

The five `LOOP_CHILD_TURNS_MAX_<ROLE>` keys began at the p75 of a 12-item sample. A bound at p75 does not detect a
runaway; it ends the fourth-heaviest child by construction, and the population it was measured on is *censored* —
the sessions the bound ends can never report the length they would have had, so the estimate is biased low. Two of
the five were not merely tight but below observed *finished* sessions (`worker-gate 60` against 67–71, `worker-tail
45` against 56–60).

The rule this change records, and the one the next re-derivation follows: **a stop bound sits at or above the
population's p95, and above the observed maximum where the sample is too small for a p95.** The cost is stated
rather than hidden — one runaway worker now spends up to 170 turns (~18 Mtok at the measured 106 k tokens a turn)
before the run ends it — and the band that flags that item already exists (`LOOP_ITEM_TOKENS_WARN`, a warning and
never a stop).

Rejected: leaving the values and fixing only the verdict (#786's first half). It would have kept the run alive, and
it would still have thrown away a 4–6 k-line patch per item, three times in one evening.

## D2. `bound` is a mechanism, not a result

An item whose child the run ended at its own bound has a tree mid-phase, so its result stays `blocked` — recording
it `cut` would assert work the child never claimed, which is the reason `end-item` is skipped for such a child
(issue #271). What was missing is the *mechanism*: `blocked` was the whole record, so the state could not tell a
refusal from a machine bound, and `state.sh next` read both. `bound` is the third value of the vocabulary that
already exists for exactly this (`refused`, `unmergeable`), and one word carries three consequences: the streak
ignores it, no decision label is written, and the report names the item on the line a reader looks at for what the
next run resumes.

Rejected: a second field (`cut: true` beside `result`). A record with two verdicts is what #280 and #785 were both
about.

## D3. What the stop pair predicts

The pair's assumption is unchanged and is *the reason* only a refusal may sit in it: a refusal predicts the next
item's failure — the same gate, the same provider, the same tree. A bound predicts nothing about an item that starts
a fresh worker on a fresh tree. A block with **no** mechanism (a red gate, a tree at the base, a worker that exited
non-zero by itself) is a refusal for this purpose and keeps the old reading, which is also what makes a state
written before this change behave exactly as it did.

Rejected: a rate over the last three items (issue #786's alternative shape). It is a larger rule for the same
evidence, and a run that alternates refusal and bound would then stop where adjacency does not.

## D4. Why the escalation went with it

A bound block used to be escalated like a refusal, so it wrote `agent/needs-decision` + `status/on-hold` and spent a
decision slot from a ceiling of 10. That is how a run whose items its own bounds cut made its *next* run refuse to
work those items — the shape `#788` was filed for. The escalation exists because the state is replaced by the next
run (issue #174); a bound needs no tracker write to survive, because the item stays eligible, the branch is at the
base and the patch is kept beside the tree.
