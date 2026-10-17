#!/usr/bin/env bash
# package-size.sh — the package size row's measurement.
#
# It consumes a package the pipeline already produced — a release artifact from the gate or a release
# bundle — and never builds one of its own: a second build for a number the pipeline already has would
# double the cost of every run (design D10). The size is compared with the bound the row declares as a
# multiple of the recorded baseline from the issue's own history.
#
# Inputs (set by `run.sh`): SENSORS_THRESHOLD, SENSORS_BASELINE, SENSORS_INPUT, SENSORS_CONDITIONS.
set -uo pipefail
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/sensors/lib.sh
source "$here/../lib.sh"

input="${SENSORS_INPUT:-}"
artifact=$(basename "${input:-unknown}")
fields=$(jq -nc --arg artifact "$artifact" '{artifact: $artifact}')

if [ -z "$input" ] || [ ! -f "$input" ]; then
  sensors_emit_error "$fields" "no-package-artifact"
  exit 0
fi

size=$(wc -c < "$input" | tr -d ' ')
case "$size" in ''|*[!0-9]*) sensors_emit_error "$fields" "unreadable-package-size"; exit 0 ;; esac

fingerprint=$(sensors_fingerprint "$fields" "artifact")
baseline=$(sensors_number "${SENSORS_BASELINE:-}")
bound=""
[ -n "$baseline" ] && bound=$(sensors_bound_from_baseline "$baseline" "${SENSORS_THRESHOLD:-0}")

printf '%s: %s bytes (baseline %s, bound %s)\n' "$artifact" "$size" "${baseline:-none}" "${bound:-none}" >&2

if [ -z "$bound" ]; then
  sensors_emit "$fingerprint" "$size" clear "$fields" "" "" \
    "no baseline is recorded yet, so there is nothing to compare the package size with"
elif sensors_compare "$size" "$bound" greater-than; then
  sensors_emit "$fingerprint" "$size" violation "$fields" "$baseline" \
    "the package grew past its bound" \
    "$artifact is ${size} bytes, past ${bound} bytes (recorded baseline ${baseline} bytes times the row's threshold)"
else
  sensors_emit "$fingerprint" "$size" clear "$fields" "$baseline" \
    "the package is inside its bound" \
    "$artifact is ${size} bytes, inside ${bound} bytes (recorded baseline ${baseline} bytes times the row's threshold)"
fi
