#!/usr/bin/env bash
# Close one issue — the only terminal state this tooling has.
#
# A closure requires a reason and the evidence for it; the policy pass runs first, so an issue that fails the
# gate is not touched at all. The reason is posted as a comment before the issue is closed, so a closure is
# auditable and reversible, and `state_reason` records whether it was a completed fix or a person's decision
# not to act (design D6).
#
# `--comment-only` posts that same reason-and-evidence comment and leaves the issue open: it is the
# "closure left to a person" path (`agent-issue-loop`, D8), where the run has finished its work and the
# decision to close is not the run's. The comment is the deliverable either way, which is why it lives here
# rather than in a second writer beside the close.
#
# There is no delete path in this tooling, and this script adds none: the issue is the only record of a
# finding.
#
# Exit: 0 closed (or commented), 2 usage, 3 policy.
#
# Usage: close.sh <issue-number> --reason <text> --evidence <text> [--not-planned] [--comment-only]
#                  [--json] [--profile <path>]
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/issues/lib.sh
source "$here/lib.sh"

[ $# -gt 0 ] || issues_die "close: usage: close.sh <issue-number> --reason <text> --evidence <text> [--not-planned]"

number="$1"
shift
case "$number" in
  ''|*[!0-9]*) issues_die "close: '$number' is not an issue number" ;;
esac

profile_arg=""
json=0
reason=""
evidence=""
state_reason="completed"
comment_only=0
while [ $# -gt 0 ]; do
  case "$1" in
    --profile) profile_arg="${2:-}"; shift 2 ;;
    --reason) reason="${2:-}"; shift 2 ;;
    --evidence) evidence="${2:-}"; shift 2 ;;
    --not-planned) state_reason="not_planned"; shift ;;
    --comment-only) comment_only=1; shift ;;
    --json) json=1; shift ;;
    -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
    *) issues_die "close: unknown argument $1" ;;
  esac
done

[ -n "$reason" ] || issues_die "close: --reason is required — a closure carries the reason it was made"
[ -n "$evidence" ] || issues_die "close: --evidence is required — a closure carries the evidence for it"

issues_resolve "$profile_arg"
profile="$ISSUES_PROFILE"

login=$(issues_login)
issues_preflight "$profile" "$login"

issue=$(gh issue view "$number" --json number,title,labels,body,author 2>/dev/null) \
  || issues_die "close: gh could not read issue #$number"
issue_file=$(mktemp) || issues_die "close: cannot create a temporary file"
trap 'rm -f "$issue_file"' EXIT
printf '%s' "$issue" > "$issue_file"

if [ "$json" -eq 0 ]; then
  out=$(issues_core Policy check "$issue_file" --profile "$profile" 2>&1)
  status=$?
  if [ "$status" -eq 3 ]; then
    printf '%s\n' "$out"
    printf 'close: refusing to close #%s — the gate did not pass, so nothing was posted\n' "$number"
    exit "$ISSUES_EXIT_POLICY"
  fi
  [ "$status" -eq 0 ] || issues_die "close: policy check failed: $out"
else
  out=$(issues_core Policy check "$issue_file" --profile "$profile" 2>&1)
  status=$?
  if [ "$status" -eq 3 ]; then
    jq -n --argjson issue "$number" --arg reason "${out%%:*}" --arg detail "${out#*: }" \
      '{closed: false, outcome: "refused", issue: $issue, reason: $reason, detail: $detail}'
    exit "$ISSUES_EXIT_POLICY"
  fi
  [ "$status" -eq 0 ] || issues_die "close: policy check failed: $out"
fi

comment=$(printf '%s\n\nEvidence: %s\n' "$reason" "$evidence")
gh issue comment "$number" --body "$comment" >/dev/null || issues_die "close: gh could not comment on #$number"
if [ "$comment_only" -eq 1 ]; then
  # The run finished its work; whether the item is done is a person's decision, and the comment is what
  # carries the reason and the evidence either way (D8).
  if [ "$json" -eq 1 ]; then
    jq -n --argjson issue "$number" --arg reason "$reason" \
      '{closed: false, outcome: "commented", issue: $issue, reason: $reason}'
  else
    printf 'commented on #%s (left open)\n' "$number"
  fi
  exit "$ISSUES_EXIT_OK"
fi
gh issue close "$number" --reason "$state_reason" >/dev/null || issues_die "close: gh could not close #$number"

if [ "$json" -eq 1 ]; then
  jq -n --argjson issue "$number" --arg stateReason "$state_reason" --arg reason "$reason" \
    '{closed: true, outcome: "closed", issue: $issue, stateReason: $stateReason, reason: $reason}'
else
  printf 'closed #%s (%s)\n' "$number" "$state_reason"
fi
exit "$ISSUES_EXIT_OK"
