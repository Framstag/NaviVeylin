#!/usr/bin/env bash
# Update one issue: its labels, its `status/*` label, its body, or a comment on it.
#
# A label outside the profile's closed vocabulary is refused with exit 3 and reason `label-opaque`, and a
# reserved label with `label-reserved` — before any `gh issue edit` is issued, so a typo can neither create
# a label nor authorise work (design D2, D5). `--remove-label` runs through the same gate: taking the
# authorisation label off an issue is as much an authorisation decision as putting it on, so a removal is
# refused for the same reasons an addition is (`agent-issue-loop`, D7 — the loop removes `agent/in-progress`
# on every exit path, including a signal, and that write belongs in this gate rather than beside it).
# `--comment` is the other write a pass needs and did not have (`issue-metadata-loop`, D4): a band
# *transition* is explained on the item, and `close.sh --comment-only` cannot serve it because that path is
# gated on eligibility while a priority comment belongs on any item.
#
# Exit: 0 updated, 2 usage, 3 policy.
#
# Usage: update.sh <issue-number> [--label <label>]… [--remove-label <label>]… [--status <value>]
#                  [--body <file>] [--comment <text>] [--comment-file <file>] [--json] [--profile <path>]
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/issues/lib.sh
source "$here/lib.sh"

[ $# -gt 0 ] || issues_die "update: usage: update.sh <issue-number> [--label <label>]… [--status <value>] [--body <file>]"

number="$1"
shift
case "$number" in
  ''|*[!0-9]*) issues_die "update: '$number' is not an issue number" ;;
esac

profile_arg=""
json=0
labels=()
remove_labels=()
status_value=""
body_file=""
comment=""
comment_file=""
while [ $# -gt 0 ]; do
  case "$1" in
    --profile) profile_arg="${2:-}"; shift 2 ;;
    --label) labels+=("${2:-}"); shift 2 ;;
    --remove-label) remove_labels+=("${2:-}"); shift 2 ;;
    --status) status_value="${2:-}"; shift 2 ;;
    --body) body_file="${2:-}"; shift 2 ;;
    --comment) comment="${2:-}"; shift 2 ;;
    --comment-file) comment_file="${2:-}"; shift 2 ;;
    --json) json=1; shift ;;
    -h|--help) sed -n '2,22p' "$0"; exit 0 ;;
    *) issues_die "update: unknown argument $1" ;;
  esac
done

issues_resolve "$profile_arg"
profile="$ISSUES_PROFILE"
separator=$(issues_profile_value namespaceSeparator)
login=$(issues_login)
issues_preflight "$profile" "$login"

if [ -n "$status_value" ]; then
  labels+=("status${separator}${status_value}")
fi
if [ -n "$body_file" ]; then
  [ -f "$body_file" ] || issues_die "update: no such body file: $body_file"
fi
if [ -n "$comment_file" ]; then
  [ -f "$comment_file" ] || issues_die "update: no such comment file: $comment_file"
  comment=$(cat "$comment_file")
fi
[ -n "$comment" ] || [ -n "$comment_file" ] || [ "${#labels[@]}" -gt 0 ] || \
  [ "${#remove_labels[@]}" -gt 0 ] || [ -n "$body_file" ] ||
  issues_die "update: nothing to change — pass --label, --remove-label, --status, --body or --comment"

if [ "${#labels[@]}" -gt 0 ]; then
  issues_check_labels "$profile" "${labels[@]}"
fi
if [ "${#remove_labels[@]}" -gt 0 ]; then
  issues_check_labels "$profile" "${remove_labels[@]}"
fi

args=(issue edit "$number")
for label in "${labels[@]}"; do
  args+=(--add-label "$label")
done
for label in "${remove_labels[@]}"; do
  args+=(--remove-label "$label")
done
if [ -n "$body_file" ]; then
  args+=(--body-file "$body_file")
fi

if [ "$json" -eq 1 ]; then
  label_json=$(if [ "${#labels[@]}" -gt 0 ]; then printf '%s\n' "${labels[@]}"; fi | jq -R . | jq -s .)
  removed_json=$(if [ "${#remove_labels[@]}" -gt 0 ]; then printf '%s\n' "${remove_labels[@]}"; fi | jq -R . | jq -s .)
  jq -n --argjson issue "$number" --argjson labels "$label_json" --argjson removed "$removed_json" \
        --arg body "${body_file:-}" --arg comment "$comment" \
        '{updated: true, issue: $issue, labels: $labels, removedLabels: $removed,
          body: (if $body == "" then null else $body end),
          commented: ($comment != "")}'
  gh "${args[@]}" >/dev/null
else
  gh "${args[@]}"
fi
if [ -n "$comment" ]; then
  gh issue comment "$number" --body "$comment" >/dev/null || issues_die "update: gh could not comment on #$number"
fi
exit "$ISSUES_EXIT_OK"
