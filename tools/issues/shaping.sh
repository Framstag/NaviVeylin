#!/usr/bin/env bash
# The shaping inbox: every item a run's verdict handed back to a person to reshape, with that verdict and the
# clause behind it.
#
# The state is neither a hold nor a decision (capability `shaping-inbox`): the labels are a mode and a comment is
# not selectable, so the queue is a query over the shaping label — and what a query cannot answer by itself is
# **what the run found**. That is the item's own record: the comment `tools/loop/labels.sh` wrote, whose first
# line is the verdict word and whose `**The shaping:**` line is the clause. The extraction lives in `lib.sh` beside
# the decision queue's, so the two queues cannot disagree about one comment (#243 is that defect, measured on the
# decision queue).
#
# Read-only: no token beyond the tracker read, no write, no preflight. An item carrying the label with no such
# comment is named as carrying no statement instead of being presented as a finding, and an empty inbox is not a
# failure: the item is visible work a person owes, and there may be none.
#
# Exit: 0 ok (an empty queue is not a failure), 2 usage, 3 the profile declares no shaping role.
#
# Usage: shaping.sh [--limit N] [--json] [--profile <path>]
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/issues/lib.sh
source "$here/lib.sh"

profile_arg=""
limit=400
json=0
while [ $# -gt 0 ]; do
  case "$1" in
    --profile) need_value "$@"; profile_arg="${2:-}"; shift 2 ;;
    --limit) need_value "$@"; limit="${2:-}"; shift 2 ;;
    --json) json=1; shift ;;
    -h|--help) sed -n '2,18p' "$0"; exit 0 ;;
    *) issues_die "shaping: unknown argument $1" ;;
  esac
done

issues_resolve "$profile_arg"
profile="$ISSUES_PROFILE"
separator=$(issues_profile_value namespaceSeparator)
separator="${separator:-/}"

shaping_agent=$(jq -r '.roles.shapingAgent // empty' "$profile")
[ -n "$shaping_agent" ] ||
  issues_die "shaping: the profile's roles section declares no shapingAgent — this project has no shaping inbox"
shaping_label="agent${separator}${shaping_agent}"

issues=$(gh issue list --state all --limit "$limit" --json number,title,labels,comments,updatedAt,state)

selected=$(printf '%s' "$issues" | jq -c --arg label "$shaping_label" '
  [ .[]
    | select((.state // "OPEN") != "CLOSED")
    | select([(.labels // [])[] | if type == "string" then . else .name end] | index($label) != null)
    | {
        issue: .number,
        title: (.title // ""),
        updatedAt: (.updatedAt // ""),
        statement: (
          ((.comments // []) | last // {})
          | (.body // "")
          | '"$ISSUES_SHAPING_JQ"'
        )
      }
    | .verdict = .statement.verdict
    | .clause = .statement.clause
    | del(.statement)
  ]
  | sort_by(.issue)')

if [ "$json" -eq 1 ]; then
  printf '%s\n' "$selected"
  printf 'LOOP-SHAPING-COUNT %s\n' "$(printf '%s' "$selected" | jq 'length')" >&2
  exit "$ISSUES_EXIT_OK"
fi

count=$(printf '%s' "$selected" | jq 'length')
if [ "$count" -eq 0 ]; then
  printf 'no item awaits shaping (%s)\n' "$shaping_label"
  printf 'LOOP-SHAPING-COUNT 0\n'
  exit "$ISSUES_EXIT_OK"
fi

# Tab-separated fields and `read`: an empty field would vanish (tab is an IFS whitespace character, so
# consecutive separators collapse), so the substitutions — and the `-` that stands for an absent field — are made
# in `jq`, once, before the loop.
printf '%s\n' "$selected" | jq -r '.[] | [
    (.issue | tostring),
    (if .verdict == "" then "(no statement recorded — the run left none)" else .verdict end),
    (if .clause == "" then "-" else .clause end),
    (if .title == "" then "(untitled)" else .title end),
    (if .updatedAt == "" then "-" else .updatedAt end)
  ] | map(gsub("\t"; " ")) | @tsv' |
  while IFS=$'\t' read -r issue verdict clause title updated; do
    printf '#%s  %s\n' "$issue" "$verdict"
    [ "$clause" = "-" ] || printf '     %s\n' "$clause"
    printf '     %s\n' "$title"
    [ "$updated" = "-" ] || printf '     last touched %s\n' "$updated"
  done
printf '%s item(s) await shaping — reshape one with tools/issues/update.sh <issue> --remove-label %s\n' \
  "$count" "$shaping_label"
printf 'LOOP-SHAPING-COUNT %s\n' "$count"
exit "$ISSUES_EXIT_OK"
