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
#     byte-identical to the pristine one when the harness is done;
#   * a selection by file scopes the run to the records that file owns, a selection no record owns is refused
#     with exit 2 instead of passing for having done nothing, and `--from-git` derives the set from a diff
#     (issue #235).
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
checks=0
failed_checks=0
check() { checks=$((checks + 1)); if [ "$2" = "$3" ]; then echo "ok   $1"; else echo "FAIL $1: expected [$2] got [$3]"; fail=1; failed_checks=$((failed_checks + 1)); fi; }

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

# 4. the scratch tree the backups live in: the project's own `.pi/tmp` unless `TMPDIR` moves it, and gone
# afterwards. A backup on the host's tmpfs is how a full /tmp emptied six engine files on 2026-10-21 (#234).
default_scratch=$(unset TMPDIR; timeout 300 bash "$harness" --probe no-apply --engine "$copy_dir" 2>&1 |
  sed -n 's/^loop-mutations: scratch //p' | head -1)
case "$default_scratch" in
  "$root/.pi/tmp/loop-mutations."*) echo "ok   the scratch tree is the project's, not the host's tmpfs";;
  *) echo "FAIL the scratch tree is '$default_scratch', not under $root/.pi/tmp"; fail=1;;
esac
[ ! -d "$default_scratch" ] || { echo "FAIL the scratch tree $default_scratch was not removed"; fail=1; }
moved_tmp="$scratch/moved-tmp"; mkdir -p "$moved_tmp"
moved_scratch=$(TMPDIR="$moved_tmp" timeout 300 bash "$harness" --probe no-apply --engine "$copy_dir" 2>&1 |
  sed -n 's/^loop-mutations: scratch //p' | head -1)
case "$moved_scratch" in
  "$moved_tmp/loop-mutations."*) echo "ok   and TMPDIR still moves it";;
  *) echo "FAIL TMPDIR was ignored: the scratch tree is '$moved_scratch'"; fail=1;;
esac

# 5. a backup that did not land must not be copied over the file it protects: the harness stops, and the file
# still holds what it held. The full /tmp on 2026-10-21 produced exactly this shape and emptied six engine
# files, so the guard is asserted rather than assumed (#234).
victim="$scratch/empty-backup-victim.txt"
out=$(LOOP_MUTATIONS_PROBE_VICTIM="$victim" timeout 120 bash "$harness" --probe empty-backup --engine "$copy_dir" 2>&1)
status=$?
check "a restore from an empty backup exits 2" "2" "$status"
case "$out" in
  *"refusing to restore"*) echo "ok   and it says it refused";;
  *) echo "FAIL the empty-backup guard message: [$out]"; fail=1;;
esac
case "$out" in
  *"the restore was NOT refused"*) echo "FAIL the empty backup was copied over the file it protects"; fail=1;;
  *) echo "ok   and nothing was copied over it";;
esac
check "the file the empty backup would have replaced still holds its content" \
  "the tree" "$(cat "$victim" 2>/dev/null)"

# 6. the set is listed, so a change can see what it owes — and the listing is checked against the set the
# harness *declares*, not against a number spelled here. A hard number goes stale on every change that adds
# a mutation (it was bumped twice: to twelve by the identity change, to nineteen by the loop-start change),
# and a stale guard reports the change rather than the harness. What this catches is the real fault: a
# record the parser drops from the listing.
declared=$(sed -n '/^MUTATIONS=(/,/^)/p' "$root/tools/loop-mutations.sh" | grep -c "^  '")
[ "$declared" -gt 0 ] || { echo "FAIL could not read the declared set out of $root/tools/loop-mutations.sh"; fail=1; }
out=$(bash "$harness" --list 2>&1)
check "the mutation set lists what it declares" "$declared" "$(printf '%s\n' "$out" | grep -c .)"

# 7. the scope of a run follows the files it touches. A record's third `@`-separated field is its file, so a
# selection may name files and run exactly the records those files own — the bound an iteration needs, since
# naming labels by hand is derived by grepping the set (issue #235). `prompts/worker.md` owns exactly one
# record, so the count line and the single RED are both observable.
out=$(timeout 300 bash "$harness" --files tools/loop/prompts/worker.md --list 2>&1)
check "a file set lists exactly the record its file owns" "9.11" "$(printf '%s\n' "$out" | awk -F'\t' '{print $1}')"
out=$(timeout 600 bash "$harness" --files tools/loop/prompts/worker.md --engine "$copy_dir" 2>&1)
status=$?
check "a file set runs the record its file owns" "0" "$status"
case "$out" in
  *"1 of $declared records selected"*) echo "ok   and it says how much of the declared set it ran";;
  *) echo "FAIL the selection line: [$out]"; fail=1;;
esac
check "exactly one record ran" "1" "$(printf '%s\n' "$out" | grep -c '^RED ')"
case "$out" in
  *"RED    9.11"*) echo "ok   and it is the record that file owns";;
  *) echo "FAIL the RED line of the selected record: [$out]"; fail=1;;
esac
diff -r "$scratch/pristine" "$copy_dir" >/dev/null 2>&1 ||
  { echo "FAIL the copy was not restored after the scoped run"; fail=1; }

# 8. a selection no record owns names nothing to do: it must be refused, not reported as a pass, because a
# harness that passes by doing nothing is the silent lie the whole runner exists to prevent.
out=$(timeout 300 bash "$harness" --files tools/loop/prompts/no-such-file.md --engine "$copy_dir" 2>&1)
status=$?
check "a file set no record owns exits 2" "2" "$status"
case "$out" in
  *"names no record of"*) echo "ok   and it says the selection selected nothing";;
  *) echo "FAIL the empty-selection guard message: [$out]"; fail=1;;
esac
case "$out" in
  *"RED    9.11"*|*"restore 9.11"*) echo "FAIL a record ran although the file set names none"; fail=1;;
  *) echo "ok   and no record ran";;
esac

# 9. `--from-git <base>` takes the set from the diff, which is what the caller actually changed — the
# committed range plus the working tree, the same two sources the loop's gate reads. A synthetic repository
# keeps the measurement independent of this checkout's state.
diff_repo="$scratch/diff-repo"
mkdir -p "$diff_repo/tools/loop/prompts" || { echo "FAIL cannot create $diff_repo"; fail=1; }
cp "$root/tools/loop/prompts/worker.md" "$diff_repo/tools/loop/prompts/worker.md"
git -C "$diff_repo" init -q >/dev/null 2>&1
git -C "$diff_repo" add -A >/dev/null 2>&1
git -C "$diff_repo" -c user.name=selftest -c user.email=selftest@example.invalid commit -qm base >/dev/null 2>&1
printf '\n<!-- the working tree touched this file -->\n' >> "$diff_repo/tools/loop/prompts/worker.md"
out=$(cd "$diff_repo" && timeout 300 bash "$harness" --from-git HEAD --list 2>&1)
check "a git diff names the record its file owns" "9.11" "$(printf '%s\n' "$out" | awk -F'\t' '{print $1}')"
out=$(cd "$diff_repo" && timeout 300 bash "$harness" --from-git HEAD --list 2>&1 | grep -c .)
check "and nothing else" "1" "$out"

if [ "$fail" -ne 0 ]; then
  echo "loop-mutations-selftest: the mutation harness does not hold its own guards — $failed_checks of $checks cases failed"
  exit 1
fi
echo "loop-mutations-selftest: all $checks cases passed (the harness reports an unapplied mutation, an unnoticed one and a real red, it restores what it touches, it backs up under the project, it refuses a backup that did not land, and it scopes a run to the files a change touched)"
exit 0
