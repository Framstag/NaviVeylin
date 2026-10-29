#!/usr/bin/env bash
# metadata-invariant.sh — the tracker's per-item invariants as a scheduled check: an item whose band label and
# the marker the metadata pass wrote disagree, or whose body keeps a settled state of its own while its state
# label says otherwise.
#
# It does not re-implement the invariants. It runs `tools/issues/check-metadata.sh --live` — the command that
# owns the rules, the per-item half included — and reports that command's own violations, so the row that watches
# them and a person's hand run can never disagree about what a drift is (issue #292; the same reason
# `stale-citations.sh` measures through the resolver). Because it reports the command's report whole, a condition
# is named by the invariant the command's own code names, never by a rule the row restates here (#683).
#
# The row exists because the per-item half had no unattended caller: CI runs only the token-free self-check,
# which reads the profile and the scripts and never the tracker, so a marker a hand edit removed on the web UI
# was enforced only when somebody remembered to run the live mode (issue #292, the owner's recorded decision of
# 2026-10-25 — option 1 of three). It reports and repairs nothing: which half of a drifted pair is the wrong one
# is a judgement, so dropping a stale label or rewriting both halves together stays a person's edit.
#
# The identity is the item and the disagreement it breaks, so an item whose pair is repaired closes the issue
# carrying that drift on the identity the issue holds, and a second, different disagreement about the same item
# is a second condition rather than a reopening of the first one's record (design D4).
#
# Inputs (set by `run.sh`): SENSORS_THRESHOLD, SENSORS_CONDITIONS, SENSORS_ROOT.
set -uo pipefail
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/sensors/lib.sh
source "$here/../lib.sh"

# The bound is the registry row's (`SENSORS_THRESHOLD`), and it is quoted in the findings' claims rather than
# applied as a count: one drifted item is already a condition, whatever the number of items that drift.
bound="${SENSORS_THRESHOLD:-1}"

report=$(bash "$SENSORS_ROOT/tools/issues/check-metadata.sh" --live --json 2>/dev/null)
status=$?
# Exit 0 is a tracker whose pairs agree and 1 is one that reports violations; both carry the report. Anything
# else — or a report without its violations array — is the third outcome, never a clean tracker.
case "$status" in
  0|1) : ;;
  *)
    sensors_emit_error '{"issue":"unknown","code":"unknown"}' "unreadable-tracker-check"
    exit 0
    ;;
esac
if [ -z "$report" ] || ! jq -e '.violations | type == "array"' >/dev/null 2>&1 <<< "$report"; then
  sensors_emit_error '{"issue":"unknown","code":"unknown"}' "unreadable-tracker-check"
  exit 0
fi
# A read that reports no item at all is not this project's tracker: 300-odd items carry a band, so a zero count
# is a read the command could not make rather than a tracker whose pairs all agree (an unparseable answer reads
# as zero items and no violations). It is the third outcome, and it files nothing.
checked=$(jq -r '.checked // 0' <<< "$report")
case "$checked" in
  ''|*[!0-9]*|0)
    sensors_emit_error '{"issue":"unknown","code":"unknown"}' "no-item-read"
    exit 0
    ;;
esac

printf 'metadata drift: %s item(s) checked, %s violation(s) (bound %s)\n' \
  "$checked" "$(jq -r '.violations | length' <<< "$report")" "$bound" >&2

findings=0
present=""

while IFS=$'\t' read -r issue code detail; do
  [ -n "$issue" ] || continue
  fields=$(jq -nc --arg issue "$issue" --arg code "$code" '{issue: $issue, code: $code}')
  fingerprint=$(sensors_fingerprint "$fields" "issue code")
  present="$present $fingerprint"
  sensors_emit "$fingerprint" "drifted" violation "$fields" "" \
    "an item's own record and its label disagree" \
    "issue #${issue}: ${code} (${detail}), so a per-item invariant of the tracker has drifted"
  findings=$((findings + 1))
done <<< "$(jq -r '.violations[]? | [.issue, .code, .detail // ""] | @tsv' <<< "$report")"

# Every condition this row owns and does not report as present has cleared — the identity comes from the
# issue's own body record, so the clearing finding names what the issue carries.
while IFS=$'\t' read -r issue code; do
  [ -n "$issue" ] || continue
  fields=$(jq -nc --arg issue "$issue" --arg code "$code" '{issue: $issue, code: $code}')
  fingerprint=$(sensors_fingerprint "$fields" "issue code")
  case " $present " in *" $fingerprint "*) continue ;; esac
  sensors_emit "$fingerprint" "settled" clear "$fields" "" \
    "the item's record and its label agree again" \
    "issue #${issue} no longer carries ${code}"
  findings=$((findings + 1))
done < <(jq -r '.[]? | select(.state == "OPEN") | [.condition.issue // "", .condition.code // ""] | @tsv' \
  <<< "${SENSORS_CONDITIONS:-[]}")

# A row that found nothing must say so: the runner reads a row that printed nothing as a failed check, and
# "every item's halves agree" is a verdict, not silence. The identity is the row's own, so the finding lands on
# no issue and closes nothing.
if [ "$findings" -eq 0 ]; then
  fields='{"issue":"none","code":"none"}'
  fingerprint=$(sensors_fingerprint "$fields" "issue code")
  sensors_emit "$fingerprint" "none" clear "$fields" "" \
    "every item's record and its label agree" \
    "the tracker reports no drift in the per-item invariants, and no drift condition is recorded"
fi
