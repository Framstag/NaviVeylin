#!/usr/bin/env bash
# broken-scan.sh — the condition "the scan of main is red".
#
# The scan is a pipeline of its own (`.github/workflows/sonar.yml`): it uploads no build artifact and, until
# this row existed, no check read it, so a scan that had failed on every push to `main` filed no item of any
# kind (issue #751). The record the scan publishes for its own commit is the input — this sensor runs no scan
# and reads no server, so the verdict is the run's own, not a second measurement of the code.
#
# The condition is a *state*, and its identity is the step that broke: a second, unrelated failing step is a
# second issue rather than a reopening of the first one's record (design D4). A green scan clears what this
# row recorded, exactly as `broken-main` does for the build.
#
# Inputs (set by `run.sh`): SENSORS_INPUT (the scan's result record), SENSORS_CONDITIONS.
set -uo pipefail
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/sensors/lib.sh
source "$here/../lib.sh"

input="${SENSORS_INPUT:-}"
if [ -z "$input" ] || [ ! -f "$input" ]; then
  sensors_emit_error '{"job":"unknown","step":"unknown"}' "no-scan-result"
  exit 0
fi
if ! jq -e . "$input" >/dev/null 2>&1; then
  sensors_emit_error '{"job":"unknown","step":"unknown"}' "unreadable-scan-result"
  exit 0
fi

job=$(jq -r '.job // "sonar"' "$input")
step=$(jq -r '.step // ""' "$input")
command=$(jq -r '.command // ""' "$input")
result=$(jq -r '(.result // .status // "") | ascii_downcase' "$input")

# The identity the issue carries: the job and the step, canonical in this order (the engine sorts the field
# names, so the record and the row agree by construction).
fields=$(jq -nc --arg job "$job" --arg step "$step" '{job: $job, step: $step}')
fingerprint=$(sensors_fingerprint "$fields" "job step")

case "$result" in
  success)
    # The scan is green: every condition this row owns and still shows open has cleared, each named by the
    # identity its own issue records.
    sensors_clear_recorded "job step" "success" "the scan of main is green again" \
      "the scan succeeded, so the condition it recorded has cleared"
    # A green scan with nothing recorded must still report: the runner reads a row that printed nothing as a
    # failed check, and "the condition is absent" is a verdict, not silence (`backlog-sensors`, three
    # outcomes). The identity is this run's own, so the finding lands on no issue and closes nothing — and it
    # carries the record's own sentinel for "no step caused this" (`the-run`, as `--scan-result` writes one for
    # a run that broke before a named step), because every fingerprint field must be non-empty.
    if [ "$SENSORS_CLEARED" -eq 0 ]; then
      fields=$(jq -nc --arg job "$job" '{job: $job, step: "the-run"}')
      fingerprint=$(sensors_fingerprint "$fields" "job step")
      sensors_emit "$fingerprint" "success" clear "$fields" "" \
        "the scan of main is green" "the scan is green and no condition is recorded for ${job}"
    fi
    ;;
  failure|failed|error|cancelled|timed_out)
    sensors_emit "$fingerprint" "$result" violation "$fields" "" \
      "the scan of main is red" \
      "the scan failed at ${step}${command:+ — \`${command}\`}"
    ;;
  *)
    sensors_emit_error "$fields" "unreadable-scan-result"
    ;;
esac
