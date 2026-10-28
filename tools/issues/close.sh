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
# `--sensor <id>` is the ownership scope a check closes under (`sensor-registry-and-metric-issues`, D7):
# the policy pass runs first, and then the issue must carry that check's *own* label — an issue the check
# does not own is refused with exit 3 and nothing is written. A check's issues are authorised by the row
# that declared the check rather than by the automation opt-in, so `missing-eligibility-label` alone does
# not stop a sensor-scoped close; every other refusal does, and the scope check is what bounds a subverted
# or buggy sensor. A check closes a completed fix, never a person's decision, so `--not-planned` is
# refused beside `--sensor`.
#
# There is no delete path in this tooling, and this script adds none: the issue is the only record of a
# finding.
#
# A closure leaves the item in the settled state the profile names, not in the one it arrived with: the
# `closeStatus` (`retiredStatus` beside `--not-planned`) is written through `update.sh` before `gh issue
# close`, so `status/*` states one thing about a closed item whatever closed it (#181, #188).
#
# Exit: 0 closed (or commented), 2 usage, 3 policy.
#
# Usage: close.sh <issue-number> --reason <text> --evidence <text> [--not-planned] [--comment-only]
#                  [--sensor <id>] [--json] [--profile <path>]
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
sensor_id=""
while [ $# -gt 0 ]; do
  case "$1" in
    --profile) need_value "$@"; profile_arg="${2:-}"; shift 2 ;;
    --reason) need_value "$@"; reason="${2:-}"; shift 2 ;;
    --evidence) need_value "$@"; evidence="${2:-}"; shift 2 ;;
    --not-planned) state_reason="not_planned"; shift ;;
    --comment-only) comment_only=1; shift ;;
    --sensor) need_value "$@"; sensor_id="${2:-}"; shift 2 ;;
    --json) json=1; shift ;;
    -h|--help) sed -n '2,32p' "$0"; exit 0 ;;
    *) issues_die "close: unknown argument $1" ;;
  esac
done

[ -n "$reason" ] || issues_die "close: --reason is required — a closure carries the reason it was made"
[ -n "$evidence" ] || issues_die "close: --evidence is required — a closure carries the evidence for it"

issues_resolve "$profile_arg"
profile="$ISSUES_PROFILE"

sensor_label=""
if [ -n "$sensor_id" ]; then
  [ "$state_reason" = "completed" ] ||
    issues_die "close: --sensor closes a completed fix — a not-planned closure is a person's decision, and the reopen path refuses to reverse it"
  sensor_label=$(issues_sensor_label "$sensor_id")
  [ -n "$sensor_label" ] || issues_refuse "label-opaque" \
    "sensor/$sensor_id is not a declared check — the registry declares: $(issues_sensor_ids | tr '\n' ' ')"
fi

issues_preflight "$profile"

issue=$(gh issue view "$number" --json number,title,labels,body,author 2>/dev/null) \
  || issues_die "close: gh could not read issue #$number"
issue_file=$(mktemp) || issues_die "close: cannot create a temporary file"
trap 'rm -f "$issue_file"' EXIT
printf '%s' "$issue" > "$issue_file"

# The gate. A sensor-scoped close is refused by the author condition and by any refusal other than the
# missing opt-in: the check's own issues are authorised by the row, and its scope is the label below.
# A not-planned closure is exempt from the opt-in for the same reason and no other: it records a person's
# decision not to act, not work performed on the item, and it writes the settled status the item may already
# carry (#251). Every other refusal still stops it, and a *completed* closure still needs the label, because
# that one asserts work the automation did.
out=$(issues_core Policy check "$issue_file" --profile "$profile" 2>&1)
status=$?
reason_code="${out%%:*}"
if [ "$status" -eq 3 ] && [ "$reason_code" = "missing-eligibility-label" ] \
  && { [ -n "$sensor_label" ] || [ "$state_reason" = "not_planned" ]; }; then
  :
elif [ "$status" -eq 3 ]; then
  if [ "$json" -eq 1 ]; then
    jq -n --argjson issue "$number" --arg reason "$reason_code" --arg detail "${out#*: }" \
      '{closed: false, outcome: "refused", issue: $issue, reason: $reason, detail: $detail}'
  else
    printf '%s\n' "$out"
    printf 'close: refusing to close #%s — the gate did not pass, so nothing was posted\n' "$number"
  fi
  exit "$ISSUES_EXIT_POLICY"
elif [ "$status" -ne 0 ]; then
  issues_die "close: policy check failed: $out"
fi

# The ownership scope, checked after the gate and before any write: a check may act only on an issue that
# carries its own label, whatever the label or the pipeline claims.
if [ -n "$sensor_label" ]; then
  owned=$(printf '%s' "$issue" | jq -r --arg label "$sensor_label" \
    '[.labels[]? | if type == "string" then . else .name end] | index($label) != null')
  if [ "$owned" != "true" ]; then
    if [ "$json" -eq 1 ]; then
      jq -n --argjson issue "$number" --arg expected "$sensor_label" \
        '{closed: false, outcome: "refused", issue: $issue, reason: "sensor-scope", expected: $expected}'
    else
      printf 'sensor-scope: expected %s\n' "$sensor_label"
      printf 'close: refusing to close #%s — it does not carry %s\n' "$number" "$sensor_label"
    fi
    exit "$ISSUES_EXIT_POLICY"
  fi
fi

# The settled state the closure will record (#181, #188). Resolved before the first write, so a profile that
# names no such state refuses while the item is still untouched — the alternative is a comment explaining a
# closure that then does not happen.
settled_role="closeStatus"
[ "$state_reason" = "not_planned" ] && settled_role="retiredStatus"
settled_status=$(jq -r --arg k "$settled_role" '.roles[$k] // empty' "$profile")
[ -n "$settled_status" ] || issues_die \
  "close: the profile's roles section declares no $settled_role — a closed item would keep the state it carried"

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

# The settled state is a label, and a closure that leaves the old one behind makes the item read open to every
# query that asks the tracker, whatever `gh issue close` recorded (#181). A person's closure and the loop's
# closure must agree about the state they leave, because a reader cannot tell which path closed an item: the
# loop's own path writes the status first (`tools/loop/labels.sh`), and this one writes it through the same
# `update.sh --status` gate, so the closed vocabulary and the reserved-label refusal apply here unchanged and
# the state the item carried is displaced rather than left beside the new one (#188).
#
# The decision label goes with it for the same reason one step further out: an item that carries it and is
# closed stays in the decision queue (`tools/issues/decisions.sh`, a query over the label) for ever, and the
# ceiling that counts the queue then refuses runs over an item nobody can answer — measured 2026-10-20 on #81,
# closed and still listed (issue #225). An item without the label is untouched, so a closure writes nothing it
# would have to justify.
separator=$(jq -r '.namespaceSeparator // "/"' "$profile")
decision_role=$(jq -r '.roles.decisionAgent // empty' "$profile")
remove_args=()
if [ -n "$decision_role" ]; then
  decision_label_closed="agent${separator}${decision_role}"
  if printf '%s' "$issue" | jq -e --arg l "$decision_label_closed" \
    '[.labels[]? | if type == "string" then . else .name end] | index($l) != null' >/dev/null 2>&1; then
    remove_args=(--remove-label "$decision_label_closed")
  fi
fi
write_status=0
# The state a closure records is settled, and the state it displaces may be the other settled one (a completed
# fix closed as not to be done, a retired entry closed as fixed): recording a settled state is this verb's own
# purpose, so it names the transition to the guard `update.sh` applies to every routine writer (#684).
bash "$here/update.sh" "$number" --status "$settled_status" --displace-settled "${remove_args[@]}" \
  --profile "$profile" >&2 || write_status=$?
if [ "$write_status" -ne 0 ]; then
  # The closure did not happen: the item is left open with the state it carried, which is the honest outcome —
  # a half-written item is worse than one a person can still read.
  printf 'close: the settled state was not written, so #%s was left open\n' "$number" >&2
  exit "$write_status"
fi
# The CLI and the API spell the second reason differently: `gh issue close --reason` takes `not planned`,
# while the record keeps the API's `not_planned`. The translation lives here, at the one call site that talks
# to the CLI, because a value the CLI refuses leaves the item open with its settled state already written
# (#195).
case "$state_reason" in
  not_planned) cli_reason="not planned" ;;
  *) cli_reason="$state_reason" ;;
esac
gh issue close "$number" --reason "$cli_reason" >/dev/null || issues_die \
  "close: gh could not close #$number — it now carries status/$settled_status, check the tracker"
if [ "$json" -eq 1 ]; then
  jq -n --argjson issue "$number" --arg stateReason "$state_reason" --arg reason "$reason" \
    '{closed: true, outcome: "closed", issue: $issue, stateReason: $stateReason, reason: $reason}'
else
  printf 'closed #%s (%s)\n' "$number" "$state_reason"
fi
exit "$ISSUES_EXIT_OK"
