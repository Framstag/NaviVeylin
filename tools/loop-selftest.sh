#!/usr/bin/env bash
# The loop's self-test: the gate for the change that owns `tools/loop/**`.
#
# Every case drives the real scripts — the wrapper, the worktree, the branches, the commits, the label
# transitions and the closure — inside a throwaway git repository created per case, with the stub `gh`
# (change 1) and the stub `pi` on PATH. Nothing here needs a token, a network, a model, a device or a
# sandbox tool: git is never stubbed, because the git logic is part of what is being tested (D13).
#
# Usage: bash tools/loop-selftest.sh [--list] [--case <group#name|glob>] [--group <group>] [--jobs <n>]
#                                    [--files <path> ...] [--from-git <base>] [--case-files]
#        A run that may execute more than one case holds this tree's lock; `--list`, `--case-files` and one named
#        `--case` hold none. `--jobs` bounds how many cases run at once — the value the invocation names, else the
#        profile's `LOOP_SELFTEST_JOBS`, else the host's CPU count. Every case keeps a process group, a scratch root
#        and an output of its own, and what it left is ended before its slot is handed to the next case (issue #260).
# Exit:  0 every case passed (or there are no cases), 1 a case failed, 2 the environment is unusable — including a
#        second suite in this tree (issue #295) and a bash without `wait -n`, which is the pool's reaper.
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
# How many cases run at once: the invocation's `--jobs`, else the profile's `LOOP_SELFTEST_JOBS`, else the host's CPU
# count. Read into this variable here and resolved once the profile is sourced; only the value is checked below, so a
# refusal is a usage error and arrives before anything is started.
only_jobs=""
while [ $# -gt 0 ]; do
  case "$1" in
    --list) list_only=1; shift ;;
    --case) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; only_case="${2:-}"; shift 2 ;;
    --group) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; only_group="${2:-}"; shift 2 ;;
    --files) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }
      for _wanted in ${2:-}; do only_files="$only_files$_wanted "; done; shift 2 ;;
    --from-git) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; from_git="${2:-}"; shift 2 ;;
    --jobs) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; only_jobs="${2:-}"; shift 2 ;;
    --case-files) list_declared=1; shift ;;
    -h|--help) sed -n '2,17p' "$0"; exit 0 ;;
    *) echo "loop-selftest: unknown argument $1" >&2; exit 2 ;;
  esac
done

# A `--jobs` value that names no worker count is refused before anything is started: a pool of zero runs nothing, and
# a suite that ran nothing and exited 0 is the one answer a selection must never give.
case "$only_jobs" in
  '') ;;
  *[!0-9]*) printf 'loop-selftest: --jobs takes a positive integer, got %s\n' "$only_jobs" >&2; exit 2 ;;
  *) [ "$only_jobs" -gt 0 ] || { printf 'loop-selftest: --jobs takes a positive integer, got %s\n' "$only_jobs" >&2; exit 2; } ;;
esac

# `pgrep` is required because the attribution asks the kernel for the child's own process tree (run.sh,
# `loop_tree_pids`): a sweep over every process costs about a second, which is longer than the writer lives
# (issue #288). A host without procps must refuse the suite here rather than attribute nothing silently.
for tool in bash jq git java javac pgrep; do
  command -v "$tool" >/dev/null 2>&1 || {
    echo "loop-selftest: $tool is required" >&2
    exit 2
  }
done

# The pool below reaps with `wait -n`, which is bash 4.3: a barrier-shaped pool (wait for the whole batch) would hold
# every slot until the slowest case of the batch finished, and the `run#*` cases are the longest of the suite. A host
# whose bash is older is refused here rather than run serially under a name that says it was pooled.
if [ "${BASH_VERSINFO[0]:-0}" -lt 4 ] ||
   { [ "${BASH_VERSINFO[0]}" -eq 4 ] && [ "${BASH_VERSINFO[1]:-0}" -lt 3 ]; }; then
  echo "loop-selftest: bash 4.3 or newer is required (the pool reaps with 'wait -n'), found $BASH_VERSION" >&2
  exit 2
fi

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

# How many cases run at once (issue #260). The value is the invocation's `--jobs`, else the profile's
# `LOOP_SELFTEST_JOBS`, else the host's CPU count — a host that cannot answer is a pool of one, never a suite that
# runs nothing.
selftest_workers() { # <value or nothing> → a positive worker count
  local n="${1:-}"
  case "$n" in
    ''|*[!0-9]*) n="" ;;
  esac
  if [ -n "$n" ] && [ "$n" -gt 0 ]; then printf '%s' "$n"; return 0; fi
  n=$(nproc 2>/dev/null) || n=""
  case "$n" in
    ''|*[!0-9]*) n=$(getconf _NPROCESSORS_ONLN 2>/dev/null) || n="" ;;
  esac
  case "$n" in
    ''|*[!0-9]*|0) n=1 ;;
  esac
  printf '%s' "$n"
}
workers=$(selftest_workers "${only_jobs:-${LOOP_SELFTEST_JOBS:-}}")
# How long one case may run before the suite ends it (issue #308). Whole seconds, validated like the profile's other
# bounds: empty, non-numeric or `0` is the default rather than a refused suite. Measured 2026-10-25: a case that
# never returned held a suite — and the gate behind it — for 56 minutes, and the item whose gate it was had to be
# repaired by hand afterwards. The suite's slowest case is 9–15 s and all 223 cases took 273 s the same day, so the
# default is 60× the slowest case; a case that needs more raises the key.
case_max="${LOOP_CASE_MAX_SECONDS:-900}"
case "$case_max" in ''|*[!0-9]*|0) case_max=900 ;; esac
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
# The slot a running case holds: its pid, the process group that pid leads, the case's name and its own directory
# under the suite's scratch root (`<dir>/tree` is the case's scratch root, `<dir>/status` how it ended, `<dir>/out`
# what it printed). A slot is free while its pid is empty, and `finish_case` empties it.
case_slot_pid=()
case_slot_pgid=()
case_slot_name=()
case_slot_dir=()
# …and the case's bound: when it started, and the watcher job that enforces it (issue #308).
case_slot_started=()
case_slot_watch=()
# The repository's lock, set only on the way through `selftest_lock_take` that took it (issue #295). Empty
# until then, and named here rather than beside the function because `on_exit` may run before that line.
selftest_lock=""
on_exit() {
  local rc=$? slot
  trap - EXIT
  # The cases first, then their trees. Each case subshell has a process group of its own (`start_case` starts it
  # under `set -m`), so one signal per group ends the `run.sh` that case started, the stub child it waited for and
  # the `sleep` beside them — the processes that used to outlive their suite by hours, at a deleted directory.
  if [ "${#case_slot_pid[@]}" -gt 0 ]; then
    for slot in "${!case_slot_pid[@]}"; do
      [ -n "${case_slot_pid[$slot]:-}" ] || continue
      loop_end_group "${case_slot_pgid[$slot]:-}"
    done
  fi
  # …and the bound watcher each case started, which is a job of *this* shell and not a member of the case's group
  # (issue #308): a suite that takes a signal must not leave a `sleep` behind that would later end a group it no
  # longer owns.
  if [ "${#case_slot_watch[@]}" -gt 0 ]; then
    for slot in "${!case_slot_watch[@]}"; do
      loop_end_pid "${case_slot_watch[$slot]:-}"
    done
  fi
  # …and then anything else that is still standing in the suite's own scratch root. A case that ends *normally*
  # while the `run.sh` it started is still polling leaves that run behind: it was started by the case, whose own
  # exit only ends the group the harness still remembers, and hours of suite runs left dozens of them (issue
  # #233 — 44 processes, 2 to 4.6 h old, a `sleep 1` child each, trees long deleted and `loop-clean.sh` blind to
  # them because the tree was gone). The sweep is the same test `loop-clean.sh` reports with, run before the tree
  # is removed, so a run still standing in it is named, ended and attributed to this suite.
  loop_end_pids_under "$tmp"
  rm -rf "$tmp"
  # …and the repository's lock last, when nothing of this suite is left that a second one could be refused by.
  # Only the suite that took it releases it: the path is set on that one way through `selftest_lock_take`.
  [ -z "$selftest_lock" ] || rm -rf "$selftest_lock"
  exit "$rc"
}
trap on_exit EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

# ── one suite per repository (issue #295) ───────────────────────────────────────────────────────────────
# Two suites in one repository redden different sets of cases: measured 2026-10-24, a run of 210 cases and a
# control run of the same suite from a pristine base, same host and same day, failed two triples of cases whose
# intersection was one case — and that one was another item's stable red. The victim moves between the runs, so
# the failing set of a suite depends on the other suite, and a red gate then reads as a defect in the change
# under test. What this refuses is a second *suite* in one repository, and the refusal names whose lock it found;
# that is the bound the issue states, and it is the whole of what a lock can give.
#
# The lock is a lease, not a tombstone, exactly as the loop's own run lock is (`state.sh lock`, D12): it names the
# pid that took it and the moment it did, a suite whose owner is not running any more takes it over and says so,
# and a live owner is the refusal. It stands in the scratch root this invocation resolved — so a suite in another
# repository, in another worktree of this one, or in a nested suite that resolves a root of its own, is not refused
# by it: two worktrees of one repository are one repository and two scratch roots, and the load a suite in the
# neighbouring tree puts on this one is the host's, not this lock's business.
#
# Three bounds, named rather than implied. (1) An unreadable owner is read again before it is read as "no owner",
# because the directory exists a moment before its owner file does; a lock that stays unreadable is taken over.
# (2) A stale lock is removed and re-created, so two suites that read the *same* dead owner and remove it in the
# same instant can both end up holding it — the gap between that removal and the `mkdir` is the bound, and it is the
# same shape and the same bound as the run's own lock; refusing instead of taking over would leave a repository
# blocked until a person removed the lock, which is the state the lease exists to avoid. (3) A `kill -9`ed suite
# whose pid is later reused by a long-lived process refuses this repository until that process exits; the refusal
# prints the repair. The lock is also not one of the leftovers `loop-clean.sh` reports — its glob is the
# `loop-selftest.` prefix of the scratch trees — which is why the takeover above has to recover it.
#
# An invocation that cannot run more than one case takes no lock, because it cannot be the suite the issue is
# about: `--list` and `--case-files` run no case at all, and one *named* case is one behaviour — the shape the
# mutation harness and the cases that start a nested suite use, and the shape the driver already treats as a
# single-case run when it prints no summary below. The selection decides, never a case's cost: a `--case` pattern,
# a `--group`, a `--files`/`--from-git` selection and an unfiltered run are all suites, and the gate's own
# `--from-git` run — the selection that was red on 2026-10-24 — is one of them.
is_glob() { # a filter value that carries a wildcard rather than a name
  case "$1" in *'*'*|*'?'*|*'['*) return 0 ;; esac
  return 1
}

selftest_runs_one_named_case() { # the shapes that are not a suite: a listing, and one named case
  if [ "$list_only" -eq 1 ] || [ "$list_declared" -eq 1 ]; then return 0; fi
  [ -n "$only_case" ] && ! is_glob "$only_case" || return 1
  [ -z "$only_group" ] && [ -z "$from_git" ] && [ "$only_files" = " " ]
}

selftest_lock_take() {
  local lock="$LOOP_TMP_DIR/.loop-selftest.lock" holder holder_pid attempt
  if ! mkdir "$lock" 2>/dev/null; then
    holder=""
    for attempt in 1 2 3 4 5 6 7 8 9 10; do
      holder=$(cat "$lock/owner" 2>/dev/null || printf '')
      [ -n "$holder" ] && break
      sleep 0.1
    done
    holder_pid=${holder%% *}
    case "$holder_pid" in ''|*[!0-9]*) holder_pid=0 ;; esac
    if [ "$holder_pid" -gt 0 ] && kill -0 "$holder_pid" 2>/dev/null; then
      printf 'loop-selftest: another suite holds %s (owner %s) — one suite per repository: two suites in one repository make the failing set of each depend on the other (issue #295)\n' \
        "$lock" "$holder" >&2
      printf 'loop-selftest: wait for it, run this suite in another worktree, or remove %s if that process is not a suite\n' "$lock" >&2
      # A gate that runs this suite reads exit 2 as a refused suite, not as a failed case: it is the environment,
      # and the run that owes the gate is the one that reschedules it.
      exit 2
    fi
    # A suite ended by a signal no trap can catch leaves its lock behind, and a lock whose owner is gone must not
    # refuse every suite that follows it: the next one takes it over and says whose it was. Only a lock that still
    # carries the record this suite read is removed, and the `mkdir` below is what decides between two suites that
    # took the same dead lock over at once. The bound is the gap between that removal and that `mkdir` — the same
    # shape, and the same bound, as the run's own lock (`state.sh lock`); an advisory `flock` would close it and is
    # not a tool this suite may require of a host.
    if [ "$(cat "$lock/owner" 2>/dev/null)" != "$holder" ]; then
      printf 'loop-selftest: the lock %s changed hands while this suite read it — one suite per repository\n' "$lock" >&2
      exit 2
    fi
    rm -rf "$lock" 2>/dev/null || true
    mkdir "$lock" 2>/dev/null || {
      printf 'loop-selftest: another suite took %s while this one read it — one suite per repository\n' "$lock" >&2
      exit 2
    }
    printf 'loop-selftest: took over the lock %s: its owner %s is not running any more\n' "$lock" "${holder:-an unknown process}" >&2
  fi
  if ! printf '%s %s\n' "$$" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$lock/owner" 2>/dev/null; then
    # A lock that cannot say who holds it cannot be released by its holder, and leaving it behind would refuse the
    # next suite as a dead owner's: remove what this suite just made and refuse.
    rm -rf "$lock" 2>/dev/null || true
    printf 'loop-selftest: cannot write the owner of %s — no lock is held\n' "$lock" >&2
    exit 2
  fi
  selftest_lock="$lock"
}
selftest_runs_one_named_case || selftest_lock_take

mkdir -p "$tmp/classes"
# The two paths the compile uses are named for the driver, not `tmp`: a case may declare `local tmp` of its own (five
# do), and a `local` of that name shadows the driver's scratch directory for the function that calls `core_ensure` —
# measured in this change's first gate run, `tmp ist nicht gesetzt` out of `clean#reportsWithoutApplyAndRemovesWithIt`.
readonly selftest_classes="$tmp/classes"
readonly selftest_core_marker="$tmp/core-compiled"
readonly selftest_core_claim="$tmp/core-claim"

# The issue tooling's Java core is compiled when a case first asks for it, not before the case list is known: an
# invocation that narrowed one shell case paid a fixed second for a compile nothing used (issue #261). A case asks
# by calling `core_ensure` — `tools/loop-cases.sh` calls it at every place that starts the engine or the core — and
# the marker is how the case *after* it learns the classes are already there, because a case runs in a subshell of
# its own and what that subshell exports does not come back to the driver.
#
# The pool is what makes the claim necessary: two cases can ask in the same instant, and two `javac`s writing the same
# output directory is a class path neither of them can use. The claiming `mkdir` is the same shape the repository's
# lock uses, the wait is the marker (so the second asking case compiles nothing and is handed the same path), and the
# wait is bounded — a claim left by a case the kernel killed is removed after ~60 s and taken again.
core_ensure() {
  local waited=0
  if [ ! -f "$selftest_core_marker" ]; then
    while ! mkdir "$selftest_core_claim" 2>/dev/null; do
      [ -f "$selftest_core_marker" ] && break
      sleep 0.05
      waited=$((waited + 1))
      [ "$waited" -lt 1200 ] || { rm -rf "$selftest_core_claim" 2>/dev/null || true; waited=0; }
    done
    if [ ! -f "$selftest_core_marker" ]; then
      javac --release 17 -d "$selftest_classes" "$root"/tools/issues/core/*.java || {
        rm -rf "$selftest_classes" "$selftest_core_claim"
        echo "loop-selftest: the issue tooling's Java core does not compile" >&2
        exit 1
      }
      : > "$selftest_core_marker"
    fi
    rm -rf "$selftest_core_claim" 2>/dev/null || true
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

# ── the pool: each case in a process group and a scratch root of its own (issue #260) ─────────────────────────
# Measured 2026-10-22 (issue #260): 163 cases ran strictly one after another, 723 s, and the serialization was the
# whole of what that cost — every case already owned a throwaway repository, a scratch tree under `.pi/tmp/` and a
# process group of its own, and they share nothing but the read-only engine. The driver starts `$workers` cases and
# starts the next as one finishes; a case's group, its scratch root and the processes standing in it are ended
# before its slot is handed on, which is the sweep `on_exit` makes for the suite, made per case (the exit trap's
# `/proc` sweep is what a pool has to take apart first: beside a running case it would end that case's processes).
#
# What a case keeps unchanged: the `PASS`/`FAIL` line, the counts, the summary line and the exit status, and a
# `--case` run of one case is still one case. What it gains: a scratch root of its own (`LOOP_CASE_TMP`, which
# `scratch()` creates the case's trees under), an output of its own — so two cases' lines never interleave, and a
# case's lines arrive prefixed with its own name as they are written (issue #308: a case that hung used to be named
# nowhere while it hung) — and a claim on the one compile (`core_ensure` above).
selftest_case_root="$tmp/cases"
mkdir -p "$selftest_case_root" || {
  echo "loop-selftest: cannot create $selftest_case_root" >&2
  exit 2
}

start_case() { # <slot> <case name> → the case is running in that slot
  local slot="$1" name="$2" dir
  dir="$selftest_case_root/$slot"
  rm -rf "$dir"
  mkdir -p "$dir/tree" || {
    printf 'loop-selftest: cannot create the scratch directory of %s\n' "$name" >&2
    exit 2
  }
  case_slot_name[slot]="$name"
  case_slot_dir[slot]="$dir"
  # `set -m` makes the case's subshell a group leader, which is what lets one signal reach the case and everything it
  # started (issue #213). The inner subshell is what keeps a case's own `exit` — which is how `fail` fails a case —
  # from taking the record of how it ended with it. The stdin is closed for the same reason `run_child` closes its
  # child's: a member of a background group that reads a terminal is stopped, not run.
  #
  # A case's output is relayed **while it runs** (issue #308): each line reaches the suite's own output as it
  # arrives, prefixed with the case's name, and is appended to `$dir/out` byte for byte for the case's failure text.
  # Measured 2026-10-25, a hung case's gate held 222 `PASS` lines and then fifty minutes of nothing, because a case's
  # output was held in that file until the case ended. fd 3 is the suite's own stdout, taken before it is replaced
  # by the relay's sink.
  #
  # The case is run **in the foreground of its subshell** and the status is written straight after it returns — a
  # pipeline (`( case ) | relay`) would make the subshell wait for the relay to reach EOF, and a case's own
  # descendants inherit that pipe and can hold it open, which is the hang this relay must not reintroduce (measured
  # on the first shape of this change: the whole `driver#*` group stood at its 900 s bound). The relay reads a fifo
  # of its own instead: it is a child of the case's subshell and therefore a member of its process group, so
  # `finish_case` ends it with the group, and the case's status never waits for it.
  case_slot_started[slot]=$(date +%s)
  set -m
  ( LOOP_CASE_TMP="$dir/tree"; export LOOP_CASE_TMP
    mkfifo "$dir/stream" 2>/dev/null || exit 1
    ( while IFS= read -r line; do
        printf '%s| %s\n' "$name" "$line" >&3
        printf '%s\n' "$line" >> "$dir/out"
      done < "$dir/stream" ) &
    relay=$!
    ( loop_run_shell_case "$name" ) > "$dir/stream" 2>&1
    rc=$?
    # The relay is given a short, bounded grace **before the status is written**, so what the case printed is read
    # before the pool can end the group: a case whose descendants hold the fifo open (the sweep case leaves exactly
    # one) never gives the relay EOF, and a grace in `finish_case` instead would sit between a case's end and the
    # sweep that ends what it left — measured, that made `driver#aCaseThatLeavesAProcessBehindIsSweptWithIt` see
    # the leaked process still running. The pool's own order is untouched.
    for ((g = 0; g < 20; g++)); do
      kill -0 "$relay" 2>/dev/null || break
      sleep 0.05
    done
    printf '%s\n' "$rc" > "$dir/status" ) 3>&1 >/dev/null 2>&1 < /dev/null &
  case_slot_pid[slot]=$!
  set +m
  case_slot_pgid[slot]=$!
  # The case's bound is a watcher, and it is a job of *this* shell rather than a member of the case's group (issue
  # #308): it writes the case's `status` and `bound` **before** it ends the group, so a case that recorded nothing
  # is a failure answered by its bound — and the group it signals does not contain the writer. `$token` keeps a
  # watcher that fires late out of a slot the pool has since handed on: the slot directory is recreated per case,
  # and a token that no longer matches stops the watcher without writing. The check that no status exists narrows
  # the one race left — a case that finishes in the same instant its bound fires — to a case that was at its bound
  # anyway.
  token="${case_slot_pid[$slot]}-${case_slot_started[$slot]}"
  printf '%s\n' "$token" > "$dir/token" 2>/dev/null || true
  # …and its own stdin, stdout and stderr are `/dev/null`, not the suite's: the watcher outlives every case until
  # it fires or is ended, so a suite whose output is captured in a command substitution (`$( … )`, which three of
  # the driver's own cases do) would never see EOF while a watcher held the write end of that pipe — the whole
  # nested-suite group of cases hung on exactly that. Its writes are files it names, and its one signal is a pid.
  set -m
  ( sleep "$case_max"
    [ "$(cat "$dir/token" 2>/dev/null)" = "$token" ] || exit 0
    [ -s "$dir/status" ] && exit 0
    printf '%s\n' "$LOOP_CHILD_TIMEOUT" > "$dir/status"
    printf '%s\n' "$case_max" > "$dir/bound"
    kill -TERM -- -"${case_slot_pgid[$slot]}" 2>/dev/null || true ) < /dev/null > /dev/null 2>&1 &
  case_slot_watch[slot]=$!
  set +m
}

# Reap the case, end what it left, print what it printed and answer its own status. The record of how it ended is
# written by the subshell that ran it, so a case whose subshell the kernel killed records nothing — and a case that
# recorded nothing is a failure, never a pass.
finish_case() { # <slot> → the case's own status
  local slot="$1" dir status
  dir="${case_slot_dir[$slot]}"
  # The bound's watcher first, while the slot directory is still the one it was started for (issue #308): a watcher
  # that outlived its case would otherwise fire into the slot the pool hands to the next one.
  loop_end_pid "${case_slot_watch[$slot]:-}"
  case_slot_watch[slot]=""
  wait "${case_slot_pid[$slot]}" 2>/dev/null
  case_slot_pid[slot]=""
  case_slot_started[slot]=""
  status=$(cat "$dir/status" 2>/dev/null)
  case "$status" in
    ''|*[!0-9]*) status=1 ;;
  esac
  # The group first, then the case's own scratch root: whatever the case left inside its group is ended by one signal,
  # and whatever took a group of its own is what the sweep finds. The suite-level sweep in `on_exit` remains for a
  # process that stood in the suite's root itself (issue #233).
  loop_end_group "${case_slot_pgid[$slot]:-}"
  case_slot_pgid[slot]=""
  loop_end_pids_under "$dir/tree" --quiet
  # What the case printed is its failure text (issue #308): the relay has already sent every line to the suite's own
  # output as it arrived, so the file is repeated here only for a case that failed — a reader of the transcript finds
  # a failed case's text together and in order, and a passing case is not printed twice.
  if [ "$status" -ne 0 ] && [ -s "$dir/out" ]; then
    printf '%s\n' "--- $case_slot_name[$slot] (exit $status)"
    cat "$dir/out"
  fi
  rm -rf "$dir"
  case_slot_name[slot]=""
  case_slot_dir[slot]=""
  return "$status"
}

selftest_free_slot() { # → the first slot no case holds
  local i
  for ((i = 0; i < workers; i++)); do
    [ -z "${case_slot_pid[$i]:-}" ] && { printf '%s' "$i"; return 0; }
  done
  return 1
}

# `matches <case> <pattern>` — the case's name against a filter value, where the value is a glob and not a
# quoted literal: `run#*` is a pattern, `run#recordsAFailedReviewOnTheBranch` a name that matches itself.
matches() {
  # shellcheck disable=SC2254 # the pattern is the point, exactly as in `gate.sh`'s `matches`
  case "$1" in $2) return 0 ;; esac
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
# The selection, one case per element, and the pool that runs them: a case starts while fewer than `$workers` are
# running, and the next one starts the moment one finishes — no barrier, because a batch waits for its slowest case
# and the `run#*` cases are the suite's longest. `wait -n` is the reaper; which slot finished is read from the record
# the case's subshell wrote, so a status is never attributed to the wrong case.
declare -a selftest_queue=()
while IFS= read -r _case_name; do
  [ -n "$_case_name" ] || continue
  selftest_queue+=("$_case_name")
 done <<< "$selected"
queued=${#selftest_queue[@]}
started=0
running=0
while [ "$started" -lt "$queued" ] || [ "$running" -gt 0 ]; do
  while [ "$running" -lt "$workers" ] && [ "$started" -lt "$queued" ]; do
    slot=$(selftest_free_slot) || break
    start_case "$slot" "${selftest_queue[$started]}"
    started=$((started + 1))
    running=$((running + 1))
    total=$((total + 1))
  done
  [ "$running" -gt 0 ] || break
  wait -n
  finished=0
  for ((slot = 0; slot < workers; slot++)); do
    [ -n "${case_slot_pid[$slot]:-}" ] || continue
    [ -f "${case_slot_dir[$slot]}/status" ] || continue
    case_name="${case_slot_name[$slot]}"
    # The bound's answer is read before the slot is emptied (issue #308): a case that recorded nothing because its
    # watcher ended it is a failure, and the line says which clock answered it.
    bound=$(cat "${case_slot_dir[$slot]}/bound" 2>/dev/null)
    if finish_case "$slot"; then
      printf 'PASS %s\n' "$case_name"
    else
      failed=$((failed + 1))
      if [ -n "$bound" ]; then
        printf 'FAIL %s (exceeded %s s)\n' "$case_name" "$bound"
      else
        printf 'FAIL %s\n' "$case_name"
      fi
    fi
    running=$((running - 1))
    finished=$((finished + 1))
  done
  # A child that exited between the reaper and the scan writes its record before it exits, so this is the wait for
  # that write and not a busy loop: it costs one pass over the slots and at most one short sleep.
  [ "$finished" -gt 0 ] || sleep 0.02
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
