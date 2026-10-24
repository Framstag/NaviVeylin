#!/usr/bin/env bash
# checks.sh <change-root> — every check the round-3 correction owes, on the corrected tree.
#
# The correction changes no compiled file: two `spec.md` copies (one clause deleted from the first scenario's
# THEN) and `tasks.md`. `guidelines/Build.md` §2's scope block therefore maps it to the structural pair plus the
# CLI's own validation; the gate is not re-run (it was run once for this item, task 4.4).
#
# Exit 0 when every check passed.
set -uo pipefail
root=${1:-}
[ -n "$root" ] && [ -f "$root/AGENTS.md" ] || { echo "checks: pass the change root (no $root/AGENTS.md)" >&2; exit 2; }
name=fix-openspec-strict-rules-unread
arch="$root/openspec/changes/archive/2026-10-22-$name"
delta="$arch/specs/documentation-ownership/spec.md"
merged="$root/openspec/specs/documentation-ownership/spec.md"
fail=0

date -Iseconds

echo "== check-doc-routes"
( cd "$root" && bash tools/check-doc-routes.sh ); rc=$?; echo "EXIT=$rc"; [ "$rc" -eq 0 ] || fail=1

echo "== gen-feature-list --check-classification"
( cd "$root" && bash tools/gen-feature-list.sh --check-classification ); rc=$?; echo "EXIT=$rc"; [ "$rc" -eq 0 ] || fail=1

echo "== openspec validate documentation-ownership --type spec"
( cd "$root" && openspec validate documentation-ownership --type spec ); rc=$?; echo "EXIT=$rc"; [ "$rc" -eq 0 ] || fail=1

echo "== openspec validate $name --strict (archived change, temporary open copy)"
if [ -e "$root/openspec/changes/$name" ]; then
  echo "REFUSED: $root/openspec/changes/$name already exists" >&2; fail=1
else
  cp -a "$arch" "$root/openspec/changes/$name"
  ( cd "$root" && openspec validate "$name" --strict ); rc=$?; echo "EXIT=$rc"; [ "$rc" -eq 0 ] || fail=1
  rm -rf "$root/openspec/changes/$name"
  echo "temp open copy removed: $(ls "$root/openspec/changes")"
fi

echo "== probe (both cases), run from the archived change"
( cd "$arch" && bash evidence/probe.sh ); rc=$?; echo "EXIT=$rc"; [ "$rc" -eq 0 ] || fail=1

echo "== the deleted clause is gone from both copies"
n=$(grep -c 'summarises each in one sentence' "$delta" "$merged" | grep -vc ':0$')
echo "copies still carrying the clause: $n (0 expected)"
[ "$n" -eq 0 ] || fail=1

echo "== requirement body characters"
chars=$(awk '/^### Requirement: The validator.s own strict rules/{f=1;next} f && /^#### /{exit} f{print}' \
  "$merged" | tr '\n' ' ' | wc -c)
echo "$chars"
[ "$chars" -le 500 ] || fail=1

echo "== delta block vs merged block"
strip() { awk '/^### Requirement: The validator.s own strict rules/{f=1} f' "$1"; }
if diff -q <(strip "$delta") <(strip "$merged") >/dev/null; then echo IDENTICAL; else echo DIFFERENT; fail=1; fi

echo "== scenarios and cases in the change"
grep -c '^#### Scenario: ' "$delta"
grep -c '^- \*\*Case\*\*: ' "$delta"

echo "== AGENTS.md added block, lines"
( cd "$root" && awk '/^Two more rules are the validator.s own/{f=1} f && /^## Build & Test/{exit} f' AGENTS.md | wc -l )

echo "== open change set"
ls "$root/openspec/changes"

echo "==EXIT=$fail"
exit "$fail"
