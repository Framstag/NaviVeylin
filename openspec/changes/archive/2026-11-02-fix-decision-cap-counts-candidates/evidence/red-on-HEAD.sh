#!/usr/bin/env bash
# The red case on the base tree: the case this change adds, driven against `71f7eafb`'s `tools/loop/run.sh`.
# Expected: FAIL — the base counts every labelled item, prints the parked #45 and states no candidate count.
# The working tree's fix is copied aside and restored, so nothing else in the tree is touched.
# Usage: bash red-on-HEAD.sh   (it reads its tree from its own location)
set -uo pipefail
root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)
cd "$root" || exit 2
save=$(mktemp)
cp tools/loop/run.sh "$save" || exit 2
git checkout -- tools/loop/run.sh || exit 2
bash tools/loop-selftest.sh --case 'run#decisionCapCountsOnlyCandidates'
status=$?
cp "$save" tools/loop/run.sh && rm -f "$save"
printf 'exit: %s (the fix is restored)\n' "$status"
exit "$status"
