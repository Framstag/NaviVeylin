#!/usr/bin/env bash
# revert-check.sh — the three falsifications of this change's cases, run from a checkout of the change.
#
# Each mutation is applied to a copy of the file it guards, the probe is run, the file is restored, and the
# probe is required green again. `routed-read#bound` is falsified by lowering the probe's own threshold, because
# the tree's shape is the guard it measures: at 5 000 bytes §7 and §9 join the four offenders.
#
# Usage: revert-check.sh [<log file>]      # default: evidence/revert-check.log
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
root=$(cd "$here/../../../.." && pwd)
probe="$here/red-probe.sh"
log="${1:-$here/revert-check.log}"
build="$root/guidelines/Build.md"
agents="$root/AGENTS.md"
out=$(mktemp "${TMPDIR:-/tmp}/revert-check.XXXXXX") || exit 2
trap 'rm -f "$out"' EXIT

: > "$log"
say() { printf '%s\n' "$*" | tee -a "$log"; }

# run <label> <probe arguments...> — runs the probe, logs its output and its real exit status.
run() {
  local label="$1"; shift
  bash "$probe" "$@" > "$out" 2>&1
  local status=$?
  sed "s/^/[$label] /" "$out" | tee -a "$log"
  say "[$label] EXIT=$status"
  return "$status"
}

say "revert-check $(date -u +%Y-%m-%dT%H:%M:%SZ) — root $root"

# 1. routed-read#verbatim — reword one sentence of §6f in place.
say ''
say '## routed-read#verbatim — one sentence of §6f reworded'
cp "$build" "$build.bak"
python3 - "$build" <<'PY'
import sys
p=sys.argv[1]; s=open(p,encoding='utf-8').read()
old="Gradle's short location is the frame"
assert old in s, 'the sentence the mutation targets is gone'
open(p,'w',encoding='utf-8').write(s.replace(old,"Gradle's brief location is the frame",1))
PY
run mutated verbatim || true
mv "$build.bak" "$build"
run restored verbatim
say "EXIT=$?"

# 2. routed-read#route — the build row back to the bare parents.
say ''
say '## routed-read#route — the build row reverted to `Build.md` §2, §3, §4'
cp "$agents" "$agents.bak"
python3 - "$agents" <<'PY'
import sys,re
p=sys.argv[1]; s=open(p,encoding='utf-8').read()
row=re.search(r'^\| build, run, force a rerun.*$', s, re.M)
assert row, 'the build row is gone'
s=s[:row.start()]+'| build, run, force a rerun, read a gate result, or a workflow\'s action pin | `Build.md` §2, §3, §4 |'+s[row.end():]
open(p,'w',encoding='utf-8').write(s)
PY
run mutated route || true
mv "$agents.bak" "$agents"
run restored route
say "EXIT=$?"

# 3. routed-read#bound — the probe's own threshold, lowered.
say ''
say '## routed-read#bound — the threshold lowered to 5 000 bytes'
run mutated --bound 5000 bound || true
run restored bound
say "EXIT=$?"

say ''
say '## all four cases, restored'
run restored
say "EXIT=$?"
