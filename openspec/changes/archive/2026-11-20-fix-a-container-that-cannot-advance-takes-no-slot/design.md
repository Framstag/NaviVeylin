# Design — the container as a unit in the pre-screen

## The root cause, one sentence

The pre-screen's candidate loop asks every question about one item — its class, its author, its status, its type, its
decisions, its branch, its citations — and no question about the container that item belongs to, so a container the run
cannot advance keeps offering children as work (`tools/loop/prescreen.sh:325`–`:714`, the per-candidate loop, and
`:743`, the ranked queue that leaves it).

## The fix, one read, one pass

Two facts make a container unadvanceable, and each is already an answer the screen holds:

1. **The run's own record.** A child of the container whose record carries `result: cut` (or the older shape,
   `result: blocked` with `stopReason: bound`) is a slot the container spent this run and a bound ended. `state.sh`
   writes `boundKey`/`boundValue` beside that mechanism (`tools/loop/state.sh:1025`–`:1026`) and the child's `parent`
   (`tools/loop/state.sh:994`), so the container is named without a tracker read. The read is `cut_within_run` at
   `tools/loop/prescreen.sh:281`, taken once beside `blocked_within_run` (`:268`) for the same reason and with the same
   guard: a `bound` record is not a prerequisite failure, so the two reads are complements of one field.
2. **The tracker's labels.** The children the screen saw under that container — the candidates, which carry the opt-in
   label whatever else they carry — of which every one is excluded with the decision verdict. They are records the loop
   has already built, so this is a grouping of `$records` by `parent` and no second read of any item.

The pass at `tools/loop/prescreen.sh:743` sets `epic-stalled` on a queued candidate whose container holds either fact,
with the reason naming what the container waits for — `#<child>` and `KEY=VALUE` for a bound, the decision items
otherwise — and `evidence` set to `#<container>` so the record says which container was refused. It runs before the
ranking, so a refused child is not ranked, not queued, and not handed to `state.sh next`.

## Blast radius

- **Verdicts.** Only candidates that would have been `eligible` change verdict, and only when their container has one of
  the two facts. `needs-decision`, `container`, `settled`, `class-feature`, `in-flight`, `blocked:#N` and every other
  refusal keeps its own clause: a child that awaits a decision is still refused as one, so the two arms never contend
  for the same record.
- **No label, no comment.** `refusal_labels` (`tools/loop/prescreen.sh:697`) names `class-feature` and `settled` only,
  and `epic-stalled` is not among them: the reason it names is already on the tracker as a label (the decision) or in
  the run's record (the cut), which is the argument `blocked` already makes for writing none (`:441`).
- **`state.sh`, `run.sh`, `report.sh`.** Untouched. `state.sh queue` stores the verdict as the free string it always
  stored (`tools/loop/state.sh:464`–`:473`), the report prints an exclusion's verdict and reason where it printed them,
  and the run's `escalate_prescreen_decisions` (`tools/loop/run.sh:385`) escalates the decision verdict alone, so a
  container's refusal is recorded and left untracked — which is what "no write of ours" means here.
- **A container with two workable children.** Screened as before while this run has cut no child of it: the rule
  answers "is every remaining child of this container a decision, or did the run already cut one of them", and nothing
  else. Once the run did cut one, the container has spent its slot and takes no further one — one slot per container
  per run, which is what the bound arm's own reason states ("it takes no further slot", `tools/loop/prescreen.sh:759`),
  so a second queued child of that container is refused beside the first.
- **The starvation the item names.** A container whose children are all decisions is refused only while those labels
  stand: the arm reads the labels every run, so the first run after a person answers queues the child again, with no
  write to withdraw. The bound arm clears with the run that recorded the cut, since `init` replaces the state.
- **No compiled file** is touched, so the Gradle suites are not owed and the loop's own suite is the gate; the run has
  deferred that gate to the gate child, so this session runs the cases its delta reaches and nothing else.

## Rollback

Revert the two hunks in `tools/loop/prescreen.sh`, the two case rows and their bodies in
`tools/loop/tests/loop-cases.sh`, the fixture directory, the mutation record `938.1` and the `guidelines/Backlog.md:87`
paragraph. Nothing in the state format, the tracker or the queue's shape changes, so a reverted tree reads every state
this one wrote exactly as before, and no state has to be migrated in either direction.

## Alternatives the evidence eliminated

- **Read the container's sub-issues from the tracker** (`gh issue view <epic> --json subIssues`). It costs one call per
  container in the run and answers a superset of what the rule needs: the children the screen has already read are the
  ones it can work, and a child that carries no opt-in label is not work under any verdict. The screen's own candidate
  set is the narrower and cheaper answer, and the fixture that drives the cases needs no new stub.
- **Refuse the container as a record of its own**, a synthesized entry in the exclusion list. `state.sh queue` writes
  one record per candidate and the report reads `.excluded` per item; an entry with no tracker item would have to be
  invented by the screen and unwound by every reader of the queue. The child's verdict carries the same sentence with
  none of that.
- **Count the item's spend or its cut rounds to decide the container is stalled.** Both are properties of the child the
  run already recorded under its own verdict (`blocked`, `cut`), and a second reading of them would make the screen a
  second judge of what a cut means — the defect the `bound` mechanism was introduced to end (issue #790).
- **Put the rule in `run.sh`'s queue loop** instead of the screen. The run re-screens when its queue empties
  (`tools/loop/run.sh:2669`), so the screen is where a container's second child is already met; a second rule in the
  run's loop would decide the same fact twice.

## Open decisions

none
