#!/usr/bin/env bash
# gate.sh — the gate a change owes is the one its own pathspec asks for, not one command for every item.
#
# `guidelines/Build.md` §2 already states the rule ("verification follows the changed files"), and the pipeline
# enforces more than the loop's gate did: it runs the shell suites beside the build's gate, while the loop's gate
# was the build's alone, so a landing could be green in the run and red in the pipeline. It was, on 2026-10-21
# (issue #242): a merge passed the build's gate and broke a shell suite, and the red main was found by the pipeline
# instead of by the run.
#
# This half is the engine's, and it names no project fact of its own — the loop's own case for that rule forbids
# it — so the mapping comes from the profile:
#
#   LOOP_GATE_COMPILED_CMD       the build's own gate, run only when a compiled path changed
#   LOOP_GATE_COMPILED_PATTERNS  the globs that make a path compiled
#   LOOP_GATE_SUITES             `<glob>|<command>` pairs, `;`-separated: the suites a path pulls in
#
# Every suite that ran contributes the numbers of its own summary line to one record, and its own wall clock
# beside them: the summary line names each suite's seconds, and the tally carries the same number as its `time`
# attribute, so the gate reports what an iteration cost instead of leaving it to a stopwatch and a `ps`.
# Each writes a JUnit-shaped tally under the tally directory, so the run's own check — "a green claim has a test
# result in the tree" — reads what actually executed. The tally is derived from the suite's summary, never
# invented.
#
# Exit: 0 everything that ran passed (or nothing was owed); 1 something failed, and the line names which; 2 usage
# or a base this tree cannot resolve.
#
# Usage: gate.sh [--base <sha>] [--plan] [--quiet]
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/loop/lib.sh
source "$here/lib.sh"

base=""
plan=0
quiet=0
while [ $# -gt 0 ]; do
  case "$1" in
    --base) need_value "$@"; base="${2:-}"; shift 2 ;;
    --plan) plan=1; shift ;;
    --quiet) quiet=1; shift ;;
    -h|--help) sed -n '2,24p' "$0"; exit 0 ;;
    *) loop_die "gate: unknown argument $1" ;;
  esac
done

loop_resolve

compiled_cmd=$(loop_conf_value LOOP_GATE_COMPILED_CMD)
compiled_patterns=$(loop_conf_value LOOP_GATE_COMPILED_PATTERNS)
suites=$(loop_conf_value LOOP_GATE_SUITES)
tally_root=$(loop_conf_value LOOP_GATE_TALLY_DIR)

# A step is bounded, and the profile owns the bound (issue #311): the run bounds a child with
# `LOOP_CHILD_MAX_SECONDS` and the suite bounds one case with `LOOP_CASE_MAX_SECONDS`, but inside the gate nothing
# answered a step, so one suite that never returned held a worker for its whole wall-clock — measured 2026-10-25, the
# run ended that child while its gate log already read `LOOP-GATE: tests=558 failures=0 errors=0` and its evidence
# check already read PASS, and the item was recorded `blocked` with its work uncommitted. The bound is a recorded
# value like the other two, it is read here, and `run_step` enforces it.
step_max=$(loop_conf_value LOOP_GATE_STEP_MAX_SECONDS)
case "$step_max" in ''|*[!0-9]*|0) step_max=1800 ;; esac
# …and the bound is the smaller of that and the ceiling of whatever started this gate (issue #692): the harness a
# child runs under ends one tool call at a ceiling of its own (900 s on the measured host, twice on 2026-10-28), and a
# step bound above it is not a bound — the caller ends the gate first, mid-step, and neither the console line nor the
# tally's `LOOP-GATE-STEP:` line that names the step is ever written. The clock in force is printed below, before the
# first step starts, so a log that ends without a verdict still says which one it was under.
step_recorded=$step_max
step_max=$(loop_bound_under_ceiling "$step_max")

say() { [ "$quiet" -eq 1 ] || printf '%s\n' "$*"; }

# A step's output is followed in its file, not carried on a pipe (issue #315). `$cmd 2>&1 | tee "$stream"` reads to EOF,
# and EOF arrives only when *every* writer that inherited the write end has let go of it — so a suite that leaves a
# watcher behind (the per-case bound at `tools/loop-selftest.sh` did) spent the step's wall clock on that watcher's
# life: measured 2026-10-26, a `loop-selftest.sh` step printed its verdict 565 s in and its own line read `— 1237s`,
# ~670 s of gate after the verdict, ending a supervised gate that had capped the call at 900 s. The follower below
# reads what the file has gained since its last pass — byte for byte, `tail -c +N` — so a partially written line is
# printed as it is written and the console sees the suite's output while the suite runs (`issue #269`, the behaviour
# this keeps). It ends once `<stop>` exists *and* the file has been drained, so the last line of a suite's output is
# on the console before the gate moves on.
follow_stream() { # <file> <stop file> → the file's new bytes, as they arrive, until the stop is written
  local file=$1 stop=$2 have=0 size final=0
  while :; do
    size=$(wc -c < "$file" 2>/dev/null || printf '0')
    size=${size//[!0-9]/}
    if [ "${size:-0}" -gt "$have" ]; then
      tail -c +"$((have + 1))" "$file"
      have=$size
      # One more pass before believing the file is drained: the stop is written when the command has ended, and a
      # pass that started before the last write would otherwise end the follower on the bytes it had already read.
      continue
    fi
    [ "$final" -eq 1 ] && break
    if [ -e "$stop" ]; then final=1; continue; fi
    sleep 0.1
  done
}

# The profile this gate read is the rule its numbers attest, so the log names it (issue #277): the gate of a change
# whose delta edits `loop.conf` must read the profile of the tree it is measuring, and a profile read from outside
# that tree is named as pinned, beside the one this engine ships, instead of leaving a reader to recognise it by its
# numbers. `--plan` prints the commands a delta owes and nothing else — `measure.sh` reads them as that list — so the
# pin is written only when the gate is going to run something.
profile_beside_engine() { # the resolved profile is the one this engine ships
  local resolved engine
  resolved=$(cd "$(dirname "$LOOP_CONF_PATH")" 2>/dev/null && pwd -P) || return 1
  engine=$(cd "$here" 2>/dev/null && pwd -P) || return 1
  [ "$resolved/$(basename "$LOOP_CONF_PATH")" = "$engine/loop.conf" ]
}
if [ "$plan" -eq 0 ]; then
  if profile_beside_engine; then
    say "LOOP-GATE-PROFILE: $LOOP_CONF_PATH"
  else
    say "LOOP-GATE-PROFILE: $LOOP_CONF_PATH — pinned; not the profile beside this engine ($here/loop.conf)"
  fi
  # The clock a step is held to is on the console before the first step starts, so a log that ends without a verdict
  # still says which bound was in force (issue #311) — and when the caller's ceiling is the smaller of the two, the
  # line names it as such rather than reading as a project value the profile never recorded (issue #692).
  if [ "$step_max" -lt "$step_recorded" ]; then
    say "LOOP-GATE-STEP-BOUND: ${step_max}s per step — a step that reaches it is ended, named and counted as an error; the ceiling the caller named is smaller than the recorded ${step_recorded}s"
  else
    say "LOOP-GATE-STEP-BOUND: ${step_max}s per step — a step that reaches it is ended, named and counted as an error"
  fi
fi

# The paths the change touches: what it committed since the base, plus whatever is still uncommitted — a gate
# run before the run's own commit measures the same work. A base git cannot answer is named, never treated as
# "no change": a range that cannot be resolved is the one thing a gate must not read as green.
#
# The same base is what a suite can narrow itself with: a profile's command may carry the token `{base}`, which is
# replaced by this base before the command runs, so the self-test of `tools/loop/**` owes the cases the delta's own
# files declare instead of the whole set (issue #276 — a two-file delta paid 723 s). The token is the engine's, not a
# project fact: `gate.sh` does not know which suite narrows itself, only that a suite may ask for the base it measured.
if [ -z "$base" ]; then
  base=$(git merge-base HEAD origin/main 2>/dev/null || git rev-parse --verify --quiet 'HEAD~1' 2>/dev/null || true)
fi
[ -n "$base" ] || loop_die "gate: cannot resolve a base to compare against — pass --base <sha>"
git rev-parse --verify --quiet "$base" >/dev/null || loop_die "gate: '$base' is not a commit this tree knows"
paths=$( { git diff --name-only "$base" 2>/dev/null; git status --porcelain 2>/dev/null | sed 's/^...//'; } | sort -u)

# A pattern is data, and only the second argument is a path: unquoted, `for glob in $1` lets the shell expand the
# profile's *pattern list* against the directory the gate happens to run in, so a pattern that names a tree is
# replaced by the names it matched and then compared literally. Measured 2026-10-23 (issue #273): with the profile's
# own resource-tree pattern and a new file under that directory, the plan owed nothing, and with `set -f` here it
# owed the build — which is why two anchored patterns the profile already declared were dead weight.
matches() { # <space-separated globs> <path>
  local glob was_f=0
  case $- in *f*) was_f=1 ;; *) set -f ;; esac
  for glob in $1; do
    # shellcheck disable=SC2254 # the glob is the point: a pattern, not a literal
    case "$2" in $glob) [ "$was_f" -eq 1 ] || set +f; return 0 ;; esac
  done
  [ "$was_f" -eq 1 ] || set +f
  return 1
}

owed=""
if [ -n "$compiled_patterns" ] && [ -n "$compiled_cmd" ]; then
  while IFS= read -r p; do
    [ -n "$p" ] || continue
    if matches "$compiled_patterns" "$p"; then owed="$owed${compiled_cmd//\{base\}/$base}"$'\n'; break; fi
  done <<< "$paths"
fi
if [ -n "$suites" ]; then
  while IFS='|' read -r glob cmd; do
    [ -n "${glob:-}" ] && [ -n "${cmd:-}" ] || continue
    cmd=${cmd//\{base\}/$base}
    while IFS= read -r p; do
      [ -n "$p" ] || continue
      # shellcheck disable=SC2254 # the same: a pattern from the profile
      case "$p" in $glob) owed="$owed$cmd"$'\n' ;; esac
    done <<< "$paths"
  # The trailing newline is not cosmetic: `read` returns non-zero on a last line it did not delimit, so a
  # value printed without one loses its final pair — the last glob the profile names was never consulted
  # (measured 2026-10-21, issue #246: an 18-pair value iterated 17 times, a 2-pair value once).
  done < <(printf '%s\n' "$suites" | tr ';' '\n')
fi
owed=$(printf '%s' "$owed" | sed '/^$/d' | awk '!seen[$0]++')

if [ -z "$owed" ]; then
  say "LOOP-GATE: nothing owed — no path this profile gates changed since $base"
  exit 0
fi
if [ "$plan" -eq 1 ]; then
  printf '%s\n' "$owed"
  exit 0
fi

cases=0
failures=0
errors=0
failed_suites=""
ran_entries=""
# A suite's output is streamed, not replayed: the console reads the file the suite writes as the suite writes it, and
# that one file is what the summary is parsed from — one observation feeds the log and the tally. Measured 2026-10-23
# (issue #269): `out=$($cmd 2>&1)` held the whole output until the command exited, so a gate that ran a suite for
# eleven minutes carried two lines, and neither an operator nor a worker could tell a long suite from a stopped one,
# nor see which case a failure was in.
stream=$(mktemp "${TMPDIR:-/tmp}/loop-gate-output.XXXXXX") \
  || loop_die "gate: cannot create the file that carries a suite's output"
# The stop is a second file beside the stream: written once a step's command has ended, it is what tells the follower
# that the file will gain no more bytes.
follow_stop="$stream.stop"
# …and a step's own completion record (issue #311): the status is written by the step itself, because a `wait` on a pid
# the shell has already reaped answers nothing — the lesson the run's own child wait records (issue #227) — and the
# bound below needs the same event.
status_file="$stream.status"
trap 'rm -f "$stream" "$follow_stop" "$status_file"' EXIT
# One step: the command's output into the file, the console reading that file as it grows, the command's own status,
# and its own bound. The step ends with the command it started — a writer that outlives it holds the file open, which
# holds nothing else (issue #315) — and with the bound, which ends the step's process group when the command has not
# returned in `LOOP_GATE_STEP_MAX_SECONDS` (issue #311). `step_overran` is the one fact the caller needs beside the
# status, and it is set here because the status alone cannot say it: a command the bound ended never wrote one.
step_overran=0
run_step() { # <command> → the command's own status; 124 when the bound ended the step
  local cmd=$1 step follow rc started
  step_overran=0
  rm -f "$status_file"
  started=$SECONDS
  # `set -m` on the one command that starts the step, immediately undone: a background job of a shell with job
  # control is its own process group, which is what gives the bound a handle on everything the step started — a suite
  # leaves its own watchers and workers behind, and those are the processes that held the gate open.
  set -m
  ( $cmd; printf '%s\n' "$?" > "$status_file" ) >"$stream" 2>&1 &
  step=$!
  set +m
  follow=""
  # `--quiet` keeps the console for the verdict, but the file is written either way: the tally is read from it.
  if [ "$quiet" -eq 0 ]; then
    follow_stream "$stream" "$follow_stop" &
    follow=$!
  fi
  # The bound is a whole-second clock like the run's own two, read once per look against `SECONDS`, the shell's own
  # integer counter: a `date` per 0.1 s look would spend on forks what the granularity buys back (issue #259).
  while [ ! -e "$status_file" ]; do
    if [ $((SECONDS - started)) -ge "$step_max" ]; then
      step_overran=1
      loop_end_group "$step"
      break
    fi
    sleep 0.1
  done
  if [ "$step_overran" -eq 1 ]; then
    rc=124
  else
    rc=$(cat "$status_file" 2>/dev/null || printf '1')
    case "$rc" in ''|*[!0-9]*) rc=1 ;; esac
  fi
  # Nothing is left to a bare `wait`, the same rule the run's own cap follows: a step still standing after its own
  # record, or after the bound ended its group, is ended by pid.
  if kill -0 "$step" 2>/dev/null; then loop_end_pid "$step"; fi
  : > "$follow_stop"
  [ -z "$follow" ] || wait "$follow" 2>/dev/null
  rm -f "$follow_stop"
  return "$rc"
}
while IFS= read -r cmd; do
  [ -n "$cmd" ] || continue
  say "gate: $cmd"
  # A suite's wall clock is recorded, not guessed: without it neither an operator nor a worker can tell a suite
  # that costs a quarter of an hour from one that costs three seconds, and an iteration that has to be made
  # cheaper cannot say which suite it is paying for. `SECONDS` is the shell's own counter, integer and monotonic
  # enough for a wall clock of this size.
  t0=$SECONDS
  # The status is the suite's own, read from the process the step started rather than from a pipeline: `pipefail`
  # answered that for the last command of a pipeline, and this reads it directly (issue #315).
  run_step "$cmd"
  rc=$?
  # A step the bound answered names itself, in the stream the tally and the console are both read from (issue #311):
  # "the gate never returned" cannot say which suite overran, and the tally's last lines are where a reader looks.
  if [ "$step_overran" -eq 1 ]; then
    printf 'LOOP-GATE-STEP: %s exceeded %s s — the gate ended it and everything it started\n' "$cmd" "$step_max" >> "$stream"
  fi
  out=$(cat "$stream")
  took=$((SECONDS - t0))
  if [ "$step_overran" -eq 1 ]; then
    say "gate: $cmd — ${took}s — the step overran its bound of ${step_max}s and was ended"
  else
    say "gate: $cmd — ${took}s"
  fi
  ran_entries="${ran_entries}${ran_entries:+, }$cmd ${took}s"
  [ "$rc" -eq 0 ] || failed_suites="$failed_suites $cmd"
  # The suite's own summary line: `<name>: all N cases passed` or `<name>: M of N cases failed`.
  n=$(printf '%s\n' "$out" | sed -n 's/.*all \([0-9][0-9]*\) cases passed.*/\1/p' | tail -1)
  if [ -z "$n" ]; then
    m=$(printf '%s\n' "$out" | sed -n 's/.*: \([0-9][0-9]*\) of \([0-9][0-9]*\) cases failed.*/\2/p' | tail -1)
    n="${m:-}"
  fi
  case "$n" in ''|*[!0-9]*) n=0 ;; esac
  f=0
  [ "$rc" -eq 0 ] || f=$(printf '%s\n' "$out" | sed -n 's/.*: \([0-9][0-9]*\) of [0-9][0-9]* cases failed.*/\1/p' | tail -1)
  case "$f" in ''|*[!0-9]*) f=0 ;; esac
  # A failed suite's *absence of a summary* is not a failing case: `n` and `f` stay the suite's own numbers, and the
  # failure is kept apart as an error. Measured 2026-10-23 (issue #268): a compiled step that died at configuration
  # (`BUILD FAILED in 21s`, no test task reached its action) was tallied `tests="1" failures="1"`, because the
  # former `n=1`/`f=1` fallbacks stated a case and a failure the suite never reported — a tally meant to prove what
  # ran became evidence that something did.
  err=0
  [ "$rc" -eq 0 ] || [ "$f" -gt 0 ] || err=1
  cases=$((cases + n))
  failures=$((failures + f))
  errors=$((errors + err))
  # One tally per suite, under the directory the profile names (`*test-results*`, so the run's own check finds
  # it) and only when it names one: the engine may hold no project path, and the portability check is what says so.
  if [ -n "$tally_root" ]; then
    mkdir -p "$tally_root" 2>/dev/null || true
    # The name is the command, and a tally's name is never a dotfile: the profile's compiled command is the `./`-form
    # a program in the project root needs, and that leading `.` survived sanitising into `._<name>.xml`, so a plain
    # `ls` printed five files where the directory held six and the pattern a reader globs missed the one tally that
    # records the build — the round-3 reviewer of #273 read that copy as short and refused a round on a tally that
    # was present (measured 2026-10-23, issue #278).
    name=$(printf '%s' "${cmd#./}" | tr '/ ' '__' | cut -c1-80)
    printf '<?xml version="1.0" encoding="UTF-8"?>\n<testsuite name="%s" tests="%s" failures="%s" errors="%s" time="%s">\n  <system-out>%s</system-out>\n</testsuite>\n' \
      "$cmd" "$n" "$f" "$err" "$took" \
      "$(printf '%s' "$out" | tail -3 | tr '\n' ' ' | cut -c1-400)" \
      > "$tally_root/$name.xml" 2>/dev/null || true
  fi
done <<< "$owed"

if [ -n "$failed_suites" ]; then
  printf 'LOOP-GATE: tests=%s failures=%s errors=%s (%s) — failing:%s\n' "$cases" "$failures" "$errors" "$ran_entries" "$failed_suites"
  exit 1
fi
printf 'LOOP-GATE: tests=%s failures=%s errors=%s (%s)\n' "$cases" "$failures" "$errors" "$ran_entries"
exit 0
