#!/usr/bin/env bash
# The change's falsification: one mutation per invariant, restored after each, and the named case must go
# red in between (guidelines/Build.md §2's "a new invariant owes a revert-check", TODO.md §113).
#
# Five mutations, five invariants:
#   7.1  instruction detection      the check stops rejecting a line that names the file without citing one
#   7.2a the citation half          the check resolves every number, whatever the record says
#   7.2b the `nvid` lookup          list.sh matches a substring instead of the marker's delimiters
#   7.3  the allowlist              `guidelines/` is excluded, so an instruction there is invisible
#   7.4  the rewiring's completeness the AGENTS.md map row goes back to a plain mention of the file
#   7.5  the import assertion       the `agent/eligible: 0` assertion is relaxed to always pass
#
# Every mutation is applied to the working tree and reverted with a backup copy; the script asserts the tree
# is byte-identical at the end (a git diff of the two files it touches must be empty).
#
# Usage: revert-checks.sh            # prints one line per mutation plus the restored-state check
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
root=$(cd "$here/../../.." && pwd)
check="$root/tools/check-todo-references.sh"
selftest="$root/tools/check-todo-references-selftest.sh"
tooling="$root/tools/issue-tooling-selftest.sh"
list="$root/tools/issues/list.sh"
agents="$root/AGENTS.md"
backup=$(mktemp -d "${TMPDIR:-/tmp}/revert-checks.XXXXXX") || exit 2
failures=0

cp "$check" "$backup/check.sh"
cp "$list" "$backup/list.sh"
cp "$agents" "$backup/AGENTS.md"
before=$(md5sum "$check" "$list" "$agents")

restore() {
  cp "$backup/check.sh" "$check"
  cp "$backup/list.sh" "$list"
  cp "$backup/AGENTS.md" "$agents"
  rm -rf "$backup"
}
trap restore EXIT

note() { printf '%s\n' "$*"; }

case_result() { # $1 = runner, $2 = case name, $3 = expected exit (1 = must fail, 0 = must pass)
  local runner="$1" name="$2" expect="$3" out got
  out=$(bash "$runner" 2>&1 | grep -F "$name" | head -1)
  case "$out" in
    PASS*) got=0 ;;
    FAIL*) got=1 ;;
    *) got=2 ;;
  esac
  if [ "$got" = "$expect" ]; then
    note "  ok    $name → $out"
  else
    note "  BAD   $name → expected $( [ "$expect" = 1 ] && echo 'FAIL' || echo 'PASS' ), got: ${out:-<no line>}"
    failures=$((failures + 1))
  fi
}

note "7.1 instruction detection — the check stops rejecting a mention without a citation"
# the mutation is written against a backslash-free anchor: the file's pattern carries `\.md`, and a
# double-escaped sed pattern silently matches nothing (measured 2026-10-17 — the case stayed green)
sed -i 's|text !~ /§/) {|text !~ /§/ \&\& 0) {|' "$check"
case_result "$selftest" "refcheck#instructionLineFails" 1
cp "$backup/check.sh" "$check"
case_result "$selftest" "refcheck#instructionLineFails" 0

note "7.2a the citation half — every number resolves, whatever the record says"
sed -i 's|^  grep -q "\^\$num	" "\$tmp/table" && return 0|  return 0|' "$check"
case_result "$selftest" "refcheck#unresolvedCitationFails" 1
cp "$backup/check.sh" "$check"
case_result "$selftest" "refcheck#unresolvedCitationFails" 0

note "7.2b the nvid lookup — a substring match instead of the marker's delimiters"
sed -i 's|select(.body // "" \| contains(\$marker))|select(.body // "" \| contains("nvid: " + \$id))|' "$list"
case_result "$tooling" "list#idDoesNotMatchASubstring" 1
cp "$backup/list.sh" "$list"
case_result "$tooling" "list#idDoesNotMatchASubstring" 0

note "7.3 the allowlist — guidelines/ excluded, so an instruction there is invisible"
sed -i "s|  'tools/check-todo-references.sh|  'guidelines/*\|a mutation that must break the check'\n  'tools/check-todo-references.sh|" "$check"
case_result "$selftest" "refcheck#instructionLineFails" 1
cp "$backup/check.sh" "$check"
case_result "$selftest" "refcheck#instructionLineFails" 0

note "7.4 rewiring completeness — the AGENTS.md map row names the file with no citation"
sed -i 's|^\(. what is open, and what to do next .\) |\1 `TODO.md` |' "$agents"
out=$(bash "$check" --quiet 2>&1)
code=$?
if [ "$code" = 1 ] && printf '%s' "$out" | grep -qF 'file=AGENTS.md'; then
  note "  ok    tools/check-todo-references.sh → exit 1, naming AGENTS.md"
else
  note "  BAD   tools/check-todo-references.sh → expected exit 1 naming AGENTS.md, got exit $code"
  failures=$((failures + 1))
fi
cp "$backup/AGENTS.md" "$agents"
out=$(bash "$check" --quiet 2>&1)
code=$?
if [ "$code" = 0 ]; then
  note "  ok    tools/check-todo-references.sh → exit 0 again"
else
  note "  BAD   tools/check-todo-references.sh → expected exit 0 after the restore, got exit $code"
  failures=$((failures + 1))
fi

note "7.5 the import assertion — 'agent/eligible: 0' relaxed to always pass"
strict='[.[] | select([.labels[].name] | index("agent/eligible") != null)] | length'
relaxed='[.[] | select([.labels[].name] | index("agent/eligible") != null) | select(false)] | length'
fixture='[{"number":1,"labels":[{"name":"class/bug"},{"name":"agent/eligible"}]}]'
got_strict=$(printf '%s' "$fixture" | jq "$strict")
got_relaxed=$(printf '%s' "$fixture" | jq "$relaxed")
if [ "$got_strict" = "1" ] && [ "$got_relaxed" = "0" ]; then
  note "  ok    a fixture carrying the label: the recorded assertion reports $got_strict, the relaxed one $got_relaxed"
else
  note "  BAD   the assertion pair does not separate: strict=$got_strict relaxed=$got_relaxed"
  failures=$((failures + 1))
fi
recorded=$(grep -c 'agent/eligible' "$root/openspec/changes/migrate-todo-to-github-issues/import-evidence.md")
if [ "$recorded" -gt 0 ]; then
  note "  ok    the recorded assertion is in import-evidence.md ($recorded line(s) name the label)"
else
  note "  BAD   import-evidence.md records no 'agent/eligible' assertion"
  failures=$((failures + 1))
fi

note ""
after=$(md5sum "$check" "$list" "$agents")
if [ "$after" = "$before" ]; then
  note "restored: the three mutated files are byte-identical to their state before the mutations"
else
  note "BAD: a mutation survived — a mutated file differs from its state before the run"
  printf '%s\n' "$after"
  failures=$((failures + 1))
fi

if [ "$failures" -eq 0 ]; then
  note "revert-checks: every mutation went red as named, and every restore is green"
  exit 0
fi
note "revert-checks: $failures check(s) failed"
exit 1
