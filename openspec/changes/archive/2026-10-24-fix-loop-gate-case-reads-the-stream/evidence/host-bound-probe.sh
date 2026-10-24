#!/usr/bin/env bash
# The three forms of the reading and of the gate, on one host shape (issue #296).
#
# The case's body is reproduced here, with the host shape the item names: a machine whose own start-up outlasts
# the sample. That shape is made deliberate rather than waited for — a `git` wrapper that costs
# PROBE_GIT_DELAY seconds adds it to the three `git` calls the gate makes before it announces the suite
# (`git rev-parse --verify`, `git diff --name-only`, `git status --porcelain`), which is what a loaded host does
# to them by contention instead. Everything else is the case: the throwaway project, the stand-in suite (one line,
# a 3 s hold, a summary), the profile that gates `notes/*` and nothing else.
#
#   run 1  the base reading — the console read once, 1.5 s after the gate was started, as HEAD's case does it
#   run 2  the fixed reading — wait, within the bound, for the suite's own first line, then assert
#   run 3  a gate that *replays*: the whole output captured and printed after the command returned, read by the
#          fixed form, which must still refuse it
#
# Usage: bash host-bound-probe.sh [git-delay-seconds]
set -uo pipefail

ROOT=$(git rev-parse --show-toplevel)
export LOOP_PROJECT_ROOT="$ROOT"
export LOOP_ENGINE="$ROOT/tools/loop"
export LOOP_FIXTURES="$ROOT/tools/loop/fixtures"
export LOOP_ISSUE_FIXTURES="$ROOT/tools/issues/fixtures"
export LOOP_CONF="$ROOT/tools/loop/loop.conf"
export LOOP_TMP; LOOP_TMP=$(mktemp -d "${TMPDIR:-/tmp}/host-bound-probe.XXXXXX")
export PATH="$LOOP_FIXTURES:$LOOP_ISSUE_FIXTURES:$PATH"
# shellcheck source=/dev/null
source "$ROOT/tools/loop-cases.sh"

delay=${1:-0.6}
real_git=$(command -v git)

setup() { # <scratch dir> — a throwaway project, the profile and the stand-in suite, exactly as the case builds them
  local d=$1 p
  p=$(new_project)
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
  # `setup` runs in a command substitution, so the base is handed back through a file rather than a variable.
  printf '%s\n' "$base" > "$d/base"
  printf '%s\n' "$p"
}

slow_host() { # <dir> — a PATH prefix whose `git` costs `delay` seconds per call
  local dir=$1
  mkdir -p "$dir"
  {
    printf '#!/usr/bin/env bash\n'
    printf 'sleep %s\n' "$delay"
    printf 'exec %s "$@"\n' "$real_git"
  } > "$dir/git"
  chmod +x "$dir/git"
  printf '%s\n' "$dir"
}

printf '%s\n' "host: $(nproc) cpus, load $(cut -d' ' -f1-3 /proc/loadavg); an added ${delay}s per git call"

# ---- run 1: the base reading — one sample at a fixed 1.5 s ------------------------------------------------
d=$(scratch); p=$(setup "$d"); base=$(cat "$d/base"); slow=$(slow_host "$d/bin")
log="$d/gate.log"
( cd "$p" && PATH="$slow:$PATH" LOOP_CONF="$d/gate.conf" bash "$LOOP_ENGINE/gate.sh" --base "$base" ) > "$log" 2>&1 &
pid=$!
sleep 1.5
out=$(cat "$log")
printf '\n--- run 1: the base reading (HEAD), one sample 1.5 s in ---\n'
if [ -z "$out" ]; then printf '(the console is empty)\n'; else printf '%s\n' "$out"; fi
red=0
( assert_contains "$out" "gate: bash" "the suite is announced before it runs" ) || red=1
( assert_contains "$out" "case: a note is read" \
  "the suite's own first line reaches the console while the suite runs" ) || red=1
printf 'the base reading: %s\n' "$([ "$red" -eq 0 ] && echo 'would pass' || echo 'RED — the sample holds no stream at all')"
wait "$pid" || fail "the gate failed"

# ---- run 2: the fixed reading — wait for the suite's own first line, within the bound --------------------
d2=$(scratch); p2=$(setup "$d2"); base2=$(cat "$d2/base"); slow2=$(slow_host "$d2/bin")
log2="$d2/gate.log"
( cd "$p2" && PATH="$slow2:$PATH" LOOP_CONF="$d2/gate.conf" bash "$LOOP_ENGINE/gate.sh" --base "$base2" ) > "$log2" 2>&1 &
pid2=$!
out2=""
for _ in $(seq 600); do
  out2=$(cat "$log2")
  case "$out2" in *'case: a note is read'*) break ;; esac
  sleep 0.05
done
printf '\n--- run 2: the fixed reading, waiting for the suite''s own first line ---\n'
printf '%s\n' "$out2"
green=0
( assert_contains "$out2" "gate: bash" "the suite is announced before it runs" ) || green=1
( assert_contains "$out2" "case: a note is read" \
  "the suite's own first line reaches the console while the suite runs" ) || green=1
( assert_not_contains "$out2" "notes: all 1 cases passed" \
  "and the summary it has not printed yet does not: the output is streamed, not replayed" ) || green=1
printf 'the fixed reading: %s\n' "$([ "$green" -eq 0 ] && echo 'green on the same slow host' || echo 'RED')"
wait "$pid2" || fail "the gate failed"

# ---- run 3: a gate that replays — the fixed reading must refuse it ---------------------------------------
d3=$(scratch); p3=$(setup "$d3"); base3=$(cat "$d3/base")
# The copy is of the whole `tools` tree, not of `tools/loop`: `lib.sh` sources `../issues/lib.sh`, and an engine
# beside no tooling dies in its own first line instead of reaching the suite.
mkdir -p "$d3/re"
cp -r "$(dirname "$LOOP_ENGINE")" "$d3/re/tools"
perl -0777 -i -pe 's{    \$cmd 2>&1 \| tee "\$stream"\n    rc=\$\{PIPESTATUS\[0\]\}\n}{    out=\$(\$cmd 2>&1); printf "%s\\n" "\$out"\n    rc=\$?\n    printf "%s\\n" "\$out" > "\$stream"\n}' "$d3/re/tools/loop/gate.sh"
grep -q 'printf "%s' "$d3/re/tools/loop/gate.sh" || fail "the replay probe could not rewrite the gate's stream"
log3="$d3/gate.log"
( cd "$p3" && LOOP_CONF="$d3/gate.conf" bash "$d3/re/tools/loop/gate.sh" --base "$base3" ) > "$log3" 2>&1 &
pid3=$!
out3=""
for _ in $(seq 600); do
  out3=$(cat "$log3")
  case "$out3" in *'case: a note is read'*) break ;; esac
  sleep 0.05
done
printf '\n--- run 3: a gate whose output is captured and replayed, read by the fixed form ---\n'
printf '%s\n' "$out3"
replayred=0
( assert_contains "$out3" "case: a note is read" \
  "the suite's own first line reaches the console while the suite runs" ) || replayred=1
( assert_not_contains "$out3" "notes: all 1 cases passed" \
  "and the summary it has not printed yet does not: the output is streamed, not replayed" ) || replayred=1
printf 'the replayed gate: %s\n' "$([ "$replayred" -eq 0 ] && echo 'would pass — the guard is SOFT' || echo 'RED — the fixed reading still refuses a replay')"
wait "$pid3" || fail "the gate failed"

rm -rf "$LOOP_TMP"
