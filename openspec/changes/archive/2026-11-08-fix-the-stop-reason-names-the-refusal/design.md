# Design — the run's stop reason names the refusal it stopped on

## Root cause

The arm that decides a child "could not start" reads three facts about the child and none about *why* its answer was
empty. On the base `b114dc61`:

```
if [ -z "$verdict_claim" ] && [ -z "$timeout_claim" ] && [ -z "$empty_tree_reading" ] && [ "$head_now" = "$base" ]; then
  child_words=$(last_line "$final_report")
  answer_bytes=$(wc -c < "$final_report" 2>/dev/null || printf 0)
  …
  stop_run "the child could not start — the $last_child exited $last_exit with an answer of $answer_bytes B and no verdict line, leaving the tree at the base${child_words:+: $child_words}"
```

(`tools/loop/run.sh:2450`–`:2459`). The child's exit status, the answer's byte size and the report's last line are all
the reason carries. The refusal is not in the report — the adapter prints only the last assistant message to stdout
and keeps the event stream in `$LOOP_CHILD_EVENTS` (`tools/pi-child-events.sh`, `run_child`'s redirect) — so the one
artifact that says a provider refused every call is the one artifact the arm does not read. Measured on the run of
2026-11-01: `.pi/issue-loop/items/85/events.jsonl` ends on a `429` weekly-limit refusal (`auto_retry_end`,
`success=false`) beside a 0-byte `worker-report.md`, and the run's recorded stop names no part of it, so a refused
provider and a broken child read the same. (The item also names `95/events.jsonl`; that stream carries no
`auto_retry_end`, so only #85 is relied on here.)

## The fix

One reader, one call, one appended clause.

**`child_refusal`** (`tools/loop/run.sh:1389`), beside the two readers of the same file, `child_turns` and
`child_context`:

```
child_refusal() { # child_refusal <events file> → "<provider>/<model>: <refusal>" for the last refused request, or empty
  local f="${1:-}" out
  [ -n "$f" ] && [ -f "$f" ] || return 0
  out=$(tail -n 200 "$f" 2>/dev/null | jq -rs '
    [ .[] ] as $e
    | ($e | map(select((.finalError? // "") != "" or (.message?.errorMessage? // "") != "" or (.message?.stopReason? // "") == "error")) | last) as $refused
    | select($refused != null)
    | ($e | map(select((.message?.provider? // "") != "")) | last) as $named
    | … | (if $who == "" then "" else $who + ": " end) + $why' 2>/dev/null) || out=""
  printf '%s' "$out"
}
```

Three design points, each one the base's own habit:

- **The tail, not the file.** The refusal is the newest event in a stream that runs to megabytes
  (`LOOP_CHILD_EVENTS_MAX_BYTES` is 16 MiB in this tree); `child_context` reads only the lines a wait has not seen,
  and this reader takes the last 200, so the stop's own cost is bounded by the same reasoning.
- **No reading is not an error.** A stream that is absent, unreadable, empty or carries no refusal prints nothing —
  exactly as `child_turns` treats one. The two reasons the arm already writes are then unchanged byte for byte, which
  is what keeps `run#childThatNeverStartedIsTheRunsFault` (the adapter-refused argv, whose stream carries no refusal)
  passing.
- **One line.** The refusal's own text may carry newlines; `gsub("[\\r\\n\\t]+"; " ")` squeezes it, so the reason
  stays one line in the state file and in the report.

**The call site** (`tools/loop/run.sh:2485`, `:2490`):

```
refused_by=$(child_refusal "${events_file:-}")
…
stop_run "…leaving the tree at the base${child_words:+: $child_words}${refused_by:+ — refused by $refused_by}"
```

`events_file` is the item's own stream, set at `run.sh:2118` and exported to every child as `LOOP_CHILD_EVENTS`, so
the reader sees the refusal whichever child of the item left it. The empty guard means a run with no refusal writes
the base's reason exactly.

## Blast radius

- `tools/loop/run.sh`: the reader and its call site — the only production change.
- `tools/loop-cases.sh`: one existing case extended with the refusal stream and two assertions; registered and
  dispatched already, so no case list changes.
- `tools/loop-mutations.sh`: record `102.1`.
- `guidelines/Backlog.recipes.md` §7d and the spec delta — the rule and its requirement.

No compiled file is touched: the delta owes `tools/loop-selftest.sh`, not Gradle.

## Alternatives eliminated

- **Parse the refusal in the outcome block that writes the item's block, and widen it there.** Rejected: the item's
  body asks only that the run's *stop* name the refusal it stopped on, the blocked arm already names the item's own
  tree, and a block that carried a refusal would state a cause for an item whose remaining evidence is the tree.
- **Read the whole stream with `jq -s`.** Rejected: the file runs to megabytes and the refusal is at its end; `tail`
  first is the shape `child_context` and `tail_after_gate` already use.
- **Print the `finalError` verbatim.** Rejected: it carries newlines and a `\"`-escaped body; saturated to one line it
  is the text a person needs and no more.
- **Store the refusal in the report rather than append it to the reason.** Rejected: there is no report — the arm
  fires because the answer was empty — and the state's own `run.stop.reason` is what a report read after the process
  is gone prints.

## Rollback

Reverting the two hunks in `tools/loop/run.sh`, the case's extended lines, the mutation record and the two documents
restores the base byte for byte and returns the case to red. The engine's behaviour, the reason's old text and the
case's `0 B` assertion are all untouched, so the rollback carries no data migration and no follow-up.
