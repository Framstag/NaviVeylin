#!/usr/bin/env bash
# A fixture sensor: prints the findings the case prepared, and nothing else. It stands in for a real
# measurement so the runner's selection, validation, dispatch and lifecycle can be driven offline.
set -uo pipefail
[ -f "${SENSORS_INPUT:-}" ] || { printf 'fixture sensor: no input at %s\n' "${SENSORS_INPUT:-}" >&2; exit 1; }
cat "$SENSORS_INPUT"
