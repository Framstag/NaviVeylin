#!/usr/bin/env bash
# measure-the-tree.sh — the numbers this change states, taken with the shipping scripts (issue #197).
#
# Run from the change root: bash evidence/measure-the-tree.sh (or from anywhere with the tree as $1).
# The pairs are counted the way the metric derives them; the two rows are run through the registry, so the
# bounds printed are the ones the checks apply.
set -uo pipefail
root="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$root" || exit 2
tmp=$(mktemp -d "${TMPDIR:-/tmp}/measure-the-tree.XXXXXX") || exit 2
trap 'rm -rf "$tmp"' EXIT

tab=$(printf '\t')
named=$(ls guidelines/*.md | xargs -n1 basename | sed 's/\.md$//' | sort | paste -sd '|' -)
pairs=$(
  awk '/^## Documentation Map$/{f=1;next} f && /^## /{exit} f{print}' AGENTS.md |
    sed -E "s/($named)\\.md/@@&@@/g" |
    awk -F'@@' -v pat="$named" '
      BEGIN { filere = "^(" pat ")\\.md$" }
      {
        file = ""
        for (i = 1; i <= NF; i++) {
          if ($i ~ filere) { file = substr($i, 1, length($i) - 3); continue }
          if (file == "") continue
          chunk = $i
          while ((p = index(chunk, "§")) > 0) {
            chunk = substr(chunk, p + 1)
            if (match(chunk, /^[0-9]+[a-z]?/) == 0) continue
            num = substr(chunk, 1, RLENGTH); chunk = substr(chunk, RLENGTH + 1)
            txt = ""
            sub(/^[ \t]+/, "", chunk)
            if (substr(chunk, 1, 1) == "\"") { rest = substr(chunk, 2); q = index(rest, "\""); if (q > 0) { txt = substr(rest, 1, q - 1); chunk = substr(rest, q + 1) } }
            print file "\t" num "\t" txt
          }
        }
      }' | sort -u | wc -l
)

printf 'routed pairs: %s\n' "$pairs"
printf 'entry document: %s bytes, %s lines\n' "$(wc -c < AGENTS.md | tr -d ' ')" "$(wc -l < AGENTS.md | tr -d ' ')"
printf 'documents measured: %s (%s bytes over %s lines, %s bytes/line)\n' \
  "$(ls guidelines/*.md | wc -l | tr -d ' ')" \
  "$(cat AGENTS.md guidelines/*.md | wc -c | tr -d ' ')" \
  "$(cat AGENTS.md guidelines/*.md | wc -l | tr -d ' ')" \
  "$(awk -v b="$(cat AGENTS.md guidelines/*.md | wc -c)" -v l="$(cat AGENTS.md guidelines/*.md | wc -l)" 'BEGIN { printf "%.1f", b / l }')"

for id in routed-section-size entry-document-size; do
  row=$(jq -c --arg id "$id" '.rows[] | select(.id == $id)' tools/sensors/sensors.json)
  [ -n "$row" ] || { echo "no such row: $id" >&2; exit 2; }
  printf '\nrow %s (threshold %s, scope %s)\n' "$id" \
    "$(jq -r '.threshold' <<< "$row")" "$(jq -r '.scope' <<< "$row")"
  SENSORS_ID="$id" SENSORS_ROOT="$root" SENSORS_ROW="$row" \
    SENSORS_THRESHOLD="$(jq -r '.threshold' <<< "$row")" SENSORS_CONDITIONS='[]' \
    SENSORS_PROFILE="$root/tools/tooling.json" \
    bash tools/sensors/metrics/doc-context.sh > "$tmp/findings" 2> "$tmp/log"
  sed 's/^/  /' "$tmp/log"
  jq -r 'if .verdict == "violation" then "violation \(.document // .path).md §\(.section // "-") \(.measurement) bytes"
         else "\(.verdict) \(.document // .path) \(.measurement)" end' "$tmp/findings" ||
    printf '(the row reported nothing parseable)\n'
done

printf '\nthe pair count is the table as it stands: %s pairs, each one condition when past the bound\n' "$pairs"
