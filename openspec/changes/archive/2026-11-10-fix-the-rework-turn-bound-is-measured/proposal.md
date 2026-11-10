# Proposal — the rework round's turn bound is a measured 150, and the header prints it

## Why

**The reading that moves a bound.** `tools/loop/loop.conf` records six per-role turn bounds, and the rework round's
was 100 — that role's p95 over 109 sessions (issue #821). A p95 answers *how long the population runs*; it cannot
answer *which bound ended an item*, and in the run started 2026-11-09T21:42:41Z the answer differs from the
distribution: of the seven items the run cut, six died at or just past `LOOP_ITEM_CONTEXT_MAX` 200 000 — 200 949,
201 500, 201 545, 202 253, 202 372, 202 961, 0.5 % to 1.5 % over — and one, #831, died at **199 178**, 0.4 % *under*
the token stop, ended by `LOOP_CHILD_TURNS_MAX_WORKER_REWORK` at 100 turns. The turn count fired where the token stop
had not. Raising the context bound reaches six of those seven and leaves #831 exactly where it was; the one bound a
per-step increase can be priced against is this turn bound, and #831 is the only cut of that run it could reach.

**Priced from that item's own record** ⊨ (`.pi/issue-loop/run.json`, item #831: `metrics.context` 199178, `turns` 198,
`tokens` 16 252 700 → **82 084 tokens a turn**): fifty more turns is about **4.1 Mtok**, **3.7 %** of the run's
109.8 Mtok, for one item already **4 279 s** in. The counter-weight is #686: the 35 items past 250 turns held 878 of
1 982 Mtok — **44 %** of the archive's spend — which is why this is fifty turns on one role and not a raise of the
population.

**Why the token bound is not raised instead.** #833 recorded the refusal of raising `LOOP_ITEM_CONTEXT_MAX`, and #836
re-proposed 200 000 → 250 000 and was closed `NOT_PLANNED`; both are about a bound at the provider's window. A turn
bound of 150 costs turns *inside* that window and moves nothing else, so it is a different lever, and what it inherits
is their caution: a larger bound buys more of the post-gate tail, so the raise is a **measured experiment** read on the
cut items' overshoot and on the share of the run's spend that falls after the first gate (78 % of 70.2 Mtok, 54.6 Mtok
in that run) — never an assumption that #831 would have landed.

**The header could not have audited the raise.** `run.sh`'s `child_turns_bounds` loop listed five roles — `worker`,
`worker-gate`, `worker-tail`, `reviewer`, `reviewer-pre-gate` — although six keys exist, so a run's header read
`child-turns-max=worker:200,worker-gate:100,worker-tail:65,reviewer:110,reviewer-pre-gate:110` while its
`agent-roles=` token named `worker-rework`. The bound a rework child is ended at was therefore invisible in the run's
own header, against the invariant that loop's comment states (`run.sh:193`–`:195`): the header and the `LOOP-TIMEOUT`
line that ends a child cannot disagree about the bound they apply. A raise has to be readable as the turn count, which
makes the missing role part of this item rather than a follow-up.

## Blast radius

- **`tools/loop/loop.conf`** — one key, `LOOP_CHILD_TURNS_MAX_WORKER_REWORK` 100 → 150, with the derivation beside it in
  the block the neighbouring bounds use: #831's figures, the counter-weight, and the reason the context bound is not
  raised beside it (the recorded refusal is not duplicated in the configuration; it is named where it lives).
- **`tools/loop/run.sh`** — one word in the header loop (`worker-rework`, in the position `agent-roles=` uses). No
  function changes; `child_turn_bound` already resolves the role.
- **`guidelines/Backlog.recipes.md`** §7b — the section that owns a bound's *value*: the rework role's 150 in the value
  list, the derivation as a paragraph of its own, the header token's role rule, and #836 + this run's cluster appended
  to the refusal the section already records. **`guidelines/BacklogTooling.md`** §11 lists the same key, so its one
  figure moves with it (100 → 150).
- **No other key and no other bound** moves: `LOOP_ITEM_CONTEXT_MAX` stays 200 000, the five other role bounds stay
  what they are, and `guidelines/Backlog.recipes.md` §7b's figures for the other five are left stale rather than
  corrected here (reported as an item of its own; see the change's tasks).

surface: 1 key, 1 header-loop word, 2 cases extended (0 added), 1 mutation record, 1 requirement added in
`openspec/specs/agent-issue-loop/spec.md`; `grep -c 'LOOP_CHILD_TURNS_MAX_WORKER_REWORK' tools/loop/loop.conf` → 1 (the
key's own line, the derivation naming the role and not the key); `grep -c 'worker-rework' tools/loop/run.sh` → 5 (the
`agent_roles` loop and the header loop, whose two role lists now read the same, the block comment that lists the six
roles, `child_role`'s definition comment and its `*round2*` arm — the last three unchanged by this change); the two
cases are `run#eachChildIsBoundedByItsOwnRolesTurns` and `state#theReworkRoundIsARoleOfItsOwn`, both already declared
and dispatched.
