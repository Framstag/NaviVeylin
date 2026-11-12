#!/usr/bin/env bash
# sonar-maintainability.sh — the maintainability row's measurement: the analysis's open maintainability findings
# at or above the row's severity floor (HIGH), grouped by rule.
#
# One rule is one condition (design D4): the fingerprint is the rule, the measurement is the count of that
# rule's findings, and the text names up to `SENSORS_SUMMARY_LIST_MAX` of the files, so one item is worked by
# finishing a rule rather than by chasing one line. A rule that shrinks keeps its item and records the smaller
# number; it closes when the rule reports no finding for the row's own consecutive count.
#
# Inputs (set by `run.sh`): SENSORS_THRESHOLD, SENSORS_INPUT, SENSORS_CONDITIONS.
set -uo pipefail
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/sensors/lib.sh
source "$here/../lib.sh"

input="${SENSORS_INPUT:-}"
if [ -z "$input" ] || [ ! -f "$input" ]; then
  sensors_emit_error '{"rule":"unknown"}' "no-findings-record"
  exit 0
fi
if ! jq -e . "$input" >/dev/null 2>&1; then
  sensors_emit_error '{"rule":"unknown"}' "unreadable-findings-record"
  exit 0
fi

# The band a severity stands for: one table, so "at or above the floor" is one place when the project
# disagrees with the server's vocabulary. The floor this row declares is HIGH.
floor_band="HIGH"
floor=3
list_max=$(printf '%s' "${SENSORS_SUMMARY_LIST_MAX:-5}" | awk '{ printf "%d", $1 + 0 }')
[ -n "$list_max" ] || list_max=5

rules=$(jq -r '.issues[]? | select(.quality == "MAINTAINABILITY") | .rule' "$input" | sort -u)

findings=0
at_floor=" "
while IFS= read -r rule; do
  [ -n "$rule" ] || continue
  group=$(jq -c --arg rule "$rule" --argjson floor "$floor" '
    def rank: {BLOCKER: 4, CRITICAL: 4, HIGH: 3, MEDIUM: 2, LOW: 1, INFO: 0}[.] // -1;
    [.issues[]? | select(.quality == "MAINTAINABILITY" and .rule == $rule)
     | . + {band: (.severity | rank)}]
    | map(select(.band >= $floor))
    | {count: length,
       severity: ((max_by(.band) // {severity: ""}).severity),
       paths: ([.[].path] | unique)}' "$input")
  count=$(jq -r '.count' <<< "$group")
  [ "$count" -gt 0 ] || continue
  severity=$(jq -r '.severity' <<< "$group")
  paths=$(jq -r --argjson max "$list_max" '.paths as $all | ($all[:$max] | join(", ")) + (if ($all | length) > $max then " and \(($all | length) - $max) more" else "" end)' <<< "$group")
  fields=$(jq -nc --arg rule "$rule" --arg severity "$severity" '{rule: $rule, severity: $severity}')
  fingerprint=$(sensors_fingerprint "$fields" "rule")
  printf 'sonar-maintainability: rule=%s findings=%s severity=%s\n' "$rule" "$count" "$severity" >&2
  sensors_emit "$fingerprint" "$count" violation "$fields" "" \
    "the rule's open findings are at or above the maintainability floor" \
    "$rule holds $count open finding(s) at $severity or above: $paths"
  findings=$((findings + 1))
  at_floor="$at_floor$rule "
done <<< "$rules"

while IFS= read -r fields; do
  [ -n "$fields" ] || continue
  rule=$(jq -r '.rule // ""' <<< "$fields")
  [ -n "$rule" ] || continue
  case "$at_floor" in
    *" $rule "*) continue ;;
  esac
  fingerprint=$(sensors_fingerprint "$fields" "rule")
  sensors_emit "$fingerprint" "0" clear "$fields" "" \
    "the rule reports no finding at or above the maintainability floor" \
    "$rule reports no open finding at or above the floor"
  findings=$((findings + 1))
done < <(jq -c '.[]? | select(.state == "OPEN") | (.condition // {}) | select(length > 0)' \
  <<< "${SENSORS_CONDITIONS:-[]}")

if [ "$findings" -eq 0 ]; then
  fields='{"rule":"none"}'
  fingerprint=$(sensors_fingerprint "$fields" "rule")
  sensors_emit "$fingerprint" "none" "clear" "$fields" "" \
    "no maintainability finding is open at the row's floor" \
    "the analysis reports no open maintainability finding at or above the floor, and no maintainability condition is recorded"
fi
