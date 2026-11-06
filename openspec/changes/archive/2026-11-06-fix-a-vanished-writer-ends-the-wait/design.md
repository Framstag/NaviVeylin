# Design — a wait ends when the writer it waits for is gone

## Root cause

`run_child`'s poll waits for one event — the completion record its own subshell writes — and the conditions that end
it (the run's deadline, the child's wall clock, the role's turn bound, the item's context bound, and the record
itself) are all about what the child *spends*. None asks whether the process that would write the record is still
there, so a writer killed under the wait leaves a loop with no exit until a clock: 2 700 s at the floor, more where a
project records what one item's gate costs.

## The single fix

One more condition inside the loop, read once a second beside the bounds and only while the record is absent:

    if [ ! -e "$status_file" ] && ! kill -0 "$child" 2>/dev/null; then
      ended_by=writer
      timed_out=1
      break
    fi

and one arm in the timeout block that names it in the child's transcript
(`LOOP-TIMEOUT: the child's writer (pid <n>) vanished before it wrote its completion record — the run ended the wait`).
Nothing else in the engine changes: the sweep of the change root, the leftover patch, the `bound` mechanism and the
timeout's exit status are the ones the cap and the deadline already use, and the one line the run reads as its own
timeout (`LOOP-TIMEOUT`) is the same line.

The condition is read *after* the clocks in the same whole second, so a wait whose deadline or cap has also passed is
still ended — and named — by the clock, and a record that appears between the loop condition and this reading is not
overridden by it: the `[ ! -e "$status_file" ]` is re-read here, not assumed.

## Blast radius

- `tools/loop/run.sh`: `run_child` — one condition (13 lines with its comment) and one `elif` arm (7 lines).
- `tools/loop-cases.sh`: 2 cases, their two list entries and their two dispatch entries.
- `tools/loop-mutations.sh`: record `82.1`.
- `guidelines/Backlog.recipes.md`:195 "What an item costs, and the bounds on it": one paragraph.
- `openspec/`: this change and its delta on `agent-issue-loop`.
- No clock, no bound, no configuration key, no state field and no label vocabulary changes; the status a wait the run
  ended returns is the one it already returned.

## Alternatives the evidence eliminated

- **`wait` on the child's pid.** #227 measured it: a pid bash has reaped answers nothing, and a `wait` on a pid bash no
  longer tracks returns no status — fourteen runs sat in this poll for up to 4 h 52 min. This wait takes no `wait`: the
  liveness reading is only ever used to *stop* waiting, so the shape that blocked for ever is not reachable from it.
- **The child's process group instead of the pid.** The group is a superset — a descendant that outlives its parent
  keeps the group alive, and the wait would fall back to the clock for a writer that is already gone. The subshell's
  pid *is* the writer, and the descendants are ended by the sweep the timeout path already runs.
- **A ready file written at launch, with a timeout on it.** It answers a different question — whether the child
  started — and cannot tell a child that started and is working from one whose writer died after writing it. The tree
  already carries that shape where it is the right question (`$2.progress-stop`).
- **Ending the run itself.** That is the item's second guard, and a different mechanism — the lock's lease is read only
  when it is taken (`tools/loop/state.sh:815`) and never while it is held. It owes its own red case, so it is not this
  delta: two mechanisms would make the item ineligible (the eligibility gate's condition 2).

## Rollback

Revert the two hunks of `tools/loop/run.sh`, the two cases and the mutation record: the wait returns to its clocks, no
line of the kind is written, and the item's records are written as the tree wrote them before. Nothing else reads
`ended_by=writer`; `report_field` reads `LOOP-TIMEOUT` as it always has.

## Open decisions

none
