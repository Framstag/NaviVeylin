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
bound=$(sed -n 's/^LOOP_TOOL_RESULT_MAX_CHARS=["]\{0,1\}\([0-9][0-9]*\)["]\{0,1\}[[:space:]]*$/\1/p' \
  "$here/loop/loop.conf" 2>/dev/null | tail -1)
export LOOP_TOOL_RESULT_MAX_CHARS="${bound:-0}"

if [ $# -lt 2 ]; then
  printf 'pi-child-events: usage: %s <command> [args…] <prompt-file>\n' "$(basename "$0")" >&2
  exit 2
fi
prompt="${!#}"        # the last argument
cmd=("${@:1:$#-1}")   # …and everything before it starts the child

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

# The stream is read line by line. A line that is a JSON object is one event and is kept; anything else is the
# command's own text — an agent that ignores the mode flag prints its answer as plain text — and is passed
# through, so such a command still answers the loop and a project needs no second code path to keep working.
"${cmd[@]}" "${extension[@]}" "${mode[@]}" "${context[@]}" "$prompt" | while IFS= read -r line || [ -n "$line" ]; do
  case "$line" in
    '{'*)
      [ -z "$events" ] || printf '%s\n' "$line" >> "$events"
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
