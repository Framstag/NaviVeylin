#!/usr/bin/env bash
# derive-probe.sh — the reading set one path derives from the map, derived the way
# `tools/check-rule-routes.sh --diff` derives it: the union of every route whose glob matches the path.
#
# Usage: derive-probe.sh <map> <path>…
# Prints, per path: the entry count, the documents the entries name, and the globs that matched.
#
# It exists because `--diff` derives from a git range, and a baseline measurement is wanted for a path a
# change has not touched yet. Run from the change root.
set -uo pipefail
map="${1:?usage: derive-probe.sh <map> <path>...}"; shift
tmp=$(mktemp -d "${TMPDIR:-/tmp}/derive-probe.XXXXXX") || exit 2
trap 'rm -rf "$tmp"' EXIT
for path in "$@"; do
  : > "$tmp/globs"
  : > "$tmp/entries"
  # The map is read one route per line, and the match is `case`, so no expansion of a glob's own text
  # happens anywhere (an unquoted `$glob` would be pathname-expanded by the shell).
  while IFS= read -r route; do
    glob=$(jq -r '.glob' <<< "$route")
    # shellcheck disable=SC2254
    case "$path" in $glob) ;; *) continue ;; esac
    printf '%s\n' "$glob" >> "$tmp/globs"
    jq -r '.sections[]?, .rules[]?' <<< "$route" >> "$tmp/entries"
  done < <(jq -c '.routes[]' "$map")
  sort -u "$tmp/globs" -o "$tmp/globs"
  grep -E '\.md §' "$tmp/entries" | sort -u > "$tmp/derived"
  printf '%s\n' "$path"
  printf '  globs: %s\n' "$(tr '\n' ' ' < "$tmp/globs")"
  printf '  entries: %s\n' "$(grep -c . "$tmp/derived")"
  printf '  documents: %s\n' "$(sed -E 's/^([A-Za-z0-9_-]+)\.md.*/\1/' "$tmp/derived" | sort -u | tr '\n' ' ')"
done
