#!/usr/bin/env bash
# The mutation harness's self-test — it proves the *harness* (not a change) can go red, on a **throwaway copy**
# of the engine, so it is safe to run anywhere and costs no mutation of the working tree.
#
# Five things are asserted, and each is the harness's own failure mode (`tools/loop/tests/loop-mutations.sh`):
#
#   * a mutation whose pattern no longer matches the file is `MUTATION-DID-NOT-APPLY`, exit 1 — never a
#     silent pass;
#   * a mutation that lands but that no case notices is `the case did not notice the mutation`, exit 1;
#   * a mutation that lands and whose case notices it is `RED`, the restore is `green again`, and the copy is
#     byte-identical to the pristine one when the harness is done;
#   * a record that is `RED` quotes the assertion the case failed on, and the line of the case's own log it was read
#     from, not the fixture line the case printed first (issue #964);
#   * a **file set** bounds the run: it selects exactly the records whose file it names, it says how many of
#     the declared set it selected, and a set that names no record refuses with exit 2 rather than passing by
#     running nothing (issue #235: without the bound a change pays all 81 records × two case runs per pass).
#
# Every check ends in one line and the suite ends in a tally the loop's gate parses (`all N cases passed` /
# `M of N cases failed`), so a change that maps this file in `tools/loop/loop.conf` reports numbers.
#
# Usage: bash tools/loop/tests/loop-mutations-selftest.sh
# Exit:  0 all of the above hold; 1 one of them does not; 2 the environment is unusable.
set -uo pipefail

root=$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel 2>/dev/null) || root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)
harness="$root/tools/loop/tests/loop-mutations.sh"

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
# `…/tools/loop` with `…/tools/issues` beside it (`lib.sh` resolves its dependency as a sibling) and, beside that,
# the gate-log reader a red tally is attributed through (`tools/gate-attribute.sh`, issue #853). Both dependencies
# are symlinks because no mutation touches them.
pristine="$scratch/pristine"
cp -a "$root/tools/loop" "$pristine"
mkdir -p "$scratch/engine/tools"
cp -a "$root/tools/loop" "$scratch/engine/tools/loop"
ln -s "$root/tools/issues" "$scratch/engine/tools/issues"
ln -s "$root/tools/gate-attribute.sh" "$scratch/engine/tools/gate-attribute.sh"
copy_dir="$scratch/engine/tools/loop"

failures=0
cases=0
ok() { cases=$((cases + 1)); printf 'ok   %s\n' "$1"; }
bad() { cases=$((cases + 1)); failures=$((failures + 1)); printf 'FAIL %s\n' "$1"; }
check() { if [ "$2" = "$3" ]; then ok "$1"; else bad "$1: expected [$2] got [$3]"; fi; }
contains() { # contains <what> <needle> <haystack>
  case "$3" in
    *"$2"*) ok "$1" ;;
    *) bad "$1: expected [$2] in [$3]" ;;
  esac
}
copy_is_pristine() { # copy_is_pristine <what>
  if diff -r "$scratch/pristine" "$copy_dir" >/dev/null 2>&1; then
    ok "$1"
  else
    bad "$1 — the copy differs:"
    diff -r "$scratch/pristine" "$copy_dir" | head -5
  fi
}

# 1. a mutation whose pattern no longer matches must be reported, not swallowed
out=$(timeout 300 bash "$harness" --probe no-apply --engine "$copy_dir" 2>&1)
status=$?
check "a non-matching mutation exits non-zero" "1" "$status"
contains "and it is named as such" "MUTATION-DID-NOT-APPLY" "$out"
copy_is_pristine "and the copy was restored after the non-matching probe"

# 2. a mutation that lands but that no case notices must be reported, not accepted
out=$(timeout 600 bash "$harness" --probe stays-green --engine "$copy_dir" 2>&1)
status=$?
check "an unnoticed mutation exits non-zero" "1" "$status"
contains "and it says the case did not notice" "did not notice the mutation" "$out"
copy_is_pristine "and the copy was restored after the stays-green probe"

# 3. a real mutation of the set, on the copy: red, restored green, and the copy byte-identical
out=$(timeout 600 bash "$harness" --only 9.8 --engine "$copy_dir" 2>&1)
status=$?
check "a real mutation exits zero" "0" "$status"
contains "the case noticed it" "RED    9.8" "$out"
case "$out" in
  *"restore 9.8"*"green again"*) ok "and the restore went green" ;;
  *) bad "the restore line: [$out]" ;;
esac
copy_is_pristine "the mutated copy is byte-identical to the pristine one again"

# 3b. the record's reason is the assertion the case failed on (issue #964). A `RED` line that quotes the first line a
# case printed quotes its fixture: `29.8`'s case runs `git init` and fails on a later refusal, and the record archived
# with item #962 quoted `Bereite Arbeitsverzeichnis vor (neuer Branch 'loop-test')` instead of the refusal the case
# failed on (`openspec/changes/archive/2026-11-18-a-path-no-child-wrote-is-not-a-write/evidence/mutation-29.8-red.txt`).
# The reason also names the line of the case's log it was read from, and that line is not the log's first.
out=$(timeout 600 bash "$harness" --only 29.8 --engine "$copy_dir" 2>&1)
status=$?
check "a record whose case prints before it fails exits zero" "0" "$status"
if printf '%s\n' "$out" | grep -qE '^ *[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9:]+Z — case log line ([2-9]|[1-9][0-9]+): the merge was refused on a build directory'; then
  ok "a red record quotes the assertion the case failed on"
else
  bad "a red record quotes the assertion the case failed on — the reason line: [$out]"
fi
copy_is_pristine "and the copy is byte-identical again after the record whose reason is read"

# 4. the scratch tree the backups live in: the project's own `.pi/tmp` unless `TMPDIR` moves it, and gone
# afterwards. A backup on the host's tmpfs is how a full /tmp emptied six engine files on 2026-10-21 (#234).
default_scratch=$(unset TMPDIR; timeout 300 bash "$harness" --probe no-apply --engine "$copy_dir" 2>&1 |
  sed -n 's/^loop-mutations: scratch //p' | head -1)
case "$default_scratch" in
  "$root/.pi/tmp/loop-mutations."*) ok "the scratch tree is the project's, not the host's tmpfs" ;;
  *) bad "the scratch tree is '$default_scratch', not under $root/.pi/tmp" ;;
esac
if [ -d "$default_scratch" ]; then bad "the scratch tree $default_scratch was not removed"; else ok "and it is removed afterwards"; fi
moved_tmp="$scratch/moved-tmp"; mkdir -p "$moved_tmp"
moved_scratch=$(TMPDIR="$moved_tmp" timeout 300 bash "$harness" --probe no-apply --engine "$copy_dir" 2>&1 |
  sed -n 's/^loop-mutations: scratch //p' | head -1)
case "$moved_scratch" in
  "$moved_tmp/loop-mutations."*) ok "and TMPDIR still moves it" ;;
  *) bad "TMPDIR was ignored: the scratch tree is '$moved_scratch'" ;;
esac

# 5. a backup that did not land must not be copied over the file it protects: the harness stops, and the file
# still holds what it held. The full /tmp on 2026-10-21 produced exactly this shape and emptied six engine
# files, so the guard is asserted rather than assumed (#234).
victim="$scratch/empty-backup-victim.txt"
out=$(LOOP_MUTATIONS_PROBE_VICTIM="$victim" timeout 120 bash "$harness" --probe empty-backup --engine "$copy_dir" 2>&1)
status=$?
check "a restore from an empty backup exits 2" "2" "$status"
contains "and it says it refused" "refusing to restore" "$out"
if printf '%s' "$out" | grep -q "the restore was NOT refused"; then
  bad "the empty backup was copied over the file it protects"
else
  ok "and nothing was copied over it"
fi
check "the file the empty backup would have replaced still holds its content" "the tree" "$(cat "$victim" 2>/dev/null)"

# 6. the set is listed, so a change can see what it owes — and the listing is checked against the set the
# harness *declares*, not against a number spelled here. A hard number goes stale on every change that adds
# a mutation (it was bumped twice: to twelve by the identity change, to nineteen by the loop-start change),
# and a stale guard reports the change rather than the harness. What this catches is the real fault: a
# record the parser drops from the listing.
declared=$(sed -n '/^MUTATIONS=(/,/^)/p' "$root/tools/loop/tests/loop-mutations.sh" | grep -c "^  '")
[ "$declared" -gt 0 ] || { echo "FAIL could not read the declared set out of $root/tools/loop/tests/loop-mutations.sh"; failures=$((failures + 1)); }
out=$(bash "$harness" --list 2>&1)
check "the mutation set lists what it declares" "$declared" "$(printf '%s\n' "$out" | grep -c .)"

# 7. a file set is the bound #235 asks for: the records whose file it names, the count against the declared
# set, those records' REDs and a clean restore. The file is the one the *declared set* gives the fewest records to
# among the files that own at least two — a set of one is no set — because a file that owns most of the declaration
# spends this leg's bound before the selection finishes: `tools/loop/state.sh`'s 39 of the 401 declared records ran the
# leg past `timeout 900` and reddened the step on every delta, for a change that touched nothing it reads (issue #934).
# `tools/loop/evidence-check.sh` and `tools/loop/phase.sh` own 2 each, and the tie goes to the path that sorts first;
# `mutation#theFileSetLegScopesByTheCheapestFile` holds this pick to the declaration, so a record that moves off it names
# the file to point the leg at instead of leaving the constant to rot.
scoped_file="tools/loop/evidence-check.sh"
owned=$(sed -n '/^MUTATIONS=(/,/^)/p' "$root/tools/loop/tests/loop-mutations.sh" |
  awk -F'@' -v f="$scoped_file" '$3 == f { print $1 }' | sed "s/^[[:space:]]*'//; s/'$//" | sort)
owned_count=$(printf '%s\n' "$owned" | grep -c .)
check "the file this check scopes by is owned by at least one declared record" "yes" "$([ "$owned_count" -gt 0 ] && echo yes || echo no)"
selected=$(timeout 300 bash "$harness" --files "$scoped_file" --list 2>&1)
check "a file set lists exactly the records its file owns" \
  "$(printf '%s' "$owned" | tr '\n' ' ' | sed 's/ *$//')" \
  "$(printf '%s\n' "$selected" | awk -F'\t' 'NF > 1 { print $1 }' | sort | tr '\n' ' ' | sed 's/ *$//')"
contains "and it says how many of the declared set the file set selects" \
  "selected $owned_count of $declared records" "$selected"

out=$(timeout 900 bash "$harness" --files "$scoped_file" --engine "$copy_dir" 2>&1)
status=$?
check "a file set runs the records its file owns" "0" "$status"
contains "the selection line names the count and the declared set" "selected $owned_count of $declared records" "$out"
check "exactly the owned records ran" "$owned_count" "$(printf '%s\n' "$out" | grep -c '^RED')"
check "no record outside the file set ran" "0" \
  "$(printf '%s\n' "$out" | awk '/^RED/ { print $2 }' | grep -vc -F -x -f <(printf '%s\n' "$owned") || true)"
copy_is_pristine "and the copy is byte-identical to the pristine one again"

# 8. a file set that names no record must refuse, not pass by doing nothing — and it refuses before a scratch
# directory or a mutation exists.
out=$(timeout 300 bash "$harness" --files tools/loop/nothing-owns-this-file.sh --engine "$copy_dir" 2>&1)
status=$?
check "a file set no record owns exits 2" "2" "$status"
contains "the empty-selection guard message" "names no record of the $declared declared" "$out"
check "and no record ran" "0" "$(printf '%s\n' "$out" | grep -c '^RED')"
copy_is_pristine "and nothing was mutated"

# 9. `--from-git <base>` derives the same set from the paths a change touched, on a synthetic repository: the
# diff's paths are what selects the records, so the harness and the gate read the change the same way.
repo="$scratch/repo"
mkdir -p "$repo/$(dirname "$scoped_file")"
printf 'the file\n' > "$repo/$scoped_file"
git -C "$repo" init -q >/dev/null 2>&1
git -C "$repo" -c user.name=selftest -c user.email=selftest@example.invalid add -A >/dev/null 2>&1
git -C "$repo" -c user.name=selftest -c user.email=selftest@example.invalid commit -qm 'the file' >/dev/null 2>&1
printf 'the file, changed\n' > "$repo/$scoped_file"
out=$( cd "$repo" && timeout 900 bash "$harness" --from-git HEAD --engine "$copy_dir" 2>&1 )
status=$?
check "a git diff names the records its file owns" "0" "$status"
check "and nothing else" "$owned_count" "$(printf '%s\n' "$out" | grep -c '^RED')"
contains "and it says the same selection the file set named" "selected $owned_count of $declared records" "$out"
copy_is_pristine "and the copy is byte-identical to the pristine one again"

# 10. the engine's second sibling. A copy that ships `tools/loop` and the issue tooling but not the gate-log reader a
# red tally is attributed through is refused, rather than running cases that could only answer `unattributable` —
# the shape the run of 2026-11-13 measured as a restore-failure on `853.2` (issue #853).
mkdir -p "$scratch/bare/tools"
cp -a "$root/tools/loop" "$scratch/bare/tools/loop"
ln -s "$root/tools/issues" "$scratch/bare/tools/issues"
out=$(timeout 120 bash "$harness" --files "$scoped_file" --engine "$scratch/bare/tools/loop" 2>&1)
status=$?
check "a copy with no sibling gate-log reader exits 2" "2" "$status"
contains "and it names the sibling it lacks" "has no sibling gate-attribute.sh" "$out"

# 11. A record's line is one its case reaches. The compile-per-suite invariant is the one record `48.2` asserts, and
# its expression quotes the *guard* the second asking case passes through — so a second, earlier read of the same
# marker (a fast path above the claiming block) shadows that guard: the mutation lands, the second asking case never
# arrives at the line, and the record prints `the case did not notice the mutation` while `--check` says it applies
# (issue #1047). One read is the whole guard, so the count is what this case asserts — over this repository's driver,
# not the copied engine, because the count is a claim about the file the record names.
marker_reads=$(sed -n '/^core_ensure() {/,/^}$/p' "$root/tools/loop/tests/loop-selftest.sh" |
  grep -c 'if \[ ! -f "$selftest_core_marker" \]; then')
check "the compile's marker is read in one place, so the line record 48.2 mutates is one a case reaches" "1" \
  "$marker_reads"

if [ "$failures" -ne 0 ]; then
  printf 'loop-mutations-selftest: %s of %s cases failed — the mutation harness does not hold its own guards\n' \
    "$failures" "$cases"
  exit 1
fi
printf 'loop-mutations-selftest: all %s cases passed — the harness reports an unapplied mutation, an unnoticed one and a real red, it quotes the assertion a red case failed on and the case-log line it read it from, it restores what it touches, it backs up under the project, it refuses a backup that did not land and it bounds a run by the files a change touched\n' \
  "$cases"
exit 0
