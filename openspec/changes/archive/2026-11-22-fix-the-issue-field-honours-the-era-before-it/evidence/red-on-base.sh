#!/usr/bin/env bash
# red-on-base.sh — the reading this change is red on, taken without reverting the tree: the base checker
# (`git show <base>:tools/check-openspec-hygiene.sh`) is copied beside this change's case file into a scratch root,
# and that case file is run over it. The base file is the one the item was filed against, so a case that fails here
# and passes in the tree is the evidence the change answers the item.
#
# Usage: evidence/red-on-base.sh [<root>] [<case name>]   (default root: this repository's top level)
#        the optional case name narrows the run to one case (`artifact-hygiene#…`), which is the reading a
#        single-case proof quotes; the whole set is the reading that the other cases assert what the base does.
# Exit:  the case file's own — 1 is the red reading this change expects, 0 would mean the base already reports the era.
set -uo pipefail
root="${1:-}"
only_case="${2:-}"
if [ -z "$root" ]; then
  root=$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel) || exit 2
fi
base=f826cd7a5e4952fc0e5331a32dcea0cb67baa99a
scratch=$(mktemp -d) || exit 2
trap 'rm -rf "$scratch"' EXIT
mkdir -p "$scratch/tools/tests"
git -C "$root" show "$base:tools/check-openspec-hygiene.sh" > "$scratch/tools/check-openspec-hygiene.sh" \
  || { echo "red-on-base: the base commit carries no $base:tools/check-openspec-hygiene.sh" >&2; exit 2; }
cp "$root/tools/tests/check-openspec-hygiene-selftest.sh" "$scratch/tools/tests/" || exit 2
echo "# the base checker ($base:tools/check-openspec-hygiene.sh) over this change's twelve cases, in a throwaway root"
echo "# $(date -u +%Y-%m-%dT%H:%M:%SZ)"
if [ -n "$only_case" ]; then
  bash "$scratch/tools/tests/check-openspec-hygiene-selftest.sh" --root "$scratch" --case "$only_case"
else
  bash "$scratch/tools/tests/check-openspec-hygiene-selftest.sh" --root "$scratch"
fi
rc=$?
echo "red-on-base exit: $rc"
exit "$rc"
