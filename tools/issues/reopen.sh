#!/usr/bin/env bash
# Reopen one issue — the one write a returning condition needs, and the one a person's decision forbids.
#
# A reopen is not a second filing: the condition is a *state*, so the issue that already carries its
# history, its evidence and its closure reason is the record, and the recurrence is what that record
# should gain (design D4). Two rules bound it (design D6):
#
#   * only a closure recorded as a completed fix is reopened. An issue a person closed as a decision not
#     to act gets the measurement as a comment and nothing else, because `state_reason` exists precisely
#     to tell "it was fixed" from "we chose not to";
#   * the recurrence count in the body record is incremented here — once, by the one writer — and the
#     label that authorises automated work is never touched by this path.
#
# Exit: 0 reopened, 2 usage, 3 policy — including a closure this path may not reverse.
#
# Usage: reopen.sh <issue-number> --status <value> (--measurement <text> | --measurement-file <file>)
#                  [--body <file>] [--profile <path>] [--json]
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/issues/lib.sh
source "$here/lib.sh"

[ $# -gt 0 ] || issues_die "reopen: usage: reopen.sh <issue-number> --status <value> --measurement <text>"

number="$1"
shift
case "$number" in
  ''|*[!0-9]*) issues_die "reopen: '$number' is not an issue number" ;;
esac

profile_arg=""
json=0
status_value=""
measurement=""
measurement_file=""
body_file=""
while [ $# -gt 0 ]; do
  case "$1" in
    --profile) need_value "$@"; profile_arg="${2:-}"; shift 2 ;;
    --status) need_value "$@"; status_value="${2:-}"; shift 2 ;;
    --measurement) need_value "$@"; measurement="${2:-}"; shift 2 ;;
    --measurement-file) need_value "$@"; measurement_file="${2:-}"; shift 2 ;;
    --body) need_value "$@"; body_file="${2:-}"; shift 2 ;;
    --json) json=1; shift ;;
    -h|--help) sed -n '2,16p' "$0"; exit 0 ;;
    *) issues_die "reopen: unknown argument $1" ;;
  esac
done

[ -n "$status_value" ] || issues_die "reopen: --status is required — the state an issue returns to is the project's vocabulary"
if [ -n "$measurement_file" ]; then
  [ -f "$measurement_file" ] || issues_die "reopen: no such measurement file: $measurement_file"
  measurement=$(cat "$measurement_file")
fi
[ -n "$measurement" ] || issues_die "reopen: --measurement or --measurement-file is required — a reopen carries the returning measurement"
if [ -n "$body_file" ]; then
  [ -f "$body_file" ] || issues_die "reopen: no such body file: $body_file"
fi

issues_resolve "$profile_arg"
profile="$ISSUES_PROFILE"
issues_preflight "$profile"

issue=$(gh issue view "$number" --json number,title,labels,body,author,state,stateReason 2>/dev/null) \
  || issues_die "reopen: gh could not read issue #$number"

state=$(jq -r '.state // ""' <<< "$issue")
state_reason=$(jq -r '(.stateReason // "") | ascii_downcase' <<< "$issue")
status_prefix=$(issues_status_prefix)
status_label="${status_prefix}${status_value}"

# A closure this path may not reverse: the measurement is recorded, and the owner's decision stands.
if [ "$state" != "CLOSED" ] || [ "$state_reason" != "completed" ]; then
  reason="closure-not-reopenable"
  detail="issue #$number is ${state:-unknown} with state_reason '${state_reason:-none}'"
  gh issue comment "$number" --body "$measurement" >/dev/null || issues_die "reopen: gh could not comment on #$number"
  if [ "$json" -eq 1 ]; then
    jq -n --argjson issue "$number" --arg reason "$reason" --arg detail "$detail" \
      '{reopened: false, outcome: "commented", issue: $issue, reason: $reason, detail: $detail}'
  else
    printf '%s: %s\n' "$reason" "$detail"
    printf 'reopen: the measurement was recorded on #%s as a comment; the closure is left alone\n' "$number"
  fi
  exit "$ISSUES_EXIT_POLICY"
fi

# The one label this path writes, checked against the profile's closed vocabulary: a reserved label
# (the authorisation) or a value outside the vocabulary is refused before anything is written.
issues_check_labels "$profile" "$status_label"

body=$(if [ -n "$body_file" ]; then cat "$body_file"; else jq -r '.body // ""' <<< "$issue"; fi)
occurrence=$(( $(issues_occurrence "$body") + 1 ))
new_body=$(issues_body_set "$body" occurrence "$occurrence")

# The status labels the issue already carries are replaced, not accumulated: two status labels on one
# issue make every status query answer twice. The scan is shared with `update.sh`, which performs the same
# replacement (`lib.sh`, "the state namespace").
remove_labels=()
while IFS= read -r label; do
  [ -n "$label" ] || continue
  remove_labels+=("$label")
done < <(issues_status_displaced "$issue" "$status_label" "$status_prefix")
# A completed closure carries a settled state (`roles.closeStatus`), so the write below displaces one — and a
# reopen is the call that names that transition: it may only reverse a closure recorded `completed`
# (`closure-not-reopenable`, above) and it carries the returning measurement, so the settled state it removes is
# the one it means to remove. The guard is the same one `update.sh` applies to every routine writer (#684); this
# path says `named` because the verb is the transition, not because the check is weaker.
issues_status_settled_guard "$number" 1 "${remove_labels[@]}"

body_tmp=$(mktemp) || issues_die "reopen: cannot create a temporary file"
trap 'rm -f "$body_tmp"' EXIT
printf '%s' "$new_body" > "$body_tmp"

# The measurement first, then the state: the record of why is on the issue before it becomes open again.
gh issue comment "$number" --body "$measurement" >/dev/null || issues_die "reopen: gh could not comment on #$number"
edit_args=(issue edit "$number" --body-file "$body_tmp" --add-label "$status_label")
for label in "${remove_labels[@]}"; do
  edit_args+=(--remove-label "$label")
done
gh "${edit_args[@]}" >/dev/null || issues_die "reopen: gh could not update issue #$number"
gh issue reopen "$number" >/dev/null || issues_die "reopen: gh could not reopen #$number"

if [ "$json" -eq 1 ]; then
  jq -n --argjson issue "$number" --argjson occurrence "$occurrence" --arg status "$status_label" \
    '{reopened: true, outcome: "reopened", issue: $issue, occurrence: $occurrence, status: $status}'
else
  printf 'reopened #%s (occurrence %s, %s)\n' "$number" "$occurrence" "$status_label"
fi
exit "$ISSUES_EXIT_OK"
