# Design — why the turn count and not the token stop, and why the header is part of the bound

## What the item is a fix of

Not that a bound exists — six per-role turn bounds and the context stop are #686/#784 and #760's decisions and they
stand — but that the value of one of them was read off a distribution the run's own cuts contradict, and that the run's
header could not have shown the raise.

- **The p95 the rework role got was not wrong; it was incomplete.** 100 is that role's p95 over 109 sessions (issue
  #821). What it cannot express is which bound fired on a given item, and the run of 2026-11-09T21:42:41Z gives one of
  each: #831 ended at 199 178 tokens — *under* the context stop — by the turn count, while six others ended over it.
  A bound a distribution sets is read once more against the cuts it actually makes, and this is that reading.
- **The raise is priced, not hoped for.** 16 252 700 tokens over 198 turns is 82 084 a turn; fifty turns is ≈ 4.1 Mtok,
  3.7 % of the run's 109.8 Mtok, on an item already 4 279 s in. The figures that decide whether the raise helped are the
  cut items' overshoot and the share of the run's spend after the first gate (78 % of 70.2 Mtok, 54.6 Mtok here).
- **The counter-weight is stated where the value is.** #686: the 35 items past 250 turns held 878 of 1 982 Mtok, 44 % of
  the archive's spend. One role, fifty turns — not the population.

## Why the context stop is not raised beside it

#833 recorded the refusal of raising `LOOP_ITEM_CONTEXT_MAX` and #836 re-proposed 200 000 → 250 000 and was closed
`NOT_PLANNED`. Both are about a bound at the provider's window, where a turn costs several times the archive's median
turn; a larger value there buys tail at a price the run cannot see. A turn bound of 150 costs turns **inside** that
window and changes nothing about what one turn costs. The refusal is not copied into `loop.conf` or this change: it is
extended where it already lives (`guidelines/Backlog.recipes.md` §7b) with #836 and with this run's cluster, so one
fact has one place.

## Why the header omission belongs to this item

`run.sh`'s header loop listed five roles while six keys exist, so the rework round's bound — the one this item moves —
was the one the header never printed, while `agent-roles=` in the same line named `worker-rework`. The comment above
that loop already states the invariant it breaks: the header and the `LOOP-TIMEOUT` line that ends a child cannot
disagree about the bound they apply. A raise that a run's own header does not show is a bound that cannot be audited,
which is #842's subject in one sentence. The change is one word, in the position `agent-roles=` uses, so the two tokens
can be read against each other.

The alternatives, and why not:

- **A new case reading `child-turns-max=`** — refused. `run#eachChildIsBoundedByItsOwnRolesTurns` already reads that
  token against a throwaway conf, and a second reader of the same variable is two cases that must be updated together.
  Extending it means the assertion quotes the whole six-role string, so the same line catches a dropped role and a
  moved figure.
- **Correcting §7b's five stale figures** — refused, this change's own scope. §7b states the worker, gate, tail and
  reviewer bounds as 170/80/65/75/75 while `loop.conf` records 200/100/65/110/110; that is a finding of the class #833
  landed a rule about and it gets its own item. This change adds the rework role's value and its derivation and leaves
  the five alone, so the diff is one value.

## Verification

- `run#eachChildIsBoundedByItsOwnRolesTurns` — the header's `child-turns-max=` token, quoted whole:
  `worker:20,worker-gate:100,worker-tail:65,worker-rework:150,reviewer:8,reviewer-pre-gate:110`. Red on the base engine
  twice over (the role is absent from the token, and the key reads 100).
- `state#theReworkRoundIsARoleOfItsOwn` — a rework child ended at a throwaway bound of 3 names the key and the role in
  its `LOOP-TIMEOUT` line, and the header carries that same figure for that same role, so the transcript's bound is the
  header's.
- Mutation `269.1` restores the key to 100 and reddens the run case; dropping `worker-rework` from the header loop
  reddens the same assertion by hand (quoted in the tasks), because the string is asserted whole.
