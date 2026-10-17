#!/usr/bin/env bash
# hotspot-report.sh — the hotspot row's measurement: per path, its size times how often it changed, from
# `git log --numstat`.
#
# The bound and the consecutive count are the registry row's and arrive in `SENSORS_THRESHOLD` and
# `SENSORS_CONSECUTIVE`: this script holds no threshold constant, so retuning the check is one edit in
# `sensors.json` (design D9, D10). The ranking is printed, so "which paths are hot" is answerable without
# the sensor filing anything, and one path is one condition — the fingerprint is the path.
#
# Inputs (set by `run.sh`): SENSORS_THRESHOLD, SENSORS_CONDITIONS, SENSORS_WINDOW (commits of history,
# default 200).
set -uo pipefail
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/sensors/lib.sh
source "$here/lib.sh"

threshold="${SENSORS_THRESHOLD:-0}"
window="${SENSORS_WINDOW:-200}"
print_ranking="${SENSORS_PRINT_RANKING:-20}"

history=$(mktemp) || sensors_die "hotspot: cannot create a temporary file"
trap 'rm -f "$history"' EXIT
git log --numstat --format='C%H' -n "$window" > "$history" 2>/dev/null ||
  { sensors_emit_error '{"path":"unknown"}' "no-history"; exit 0; }

# Changes per path, then the size on disk now: the score is their product, which is the whole metric.
scores=$(
  awk '
    /^C/ { next }
    NF == 3 { path = $3; changes[path]++ }
    END { for (p in changes) printf "%d\t%s\n", changes[p], p }
  ' "$history" |
    while IFS=$'\t' read -r changes path; do
      [ -f "$path" ] || continue
      size=$(wc -c < "$path" | tr -d ' ')
      case "$size" in ''|*[!0-9]*) continue ;; esac
      printf '%s\t%s\t%s\t%s\n' "$((size * changes))" "$path" "$size" "$changes"
    done | sort -k1,1nr -k2,2
)

if [ -z "$scores" ]; then
  sensors_emit_error '{"path":"unknown"}' "no-path-in-history"
  exit 0
fi

printf '%s\n' "$scores" | head -n "$print_ranking" |
  while IFS=$'\t' read -r score path size changes; do
    printf 'hotspot: score=%s path=%s size=%s changes=%s\n' "$score" "$path" "$size" "$changes" >&2
  done

violating=""
printf '%s\n' "$scores" | while IFS=$'\t' read -r score path size changes; do
  if sensors_compare "$score" "$threshold" greater-than; then
    fields=$(jq -nc --arg path "$path" '{path: $path}')
    fingerprint=$(sensors_fingerprint "$fields" "path")
    sensors_emit "$fingerprint" "$score" violation "$fields" "" \
      "the path is past the hotspot bound" \
      "$path scores ${score} (${size} bytes changed ${changes} times), past the row's bound of ${threshold}"
  fi
done

# Every condition this row owns and does not report as present has cleared — the fingerprint comes from the
# issue's own body record, so the clearing finding names the identity the issue carries (design D4).
while IFS= read -r path; do
  [ -n "$path" ] || continue
  current=$(printf '%s\n' "$scores" | awk -v p="$path" -F'\t' '$2 == p { print $1; exit }')
  [ -n "$current" ] || current=0
  sensors_compare "$current" "$threshold" greater-than && continue
  fields=$(jq -nc --arg path "$path" '{path: $path}')
  fingerprint=$(sensors_fingerprint "$fields" "path")
  sensors_emit "$fingerprint" "$current" clear "$fields" "" \
    "the path is inside the hotspot bound" \
    "$path scores ${current}, inside the row's bound of ${threshold}"
done < <(jq -r '.[]? | select(.state == "OPEN") | .condition.path // empty' <<< "${SENSORS_CONDITIONS:-[]}")
