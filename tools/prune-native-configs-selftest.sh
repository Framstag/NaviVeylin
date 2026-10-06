#!/usr/bin/env bash
# Device-free self-test for tools/prune-native-configs.sh — change `speed-up-build-test-gate`,
# task 6.2. Builds its own fixture tree under a temp dir (never touches app/.cxx) and checks the
# bounded-keep rule, the dry run, and both refusal paths.
#
#   bash tools/prune-native-configs-selftest.sh
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PRUNE="$HERE/prune-native-configs.sh"

fails=0
ok() { echo "ok:   $*"; }
bad() { echo "FAIL: $*"; fails=$((fails + 1)); }

# Four configurations with mtimes oldest → newest: aaaa, bbbb, cccc, dddd.
make_fixture() {
    local d="$1"
    rm -rf "$d"
    mkdir -p "$d/Debug"
    local h
    for h in aaaa bbbb cccc dddd; do
        mkdir -p "$d/Debug/$h"
        # A real configuration tree holds ABI directories; the mis-scope guard keys on that.
        mkdir -p "$d/Debug/$h/arm64-v8a"
        head -c 2048 /dev/zero > "$d/Debug/$h/blob"
        head -c 512 /dev/zero > "$d/Debug/$h/arm64-v8a/.ninja_log"
    done
    touch -t 202601010101 "$d/Debug/aaaa"
    touch -t 202601020101 "$d/Debug/bbbb"
    touch -t 202601030101 "$d/Debug/cccc"
    touch -t 202601040101 "$d/Debug/dddd"
}

remaining() { find "$1/Debug" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' | sort | tr '\n' ' '; }

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

echo "--- keeps the newest two, deletes the rest ---"
make_fixture "$tmp/f1"
if bash "$PRUNE" --force "$tmp/f1" >"$tmp/o1" 2>&1; then ok "exits 0 on a fixture tree"; else bad "exited non-zero: $(cat "$tmp/o1")"; fi
r="$(remaining "$tmp/f1")"
[ "$r" = "cccc dddd " ] && ok "kept exactly the newest two (cccc dddd)" || bad "kept '$r', expected 'cccc dddd '"
grep -q 'kept 2, deleted 2' "$tmp/o1" && ok "summary reports kept/deleted counts" || bad "summary wrong: $(tail -1 "$tmp/o1")"

echo "--- dry run ---"
make_fixture "$tmp/f2"
bash "$PRUNE" --force --dry-run "$tmp/f2" >"$tmp/o2" 2>&1
r="$(remaining "$tmp/f2")"
[ "$r" = "aaaa bbbb cccc dddd " ] && ok "dry run deletes nothing" || bad "dry run changed the tree: $r"
grep -q 'would delete' "$tmp/o2" && ok "dry run reports what it would delete" || bad "dry-run output lacks 'would delete'"

echo "--- --keep 1 keeps only the newest ---"
make_fixture "$tmp/f3"
bash "$PRUNE" --force --keep 1 "$tmp/f3" >"$tmp/o3" 2>&1
r="$(remaining "$tmp/f3")"
[ "$r" = "dddd " ] && ok "--keep 1 keeps exactly the newest" || bad "--keep 1 kept '$r'"

echo "--- refuses while a build is active ---"
make_fixture "$tmp/f4"
env NAVIVEYLIN_PRUNE_ASSUME_BUSY=1 bash "$PRUNE" "$tmp/f4" >"$tmp/o4" 2>&1
rc=$?
[ "$rc" -eq 2 ] && ok "busy refusal exits 2" || bad "busy refusal exit was $rc"
r="$(remaining "$tmp/f4")"
[ "$r" = "aaaa bbbb cccc dddd " ] && ok "busy refusal deletes nothing" || bad "busy refusal changed the tree: $r"
grep -q 'Gradle build is active' "$tmp/o4" && ok "busy refusal names the reason" || bad "busy refusal message: $(cat "$tmp/o4")"

echo "--- missing root ---"
bash "$PRUNE" --force "$tmp/does-not-exist" >"$tmp/o5" 2>&1
rc=$?
[ "$rc" -eq 1 ] && ok "missing root exits 1" || bad "missing root exit was $rc"
grep -q 'no native configuration root' "$tmp/o5" && ok "missing root names the reason" || bad "missing-root message: $(cat "$tmp/o5")"

echo "--- refuses a mis-scoped root (a build-type dir) ---"
make_fixture "$tmp/f5"
bash "$PRUNE" --force "$tmp/f5/Debug" >"$tmp/o6" 2>&1
rc=$?
[ "$rc" -eq 1 ] && ok "mis-scoped root exits 1" || bad "mis-scoped root exit was $rc"
r="$(remaining "$tmp/f5")"
[ "$r" = "aaaa bbbb cccc dddd " ] && ok "mis-scoped root deletes nothing" || bad "mis-scoped root changed the tree: $r"
grep -q 'looks like a build-type directory' "$tmp/o6" && ok "mis-scoped refusal names the reason" || bad "mis-scoped message: $(cat "$tmp/o6")"

if [ "$fails" -gt 0 ]; then
    echo "prune-native-configs-selftest: $fails check(s) failed"
    exit 1
fi
echo "prune-native-configs-selftest: all checks passed"
