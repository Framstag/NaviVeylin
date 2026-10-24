#!/usr/bin/env bash
# When does the gate's console carry `gate: <command>` and the stand-in suite's own first line?
#
# Polled every 10 ms, not sampled at one instant, so the two numbers are the gate's own start-up on this host:
# `loop_resolve`, the four profile reads, `git diff --name-only` beside `git status --porcelain` and the awk
# pipeline that de-duplicates the owed commands — none of which is the streaming this case is about (issue #296).
# The window the shipped case used is printed beside the readings.
#
# Usage: bash timing-probe.sh [runs]
set -uo pipefail

ROOT=$(git rev-parse --show-toplevel)
export LOOP_PROJECT_ROOT="$ROOT"
export LOOP_ENGINE="$ROOT/tools/loop"
export LOOP_FIXTURES="$ROOT/tools/loop/fixtures"
export LOOP_ISSUE_FIXTURES="$ROOT/tools/issues/fixtures"
export LOOP_CONF="$ROOT/tools/loop/loop.conf"
export LOOP_TMP; LOOP_TMP=$(mktemp -d "${TMPDIR:-/tmp}/timing-probe.XXXXXX")
export PATH="$LOOP_FIXTURES:$LOOP_ISSUE_FIXTURES:$PATH"
# shellcheck source=/dev/null
source "$ROOT/tools/loop-cases.sh"

runs=${1:-3}
printf '%s\n' "host: $(nproc) cpus, load $(cut -d' ' -f1-3 /proc/loadavg)"
for i in $(seq "$runs"); do
  d=$(scratch); p=$(new_project)
  conf="$d/gate.conf"
  cp "$LOOP_ENGINE/loop.conf" "$conf" || fail "could not copy the loop configuration"
  {
    printf 'LOOP_GATE_COMPILED_CMD=""\n'
    printf 'LOOP_GATE_COMPILED_PATTERNS=""\n'
    printf 'LOOP_GATE_SUITES="notes/*|bash %s/suite.sh"\n' "$d"
    printf 'LOOP_GATE_TALLY_DIR="%s/tally"\n' "$d"
  } >> "$conf"
  cat > "$d/suite.sh" <<'SUITE'
printf 'case: a note is read\n'
printf 'progress: the suite is still working\n' >&2
sleep 3
printf 'notes: all 1 cases passed\n'
SUITE
  mkdir -p "$p/notes"
  printf 'a note\n' > "$p/notes/a.md"
  git -C "$p" add -A >/dev/null 2>&1 && git -C "$p" commit -qm 'the change' >/dev/null 2>&1 \
    || fail "could not commit the change in the throwaway repository"
  base=$(git -C "$p" rev-parse HEAD~1)
  log="$d/gate.log"
  t0=$(date +%s%N)
  ( cd "$p" && LOOP_CONF="$conf" bash "$LOOP_ENGINE/gate.sh" --base "$base" ) > "$log" 2>&1 &
  pid=$!
  announced=-; first=-
  for _ in $(seq 3000); do
    out=$(cat "$log")
    now=$(( ($(date +%s%N) - t0) / 1000000 ))
    if [ "$announced" = "-" ]; then case "$out" in *'gate: bash'*) announced=$now ;; esac; fi
    if [ "$first" = "-" ]; then case "$out" in *'case: a note is read'*) first=$now ;; esac; fi
    if [ "$announced" != "-" ] && [ "$first" != "-" ]; then break; fi
    sleep 0.01
  done
  wait "$pid" || fail "the gate failed"
  printf '%s\n' "run $i: \`gate: bash\` at ${announced} ms, the suite's first line at ${first} ms, the window the case used was 1500 ms"
done
rm -rf "$LOOP_TMP"
