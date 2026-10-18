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
#   stale · unspecifiable · too-wide         + status/on-hold            (on the same issue; no new issue)
#   … and the improvement verdicts
#   needs-device                            + agent/needs-device + status/on-hold
#   needs-decision · needs-diagnosis        + agent/needs-decision + status/on-hold
#   close                                   − agent/in-progress, + status/fixed-by, then the closure
#   escalate                                + agent/needs-decision + status/on-hold, and a comment that
#                                           names the root cause, the failing case and the branch
#
# Exit: 0 ok, 2 usage, 3 policy (propagated from the tooling), 5 refuse.
#
# Usage: labels.sh <verdict> <issue-number> [--change <name>] [--reason <text>] [--evidence <text>]
#                  [--no-close]
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
may_close=1
while [ $# -gt 0 ]; do
  case "$1" in
    --change) change="${2:-}"; shift 2 ;;
    --reason) reason="${2:-}"; shift 2 ;;
    --evidence) evidence="${2:-}"; shift 2 ;;
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
hold_status=$(loop_role holdStatus)
close_status=$(loop_role closeStatus)

add_labels=()
remove_labels=()
status_value=""
close_issue=0
escalate=0

case "$verdict" in
  pick)
    add_labels=("$in_progress")
    ;;
  unpick)
    remove_labels=("$in_progress")
    ;;
  stale|unspecifiable|too-wide|not-spec-shaped|needs-target)
    # The item is not thrown away and no new issue is filed for it: the verdict and its one clause of
    # evidence are what the run produces, and they are attached to the item itself (D6).
    status_value="$hold_status"
    ;;
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
    loop_die "labels: unknown verdict '$verdict' — pick, unpick, escalate, close, stale, unspecifiable, too-wide, not-spec-shaped, needs-target, needs-device, needs-decision, needs-diagnosis"
    ;;
esac

args=("$issue")
if [ "${#add_labels[@]}" -gt 0 ]; then args+=(--label "${add_labels[@]}"); fi
if [ "${#remove_labels[@]}" -gt 0 ]; then args+=(--remove-label "${remove_labels[@]}"); fi
if [ -n "$status_value" ]; then args+=(--status "$status_value"); fi

if [ "${#add_labels[@]}" -gt 0 ] || [ "${#remove_labels[@]}" -gt 0 ] || [ -n "$status_value" ]; then
  loop_issue_tool update.sh "${args[@]}" >&2 || exit $?
fi

if [ "$close_issue" -eq 1 ] || [ "$escalate" -eq 1 ]; then
  [ -n "$reason" ] || loop_die "labels: $verdict needs --reason (the one line of root cause)"
  [ -n "$evidence" ] || loop_die "labels: $verdict needs --evidence (the paths that carry the proof)"
  close_args=("$issue" --reason "$reason" --evidence "$evidence")
  # `--may-close no` keeps the item open and still posts the comment: the deliverable is the evidence,
  # and whether the item is done is then a person's decision (D8). An escalation always leaves it open
  # for the same reason — the decision is the owner's.
  if [ "$close_issue" -eq 0 ] || [ "$may_close" -eq 0 ]; then close_args+=(--comment-only); fi
  loop_issue_tool close.sh "${close_args[@]}" >&2 || exit $?
fi

if [ "$close_issue" -eq 1 ] && [ "$may_close" -eq 0 ]; then
  printf '%s #%s — status%s%s kept, item left open\n' "$verdict" "$issue" "$separator" "$status_value"
else
  printf '%s #%s\n' "$verdict" "$issue"
fi
