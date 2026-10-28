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
# `--status <value>` *replaces* the item's state rather than adding to it: the item's labels are read, every
# other value of the status namespace is removed, and the add and the removals ride the one `gh issue edit`
# this script issues. Two status labels on one issue make every status query answer twice, and a transition
# that only ever adds leaves the old state behind for every loop verdict and every repair
# (`fix-status-label-replacement`; the scan is `lib.sh`, "the state namespace"). A call that names two states
# — a status-namespace `--label` beside `--status`, or a `--remove-label` cancelling the value it sets — is
# refused instead of resolved: which state the item would carry is a decision, and a silent one would leave
# the item's reader unable to tell what the caller meant.
#
# A value the profile declares settled (`roles.settledStatuses` — a fix's `closeStatus`, a person's
# `retiredStatus`) is not displaced by a routine write at all: the claim that an item's premise is settled and
# the claim that a run has no capacity to take it are different claims, and the second must not erase the first
# without saying so (#684: one write turned #146's `status/retired` into `status/on-hold`). Such a write is
# refused with exit 3 and `status-settled` unless the call passes `--displace-settled`, which is how a caller
# says it means to take the item off the settled state; the write that does pass it reports the value it
# removed.
#
# Exit: 0 updated, 2 usage, 3 policy.
#
# Usage: update.sh <issue-number> [--label <label>]… [--remove-label <label>]… [--status <value>]
#                  [--displace-settled] [--body <file>] [--comment <text>] [--comment-file <file>] [--json]
#                  [--profile <path>]
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
displace_settled=0
body_file=""
comment=""
comment_file=""
while [ $# -gt 0 ]; do
  case "$1" in
    --profile) need_value "$@"; profile_arg="${2:-}"; shift 2 ;;
    --label) need_value "$@"; labels+=("${2:-}"); shift 2 ;;
    --remove-label) need_value "$@"; remove_labels+=("${2:-}"); shift 2 ;;
    --status) need_value "$@"; status_value="${2:-}"; shift 2 ;;
    --displace-settled) displace_settled=1; shift ;;
    --body) need_value "$@"; body_file="${2:-}"; shift 2 ;;
    --comment) need_value "$@"; comment="${2:-}"; shift 2 ;;
    --comment-file) need_value "$@"; comment_file="${2:-}"; shift 2 ;;
    --json) json=1; shift ;;
    -h|--help) sed -n '2,32p' "$0"; exit 0 ;;
    *) issues_die "update: unknown argument $1" ;;
  esac
done

issues_resolve "$profile_arg"
profile="$ISSUES_PROFILE"
# The prefix is resolved once here: reading the profile starts a JVM, and both the target label and the scan
# below need it (`lib.sh`, "the state namespace").
status_prefix=$(issues_status_prefix)
issues_preflight "$profile"

# The state can be named either way: `--status <value>`, or the namespaced label itself. A check's escalation
# writes the second form — the registry declares `status/on-hold` among `escalation.labels` and `sensors/run.sh`
# passes it as a plain label — and a state written as a label is the same act as one written as a value, so the
# one-state rule has to hold for it too. The label spelling is folded into the value spelling here, before the
# contradiction checks, the vocabulary gate and the read, so the rest of this script has one shape to handle.
status_label=""
if [ -n "$status_value" ]; then
  status_label="${status_prefix}${status_value}"
  status_named_as="--status $status_value"
else
  status_named_as=""
  kept=()
  for label in "${labels[@]}"; do
    case "$label" in
      "$status_prefix"*)
        if [ -n "$status_label" ]; then
          [ "$label" != "$status_label" ] ||
            issues_die "update: --label $label is passed twice — pass the state once"
          issues_die "update: --label $status_label and --label $label name two states — a state write sets one"
        fi
        status_label="$label"
        status_named_as="--label $label" ;;
      *) kept+=("$label") ;;
    esac
  done
  labels=()
  [ "${#kept[@]}" -eq 0 ] || labels=("${kept[@]}")
fi

# One call names one state. Both contradictions are refused before the vocabulary gate, the read and the write,
# so a caller cannot resolve an ambiguity by accident and the item is untouched either way.
if [ -n "$status_label" ]; then
  for label in "${labels[@]}"; do
    case "$label" in
      "$status_label")
        issues_die "update: $status_named_as and --label $label ask for the same state twice — pass one" ;;
      "$status_prefix"*)
        issues_die "update: $status_named_as and --label $label name two states — a state write sets one" ;;
    esac
  done
  for label in "${remove_labels[@]}"; do
    [ "$label" = "$status_label" ] &&
      issues_die "update: $status_named_as removes the state it sets (--remove-label $label)"
  done
fi

if [ -n "$status_label" ]; then
  labels+=("$status_label")
fi
# The flag names a state transition, so a call that sets no state cannot pass it: a flag every caller may pass
# and none is checked for is how a `--displace-settled` ends up on a comment-only call nobody reads.
if [ "$displace_settled" -eq 1 ] && [ -z "$status_label" ]; then
  issues_die "update: --displace-settled names a state transition, and this call sets no state"
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

# The band label and the body's marker are one write (`lib.sh`, "the band pair"): a call that names a band
# label either carries the body that leaves the pair intact or is refused, so neither half is written by this
# path on its own — the drift `check-metadata.sh --live` reports and nothing repairs, because which half of a
# drifted pair is wrong is a judgement (issue #292's recorded decision; issue #671). Two shapes stay outside the
# gate on purpose: a `--body` that names no band label, because seeing that its marker disagrees with the item's
# stored label needs a read of the item the gate does not buy, and a hand edit on the web UI.
body_text=""
if [ -n "$body_file" ]; then
  body_text=$(cat "$body_file")
fi
issues_band_pair_gate "$profile" "$body_text" "${labels[@]}" -- "${remove_labels[@]}"

# The state transition, after the gates and before the write: the item decides both what this call must
# remove and whether its target is already there. Only a state write pays for the read, so a label- or
# comment-only call is unchanged.
add_labels=("${labels[@]}")
implied_removals=()
if [ -n "$status_label" ]; then
  issue=$(gh issue view "$number" --json labels 2>/dev/null) \
    || issues_die "update: gh could not read #$number — a state write needs the state it replaces"
  # The label list is captured first: under `pipefail`, `grep -q` exiting on its match can kill `jq` with
  # SIGPIPE and turn the pipeline non-zero, which would read as "the label is absent" and re-add it.
  labels_now=$(issues_labels_of "$issue")
  if grep -Fxq -- "$status_label" <<< "$labels_now"; then
    # The item already carries the state: adding it again is not a change, and one edit may not both add
    # and remove the same label.
    kept=()
    for label in "${add_labels[@]}"; do
      [ "$label" = "$status_label" ] && continue
      kept+=("$label")
    done
    add_labels=()
    [ "${#kept[@]}" -eq 0 ] || add_labels=("${kept[@]}")
  fi
  while IFS= read -r label; do
    [ -n "$label" ] && implied_removals+=("$label")
  done < <(issues_status_displaced "$issue" "$status_label" "$status_prefix")
  # What the profile declares settled is not this write's to remove unless the call names the transition: the
  # guard refuses here, after the read and before the write, so a refused call leaves the item as it was.
  issues_status_settled_guard "$number" "$displace_settled" "${implied_removals[@]}"
fi

args=(issue edit "$number")
for label in "${add_labels[@]}"; do
  args+=(--add-label "$label")
done
for label in "${remove_labels[@]}"; do
  args+=(--remove-label "$label")
done
for label in "${implied_removals[@]}"; do
  args+=(--remove-label "$label")
done
if [ -n "$body_file" ]; then
  args+=(--body-file "$body_file")
fi
# Nothing to write: a state write that finds the state already in place, with no other label and no body,
# is a no-op reported as such, not an `edit` with no flag for `gh` to refuse.
edit_needed=0
if [ "${#add_labels[@]}" -gt 0 ] || [ "${#remove_labels[@]}" -gt 0 ] || \
   [ "${#implied_removals[@]}" -gt 0 ] || [ -n "$body_file" ]; then
  edit_needed=1
fi
# The note explains a state, so only a call that named one can have it: `--status` that finds the value in
# place, or a `--label` of the state namespace. A comment-only or body-only call has no state to report and
# used to print `already carries  — no label write`, with the value empty (#182).
if [ "$edit_needed" -eq 0 ] && [ -n "$status_label" ]; then
  issues_note "update: #$number already carries $status_label — no label write"
fi

# `gh` rejecting the write is not a state the caller may proceed from: this tool reports `updated: true` and
# exits 0, and every reader — the loop's labels path, `close.sh`'s status write, a person's repair — treats
# that as "the tracker now carries this". A token without the scope, a rate limit or a network fault used to
# exit 0 with the write silently missing (#181). The write therefore lands before the report, not after it.
if [ "$edit_needed" -eq 1 ]; then
  if [ "$json" -eq 1 ]; then
    gh "${args[@]}" >/dev/null || issues_die "update: gh could not write #$number's labels — nothing was recorded"
  else
    gh "${args[@]}" || issues_die "update: gh could not write #$number's labels — nothing was recorded"
  fi
  # A transition the caller named is one it can be held to: the settled value it removed is said out loud, so a
  # reader of the call's own output sees the claim that moved and not only the flag that allowed it. The note is
  # on stderr, so a `--json` document on stdout stays a document a caller can pipe.
  [ "${#ISSUES_SETTLED_DISPLACED[@]}" -eq 0 ] ||
    issues_note "update: #$number removed ${ISSUES_SETTLED_DISPLACED[*]} — a settled state, displaced by --displace-settled"
fi

if [ "$json" -eq 1 ]; then
  # Both arrays default to empty: a legitimate write can add nothing and remove nothing (a state the item
  # already carries), and an empty string would reach `jq --argjson` as invalid JSON and print no document.
  label_json='[]'
  removed_json='[]'
  [ "${#add_labels[@]}" -eq 0 ] || label_json=$(printf '%s\n' "${add_labels[@]}" | jq -R . | jq -s .)
  if [ "${#remove_labels[@]}" -gt 0 ] || [ "${#implied_removals[@]}" -gt 0 ]; then
    removed_json=$(
      { [ "${#remove_labels[@]}" -eq 0 ] || printf '%s\n' "${remove_labels[@]}";
        [ "${#implied_removals[@]}" -eq 0 ] || printf '%s\n' "${implied_removals[@]}"; } | jq -R . | jq -s .
    )
  fi
  jq -n --argjson issue "$number" --argjson labels "$label_json" --argjson removed "$removed_json" \
        --arg body "${body_file:-}" --arg comment "$comment" \
        '{updated: true, issue: $issue, labels: $labels, removedLabels: $removed,
          body: (if $body == "" then null else $body end),
          commented: ($comment != "")}'
fi
if [ -n "$comment" ]; then
  gh issue comment "$number" --body "$comment" >/dev/null || issues_die "update: gh could not comment on #$number"
fi
exit "$ISSUES_EXIT_OK"
