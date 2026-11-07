#!/usr/bin/env bash
# The structural checks the change owes: no compiled file is touched, so the build gate is replaced by these and by
# the loop's own suite, which is the gate for `tools/loop/**`.
set -uo pipefail
cd "$(dirname "$0")/../../../.." || exit 2
ev=openspec/changes/fix-a-child-has-a-working-search/evidence

echo "# structural checks the change owes (no compiled file is touched)"
echo "\$ bash -n tools/look.sh tools/pi-child-events.sh tools/loop-cases.sh tools/loop-mutations.sh tools/look-selftest.sh"
if bash -n tools/look.sh tools/pi-child-events.sh tools/loop-cases.sh tools/loop-mutations.sh tools/look-selftest.sh; then
  echo ok
fi
echo "\$ bash tools/check-portability.sh"
bash tools/check-portability.sh 2>&1 | tail -1
echo "\$ bash tools/check-doc-routes.sh"
bash tools/check-doc-routes.sh 2>&1 | tail -1
echo "\$ bash tools/check-openspec-hygiene.sh"
bash tools/check-openspec-hygiene.sh 2>&1 | tail -1
echo "\$ openspec validate fix-a-child-has-a-working-search --strict"
openspec validate fix-a-child-has-a-working-search --strict 2>&1 | tail -1
echo "\$ bash tools/loop-selftest.sh (the gate for tools/loop/**)"
grep -h "^loop-selftest: all" "$ev/loop-suite.log" || echo "no suite summary in $ev/loop-suite.log"
echo "at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
