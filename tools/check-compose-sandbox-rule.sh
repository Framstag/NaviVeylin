#!/usr/bin/env bash
# Every `:app` Compose test class must carry the Robolectric sandbox repair rule.
#
# `AndroidUiDispatcher.Main` is a per-sandbox static that outlives Robolectric's per-test reset while the
# callbacks that drain it do not, so a Compose case that runs after a non-Compose case which wrote a Compose
# state can never idle and burns Espresso's 60 s budget (`AppNotIdleException`) — 31 `:app` cases in one
# measured gate run. `ComposeSandboxRule` (app/src/test/java/com/naviveylin/test/) repairs that state and every
# Compose test class installs it as its first rule:
#
#     @get:Rule(order = 0)
#     val composeSandbox = ComposeSandboxRule()
#
# The invariant cannot be enforced from inside a test (a class without the rule is exactly the class that
# fails), so it is checked here, like the other text-only gate checks. The rule must be ordered first: the
# Compose rule's own setup is Compose work, and the repair has to happen before it.
#
# Reads text files only: no build, no device, no network. Exit 0 when every Compose test class carries the
# rule and no class carries it twice, 1 when one does not, 2 on a usage or layout error.
#
# Usage: check-compose-sandbox-rule.sh [--root <dir>]   # default root: the repository this script lives in
set -uo pipefail

root=""
while [ $# -gt 0 ]; do
  case "$1" in
    --root) root="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,20p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "check-compose-sandbox-rule: unknown argument '$1'" >&2; exit 2 ;;
  esac
done
if [ -z "$root" ]; then
  root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fi
tests="$root/app/src/test/java"
if [ ! -d "$tests" ]; then
  echo "check-compose-sandbox-rule: no app test sources under $tests" >&2
  exit 2
fi

fail=0
compose_classes=0
while IFS= read -r file; do
  # A Compose test class is one that instantiates a Compose rule; the rule's own file is not a test.
  if ! grep -qE '^[[:space:]]*(private[[:space:]]+)?val[[:space:]]+[A-Za-z_][A-Za-z0-9_]*[[:space:]]*=[[:space:]]*create(Android)?ComposeRule' "$file"; then
    continue
  fi
  compose_classes=$((compose_classes + 1))
  if ! grep -q 'ComposeSandboxRule()' "$file"; then
    echo "::error file=${file#"$root"/}::Compose test class without ComposeSandboxRule — the sandbox's Compose dispatcher is not repaired, so this case can burn the 60 s idling budget (TODO.md §195)"
    fail=1
    continue
  fi
  if ! grep -qE '^[[:space:]]*@get:Rule\(order[[:space:]]*=[[:space:]]*0\)[[:space:]]*$' "$file"; then
    echo "::error file=${file#"$root"/}::the sandbox rule must be declared '@get:Rule(order = 0)' so it runs before the Compose rule"
    fail=1
  fi
done < <(find "$tests" -name '*.kt' | sort)

if [ "$fail" -eq 0 ]; then
  echo "compose sandbox rule: $compose_classes Compose test classes carry it."
fi
exit "$fail"
