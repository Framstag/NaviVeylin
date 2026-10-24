#!/usr/bin/env bash
# revert-check.sh — the four falsifications of this change's cases, run from a checkout of the change.
#
# One mutation per check (guidelines/Build.md §4f, "One mutation per revert check"): the mutation is applied to
# the file the case guards, the case is run, the file is restored, and the case is required green again.
#
#   routed-read#verbatim   reword one sentence of §6f in place
#   routed-read#route (a)  the build row back to the bare parents (§2, §3, §4)
#   routed-read#route (b)  the build row keeps its sub-section names but loses the cost it states
#   routed-read#bound      §10a–§10j demoted to `###`, i.e. the offender's single-heading shape restored — the
#                          case must name §10 at its pre-split size again. No threshold is touched: the case
#                          fires on production content, which is what the invariant guards.
#
# Every check keeps its own log (`revert-check-<case>.log`) beside the combined one, because a task cites a run,
# and a cited file that does not exist is a dangling pointer.
#
# Usage: revert-check.sh [<log file>]      # default: evidence/revert-check.log
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
root=$(cd "$here/../../../.." && pwd)
probe="$here/red-probe.sh"
combined="${1:-$here/revert-check.log}"
build="$root/guidelines/Build.md"
agents="$root/AGENTS.md"
out=$(mktemp "${TMPDIR:-/tmp}/revert-check.XXXXXX") || exit 2
trap 'rm -f "$out"' EXIT
failures=0

: > "$combined"
log="$combined"
say() { printf '%s\n' "$*" | tee -a "$log" >> "$combined"; }
start() { # $1 = case name that owns this log
  log="$here/revert-check-$1.log"
  : > "$log"
  say "# revert-check for routed-read#$1 — $(date -u +%Y-%m-%dT%H:%M:%SZ), root $root"
}

# run <label> <probe arguments...> — runs the probe, logs its output and its real exit status.
run() {
  local label="$1"; shift
  bash "$probe" "$@" > "$out" 2>&1
  local status=$?
  sed "s/^/[$label] /" "$out" | tee -a "$log" >> "$combined"
  say "[$label] EXIT=$status"
  return "$status"
}

# red <label> <probe arguments...> — the case must fail under the mutation.
red() {
  if run "$@" ; then
    say "[$1] UNEXPECTED GREEN — the mutation did not reach the case"
    failures=$((failures + 1))
  fi
}
# green <label> <probe arguments...> — the restored file must pass.
green() {
  if ! run "$@" ; then
    say "[$1] UNEXPECTED RED — the restore did not return the case to green"
    failures=$((failures + 1))
  fi
}

say "revert-check $(date -u +%Y-%m-%dT%H:%M:%SZ) — root $root"

# 1. routed-read#verbatim — reword one sentence of §6f in place.
start verbatim
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
red mutated verbatim || true
mv "$build.bak" "$build"
green restored verbatim
say "EXIT=$failures"

# 2a. routed-read#route — the build row back to the bare parents.
start route
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
red mutated-route-parents route || true
mv "$agents.bak" "$agents"

# 2b. routed-read#route — the same row keeps its sub-section names and loses the cost it states.
say ''
say '## routed-read#route — the build row keeps its sub-sections and states no cost'
cp "$agents" "$agents.bak"
python3 - "$agents" <<'PY'
import sys,re
p=sys.argv[1]; s=open(p,encoding='utf-8').read()
row=re.search(r'^\| build, run, force a rerun.*$', s, re.M)
assert row, 'the build row is gone'
mutated=row.group(0).replace(' kB', '')
assert mutated != row.group(0), 'the build row states no kB cost to strip'
s=s[:row.start()]+mutated+s[row.end():]
open(p,'w',encoding='utf-8').write(s)
PY
red mutated-route-cost route || true
mv "$agents.bak" "$agents"
green restored route
say "EXIT=$failures"

# 3. routed-read#bound — the offender's single-heading shape restored.
start bound
say ''
say '## routed-read#bound — §10a–§10j demoted to `###` (section 10 whole again)'
cp "$build" "$build.bak"
python3 - "$build" <<'PY'
import sys,re
p=sys.argv[1]; s=open(p,encoding='utf-8').read()
s2,n=re.subn(r'(?m)^## (10[a-j])\.', r'### \1.', s)
assert n == 10, f'expected ten §10 sub-headings, found {n}'
open(p,'w',encoding='utf-8').write(s2)
PY
red mutated bound || true
mv "$build.bak" "$build"
green restored bound
say "EXIT=$failures"

say ''
say '## all four cases, restored'
green restored-all
say "EXIT=$failures"

log="$combined"
[ "$failures" -eq 0 ] || { say "revert-check: $failures unexpected result(s)"; exit 1; }
exit 0
