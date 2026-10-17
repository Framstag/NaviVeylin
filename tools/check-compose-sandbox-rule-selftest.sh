#!/usr/bin/env bash
# Self-test for check-compose-sandbox-rule.sh: the checker must accept a Compose test class that carries the
# rule, and reject one that does not (or that carries it unordered). Runs against a temporary tree, so it
# needs no device, no build and no fixtures in the repository.
#
# Usage: check-compose-sandbox-rule-selftest.sh
set -uo pipefail

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
checker="$repo/tools/check-compose-sandbox-rule.sh"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

probe="$tmp/app/src/test/java/com/naviveylin/probe"
mkdir -p "$probe"
subject="$probe/SubjectComposeTest.kt"

write_good() {
  cat >"$subject" <<'KOTLIN'
package com.naviveylin.probe

import androidx.compose.ui.test.junit4.createComposeRule
import com.naviveylin.test.ComposeSandboxRule
import org.junit.Rule

class SubjectComposeTest {
    @get:Rule(order = 0)
    val composeSandbox = ComposeSandboxRule()

    @get:Rule
    val composeRule = createComposeRule()
}
KOTLIN
}

write_without_rule() {
  cat >"$subject" <<'KOTLIN'
package com.naviveylin.probe

import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule

class SubjectComposeTest {
    @get:Rule
    val composeRule = createComposeRule()
}
KOTLIN
}

write_unordered() {
  cat >"$subject" <<'KOTLIN'
package com.naviveylin.probe

import androidx.compose.ui.test.junit4.createComposeRule
import com.naviveylin.test.ComposeSandboxRule
import org.junit.Rule

class SubjectComposeTest {
    @get:Rule
    val composeSandbox = ComposeSandboxRule()

    @get:Rule
    val composeRule = createComposeRule()
}
KOTLIN
}

fail=0
expect() { # expect <0|1> <label>
  local want="$1" label="$2" got=0
  bash "$checker" --root "$tmp" >/dev/null 2>&1 || got=1
  if [ "$got" != "$want" ]; then
    echo "FAIL: $label (expected exit $want, got $got)"
    fail=1
  else
    echo "ok: $label"
  fi
}

write_good
expect 0 "a Compose class carrying the ordered sandbox rule passes"

write_without_rule
expect 1 "a Compose class without the rule fails"

write_unordered
expect 1 "the rule without @get:Rule(order = 0) fails"

rm "$subject"
expect 0 "a tree without a Compose test class passes"

exit "$fail"
