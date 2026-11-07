#!/usr/bin/env bash
# Issue #280, correction round: the two corrected cases against the *pre-correction* tree.
#
# The round-1 record (`stopReason:$sr`) and the round-1 column (`\(.stopReason // "-")`) are put back into a copy
# of `tools/loop/state.sh`, the two cases run against it, and the file is restored — so the failure each case
# reports is the failure the tree carried before this round, not a failure this round's edit caused.
#
# Usage: bash red-cases-280-round2.sh [change-root]
set -uo pipefail
root=${1:-.}
engine="$root/tools/loop/state.sh"
backup=$(mktemp)

cp "$engine" "$backup"
trap 'cp "$backup" "$engine"; rm -f "$backup"' EXIT

python3 - "$engine" <<'PY'
import sys
p = sys.argv[1]
s = open(p).read()
fixed_write = '                  stopReason:(if $sr == "" then null else $sr end),'
round1_write = '                  stopReason:$sr,'
assert fixed_write in s, "the correction's state write is not in the tree"
s = s.replace(fixed_write, round1_write)
fixed_col = '''       # A record written before the field existed has no `stopReason`, one written since carries `null` when no
       # mechanism is given, and only a mechanism block carries a word: all three print `-`, and `//` alone does
       # not, because it yields the empty string for an empty one (#280, review round 1).
       | ((.stopReason // "") | if . == "" then "-" else . end) as $stopcol
'''
assert fixed_col in s, "the correction's stop column is not in the tree"
s = s.replace(fixed_col, '')
assert '\\($stopcol)' in s, "the row does not use the stop column"
s = s.replace('\\($stopcol)', '\\(.stopReason // "-")')
open(p, 'w').write(s)
PY

printf '== %s — pre-correction tree: round-1 record and round-1 column restored\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
for c in 'state#aBlockedItemNamesTheMechanismThatStoppedIt' 'report#wallTimeAndReasonInTheTable'; do
  printf '== %s — %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$c"
  bash "$root/tools/loop-selftest.sh" --case "$c" 2>&1; status=$?
  printf '== %s — %s exit=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$c" "$status"
done
