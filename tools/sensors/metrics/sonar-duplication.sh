#!/usr/bin/env bash
# sonar-duplication.sh — the duplication row's measurement: one project-wide number against the row's bound.
#
# The identity is the project (design D5): duplication is a property of the project, not of a module, so a
# single condition carries the density and the row needs two consecutive runs above its bound before it files
# — one refactor's temporary duplication clears first.
#
# An absent measure is a failed check, never a clear: a record whose `duplicated_lines_density` is empty says
# the scan did not measure duplication, and "not measured" is not "clean" (`backlog-sensors`, three outcomes).
#
# Inputs (set by `run.sh`): SENSORS_THRESHOLD, SENSORS_INPUT, SENSORS_CONDITIONS.
set -uo pipefail
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/sensors/lib.sh
source "$here/../lib.sh"

input="${SENSORS_INPUT:-}"
if [ -z "$input" ] || [ ! -f "$input" ]; then
  sensors_emit_error '{"project":"unknown"}' "no-findings-record"
  exit 0
fi
if ! jq -e . "$input" >/dev/null 2>&1; then
  sensors_emit_error '{"project":"unknown"}' "unreadable-findings-record"
  exit 0
fi

threshold="${SENSORS_THRESHOLD:-0}"
project="${SONAR_PROJECT_KEY:-}"
if [ -z "$project" ] && [ -n "${SENSORS_ROOT:-}" ] && [ -f "$SENSORS_ROOT/sonar-project.properties" ]; then
  project=$(sed -n 's/^sonar\.projectKey=//p' "$SENSORS_ROOT/sonar-project.properties" | head -1)
fi
[ -n "$project" ] || project="unknown"

value=$(jq -r '.measures.duplicated_lines_density // ""' "$input")
if [ -z "$value" ]; then
  sensors_emit_error "$(jq -nc --arg project "$project" '{project: $project}')" "no-duplication-measure"
  exit 0
fi

fields=$(jq -nc --arg project "$project" '{project: $project}')
fingerprint=$(sensors_fingerprint "$fields" "project")
ncloc=$(jq -r '.measures.ncloc // ""' "$input")
duplicated=$(jq -r '.measures.duplicated_lines // ""' "$input")
printf 'sonar-duplication: density=%s bound=%s ncloc=%s duplicated=%s\n' "$value" "$threshold" "$ncloc" "$duplicated" >&2

if sensors_compare "$value" "$threshold" greater-than; then
  sensors_emit "$fingerprint" "$value" violation "$fields" "" \
    "the project's duplicated-line density is above the row's bound" \
    "duplicated_lines_density is ${value}% over $ncloc ncloc, above the row's bound of ${threshold}%"
else
  sensors_emit "$fingerprint" "$value" clear "$fields" "" \
    "the project's duplicated-line density is inside the row's bound" \
    "duplicated_lines_density is ${value}% over $ncloc ncloc, inside the row's bound of ${threshold}%"
fi
