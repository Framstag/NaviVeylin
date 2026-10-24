#!/usr/bin/env bash
# revert-check-293.sh — the shape `issues_prio_marker_line` adds, falsified one clause at a time (issue #293,
# task 3.3). Every mutation is made **in the tree** (`tools/issues/lib.sh`), the case that is supposed to hold
# the clause must go red, and after the restore the case must be green again — a clause no case notices is a
# clause the gate does not hold.
#
# Usage: bash revert-check-293.sh [root]      (root defaults to `.`; run it from the change root)
# Exit:  0 every clause is held by a named case, 1 a clause was not noticed (or the restore left the tree red),
#        2 the environment is unusable.
#
# Every mutation is applied by exact replacement and refuses to run when the clause does not appear exactly once,
# so a drifted `lib.sh` fails the script instead of silently mutating nothing.
set -uo pipefail

root="${1:-.}"
lib="$root/tools/issues/lib.sh"
check="tools/issues/check-metadata-selftest.sh"
tooling="tools/issue-tooling-selftest.sh"

stamp() { date -u +%Y-%m-%dT%H:%M:%SZ; }
[ -f "$lib" ] || { echo "revert-check-293: $lib is missing (run from the change root)" >&2; exit 2; }
backup=$(mktemp "${TMPDIR:-/tmp}/revert-check-293.XXXXXX") || exit 2
cp "$lib" "$backup" || exit 2
restore() { cp "$backup" "$lib"; }
trap 'restore; rm -f "$backup"' EXIT

fails=0

mutate() { # mutate <clause> <replacement> → refuses unless the clause appears exactly once
  python3 - "$lib" "$1" "$2" <<'PY'
import sys
path, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
text = open(path, encoding="utf-8").read()
if text.count(old) != 1:
    sys.exit(f"revert-check-293: the clause {old!r} appears {text.count(old)} times in {path}")
open(path, "w", encoding="utf-8").write(text.replace(old, new))
PY
}

holds() { # holds <case> → 0 when the case passes
  bash "$1" >/dev/null 2>&1
}

runs() { # runs <suite> <case> → 0 when the case passes in that suite
  bash "$1" --case "$2" >/dev/null 2>&1
}

one() { # one <n> <title> <clause> <replacement> <suite> <case> <what the clause bounds>
  local n="$1" title="$2" clause="$3" replacement="$4" suite="$5" case_name="$6" bound="$7" start
  start=$(stamp)
  printf '== mutation %s — %s\n' "$n" "$title"
  printf '   %-58s %s\n' "$clause" "→  $replacement"
  if ! mutate "$clause" "$replacement"; then
    printf '  FAIL the clause could not be applied — the tree does not carry it as written\n'
    fails=$((fails+1)); restore; return
  fi
  if runs "$suite" "$case_name"; then
    printf '  NOT NOTICED: %s still passes — nothing the gate runs holds "%s"  (%s)\n' "$case_name" "$bound" "$start"
    fails=$((fails+1))
  else
    printf '  noticed by %s: the mutated clause makes it fail  (%s)\n' "$case_name" "$start"
  fi
  restore
  if runs "$suite" "$case_name"; then
    printf '  quiet again: %s passes on the restored tree  (%s)\n' "$case_name" "$(stamp)"
  else
    printf '  FAIL the restored tree is still red — %s  (%s)\n' "$case_name" "$(stamp)"
    fails=$((fails+1))
  fi
}

one 1 "the column-0 anchor" \
  'index(t, mo) == 1' 'index(t, mo) > 0' \
  "$check" 'check#aQuotationIsNotAMarker' \
  'a prose line that ends at the close is not the record'

one 2 "the end anchor" \
  'substr(t, length(t) - length(mc) + 1) == mc' '1 == 1' \
  "$check" 'check#aQuotationIsNotAMarker' \
  'a prose line that starts at the open but continues past the close is not the record'

one 3 "the fence" \
  'if (infence) next' 'if (0) next' \
  "$check" 'check#aMarkerLineBesideAQuotationIsTheRecord' \
  'a fenced excerpt is prose, not the record'

one 4 "the write path's line" \
  'NR == n { print marker; next }' 'NR == 0 { print marker; next }' \
  "$tooling" 'prioritise#theWriteKeepsAQuotation' \
  'the stored marker line is the line the writer replaces'

# The restore is part of the claim: a check that leaves a mutation behind is worse than no check, so the file is
# compared with the copy taken before the first mutation rather than trusted.
printf '== the tree is the change'"'"'s own again\n'
if cmp -s "$lib" "$backup"; then
  printf '  lib.sh restored byte for byte  (%s)\n' "$(stamp)"
else
  printf '  FAIL lib.sh differs from the copy taken before mutation 1  (%s)\n' "$(stamp)"
  fails=$((fails+1))
fi

if [ "$fails" -eq 0 ]; then
  printf 'REVERT-CHECK: PASS — four clauses, each noticed by the case that holds it  (%s)\n' "$(stamp)"
  exit 0
fi
printf 'REVERT-CHECK: FAIL — %s clause(s) no case notices  (%s)\n' "$fails" "$(stamp)"
exit 1
