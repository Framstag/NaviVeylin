#!/usr/bin/env bash
# broken-main.sh — the condition the whole registry exists for: the project's main build is red.
#
# The condition is a *state*, and its identity is the cause: the failing job and the failing case, so a
# second, unrelated failure is a second issue rather than a reopening of the first one's record (design
# D4). The artifact the gate already produces is the input — this sensor runs no build of its own.
#
# Inputs (set by `run.sh`): SENSORS_INPUT (the gate's result record), SENSORS_CONDITIONS.
set -uo pipefail
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/sensors/lib.sh
source "$here/../lib.sh"

input="${SENSORS_INPUT:-}"
if [ -z "$input" ] || [ ! -f "$input" ]; then
  sensors_emit_error '{"job":"unknown","case":"unknown"}' "no-build-result"
  exit 0
fi
if ! jq -e . "$input" >/dev/null 2>&1; then
  sensors_emit_error '{"job":"unknown","case":"unknown"}' "unreadable-build-result"
  exit 0
fi

job=$(jq -r '.job // "gate"' "$input")
case_name=$(jq -r '.case // .task // "the-build"' "$input")
result=$(jq -r '(.result // .status // "") | ascii_downcase' "$input")

fields=$(jq -nc --arg job "$job" --arg case "$case_name" '{job: $job, case: $case}')
fingerprint=$(sensors_fingerprint "$fields" "job case")

case "$result" in
  success)
    # The build is green: every condition this row owns and still shows open has cleared. The identity
    # comes from each issue's own body record, so the clearing finding names the issue's cause.
    cleared=0
    while IFS=$'\t' read -r cjob ccase; do
      [ -n "$cjob" ] || continue
      known=$(jq -nc --arg job "$cjob" --arg case "$ccase" '{job: $job, case: $case}')
      known_fp=$(sensors_fingerprint "$known" "job case")
      sensors_emit "$known_fp" "success" clear "$known" "" \
        "the build is green again" "the build succeeded, so the condition for ${cjob}/${ccase} has cleared"
      cleared=$((cleared + 1))
    done < <(jq -r '.[]? | select(.state == "OPEN") | [.condition.job // "", .condition.case // ""] | @tsv' \
      <<< "${SENSORS_CONDITIONS:-[]}")
    # A green build with nothing recorded must still report: the runner reads a row that printed nothing as a
    # failed check, and "the condition is absent" is a verdict, not silence (`backlog-sensors`, three
    # outcomes). The identity is this build's own, so the finding lands on no issue and closes nothing.
    if [ "$cleared" -eq 0 ]; then
      sensors_emit "$fingerprint" "success" clear "$fields" "" \
        "the build is green" "the build is green and no condition is recorded for ${job}/${case_name}"
    fi
    ;;
  failure|failed|error|cancelled|timed_out)
    sensors_emit "$fingerprint" "$result" violation "$fields" "" \
      "the main build is red" "the build failed: ${job} / ${case_name}"
    ;;
  *)
    sensors_emit_error "$fields" "unreadable-build-result"
    ;;
esac
