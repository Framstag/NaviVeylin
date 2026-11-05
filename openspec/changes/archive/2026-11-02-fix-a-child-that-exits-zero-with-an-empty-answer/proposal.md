# A child that answers nothing is no part of the run's item work, whatever status it ended with

## Why

Issue **#742**. Measured 2026-11-01T23:30:01Z (the overnight window's first attempt), read from the records that
run left (`evidence/refused-provider-shape.log` quotes every command and its output):

```
$ jq -r 'select(.type=="turn_end") | .message.stopReason' .pi/issue-loop/items/739/events.jsonl | sort | uniq -c
      4 error

$ wc -c .pi/issue-loop/items/739/worker-report.md .pi/issue-loop/items/740/worker-report.md
0 .pi/issue-loop/items/739/worker-report.md
0 .pi/issue-loop/items/740/worker-report.md

$ jq -r '.items[] | "\(.issue) piExit=\(.metrics.piExit) result=\(.result) escalated=\(.escalatedLabel) note=\(.note)"' \
     .pi/issue-loop/runs/2026-11-01T23:30:01Z.json
740 piExit=0 result=blocked escalated=agent/needs-decision note=reported success but the tree is still at the base commit — nothing was committed
739 piExit=0 result=blocked escalated=agent/needs-decision note=reported success but the tree is still at the base commit — nothing was committed

$ jq -r '.run | {minutes, stop: .stop.reason, startedAt, deadlineAt} | tostring' .pi/issue-loop/runs/2026-11-01T23:30:01Z.json
{"minutes":360,"stop":"two consecutive items ended blocked","startedAt":"2026-11-01T23:30:01Z","deadlineAt":"2026-11-02T05:30:01Z"}

$ jq -r '[.items[].at] | (.[0]), (.[-1])' .pi/issue-loop/runs/2026-11-01T23:30:01Z.json
2026-11-01T23:31:30Z
2026-11-01T23:32:02Z
```

Every request both children made was refused by the provider (`429 … you (framstag) have reached your weekly usage
limit`), so each wrote an empty answer and ended with status 0. Those two records are the last two lines of a run
that started at 23:30:01Z and stopped 121 s later on *two consecutive items ended blocked*, against the 360 minutes
its own header records.

**Root cause: the guard that keeps a child which never read the item off the item's side of the line is keyed on a
non-zero status.** `tools/loop/run.sh:1655` (on the run's base commit `412ec81d`) reads

```bash
if [ "$worker_exit" -ne 0 ] && [ -z "$verdict_claim" ] && [ -z "$timeout_claim" ] && [ "$head_now" = "$base" ]; then
```

so an exit-0 child with no verdict line and a tree still at the base does not enter that arm. It falls through to the
outcome dispatch, where `run.sh:1718` (`[ "$head_now" = "$base" ]`) records it `blocked` with *"reported success but
the tree is still at the base commit — nothing was committed"* and escalates it with `agent/needs-decision` and
`status/on-hold` — the two labels the requirement's second sentence forbids for an item no part of which was read,
and both of them count against the decision cap the profile records (`tools/loop/loop.conf:20`,
`LOOP_MAX_DECISIONS_DEFAULT=10`).

**The requirement the defect contradicts** is `agent-issue-loop`'s *"One command starts a budgeted run, with no
dialogue"*, scenario *"A child that never started is the run's fault, not the item's"*
(`openspec/specs/agent-issue-loop/spec.md:71-84`): its WHEN names a *non-zero* status, which is the same
status-shaped test the code makes, and its THEN already says what an item that was never read must get — one stop
reason, no item record, no decision label, no hold. The status is what the requirement got wrong, not what the
outcome should be.

**The red case** is `run#aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault` (`tools/loop-cases.sh`) — the sibling
`run#childThatNeverStartedIsTheRunsFault`'s scenario with the status the provider leaves: the stub exits 0 and its
answer is empty (no report block, no words of its own, no action in the tree). On the base it fails at its third
assertion, *the item is not recorded as a blocked one: expected '0', got '1'* — the item is recorded and escalated,
which is the defect (`evidence/red-on-HEAD.xml`, `tests="1" failures="1"`).

## What changes

**One fix: the guard reads the item's evidence and not the child's status.** `tools/loop/run.sh`'s classification
drops the `[ "$worker_exit" -ne 0 ]` clause, so a child that wrote no verdict line and left the tree at the base is
the run's own stop whatever status it ended with; the stop reason quotes the status (see the comment in the code) and the size
of the answer (`0 B` for the refused-provider shape), because that number, not the status, is what a reader of the
reason needs in order to see why nobody was asked anything.

1. **`tools/loop/run.sh`** — the guard at 1655 loses its status clause and names the answer's size in its stop
   reason; the comment above it states the two shapes measured (argv refused, exit 1; provider refused, exit 0 with a
   0-byte answer).
2. **One case added, none rewritten** — `run#aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault`, red on the base,
   green with the fix; it asserts one child was started (so a run that started none cannot pass it), that the answer
   is 0 bytes, that no item record exists, and that nothing escalates, holds, comments on or closes the item.
3. **Two mutation records on that one line** — `27.1` re-pointed at the reworded guard (its pattern quoted the old
   line) and `27.2` added, so each shape's case must go red when the classification is made unreachable.
4. **`guidelines/Backlog.recipes.md`, the section "Refusals, records and the landing"** — the paragraph that states
   this rule says "a worker that ended non-zero";
   it now says the evidence decides and the status does not, naming #742's measurement.
5. **The capability delta** — `agent-issue-loop`, requirement *"One command starts a budgeted run, with no
   dialogue"*, one scenario added beside the existing one.

surface: 1 case added, 0 cases rewritten, 0 fixtures changed, 1 engine line edited, 2 mutation records (1 re-pointed, 1 added), 1 guideline paragraph reworded, 1 spec scenario added — `grep -rn 'z "\$verdict_claim"' tools/` → 1 site; `grep -rln 'never read the item' tools/ guidelines/ openspec/specs/` → 3 files; `grep -rn 'childThatNeverStartedIsTheRunsFault' tools/ guidelines/ openspec/specs/` → 3 files, 5 lines

The count is stated here as a line of its own because `measure.sh` reads it (`LOOP-MEASURE-SURFACE`), and the
surface is what the estimate is set against: one condition decides this rule, one case per shape of it, and the
two files that quote the line are the ones the count above names.

## Impact

| file | change |
|---|---|
| `tools/loop/run.sh` | the "never read the item" guard drops its status clause and names the answer's size |
| `tools/loop-cases.sh` | `run#aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault` (added) |
| `tools/loop-mutations.sh` | `27.1` re-pointed at the reworded line, `27.2` added for the new case |
| `guidelines/Backlog.recipes.md` | the paragraph on a child that never started — the status is not the test |
| `openspec/specs/agent-issue-loop/spec.md` | *"One command starts a budgeted run, with no dialogue"* — one scenario added (merged at archive) |

**Additive in effect, subtractive in code**: one clause removed from a condition, so a shape that used to become a
blocked item plus an escalation becomes the run's own stop. An item nothing was read from gets *fewer* writes, and the
run's honest failure leaves no question on the tracker. A run whose provider is exhausted therefore ends once, at its
own stop, instead of spending a decision slot per item and then stopping on two consecutive blocks.

**Rollback**: restore `[ "$worker_exit" -ne 0 ] && ` to the guard, the old stop reason's wording, and delete `27.2`;
`run#aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault` then fails exactly as `evidence/red-on-HEAD.xml` records, and
`27.1` matches its old pattern again.

**Not in scope** (the item's own boundary): what the *reviewer* child leaves when its provider refuses (the review
round has its own two shapes, `run.sh:1772-1790`), and re-queueing the item instead of stopping the run. The item asks
for the same treatment the requirement already gives the exit-1 shape; it does not ask to change where the run stops.

No compiled file changes: the gate for `tools/loop/**` is `tools/loop-selftest.sh`, offline, and the change is one
shell delta.
