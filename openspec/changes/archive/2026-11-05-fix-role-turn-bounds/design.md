# Design — bound the child, not the item

## Root cause

`run_child` compares one number against one constant:

```
if [ "$item_turns_max" -gt 0 ]; then
  turns=$(child_turns "${LOOP_CHILD_EVENTS:-}")
  if [ "$turns" -ge "$item_turns_max" ]; then … ended_by=turns
```

`LOOP_CHILD_EVENTS` is `items/<n>/events.jsonl`, and every child of the item appends to it: the worker, the round
before the gate, the gate child, the tail, each review round and the rework worker a refusal buys. So the count is
the item's, and each child is given the remainder. The old derivation (p75 over 164 four-child items) was correct
for the arithmetic it described; #758 then added a fifth phase and the arithmetic changed without the number.

## The fix

1. **A role per phase.** `child_role` already names the worker, the tail and the reviewer for the
   `LOOP_AGENT_CMD_<ROLE>` indirection. It gains the two phases that were collapsed into them: `*pre-gate*` before
   `*gate*`, and `*gate*` before the plain shapes — the order of the arms is the rule, because
   `worker-gate-prompt.md` matches `worker-*` and `reviewer-pre-gate-prompt.md` matches `reviewer-*`.
2. **One key per role.** `LOOP_CHILD_TURNS_MAX_WORKER`, `…_WORKER_GATE`, `…_WORKER_TAIL`, `…_REVIEWER`,
   `…_REVIEWER_PRE_GATE`, read through `loop_conf_value` so a project inherits no number it did not record
   (D16). A malformed or absent value is `0` — no bound, the switch the memory floor and the token band use.
3. **The child's own turns, not the stream's.** The stream is shared, so the wait compares a *difference*:
   `turns_base` is read before the fork, and the child is ended when `child_turns − turns_base ≥ its bound`.
   The transcript names the child's own count, so a reader does not have to know how many turns the phases
   before it spent. This is also what makes the bound mean what the key says: an item in its tail is not
   punished for the gate's spend.
4. **The item's total stays visible and stops nothing.** `metrics.turns` is the sum over the item's children,
   the report prints it in the `calls:` line with the two counts it was derived from, and the token band
   (`LOOP_ITEM_TOKENS_WARN`) is the run's only per-item warning. No key of the run reads an item-level turn
   limit any more.

## The numbers, and why these

The post-#758 population is ten items, and their sessions classify by the prompt path each one was started with
(the first user message of a session is the prompt file), so each child has a role and a turn count:

| role | n | p50 | p75 | p90 | max | key |
|---|---|---|---|---|---|---|
| worker | 12 (341 over the archive) | 82.5 (77) | 95 (104) | 101 (139) | 131 (219) | 110 |
| round before the gate | 10 | 40 | 46 | 52 | 55 | 55 |
| gate child | 10 | 39.5 | 50 | 67 | 71 | 60 |
| tail | 11 | 29 | 39 | 56 | 60 | 45 |
| review round | 6 (232 over the archive) | 38.5 (31) | 42 (40) | 48 (52) | 53 (107) | 55 |

Each key is just past its own population's p75, which is the rule the old 250 was derived by (`250` sat past
p75 236). The worker and the review round read the archive because their samples there are large; the three new
phases exist only after #758 and have ten or eleven children each. The review round's sample is **censored**:
#780's 53 was the count the old item bound cut, so the only round above 48 was still running when it was ended —
55 sits above every measured round for that reason, and one round above it is a deliberate margin.

The four per-role p75s add to 272 for a worker, a round before the gate, a gate child and a tail — near the old
250, which is the point: the total was never wrong, its *allocation* was.

## Alternatives rejected

- **Keep one key and raise it to 350.** The failure is not the number but that the last phase inherits the
  remainder; a larger pot moves which phase is the victim, and the day's measurements show the victim was
  usually the one that reads the finished work.
- **Give each child its own event file.** The stream is the item's archive: `state.sh` sums its children's spend
  from one file, `tools/loop-spend.py` reads it per item, and every earlier item's directory holds one
  `events.jsonl`. Splitting the file would break the report's totals for no gain — the baseline difference is
  the same count with none of the migration.
- **Bound the item *and* the child.** Two stops for one spend, and the item-level one still discards whichever
  phase crosses it. The item's total is a warning, and warnings belong where they are read: the report.
