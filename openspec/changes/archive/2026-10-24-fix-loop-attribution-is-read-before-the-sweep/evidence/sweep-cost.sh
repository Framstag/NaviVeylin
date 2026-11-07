#!/usr/bin/env bash
# The cost of one host-wide sweep of the process table, and of a bare `readlink` of every process — the two
# numbers that made the reading order a defect (issue #288, change
# `fix-loop-attribution-is-read-before-the-sweep`). Re-runnable as it stands.
set -uo pipefail
cd "$(dirname "$0")/../../../.." || exit 2
source tools/loop/lib.sh
printf 'pids visible: %s\n' "$(ls -d /proc/[0-9]* | wc -l)"
printf '\n--- `readlink /proc/<pid>/cwd` for every pid ---\n'
time (for d in /proc/[0-9]*/cwd; do readlink "$d" >/dev/null 2>&1; done)
printf '\n--- loop_pids_under <project> (what outside_write_pids sweeps with) ---\n'
time loop_pids_under "$PWD" >/dev/null
printf '\n--- the same, into a command substitution, as outside_write_pids calls it ---\n'
time ( cd / && x=$(loop_pids_under "$PWD" | cut -c1-120) )
