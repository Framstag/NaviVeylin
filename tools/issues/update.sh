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
    -h|--help) sed -n '2,24p' "$0"; exit 0 ;;
    *) issues_die "update: unknown argument $1" ;;
  esac
done

issues_resolve "$profile_arg"
profile="$ISSUES_PROFILE"
# The prefix is resolved once here: reading the profile starts a JVM, and both the target label and the scan
# below need it (`lib.sh`, "the state namespace").
status_prefix=$(issues_status_prefix)
login=$(issues_login)
issues_preflight "$profile" "$login"

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
[ "$edit_needed" -eq 1 ] || issues_note "update: #$number already carries $status_label — no label write"

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
