#!/usr/bin/env bash
# The change's revert checks (tasks.md §7): one mutation each, the named case must go red, then the mutation
# is restored and the same case must be green again. A mutation that does not falsify its case is itself a
# failure — it means the case does not exercise what the task says it does.
#
# This script mutates the working tree and restores it from a pristine copy it takes first, including on
# exit. Run it from the repository:  bash openspec/changes/github-issue-tooling/evidence/revert-checks.sh
set -uo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)
work=$(mktemp -d "${TMPDIR:-/tmp}/revert-checks.XXXXXX") || exit 2
cp -R "$root/tools/issues" "$work/issues"
cp "$root/tools/tooling.json" "$work/tooling.json"

restore() {
  rm -rf "$root/tools/issues"
  cp -R "$work/issues" "$root/tools/issues"
  cp "$work/tooling.json" "$root/tools/tooling.json"
}
trap 'restore; rm -rf "$work"' EXIT

case_state() {
  local raw
  raw=$(bash "$root/tools/issue-tooling-selftest.sh" --case "$1" 2>&1)
  printf '%s\n' "$raw" | grep -E "^(PASS|FAIL) $1(:|$)" | head -1
  if ! printf '%s\n' "$raw" | grep -qE "^(PASS|FAIL) $1(:|$)"; then
    printf '%s\n' "$raw" | sed 's/^/    raw: /' >&2
  fi
}

failures=0
check() {
  local id="$1" case_name="$2" description="$3"; shift 3
  printf '\n%s — %s\n  mutation: %s\n' "$id" "$case_name" "$description"
  "$@"
  local before after
  before=$(case_state "$case_name")
  restore
  after=$(case_state "$case_name")
  printf '  with the mutation: %s\n  restored:          %s\n' "${before:-<no result>}" "${after:-<no result>}"
  case "$before" in
    FAIL\ *) ;;
    *) printf '  FALSIFICATION FAILED: the mutation did not turn %s red\n' "$case_name"; failures=$((failures + 1)) ;;
  esac
  if [ -z "$before" ]; then
    printf '  (the selftest produced no verdict — its output is above)\n'
  fi
  case "$after" in
    PASS\ *) ;;
    *) printf '  RESTORE FAILED: %s is not green after restoring\n' "$case_name"; failures=$((failures + 1)) ;;
  esac
}

m71() { perl -0pi -e 's/"allowedAuthors": \["[^"]*"\]/"allowedAuthors": ["no-one"]/' "$root/tools/tooling.json"; }
m72() { perl -0pi -e 's/\s*\+ "\|anchor=" \+ normalize\(stripLineNumbers\(anchor\)\)//' "$root/tools/issues/core/Fingerprint.java"; }
m73() { perl -0pi -e 's/      "stylesheets",/      "stylesheets",\n      "wibble",/' "$root/tools/tooling.json"; }
m74() { perl -0pi -e 's/String escaped = body\.replace\(OPEN, "<<< ISSUE_TEXT"\);/String escaped = body;/' "$root/tools/issues/core/WorkPackage.java"; }
m75() { perl -0pi -e 's/"maxBodyBytes": [0-9]+/"maxBodyBytes": 8388608/' "$root/tools/tooling.json"; }
m76a() { perl -0pi -e 's/(public final class Taxonomy \{)/$1\n\n    static final String LEAKED_VALUE = "verification";/' "$root/tools/issues/core/Taxonomy.java"; }
m76b() { perl -0pi -e 's|ISSUES_PROFILE="\$ISSUES_ROOT/tools/tooling.json"|ISSUES_PROFILE="$(dirname "$ISSUES_LIB_DIR")/tooling.json"|' "$root/tools/issues/lib.sh"; }
m77() { perl -0pi -e 's/--arg marker "<!-- nvid: \$\{source_id\} -->"/--arg marker "NEVER-MATCHES"/' "$root/tools/issues/import.sh"; }
m78() { perl -0pi -e 's/"reservedLabels": \[[^]]*\]/"reservedLabels": []/' "$root/tools/tooling.json"; }

echo "revert checks — $root"
echo "started: $(date -u +%Y-%m-%dT%H:%M:%SZ)"

check 7.1 'Policy#gateMatrix' 'remove the allowlisted author from the profile' m71
check 7.1b 'policy#preflightRefusesUnknownLogin' 'the same mutation, seen through the preflight command' m71
check 7.2 'Fingerprint#separatesDistinctEntries' 'drop the anchor term from the canonical string' m72
check 7.3 'Taxonomy#unknownValueRefuses' 'add an undeclared value to the category vocabulary' m73
check 7.3b 'update#refusesUnknownLabel' 'the same mutation, seen through the update path' m73
check 7.4 'WorkPackage#bodyCannotBreakOutOfTheDataBlock' 'remove the delimiter escaping' m74
check 7.5 'WorkPackage#oversizedBodyRefuses' 'raise the body bound past the fixture' m75
check 7.6 'lib#noProjectValueInTheEngine' 'name a profile value in the engine' m76a
check 7.6b 'lib#discoversTheCurrentProject' 'resolve the profile beside the engine instead of the project' m76b
check 7.7 'import#secondRunIsANoOp' 'make the reconciler ignore the existing source id' m77
check 7.8 'surface#reservedLabelRefusesInEveryWritePath' 'drop the reserved label from the profile' m78
check 7.8b 'surface#reservationIsNotBypassedByTheOtherLabelFlags' 'the same mutation, through the other label routes' m78

echo
if [ "$failures" -gt 0 ]; then
  echo "revert checks: $failures check(s) did not behave as required"
  exit 1
fi
echo "revert checks: every mutation falsified its case and every restore left the suite green"
echo "finished: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
