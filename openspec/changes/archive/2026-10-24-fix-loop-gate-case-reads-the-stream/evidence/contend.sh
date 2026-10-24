#!/usr/bin/env bash
# A contended host, made real instead of simulated: 24 spinning shells at nice 0 beside a case run at nice 19,
# which is what "a busy machine" does to a process that has to get CPU before the sample is taken. The case's
# own `sleep 1.5` is a wall-clock wait and does not slow down; everything the gate does before it announces the
# suite does.
#
# Usage: bash contend.sh <tree> [hogs]
set -uo pipefail
tree=${1:?tree}
hogs_n=${2:-24}
cd "$tree" || exit 2

hogs=""
cleanup() { [ -n "$hogs" ] && kill $hogs 2>/dev/null; }
trap cleanup EXIT
for _ in $(seq "$hogs_n"); do
  ( while :; do :; done ) &
  hogs="$hogs $!"
done
printf '%s\n' "contention: $hogs_n spinning shells at nice 0, the case runs at nice 19, on $(nproc) cpus"
t0=$(date +%s)
nice -n 19 bash tools/loop-selftest.sh --case 'gate#theSuitesOutputReachesTheConsoleWhileItRuns' 2>&1
rc=$?
printf '%s\n' "the contended run took $(( $(date +%s) - t0 )) s wall, exit=$rc"
exit "$rc"
