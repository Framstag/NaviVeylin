#!/usr/bin/env bash
# The decision queue: every item that awaits a person's decision, with the question recorded on it.
#
# A label is a mode and a comment is not selectable, which is why the queue is a query over the decision label
# rather than a reading of each item (`tools/loop/labels.sh`, header). What this adds is the half a query cannot
# answer by itself: **what is being asked**. The question is the item's own recorded statement — the escalation's
# comment — so the queue prints the tracker's text and never a summary of it, and an item whose escalation left no
# statement is named as carrying none instead of being presented as a question (#194).
#
# The set it reads is the items carrying the label, and it is counted before it is read: a fixed window is a guess
# that goes stale, and the one this queue used (400) left the 53 oldest items of a 453-item tracker unread, three of
# them awaiting a decision (#805). So the tracker is asked how many items carry the label, the list is asked for a
# window one larger than that, and a read that comes back full is refused: it is a read the tracker overran, and the
# queue states the set it read rather than printing a count over one it could not cover. A read that comes back short
# has covered the set, because nothing was cut off it.
#
# Read-only: no token beyond the tracker read, no write, no preflight. It answers two questions a person asks
# before answering anything — which items are waiting, and what each one asks.
#
# It lists the decision label and nothing else: an item a run handed back to be reshaped carries the shaping
# label instead, awaits no question, and is listed by `tools/issues/shaping.sh` (capability `shaping-inbox`).
#
# Exit: 0 ok (an empty queue is not a failure), 2 usage, 3 the profile declares no decision role, or the set the
# queue had to read could not be covered.
#
# Usage: decisions.sh [--json] [--profile <path>]
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/issues/lib.sh
source "$here/lib.sh"

profile_arg=""
json=0
while [ $# -gt 0 ]; do
  case "$1" in
    --profile) need_value "$@"; profile_arg="${2:-}"; shift 2 ;;
    --json) json=1; shift ;;
    -h|--help) sed -n '2,26p' "$0"; exit 0 ;;
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

repo="$ISSUES_REPO"
[ -n "$repo" ] ||
  issues_die "decisions: the repository could not be resolved — the queue cannot count the set it reads"
owner="${repo%%/*}"
name="${repo#*/}"

# The count first, then the read. A number the queue picks itself is the defect this fixes (#805); a window one
# larger than the count is what makes the read self-checking, because a read that comes back full is a read the
# tracker overran.
decision_set=$(gh api graphql \
  -f query='query($owner: String!, $name: String!, $label: String!) { repository(owner: $owner, name: $name) { issues(states: [OPEN, CLOSED], labels: [$label]) { totalCount } } }' \
  -f owner="$owner" -f name="$name" -f label="$decision_label" \
  --jq '.data.repository.issues.totalCount') || exit $?
case "$decision_set" in
  ''|*[!0-9]*)
    issues_die "decisions: the tracker did not answer how many items carry $decision_label (got '$decision_set')" ;;
esac

window=$((decision_set + 1))
# The list is scoped by the same label the count was taken over, so the window it is asked for is the counted set's
# own size (plus the one that makes a full read visible) rather than the tracker's.
issues=$(gh issue list --state all --label "$decision_label" --limit "$window" \
                      --json number,title,labels,comments,updatedAt,state) || exit $?
read_count=$(printf '%s' "$issues" | jq 'length')
[ "$read_count" -lt "$window" ] ||
  issues_refuse "decision-set-uncovered" \
    "the tracker reports $decision_set item(s) carrying $decision_label and the read came back full at $read_count — the queue refuses a set it could not cover"

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
          | (.body // "")
          | '"$ISSUES_QUESTION_JQ"'
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
# The set the count was taken over is stated beside it: a person reads which population the queue covered, and an
# item outside it is the defect the message names instead of hiding (#805).
printf 'read from the %s item(s) the tracker counts carrying %s\n' "$decision_set" "$decision_label"
exit "$ISSUES_EXIT_OK"
