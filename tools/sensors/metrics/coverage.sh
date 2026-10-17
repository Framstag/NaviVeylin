#!/usr/bin/env bash
# coverage.sh — the coverage row's measurement.
#
# It consumes the coverage instrumentation's own report (a JSON object of module → percentage) and applies
# the row's floor to every module, so one module's regression is its own condition and its own issue — the
# fingerprint is the module name (design D4, D10).
#
# Inputs (set by `run.sh`): SENSORS_THRESHOLD (the floor, in percent), SENSORS_INPUT, SENSORS_CONDITIONS.
set -uo pipefail
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/sensors/lib.sh
source "$here/../lib.sh"

input="${SENSORS_INPUT:-}"
if [ -z "$input" ] || [ ! -f "$input" ]; then
  sensors_emit_error '{"module":"unknown"}' "no-coverage-report"
  exit 0
fi
if ! jq -e . "$input" >/dev/null 2>&1; then
  sensors_emit_error '{"module":"unknown"}' "unreadable-coverage-report"
  exit 0
fi

floor="${SENSORS_THRESHOLD:-0}"
modules=$(jq -r '(.modules // {}) | to_entries[] | "\(.key)\t\(.value)"' "$input")
if [ -z "$modules" ]; then
  sensors_emit_error '{"module":"unknown"}' "no-module-in-the-report"
  exit 0
fi

printf '%s\n' "$modules" | while IFS=$'\t' read -r module percentage; do
  fields=$(jq -nc --arg module "$module" '{module: $module}')
  fingerprint=$(sensors_fingerprint "$fields" "module")
  printf '%s: %s%% (floor %s%%)\n' "$module" "$percentage" "$floor" >&2
  if sensors_compare "$percentage" "$floor" below-floor; then
    sensors_emit "$fingerprint" "$percentage" violation "$fields" "" \
      "the $module module's coverage is below its floor" \
      "$module is at ${percentage}%, below the ${floor}% floor"
  else
    sensors_emit "$fingerprint" "$percentage" clear "$fields" "" \
      "the $module module's coverage is above its floor" \
      "$module is at ${percentage}%, at or above the ${floor}% floor"
  fi
done
