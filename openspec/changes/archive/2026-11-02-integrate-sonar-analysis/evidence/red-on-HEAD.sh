#!/usr/bin/env bash
# The red case of change 2026-11-02-integrate-sonar-analysis (issue #741): the scan's structural check against the
# tree this change lands on. `tools/check-sonar-workflow.sh` is new with the change, so the case cannot exist on
# HEAD; what is run is the landed checker against a tree holding HEAD's files only, which is the smallest case
# that exercises the defect — nothing in the repository produces the analysis the three README badges measure.
#
# Writes `red-on-HEAD.log` (the run) and `red-on-HEAD.xml` (a JUnit-shaped record derived from the run's own exit
# code and its `::error` count, in the shape the gate's tallies use), both beside this script.
#
# Usage: bash openspec/changes/archive/<change>/evidence/red-on-HEAD.sh [<commit>]
set -uo pipefail
root="$(cd "$(dirname "$0")/../../../../.." && pwd)"
ev="$root/openspec/changes/archive/2026-11-02-integrate-sonar-analysis/evidence"
cd "$root" || exit 1

# The tree the change lands on: HEAD's tracked files, without the change's own additions.
head_commit=$(git rev-parse --short HEAD)
stamp=$(date -u +%Y-%m-%dT%H:%M:%SZ)
tmp=/tmp/nv-sonar-red-tree
rm -rf "$tmp"; mkdir -p "$tmp/.github/workflows"
git show HEAD:.github/workflows/build.yml > "$tmp/.github/workflows/build.yml"
git show HEAD:README.md > "$tmp/README.md"

out=$(bash "$root/tools/check-sonar-workflow.sh" --root "$tmp" 2>&1)
status=$?
errors=$(printf '%s\n' "$out" | grep -c '^::error' || true)

{
  echo "# red on HEAD: the tree the change lands on ($head_commit, $stamp)"
  echo "# The tree holds HEAD's workflows and README and none of the change's files."
  echo
  echo '$ grep -c sonarcloud README.md'
  printf '0\n'
  echo
  echo '$ ls .github/workflows/sonar.yml sonar-project.properties'
  ls "$tmp/.github/workflows/sonar.yml" "$tmp/sonar-project.properties" 2>&1
  echo
  echo '$ bash tools/check-sonar-workflow.sh --root <HEAD tree>'
  printf '%s\n' "$out"
  echo "exit=$status"
} > "$ev/red-on-HEAD.log" 2>&1

# The record is the run's own: one case, failing when the checker did, with the count of what it reported.
if [ "$status" -eq 0 ]; then
  failures=0
else
  failures=1
fi
{
  echo '<?xml version="1.0" encoding="UTF-8"?>'
  printf '<testsuite name="tools/check-sonar-workflow.sh --root &lt;HEAD tree&gt;" tests="1" failures="%s" errors="0" time="0" timestamp="%s">\n' "$failures" "$stamp"
  printf '  <system-out>%s error line(s) reported for the HEAD tree; the check exited %s</system-out>\n' "$errors" "$status"
  echo '</testsuite>'
} > "$ev/red-on-HEAD.xml"

rm -rf "$tmp"
cat "$ev/red-on-HEAD.log"
echo "---"
cat "$ev/red-on-HEAD.xml"
