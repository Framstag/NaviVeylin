#!/usr/bin/env bash
# labels.sh — the loop's label transitions, and the only place the loop changes an issue's state.
#
# Every write goes through the issue tooling's own write paths (`update.sh`, `close.sh`), so the
# reserved-label refusal and the closed vocabulary apply to the loop exactly as they apply to a person,
# and the label that authorises automated work stays unwritable by the loop that is authorised by it
# (design D7, change 1 D2). Labels are a mode: a comment cannot be selected on, so the decision list and
# the in-progress list would stop being one query.
#
# The transitions (D7):
#
#   pick                                    + agent/in-progress
#   unpick                                  − agent/in-progress          (every exit path, incl. a signal)
#   unspecifiable · too-wide                 + status/on-hold            (on the same issue; no new issue,
#     · not-spec-shaped · needs-target           and a comment that names the verdict and its one clause
#                                                of evidence — a state change nobody can read is the
#                                                defect #192, so the reason travels with the hold)
#   (a verdict the profile's `roles.shapingVerdicts` names — `unspecifiable`, `too-wide` and
#    `not-spec-shaped` in this project — additionally takes `agent/needs-shaping`, in the
#    same write, and its comment opens with the verdict word. That state is neither a hold nor a decision:
#    it states that a person must reshape the item, and `tools/issues/shaping.sh` is its queue. The decision
#    ceiling counts the decision label only, so nothing on this path can stop a run — capability
#    `shaping-inbox`.)
#   (the pre-screen no longer has `exclude-stale`: a citation the tree does not hold is reported and refuses
#    nothing, #281. The worker's `stale` sub-verdict is not one of these holds any more either: a stale claim
#    leaves a question, so the run escalates it and this writer has no `stale` verdict to write — issue #682)
#   … and the improvement verdicts
#   needs-device                            + agent/needs-device + status/on-hold
#   needs-decision · needs-diagnosis        + agent/needs-decision + status/on-hold
#   exclude-feature · exclude-settled       + agent/<verdict>, status and the opt-in untouched — the
#                                           pre-screen's own refusal, which a person clears
#   close                                   − agent/in-progress, + status/fixed-by naming the transition, then the
#                                           closure — and, when `--landed <commit>` names the commit the
#                                           landing made, only if the base branch carries it (#715)
#   escalate                                + agent/needs-decision + status/on-hold, and a comment that
#                                           names the root cause, the failing case and the branch
#
# Exit: 0 ok, 2 usage, 3 policy (propagated from the tooling), 5 refuse.
#
# Usage: labels.sh <verdict> <issue-number> [--change <name>] [--reason <text>] [--evidence <text>]
#                  [--landed <commit>] [--no-close]
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/loop/lib.sh
source "$here/lib.sh"

loop_resolve
loop_roles_load

verdict="${1:-}"
issue="${2:-}"
shift 2 2>/dev/null || true
change=""
reason=""
evidence=""
landed=""
may_close=1
while [ $# -gt 0 ]; do
  case "$1" in
    --change) need_value "$@"; change="${2:-}"; shift 2 ;;
    --reason) need_value "$@"; reason="${2:-}"; shift 2 ;;
    --evidence) need_value "$@"; evidence="${2:-}"; shift 2 ;;
    --landed) need_value "$@"; landed="${2:-}"; shift 2 ;;
    --no-close) may_close=0; shift ;;
    *) loop_die "labels: unknown argument $1" ;;
  esac
done

case "$issue" in ''|*[!0-9]*) loop_die "labels: usage: labels.sh <verdict> <issue-number> [--change <name>]" ;; esac

separator=$(loop_compat_profile_value namespaceSeparator)
separator="${separator:-/}"
# The verdict → label mapping is data: the values come from the profile's `roles` section, so a project
# that spells its vocabulary differently writes a different profile, not a different script (D7, D16).
in_progress="agent${separator}$(loop_role inProgressAgent)"
needs_device="agent${separator}$(loop_role deviceAgent)"
needs_decision="agent${separator}$(loop_role decisionAgent)"
# The shaping label (capability `shaping-inbox`): the value is the profile's, and a profile that declares no
# shaping verdict writes none of it — the list answers which verdicts carry it, so no verdict is named here.
shaping_agent=$(loop_role shapingAgent)
shaping_label=""
[ -z "$shaping_agent" ] || shaping_label="agent${separator}${shaping_agent}"
hold_status=$(loop_role holdStatus)
close_status=$(loop_role closeStatus)

add_labels=()
remove_labels=()
status_value=""
close_issue=0
escalate=0
# A hold states no question, so it is not an escalation — but it still has to be readable from the item (#192):
# the comment carries the verdict and its one clause of evidence, and no label beyond the state is written.
comment_only=0

case "$verdict" in
  pick)
    add_labels=("$in_progress")
    ;;
  unpick)
    remove_labels=("$in_progress")
    ;;
  unspecifiable|too-wide|not-spec-shaped|needs-target)
    # The item is not thrown away and no new issue is filed for it: the verdict and its one clause of
    # evidence are what the run produces, and they are attached to the item itself (D6, #192).
    status_value="$hold_status"
    comment_only=1
    ;;
  exclude-feature) add_labels=("agent${separator}$(loop_role excludedFeatureAgent)") ;;
  exclude-settled) add_labels=("agent${separator}$(loop_role excludedSettledAgent)") ;;
  # The pre-screen's refusals the item cannot escape by itself (#163): the label is a refusal, so the
  # authorisation and the status stay as the owner left them — withdrawing the opt-in would make "nobody
  # authorised this" and "the loop refused it" the same state, which is the defect rather than the fix.
  clear-stale) remove_labels=("agent${separator}$(loop_role excludedStaleAgent)") ;;
  clear-feature) remove_labels=("agent${separator}$(loop_role excludedFeatureAgent)") ;;
  clear-settled) remove_labels=("agent${separator}$(loop_role excludedSettledAgent)") ;;
  # A verdict that is no longer a refusal (the citation was repaired, the class changed) takes the label
  # the previous run wrote off, so a repair is not silently frozen.
  needs-device)
    # A device is a fact, not a judgement: the verdict is recorded and the work stays on the host (D15).
    add_labels=("$needs_device")
    status_value="$hold_status"
    ;;
  needs-decision|needs-diagnosis)
    add_labels=("$needs_decision")
    status_value="$hold_status"
    ;;
  escalate)
    # A verdict the worker reached mid-item: the labels mark it as awaiting a decision, and the comment
    # names the work already done (root cause, the failing case, the branch) so it is escalated rather
    # than abandoned — the run does not throw work away (spec, "An item needing a decision is escalated,
    # not abandoned"; D9).
    add_labels=("$needs_decision")
    status_value="$hold_status"
    escalate=1
    ;;
  close)
    remove_labels=("$in_progress")
    status_value="$close_status"
    close_issue=1
    ;;
  *)
    loop_die "labels: unknown verdict '$verdict' — pick, unpick, escalate, close, unspecifiable, too-wide, not-spec-shaped, needs-target, exclude-feature, exclude-settled, clear-stale, clear-feature, clear-settled, needs-device, needs-decision, needs-diagnosis"
    ;;
esac

args=("$issue")
# A verdict the profile names as a shaping verdict carries the shaping label in the same write that records the
# hold: one `update.sh` call, so there is no window in which the item is held and nameless to the queue that
# exists for it (capability `shaping-inbox`, D5). `needs-diagnosis` is not one of them (#738): it states a
# question, so it keeps the decision label and writes no shaping label.
wrote_shaping=0
if [ -n "$shaping_label" ] && loop_shaping_verdicts "$verdict"; then
  add_labels+=("$shaping_label")
  wrote_shaping=1
fi
if [ "${#add_labels[@]}" -gt 0 ]; then
  # One `--label` per label: the flag takes one value, and a second one would be read as an argument.
  for label in "${add_labels[@]}"; do args+=(--label "$label"); done
fi
if [ "${#remove_labels[@]}" -gt 0 ]; then args+=(--remove-label "${remove_labels[@]}"); fi
if [ -n "$status_value" ]; then
  args+=(--status "$status_value")
  # The `close` verdict's write IS the closure's transition, exactly as `close.sh`'s own is
  # (`tools/issues/close.sh`): the item it closes may carry the *other* settled value of the profile — a fixed
  # item a person retires, a retired entry a fix closes — and a write that records a settled state without
  # naming the transition is refused by the guard every routine writer meets (exit 3, `status-settled`, #684).
  # Naming it here is what lets the closure land and report the value it displaced instead of leaving the
  # item half attempted between the two writes (#699).
  [ "$close_issue" -eq 0 ] || args+=(--displace-settled)
fi

# Both arguments are required *before* anything is written: a call that forgets them used to leave the state
# written and the reason missing, which is the half-written item the closure path already refuses to risk
# (#192).
if [ "$close_issue" -eq 1 ] || [ "$escalate" -eq 1 ] || [ "$comment_only" -eq 1 ]; then
  [ -n "$reason" ] || loop_die "labels: $verdict needs --reason (the one line of root cause)"
  [ -n "$evidence" ] || loop_die "labels: $verdict needs --evidence (the paths that carry the proof)"
fi

# The closure's claim is read *before* the state it settles is written (#715): `close.sh` refuses a commit the base
# branch does not carry, but the loop reaches it only after its own `status/fixed-by` write, so that refusal landed
# on an item that already read settled — and a settled item is one no later run revisits. Asking the same question
# here means a refused closure writes nothing at all: no state, no comment, no close.
if [ -n "$landed" ]; then
  [ "$close_issue" -eq 1 ] ||
    loop_die "labels: --landed belongs to the close verdict — another verdict claims no landing to check"
  landed_check=0
  loop_landed_on_base "$landed" || landed_check=$?
  if [ "$landed_check" -ne 0 ]; then
    base_ref=$(loop_base_ref)
    case "$landed_check" in
      2) landed_detail="$landed does not resolve in this tree, so the base branch cannot carry it" ;;
      3) landed_detail="the base branch $base_ref does not resolve in this tree, so the claim cannot be read" ;;
      *) landed_detail="$landed is not on the base branch $base_ref — the landing is still owed, and a closure records a fix the tree has (the same branch, landed by \`worktree.sh adopt-item\`, or a commit that is on $base_ref)" ;;
    esac
    printf 'not-on-base: %s\n' "$landed_detail"
    printf 'labels: refusing to record %s on #%s — nothing was written, and the item keeps the state it carries\n' \
      "$status_value" "$issue" >&2
    # The tooling's policy code, so a caller reads a refused claim exactly as it reads a refused closure
    # (`close.sh` exits 3 for the same reason).
    exit 3
  fi
fi

if [ "${#add_labels[@]}" -gt 0 ] || [ "${#remove_labels[@]}" -gt 0 ] || [ -n "$status_value" ]; then
  loop_issue_tool update.sh "${args[@]}" >&2 || exit $?
fi

if [ "$close_issue" -eq 1 ] || [ "$escalate" -eq 1 ] || [ "$comment_only" -eq 1 ]; then
  # A shaping verdict's comment states the verdict before the clause, because the queue prints the item's own
  # record and a reader who is handed a clause without its verdict cannot tell what the run found (#243 is the
  # same defect on the decision queue). `too-wide` states what the verdict means for the reader: the item wants
  # splitting, and no verb here can split it.
  comment_reason="$reason"
  if [ "$wrote_shaping" -eq 1 ] && [ "$close_issue" -eq 0 ]; then
    shaping_line="$verdict"
    [ "$verdict" != "too-wide" ] || shaping_line="$verdict: the item wants splitting into children"
    comment_reason="$shaping_line"$'\n\n'"**The shaping:** $reason"
  fi
  close_args=("$issue" --reason "$comment_reason" --evidence "$evidence")
  # The commit the claim rests on travels with the closure: `close.sh` reads it against the base branch itself, so a
  # caller that reaches the closure another way meets the same refusal (#715).
  [ -z "$landed" ] || close_args+=(--landed "$landed")
  # `--may-close no` keeps the item open and still posts the comment: the deliverable is the evidence,
  # and whether the item is done is then a person's decision (D8). An escalation always leaves it open
  # for the same reason — the decision is the owner's.
  if [ "$close_issue" -eq 0 ] || [ "$may_close" -eq 0 ]; then close_args+=(--comment-only); fi
  loop_issue_tool close.sh "${close_args[@]}" >&2 || exit $?
fi

if [ "$close_issue" -eq 1 ] && [ "$may_close" -eq 0 ]; then
  printf '%s #%s — status%s%s kept, item left open\n' "$verdict" "$issue" "$separator" "$status_value"
else
  # The label the call wrote is named on stdout, so a run's own log states what the tracker now carries.
  printf '%s #%s%s\n' "$verdict" "$issue" "$([ "$wrote_shaping" -eq 1 ] && printf ' + %s' "$shaping_label")"
fi
