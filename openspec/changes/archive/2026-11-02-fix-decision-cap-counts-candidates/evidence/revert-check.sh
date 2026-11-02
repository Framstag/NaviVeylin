#!/usr/bin/env bash
# Falsification of this change's own invariant: the count is taken over the candidates. The mutation removes
# `--eligible-only` from the step-6 read and nothing else, so the run is back to counting every labelled item.
# Expected: the named case fails on the mutation, passes once the mutation is lifted.
# Usage: bash revert-check.sh   (it reads its tree from its own location)
set -uo pipefail
root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)
cd "$root" || exit 2
save=$(mktemp)
cp tools/loop/run.sh "$save" || exit 2
restore() { cp "$save" tools/loop/run.sh && rm -f "$save"; }
trap restore EXIT
sed -i 's|^open_decisions=$(loop_issue_tool list.sh --json --limit 400 --eligible-only|open_decisions=$(loop_issue_tool list.sh --json --limit 400|' \
  tools/loop/run.sh || exit 2
printf '=== mutated (--eligible-only removed) ===\n'
bash tools/loop-selftest.sh --case 'run#decisionCapCountsOnlyCandidates' 2>&1 | grep -E '^(PASS|FAIL|loop-selftest:)'
restore
trap - EXIT
printf '=== restored ===\n'
bash tools/loop-selftest.sh --case 'run#decisionCapCountsOnlyCandidates' 2>&1 | grep -E '^(PASS|FAIL|loop-selftest:)'
