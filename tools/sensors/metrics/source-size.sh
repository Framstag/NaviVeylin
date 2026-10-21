#!/usr/bin/env bash
# source-size.sh — the source-size row's measurement: lines per tracked Kotlin or Java file.
#
# The bound and the consecutive count are the registry row's and arrive in `SENSORS_THRESHOLD` and
# `SENSORS_CONSECUTIVE`: this script holds no threshold constant, so retuning the check is one edit in
# `sensors.json` (design D9, D10). One file is one condition — the fingerprint is the path, so an issue closes
# when that path drops under the bound.
#
# The unit and the scope are the recorded decision of the item that asked for this row: lines per *file*,
# Kotlin and Java only, tests included, the native sources out (the whole native tree is two orders of
# magnitude below the bound). It is the file-level cut, not a class-level one: a file carrying several classes
# is one condition here, because separating them needs a brace-matching counter this tooling does not have —
# the refinement the item names, and a second change rather than this one.
#
# Inputs (set by `run.sh`): SENSORS_THRESHOLD, SENSORS_CONDITIONS.
set -uo pipefail
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/sensors/lib.sh
source "$here/../lib.sh"

threshold="${SENSORS_THRESHOLD:-0}"
tab=$(printf '\t')

# The tracked sources of the two languages, each with its line count. The count is `awk`'s own record number
# and not `wc -l`'s: `wc` prints a total line whose label is a translated word, so a parser keyed on it would
# need the locale. The list is NUL-separated, so a path with a space in it survives; a batch of a hundred
# files per `awk` keeps a tree of a thousand sources to a few processes.
sizes=$(git ls-files -z '*.kt' '*.java' 2>/dev/null | xargs -0 -r -n 100 awk '
  FNR == 1 { if (seen) printf "%d\t%s\n", lines, previous; previous = FILENAME; lines = 0; seen = 1 }
  { lines++ }
  END { if (seen) printf "%d\t%s\n", lines, previous }
') || {
  # A tree git cannot list is the third outcome, never a clean one: an unreadable source list would otherwise
  # reach the tracker as "no file is past the bound" (`backlog-sensors`, "The check itself failed").
  sensors_emit_error '{"path":"unknown"}' "no-source-list"
  exit 0
}

ranked=$(printf '%s\n' "$sizes" | grep -v '^$' | sort -t "$tab" -k1,1nr)

# The ranking is what makes "which files are largest" answerable without the row filing anything; it is the
# stderr half, and the findings below are the contract.
printf '%s\n' "$ranked" | grep -v '^$' | head -n 5 |
  while IFS="$tab" read -r lines path; do
    printf 'source-size: lines=%s path=%s\n' "$lines" "$path" >&2
  done

findings=0

while IFS="$tab" read -r lines path; do
  [ -n "$path" ] || continue
  sensors_compare "$lines" "$threshold" greater-than || continue
  fields=$(jq -nc --arg path "$path" '{path: $path}')
  fingerprint=$(sensors_fingerprint "$fields" "path")
  sensors_emit "$fingerprint" "$lines" violation "$fields" "" \
    "the source file is past the size bound" \
    "$path is $lines lines, past the row's bound of ${threshold}"
  findings=$((findings + 1))
done <<< "$ranked"

# Every condition this row owns and does not report as present has cleared — the fingerprint comes from the
# issue's own body record, so the clearing finding names the identity the issue carries (design D4).
while IFS= read -r path; do
  [ -n "$path" ] || continue
  current=$(printf '%s\n' "$ranked" | awk -v p="$path" -F "$tab" '$2 == p { print $1; exit }')
  [ -n "$current" ] || current=0
  sensors_compare "$current" "$threshold" greater-than && continue
  fields=$(jq -nc --arg path "$path" '{path: $path}')
  fingerprint=$(sensors_fingerprint "$fields" "path")
  sensors_emit "$fingerprint" "$current" clear "$fields" "" \
    "the source file is inside the size bound" \
    "$path is ${current} lines, inside the row's bound of ${threshold}"
  findings=$((findings + 1))
done < <(jq -r '.[]? | select(.state == "OPEN") | .condition.path // empty' <<< "${SENSORS_CONDITIONS:-[]}")

# A row that found nothing must say so: the runner reads a row that printed nothing as a failed check, and
# "no tracked source file is past the bound" is a verdict, not silence. The identity is the row's own, so the
# finding lands on no issue and closes nothing.
if [ "$findings" -eq 0 ]; then
  fields='{"path":"none"}'
  fingerprint=$(sensors_fingerprint "$fields" "path")
  sensors_emit "$fingerprint" "none" "clear" "$fields" "" \
    "no tracked source file is past the size bound" \
    "every tracked Kotlin and Java file is inside the row's bound of ${threshold} lines, and no size condition is recorded"
fi
