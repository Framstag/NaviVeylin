#!/usr/bin/env bash
# revert-check.sh — the change's one mutation: put the split scenario's Case line back the way HEAD had it and
# the probe must go red; restore the archived restatement and it must go green.
#
# The invariant this change introduces is the probe's claim itself, so the mutation is the exact text the item
# reports: the scenario names `routed-read#bound` alone and says nothing about the sensor's condition. The
# mutation is applied to the merged requirement block of `openspec/specs/documentation-ownership/spec.md`, then
# restored from a byte-identical backup, so the tree is left as the archive left it.
#
# Usage: revert-check.sh [--root <dir>]
# Exit: 0 the mutation was red and the restore was green; 1 otherwise.
set -uo pipefail

root=""
while [ $# -gt 0 ]; do
  case "$1" in
    --root) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf 'revert-check: %s needs a value\n' "$1" >&2; exit 2; }; root="$2"; shift 2 ;;
    *) printf 'revert-check: unknown argument %s\n' "$1" >&2; exit 2 ;;
  esac
done
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -z "$root" ]; then
  root="$here"
  while [ ! -d "$root/openspec" ] && [ "$root" != "/" ]; do root="$(dirname "$root")"; done
fi
spec="$root/openspec/specs/documentation-ownership/spec.md"
probe="$here/red-probe.sh"

backup=$(mktemp)
head_block=$(mktemp)
mutated=$(mktemp)
trap 'cp "$backup" "$spec"; rm -f "$backup" "$head_block" "$mutated"' EXIT
cp "$spec" "$backup"

# The requirement block as HEAD had it — the text the item reports.
git -C "$root" show 2f954a04b3f75e138c529fffe7985ca011375866:openspec/specs/documentation-ownership/spec.md \
  | sed -n '/^### Requirement: The routing cost is measured$/,/^### Requirement: The always-read document states facts$/p' \
  | head -n -1 > "$head_block"
[ -s "$head_block" ] || { printf 'revert-check: the base commit carries no requirement block\n' >&2; exit 1; }

awk -v block="$head_block" '
  /^### Requirement: The routing cost is measured$/ {
    skip = 1
    while ((getline line < block) > 0) print line
    close(block)
    next
  }
  skip && /^### Requirement: / { skip = 0 }
  skip { next }
  { print }' "$backup" > "$mutated"
cp "$mutated" "$spec"

printf '## mutated — the split scenario names `routed-read#bound` alone, as HEAD had it\n'
if bash "$probe" --root "$root"; then
  printf 'MUTATION NOT NOTICED — the probe passed on the mutated scenario\n'
  cp "$backup" "$spec"
  exit 1
fi
printf '\n## restored — the archived restatement back in place\n'
cp "$backup" "$spec"
cmp -s "$backup" "$spec" || { printf 'revert-check: the restore is not byte-identical\n' >&2; exit 1; }
bash "$probe" --root "$root"
