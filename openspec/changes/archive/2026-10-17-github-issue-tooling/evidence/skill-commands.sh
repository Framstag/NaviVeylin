#!/usr/bin/env bash
# The four skills' documented commands, run as documented against the stub `gh` (tasks.md 6.1-6.4).
# Each line is `<command>  [exit=<code>]`; a mismatch with the skill's exit-code table prints MISSING.
set -uo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)
cd "$root" || exit 2
export PATH="$root/tools/issues/fixtures:$PATH"
scratch=$(mktemp -d "${TMPDIR:-/tmp}/skill-commands.XXXXXX") || exit 2
trap 'rm -rf "$scratch"' EXIT
export GH_STUB_RECORD="$scratch/calls.log"
: > "$GH_STUB_RECORD"

mismatches=0
run() {
  local want="$1"; shift
  printf -- '-- %s\n' "$*"
  "$@"
  local got=$?
  if [ "$got" != "$want" ]; then
    printf '   exit=%s (the skill documents %s)  <-- MISMATCH\n' "$got" "$want"
    mismatches=$((mismatches + 1))
  else
    printf '   exit=%s (as documented)\n' "$got"
  fi
}

jq '.findings[0]' "$root/tools/issues/fixtures/findings.json" > "$scratch/finding.json"
printf -- '- **Observed** ✗: a replacement body.\n' > "$scratch/body.md"

echo "=== create-issue — base fixtures, no issues yet"
export GH_STUB_DIR="$root/tools/issues/fixtures"
run 0 bash tools/issues/create.sh <<'FINDING'
{
  "title": "The phone card header shows a number the step list disagrees with",
  "class": "bug",
  "category": "ui",
  "claim": "The phone card header shows a number the step list disagrees with",
  "anchor": "app/src/main/java/com/naviveylin/…/Card.kt:HeaderStatistics",
  "labels": ["class/bug", "category/ui", "status/open", "source/agent"],
  "body": "- **Observed** ✗: the header and the sum of the steps disagree by 34 %.\n- **Evidence** ⊨: `RouteInstructionPositionDeviceTest`, run 2026-10-17."
}
FINDING
run 0 bash tools/issues/create.sh --finding "$scratch/finding.json"
run 0 bash tools/issues/create.sh --label category/car --finding "$scratch/finding.json"
run 0 bash tools/issues/create.sh --status open-device --finding "$scratch/finding.json"
run 0 bash tools/issues/create.sh --allow-duplicate --finding "$scratch/finding.json"
run 0 bash tools/issues/create.sh --json --finding "$scratch/finding.json"
run 3 bash tools/issues/create.sh --label agent/eligible --finding "$scratch/finding.json"

echo
echo "=== update-issue and list-issues — the three-issue fixture"
export GH_STUB_DIR="$root/tools/issues/fixtures/scenarios/three"
run 0 bash tools/issues/update.sh 12 --label category/car
run 0 bash tools/issues/update.sh 12 --status open-device
run 0 bash tools/issues/update.sh 12 --body "$scratch/body.md"
run 0 bash tools/issues/update.sh 12 --label class/bug --label source/ci
run 0 bash tools/issues/update.sh 12 --status open-device --json
run 3 bash tools/issues/update.sh 12 --label category/wibble
run 3 bash tools/issues/update.sh 12 --label agent/eligible
run 2 bash tools/issues/update.sh 12
run 0 bash tools/issues/list.sh
run 0 bash tools/issues/list.sh --class bug
run 0 bash tools/issues/list.sh --category ui
run 0 bash tools/issues/list.sh --status open-device
run 0 bash tools/issues/list.sh --author Framstag
run 0 bash tools/issues/list.sh --eligible-only
run 0 bash tools/issues/list.sh --class bug --status open --json
run 0 bash tools/issues/list.sh --limit 100

echo
echo "=== close-issue — #11 carries the opt-in label, #12 does not"
run 2 bash tools/issues/close.sh 11 --reason "the router's overall distance is taken from the description total"
run 2 bash tools/issues/close.sh 11 --evidence "RouteInstructionPositionDeviceTest is green on HEAD"
run 0 bash tools/issues/close.sh 11 --reason "the router's overall distance is taken from the description total" \
  --evidence "RouteInstructionPositionDeviceTest is green on HEAD"
run 0 bash tools/issues/close.sh 11 --reason "not worth the churn" --evidence "discussion on #17" --not-planned
run 3 bash tools/issues/close.sh 12 --reason "r" --evidence "e"

echo
echo "writes the stub recorded: $(grep -c . "$GH_STUB_RECORD")"
if [ "$mismatches" -gt 0 ]; then
  echo "skill commands: $mismatches command(s) did not match their documented exit code"
  exit 1
fi
echo "skill commands: every documented command ran against the stub with its documented exit code"
