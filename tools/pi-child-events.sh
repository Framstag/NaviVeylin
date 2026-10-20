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
#   `LOOP_AGENT_CMD="bash tools/pi-child-events.sh sbx exec pi"`. The mode flag is appended to that command,
#   immediately before the prompt.
#
# Environment:
#   LOOP_CHILD_EVENTS  the file the event lines are appended to; `run.sh` exports one per item so the item's
#                      children are summed and read as one spend. Unset or empty: the stream is still read and
#                      the answer still printed, and nothing is kept — a child started by hand.
#
# Exit: the child's own status, so the run's "the worker process exited N" path keeps its meaning.
set -uo pipefail

mode="--mode json"

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
fi

# The answer is held in a file and not in a variable because the reader below runs in a pipeline, whose
# subshell would discard it: the stream has to be consumed as it is produced (a reader that stops reading
# stalls the child when the pipe buffer fills), and the answer is printed once the child is done.
answer_file=$(mktemp) || { printf 'pi-child-events: cannot create a temporary file\n' >&2; exit 2; }
trap 'rm -f "$answer_file"' EXIT

# The stream is read line by line. A line that is a JSON object is one event and is kept; anything else is the
# command's own text — an agent that ignores the mode flag prints its answer as plain text — and is passed
# through, so such a command still answers the loop and a project needs no second code path to keep working.
"${cmd[@]}" "$mode" "$prompt" | while IFS= read -r line || [ -n "$line" ]; do
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
