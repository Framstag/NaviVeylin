#!/usr/bin/env bash
# loop-state.sh — a **forwarder**, kept so a reference written before `agent-issue-loop` still works.
#
# The loop's state, its budget, its queue and its lock are `tools/loop/state.sh`'s now, and that script is
# their only writer. This file translates the old command names and hands over, so an old invocation —
# `scripts/loop-state.sh next`, from a session, a skill or a note — still exits 3 on a spent budget instead
# of dying on an unknown script (design D14).
#
# Two deliberate non-changes:
#
#   * the old state path `.pi/bugfix-loop/run.json` is left where it is: an open run there must not be
#     orphaned by a port. The new state is `.pi/issue-loop/run.json`, and `LOOP_STATE_FILE` overrides both.
#   * an id is written `§N` in the prose era and is an *issue number* now. The forwarder strips the `§` and
#     treats the rest as the issue number; a citation that no longer resolves is `tools/issues/list.sh --id N`'s
#     answer, not this file's.
#
# Exit: whatever `tools/loop/state.sh` exits with (0 ok, 2 usage, 3 stop, 5 refuse).
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
root=$(cd "$here/../../../.." && pwd)
state="$root/tools/loop/state.sh"

if [ ! -f "$state" ]; then
  printf 'loop-state: %s is missing — the loop state now lives in tools/loop/state.sh\n' "$state" >&2
  exit 2
fi

normalize_id() { printf '%s' "${1#§}"; }

# The old script's override was `LOOP_STATE`; the new one's is `LOOP_STATE_FILE`. A caller that still sets
# the old name must reach the same file rather than silently writing the default.
if [ -z "${LOOP_STATE_FILE:-}" ] && [ -n "${LOOP_STATE:-}" ]; then
  export LOOP_STATE_FILE="$LOOP_STATE"
fi

cmd="${1:-}"
shift || true

case "$cmd" in
  init)
    # `--allow-closure` was the old name of `--may-close`.
    args=()
    while [ $# -gt 0 ]; do
      case "$1" in
        --allow-closure) args+=(--may-close "${2:-}"); shift 2 ;;
        *) args+=("$1"); shift ;;
      esac
    done
    exec bash "$state" init "${args[@]}"
    ;;
  show|status) exec bash "$state" show "$@" ;;
  remaining|next) exec bash "$state" next "$@" ;;
  step) exec bash "$state" step "$@" ;;
  report) exec bash "$state" report "$@" ;;
  triage)
    # The old queue was a list of ids; the new one is a JSON array the pre-screen writes.
    [ $# -gt 0 ] || { printf 'loop-state: triage needs at least one id\n' >&2; exit 2; }
    json='['
    rank=0
    for id in "$@"; do
      rank=$((rank + 1))
      [ "$rank" -eq 1 ] || json+=','
      json+="{\"issue\":$(normalize_id "$id"),\"class\":\"\",\"verdict\":\"eligible\",\"rank\":$rank}"
    done
    json+=']'
    printf "%s" "$json" | bash "$state" queue
    ;;
  start)
    change=""
    issue=""
    while [ $# -gt 0 ]; do
      case "$1" in
        --id) issue=$(normalize_id "${2:-}"); shift 2 ;;
        --*) printf 'loop-state: start: unknown flag %s\n' "$1" >&2; exit 2 ;;
        *) change="$1"; shift ;;
      esac
    done
    [ -n "$issue" ] || { printf 'loop-state: start needs --id <issue> (the id is an issue number now)\n' >&2; exit 2; }
    [ -n "$change" ] || { printf 'loop-state: start needs the change name\n' >&2; exit 2; }
    exec bash "$state" start "$issue" --change "$change"
    ;;
  done)
    change=""
    issue=""
    rest=()
    while [ $# -gt 0 ]; do
      case "$1" in
        --id) issue=$(normalize_id "${2:-}"); shift 2 ;;
        --) shift; rest+=("$@"); break ;;
        --*) rest+=("$1" "${2:-}"); shift 2 ;;
        *) change="$1"; shift ;;
      esac
    done
    [ -n "$issue" ] || { printf 'loop-state: done needs --id <issue> (the id is an issue number now)\n' >&2; exit 2; }
    args=("$issue")
    [ -n "$change" ] && args+=(--change "$change")
    args+=("${rest[@]}")
    exec bash "$state" done "${args[@]}"
    ;;
  verdicts)
    # The old histogram, read from the new state.
    file="${LOOP_STATE_FILE:-$root/.pi/issue-loop/run.json}"
    [ -f "$file" ] || { printf 'loop-state: no run state at %s\n' "$file" >&2; exit 2; }
    jq -r '[.excluded[]?.verdict, .items[]?.verdict] | map(select(. != "" and . != null)) | group_by(.) | map("\(length)\t\(.[0])") | .[]' "$file" | sort -rn
    ;;
  ""|-h|--help|help)
    sed -n '2,19p' "$0"
    printf '\nthe loop itself: %s/run.sh --items N --minutes M [--review on|off] [--may-close yes|no]\n' "$root/tools/loop"
    ;;
  *) printf 'loop-state: unknown command %s\n' "$cmd" >&2; exit 2 ;;
esac
