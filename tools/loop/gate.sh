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
#   LOOP_GATE_FIRST_STEPS        the same shape, read before the build and before every suite: a step that
#                                refuses a delta in seconds is not left to the end of the gate
#   LOOP_GATE_POOL_JOBS          how many of the suites that follow may be in flight at once: the first steps and
#                                the build run alone, and `1` is the serial gate (issue #801)
#
# Every suite that ran contributes the numbers of its own summary line to one record, and its own wall clock
# beside them: the summary line names each suite's seconds, and the tally carries the same number as its `time`
# attribute, so the gate reports what an iteration cost instead of leaving it to a stopwatch and a `ps`.
# Each writes a JUnit-shaped tally under the tally directory, so the run's own check — "a green claim has a test
# result in the tree" — reads what actually executed. The tally is derived from the suite's summary, never
# invented.
#
# Exit: 0 everything that ran passed (or nothing was owed); 1 something failed, and the line names which; 2 usage
# or a base this tree cannot resolve; 3 this child has already spent the full runs the profile records, so this one
# was refused and measured nothing (neither a red nor a green); 4 another gate of this tree holds the tally
# directory, so this one was refused before it wrote a tally and measured nothing either.
#
# A gate is a measurement, and a measurement is taken once: see `runs_max` below and the refusal it arms.
#
# Usage: gate.sh [--base <sha>] [--plan] [--quiet]
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# What this run was asked to do, for the record it leaves behind (issue #756). The parser below shifts the arguments
# away, so the line is taken here: the engine names its own script and never a project's gate command (D16,
# `tools/check-portability.sh`), and the arguments are what tells a reader which run it was.
gate_argv="${BASH_SOURCE[0]##*/}${*:+ $*}"
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
    -h|--help) sed -n '2,25p' "$0"; exit 0 ;;
    *) loop_die "gate: unknown argument $1" ;;
  esac
done

loop_resolve

# The environment a kept worktree carries is the gate's own, not only its children's (issue #267). `worktree.sh`
# writes the machine-local values into `<worktree>/.pi/loop-env.sh` — a file rather than a `vcpkg` symlink, because a
# symlink is untracked and pollutes the `git status` the run reads for evidence — and until this line existed only
# `run.sh` sourced it for the children it starts. A gate typed by hand in that tree therefore ran without the
# dependency root and failed at configuration, in 21 s, having executed no test (a Gradle task could not even be
# created: `vcpkg installed-status file not found`): a red indistinguishable from a real one, and faster than a
# working gate, which is the trap. The file is read from the tree git resolves, not from the cwd, so a gate invoked
# from a subdirectory of that tree still gets it; a tree that carries no such file gates exactly as before.
# Sourcing it twice (a run's child already inherits it) is harmless: the file assigns, it never appends.
if [ -f "$LOOP_ROOT/.pi/loop-env.sh" ]; then
  # shellcheck source=/dev/null
  . "$LOOP_ROOT/.pi/loop-env.sh"
fi

compiled_cmd=$(loop_conf_value LOOP_GATE_COMPILED_CMD)
compiled_patterns=$(loop_conf_value LOOP_GATE_COMPILED_PATTERNS)
first_steps=$(loop_conf_value LOOP_GATE_FIRST_STEPS)
suites=$(loop_conf_value LOOP_GATE_SUITES)
tally_root=$(loop_conf_value LOOP_GATE_TALLY_DIR)

# A step is bounded, and the profile owns the bound (issue #311): the run bounds a child with
# `LOOP_CHILD_MAX_SECONDS` and the suite bounds one case with `LOOP_CASE_MAX_SECONDS`, but inside the gate nothing
# answered a step, so one suite that never returned held a worker for its whole wall-clock — measured 2026-10-25, the
# run ended that child while its gate log already read `LOOP-GATE: tests=558 failures=0 errors=0` and its evidence
# check already read PASS, and the item was recorded `blocked` with its work uncommitted. The bound is a recorded
# value like the other two, it is read here, and the wave below enforces it per step (issue #801).
step_max=$(loop_conf_value LOOP_GATE_STEP_MAX_SECONDS)
case "$step_max" in ''|*[!0-9]*|0) step_max=1800 ;; esac
# …and the bound is the smaller of that and the ceiling of whatever started this gate (issue #692): the harness a
# child runs under ends one tool call at a ceiling of its own (900 s on the measured host, twice on 2026-10-28), and a
# step bound above it is not a bound — the caller ends the gate first, mid-step, and neither the console line nor the
# tally's `LOOP-GATE-STEP:` line that names the step is ever written. The clock in force is printed below, before the
# first step starts, so a log that ends without a verdict still says which one it was under.
step_recorded=$step_max
step_max=$(loop_bound_under_ceiling "$step_max")
# How many steps one wave may carry (issue #801). A change in this project owes the build and four shell suites, and
# the four share nothing but the read-only engine — each resolves a scratch tree of its own and writes a tally of its
# own — so they were spent one after another for no reason the profile stated. The pool is a recorded value like the
# bound beside it, because which suites a host may run at once is the host's fact and not this engine's: `1` runs
# them exactly as the serial gate did.
pool_jobs=$(loop_conf_value LOOP_GATE_POOL_JOBS)
case "$pool_jobs" in ''|*[!0-9]*|0) pool_jobs=4 ;; esac

# How many *full* runs of this gate the child that started it may spend (issue #756). The gate is the loop's only
# expensive action that a child runs rather than the run, so the run's one bound on a child — `child_turns`, which
# counts `turn_end` lines — cannot see it: measured 2026-11-03 over the archive's 227 items and 558 child sessions,
# the gate was started **910 times and spent 35.1 h** (60 % of all tool wall clock), and **117 of the 245 sessions
# that ran it at all — 48 % — ran it three or more times**. A second *full* round repeats the suites the first one
# already passed, and each round is 10–19 minutes of the item's end. Unrecorded or `0` is no bound, the rule every
# other bound in the profile follows — the engine names no project value of its own (D16).
runs_max=$(loop_conf_value LOOP_GATE_RUNS_MAX)
case "$runs_max" in ''|*[!0-9]*) runs_max=0 ;; esac

# A run leaves its own record, and the record is what the count is read from. Reading the child's event stream instead
# was the first shape of this rule and it does not hold: that stream carries every command the child ran, and a command
# that *mentions* this file — a `grep` over it, a `sed` of it, a read of it — cannot be told from one that runs it.
# What the stream is needed for is the child's identity: the stream carries one `session` line per child, and a record
# line that carries another session's id was another child's run — so each child of an item starts at zero, which is
# what the run's own phase split needs.
runs_record=""
session_key=""
session_key_of() { # session_key_of <events file> → the id of the last `session` line, or nothing
  local f=${1:-} line
  [ -n "$f" ] && [ -f "$f" ] || return 0
  line=$(grep -E '"type": *"session"' "$f" 2>/dev/null | tail -1)
  [ -n "$line" ] || return 0
  printf '%s' "$line" | jq -r '.id // empty' 2>/dev/null
}

# The gate's own log, beside its tallies (issue #853). A red gate is attributed from what it printed — its step
# blocks, its `FAIL <group>#<name>` lines and its verdict — and `tools/gate-attribute.sh` reads exactly that text, so
# the gate writes it rather than leaving a reader to the child's console: the file is the one source of the failing
# case and of the rerun command derived from the failing step's own line. It is written whether or not `--quiet`
# suppressed the console, and it is named once, where the tally directory is claimed.
gate_log=""
log_line() { [ -z "$gate_log" ] || printf '%s\n' "$*" >> "$gate_log" 2>/dev/null || true; }

say() { log_line "$*"; [ "$quiet" -eq 1 ] || printf '%s\n' "$*"; }

# A step's output is followed in its file, not carried on a pipe (issue #315). `$cmd 2>&1 | tee "$stream"` reads to EOF,
# and EOF arrives only when *every* writer that inherited the write end has let go of it — so a suite that leaves a
# watcher behind (the per-case bound at `tools/loop/tests/loop-selftest.sh` did) spent the step's wall clock on that watcher's
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
  # The pool is named for the same reason the bound is: a log that ends without a verdict still says which clock and
  # which wave were in force, and a pooled step that contends on a host is one key away from the serial gate.
  say "LOOP-GATE-POOL: at most ${pool_jobs} suite step(s) in flight at once — the first steps and the build run alone"
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
# The tree this run measures, named before the first step and written into the run's own record (issue #819): the
# commit it was taken on and a digest of the change itself, so a tree corrected after the gate is a record a reader
# can set apart from the tree the gate saw. `run.sh` derives the same identity for the diff a round is handed, through
# the same `loop_change_identity`, so the reviewer compares two recorded values instead of inferring one — and a
# `--plan` call measures nothing, so it names no tree and prints only the commands the delta owes.
tree_identity=""
if [ "$plan" -eq 0 ]; then
  tree_identity=$(loop_change_identity "$LOOP_ROOT" "$base")
  [ -n "$tree_identity" ] || tree_identity="head=unknown diff=unknown"
  say "LOOP-GATE-TREE: $tree_identity"
fi

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
# A pair list is read one `<glob>|<command>` pair at a time, in the order the profile wrote it, so the order of the
# owed steps is a value of the profile and not of this engine. Each owed step also carries *which* list owed it —
# `first`, `compiled` or `suite` — because the three lists are not run the same way (issue #801): a first step refuses
# a delta in seconds and runs alone, the build owns the build directory the suites read through the environment and
# runs alone, and the suites share nothing but the read-only engine, so they run together.
owe_pairs() { # <kind> <";"-separated `<glob>|<command>` pairs> → every command this delta owes, appended in order
  local kind=$1 pairs=$2 glob cmd p
  [ -n "$pairs" ] || return 0
  while IFS='|' read -r glob cmd; do
    [ -n "${glob:-}" ] && [ -n "${cmd:-}" ] || continue
    cmd=${cmd//\{base\}/$base}
    while IFS= read -r p; do
      [ -n "$p" ] || continue
      # shellcheck disable=SC2254 # the same: a pattern from the profile
      case "$p" in $glob) owed="$owed$kind"$'\t'"$cmd"$'\n' ;; esac
    done <<< "$paths"
  # The trailing newline is not cosmetic: `read` returns non-zero on a last line it did not delimit, so a
  # value printed without one loses its final pair — the last glob the profile names was never consulted
  # (measured 2026-10-21, issue #246: an 18-pair value iterated 17 times, a 2-pair value once).
  done < <(printf '%s\n' "$pairs" | tr ';' '\n')
}

# A step that refuses a delta in seconds is owed before the steps that spend minutes: a delta that writes a case's
# `# files:` declaration is refused by the one case that reads it, and was refused only by the full suite twelve
# minutes into the gate (issue #792 — #790's attempt passed the 11 cases its own selection owed and died on a
# declaration one path token short). `LOOP_GATE_FIRST_STEPS` is read before the compiled step and before every suite,
# and it names no project fact here: which command is cheap, and which path owes it, are the profile's.
owe_pairs first "$first_steps"
if [ -n "$compiled_patterns" ] && [ -n "$compiled_cmd" ]; then
  while IFS= read -r p; do
    [ -n "$p" ] || continue
    if matches "$compiled_patterns" "$p"; then owed="$owed"$'compiled\t'"${compiled_cmd//\{base\}/$base}"$'\n'; break; fi
  done <<< "$paths"
fi
if [ -n "$suites" ]; then
  owe_pairs suite "$suites"
fi
# One step per command, and the first list that named it owns how it runs: a command two pairs owe is one step and not
# two, so the dedupe is on the command alone.
owed=$(printf '%s' "$owed" | sed '/^$/d' | awk -F'\t' '!seen[$2]++')

owed_kind=()
owed_cmd=()
while IFS=$'\t' read -r kind cmd; do
  [ -n "${cmd:-}" ] || continue
  owed_kind+=("$kind")
  owed_cmd+=("$cmd")
done <<< "$owed"

if [ "${#owed_cmd[@]}" -eq 0 ]; then
  say "LOOP-GATE: nothing owed — no path this profile gates changed since $base"
  exit 0
fi
if [ "$plan" -eq 1 ]; then
  printf '%s\n' "${owed_cmd[@]}"
  exit 0
fi

# The refusal: a child that has already spent the runs its profile records is handed the commands its delta owes
# instead of a further full round. `--plan`'s list is that command set, so the refusal prints what that call prints
# — a scoped step, not a whole gate — and writes no `LOOP-GATE:` line, because a refused run measured nothing and a
# caller reading the log has to be able to tell that from a green one. The record is written only when this run is
# going to measure something: a refusal and a `--plan` call spend nothing and leave the file alone, and a run that is
# counted is a run the child paid for whether or not its suites passed.
if [ "$runs_max" -gt 0 ] && [ -n "${LOOP_CHILD_EVENTS:-}" ]; then
  session_key=$(session_key_of "$LOOP_CHILD_EVENTS")
  if [ -n "$session_key" ]; then
    # Beside the stream, and named after it: the record belongs to the same directory the run keeps an item's stream in,
    # and it is one directory the run already owns and sweeps. A child's session is the only thing that separates one
    # child's runs from the next's, so a line without a session is another child's and is not counted.
    runs_record="$LOOP_CHILD_EVENTS.gate-runs"
    earlier=$(awk -F'\t' -v k="$session_key" '$1 == k { print $2 "\t" $3 }' "$runs_record" 2>/dev/null) || true
    count=$(printf '%s\n' "$earlier" | grep -c . || true)
    case "$count" in ''|*[!0-9]*) count=0 ;; esac
    if [ "$count" -ge "$runs_max" ]; then
      printf 'LOOP-GATE-REFUSED: %s full runs of this gate were already started in this child (LOOP_GATE_RUNS_MAX=%s) — a further full run is not a measurement:\n' \
        "$count" "$runs_max"
      printf '%s\n' "$earlier" | while IFS= read -r line; do
        [ -n "$line" ] || continue
        cmd=${line%%$'\t'*}
        tree=${line#*$'\t'}
        [ "$tree" = "$line" ] && tree=""
        printf '  earlier run: %s — tree: %s\n' "$cmd" "${tree:-unrecorded}"
      done
      printf 'run the command this delta owes instead:\n'
      printf '%s\n' "${owed_cmd[@]}" | sed 's/^/  scoped: /'
      exit 3
    fi
    printf '%s\t%s\t%s\n' "$session_key" "$gate_argv" "${tree_identity:-head=unknown diff=unknown}" >> "$runs_record" 2>/dev/null || true
  fi
fi

cases=0
failures=0
errors=0
failed_suites=""
ran_entries=""
# A suite's output is read from the file the suite writes as the suite writes it, and that one file is what the
# summary is parsed from — one observation feeds the log and the tally. Measured 2026-10-23 (issue #269):
# `out=$($cmd 2>&1)` held the whole output until the command exited, so a gate that ran a suite for eleven minutes
# carried two lines, and neither an operator nor a worker could tell a long suite from a stopped one, nor see which
# case a failure was in.
#
# That file is per step (issue #801): a step that shares its output with the step beside it shares the summary its
# tally is derived from, so each slot of a wave gets its own output, its own completion record and its own stop.
# A gate's tallies belong to the run that wrote them (issue #848). The tally directory is one path the profile names
# — one per tree, not one per run — and until this pair of halves existed nothing made it one run's: a step file an
# earlier run left survived the next one, and a reader copying the directory as the gate's evidence copied a step the
# gate never ran. Measured 2026-11-10 (item #830): the green gate's directory held a carrier reading
# `tests="1" failures="1" time="1800"` with the `LOOP-GATE-STEP:` overrun of a run that had been red for the
# self-test suite's own lock, and `tools_tests_check-portability-selftest.sh.xml` reading `tests="31" failures="4"`
# — neither step ran that way in the run being archived, and excluding them by mtime window took three attempts.
#
# The first half is the rule the self-test suite already applies to itself (`tools/loop/tests/loop-selftest.sh`,
# "one suite per repository", issue #295), applied to the gate as a whole: the run holds the directory's lock for its
# whole life, so a second gate of this tree refuses by name before it runs a step. The second half is the sweep: a
# run clears every carrier the directory still holds before its first step writes one, so a run that crashed, or the
# run before, cannot be read as this one's. Each tally then carries the run that wrote it, and the console names the
# directory and that run, so a copy made mid-run is told apart from another run's without its mtime.
tally_lock=""
tally_owner="$$ $(date -u +%Y-%m-%dT%H:%M:%SZ)"
# The lock this gate holds is released, never left for the next run to find dead; only the record this gate wrote is
# removed, so a lock that changed hands is somebody else's and stays.
gate_release_tally() {
  [ -n "$tally_lock" ] || return 0
  [ "$(cat "$tally_lock/owner" 2>/dev/null)" = "$tally_owner" ] && rm -rf "$tally_lock" 2>/dev/null
  tally_lock=""
}
gate_take_tally() { # → the directory is this run's, or this gate has refused and measured nothing
  local holder holder_pid attempt
  [ -n "$tally_root" ] || return 0
  tally_lock="$tally_root/.gate.lock"
  mkdir -p "$tally_root" 2>/dev/null || true
  if ! mkdir "$tally_lock" 2>/dev/null; then
    holder=""
    for attempt in 1 2 3 4 5 6 7 8 9 10; do
      holder=$(cat "$tally_lock/owner" 2>/dev/null || printf '')
      [ -n "$holder" ] && break
      sleep 0.1
    done
    holder_pid=${holder%% *}
    case "$holder_pid" in ''|*[!0-9]*) holder_pid=0 ;; esac
    if [ "$holder_pid" -gt 0 ] && kill -0 "$holder_pid" 2>/dev/null; then
      printf 'LOOP-GATE-REFUSED: another gate of this tree holds %s (owner %s) — one gate per tree, because two gates would write one tally directory and a step of one would be read as the other run (issue #848)\n' \
        "$tally_lock" "$holder"
      printf 'wait for it, run the gate in another worktree, or remove %s if that process is not a gate\n' "$tally_lock"
      tally_lock=""
      exit 4
    fi
    # A gate ended by a signal no trap can catch leaves its lock behind, and a lock whose owner is gone must not
    # refuse every run that follows it: the next one takes it over and says whose it was. Only the record this run
    # read is removed, and the `mkdir` below decides between two runs that took the same dead lock over at once.
    if [ "$(cat "$tally_lock/owner" 2>/dev/null)" != "$holder" ]; then
      printf 'LOOP-GATE-REFUSED: the lock %s changed hands while this gate read it — one gate per tree\n' "$tally_lock"
      tally_lock=""
      exit 4
    fi
    rm -rf "$tally_lock" 2>/dev/null || true
    mkdir "$tally_lock" 2>/dev/null || {
      printf 'LOOP-GATE-REFUSED: another gate took %s while this one read it — one gate per tree\n' "$tally_lock"
      tally_lock=""
      exit 4
    }
    say "LOOP-GATE-TALLY: took over the lock $tally_lock: its owner ${holder:-an unknown process} is not running any more"
  fi
  if ! printf '%s\n' "$tally_owner" > "$tally_lock/owner" 2>/dev/null; then
    # A lock that cannot say who holds it cannot be released by its holder, and leaving it behind would refuse the
    # next run as a dead owner's: remove what this run just made and refuse.
    rm -rf "$tally_lock" 2>/dev/null || true
    tally_lock=""
    printf 'LOOP-GATE-REFUSED: cannot write the owner of %s — no lock is held\n' "$tally_root"
    exit 4
  fi
  # Every carrier the directory still holds is an earlier run's — a run that crashed, or the one before this.
  rm -f "$tally_root"/*.xml 2>/dev/null || true
  # …and the log this gate writes is this run's too, so a red gate is never read through the previous run's failures.
  gate_log="$tally_root/gate.log"
  : > "$gate_log" 2>/dev/null || gate_log=""
  say "LOOP-GATE-TALLY: $tally_root run=${tally_owner%% *}"
  [ -z "$gate_log" ] || say "LOOP-GATE-LOG: $gate_log"
}

gate_tmp=$(mktemp -d "${TMPDIR:-/tmp}/loop-gate-steps.XXXXXX") \
  || loop_die "gate: cannot create the directory that carries the steps' output"
trap 'gate_release_tally; rm -rf "$gate_tmp"' EXIT
# The directory is claimed before any step writes a tally into it, and after the refusals above: a gate that owes
# nothing, prints a plan or has spent its runs measures nothing and leaves no lock behind.
gate_take_tally

# One slot per running step: its command, its process, its output, its completion record, its clock, and the two facts
# a reader needs beside the status — whether the bound ended it, and whether its bytes already reached the console.
st_cmd=(); st_pid=(); st_stream=(); st_status=(); st_started=(); st_ended=(); st_overran=(); st_announced=(); st_followed=()

start_step() { # <slot> <command> → the step is running, its own files written and its own clock started
  local i=$1 cmd=$2 stream status_file
  st_cmd[i]=$cmd
  stream="$gate_tmp/step-$i.out"
  status_file="$gate_tmp/step-$i.status"
  st_stream[i]=$stream
  st_status[i]=$status_file
  st_started[i]=$SECONDS
  st_ended[i]=0
  st_overran[i]=0
  st_announced[i]=0
  st_followed[i]=0
  rm -f "$status_file"
  # `set -m` on the one command that starts the step, immediately undone: a background job of a shell with job
  # control is its own process group, which is what gives the bound a handle on everything the step started — a suite
  # leaves its own watchers and workers behind, and those are the processes that held the gate open. Two steps of a
  # wave are two groups, so a step that reaches its bound is ended without ending its neighbours.
  set -m
  ( $cmd; printf '%s\n' "$?" > "$status_file" ) >"$stream" 2>&1 &
  st_pid[i]=$!
  set +m
}
# One wave: the steps are started together, each is ended at its own bound, and the wave is over when every slot has
# written its own record or been ended (issue #801). One poll loop over the slots rather than one loop per step keeps
# a wave of four at the cost of a wave of one.
wave_wait() { # <slot> … → every step has ended, or was ended at its own bound
  local slot stream follow_stop follow="" done_all
  # A wave of one keeps the follower (issue #269): one writer, so nothing can interleave with it and the console reads
  # the suite's lines while the suite runs. A wave of several does not stream: two writers on one console interleave
  # their bytes, and a failing case would belong to the step that happened to print next — the log's
  # `gate: <cmd>` … `— <n>s` block is what a reader, and `tools/gate-attribute.sh`, attribute a failure from. Those
  # steps are read from their own files, whole, under their own header, when the wave has drained.
  if [ "$#" -eq 1 ] && [ "$quiet" -eq 0 ]; then
    slot=$1
    stream=${st_stream[slot]}
    follow_stop="$stream.stop"
    follow_stream "$stream" "$follow_stop" &
    follow=$!
    st_followed[slot]=1
  fi
  # The bound is a whole-second clock like the run's own two, read once per look against `SECONDS`, the shell's own
  # integer counter: a `date` per 0.1 s look would spend on forks what the granularity buys back (issue #259).
  while :; do
    done_all=1
    for slot in "$@"; do
      # A step's own clock stops where the step ended, not where the wave drained (issue #801): a step that finished
      # first inside a slow wave cost what it cost, and a number that carried its neighbours' wait would be a cost
      # nobody spent. One look of the poll sets it.
      if [ -e "${st_status[slot]}" ]; then
        [ "${st_ended[slot]}" -ne 0 ] || st_ended[slot]=$SECONDS
        continue
      fi
      if [ "${st_overran[slot]}" -eq 1 ]; then
        [ "${st_ended[slot]}" -ne 0 ] || st_ended[slot]=$SECONDS
        continue
      fi
      if [ $((SECONDS - st_started[slot])) -ge "$step_max" ]; then
        st_overran[slot]=1
        st_ended[slot]=$SECONDS
        loop_end_group "${st_pid[slot]}"
        continue
      fi
      done_all=0
    done
    [ "$done_all" -eq 1 ] && break
    sleep 0.1
  done
  if [ -n "$follow" ]; then
    : > "$follow_stop"
    wait "$follow" 2>/dev/null
    rm -f "$follow_stop"
  fi
}

# One step's record: its console block, its summary, its tally and its contribution to the gate's totals, all read
# from the file this step wrote (issue #801). The wave has drained first, so the steps are recorded in the profile's
# order whatever order they ended in — which is what keeps a serial gate's log and its numbers the same bytes.
finish_step() { # <slot> → the step's block on the console and its numbers in the gate's totals
  local slot=$1 cmd stream status_file out n m f err rc took t0 name
  cmd=${st_cmd[slot]}
  stream=${st_stream[slot]}
  status_file=${st_status[slot]}
  # A step the bound answered names itself, in the stream the tally and the console are both read from (issue #311):
  # "the gate never returned" cannot say which suite overran, and the tally's last lines are where a reader looks.
  if [ "${st_overran[slot]}" -eq 1 ]; then
    printf 'LOOP-GATE-STEP: %s exceeded %s s — the gate ended it and everything it started\n' "$cmd" "$step_max" >> "$stream"
  fi
  # Nothing is left to a bare `wait`, the same rule the run's own cap follows: a step still standing after its own
  # record, or after the bound ended its group, is ended by pid.
  if kill -0 "${st_pid[slot]}" 2>/dev/null; then loop_end_pid "${st_pid[slot]}"; fi
  if [ "${st_overran[slot]}" -eq 1 ]; then
    rc=124
  else
    rc=$(cat "$status_file" 2>/dev/null || printf '1')
    case "$rc" in ''|*[!0-9]*) rc=1 ;; esac
  fi
  out=$(cat "$stream")
  t0=${st_started[slot]}
  took=$((st_ended[slot] - t0))
  [ "$took" -ge 0 ] || took=0
  # A step of a wave prints its block whole and header first, so its output is its own between its own header and its
  # own duration line — the shape `tools/gate-attribute.sh` reads a failure and its step from.
  if [ "${st_announced[slot]}" -eq 0 ]; then say "gate: $cmd"; fi
  if [ -n "$out" ]; then
    log_line "$out"
    if [ "${st_followed[slot]}" -eq 0 ] && [ "$quiet" -eq 0 ]; then printf '%s\n' "$out"; fi
  fi
  if [ "${st_overran[slot]}" -eq 1 ]; then
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
    printf '<?xml version="1.0" encoding="UTF-8"?>\n<testsuite name="%s" tests="%s" failures="%s" errors="%s" time="%s" run="%s">\n  <system-out>%s</system-out>\n</testsuite>\n' \
      "$cmd" "$n" "$f" "$err" "$took" "${tally_owner%% *}" \
      "$(printf '%s' "$out" | tail -3 | tr '\n' ' ' | cut -c1-400)" \
      > "$tally_root/$name.xml" 2>/dev/null || true
  fi
}

run_wave() { # <slot> … → the steps started together, waited for, then each recorded in the profile's order
  local slot
  for slot in "$@"; do start_step "$slot" "${owed_cmd[slot]}"; done
  if [ "$#" -eq 1 ]; then
    # A wave of one is announced before it runs, which is where the console carried the header before (issue #269).
    say "gate: ${owed_cmd[$1]}"
    st_announced[$1]=1
  else
    say "gate-pool: $# steps start together — at most ${pool_jobs} in flight"
  fi
  wave_wait "$@"
  for slot in "$@"; do finish_step "$slot"; done
}

# The owed list is walked in the order the profile gave it, and the kind of the list that owed a step decides the
# wave: a first step and the build run alone, and a run of adjacent suites runs up to `LOOP_GATE_POOL_JOBS` at once.
slot_next=0
while [ "$slot_next" -lt "${#owed_cmd[@]}" ]; do
  if [ "${owed_kind[slot_next]}" != "suite" ] || [ "$pool_jobs" -le 1 ]; then
    run_wave "$slot_next"
    slot_next=$((slot_next + 1))
    continue
  fi
  wave_slots=()
  last=$slot_next
  j=$slot_next
  while [ "$j" -lt "${#owed_cmd[@]}" ] && [ "${#wave_slots[@]}" -lt "$pool_jobs" ] && [ "${owed_kind[j]}" = "suite" ]; do
    wave_slots+=("$j")
    last=$j
    j=$((j + 1))
  done
  run_wave "${wave_slots[@]}"
  slot_next=$((last + 1))
done

# The verdict is the last line of the log as well, so a reader that only has the file still gets the numbers the
# console carried — and a `--quiet` gate writes no console at all but leaves a log a red run can be attributed from.
if [ -n "$failed_suites" ]; then
  verdict_line=$(printf 'LOOP-GATE: tests=%s failures=%s errors=%s (%s) — failing:%s' "$cases" "$failures" "$errors" "$ran_entries" "$failed_suites")
  log_line "$verdict_line"
  printf '%s\n' "$verdict_line"
  exit 1
fi
verdict_line=$(printf 'LOOP-GATE: tests=%s failures=%s errors=%s (%s)' "$cases" "$failures" "$errors" "$ran_entries")
log_line "$verdict_line"
printf '%s\n' "$verdict_line"
exit 0
