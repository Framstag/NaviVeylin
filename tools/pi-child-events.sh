#!/usr/bin/env bash
# pi-child-events.sh — start one child agent process in its JSON event mode, keep the event stream it writes,
# and print the answer it gave.
#
# Why this file is not part of the loop engine: `tools/loop/**` may name no agent command and no mode flag
# (design D16, `tools/check-portability.sh`, the case `run#engineNamesNoProjectFact`), because the loop is
# meant to be copied into another project with its own agent. The *command* is configuration
# (`LOOP_AGENT_CMD` in `tools/loop/loop.conf`), and the one place a mode flag is a fact about this project's
# agent is this adapter, which that configuration names. The engine therefore stays a caller: it starts
# `LOOP_AGENT_CMD`, reads the answer from stdout and reads the numbers (tool calls, tokens, cost, the largest
# context) from the event file it told the adapter to fill (issue #211).
#
# The mode matters because print mode carries the final assistant text and nothing else: a run could report an
# item's verdict and wall time and not one tool call, token or context size, although the agent's own stream
# carries all three. The stream is kept whole, so a later reader can re-derive any figure without a second run.
#
# Usage: pi-child-events.sh <command> [command args…] <prompt-file>
#
#   The **last** argument is the prompt — that is where `run.sh` puts it (`$LOOP_AGENT_CMD "$prompt"`), and
#   every argument before it starts the child, so a project that needs a wrapper keeps it:
#   `LOOP_AGENT_CMD="bash tools/pi-child-events.sh sbx exec pi"`. The flags this adapter adds are appended to that
#   command, immediately before the prompt.
#
# Environment:
#   LOOP_CHILD_EVENTS  the file the event lines are appended to; `run.sh` exports one per item so the item's
#                      children are summed and read as one spend. Unset or empty: the stream is still read and
#                      the answer still printed, and nothing is kept — a child started by hand.
#   LOOP_CHILD_EVENTS_MAX_BYTES  the size past which the kept stream is rotated; unset or empty reads
#                      `LOOP_CHILD_EVENTS_MAX_BYTES` from `tools/loop/loop.conf`, and `0` keeps every line (issue
#                      #802). The value is read here rather than by `run.sh` for the reason the tool-result bound is:
#                      the adapter is the one writer of the stream, and a hand-started child gets the same bound.
#
# Exit: the child's own status, so the run's "the worker process exited N" path keeps its meaning.
set -uo pipefail

# The mode the child is started in, as separate argv words: an option parser reads `--mode` and its value,
# and a single quoted `"--mode json"` is one unknown option to it — measured 2026-10-20, when every child of a
# run died with `Error: Unknown option: --mode json` in under 4 s and the run stopped after 3 min.
mode=(--mode json)

# …and the project's `AGENTS.md` is not handed to the child at all (issue #661). The child is started in the
# project root, so the agent discovers that file there and turns it into the `project_context` section of its
# system prompt — measured at 25 624 of 33 088 characters of system prompt, 77 % of it, on the stream of one
# item (`evidence/sections-probe.py` in the change `2026-10-27-fix-a-child-carries-its-own-route`) — and that
# section is re-read on every turn of a child whose whole work is one item. `--no-context-files` disables that
# discovery and nothing else, which is why it may be the only word added here: the project's tool policy is
# resolved from the directory the process starts in, and `run.sh` still starts every child in the project (spec
# `agent-issue-loop`, "A process runs where the project's own policy applies"; case `run#childStartsInTheProjectRoot`).
# What a phase needs to know about the project is in its own prompt — `prompt#thePhasePromptsCarryTheirOwnRoute`.
context=(--no-context-files)

# …and one bound on what a single tool result may put into the child's context (issue #660). A result is injected
# whole and re-read by every later turn, and nothing in this tree between the tool and the context bounded it: the
# archive these children wrote carried 59.2 M characters of tool output and re-read 876.4 M tokens of it, 53 % of
# everything it spent (`python3 tools/loop-spend.py`). The extension is this tree's, beside this adapter, and every
# child is started through it. The bound is the value `tools/loop/loop.conf` records — **read** here and not sourced,
# the same shape `tools/loop/lib.sh`'s `loop_tmp_dir_for` uses for another tree's configuration — so the recorded
# number has one home; `0`, or a project that records no key, is no bound, and the extension then hands every
# result over unchanged.
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
extension=(-e "$here/pi-trim-result.mjs")
# …and the shell does not answer a tree question (issue #687): 9 338 of the archive's 16 987 `ctx_shell` calls (55 %)
# carry a search or read verb — `grep` 4 997, `head` 4 955, `ls` 2 550 — at a p50 of 52 per item, and they chain
# (`grep -> grep` 1 017x in 132 items). The tools that answer them are there and unused (`ctx_grep` 361 calls against
# ~1 700 shell `grep`s), so the policy sits here, where every child is started, and not in a prompt: the second
# extension refuses the call and names `tools/look.sh`, `ctx_grep` or `ctx_read` instead, leaving `sed -n 'N,Mp'`,
# `tail -n` and a filter of another command's output alone. Its own rule is `tools/pi-shell-refusal.mjs`, and its
# cases are `tools/loop/tests/loop-cases.sh`'s `policy#*` (the rule) and `adapter#theChildIsStartedWithTheShellPolicy` (this line).
policy=(-e "$here/pi-shell-refusal.mjs")
# …and the directory this project's agent keeps its own tools in is on the child's `PATH` (issue #796). The child
# inherits the run's environment and nothing between the two guarantees it carries that directory: `rg` lives there,
# and 282 of 482 `ctx_grep` calls over this run archive answered `zsh:1: command not found: rg`
# (`evidence/ctx-grep-census.py`), so a run started from a context whose `PATH` lacks it hands every worker no `rg` —
# the binary `ctx_grep` and the agent's own subprocesses look for, while `tools/look.sh` keeps working without it. This
# adapter is the one place a fact about this project's agent may live (design D16, `tools/check-portability.sh`), and
# the directory is a fact about the agent, not about the loop — so it is prepended here, only when it exists, and
# never twice.
for agent_bin in "${PI_CODING_AGENT_DIR:+$PI_CODING_AGENT_DIR/bin}" "${HOME:-}/.pi/agent/bin"; do
  [ -n "$agent_bin" ] && [ -d "$agent_bin" ] || continue
  case ":${PATH:-}:" in
    *":$agent_bin:"*) ;;
    *) export PATH="$agent_bin:${PATH:-/usr/local/bin:/usr/bin:/bin}" ;;
  esac
  break
done
bound=$(sed -n 's/^LOOP_TOOL_RESULT_MAX_CHARS=["]\{0,1\}\([0-9][0-9]*\)["]\{0,1\}[[:space:]]*$/\1/p' \
  "$here/loop/loop.conf" 2>/dev/null | tail -1)
export LOOP_TOOL_RESULT_MAX_CHARS="${bound:-0}"

if [ $# -lt 2 ]; then
  printf 'pi-child-events: usage: %s <command> [args…] <prompt-file>\n' "$(basename "$0")" >&2
  exit 2
fi
prompt="${!#}"        # the last argument
cmd=("${@:1:$#-1}")   # …and everything before it starts the child

# How large the kept stream may grow before its oldest events are dropped (issue #802). Measured 2026-11-06 over
# this archive: 254 `events.jsonl` files held 3 483 MiB — p50 11.84 MiB, p90 29.88 MiB, one 63.47 MiB — and the file
# grows for the item's whole life with no bound anywhere in this path: `LOOP_TOOL_RESULT_MAX_CHARS` bounds a result
# the child sees, not the line that is kept. A bound above the median item's *whole* history leaves an ordinary item
# its stream untouched and caps only the tail that makes the archive gigabytes (census:
# `evidence/archive-census.py`). `0`, or a project that records no key, is no bound.
events_bound=${LOOP_CHILD_EVENTS_MAX_BYTES:-}
if [ -z "$events_bound" ]; then
  events_bound=$(sed -n 's/^LOOP_CHILD_EVENTS_MAX_BYTES=["]\{0,1\}\([0-9][0-9]*\)["]\{0,1\}[[:space:]]*$/\1/p' \
    "$here/loop/loop.conf" 2>/dev/null | tail -1)
fi
case "$events_bound" in ''|*[!0-9]*) events_bound=0 ;; esac

events="${LOOP_CHILD_EVENTS:-}"
if [ -n "$events" ]; then
  mkdir -p "$(dirname "$events")" || {
    printf 'pi-child-events: cannot create %s for the event stream\n' "$(dirname "$events")" >&2
    exit 2
  }
  # What a bound cut is a fact of the item, not of the child's memory (issue #660): the record lands beside the
  # child's own event stream, which `run.sh` puts in the item's directory, so a reader of the item can see which
  # result was trimmed and re-read the range instead of trusting a cut line.
  export LOOP_TOOL_RESULT_TRIM_RECORD="$(dirname "$events")/tool-result-trims.log"
fi

# The answer is held in a file and not in a variable because the reader below runs in a pipeline, whose
# subshell would discard it: the stream has to be consumed as it is produced (a reader that stops reading
# stalls the child when the pipe buffer fills), and the answer is printed once the child is done.
answer_file=$(mktemp) || { printf 'pi-child-events: cannot create a temporary file\n' >&2; exit 2; }
trap 'rm -f "$answer_file"' EXIT

# Rotate the kept stream rather than let it grow without bound. The **delta** lines go first and every record line
# stays; only a stream that is still over its bound after that pass is cut at its oldest half. Either way the drop is
# *recorded* as the first line, so a reader can tell a rotated stream from a short one and add the turns it no longer
# holds — `run.sh`'s `child_turns` does, which is what keeps a child's turn bound counting from zero after a rotation.
#
# Why the deltas go first (issue #840): the oldest half of a stream is where the records live, and the records are
# what `state.sh`'s `child_usage_json` sums an item's `turns`, `tokens`, `phases`, `roles` and `afterGate` from — so a
# cut decided by line position made a rotated item's spend a lower bound printed as a measurement. Measured on the run
# started 2026-11-09T21:42:41Z, nine of fifteen item streams rotated and dropped 1 697 `turn_end` records against the
# 1 372 turns the state recorded. What fills a stream is not records but streamed deltas: in
# `items/833/events.jsonl` 16 662 of the ~17 400 surviving lines are `message_update` (4 741 310 of 8 565 562 bytes,
# 55 %), `tool_execution_update` another 255 184 B (3 %), so the deltas alone are most of what a bound has to trim.
#
# `message_update` may only go when **this** stream's `turn_end` records carry the same usage the deltas do: a turn's
# usage is `message_update.usage` (cumulative within a response), every `turn_end` of the measured archive carries it
# as `message.usage` (747 of 747 turns over six item streams), and `state.sh`'s `turns` reads it when no update
# survives — but a provider that reports usage on the updates alone would lose a turn's spend with them. So the pass
# reads the stream once and keeps the updates when any `turn_end` of it carries no `usage` object;
# `tool_execution_update`, which nothing reads, goes either way. A stream that is still over its bound after that is
# a lower bound still — that residue is issue #841, and it stays a *recorded* one.
#
# The record's shape is unchanged and `dropped_turn_ends` is cumulative: the record it replaces is dropped with the
# events it counted, so the count it carried is added to the one being dropped. `dropped_lines` and `dropped_bytes`
# are what this rotation removed — both passes together, and never the record they are written in. Called only when
# the stream has outgrown its bound, so the passes here are paid once per rotation and never per event.
rotate_events() { # rotate_events <file> — drop the delta lines, and the oldest half only if the stream is still over
  # `kept_bytes` is deliberately not local: the caller's own counter is reset to what the rotation left, or a
  # stream that rotated once would rotate on every event after it.
  local f="$1" lines keep drop turns prev rec tmp total delta_types size_before size_tmp lines_tmp
  local turn_ends turn_ends_with_usage keep_updates filter_rc cut cut_bytes
  lines=$(wc -l < "$f" 2>/dev/null) || true
  case "$lines" in ''|*[!0-9]*) lines=0 ;; esac
  # One line is the line just written: there is nothing older to drop, and dropping it would lose the newest event.
  [ "$lines" -ge 2 ] || return 0
  prev=$(head -n 1 "$f" 2>/dev/null | sed -n 's/.*"dropped_turn_ends": *\([0-9][0-9]*\).*/\1/p')
  case "$prev" in ''|*[!0-9]*) prev=0 ;; esac

  # The delta pass. Which types may go is a property of the stream being rotated, not of this adapter's assumption
  # about its agent: the updates are kept when a `turn_end` of it carries no usage for the turn to fall back on.
  delta_types='"type": *("message_update"|"tool_execution_update")'
  keep_updates=1
  turn_ends=$(grep -cE '"type": *"turn_end"' "$f" 2>/dev/null) || true
  case "$turn_ends" in ''|*[!0-9]*) turn_ends=0 ;; esac
  turn_ends_with_usage=$(grep -E '"type": *"turn_end"' "$f" 2>/dev/null | grep -cE '"usage": *\{') || true
  case "$turn_ends_with_usage" in ''|*[!0-9]*) turn_ends_with_usage=0 ;; esac
  [ "$turn_ends" -eq 0 ] || [ "$turn_ends_with_usage" -ge "$turn_ends" ] || keep_updates=0
  [ "$keep_updates" -eq 1 ] || delta_types='"type": *"tool_execution_update"'
  tmp="$f.rotating.$$"
  size_before=$(wc -c < "$f" 2>/dev/null) || size_before=0
  # The record a previous rotation wrote goes with the pass: the one below replaces it and carries its turn count.
  { grep -vE '"type": *"stream_rotation"' "$f" | grep -vE "$delta_types"; } > "$tmp"
  filter_rc=$?
  case "$size_before" in ''|*[!0-9]*) size_before=0 ;; esac
  if [ "$filter_rc" -gt 1 ]; then rm -f -- "$tmp"; return 0; fi
  lines_tmp=$(wc -l < "$tmp" 2>/dev/null) || lines_tmp=0
  size_tmp=$(wc -c < "$tmp" 2>/dev/null) || size_tmp=0
  case "$lines_tmp" in ''|*[!0-9]*) lines_tmp=0 ;; esac
  case "$size_tmp" in ''|*[!0-9]*) size_tmp=0 ;; esac
  drop=$(( lines - lines_tmp ))
  total=$(( size_before - size_tmp ))

  # The fallback: the records alone outgrew the bound, so the oldest half of them goes — and the turns it takes with
  # it are recorded, cumulatively, so the count a reader adds them back to never falls.
  turns=0
  if [ "${events_bound:-0}" -gt 0 ] && [ "$size_tmp" -gt "$events_bound" ] && [ "$lines_tmp" -ge 2 ]; then
    keep=$(( lines_tmp / 2 ))
    cut=$(( lines_tmp - keep ))
    drop=$(( drop + cut ))
    turns=$(head -n "$cut" "$tmp" 2>/dev/null | grep -cE '"type": *"turn_end"') || true
    case "$turns" in ''|*[!0-9]*) turns=0 ;; esac
    cut_bytes=$(head -n "$cut" "$tmp" 2>/dev/null | wc -c | tr -d ' ')
    case "$cut_bytes" in ''|*[!0-9]*) cut_bytes=0 ;; esac
    total=$(( total + cut_bytes ))
    { tail -n "+$(( cut + 1 ))" "$tmp"; } > "$tmp.cut" && mv -- "$tmp.cut" "$tmp"
  fi

  rec=$(printf '{"type":"stream_rotation","dropped_lines":%s,"dropped_bytes":%s,"dropped_turn_ends":%s,"at":"%s"}' \
    "$drop" "$total" "$(( turns + prev ))" "$(date -u +%Y-%m-%dT%H:%M:%SZ)")
  { printf '%s\n' "$rec"; cat -- "$tmp"; } > "$tmp.rec" && mv -- "$tmp.rec" "$f"
  rm -f -- "$tmp" "$tmp.cut"
  kept_bytes=$(wc -c < "$f" 2>/dev/null) || kept_bytes=0
  case "$kept_bytes" in ''|*[!0-9]*) kept_bytes=0 ;; esac
}

# The stream is read line by line. A line that is a JSON object is one event and is kept; anything else is the
# command's own text — an agent that ignores the mode flag prints its answer as plain text — and is passed
# through, so such a command still answers the loop and a project needs no second code path to keep working.
#
# The count starts from what the stream already holds, not from zero: the file is one per item and outlives every
# child that appends to it, so a stream that was already past its bound when this child started — an item kept from
# an earlier run, or one written before the bound existed — must be rotated by the first event appended to it, not
# only once *this* child has added a bound's worth of its own. One `wc` per child, paid here and never per event.
kept_bytes=0
if [ -n "$events" ] && [ -f "$events" ]; then
  kept_bytes=$(wc -c < "$events" 2>/dev/null) || kept_bytes=0
  case "$kept_bytes" in ''|*[!0-9]*) kept_bytes=0 ;; esac
fi
"${cmd[@]}" "${extension[@]}" "${policy[@]}" "${mode[@]}" "${context[@]}" "$prompt" | while IFS= read -r line || [ -n "$line" ]; do
  case "$line" in
    '{'*)
      if [ -n "$events" ]; then
        printf '%s\n' "$line" >> "$events"
        # The bound is kept in a counter rather than re-read from the file: `wc` per event would cost the writer a
        # process per line, and the rotation is triggered a whole half-stream later anyway. The counter counts
        # characters, so a stream of multibyte text can pass the bound by the width of the lines it carries before
        # the rotation trims it to half; it is a bound on growth, and after each rotation the count restarts from the
        # size `wc` reports, not from the estimate.
        if [ "$events_bound" -gt 0 ]; then
          kept_bytes=$(( kept_bytes + ${#line} + 1 ))
          [ "$kept_bytes" -le "$events_bound" ] || rotate_events "$events"
        fi
      fi
      # The authoritative final text is the last completed assistant message (`message_end`, not the
      # delta-only `message_update`), and only that is the answer the loop parses.
      case "$line" in
        *'"message_end"'*)
          text=$(printf '%s' "$line" | jq -r '
            select(.type == "message_end" and .message.role == "assistant")
            | .message.content
            | if type == "string" then .
              elif type == "array" then [.[] | select(.type == "text") | .text] | join("")
              else "" end' 2>/dev/null) || text=""
          if [ -n "$text" ]; then printf '%s' "$text" > "$answer_file"; fi
          ;;
      esac
      ;;
    *) printf '%s\n' "$line" ;;
  esac
done
rc=${PIPESTATUS[0]}

if [ -s "$answer_file" ]; then
  cat "$answer_file"
  printf '\n'
fi
exit "$rc"
