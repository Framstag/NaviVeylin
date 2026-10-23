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
# Every suite that ran contributes the numbers of its own summary line to one record, and each writes a
# JUnit-shaped tally under the tally directory, so the run's own check — "a green claim has a test result in the
# tree" — reads what actually executed. The tally is derived from the suite's summary, never invented.
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

# The paths the change touches: what it committed since the base, plus whatever is still uncommitted — a gate
# run before the run's own commit measures the same work. A base git cannot answer is named, never treated as
# "no change": a range that cannot be resolved is the one thing a gate must not read as green.
if [ -z "$base" ]; then
  base=$(git merge-base HEAD origin/main 2>/dev/null || git rev-parse --verify --quiet 'HEAD~1' 2>/dev/null || true)
fi
[ -n "$base" ] || loop_die "gate: cannot resolve a base to compare against — pass --base <sha>"
git rev-parse --verify --quiet "$base" >/dev/null || loop_die "gate: '$base' is not a commit this tree knows"
paths=$( { git diff --name-only "$base" 2>/dev/null; git status --porcelain 2>/dev/null | sed 's/^...//'; } | sort -u)

matches() { # <space-separated globs> <path>
  local glob
  for glob in $1; do
    # shellcheck disable=SC2254 # the glob is the point: a pattern, not a literal
    case "$2" in $glob) return 0 ;; esac
  done
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
failed_suites=""
ran=""
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
  ran="$ran${ran:+, }$cmd"
  say "gate: $cmd"
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
  case "$f" in ''|*[!0-9]*) [ "$rc" -eq 0 ] || f=1 ;; esac
  cases=$((cases + n))
  failures=$((failures + f))
  # One tally per suite, under the directory the profile names (`*test-results*`, so the run's own check finds
  # it) and only when it names one: the engine may hold no project path, and the portability check is what says so.
  if [ -n "$tally_root" ]; then
    mkdir -p "$tally_root" 2>/dev/null || true
    name=$(printf '%s' "$cmd" | tr '/ ' '__' | cut -c1-80)
    [ "$rc" -eq 0 ] || [ "$n" -gt 0 ] || n=1
    printf '<?xml version="1.0" encoding="UTF-8"?>\n<testsuite name="%s" tests="%s" failures="%s" errors="0">\n  <system-out>%s</system-out>\n</testsuite>\n' \
      "$cmd" "$n" "$f" "$(printf '%s' "$out" | tail -3 | tr '\n' ' ' | cut -c1-400)" \
      > "$tally_root/$name.xml" 2>/dev/null || true
  fi
done <<< "$owed"

if [ -n "$failed_suites" ]; then
  printf 'LOOP-GATE: tests=%s failures=%s (%s) — failing:%s\n' "$cases" "$failures" "$ran" "$failed_suites"
  exit 1
fi
printf 'LOOP-GATE: tests=%s failures=%s (%s)\n' "$cases" "$failures" "$ran"
exit 0
