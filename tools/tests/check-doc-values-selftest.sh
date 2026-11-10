#!/usr/bin/env bash
# Self-test for tools/check-doc-values.sh: the turn bounds a guideline states are the ones the profile records.
#
# Every case reads a throwaway fixture under a temp directory, except `values#proseMatchesTheProfile`: that case reads
# this repository's own `guidelines/Backlog.recipes.md` §7b and `tools/loop/loop.conf`, because its claim is about this
# tree's two records and not about a fixture's — it is the case that fails while the section still states the figures
# an earlier measurement set (`prose 170 config 200`). None of them writes anything.
#
# Cases: the section's table and the profile agree (`values#proseMatchesTheProfile`), a value the profile moved is
# reported with both figures (`values#aDriftedValueIsReported`), a role the table has no row for is reported
# (`values#aRoleWithNoProseRow`), a row the profile records no key for is reported the other way round
# (`values#aRowTheProfileDoesNotRecord`), and a profile that records no turn-bound key at all is refused rather than
# read as "nothing to compare" (`values#aProfileWithoutTheKeysIsRefused`).
#
# A case that asserted only the exit status would pass on a check that reports the wrong role or the wrong figure, so
# each failing case asserts the whole line the check prints — which is what a reader of the gate's log reads.
#
# Usage: check-doc-values-selftest.sh [--case <id>]
set -uo pipefail

root=$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel 2>/dev/null) || root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
check="$root/tools/check-doc-values.sh"
case_filter=""
while [ $# -gt 0 ]; do
  case "$1" in
    --case) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { echo "check-doc-values-selftest: $1 needs a value" >&2; exit 2; }; case_filter="${2:-}"; shift 2 ;;
    *) echo "check-doc-values-selftest: unknown argument '$1'" >&2; exit 2 ;;
  esac
done
tmp=$(mktemp -d "${TMPDIR:-/tmp}/doc-values-selftest.XXXXXX") || exit 2
trap 'rm -rf "$tmp"' EXIT

[ -x "$check" ] || { echo "check-doc-values-selftest: $check is not executable" >&2; exit 2; }

pass=0
failures=0

# mk_fixture <dir> [<the table's rows>]: a root that carries the section's table and the profile's keys — two roles,
# a row each, one figure each — which a case then edits from one side or the other.
mk_fixture() {
  local d=$1 rows=${2:-}
  mkdir -p "$d/guidelines" "$d/tools/loop"
  {
    printf '# Backlog recipes\n\n## §7b What an item costs, and the bounds on it\n\n'
    printf '| role (`LOOP_CHILD_TURNS_MAX_<ROLE>`) | records | the measurement that set it |\n'
    printf '|---|---|---|\n'
    if [ -n "$rows" ]; then
      printf '%s\n' "$rows"
    else
      printf '| `LOOP_CHILD_TURNS_MAX_WORKER` | 200 | p95 161.8 over 346 sessions |\n'
      printf '| `LOOP_CHILD_TURNS_MAX_REVIEWER` | 110 | p95 57.7 over 234 rounds |\n'
    fi
    printf '\nThe prose after the table states no figure of its own.\n'
  } > "$d/guidelines/Backlog.recipes.md"
  printf 'LOOP_CHILD_MAX_SECONDS=2700\nLOOP_CHILD_TURNS_MAX_WORKER=200\nLOOP_CHILD_TURNS_MAX_REVIEWER=110\n' \
    > "$d/tools/loop/loop.conf"
}

expect() { # <case id> <expected exit> <fixture dir> <expected substring> <description>
  local out got ok=1
  [ -z "$case_filter" ] || [ "$case_filter" = "$1" ] || return 0
  out=$("$check" --root "$3" 2>&1)
  got=$?
  [ "$got" = "$2" ] || ok=0
  printf '%s\n' "$out" | grep -qF -- "$4" || ok=0
  if [ "$ok" = 1 ]; then
    echo "PASS  $1 — $5"
    pass=$((pass + 1))
  else
    echo "FAIL  $1: exit $got (want $2) and '$4' in: $out"
    failures=$((failures + 1))
  fi
}

# 1. This tree: the table's six rows are the profile's six keys.
if [ -z "$case_filter" ] || [ "$case_filter" = "values#proseMatchesTheProfile" ]; then
  out=$("$check" --root "$root" 2>&1); got=$?
  if [ "$got" = 0 ] && printf '%s\n' "$out" | grep -qE '^LOOP-CHILD-TURNS-MAX: [0-9]+ roles, prose matches config$'; then
    echo "PASS  values#proseMatchesTheProfile — $out"
    pass=$((pass + 1))
  else
    echo "FAIL  values#proseMatchesTheProfile: the section's table and the profile disagree (exit $got): $out"
    failures=$((failures + 1))
  fi
fi

# 2. A value the profile moved is reported with both figures, the role and the key's section.
mk_fixture "$tmp/drift"
sed -i 's/^LOOP_CHILD_TURNS_MAX_WORKER=200$/LOOP_CHILD_TURNS_MAX_WORKER=201/' "$tmp/drift/tools/loop/loop.conf"
expect values#aDriftedValueIsReported 1 "$tmp/drift" 'LOOP-CHILD-TURNS-MAX: worker prose 200 config 201' \
  "a profile value the section does not state is reported with the figure the section states and the one the profile records"

# 3. A role the table has no row for is reported, so a key added to the profile is not silently unbounded in the prose.
mk_fixture "$tmp/no-row"
printf 'LOOP_CHILD_TURNS_MAX_REVIEWER_REWORK=150\n' >> "$tmp/no-row/tools/loop/loop.conf"
expect values#aRoleWithNoProseRow 1 "$tmp/no-row" 'LOOP-CHILD-TURNS-MAX: reviewer-rework prose - config 150' \
  "a profile role the section states no value for is reported, with the figure a run would apply"

# 4. …and the other direction: a row whose role the profile records no key for.
mk_fixture "$tmp/stale-row" '| `LOOP_CHILD_TURNS_MAX_WORKER` | 200 | p95 161.8 over 346 sessions |
| `LOOP_CHILD_TURNS_MAX_REVIEWER` | 110 | p95 57.7 over 234 rounds |
| `LOOP_CHILD_TURNS_MAX_REVIEWER_REWORK` | 120 | a bound no key applies |'
expect values#aRowTheProfileDoesNotRecord 1 "$tmp/stale-row" 'LOOP-CHILD-TURNS-MAX: reviewer-rework prose 120 config -' \
  "a stated bound the profile records no key for is reported, so the section cannot promise a value nothing enforces"

# 5. A profile with no turn-bound key is a defect, not an empty comparison.
mk_fixture "$tmp/no-keys"
printf 'LOOP_CHILD_MAX_SECONDS=2700\n' > "$tmp/no-keys/tools/loop/loop.conf"
expect values#aProfileWithoutTheKeysIsRefused 2 "$tmp/no-keys" 'records no LOOP_CHILD_TURNS_MAX_<ROLE> key' \
  "a profile with no turn-bound key is refused instead of read as a tree with nothing to compare"

if [ -n "$case_filter" ] && [ "$pass" = 0 ] && [ "$failures" = 0 ]; then
  echo "check-doc-values-selftest: no case with the id '$case_filter'" >&2
  exit 2
fi

if [ "$failures" -ne 0 ]; then
  echo "check-doc-values-selftest: $failures of $((pass + failures)) cases failed"
  exit 1
fi
echo "check-doc-values-selftest: all $pass cases passed (throwaway fixtures and this tree, no Gradle, no device, no network)"
exit 0
