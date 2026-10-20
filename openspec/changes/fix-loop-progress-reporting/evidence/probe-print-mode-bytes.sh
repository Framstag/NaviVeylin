#!/usr/bin/env bash
# Probe: what a print-mode child's stream carries while the child is still running.
#
# The configured agent command (`LOOP_AGENT_CMD` in tools/loop/loop.conf) is `pi --print`. Print mode writes
# one thing — the final assistant text — when the run is over and nothing before it. That is the whole reason
# a run was silent between `pick #N` and the item's verdict: `run_child` redirected a stream that carries
# nothing (tools/loop/run.sh, run_child: `( cd "$LOOP_ROOT" && $LOOP_AGENT_CMD "$1" ) > "$2" 2>&1`).
#
# This probe reproduces that stream shape without starting a model or a `pi --print` process: a stand-in child
# holds its answer and writes exactly 16 bytes at exit — the size the fix's commit measured from the real
# command (`1fa8077`: "measured 16 bytes at exit, nothing before it, on a file and on a pipe"). It measures the
# *observation* the loop depends on: byte count at one-second intervals, on a redirected file and on a pipe,
# with the child alive for 5 s.
#
# Usage: bash openspec/changes/fix-loop-progress-reporting/evidence/probe-print-mode-bytes.sh
# Exit:  0 the measurement was taken (the numbers are in the output, not in the exit status).
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
work=$(mktemp -d "$here/.probe.XXXXXX")
trap 'rm -rf "$work"' EXIT

# The stand-in child: silent while it works, its final text written once, at exit. 16 bytes, no newline
# surprises: the real command's final assistant text was 16 bytes too.
cat > "$work/child.sh" <<'CHILD'
#!/usr/bin/env bash
sleep 5
printf 'VERDICT: PASSED\n'   # 16 bytes — the final assistant text, written at exit
CHILD
chmod +x "$work/child.sh"

printf 'probe: print-mode stand-in child, 5 s of work, 16-byte final text\n'
printf 'probe: sampling every second; "at exit" is the sample taken after the child is reaped\n\n'

# ── (1) the stream as run_child consumes it: stdout redirected to a file ──────────────────────────────
out="$work/child-report.md"
: > "$out"
"$work/child.sh" > "$out" 2>&1 &
pid=$!
printf 'redirected to a file (%s)\n' "$(basename "$out")"
for i in 1 2 3 4 5; do
  sleep 1
  printf '  t=%ds  %3s B\n' "$i" "$(wc -c < "$out" | tr -d ' ')"
done
wait "$pid"
printf '  at exit  %3s B   <- what run_child had in the report file all along\n' "$(wc -c < "$out" | tr -d ' ')"

# ── (2) the stream on a pipe, sampled by a reader ──────────────────────────────────────────────────────
printf '\nredirected into a pipe\n'
fifo="$work/child.pipe"
mkfifo "$fifo"
count="$work/bytes-on-pipe"
: > "$count"
"$work/child.sh" > "$fifo" 2>&1 &
pid=$!
( while IFS= read -r -n 1 _; do printf 'x' >> "$count"; done < "$fifo" ) &
reader=$!
for i in 1 2 3 4 5; do
  sleep 1
  printf '  t=%ds  %3s B\n' "$i" "$(wc -c < "$count" | tr -d ' ')"
done
wait "$pid"
sleep 1
printf '  at exit  %3s B   <- a pipe carries no more than a file did\n' "$(wc -c < "$count" | tr -d ' ')"
kill "$reader" 2>/dev/null
wait "$reader" 2>/dev/null

printf '\nprobe: the stream is empty for the whole life of a print-mode child, on a file and on a pipe —\n'
printf 'probe: redirecting it (what run_child did) cannot report the wait; the run must say something itself.\n'
