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
# The set is counted before it is read: the label-scoped population is asked of the tracker, and the list is asked
# for a window one larger than that count, so the window is the inbox's own size rather than a constant of this
# tool (#1094). A read that comes back as full as the window is refused as `shaping-set-uncovered` rather than
# printed as coverage of the set, and every listing names the counted set it was taken over.
#
# Exit: 0 ok (an empty queue is not a failure), 2 usage, 3 the profile declares no shaping role, or a read the
# counted set overruns (`shaping-set-uncovered`).
#
# Usage: shaping.sh [--limit N] [--json] [--profile <path>]
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/issues/lib.sh
source "$here/lib.sh"

profile_arg=""
limit="" # the caller's own window, when it names one
json=0
while [ $# -gt 0 ]; do
  case "$1" in
    --profile) need_value "$@"; profile_arg="${2:-}"; shift 2 ;;
    --limit) need_value "$@"; limit="${2:-}"; shift 2 ;;
    --json) json=1; shift ;;
    -h|--help) sed -n '2,24p' "$0"; exit 0 ;;
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

repo="$ISSUES_REPO"
[ -n "$repo" ] ||
  issues_die "shaping: the repository could not be resolved — the queue cannot count the set it reads"
owner="${repo%%/*}"
name="${repo#*/}"

# The count first, then the read (#1094). A window this tool picks itself goes stale, and the one it picked (400) left
# every item of a larger tracker outside it unread while the queue still answered as though the inbox were empty. The
# count is the label-scoped population in all states, so the window ends up being the inbox's own size.
shaping_set=$(gh api graphql \
  -f query='query($owner: String!, $name: String!, $label: String!) { repository(owner: $owner, name: $name) { issues(states: [OPEN, CLOSED], labels: [$label]) { totalCount } } }' \
  -f owner="$owner" -f name="$name" -f label="$shaping_label" \
  --jq '.data.repository.issues.totalCount') || exit $?
case "$shaping_set" in
  ''|*[!0-9]*)
    issues_die "shaping: the tracker did not answer how many items carry $shaping_label (got '$shaping_set')" ;;
esac

# `--limit` is the caller's own window: it replaces the counted window rather than being added to it, and a set the
# window does not cover is the same refusal, because a list that comes back as full as the window it was asked for is
# a read the tracker overran either way.
if [ -n "$limit" ]; then
  case "$limit" in
    *[!0-9]*) issues_die "shaping: --limit wants a number, got '$limit'" ;;
  esac
  window="$limit"
else
  window=$((shaping_set + 1))
fi

issues=$(gh issue list --state all --label "$shaping_label" --limit "$window" \
                      --json number,title,labels,comments,updatedAt,state) || exit $?
read_count=$(printf '%s' "$issues" | jq 'length')
[ "$read_count" -lt "$window" ] ||
  issues_refuse "shaping-set-uncovered" \
    "the tracker reports $shaping_set item(s) carrying $shaping_label and the read came back full at $read_count — the queue refuses a set it could not cover"

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
  printf 'read from the %s item(s) the tracker counts carrying %s\n' "$shaping_set" "$shaping_label"
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
# The set the count was taken over is stated beside the queue's own count: a person reads which population the queue
# covered, and an item outside it is the defect this line makes visible instead of hiding (#1094).
printf 'read from the %s item(s) the tracker counts carrying %s\n' "$shaping_set" "$shaping_label"
printf 'LOOP-SHAPING-COUNT %s\n' "$count"
exit "$ISSUES_EXIT_OK"
