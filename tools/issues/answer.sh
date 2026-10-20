#!/usr/bin/env bash
# Answer a decision: record the answer on the item, then perform the transition it implies.
#
# The answer path exists because the alternative was folklore — a person had to know that the labels *are* the
# mode, that the answer is a comment, which state to set back, and that `agent/eligible` can only be removed in
# the tracker's UI (#194). Every write here goes through `tools/issues/update.sh` or `tools/issues/close.sh`, so
# the reserved-label refusal, the closed vocabulary and "evidence before closure" apply unchanged; this script
# decides *which* writes, never how one is made.
#
# The answer is recorded before the state changes: a state without its reason is the record #192 is about.
#
# Exit: 0 answered, 2 usage, 3 policy or "this item awaits no decision".
#
# Usage: answer.sh <issue> --decision <text> [--continue|--device|--retire]
#                  [--evidence <text>] [--profile <path>] [--json]
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/issues/lib.sh
source "$here/lib.sh"

issue=""
decision=""
outcome="continue"
evidence=""
profile_arg=""
json=0
while [ $# -gt 0 ]; do
  case "$1" in
    --decision) need_value "$@"; decision="${2:-}"; shift 2 ;;
    --continue) outcome="continue"; shift ;;
    --device) outcome="device"; shift ;;
    --retire) outcome="retire"; shift ;;
    --evidence) need_value "$@"; evidence="${2:-}"; shift 2 ;;
    --profile) need_value "$@"; profile_arg="${2:-}"; shift 2 ;;
    --json) json=1; shift ;;
    -h|--help) sed -n '2,16p' "$0"; exit 0 ;;
    --*) issues_die "answer: unknown argument $1" ;;
    *) [ -z "$issue" ] || issues_die "answer: issue given twice"; issue="$1"; shift ;;
  esac
done

case "$issue" in ''|*[!0-9]*) issues_die "answer: usage: answer.sh <issue> --decision <text> [--continue|--device|--retire]" ;; esac
[ -n "$decision" ] || issues_die "answer: --decision is required — an answer nobody can read is not an answer"
case "$outcome" in
  retire) [ -n "$evidence" ] || issues_die "answer: --retire needs --evidence — a closure without its proof is refused by the tooling anyway" ;;
esac

issues_resolve "$profile_arg"
profile="$ISSUES_PROFILE"
separator=$(issues_profile_value namespaceSeparator)
separator="${separator:-/}"

decision_agent=$(jq -r '.roles.decisionAgent // empty' "$profile")
open_status=$(jq -r '.roles.openStatus // empty' "$profile")
device_status=$(jq -r '.roles.deviceStatus // empty' "$profile")
[ -n "$decision_agent" ] || issues_die "answer: the profile's roles section declares no decisionAgent"
[ -n "$open_status" ] || issues_die "answer: the profile's roles section declares no openStatus"
decision_label="agent${separator}${decision_agent}"

login=$(issues_login)
issues_preflight "$profile" "$login"

# Only an item that carries the decision label awaits one. Answering anything else is a different write — closing
# a fixed item, re-opening a refuted one — and this verb must not become a way around those gates.
current=$(gh issue view "$issue" --json number,labels,state)
labels=$(printf '%s' "$current" | jq -c '[.labels[]? | if type == "string" then {name: .} else . end]')
if ! printf '%s' "$labels" | jq -e --arg l "$decision_label" 'any(.[]; .name == $l)' >/dev/null; then
  printf 'answer: #%s carries no %s — it awaits no decision, so there is nothing to answer\n' "$issue" "$decision_label" >&2
  exit "$ISSUES_EXIT_POLICY"
fi

# 1. the answer, on the item, before anything changes state.
bash "$here/update.sh" "$issue" --comment "Decision: $decision" >/dev/null || exit $?

# 2. the transition the answer implies.
case "$outcome" in
  continue|device)
    status="$open_status"
    [ "$outcome" = "device" ] && { status="$device_status"; [ -n "$status" ] || issues_die "answer: the profile's roles section declares no deviceStatus"; }
    bash "$here/update.sh" "$issue" --remove-label "$decision_label" --status "$status" >/dev/null || exit $?
    ;;
  retire)
    bash "$here/update.sh" "$issue" --remove-label "$decision_label" >/dev/null || exit $?
    bash "$here/close.sh" "$issue" --reason "$decision" --evidence "$evidence" --not-planned >/dev/null || exit $?
    ;;
esac

if [ "$json" -eq 1 ]; then
  jq -n --argjson issue "$issue" --arg outcome "$outcome" --arg decision "$decision" \
    '{answered: true, issue: $issue, outcome: $outcome, decision: $decision}'
else
  printf 'answered #%s (%s): %s\n' "$issue" "$outcome" "$decision"
fi
exit "$ISSUES_EXIT_OK"
