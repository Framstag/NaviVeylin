# Design: fix-a-stop-reads-the-child-it-names

## Root cause

One reader serves three sentences and one of them names a child. `item_outside_write_clause` answers from the first
reading of `worker_write_note` → `gate_write_note` → `outside_write_note`, and that order is right where the sentence
names no child: the two arms that conclude "nothing was committed" are about the item, and the change child's reading
is the item's own. The third sentence is the run's own stop, and it names the child it is about — `the child could not
start — the $last_child exited …` (`tools/loop/run.sh:1962`) — while the reader still prefers the change child's note.
A gate child's own write then goes unnamed and the change child's write is reported under the gate child's name.

The three notes are not interchangeable: `worker_write_note` and `gate_write_note` are snapshots the run takes where
the change child and the gate child end (`tools/loop/run.sh:1298`, `:1724`), and `outside_write_note` is re-set by
every `run_child` (`tools/loop/child.sh:627`), so at that stop it is the last child's own reading.

## The single fix

```bash
item_outside_write_clause() { # [<the reading taken around the child the sentence names>] → …
  local note
  if [ "$#" -gt 0 ]; then
    note="$1"
  else
    note="${worker_write_note:-}"
    [ -n "$note" ] || note="${gate_write_note:-}"
    [ -n "$note" ] || note="${outside_write_note:-}"
  fi
  [ -n "$note" ] || return 0
  …
```

and the one site that names a child passes its reading:

```bash
…$(item_outside_write_clause "${outside_write_note:-}")…
```

The argument **replaces** the order rather than seeding it. Seeding it (`${1:-${worker_write_note:-}}`) fixes the
reported shape and keeps the same defect one arm narrower: a last child that wrote nothing outside its tree would
still be reported with the change child's difference, under the words "while that child ran". An empty reading from
the child the sentence names is the fact "that child wrote nothing", and the stop then keeps its own sentence.

## Blast radius

- `tools/loop/evidence.sh`: one function, three call sites, one of them changed (`tools/loop/run.sh:1962`). The two
  arms that name no child (`:1527`, `:2082`) call it with no argument and read exactly what they read before.
- The clause's text, the arm's sentence, `stop_run`, the retry budget and the outcome of every item are untouched:
  this is the reading a sentence is built from, not a new measurement or a new verdict.
- `guidelines/Backlog.recipes.md:1225` "Refusals, records and the landing" states the rule, so the paragraph that
  owns the clause states it once.
- The spec requirement "An empty tree names the difference the run recorded" already covers all three arms and states
  that the clause places the difference on no child; it gains the rule about *which* reading a named child's reason
  carries, with one scenario per case.

## Rollback

Revert the two hunks (the function's guard and the one call site). The two new cases then fail exactly as
`evidence/red-on-HEAD-991.log` shows, which is the shape they were written from; nothing else in the tree depends on
the argument.

## Alternatives the evidence eliminated

- **Seeding the order with the argument** (`"${1:-${worker_write_note:-}}"`, as the item's own fix line proposes): it
  leaves the second half of the defect in place, and the second case is red on it. Rejected on the tree, not on taste.
- **Naming the note source in the sentence** ("recorded while the change child ran"): it makes the sentence honest
  about a reading nobody asked for, while the reader of the stop is owed the named child's difference.
- **A second reader per call site**: the same rule in three places is how the three sentences came to disagree.
- **Placing the difference on the item's branch**: #983's larger arm, and not what this item is (the reading already
  exists; only the sentence that reads it is wrong).

## Red case and mutation

- `run#aStopThatNamesAChildReadsThatChildsOwnWrite`: the change child writes `demo-owned.txt` into the project's own
  tree and commits its work; the gate child answers nothing, rewinds the branch to the base it was handed (which is
  what puts `head_now` at the base after a child committed) and writes `demo-gate-<n>.txt` there, a new name per
  attempt because the phase is asked once more and a path the first attempt wrote is no difference to the second.
  `LOOP_RETRY_MAX=0`, so the stop is reached in one pass. The reason must name `the gate worker` and `demo-gate-`, and
  must not carry `demo-owned.txt`.
- `run#aStopWhoseChildWroteNothingGetsNoClause`: the same stop with the gate child writing nothing outside its tree.
  The reason names the gate worker and carries no clause about the caller's tree and none of the change child's paths.
- Records `991.1` (drop the argument) and `991.2` (make the guard fall back) are the two halves, one case each; both
  are RED and restore green in `evidence/mutation-991.log`.
