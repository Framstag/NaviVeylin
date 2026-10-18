#!/usr/bin/env bash
# The mutation runner's self-test: prove the runner's own four guards can fail, on a throwaway toy tree.
#
# `tools/mutate.sh` is the mechanical form of `.pi/skills/revert-check` for gates that are shell self-tests.
# A runner that cannot report its own failure is worth nothing — the two ways a hand-rolled pass lies
# (`MUTATION-DID-NOT-APPLY`, `STAYS-GREEN`) and the two ways a restore lies (`RESTORE-FAILED`,
# `RESTORE-STAYS-RED`) are each made to happen here, on purpose, and each must be reported as a failure with
# a non-zero exit status. The repository's own sets (`tools/portability-mutations.json`) are listed here too,
# so a set that no longer parses fails this check instead of rotting.
#
# Reads text files and runs `git` in a throwaway repository under the gitignored `.pi/tmp/`: no token, no
# network, no Gradle, no device.
#
# Usage: bash tools/mutate-selftest.sh
# Exit:  0 every guard behaved, 1 one did not, 2 the environment is unusable.
set -uo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
[ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ] && { sed -n '2,16p' "$0"; exit 0; }

for tool in bash git jq awk cmp; do
  command -v "$tool" >/dev/null 2>&1 || { printf 'mutate-selftest: %s is required\n' "$tool" >&2; exit 2; }
done

mkdir -p "$root/.pi/tmp" 2>/dev/null || true
tmp=$(mktemp -d "$root/.pi/tmp/mutate-selftest.XXXXXX") || {
  printf 'mutate-selftest: cannot create a scratch directory under %s/.pi/tmp\n' "$root" >&2
  exit 2
}
trap 'rm -rf "$tmp"' EXIT

fixtures="$root/tools/fixtures/mutate"
tree="$tmp/tree"
mkdir -p "$tree"
cp "$fixtures/engine.sh" "$fixtures/case.sh" "$tree/"
git -C "$tree" init -q -b main >/dev/null 2>&1 || { printf 'mutate-selftest: cannot create the toy repository\n' >&2; exit 2; }
git -C "$tree" add -A >/dev/null 2>&1
git -C "$tree" -c user.email=selftest@example.invalid -c user.name=selftest commit -qm init >/dev/null 2>&1

passed=0
failed=0

check() { # <description> <condition-result 0|1>
  if [ "$2" -eq 0 ]; then
    printf 'PASS  %s\n' "$1"
    passed=$((passed + 1))
  else
    printf 'FAIL  %s\n' "$1"
    failed=$((failed + 1))
  fi
}

expect() { # <description> <expected> <actual>
  if [ "$2" = "$3" ]; then
    printf 'PASS  %s\n' "$1"
    passed=$((passed + 1))
  else
    printf 'FAIL  %s — expected %s, got %s\n' "$1" "$2" "$3"
    failed=$((failed + 1))
  fi
}

run() { # <mutations file> [extra args…] → exit status, transcript in $tmp/out.log
  local file="$1"; shift
  bash "$root/tools/mutate.sh" --mutations "$file" --root "$tree" --timeout 60 "$@" > "$tmp/out.log" 2>&1
  printf '%s' "$?"
}

tree_is_pristine() {
  [ -z "$(git -C "$tree" status --porcelain)" ]
}

# 1. a real mutation: red, restored, green
rc=$(run "$fixtures/mutations-real.json")
expect "a real mutation exits 0" "0" "$rc"
check "the transcript reports the red run" "$(grep -q 'RED(exit ' "$tmp/out.log"; printf '%s' "$?")"
check "the transcript reports the restored green run" "$(grep -q 'GREEN(exit 0)' "$tmp/out.log"; printf '%s' "$?")"
check "the byte-for-byte restore is reported" "$(grep -q 'restored, and the case passes again' "$tmp/out.log"; printf '%s' "$?")"
check "the toy tree is pristine after the run" "$(tree_is_pristine; printf '%s' "$?")"

# 2. a search that matches nothing: the mutation never lands, and the file must not be touched
rc=$(run "$fixtures/mutations-no-apply.json")
expect "a mutation that does not apply exits 1" "1" "$rc"
check "MUTATION-DID-NOT-APPLY is reported" "$(grep -q 'MUTATION-DID-NOT-APPLY' "$tmp/out.log"; printf '%s' "$?")"
check "the toy tree is pristine after the failed mutation" "$(tree_is_pristine; printf '%s' "$?")"

# 3. a mutation the case does not notice: the runner must never call that a pass
rc=$(run "$fixtures/mutations-stays-green.json")
expect "a mutation the case does not notice exits 1" "1" "$rc"
check "STAYS-GREEN is reported" "$(grep -q 'STAYS-GREEN' "$tmp/out.log"; printf '%s' "$?")"
check "the toy tree is pristine after the unnoticed mutation" "$(tree_is_pristine; printf '%s' "$?")"

# 4. a record whose file the tree does not have
rc=$(run "$fixtures/mutations-missing-file.json")
expect "a record for a missing file exits 1" "1" "$rc"
check "MUTATION-FILE-MISSING is reported" "$(grep -q 'MUTATION-FILE-MISSING' "$tmp/out.log"; printf '%s' "$?")"

# 5. --list, and --only selecting nothing (a usage error, exit 2)
rc=$(run "$fixtures/mutations-real.json" --list)
expect "--list exits 0" "0" "$rc"
check "--list names the label, the case and the reason" \
  "$(grep -q 'real	toy#guardIsPresent	the toy guard' "$tmp/out.log"; printf '%s' "$?")"
rc=$(run "$fixtures/mutations-real.json" --only nope)
expect "--only selecting no mutation exits 2" "2" "$rc"

# 6. the repository's own mutation sets parse, and their labels are the ones the change's tasks name
rc=$(bash "$root/tools/mutate.sh" --mutations "$root/tools/portability-mutations.json" --list 2>&1 | tee "$tmp/portability.log" >/dev/null; printf '%s' "$?")
expect "the portability set lists cleanly" "0" "$rc"
expect "the portability set has eight records" "8" "$(grep -vc '^#' "$tmp/portability.log")"
for label in 5.1 5.2 5.3 5.3b 5.4 5.5 5.6 5.7; do
  check "the portability set declares $label" "$(grep -q "^$label	" "$tmp/portability.log"; printf '%s' "$?")"
done
check "the portability set names the check's own self-test as its gate" \
  "$(grep -q 'tools/check-portability-selftest.sh --case {case}' "$tmp/portability.log"; printf '%s' "$?")"

printf '\nmutate-selftest: %s passed, %s failed\n' "$passed" "$failed"
[ "$failed" -eq 0 ] || exit 1
exit 0
