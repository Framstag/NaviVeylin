#!/usr/bin/env bash
# build-duration.sh — the build duration row's measurement.
#
# It reuses `tools/gate-timing-report.sh` for reading and reporting the gate's timing record rather than
# parsing that format a second time, takes the wall time from the record, and compares it with the bound
# the row declares as a multiple of the *recorded* baseline — the previous measurement on the issue, which
# the runner passes in `SENSORS_BASELINE` (design D8, D10).
#
# Inputs (set by `run.sh`, never by the caller): SENSORS_THRESHOLD, SENSORS_BASELINE, SENSORS_INPUT,
# SENSORS_CONDITIONS. Output: one finding per line on stdout; the human-readable report on stderr.
set -uo pipefail
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/sensors/lib.sh
source "$here/../lib.sh"

fields='{"job":"gate"}'
input="${SENSORS_INPUT:-}"

if [ -z "$input" ] || [ ! -f "$input" ]; then
  sensors_emit_error "$fields" "no-timing-record"
  exit 0
fi

if ! report=$(bash "$SENSORS_ROOT/tools/gate-timing-report.sh" "$input" 2>&1); then
  sensors_emit_error "$fields" "the-timing-record-proves-nothing"
  exit 0
fi
printf '%s\n' "$report" >&2

duration=$(jq -r '.durationMs // empty' "$input")
if [ -z "$duration" ]; then
  sensors_emit_error "$fields" "no-duration-in-the-record"
  exit 0
fi

fingerprint=$(sensors_fingerprint "$fields" "job")
baseline=$(sensors_number "${SENSORS_BASELINE:-}")
bound=""
[ -n "$baseline" ] && bound=$(sensors_bound_from_baseline "$baseline" "${SENSORS_THRESHOLD:-0}")

if [ -z "$bound" ]; then
  # Nothing recorded to compare with: the run records the number and claims no condition, which is what
  # the clearance path then does with it (a clear with no issue files nothing).
  sensors_emit "$fingerprint" "$duration" clear "$fields" "" "" \
    "no baseline is recorded yet, so there is nothing to compare the gate's wall time with"
elif sensors_compare "$duration" "$bound" greater-than; then
  sensors_emit "$fingerprint" "$duration" violation "$fields" "$baseline" \
    "the gate's wall time is past its bound" \
    "the gate took ${duration}ms, past ${bound}ms (recorded baseline ${baseline}ms times the row's threshold)"
else
  sensors_emit "$fingerprint" "$duration" clear "$fields" "$baseline" \
    "the gate's wall time is inside its bound" \
    "the gate took ${duration}ms, inside ${bound}ms (recorded baseline ${baseline}ms times the row's threshold)"
fi
