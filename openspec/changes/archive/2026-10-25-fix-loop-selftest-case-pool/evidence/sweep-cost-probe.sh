#!/usr/bin/env bash
# throwaway probe: the suite's own sweep cost, and how many processes the host has
set -uo pipefail
root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
source "$root/tools/loop/lib.sh"
LOOP_ROOT="$root"
n=0
for e in /proc/[0-9]*; do case "${e#/proc/}" in ''|*[!0-9]*) continue ;; esac; n=$((n+1)); done
echo "processes: $n"
t0=$(date +%s%N)
loop_pids_under /tmp/no-such-tree >/dev/null
echo "one sweep: $(( ( $(date +%s%N) - t0 ) / 1000000 )) ms"
t0=$(date +%s%N)
loop_pids_under /tmp/no-such-tree >/dev/null
echo "one sweep: $(( ( $(date +%s%N) - t0 ) / 1000000 )) ms"
