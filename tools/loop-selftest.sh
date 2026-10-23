#!/usr/bin/env bash
# The loop's self-test: the gate for the change that owns `tools/loop/**`.
#
# Every case drives the real scripts — the wrapper, the worktree, the branches, the commits, the label
# transitions and the closure — inside a throwaway git repository created per case, with the stub `gh`
# (change 1) and the stub `pi` on PATH. Nothing here needs a token, a network, a model, a device or a
# sandbox tool: git is never stubbed, because the git logic is part of what is being tested (D13).
#
# Usage: bash tools/loop-selftest.sh [--list] [--case <group#name|glob>] [--group <group>]
# Exit:  0 every case passed (or there are no cases), 1 a case failed, 2 the environment is unusable.
set -uo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)

# A case is named `<group>#<name>`, and the selection is a filter over that name: `--case` takes a name and,
# because a name may carry a glob, a pattern; `--group` takes the group alone. Both may be given at once, and
# then both have to hold. A filter that names nothing is a refusal (exit 2) and never a suite that selected no
# case and reported success — the rule `tools/loop-mutations.sh` already applies to a file filter.
list_only=0
only_case=""
only_group=""
while [ $# -gt 0 ]; do
  case "$1" in
    --list) list_only=1; shift ;;
    --case|--group)
      [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }
      if [ "$1" = "--case" ]; then only_case="${2:-}"; else only_group="${2:-}"; fi
      shift 2 ;;
    -h|--help) sed -n '2,10p' "$0"; exit 0 ;;
    *) echo "loop-selftest: unknown argument $1" >&2; exit 2 ;;
  esac
done

for tool in bash jq git java javac; do
  command -v "$tool" >/dev/null 2>&1 || {
    echo "loop-selftest: $tool is required" >&2
    exit 2
  }
done

feature=$(java -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p')
if [ -z "$feature" ]; then
  echo "loop-selftest: could not read the JDK feature version from: $(java -version 2>&1 | head -1)" >&2
  exit 2
fi
if [ "$feature" -lt 17 ]; then
  echo "loop-selftest: JDK 17 or newer is required (the issue core is compiled with --release 17), found feature version $feature" >&2
  exit 2
fi

# The scratch tree goes under the gitignored `.pi/tmp/`, so a failed run leaves nothing in the diff.
#
# The root and the name one scratch tree carries are the loop's own definitions (`lib.sh`, `LOOP_TMP_DIR` in
# the configuration), so the trees a killed suite leaves behind are exactly the trees
# `tools/loop/loop-clean.sh` reports and removes.
# shellcheck source=tools/loop/lib.sh
source "$root/tools/loop/lib.sh"
LOOP_ROOT="$root"
export LOOP_CONF="${LOOP_CONF:-$root/tools/loop/loop.conf}"
[ -f "$LOOP_CONF" ] || {
  echo "loop-selftest: no loop configuration at $LOOP_CONF" >&2
  exit 2
}
# shellcheck source=tools/loop/loop.conf
source "$LOOP_CONF"
loop_tmp_resolve
mkdir -p "$LOOP_TMP_DIR" 2>/dev/null || true
tmp=$(mktemp -d "$LOOP_TMP_DIR/$LOOP_SELFTEST_PREFIX"XXXXXX) || {
  echo "loop-selftest: cannot create a scratch directory under $LOOP_TMP_DIR" >&2
  exit 2
}
# An interrupted suite removes its scratch tree the way a failing one does (issue #210): INT, TERM and HUP
# are trapped beside EXIT, and each leaves by `exit`, so the single removal below still runs. `SIGKILL`
# cannot be trapped — a `kill -9`ed suite *does* leave its tree behind, and that is one of the leftovers
# `tools/loop/loop-clean.sh` reports and removes after `LOOP_TMP_STALE_MINUTES`.
# An interrupted suite removes its scratch tree the way a failing one does (issue #210), and ends the case it was
# in together with every process that case started (issue #213): INT, TERM and HUP are trapped beside EXIT, and
# each leaves by `exit`, so the one `on_exit` below still runs. `SIGKILL` cannot be trapped — a `kill -9`ed suite
# *does* leave its tree, and the processes it leaves, behind; those are the leftovers `tools/loop/loop-clean.sh`
# reports and ends after `LOOP_TMP_STALE_MINUTES`.
case_pgid=""
on_exit() {
  local rc=$?
  trap - EXIT
  # The case first, then its tree. A case subshell has a process group of its own (`run_case` starts it under
  # `set -m`), so this one signal ends the `run.sh` the case started, the stub child it waited for and the
  # `sleep` beside them — the processes that used to outlive their suite by hours, at a deleted directory.
  loop_end_group "$case_pgid"
  case_pgid=""
  # …and then anything else that is still standing in the suite's own scratch root. A case that ends *normally*
  # while the `run.sh` it started is still polling leaves that run behind: it was started by the case, whose own
  # exit only ends the group the harness still remembers, and hours of suite runs left dozens of them (issue
  # #233 — 44 processes, 2 to 4.6 h old, a `sleep 1` child each, trees long deleted and `loop-clean.sh` blind to
  # them because the tree was gone). The sweep is the same test `loop-clean.sh` reports with, run before the tree
  # is removed, so a run still standing in it is named, ended and attributed to this suite.
  loop_end_pids_under "$tmp"
  rm -rf "$tmp"
  exit "$rc"
}
trap on_exit EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM
mkdir -p "$tmp/classes"

javac --release 17 -d "$tmp/classes" "$root"/tools/issues/core/*.java || {
  echo "loop-selftest: the issue tooling's Java core does not compile" >&2
  exit 1
}

export LOOP_PROJECT_ROOT="$root"
# Overridable so the mutation harness can point a case at a mutated *copy* of the engine and confirm its own
# guards (`tools/loop-mutations.sh --engine <copy>`); the defaults are this repository.
export LOOP_ENGINE="${LOOP_ENGINE:-$root/tools/loop}"
export LOOP_ISSUES_ENGINE="$root/tools/issues"
export LOOP_FIXTURES="$root/tools/loop/fixtures"
export LOOP_ISSUE_FIXTURES="$root/tools/issues/fixtures"
export ISSUES_CORE_CLASSES="$tmp/classes"
export LOOP_TMP="$tmp"
export GH_STUB_DIR="$LOOP_ISSUE_FIXTURES"
chmod +x "$LOOP_FIXTURES/pi" "$LOOP_ISSUE_FIXTURES/gh" 2>/dev/null || true
export PATH="$LOOP_FIXTURES:$LOOP_ISSUE_FIXTURES:$PATH"

# shellcheck source=tools/loop-cases.sh
source "$root/tools/loop-cases.sh"
shell_cases=$(loop_shell_cases)

# One case, in a process group of its own: `set -m` makes the case's subshell a group leader, which is what lets
# `on_exit` end the case *and* everything it started with one signal (issue #213). The group id is cleared once
# the case has been waited for, so an id a later process may have been given is never signalled. The case's
# stdin is closed for the same reason `run_child` closes its child's: a member of a background group that reads
# a terminal is stopped, not run, and an unattended suite has nobody to type at it.
run_case() { # <case name> → the case's own status
  local rc
  set -m
  ( loop_run_shell_case "$1" ) < /dev/null &
  case_pgid=$!
  set +m
  wait "$case_pgid"
  rc=$?
  case_pgid=""
  return "$rc"
}

# `matches <case> <pattern>` — the case's name against a filter value, where the value is a glob and not a
# quoted literal: `run#*` is a pattern, `run#recordsAFailedReviewOnTheBranch` a name that matches itself.
matches() {
  # shellcheck disable=SC2254 # the pattern is the point, exactly as in `gate.sh`'s `matches`
  case "$1" in $2) return 0 ;; esac
  return 1
}
is_glob() { # a filter value that carries a wildcard rather than a name
  case "$1" in *'*'*|*'?'*|*'['*) return 0 ;; esac
  return 1
}

count_matching() { # <pattern> → how many declared cases it names
  local pattern="$1" n=0 case_name
  for case_name in $shell_cases; do
    [ -n "$case_name" ] || continue
    matches "$case_name" "$pattern" && n=$((n + 1))
  done
  printf '%s' "$n"
}

# A filter is refused before anything runs, and each one is checked against the whole declared set: an
# intersection that came out empty would otherwise blame whichever filter was read second.
if [ -n "$only_group" ] && [ "$(count_matching "$only_group#*")" -eq 0 ]; then
  printf 'loop-selftest: no case carries the group: %s (--list prints every case, its group being the prefix)\n' "$only_group" >&2
  exit 2
fi
if [ -n "$only_case" ] && [ "$(count_matching "$only_case")" -eq 0 ]; then
  printf 'loop-selftest: no such case: %s\n' "$only_case" >&2
  exit 2
fi

selected=""
for case_name in $shell_cases; do
  [ -n "$case_name" ] || continue
  if [ -n "$only_case" ] && ! matches "$case_name" "$only_case"; then continue; fi
  if [ -n "$only_group" ] && ! matches "$case_name" "$only_group#*"; then continue; fi
  selected="$selected$case_name"$'\n'
done

if [ "$list_only" -eq 1 ]; then
  printf '%s' "$selected"
  exit 0
fi

total=0
failed=0
for case_name in $selected; do
  total=$((total + 1))
  if run_case "$case_name"; then
    printf 'PASS %s\n' "$case_name"
  else
    failed=$((failed + 1))
    printf 'FAIL %s\n' "$case_name"
  fi
done

if [ "$total" -eq 0 ]; then
  echo "no cases"
  exit 0
fi
if [ "$failed" -gt 0 ]; then
  echo "loop-selftest: $failed of $total cases failed"
  exit 1
fi
# One case named with `--case` and no pattern keeps the output every caller already reads — the `PASS` line and
# the exit status, with no summary — because the mutation harness and the leftovers cases invoke exactly that
# shape and read nothing else. A filtered run names its selection beside the count, so a green suite that ran a
# seventh of its cases never reads like the whole one.
selection=""
[ -n "$only_group" ] && selection=" --group $only_group"
[ -n "$only_case" ] && selection="$selection --case $only_case"
if [ "$total" -eq 1 ] && [ -n "$only_case" ] && [ -z "$only_group" ] && ! is_glob "$only_case"; then
  exit 0
fi
echo "loop-selftest: all $total cases passed (JDK $feature, offline, no token, no sandbox)${selection:+ — selection:$selection}"
exit 0
