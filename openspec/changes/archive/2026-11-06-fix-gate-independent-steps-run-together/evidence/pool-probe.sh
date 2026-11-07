#!/usr/bin/env bash
# A probe, not a case: the same three independent 3 s suites through the gate twice — once with the pool at 1 (the
# serial engine) and once with it at 3 — so the change records the two wall clocks it quotes, on this host, from a
# command anyone can rerun. Run from the change root: bash <this file>.
set -uo pipefail
root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)
engine="$root/tools/loop/gate.sh"
d=$(mktemp -d "${TMPDIR:-/tmp}/gate-pool-probe.XXXXXX") || exit 2
trap 'rm -rf "$d"' EXIT

p="$d/tree"
mkdir -p "$p/notes" "$p/tools"
printf 'a note\n' > "$p/notes/a.md"
for s in a b c; do
  printf 'sleep 3\nprintf "notes: all 1 cases passed\\n"\n' > "$p/suite-$s"
  chmod +x "$p/suite-$s"
done
git -C "$p" init -q 2>/dev/null
git -C "$p" add -A >/dev/null 2>&1
git -C "$p" -c user.email=probe@example.invalid -c user.name=probe commit -qm 'the change' >/dev/null 2>&1 || exit 2
printf 'a second note\n' > "$p/notes/b.md"
git -C "$p" add -A >/dev/null 2>&1
git -C "$p" -c user.email=probe@example.invalid -c user.name=probe commit -qm 'the second change' >/dev/null 2>&1 || exit 2
base=$(git -C "$p" rev-parse HEAD~1)

for pool in 1 3; do
  conf="$d/gate-$pool.conf"
  cp "$root/tools/loop/loop.conf" "$conf" || exit 2
  {
    printf 'LOOP_GATE_COMPILED_CMD=""\n'
    printf 'LOOP_GATE_COMPILED_PATTERNS=""\n'
    printf 'LOOP_GATE_SUITES="notes/*|./suite-a;notes/*|./suite-b;notes/*|./suite-c"\n'
    printf 'LOOP_GATE_TALLY_DIR="%s/tally-%s"\n' "$d" "$pool"
    printf 'LOOP_GATE_POOL_JOBS=%s\n' "$pool"
    printf 'LOOP_GATE_STEP_MAX_SECONDS=60\n'
  } >> "$conf"
  start=$SECONDS
  out=$( cd "$p" && LOOP_CONF="$conf" bash "$engine" --base "$base" 2>&1 )
  rc=$?
  took=$((SECONDS - start))
  printf 'PROBE pool=%s wall=%ss rc=%s verdict=%s\n' "$pool" "$took" "$rc" \
    "$(printf '%s\n' "$out" | sed -n 's/^\(LOOP-GATE: tests=.*\)$/\1/p' | tail -1)"
  printf 'PROBE pool=%s poolline=%s\n' "$pool" "$(printf '%s\n' "$out" | sed -n 's/^\(LOOP-GATE-POOL:.*\)$/\1/p' | tail -1)"
  printf 'PROBE pool=%s tallies=%s\n' "$pool" "$(ls "$d/tally-$pool" 2>/dev/null | tr '\n' ' ')"
done
