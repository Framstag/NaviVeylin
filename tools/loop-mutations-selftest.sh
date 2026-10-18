#!/usr/bin/env bash
# The mutation harness's self-test — it proves the *harness* (not a change) can go red, on a **throwaway copy**
# of the engine, so it is safe to run anywhere and costs no mutation of the working tree.
#
# Three things are asserted, and each is the harness's own failure mode (`tools/loop-mutations.sh`):
#
#   * a mutation whose pattern no longer matches the file is `MUTATION-DID-NOT-APPLY`, exit 1 — never a
#     silent pass;
#   * a mutation that lands but that no case notices is `the case did not notice the mutation`, exit 1;
#   * a mutation that lands and whose case notices it is `RED`, the restore is `green again`, and the copy is
#     byte-identical to the pristine one when the harness is done.
#
# Usage: bash tools/loop-mutations-selftest.sh
# Exit:  0 all three hold; 1 one of them does not; 2 the environment is unusable.
set -uo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
harness="$root/tools/loop-mutations.sh"

for tool in bash cmp sed jq git java javac; do
  command -v "$tool" >/dev/null 2>&1 || { echo "loop-mutations-selftest: $tool is required" >&2; exit 2; }
done
[ -x "$harness" ] || { echo "loop-mutations-selftest: $harness is missing or not executable" >&2; exit 2; }

mkdir -p "$root/.pi/tmp" 2>/dev/null || true
scratch=$(mktemp -d "$root/.pi/tmp/loop-mutations-selftest.XXXXXX") || {
  echo "loop-mutations-selftest: cannot create a scratch directory under $root/.pi/tmp" >&2
  exit 2
}
trap 'rm -rf "$scratch"' EXIT

# The pristine engine, and the copy the harness will mutate. The copy keeps the layout the engine expects:
# `…/tools/loop` with `…/tools/issues` beside it (`lib.sh` resolves its dependency as a sibling), and the
# dependency is a symlink because no mutation touches it.
pristine="$scratch/pristine"
cp -a "$root/tools/loop" "$pristine"
mkdir -p "$scratch/engine/tools"
cp -a "$root/tools/loop" "$scratch/engine/tools/loop"
ln -s "$root/tools/issues" "$scratch/engine/tools/issues"
copy_dir="$scratch/engine/tools/loop"

fail=0
check() { if [ "$2" = "$3" ]; then echo "ok   $1"; else echo "FAIL $1: expected [$2] got [$3]"; fail=1; fi; }

# 1. a mutation whose pattern no longer matches must be reported, not swallowed
out=$(timeout 300 bash "$harness" --probe no-apply --engine "$copy_dir" 2>&1)
status=$?
check "a non-matching mutation exits non-zero" "1" "$status"
case "$out" in
  *"MUTATION-DID-NOT-APPLY"*) echo "ok   and it is named as such";;
  *) echo "FAIL the guard message: [$out]"; fail=1;;
esac
diff -r "$scratch/pristine" "$copy_dir" >/dev/null 2>&1 ||
  { echo "FAIL the copy was not restored after the non-matching probe"; fail=1; }

# 2. a mutation that lands but that no case notices must be reported, not accepted
out=$(timeout 600 bash "$harness" --probe stays-green --engine "$copy_dir" 2>&1)
status=$?
check "an unnoticed mutation exits non-zero" "1" "$status"
case "$out" in
  *"did not notice the mutation"*) echo "ok   and it says the case did not notice";;
  *) echo "FAIL the unnoticed-mutation message: [$out]"; fail=1;;
esac
diff -r "$scratch/pristine" "$copy_dir" >/dev/null 2>&1 ||
  { echo "FAIL the copy was not restored after the stays-green probe"; fail=1; }

# 3. a real mutation of the set, on the copy: red, restored green, and the copy byte-identical
out=$(timeout 600 bash "$harness" --only 9.8 --engine "$copy_dir" 2>&1)
status=$?
check "a real mutation exits zero" "0" "$status"
case "$out" in
  *"RED    9.8"*) echo "ok   the case noticed it";;
  *) echo "FAIL the RED line: [$out]"; fail=1;;
esac
case "$out" in
  *"restore 9.8"*"green again"*) echo "ok   and the restore went green";;
  *) echo "FAIL the restore line: [$out]"; fail=1;;
esac
if diff -r "$scratch/pristine" "$copy_dir" >/dev/null 2>&1; then
  echo "ok   the mutated copy is byte-identical to the pristine one again"
else
  echo "FAIL the copy differs from the pristine engine after the run:"
  diff -r "$scratch/pristine" "$copy_dir" | head -5
  fail=1
fi

# 4. the set is listed, so a change can see what it owes
out=$(bash "$harness" --list 2>&1)
check "the mutation set lists twelve entries" "12" "$(printf '%s\n' "$out" | grep -c .)"

if [ "$fail" -ne 0 ]; then
  echo "loop-mutations-selftest: the mutation harness does not hold its own guards"
  exit 1
fi
echo "loop-mutations-selftest: the harness reports an unapplied mutation, an unnoticed one and a real red, and it restores what it touches"
exit 0
