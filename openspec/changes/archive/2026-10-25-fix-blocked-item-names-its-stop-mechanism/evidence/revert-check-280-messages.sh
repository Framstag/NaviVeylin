#!/usr/bin/env bash
# The failing assertion each of issue #280's five records notices, quoted.
#
# `tools/loop-mutations.sh` silences a case's output (`>/dev/null 2>&1`) and prints only `RED <label> <case>`, so
# the round-1 log said a record was noticed without saying what noticed it. This applies the same expression of the
# same record to the same file and runs the same case with its output visible, then restores the file.
#
# Usage: bash revert-check-280-messages.sh [change-root]
set -uo pipefail
root=${1:-.}
cd "$root" || exit 2
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
for label in 58.1 58.2 58.3 58.4 58.5; do
  record=$(grep -m1 "^  '${label}@" tools/loop-mutations.sh) || { echo "no record $label" >&2; exit 2; }
  record=${record#  \'}
  record=${record%\'}
  IFS='@' read -r _l _fifo file case_name desc exprs <<<"$record"
  cp -- "$file" "$tmp/backup"
  printf '%s\n' "$exprs" | sed 's/;;/\n/g' | while IFS= read -r expr; do
    [ -z "$expr" ] || sed -i "$expr" "$file"
  done
  printf '== %s — %s %s (%s)\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$label" "$case_name" "$desc"
  if cmp -s "$file" "$tmp/backup"; then
    printf 'MUTATION-DID-NOT-APPLY %s\n' "$label"
  else
    bash tools/loop-selftest.sh --case "$case_name" 2>&1 | grep -v '^PASS' | head -6
    rc=${PIPESTATUS[0]}
    printf '   case exit=%s\n' "$rc"
  fi
  cp -- "$tmp/backup" "$file"
  cmp -s "$file" "$tmp/backup" && printf '   restored\n' || printf 'RESTORE-FAILED %s\n' "$label"
done
