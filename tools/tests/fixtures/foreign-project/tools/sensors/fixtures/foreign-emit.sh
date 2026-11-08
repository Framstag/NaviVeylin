#!/usr/bin/env bash
# The foreign project's one sensor: it prints the findings the harness prepared, and nothing else. It
# stands in for a real measurement so the registry's command indirection is exercised offline, against a
# row whose class and category the engines do not know.
set -uo pipefail
[ -f "${SENSORS_INPUT:-}" ] || { printf 'foreign sensor: no input at %s\n' "${SENSORS_INPUT:-}" >&2; exit 1; }
cat "$SENSORS_INPUT"
