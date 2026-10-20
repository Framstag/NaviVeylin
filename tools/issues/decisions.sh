#!/usr/bin/env bash
# The decision queue: every item that awaits a person's decision, with the question recorded on it.
#
# A label is a mode and a comment is not selectable, which is why the queue is a query over the decision label
# rather than a reading of each item (`tools/loop/labels.sh`, header). What this adds is the half a query cannot
# answer by itself: **what is being asked**. The question is the item's own recorded statement — the escalation's
# comment — so the queue prints the tracker's text and never a summary of it, and an item whose escalation left no
# statement is named as carrying none instead of being presented as a question (#194).
#
# Read-only: no token beyond the tracker read, no write, no preflight. It answers two questions a person asks
# before answering anything — which items are waiting, and what each one asks.
#
# Exit: 0 ok (an empty queue is not a failure), 2 usage, 3 the profile declares no decision role.
#
# Usage: decisions.sh [--limit N] [--json] [--profile <path>]
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
    -h|--help) sed -n '2,17p' "$0"; exit 0 ;;
    *) issues_die "decisions: unknown argument $1" ;;
  esac
done

issues_resolve "$profile_arg"
profile="$ISSUES_PROFILE"
separator=$(issues_profile_value namespaceSeparator)
separator="${separator:-/}"

decision_agent=$(jq -r '.roles.decisionAgent // empty' "$profile")
[ -n "$decision_agent" ] ||
  issues_die "decisions: the profile's roles section declares no decisionAgent — there is no label to ask about"
decision_label="agent${separator}${decision_agent}"

issues=$(gh issue list --state all --limit "$limit" --json number,title,labels,comments,updatedAt)

# The question is the last comment's first non-empty line: the escalation writes its question there and the
# options below it, and a later comment (a person's own note) is the more recent statement, so "last" is
# deliberate — first line, because that is the sentence, not the option list under it.
selected=$(printf '%s' "$issues" | jq -c --arg label "$decision_label" '
  [ .[]
    | select((.state // "OPEN") != "CLOSED")
    | select([(.labels // [])[] | if type == "string" then . else .name end] | index($label) != null)
    | {
        issue: .number,
        title: (.title // ""),
        updatedAt: (.updatedAt // ""),
        question: (
          ((.comments // []) | last // {})
          | (.body // "") | split("\n") | map(select(length > 0)) | (.[0] // "")
        )
      }
  ]
  | sort_by(.issue)')

if [ "$json" -eq 1 ]; then
  printf '%s\n' "$selected"
  exit "$ISSUES_EXIT_OK"
fi

count=$(printf '%s' "$selected" | jq 'length')
if [ "$count" -eq 0 ]; then
  printf 'no item awaits a decision (%s)\n' "$decision_label"
  exit "$ISSUES_EXIT_OK"
fi

# Tab-separated fields and `read`: an empty field would vanish (tab is an IFS whitespace character, so
# consecutive separators collapse), so the substitutions are made in `jq`, once, before the loop.
printf '%s\n' "$selected" | jq -r '.[] | [
    (.issue | tostring),
    (if .question == "" then "(no question recorded — the escalation left none)" else .question end),
    (if .title == "" then "(untitled)" else .title end),
    (if .updatedAt == "" then "-" else .updatedAt end)
  ] | @tsv' |
  while IFS=$'\t' read -r issue question title updated; do
    printf '#%s  %s\n' "$issue" "$question"
    printf '     %s\n' "$title"
    [ "$updated" = "-" ] || printf '     last touched %s\n' "$updated"
  done
printf '%s item(s) await a decision — answer one with tools/issues/answer.sh <issue> --decision "<text>"\n' "$count"
exit "$ISSUES_EXIT_OK"
