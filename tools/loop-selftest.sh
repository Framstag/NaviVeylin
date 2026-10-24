#!/usr/bin/env bash
# The loop's self-test: the gate for the change that owns `tools/loop/**`.
#
# Every case drives the real scripts — the wrapper, the worktree, the branches, the commits, the label
# transitions and the closure — inside a throwaway git repository created per case, with the stub `gh`
# (change 1) and the stub `pi` on PATH. Nothing here needs a token, a network, a model, a device or a
# sandbox tool: git is never stubbed, because the git logic is part of what is being tested (D13).
#
# Usage: bash tools/loop-selftest.sh [--list] [--case <group#name|glob>] [--group <group>]
#                                    [--files <path> ...] [--from-git <base>] [--case-files]
# Exit:  0 every case passed (or there are no cases), 1 a case failed, 2 the environment is unusable.
set -uo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)

# A case is named `<group>#<name>`, and the selection is a filter over that name: `--case` takes a name and,
# because a name may carry a glob, a pattern; `--group` takes the group alone. Both may be given at once, and
# then both have to hold, beside a file set (`--files`, or `--from-git`) that no case is owed for when its
# declaration does not name it. A filter that names nothing is a refusal (exit 2) and never a suite that selected
# no case and reported success — the rule `tools/loop-mutations.sh` already applies to a file filter.
#
# The file set is why the suite has a seventh of its usual cost available: a delta that touches one engine script
# owes the cases that declare it, not 190 cases (issue #276 — a two-file delta paid 723 s). The full set is still
# owed before a change that owns `tools/loop/**` is archived: a case reaches behaviour through the scripts they
# call, and only a hand-run of the whole set covers that.
list_only=0
only_case=""
only_group=""
# The file set, kept space-padded (` path path `) so a membership test is one `case` and no array expansion runs
# under `set -u`, exactly as `tools/loop-mutations.sh` carries its own: `--files` is repeatable and its value may
# hold several paths, so a caller can pass either shape without a rule. A path is repository-relative, never a
# basename, and a filter that names no case is a refusal rather than a suite that ran nothing and reported success.
only_files=" "
from_git=""
list_declared=0
while [ $# -gt 0 ]; do
  case "$1" in
    --list) list_only=1; shift ;;
    --case) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; only_case="${2:-}"; shift 2 ;;
    --group) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; only_group="${2:-}"; shift 2 ;;
    --files) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }
      for _wanted in ${2:-}; do only_files="$only_files$_wanted "; done; shift 2 ;;
    --from-git) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; from_git="${2:-}"; shift 2 ;;
    --case-files) list_declared=1; shift ;;
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
# The two paths the compile uses are named for the driver, not `tmp`: a case may declare `local tmp` of its own (five
# do), and a `local` of that name shadows the driver's scratch directory for the function that calls `core_ensure` —
# measured in this change's first gate run, `tmp ist nicht gesetzt` out of `clean#reportsWithoutApplyAndRemovesWithIt`.
readonly selftest_classes="$tmp/classes"
readonly selftest_core_marker="$tmp/core-compiled"

# The issue tooling's Java core is compiled when a case first asks for it, not before the case list is known: an
# invocation that narrowed one shell case paid a fixed second for a compile nothing used (issue #261). A case asks
# by calling `core_ensure` — `tools/loop-cases.sh` calls it at every place that starts the engine or the core — and
# the marker is how the case *after* it learns the classes are already there, because a case runs in a subshell of
# its own and what that subshell exports does not come back to the driver.
core_ensure() {
  if [ ! -f "$selftest_core_marker" ]; then
    javac --release 17 -d "$selftest_classes" "$root"/tools/issues/core/*.java || {
      rm -rf "$selftest_classes"
      echo "loop-selftest: the issue tooling's Java core does not compile" >&2
      exit 1
    }
    : > "$selftest_core_marker"
  fi
  export ISSUES_CORE_CLASSES="$selftest_classes"
}

export LOOP_PROJECT_ROOT="$root"
# Overridable so the mutation harness can point a case at a mutated *copy* of the engine and confirm its own
# guards (`tools/loop-mutations.sh --engine <copy>`); the defaults are this repository.
export LOOP_ENGINE="${LOOP_ENGINE:-$root/tools/loop}"
export LOOP_ISSUES_ENGINE="$root/tools/issues"
export LOOP_FIXTURES="$root/tools/loop/fixtures"
export LOOP_ISSUE_FIXTURES="$root/tools/issues/fixtures"
export LOOP_TMP="$tmp"
export GH_STUB_DIR="$LOOP_ISSUE_FIXTURES"
chmod +x "$LOOP_FIXTURES/pi" "$LOOP_ISSUE_FIXTURES/gh" 2>/dev/null || true
export PATH="$LOOP_FIXTURES:$LOOP_ISSUE_FIXTURES:$PATH"

# shellcheck source=tools/loop-cases.sh
source "$root/tools/loop-cases.sh"
shell_cases=$(loop_shell_cases)

# ── the files each case can be affected by ──────────────────────────────────────────────────────────────────
# A case declares them in the first line of its body, beside its name:
#
#   case_gate_theDeltaSelectsTheSuitesItTouches() {
#     # files: tools/loop/gate.sh tools/loop/loop.conf tools/tooling.json
#
# The tokens are repository-relative paths. A token ending in `/` is a directory, so a case that builds a throwaway
# project from `tools/loop/fixtures/scenarios/` declares that directory rather than its every file, and a delta's
# path is matched by prefix against it. `*` means every file: a case that copies the whole engine, or one whose body
# names nothing this driver can see, is owed for any delta at all — the conservative side, because a selection that
# is too wide costs seconds and one that is too narrow hides a red case.
#
# The declaration is read from the file that was just sourced, never from a second copy of it, so it cannot drift
# from the case it belongs to. A case with no declaration is owed for nothing a file filter names and is counted as
# undeclared by `--case-files`; the suite's own case `driver#everyCaseDeclaresTheFilesItCanReach` is what refuses it.
declare -A case_files=()
while IFS=$'\t' read -r _decl_fn _decl_set; do
  [ -n "$_decl_fn" ] || continue
  _decl_name=${_decl_fn#case_}
  _decl_name="${_decl_name%%_*}#${_decl_name#*_}"
  case_files[$_decl_name]=$_decl_set
done < <(awk '
  /^case_[a-z]*_[A-Za-z0-9_]*\(\) \{/ { fn = $0; sub(/\(\) \{.*/, "", fn); want = 1; next }
  want { if ($0 ~ /^  # files: /) { sub(/^  # files: /, ""); printf "%s\t%s\n", fn, $0 } want = 0 }
' "$root/tools/loop-cases.sh")

# The files a change must touch for every case to be owed: a case reaches behaviour through the suite's own
# driver, its case file and the library and profile every engine script sources, so a delta to one of them is
# owed the whole set. The project names them, because which paths those are is a fact about the project.
core_files=""
if declare -F loop_case_core_files >/dev/null 2>&1; then
  core_files=$(loop_case_core_files)
fi

# `--from-git <base>` adds the paths a change touched to the file set, from the same two sources the loop's gate
# reads: the diff against the base, and the working tree's own state (a change that is not committed yet). A base
# git cannot diff is named and refused, never read as "no path changed" — that is the one answer a selection must
# not mistake for a green suite.
if [ -n "$from_git" ]; then
  git_paths=$(git diff --name-only "$from_git" 2>/dev/null) || {
    printf 'loop-selftest: --from-git %s is not a base git can diff this working tree against\n' "$from_git" >&2
    exit 2
  }
  status_paths=$(git status --porcelain 2>/dev/null | sed 's/^...//')
  while IFS= read -r _wanted; do
    [ -n "$_wanted" ] || continue
    case "$only_files" in *" $_wanted "*) continue ;; esac
    only_files="$only_files$_wanted "
  done < <(printf '%s\n' "$git_paths" "$status_paths")
fi

if [ "$list_declared" -eq 1 ]; then
  for case_name in $shell_cases; do
    [ -n "$case_name" ] || continue
    printf '%s\t%s\n' "$case_name" "${case_files[$case_name]-<undeclared>}"
  done
  exit 0
fi

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

# A changed path owes a case when the case declares it. `*` owes every case, a token ending in `/` owns the paths
# below it, and a core file owes the whole set whatever the cases declare — the three rules are read in that order
# because the widest of them decides.
files_select() { # <case> → the file set names this case
  local tok p
  [ "$only_files" = " " ] && return 0
  for p in $only_files; do
    case " $core_files " in *" $p "*) return 0 ;; esac
  done
  for tok in ${case_files[$1]:-}; do
    [ "$tok" = '*' ] && return 0
    for p in $only_files; do
      case "$tok" in
        */) case "$p" in "$tok"*) return 0 ;; esac ;;
        *) [ "$p" = "$tok" ] && return 0 ;;
      esac
    done
  done
  return 1
}
count_files_matching() { # → how many declared cases the file set names, ignoring the name filters
  local n=0 case_name
  for case_name in $shell_cases; do
    [ -n "$case_name" ] || continue
    files_select "$case_name" && n=$((n + 1))
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
# The file set is refused on its own, against the whole declared set: a caller who named the wrong script would
# otherwise read a suite that executed nothing as "the change's cases all held". It is the rule the mutation
# harness applies to its own `--files`, and the reason the declaration exists.
if [ "$only_files" != " " ] && [ "$(count_files_matching)" -eq 0 ]; then
  printf 'loop-selftest: the file set names no case of the %s declared — a run that executes nothing would report success, so this is a refusal (--case-files shows what each case declares)\n' "$(count_matching '*')" >&2
  exit 2
fi

selected=""
for case_name in $shell_cases; do
  [ -n "$case_name" ] || continue
  if [ -n "$only_case" ] && ! matches "$case_name" "$only_case"; then continue; fi
  if [ -n "$only_group" ] && ! matches "$case_name" "$only_group#*"; then continue; fi
  if ! files_select "$case_name"; then continue; fi
  selected="$selected$case_name"$'\n'
done

# The selection is stated against the declared set before anything runs, so a green suite that ran a seventh of its
# cases never reads like the whole one.
if [ "$only_files" != " " ]; then
  printf 'loop-selftest: selected %s of %s cases (files:%s)\n' "$(printf '%s' "$selected" | grep -c . || true)" \
    "$(count_matching '*')" "${only_files% }" >&2
fi

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
[ -n "$from_git" ] && selection="$selection --from-git $from_git"
[ "$only_files" != " " ] && [ -z "$from_git" ] && selection="$selection --files$only_files"
if [ "$total" -eq 1 ] && [ -n "$only_case" ] && [ -z "$only_group" ] && ! is_glob "$only_case"; then
  exit 0
fi
echo "loop-selftest: all $total cases passed (JDK $feature, offline, no token, no sandbox)${selection:+ — selection:$selection}"
exit 0
