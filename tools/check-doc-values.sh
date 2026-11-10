#!/usr/bin/env bash
# check-doc-values.sh — the turn bounds a guideline states are the ones the profile records.
#
# `guidelines/Backlog.recipes.md` §7b's table is the one place the per-role turn bounds are stated, and
# `tools/loop/loop.conf` is the one place their values are recorded. The two drifted apart in silence (issue #843):
# the prose kept the p95 figures of 2026-11-06 — worker 170, gate 80, review round 75, the round before the gate 75 —
# while the profile recorded 200, 100, 110 and 110 after issue #804 moved them, and a reader who trusted the guideline
# bounded a worker thirty turns early and a review round thirty-five late. A value that drifts in place is not a route,
# so `tools/check-doc-routes.sh` could not report it: this is the check that reads the values.
#
# The section's table states each value in one row — the key, the value the profile records, and the measurement that
# set it — and this check reads that row by its key, so the prose is not parsed by its wording:
#
#   | `LOOP_CHILD_TURNS_MAX_WORKER` | 200 | p50 77.5, … over 346 sessions |
#
# For every `LOOP_CHILD_TURNS_MAX_<ROLE>` key the profile records it takes the figure the table states and compares
# them, and it reports three shapes, each naming the role rather than the key (the name the run's own `agent-roles=`
# and its `LOOP-TIMEOUT` line use):
#
#   LOOP-CHILD-TURNS-MAX: <role> prose <n> config <n>   the two figures differ
#   LOOP-CHILD-TURNS-MAX: <role> prose - config <n>     the profile records a role the table has no row for
#   LOOP-CHILD-TURNS-MAX: <role> prose <n> config -     the table states a role the profile records no key for
#
# A profile that records no key at all is a defect here rather than "nothing to compare": the section states six
# values, and a tree that records none of them has lost the record the prose is written against.
#
# The rule it serves is `openspec/specs/documentation-ownership/spec.md`, "A documented bound states the value the
# profile records". Reads text files only: no build, no device, no network. Exit 0 when every stated value is the
# profile's, 1 when one differs or a role is unaccounted for, 2 on a usage or layout error.
#
# Usage: check-doc-values.sh [--root <dir>]   # default root: the repository this script lives in
set -uo pipefail

root=""
while [ $# -gt 0 ]; do
  case "$1" in
    --root) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; root="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,31p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "check-doc-values: unknown argument '$1'" >&2; exit 2 ;;
  esac
done
[ -n "$root" ] || root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)

doc="$root/guidelines/Backlog.recipes.md"
conf="$root/tools/loop/loop.conf"

if [ ! -f "$doc" ]; then
  echo "::error file=guidelines/Backlog.recipes.md::no such file — the section that states the turn bounds is not here"
  exit 2
fi
if [ ! -f "$conf" ]; then
  echo "::error file=tools/loop/loop.conf::no such file — the profile the bounds are compared with is not here"
  exit 2
fi

# The key names the section's table states, in the order it states them: the first cell of a row, backquoted.
prose_keys() { # → one key per line
  awk -F'|' '
    /^\|/ {
      k = $2; gsub(/[`[:space:]]/, "", k)
      if (k ~ /^LOOP_CHILD_TURNS_MAX_[A-Z][A-Z0-9_]*$/) print k
    }' "$doc"
}

prose_value() { # <key> → the figure the row states, or nothing when it states none
  awk -F'|' -v k="$1" '
    /^\|/ {
      c = $2; gsub(/[`[:space:]]/, "", c)
      if (c != k) next
      v = $3; gsub(/[^0-9]/, "", v)
      print v; exit
    }' "$doc"
}

role_of() { printf '%s' "${1#LOOP_CHILD_TURNS_MAX_}" | tr '[:upper:]' '[:lower:]' | tr '_' '-'; }

keys=$(sed -n 's/^\(LOOP_CHILD_TURNS_MAX_[A-Z][A-Z0-9_]*\)=.*/\1/p' "$conf")
if [ -z "$keys" ]; then
  echo "::error file=tools/loop/loop.conf::the profile records no LOOP_CHILD_TURNS_MAX_<ROLE> key — nothing to compare the stated bounds with"
  exit 2
fi

compared=0
fail=0
for key in $keys; do
  config=$(sed -n "s/^$key=\([0-9][0-9]*\)$/\1/p" "$conf" | tail -1)
  if [ -z "$config" ]; then
    printf 'LOOP-CHILD-TURNS-MAX: %s prose %s config - (the profile records the key with no whole number)\n' \
      "$(role_of "$key")" "$(prose_value "$key")"
    fail=1
    continue
  fi
  prose=$(prose_value "$key")
  if [ -z "$prose" ]; then
    printf 'LOOP-CHILD-TURNS-MAX: %s prose - config %s — guidelines/Backlog.recipes.md §7b states no value for this role, so a reader cannot tell the bound from the measurement that set it\n' \
      "$(role_of "$key")" "$config"
    fail=1
    continue
  fi
  compared=$((compared + 1))
  if [ "$prose" != "$config" ]; then
    printf 'LOOP-CHILD-TURNS-MAX: %s prose %s config %s — the guideline states a figure the profile does not record, so the bound a run applies is not the one a reader is given\n' \
      "$(role_of "$key")" "$prose" "$config"
    fail=1
  fi
done

# …and the other direction: a row whose role the profile records no key for is a stated bound nothing applies.
for key in $(prose_keys | sort -u); do
  if ! printf '%s\n' $keys | grep -qx -- "$key"; then
    printf 'LOOP-CHILD-TURNS-MAX: %s prose %s config - — guidelines/Backlog.recipes.md §7b states a bound tools/loop/loop.conf records no key for\n' \
      "$(role_of "$key")" "$(prose_value "$key")"
    fail=1
  fi
done

if [ "$fail" -ne 0 ]; then
  exit 1
fi
printf 'LOOP-CHILD-TURNS-MAX: %s roles, prose matches config\n' "$compared"
exit 0
