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

say() { [ "$quiet" -eq 1 ] || printf '%s\n' "$*"; }

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
fi

# The paths the change touches: what it committed since the base, plus whatever is still uncommitted — a gate
# run before the run's own commit measures the same work. A base git cannot answer is named, never treated as
# "no change": a range that cannot be resolved is the one thing a gate must not read as green.
if [ -z "$base" ]; then
  base=$(git merge-base HEAD origin/main 2>/dev/null || git rev-parse --verify --quiet 'HEAD~1' 2>/dev/null || true)
fi
[ -n "$base" ] || loop_die "gate: cannot resolve a base to compare against — pass --base <sha>"
git rev-parse --verify --quiet "$base" >/dev/null || loop_die "gate: '$base' is not a commit this tree knows"
paths=$( { git diff --name-only "$base" 2>/dev/null; git status --porcelain 2>/dev/null | sed 's/^...//'; } | sort -u)

# A pattern is data, and only the second argument is a path: unquoted, `for glob in $1` lets the shell expand the
# profile's *pattern list* against the directory the gate happens to run in, so a pattern that names a tree is
# replaced by the names it matched and then compared literally. Measured 2026-10-23 (issue #273): with the profile's
# own `app/src/main/res/*` and a new file under that directory, the plan owed nothing, and with `set -f` here it
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
    if matches "$compiled_patterns" "$p"; then owed="$owed$compiled_cmd"$'\n'; break; fi
  done <<< "$paths"
fi
if [ -n "$suites" ]; then
  while IFS='|' read -r glob cmd; do
    [ -n "${glob:-}" ] && [ -n "${cmd:-}" ] || continue
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
# A suite's output is streamed, not replayed: `tee` writes it to the console as the suite writes it, and the
# file it tees into is what the summary is parsed from — one observation feeds the log and the tally. Measured
# 2026-10-23 (issue #269): `out=$($cmd 2>&1)` held the whole output until the command exited, so a gate that ran
# a suite for eleven minutes carried two lines, and neither an operator nor a worker could tell a long suite
# from a stopped one, nor see which case a failure was in.
stream=$(mktemp "${TMPDIR:-/tmp}/loop-gate-output.XXXXXX") \
  || loop_die "gate: cannot create the file that carries a suite's output"
trap 'rm -f "$stream"' EXIT
while IFS= read -r cmd; do
  [ -n "$cmd" ] || continue
  say "gate: $cmd"
  # A suite's wall clock is recorded, not guessed: without it neither an operator nor a worker can tell a suite
  # that costs a quarter of an hour from one that costs three seconds, and an iteration that has to be made
  # cheaper cannot say which suite it is paying for. `SECONDS` is the shell's own counter, integer and monotonic
  # enough for a wall clock of this size.
  t0=$SECONDS
  # `--quiet` keeps the console for the verdict, but the file is written either way: the tally is read from it.
  # The status is the suite's own, never the stream's (`pipefail` would answer that too, but only for the last
  # command of the pipeline, and this one names the answer explicitly).
  if [ "$quiet" -eq 1 ]; then
    $cmd >"$stream" 2>&1
    rc=$?
  else
    $cmd 2>&1 | tee "$stream"
    rc=${PIPESTATUS[0]}
  fi
  out=$(cat "$stream")
  took=$((SECONDS - t0))
  say "gate: $cmd — ${took}s"
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
