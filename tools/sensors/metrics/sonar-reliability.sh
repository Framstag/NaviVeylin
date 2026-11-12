#!/usr/bin/env bash
# sonar-reliability.sh — the reliability row's measurement: the analysis's open reliability findings, grouped
# by rule, at or above the severity floor this row declares.
#
# The registry row's `threshold` is the count floor (0: any finding at or above the severity floor is a
# condition) and its `consecutive` count arrives in `SENSORS_CONSECUTIVE`. The severity floor itself is this
# row's own declaration — one band, in one place, so "at or above MEDIUM" is a single edit if the project
# disagrees — and it is compared as a rank, never as a string.
#
# One rule is one condition (design D4): the fingerprint is the rule, the measurement is how many findings the
# rule holds, and the text names the files. A rule whose severity changes keeps its item — severity is the
# filter and part of the claim, never the identity.
#
# Severity is the impact's severity for the software quality, never the legacy `severity` field the server also
# reports on another scale (design D3): the record the scan publishes carries the impact's value, so a MAJOR
# finding whose reliability impact is MEDIUM is measured as MEDIUM.
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
# disagrees with the server's vocabulary. The floor this row declares is MEDIUM.
floor_band="MEDIUM"
floor=2
list_max=$(printf '%s' "${SENSORS_SUMMARY_LIST_MAX:-5}" | awk '{ printf "%d", $1 + 0 }')
[ -n "$list_max" ] || list_max=5

# The rules the analysis reports for this quality, whatever their band: a rule that dropped under the floor is
# a cleared condition, not an absent one.
rules=$(jq -r '.issues[]? | select(.quality == "RELIABILITY") | .rule' "$input" | sort -u)

findings=0
at_floor=" "
while IFS= read -r rule; do
  [ -n "$rule" ] || continue
  group=$(jq -c --arg rule "$rule" --argjson floor "$floor" '
    def rank: {BLOCKER: 4, CRITICAL: 4, HIGH: 3, MEDIUM: 2, LOW: 1, INFO: 0}[.] // -1;
    [.issues[]? | select(.quality == "RELIABILITY" and .rule == $rule)
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
  printf 'sonar-reliability: rule=%s findings=%s severity=%s\n' "$rule" "$count" "$severity" >&2
  sensors_emit "$fingerprint" "$count" violation "$fields" "" \
    "the rule's open findings are at or above the reliability floor" \
    "$rule holds $count open finding(s) at $severity or above: $paths"
  findings=$((findings + 1))
  at_floor="$at_floor$rule "
done <<< "$rules"

# Every condition this row owns and does not report as present has cleared — the fingerprint comes from the
# issue's own body record, so the clearing finding names the identity the issue carries (design D4).
while IFS= read -r fields; do
  [ -n "$fields" ] || continue
  rule=$(jq -r '.rule // ""' <<< "$fields")
  [ -n "$rule" ] || continue
  case "$at_floor" in
    *" $rule "*) continue ;;
  esac
  fingerprint=$(sensors_fingerprint "$fields" "rule")
  sensors_emit "$fingerprint" "0" clear "$fields" "" \
    "the rule reports no finding at or above the reliability floor" \
    "$rule reports no open finding at or above the floor"
  findings=$((findings + 1))
done < <(jq -c '.[]? | select(.state == "OPEN") | (.condition // {}) | select(length > 0)' \
  <<< "${SENSORS_CONDITIONS:-[]}")

# A row that found nothing must say so: the runner reads a row that printed nothing as a failed check, and
# "no reliability finding is open at the floor" is a verdict, not silence.
if [ "$findings" -eq 0 ]; then
  fields='{"rule":"none"}'
  fingerprint=$(sensors_fingerprint "$fields" "rule")
  sensors_emit "$fingerprint" "none" "clear" "$fields" "" \
    "no reliability finding is open at the row's floor" \
    "the analysis reports no open reliability finding at or above the floor, and no reliability condition is recorded"
fi
