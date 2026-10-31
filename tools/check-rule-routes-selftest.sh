#!/usr/bin/env bash
# Self-test for tools/check-rule-routes.sh — every case runs against a throwaway fixture under a temp
# directory, so the project's own documents and map are not read and nothing in the repository is written.
#
# Cases: a map whose globs match the tree and whose rules are all reached (pass); a glob that matches no
# tracked path; a named rule no glob reaches; and `--diff` over a fixture range — the reading set follows the
# paths a change touched, and a change that names fewer sections than the map derives is reported while a
# change that names them all (or adds one) passes.
#
# Usage: check-rule-routes-selftest.sh [--case <id>]
set -uo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
check="$root/tools/check-rule-routes.sh"
case_filter=""
while [ $# -gt 0 ]; do
  case "$1" in
    --case) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { echo "check-rule-routes-selftest: $1 needs a value" >&2; exit 2; }; case_filter="${2:-}"; shift 2 ;;
    *) echo "check-rule-routes-selftest: unknown argument '$1'" >&2; exit 2 ;;
  esac
done
tmp=$(mktemp -d "${TMPDIR:-/tmp}/check-rule-routes-selftest.XXXXXX") || exit 2
trap 'rm -rf "$tmp"' EXIT

[ -x "$check" ] || { echo "check-rule-routes-selftest: $check is not executable" >&2; exit 2; }

pass=0
failures=0

mk_fixture() { # $1 = directory
  mkdir -p "$1/tools" "$1/guidelines" "$1/src"
  printf '{"profile":1,"docRoutes":"tools/doc-routes.json"}\n' > "$1/tools/tooling.json"
  printf '# Doc Specification\n\n## 1. A rule home\n\n- **Rule: nothing yet.** The refusal path.\n' > "$1/guidelines/Doc.md"
  printf 'fun main() {}\n' > "$1/src/Thing.kt"
}

write_map() { # $1 = directory, $2 = the routes array body
  printf '{ "routes": [ %s ] }\n' "$2" > "$1/tools/doc-routes.json"
}

expect() { # $1 case id, $2 expected exit, $3 expected substring, $4 description, $5... command
  local id="$1" want="$2" sub="$3" desc="$4" out got ok=1
  shift 4
  [ -z "$case_filter" ] || [ "$case_filter" = "$id" ] || return 0
  out=$("$@" 2>&1); got=$?
  [ "$got" = "$want" ] || ok=0
  if [ -n "$sub" ] && ! printf '%s' "$out" | grep -qF -- "$sub"; then ok=0; fi
  if [ "$ok" = 1 ]; then
    echo "PASS  $id — $desc (exit $got)"
    pass=$((pass + 1))
  else
    echo "FAIL  $id — $desc: expected exit $want containing '$sub', got exit $got:"
    printf '        %s\n' "$out"
    failures=$((failures + 1))
  fi
}

# ── the map resolves and every rule is reached ─────────────────────────────────────────────────────────────
mk_fixture "$tmp/ok"
write_map "$tmp/ok" '{ "glob": "src/**", "sections": ["Doc.md §1"], "rules": ["Doc.md §1 \"nothing yet.\""] }'
expect routes#theMapResolvesAndEveryRuleIsReached 0 "all 1 named rule(s) are reached" \
  "a map whose glob matches the tree and whose rule is reached passes" \
  bash "$check" --root "$tmp/ok"

# ── a glob that matches no tracked path ────────────────────────────────────────────────────────────────────
mk_fixture "$tmp/glob-dead"
write_map "$tmp/glob-dead" '{ "glob": "src/**", "sections": ["Doc.md §1"], "rules": ["Doc.md §1 \"nothing yet.\""] }, { "glob": "no/such/path/**", "sections": [], "rules": [] }'
expect routes#aGlobMatchingNoTrackedPathIsReported 1 "matches no tracked path" \
  "a glob under which the tree holds no file is reported" \
  bash "$check" --root "$tmp/glob-dead"

# ── a named rule no glob reaches ───────────────────────────────────────────────────────────────────────────
mk_fixture "$tmp/rule-orphan"
printf '# Doc Specification\n\n## 1. A rule home\n\n- **Rule: a lonely rule.** The refusal path.\n' > "$tmp/rule-orphan/guidelines/Doc.md"
write_map "$tmp/rule-orphan" '{ "glob": "src/**", "sections": ["Doc.md §1"], "rules": [] }'
expect routes#aRuleNoGlobReachesIsReported 1 'the rule "a lonely rule."' \
  "a named rule no glob reaches is reported with its name and document" \
  bash "$check" --root "$tmp/rule-orphan"

# ── the reading set follows the diff ───────────────────────────────────────────────────────────────────────
mk_git_fixture() { # $1 directory
  mk_fixture "$1"
  write_map "$1" '{ "glob": "src/**", "sections": ["Doc.md §1"], "rules": ["Doc.md §1 \"nothing yet.\""] }'
  git -C "$1" init -q -b main >/dev/null 2>&1 || { echo "check-rule-routes-selftest: git init failed" >&2; exit 2; }
  git -C "$1" add -A >/dev/null 2>&1
  git -C "$1" -c user.email=selftest@example.invalid -c user.name=selftest commit -qm one >/dev/null 2>&1
  printf 'fun main() { println("changed") }\n' > "$1/src/Thing.kt"
  git -C "$1" add -A >/dev/null 2>&1
  git -C "$1" -c user.email=selftest@example.invalid -c user.name=selftest commit -qm two >/dev/null 2>&1
}

mk_git_fixture "$tmp/diff"
mkdir -p "$tmp/diff/openspec/changes/thing"
printf '# a change\n\nIt touches `src/Thing.kt`.\n' > "$tmp/diff/openspec/changes/thing/proposal.md"
expect routes#theReadingSetFollowsTheDiff 1 "the change does not name it" \
  "a change naming fewer sections than the map derives for its paths is reported" \
  bash "$check" --root "$tmp/diff" --diff HEAD~1 --change "$tmp/diff/openspec/changes/thing"

printf '# a change\n\nIt is graded against `Doc.md` §1.\n' > "$tmp/diff/openspec/changes/thing/proposal.md"
expect routes#aChangeNamingItsDerivedSectionsPasses 0 "" \
  "a change that names every derived section passes" \
  bash "$check" --root "$tmp/diff" --diff HEAD~1 --change "$tmp/diff/openspec/changes/thing"

printf '# a change\n\nIt is graded against `Doc.md` §1, and it adds `Build.md` §3.\n' > "$tmp/diff/openspec/changes/thing/proposal.md"
expect routes#aChangeMayAddASection 0 "" \
  "a change may add a section it names beyond the derived set" \
  bash "$check" --root "$tmp/diff" --diff HEAD~1 --change "$tmp/diff/openspec/changes/thing"

if [ -n "$case_filter" ] && [ "$pass" = 0 ] && [ "$failures" = 0 ]; then
  echo "check-rule-routes-selftest: no case with the id '$case_filter'" >&2
  exit 2
fi
if [ "$failures" -ne 0 ]; then
  echo "check-rule-routes-selftest: $failures of $((pass + failures)) cases failed"
  exit 1
fi
echo "check-rule-routes-selftest: all $pass cases passed (throwaway fixtures, no Gradle, no device, no network)"
exit 0
