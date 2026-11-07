#!/usr/bin/env bash
# red-on-HEAD.sh — the red half of `artifact-hygiene#anOwnerCitedAsADirectoryListingIsReported`, taken against
# the base's checker rather than this change's.
#
# The case is the change's own (it is in `tools/check-openspec-hygiene-selftest.sh` in this tree); what it is run
# against is `check-openspec-hygiene.sh` as base `650028bf` wrote it, checked out into a throwaway root. The case
# therefore reports the base tree's answer, which is the claim the change makes: on HEAD a record that cites its
# owners as `.pi/skills/*/SKILL.md` passes, and nothing reports it.
#
# The tally is derived from the suite's own summary line, exactly as `tools/loop/gate.sh` derives the gate's —
# never invented here: `N of M cases failed` becomes tests="M" failures="N".
#
# Usage: bash red-on-HEAD.sh [<base>]      (default base: 650028bfdf6e652ebd045b4af1be0a90b6c0a481)
set -uo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." || exit 2   # the change root
base="${1:-650028bfdf6e652ebd045b4af1be0a90b6c0a481}"
case_id="artifact-hygiene#anOwnerCitedAsADirectoryListingIsReported"
out_dir="openspec/changes/fix-relocation-record-names-its-owners/evidence"

scratch=$(mktemp -d) || { echo "red-on-HEAD: no scratch directory" >&2; exit 2; }
trap 'rm -rf "$scratch"' EXIT

mkdir -p "$scratch/tools" || exit 2
git show "$base:tools/check-openspec-hygiene.sh" > "$scratch/tools/check-openspec-hygiene.sh" || exit 2
printf '# the checker this case was run against is base %s\n' "$base"

set +e
bash tools/check-openspec-hygiene-selftest.sh --root "$scratch" --case "$case_id" 2>&1 | tee "$scratch/run.log"
rc=${PIPESTATUS[0]}
set -e 2>/dev/null

summary=$(grep -E 'cases? (passed|failed)' "$scratch/run.log" | tail -1)
tests=$(sed -n 's/.*all \([0-9]\+\) cases\? passed.*/\1/p' <<<"$summary")
failures=0
if [ -z "$tests" ]; then
  tests=$(sed -n 's/.*of \([0-9]\+\) cases\? failed.*/\1/p' <<<"$summary")
  failures=$(sed -n 's/^.*: \([0-9]\+\) of [0-9]\+ cases\? failed.*/\1/p' <<<"$summary")
fi
[ -n "$tests" ] || { echo "red-on-HEAD: no summary line to derive a tally from" >&2; exit 2; }

mkdir -p "$out_dir"
{
  printf '# %s — run against base %s\n' "$case_id" "$base"
  printf '# %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf '# exit=%s\n\n' "$rc"
  cat "$scratch/run.log"
} > "$out_dir/red-on-HEAD.log"
printf '<?xml version="1.0" encoding="UTF-8"?>\n<testsuite name="tools/check-openspec-hygiene-selftest.sh --case %s" tests="%s" failures="%s" errors="0" time="0">\n  <system-out>%s</system-out>\n</testsuite>\n' \
  "$case_id" "$tests" "$failures" "$(sed 's/[<&>]/ /g' <<<"$summary")" > "$out_dir/red-on-HEAD.xml"

printf 'RED-CHECK %s: exit=%s tests=%s failures=%s\n' "$case_id" "$rc" "$tests" "$failures"
[ "$rc" = 1 ] && [ "$failures" -gt 0 ]
