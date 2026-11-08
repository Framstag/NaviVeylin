# Proposal — the run's stop reason names the refusal it stopped on

## Why

**Root cause.** The arm that reads a child whose answer carried no verdict line and whose tree is still at the run's
base commit (`tools/loop/run.sh:2459`, base `b114dc61`) builds its stop reason from the child's exit status and the
byte size of its answer alone:

```
stop_run "the child could not start — the $last_child exited $last_exit with an answer of $answer_bytes B and no verdict line, leaving the tree at the base${child_words:+: $child_words}"
```

It never reads the child's own event stream (`$LOOP_CHILD_EVENTS`, the item's `events.jsonl`), although that stream is
where a refused request is recorded — a `turn_end` on `stopReason: error` carrying the provider and the model, and an
`auto_retry_end` carrying the `finalError`. So the record a person reads cannot tell a provider that refused every
call from a child that broke.

**Evidence, verified against the tree (2026-11-08, issue #828).** The item's first stream exists and ends exactly on
the shape it describes: `.pi/issue-loop/items/85/events.jsonl` (0-byte `worker-report.md`, `stat` 2026-11-03 03:29)
ends:

```
{"type":"turn_end","message":{…,"provider":"ollama-cloud","model":"deepseek-v4.1-flash","stopReason":"error","errorMessage":"429: … weekly usage limit …"},"toolResults":[]}
{"type":"agent_end","messages":[{…"stopReason":"error"…}],"willRetry":false}
{"type":"auto_retry_end","success":false,"attempt":3,"finalError":"429: … weekly usage limit …"}
```

and the run that read it recorded as its stop reason only
`the child could not start — the worker exited 0 with an answer of 0 B and no verdict line, leaving the tree at the
base` (`evidence/red-on-HEAD.log`, this change's own red run) — naming no provider, no model and no refusal, so the same run's
#85 was read as the item's own failure on the same refusal.

The item's evidence names a second stream, `.pi/issue-loop/items/95/events.jsonl`, as ending the same way; the tree
does not bear that half out — `grep -c '"auto_retry_end"'` on it is `0` and its last line is a `needs-decision`
skip — so this change cites only #85, whose premise it can point at. The stop the item quotes names no provider
and no refusal, which is true of both.

**The single fix.** Read the refusal out of the child's own stream — a `child_refusal` reader beside `child_turns`
and `child_context` — and append it to the stop reason when the stream carries one. Nothing else about the arm
changes: the status, the answer's size and the child's last line stay where they are, and a stream that is absent,
unreadable or carries no refusal is no reading, so the reason is exactly what it was.

**The red test.** The case `run#aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault` (`tools/loop-cases.sh:7080`) is
extended, not added: it already stages the child a refused provider leaves — exit 0, no report, no action in the tree
— and this change gives it the refusal the provider left in the child's own stream and one assertion per fact the
reason must now name. Against the base's reading, one
`bash tools/loop-selftest.sh --case 'run#aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault'` run at
2026-11-08T17:07:54Z (`evidence/red-on-HEAD.log`, `evidence/red-on-HEAD.xml`):

```
run#aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault|   the stop names the provider and model the answer was refused by: 'ollama-cloud/deepseek-v4.1-flash' not found in: the child could not start — the worker exited 0 with an answer of 0 B and no verdict line, leaving the tree at the base
FAIL run#aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault
loop-selftest: 1 of 1 cases failed
```

The same case against the fix passes (`evidence/green-red-case.log`, 2026-11-08T17:23:32Z), and the invariant's own
record, `tools/loop-mutations.sh` `102.1`, empties the value the reason is built from and the case goes red again
(`evidence/mutation-102.1.log`, 17:22:48Z).

**Owning requirement.** `openspec/specs/agent-issue-loop/spec.md`, the requirement "One command starts a budgeted
run, with no dialogue" (`:11`), whose two scenarios "A child that never started is the run's fault, not the item's"
(`:71`) and "A child whose answer arrived empty is the run's fault too" (`:88`) already state the stop and the
facts its reason names. Neither states that a refusal the stream recorded is named, which is why the delta ADDS one
requirement beside them rather than rewriting either — the shape the sibling change #752 took.

**Owning guideline section.** `guidelines/Backlog.recipes.md` §7d "Refusals, records and the landing", the paragraph
"What it does with a child that never started" (`:754`), which states what the never-started shape is and what its
reason names; it gains the sentence that the reason names the provider and the refusal the stream carries.

## Surface

surface: 2 cases extended (no case added), 1 mutation record added (`102.1`), 1 engine reader and its one call site
in one file, 1 requirement added, 1 guideline paragraph — counted with
`bash tools/look.sh 'child_refusal' --path tools` → `LOOK: 3 matches in 2 files` (`tools/loop/run.sh:1389`, `:2485`,
`tools/loop-mutations.sh:993`) and `bash tools/look.sh 'could not start' --path tools` → `LOOK: 12 matches in 4
files`, of which `tools/loop/run.sh:2490` is the one stop this change widens. `bash tools/look.sh 'child_refusal'
--path tools/loop/prompts` → `0 matches`: the rule is engine, not a prompt.

## What Changes

1. **`child_refusal`** (`tools/loop/run.sh:1389`), beside `child_turns` and `child_context`: the last refused request
   in the child's event stream, printed as `<provider>/<model>: <refusal>`, read from the stream's tail (the refusal
   is the newest thing in a file that runs to megabytes) and squeezed to one line. Absent, unreadable or
   refusal-less streams print nothing.
2. **The stop reason names it.** `refused_by=$(child_refusal "${events_file:-}")` at `tools/loop/run.sh:2485`, and
   the `stop_run` at `:2490` gains `${refused_by:+ — refused by $refused_by}`.
3. **One case gains the refusal and its assertions** (`run#aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault`),
   staging the stream `.pi/issue-loop/items/85/events.jsonl` ends on and asserting the reason names the provider and
   model and quotes the refusal.
4. **One mutation record** (`tools/loop-mutations.sh` `102.1`): the reading is emptied (`refused_by=""`), the case
   must go red, the restore must go green.
5. **The guideline paragraph** (`guidelines/Backlog.recipes.md` §7d) and the delta
   (`specs/agent-issue-loop/spec.md`).

## What this does not do

- It does not change what the arm records for a child that never read the item, nor which children earn the stop:
  the status, the answer's size and the last line are what they were, and a stream with no refusal leaves the reason
  byte for byte as the base wrote it.
- It does not touch the blocked reasons. The item's title names them, but its body and evidence ask only that the
  run's own stop name the refusal it stopped on; a child that left work behind is a different reading (`run.sh:2500`
  and below), and this change does not reach it.
- It does not add a retry. #752's change caps a transient class and this reading is orthogonal to it: whatever the
  retry budget is, the stop that ends the run names the refusal it stopped on.
