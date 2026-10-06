#!/usr/bin/env bash
# Self-test for tools/declared-cases.sh — change `speed-up-test-iteration`, spec `build-test-gate`
# ("Iteration pre-gate runs the change's declared cases first").
#
# Runs against a synthetic fixture tree, so it needs no device, no emulator and no git — the
# `tools/gate-timings-selftest.sh` precedent. Usage: bash tools/declared-cases-selftest.sh
#
# Exit codes: 0 = every case passed; 1 = at least one case failed.
set -uo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
TOOL="$HERE/declared-cases.sh"
[ -f "$TOOL" ] || { echo "selftest: no tools/declared-cases.sh next to $0" >&2; exit 1; }

FIX="$(mktemp -d)" || exit 1
trap 'rm -rf "$FIX"' EXIT

# ── fixture tree ──────────────────────────────────────────────────────────────────────────────
mkdir -p "$FIX/openspec/changes/demo-change" \
         "$FIX/openspec/changes/empty-change" \
         "$FIX/app/src/test/java/com/example" \
         "$FIX/auto/src/test/java/com/auto" \
         "$FIX/app/src/main/java/com/example" \
         "$FIX/results/app"

cat > "$FIX/openspec/changes/demo-change/tasks.md" <<'TASKS'
# Tasks
- [ ] 1.1 Verify with FooBarTest and the NotAClassTest token that names nothing.
TASKS

# A change that declares no test case at all — the empty-selection case needs one, because an empty
# diff still selects whatever the change's artifacts name.
cat > "$FIX/openspec/changes/empty-change/tasks.md" <<'TASKS'
# Tasks
- [ ] 1.1 Verify that no test class is named or touched here.
TASKS

cat > "$FIX/app/src/test/java/com/example/FooBarTest.kt" <<'KT'
package com.example
class FooBarTest
KT
cat > "$FIX/app/src/test/java/com/example/BazTest.kt" <<'KT'
package com.example
// drives Widget on the phone
class BazTest
KT
cat > "$FIX/app/src/test/java/com/example/QuuxTest.kt" <<'KT'
package com.example
class QuuxTest
KT
cat > "$FIX/app/src/test/java/com/example/UnrelatedTest.kt" <<'KT'
package com.example
class UnrelatedTest
KT
cat > "$FIX/auto/src/test/java/com/auto/AutoRenderTest.kt" <<'KT'
package com.auto
// the car screen renders Widget too
class AutoRenderTest
KT
cat > "$FIX/app/src/main/java/com/example/Widget.kt" <<'KT'
package com.example
class Widget
KT
cat > "$FIX/app/src/main/java/com/example/Other.kt" <<'KT'
package com.example
class Other
KT

cat > "$FIX/diff.txt" <<'DIFF'
app/src/main/java/com/example/Widget.kt
app/src/test/java/com/example/QuuxTest.kt
DIFF
: > "$FIX/diff-empty.txt"

cat > "$FIX/results/app/TEST-com.example.FooBarTest.xml" <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.example.FooBarTest" tests="1" skipped="0" failures="0" errors="0" timestamp="2026-10-06T05:00:00.000Z" hostname="host" time="2.5">
  <testcase name="aCase" classname="com.example.FooBarTest" time="2.4"/>
</testsuite>
XML

# ── harness ───────────────────────────────────────────────────────────────────────────────────
PASS=0
FAIL=0
CASE=""

case_start() { CASE="$1"; }

ok()   { PASS=$((PASS + 1)); printf '  PASS  %s\n' "$CASE"; }
bad()  { FAIL=$((FAIL + 1)); printf '  FAIL  %s — %s\n' "$CASE" "$1"; }

# run_tool <extra args…> — fixture env, output in $OUT, exit status in $STATUS
run_tool() {
  OUT="$(DECLARED_CASES_ROOT="$FIX" \
         DECLARED_CASES_CHANGES_DIR="$FIX/openspec/changes" \
         DECLARED_CASES_TEST_ROOTS="$FIX/app/src/test/java $FIX/auto/src/test/java" \
         DECLARED_CASES_RESULT_DIRS="$FIX/results/app" \
         bash "$TOOL" demo-change "$@" 2>&1)"
  STATUS=$?
}

expect_selected() { # <fqn>
  printf '%s' "$OUT" | grep -q " $1\$" || bad "expected $1 in the selection"
}
expect_absent() { # <fqn>
  printf '%s' "$OUT" | grep -q " $1\$" && bad "did not expect $1 in the selection"
}

echo "declared-cases selftest — fixture $FIX"

# 1 · the declared rule selects the class the artifacts name
case_start "declared rule selects the artifacts-named class"
run_tool --diff "$FIX/diff.txt" --rules declared
before=$FAIL
expect_selected com.example.FooBarTest
[ "$FAIL" -eq "$before" ] && ok

# 2 · the mention rule selects the test that names the changed type, in every module
case_start "mention rule selects the mentioning tests (app and auto modules)"
run_tool --diff "$FIX/diff.txt" --rules mention
before=$FAIL
expect_selected com.example.BazTest
expect_selected com.auto.AutoRenderTest
printf '%s' "$OUT" | grep -q 'mention  *auto ' || bad "auto module not reported for AutoRenderTest"
[ "$FAIL" -eq "$before" ] && ok

# 3 · the touched rule selects the test file the diff touches
case_start "touched rule selects the test file the diff touches"
run_tool --diff "$FIX/diff.txt" --rules touched
before=$FAIL
expect_selected com.example.QuuxTest
[ "$FAIL" -eq "$before" ] && ok

# 4 · a class no rule reaches is never selected
case_start "unrelated class is never selected"
run_tool --diff "$FIX/diff.txt"
before=$FAIL
expect_absent com.example.UnrelatedTest
[ "$FAIL" -eq "$before" ] && ok

# 5 · a token that names no existing test class selects nothing
case_start "a declared token with no test class selects nothing"
run_tool --diff "$FIX/diff.txt"
before=$FAIL
expect_absent com.example.NotAClassTest
[ "$FAIL" -eq "$before" ] && ok

# 6 · the per-rule counts are reported before the union
case_start "per-rule counts are printed"
run_tool --diff "$FIX/diff.txt"
before=$FAIL
printf '%s' "$OUT" | grep -q 'declared 1 · touched 1 · mention 2' || bad "counts line missing or wrong"
[ "$FAIL" -eq "$before" ] && ok

# 7 · --rules narrows the union
case_start "--rules declared,touched excludes the mention-only classes"
run_tool --diff "$FIX/diff.txt" --rules declared,touched
before=$FAIL
expect_absent com.example.BazTest
expect_selected com.example.FooBarTest
expect_selected com.example.QuuxTest
[ "$FAIL" -eq "$before" ] && ok

# 8 · an empty selection explains itself and exits 0 (a change that declares nothing + an empty diff)
case_start "empty selection explains and exits 0"
OUT="$(DECLARED_CASES_ROOT="$FIX" \
       DECLARED_CASES_CHANGES_DIR="$FIX/openspec/changes" \
       DECLARED_CASES_TEST_ROOTS="$FIX/app/src/test/java $FIX/auto/src/test/java" \
       DECLARED_CASES_RESULT_DIRS="$FIX/results/app" \
       bash "$TOOL" empty-change --diff "$FIX/diff-empty.txt" 2>&1)"
STATUS=$?
if [ "$STATUS" -ne 0 ]; then bad "exit status $STATUS, expected 0"
elif ! printf '%s' "$OUT" | grep -q 'Nothing selected'; then bad "no explanation printed"
elif ! printf '%s' "$OUT" | grep -q 'still owed'; then bad "completion rule not restated"
else ok; fi

# 9 · the measured class time comes from the result XML
case_start "measured class time is read from the result XML"
run_tool --diff "$FIX/diff.txt" --rules declared
before=$FAIL
printf '%s' "$OUT" | grep -Eq '2\.5 +com\.example\.FooBarTest' || bad "expected the XML time 2.5 in the row"
printf '%s' "$OUT" | grep -q 'measured class time 2.5s' || bad "expected the measured total 2.5s"
[ "$FAIL" -eq "$before" ] && ok

# 10 · the printed command names the right task per module
case_start "--command prints one Gradle task per module with --tests filters"
run_tool --diff "$FIX/diff.txt" --command
before=$FAIL
printf '%s' "$OUT" | grep -q './gradlew :app:testMobileDebugUnitTest' || bad "no :app task command"
printf '%s' "$OUT" | grep -q './gradlew :auto:testDebugUnitTest' || bad "no :auto task command"
printf '%s' "$OUT" | grep -q -- '--tests "com.example.FooBarTest"' || bad "no --tests filter for the declared class"
printf '%s' "$OUT" | grep -q -- '--tests "com.auto.AutoRenderTest"' || bad "no --tests filter for the auto class"
[ "$FAIL" -eq "$before" ] && ok

# 11 · a tree with no test root is refused rather than reported as an empty selection
case_start "missing test root exits 3 instead of reporting an empty selection"
OUT="$(DECLARED_CASES_ROOT="$FIX" \
       DECLARED_CASES_CHANGES_DIR="$FIX/openspec/changes" \
       DECLARED_CASES_TEST_ROOTS="$FIX/does-not-exist" \
       bash "$TOOL" demo-change --diff "$FIX/diff.txt" 2>&1)"
STATUS=$?
if [ "$STATUS" -eq 3 ]; then ok; else bad "exit status $STATUS, expected 3"; fi

# 12 · the documented usage is reachable
case_start "--help prints usage and exits 0"
OUT="$(bash "$TOOL" --help 2>&1)"; STATUS=$?
if [ "$STATUS" -eq 0 ] && printf '%s' "$OUT" | grep -q 'declared-cases.sh <change-name>'; then ok
else bad "help did not print the usage line (status $STATUS)"; fi

printf '\n%s passed, %s failed\n' "$PASS" "$FAIL"
[ "$FAIL" -eq 0 ] || exit 1
