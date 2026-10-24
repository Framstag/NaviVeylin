#!/usr/bin/env bash
# final-verification.sh <change-root> — the round-3 correction's last pass, on the final tree.
#
# Reads only: the probe (which writes nothing but a temporary open copy it removes), the merged requirement,
# the delta, and `tasks.md`. `checks.sh` carries the structural pair and the CLI validation; this file re-states
# the numbers the correction is about so the report and `tasks.md` quote one run of each.
set -uo pipefail
root=${1:-}
[ -n "$root" ] && [ -f "$root/AGENTS.md" ] || { echo "final-verification: pass the change root" >&2; exit 2; }
name=fix-openspec-strict-rules-unread
arch="$root/openspec/changes/archive/2026-10-22-$name"
delta="$arch/specs/documentation-ownership/spec.md"
merged="$root/openspec/specs/documentation-ownership/spec.md"
fail=0

date -Iseconds

echo "== probe (both cases), run from the archived change"
( cd "$arch" && bash evidence/probe.sh ); rc=$?; echo "EXIT=$rc"; [ "$rc" -eq 0 ] || fail=1

echo "== the deleted clause, both copies"
grep -c 'summarises each in one sentence' "$delta" "$merged"

echo "== requirement body characters"
awk '/^### Requirement: The validator.s own strict rules/{f=1;next} f && /^#### /{exit} f{print}' "$merged" \
  | tr '\n' ' ' | wc -c

echo "== delta block vs merged block"
strip() { awk '/^### Requirement: The validator.s own strict rules/{f=1} f' "$1"; }
if diff -q <(strip "$delta") <(strip "$merged") >/dev/null; then echo IDENTICAL; else echo DIFFERENT; fail=1; fi

echo "== AGENTS.md added block, lines"
( cd "$root" && awk '/^Two more rules are the validator.s own/{f=1} f && /^## Build & Test/{exit} f' AGENTS.md | wc -l )

echo "== task markers"
echo "done:  $(grep -c '^- \[x\]' "$arch/tasks.md")"
echo "open:  $(grep -c '^- \[ \]' "$arch/tasks.md")"
echo "bare:  $(grep -cE '^[0-9]+\. \[' "$arch/tasks.md")"

echo "== stale figures still quoted"
grep -n 'measures 425\|15 insertions\|12:18:35' "$arch/tasks.md" || echo "none"

echo "==EXIT=$fail"
exit "$fail"
