# Design — every turn lands in a bucket, and the phase line names the clock it is a share of

## Root cause

`child_usage_json` slurps the item's stream once (`tools/loop/state.sh:970`–`:1165` on the base) and takes two readings
of it. The token total is `turns` (`:974`–`:982`, the base's numbers, as every range here): every `turn_end`'s usage, in
whatever region of the stream it stands. The role split is `sessions` (`:1014`–`:1038`) followed by the `roles`
reduction (`:1049`–`:1055`): the stream is cut into phases by its `session` records, a session's role is the prompt path
its first `user` message names, and a `turn_end` that arrives while no session is open is dropped (`.cur == null → .`). The two readings are therefore over different
sets, and everything the second one drops is spend the state records in one figure and in none of the others — 24 % of
one archived run's tokens.

`report.sh` cannot repair that and does not try: it prints the trio it knows (`:514`–`:519`). The phase line has the
mirror-image defect one level up: its three buckets are disjoint and complete *for what a turn and a tool execution
measure*, and the line prints their total as if it were the clock, when the run of 2026-11-07T19:53:44Z spent 7 586 s
in them inside items whose own walls sum to 23 890 s (`metrics.phases` against `metrics.wallSeconds`).

## The single fix

**1. `sessions` keeps every turn, in the region it stands in.** The reducer opens with a region of its own —
`{prompt:null, usage:null, turns:[], orphan:true}` — so a `turn_end` before the stream's first `session` record is
collected exactly as a turn inside a session is, and a `session` record closes that region and opens a real one. A
region that collected no turn is kept by neither, so nothing else about the reducer's output moves. The `roles`
reduction maps an orphan region to the bucket `unattributed`; a session maps to `worker`, `reviewer` or `tail` as
today. `roles` therefore partitions exactly the turns `tokens` sums — the two readings are over one set by
construction, and the state's own arithmetic says so.

**1b. `roleModels` keys the same region the same way.** The model pass reads the same `$ses[]` and mapped an orphan
region to `worker` by the null prompt, so an orphan turn's model stayed under the worker while its tokens went to the
bucket: measured on item 738's own stream through the change's reader, the `roles:` line printed
`worker (mimo-v2.6-flash) 2.3 Mtok · reviewer (minimax-m3.1-flash) 3.7 Mtok · unattributed 2.3 Mtok · 8.3 Mtok`
against 5 988 239 tokens — the same 2.3 Mtok twice. Both passes now use the one role expression, so the model rows and
the bucket cannot disagree.

**2. `report.sh` prints every bucket the record holds.** The role order is the trio the line always printed, followed
by any key the record holds that the trio does not (`unattributed`, and whatever a later bucket is called), built from
the state rather than from a list in the report. Both arms of the line — the `role (model) tokens` arm and the plain
one — read that order, so a bucket a state holds is printed and the line's total is the tokens it is over. A role
whose models are recorded keeps its `(model)` rows and the bucket prints as its own name, which is the shape
`report#aRoleWhoseTurnNamedNoModelKeepsItsRow` already pins for a role no model explained.

**3. The phase line names the clock its buckets are a share of.** The denominator is the sum of `wallSeconds` over the
items that recorded a phase figure, so the numerator and the denominator are over one set of items. The line prints
`… · 12.8 s over 1 item, 30 % of the 42 s of item wall — 29.2 s of it in neither a turn nor a tool`, and where no
item of that set recorded a wall it prints `— no item wall recorded to read it against` rather than a total that reads
as the run.

Nothing else in the run moves. The record gains one bucket key beside the three; the report gains one clause on the
phase line and one ordering on the role line; no key gates anything and no outcome reads either figure.

## Blast radius

- `state.sh`: the `sessions` reducer (`:1025`–`:1051`), the `roles` and `roleModels` reductions (`:1053`–`:1088`) and
  the `select(. != null)` guard beside them (`:1172`) — the last is unchanged, because a bucket nobody spent in stays
  `null`, the rule every figure of the function keeps.
- `report.sh`: the role order (`:470`–`:535`) and the phase line (`:469`–`:502`). The numbers are this change's own, as
  the tree stands after it.
- `metrics.tokens`, `metrics.phases`, `metrics.afterGate`, `metrics.calls`, `metrics.turns` and every per-item column
  are byte-identical: the passes that compute them are the passes that computed them before. `afterGate` stays over
  the **sessions** that ran a gate — an orphan region is no session, and the split point it would give is unknown.
- A state written before this change has no `unattributed` key and prints exactly what it printed: the trio, and a
  phase line with no denominator only where the item recorded no wall.
- Rollback: restore the `sessions` reducer's `elif .cur == null then .` and its `{cur:null, …}` seed, drop
  `unattributed` from the `roles` and `roleModels` seeds and the `if .orphan` arm from both reductions, and restore the
  report's role order to the literal trio and the phase line to its two `if` arms. Every figure the tree printed before
  returns; the bucket and the denominator are additive.

## The alternatives the evidence eliminated

- **Printing the reconciliation instead of recording the bucket** (a `tokens: … · roles: …`) — the arm the item's own
  recorded decision rejects: it makes the 24 % visible and leaves it unexplained, and the record still cannot say
  which turns the split does not reach.
- **Folding the pre-session turns into the next session's role.** Eliminated by the stream: the `session` record that
  follows is not the region's owner — in `.pi/issue-loop/items/738/events.jsonl` the surviving record is one of the
  two sessions the rotation left, and the 138 turns it dropped stood before it under a prompt the stream no longer
  holds. A fold would attribute a worker's tokens to whichever session survived, which is the mis-attribution the
  bucket exists to avoid.
- **Reading the role from the child's stream file name or from the item's phase.** Eliminated by the harness: one
  item's `events.jsonl` holds every child it started (three sessions in item 808's stream, one each for worker,
  reviewer and tail), so no file-level fact names which child a turn came from.
- **A fourth phase bucket for the clock.** Eliminated by the record: the stream carries a `timestamp` on 3 of item
  808's 22 125 events, all of them `session` records, so no reader can bound the time a child spent outside a turn
  and outside a tool call — the figure would be invented, not measured.
- **Comparing the phase sum against `run.startedEpoch`–`run.stop.at`.** Eliminated by what the window holds: it also
  holds the run's own pre-screen, landings, archives and holds, so the difference would be read as a child's idle time
  when it is the run's own work. The item wall is the clock a child's phases are inside.
