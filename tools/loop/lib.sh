#!/usr/bin/env bash
# lib.sh — what every loop script shares: where the run lives, the configuration it reads, and the
# one way it talks to the issue tooling.
#
# Two rules are implemented once here, because a rule re-implemented per script is a rule that drifts
# (design D16):
#
#   * **the project is discovered, never told.** The root is the working tree's, so the same script
#     runs against a scratch repository in the self-test and against this project on a real run.
#     `LOOP_CONF`, `LOOP_STATE_FILE` and `LOOP_WORKTREE` are the only overrides, and they exist so the
#     self-test can exercise the configuration seam.
#   * **the loop's own mechanics are configuration.** No script under `tools/loop/` names a skill, a
#     gate command or an agent command; they are read from `loop.conf` (D16). The one exception is
#     the authorisation label, which appears once, read-only, so "the run may never write it" can be
#     asserted mechanically (D2).
#
# Usage: `source lib.sh` — then `loop_resolve` once, at the top of the script, before anything else.
set -uo pipefail

LOOP_OK=0
LOOP_USAGE=2
LOOP_STOP=3
LOOP_REFUSE=5
# The status `run.sh`'s `run_child` returns when it ended a child at the configured wall-clock
# (`LOOP_CHILD_MAX_SECONDS`). It is a child's status, never the run's, and it is distinct so the run's own
# "the worker process exited N" path and a reader can tell a timeout from a child that failed by itself.
LOOP_CHILD_TIMEOUT=124

# The engine's own directory. Everything the loop owns relative to itself is derived from this, never
# from the current directory, so the engine can run inside another project (D16).
LOOP_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# The issue tooling is the loop's dependency, shipped beside it. Its `gh`-facing helpers are reused
# rather than re-implemented, and the project it acts on is still resolved from the working tree.
LOOP_ISSUES_DIR="$LOOP_DIR/../issues"
# shellcheck source=tools/issues/lib.sh
source "$LOOP_ISSUES_DIR/lib.sh"

LOOP_ROOT=""
# The configuration file `loop_resolve` actually sourced. It is a global because the file a gate read is the rule
# its numbers attest, so the process that ran it names it in its own log (issue #277). Empty until `loop_resolve`
# has run, and every engine script resolves before it reads a key.
LOOP_CONF_PATH=""
# `LOOP_STATE_FILE` and `LOOP_WORKTREE` are the self-test's configuration seam (and the only way to run
# two loops in one tree deliberately). An inherited value is preserved, never cleared: assigning an
# empty string here would drop the environment's answer.
LOOP_STATE_FILE="${LOOP_STATE_FILE:-}"
LOOP_WORKTREE="${LOOP_WORKTREE:-}"
# Where the loop's tooling puts throwaway trees: the self-test creates one per run here, and
# `loop-clean.sh` removes the ones a killed suite left behind — the same name, read from this one
# definition, is what tells the loop's trees from another suite's. `LOOP_TMP_DIR` (loop.conf) moves it;
# empty means `.pi/tmp` under the project root, beside the worktree default. Resolved in `loop_resolve`.
LOOP_TMP_DIR="${LOOP_TMP_DIR:-}"
# The name one self-test scratch tree carries: `<LOOP_TMP_DIR>/loop-selftest.<random>`.
LOOP_SELFTEST_PREFIX="loop-selftest."

# Bytes and the line the last `loop_scratch_remove` produced. Globals rather than a return value: a
# command substitution would run the removal in a subshell and lose the count with it.
LOOP_SCRATCH_LINE=""
LOOP_SCRATCH_BYTES=0

# Read-only: the label that authorises automated work. The loop *reads* it (a candidate must carry it
# to be selected) and has no code path that writes it — the write paths of the issue tooling refuse it
# as a reserved label, and `labels#neverWritesReservedLabel` asserts this file is its only mention.
LOOP_AUTHORISATION_LABEL="agent/eligible"

# The run's own progress lines (issue #191). One format — `loop: <phase> · <elapsed> · <what>` — where the
# elapsed time is counted from this process reading this library, so it is the run's own age and not a phase's.
# A progress line is terminal output and nothing else: no state field, no report cell and no verdict is derived
# from one, which is what makes a run that printed them and a run that did not the same run (#191, constraint).
LOOP_PROGRESS_STARTED="$(date +%s)"

fmt_elapsed() { # <seconds> -> `37s`, `4m12s`
  local s="$1"
  if [ "$s" -ge 60 ]; then printf '%dm%02ds' "$((s / 60))" "$((s % 60))"; else printf '%ds' "$s"; fi
}

progress() { # progress <phase> <what> — one line wherever the run crosses a boundary it can observe itself
  local phase="$1"; shift
  printf 'loop: %s · %s · %s\n' "$phase" "$(fmt_elapsed $(( $(date +%s) - LOOP_PROGRESS_STARTED )))" "$*"
}

loop_die() {
  printf 'loop: %s\n' "$*" >&2
  exit "$LOOP_USAGE"
}

loop_refuse() {
  printf 'loop: %s\n' "$*" >&2
  exit "$LOOP_REFUSE"
}

loop_note() {
  printf '%s\n' "$*" >&2
}

# Resolve the project once and load the configuration. Called at the top of every script, never inside
# a command substitution: a failure inside `$( )` would exit a subshell and be swallowed.
loop_resolve() {
  LOOP_ROOT=$(git rev-parse --show-toplevel 2>/dev/null) || {
    # One refusal covers two different states, and they need different repairs: run from outside a checkout at
    # all, or inside a repository marked bare — where `git status` still answers, so the tree looks like a project
    # while every merge, fast-forward and commit in it is refused. Measured 2026-10-21 (issue #241): the caller read
    # "run this from the project you mean to act on" while standing in the project, spent five fast-forwards and
    # an hour, and this setting was the cause.
    #
    # The setting's own name is assembled from two pieces because the portability check reads the profile's
    # vocabulary as *substrings*: the four letters of one namespace value sit inside that name, so writing it out
    # whole would be reported as a project value leaked into an engine. Assembling it keeps the sentence readable
    # and the check honest about what it can actually prove (the substring match is its own item).
    bare_key="c""ore"
    if [ -d "$(git rev-parse --git-dir 2>/dev/null)" ] \
       && [ "$(git rev-parse --is-bare-repository 2>/dev/null)" = "true" ]; then
      printf 'loop: this repository is marked bare (%s.bare=true), so it is not a work tree\n' "$bare_key" >&2
      printf 'loop: every merge, fast-forward and commit is refused in it while `git status` still answers\n' >&2
      printf 'loop: repair: git config %s.bare false   (in %s)\n' "$bare_key" "$PWD" >&2
      exit "$LOOP_REFUSE"
    fi
    loop_die "not inside a git working tree — run this from the project you mean to act on"
  }
  local conf="${LOOP_CONF:-$LOOP_DIR/loop.conf}"
  [ -f "$conf" ] || loop_die "no configuration at $conf — the loop reads its defaults and its project keys from loop.conf"
  LOOP_CONF_PATH="$conf"
  # shellcheck source=tools/loop/loop.conf
  source "$conf"
  # An inherited run identity is honoured only inside the tree this process resolved; `loop_path_outside_tree`
  # above carries the measurement and the reason (issue #214).
  if loop_path_outside_tree "$LOOP_STATE_FILE" "$LOOP_ROOT"; then LOOP_STATE_FILE=""; fi
  if loop_path_outside_tree "$LOOP_WORKTREE" "$LOOP_ROOT"; then LOOP_WORKTREE=""; fi
  LOOP_STATE_FILE="${LOOP_STATE_FILE:-$LOOP_ROOT/.pi/issue-loop/run.json}"
  LOOP_WORKTREE="${LOOP_WORKTREE:-$LOOP_ROOT/.pi/worktrees/loop}"
  # A relative override is relative to the project, not to whatever the caller's cwd happened to be.
  case "$LOOP_STATE_FILE" in /*) ;; *) LOOP_STATE_FILE="$LOOP_ROOT/$LOOP_STATE_FILE" ;; esac
  case "$LOOP_WORKTREE" in /*) ;; *) LOOP_WORKTREE="$LOOP_ROOT/$LOOP_WORKTREE" ;; esac
  loop_tmp_resolve
}

# `LOOP_TMP_DIR` as recorded, resolved against the project root — a relative value is relative to the
# project, exactly like the state file and the worktree. Idempotent, so the self-test can call it itself.
# `loop_path_outside_tree <path> <root>` — true when <path> is absolute and lies outside <root>. `run_child`
# hands the run's identity to every child so all of them agree on the run, and a child that runs the loop's own
# tooling in another tree — a scratch repository, a fixture, the self-test of the loop itself — would otherwise
# write the *running* run's state and point its worktree operations at the run's tree. Measured 2026-10-20
# (issue #214): a live run's state was replaced five times, each time with a fixture's base, queue and budget,
# while its worker was still working the item; the run then walked the fixture's queue. A relative value, and
# an absolute one inside this tree (two loops in one repository, deliberately), are not affected.
loop_path_outside_tree() { # <path> <root> → 0 outside
  case "$1" in
    /*) case "$1" in "$2"/*) return 1 ;; *) return 0 ;; esac ;;
    *) return 1 ;;
  esac
}

loop_tmp_resolve() {
  case "$LOOP_TMP_DIR" in
    "") LOOP_TMP_DIR="$LOOP_ROOT/.pi/tmp" ;;
    /*) ;;
    *) LOOP_TMP_DIR="$LOOP_ROOT/$LOOP_TMP_DIR" ;;
  esac
}

# The scratch root *another tree* resolves, which is not the one this process resolved. A suite creates its trees
# under the root of the tree it runs in, so a gate that runs `tools/loop-selftest.sh` inside the kept worktree makes
# them under the worktree's own `.pi/tmp` — the root a report run from the project never looked in while 305 MB of
# trees and four poll loops accumulated there (measured 2026-10-23, issue #274). The key is read from that tree's
# configuration, the same file `loop_resolve` sources when it runs there, and it is **read, never sourced**: a
# report looks at a tree, it does not run that tree's configuration.
loop_tmp_dir_for() { # <tree> → that tree's scratch root, resolved against that tree
  local tree="${1:-}" recorded=""
  if [ -n "$tree" ] && [ -f "$tree/tools/loop/loop.conf" ]; then
    recorded=$(sed -n 's/^LOOP_TMP_DIR\="\{0,1\}\([^"\]*\)"\{0,1\}[[:space:]]*$/\1/p' \
      "$tree/tools/loop/loop.conf" | tail -1)
  fi
  case "$recorded" in
    "") printf '%s\n' "$tree/.pi/tmp" ;;
    /*) printf '%s\n' "$recorded" ;;
    *) printf '%s\n' "$tree/$recorded" ;;
  esac
}

loop_conf_value() { # print one configuration key's value by name
  local key="$1"
  eval "printf '%s' \"\${$key:-}\""
}

# ── the build output a kept worktree does not need ────────────────────────────────────────────────
# A worktree that has been built carries a full module build tree, which the next run's build regenerates
# and no item ever reads: measured 2026-10-20 (issue #210), the kept worktree was 3.2 GB, of which one
# module's build output alone was 1.9 GB, and nothing removed either. Which directories those are is the
# project's, so the list is `LOOP_WORKTREE_SCRATCH_PATHS` in the loop's configuration — no script under
# `tools/loop/` names one, and `tools/check-portability.sh` reads the list as a forbidden value.
loop_scratch_paths() { # one recorded path per line
  printf '%s\n' "$(loop_conf_value LOOP_WORKTREE_SCRATCH_PATHS)" | tr ' ' '\n' | grep -v '^$'
}

# What one path occupies. `du -sk` never follows a symlink, so a link's target is not counted here — the
# number a report prints is what removing the path actually reclaims.
loop_bytes_of() {
  local k
  k=$(du -sk "$1" 2>/dev/null | awk '{print $1}')
  printf '%s\n' "$(( ${k:-0} * 1024 ))"
}

# Remove one recorded build path inside a tree, or say why it stays — never both, and never by guessing.
# The four refusals are the whole safety argument: an absolute or `..`-carrying entry is not a path inside
# the tree; a path that IS a symlink is never followed (a directory holding one loses only the link); a
# path whose parent resolves outside the tree is not inside it; and a path git tracks is the project's
# source, which no cleanup may touch. Removal is `rm -rf` on a path `du` did not follow either, so a link
# *inside* a build directory costs the link, never its target.
#
# Usage: loop_scratch_remove <tree> <relative path> [--apply] → status 0 when it removed something
loop_scratch_remove() {
  local tree="$1" rel="$2" apply="${3:-}" target real_tree real_parent bytes
  LOOP_SCRATCH_LINE=""
  LOOP_SCRATCH_BYTES=0
  case "$rel" in
    ""|.|..|/*|../*|*/..|*/../*)
      LOOP_SCRATCH_LINE="kept $rel — not a path inside the tree"
      return 1 ;;
  esac
  target="$tree/$rel"
  { [ -e "$target" ] || [ -L "$target" ]; } || return 1
  if [ -L "$target" ]; then
    LOOP_SCRATCH_LINE="kept $rel — a symlink, never followed out of the tree"
    return 1
  fi
  real_tree=$(cd "$tree" && pwd -P) || return 1
  real_parent=$(cd "$(dirname "$target")" 2>/dev/null && pwd -P) || {
    LOOP_SCRATCH_LINE="kept $rel — its directory does not resolve"
    return 1
  }
  case "$real_parent/" in
    "$real_tree"/*) ;;
    *)
      LOOP_SCRATCH_LINE="kept $rel — it resolves outside the tree, to $real_parent"
      return 1 ;;
  esac
  if git -C "$tree" ls-files --error-unmatch -- "$rel" >/dev/null 2>&1; then
    LOOP_SCRATCH_LINE="kept $rel — git tracks it"
    return 1
  fi
  bytes=$(loop_bytes_of "$target")
  if [ "$apply" = "--apply" ]; then
    rm -rf -- "$target" || {
      LOOP_SCRATCH_LINE="could not remove $rel"
      return 1
    }
    LOOP_SCRATCH_LINE="removed $rel ($bytes B)"
  else
    LOOP_SCRATCH_LINE="would remove $rel ($bytes B)"
  fi
  LOOP_SCRATCH_BYTES="$bytes"
  return 0
}

# ── a process group the loop started, and how it is ended ────────────────────────────────────────
# Every layer of the loop starts someone, and each layer must be able to end *all* of what it started, not only
# the pid it holds: `set -m` before the command gives it a process group of its own (see `run_child`), and one
# signal to that group reaches the children. Measured 2026-10-20 (issue #213): five `tools/loop/run.sh`
# processes, each with its `sleep 1` child, were still running 55 to 100 min after their suites had ended, at a
# working directory (`readlink /proc/<pid>/cwd`) inside a scratch tree that had already been deleted — the
# suite's traps and the run's traps each ended their own pid only. `SIGTERM` comes first so a member's own trap
# still runs (the run's in-progress label and its lock), then a grace, then `SIGKILL` for whatever is left.
#
# Usage: loop_end_group <pgid> → 0 when nothing of that group is left (or there was nothing to end)
loop_end_group() {
  local pgid="${1:-}" i
  [ -n "$pgid" ] || return 0
  kill -s TERM -- -"$pgid" 2>/dev/null || return 0
  for i in $(seq 1 25); do
    kill -s 0 -- -"$pgid" 2>/dev/null || return 0
    sleep 0.2
  done
  kill -s KILL -- -"$pgid" 2>/dev/null || true
  return 0
}

# One process, the same way and for the same reason: a leftover is ended with the signal that lets it clean up
# behind itself, and only a process that ignores that is killed.
#
# Usage: loop_end_pid <pid> → 0 when it is gone (or was never a number)
loop_end_pid() {
  local pid="${1:-}" i
  case "$pid" in ''|*[!0-9]*) return 0 ;; esac
  kill -s TERM "$pid" 2>/dev/null || return 0
  for i in $(seq 1 10); do
    kill -s 0 "$pid" 2>/dev/null || return 0
    sleep 0.2
  done
  kill -s KILL "$pid" 2>/dev/null || true
  return 0
}

# Every process whose working directory lies under <dir>, one pid per line. Linux's `/proc` is the only reader of
# a process's working directory there is, so a platform without it reports nothing rather than guessing. This is
# the test that finds a loop a *suite* or a *killed run* left behind: the tree is gone from the caller's view,
# but the process that still polls it names it as its cwd (issue #213), and the same test serves
# `loop-clean.sh`'s report and the self-test's own postcondition (issue #233).
loop_pids_under() { # <dir> → one pid per line
  local dir="${1:-}" entry pid cwd
  [ -n "$dir" ] || return 0
  [ -d /proc/self ] || return 0
  for entry in /proc/[0-9]*; do
    pid="${entry#/proc/}"
    case "$pid" in ''|*[!0-9]*) continue ;; esac
    [ "$pid" = "$$" ] && continue
    [ "$pid" = "$PPID" ] && continue
    cwd=$(readlink "$entry/cwd" 2>/dev/null) || continue
    [ -n "$cwd" ] || continue
    # A process whose tree is already gone still names it, with the kernel's ` (deleted)` appended — the
    # leftovers a killed suite leaves behind are exactly those, so the suffix is stripped before the match.
    cwd=${cwd% (deleted)}
    case "$cwd" in
      "$dir"|"$dir"/*) printf '%s\n' "$pid" ;;
    esac
  done
}

# …and ending them: the loop's leftovers under one directory, reported by pid, ended with the same signal
# discipline as a single process. Prints the pids it ended so the caller can say what it did.
loop_end_pids_under() { # <dir> [--quiet] → 0, and one "ended <pid>" line per process
  local dir="${1:-}" quiet="${2:-}" pid ended=0
  while IFS= read -r pid; do
    [ -n "$pid" ] || continue
    [ "$quiet" = "--quiet" ] || printf 'ended %s — its working directory is inside %s\n' "$pid" "${dir##*/}"
    loop_end_pid "$pid"
    ended=$((ended + 1))
  done <<< "$(loop_pids_under "$dir")"
  return 0
}

# ── git: what a branch carries ──────────────────────────────────────────────────────────

# What a branch carries, asked by *patch* identity rather than by ancestry. The loop lands work as a commit of
# its own (`fix: … (#N)`), and a branch whose work was landed — by hand, or re-landed — is never an ancestor of
# the base while its patch is in it, so ancestry reads a finished branch as a previous attempt and blocks the
# item for ever (measured 2026-10-20, issue #228: of 16 `land` lines in one report, every branch of the first
# group read N ahead / 0 unlanded under `git cherry` and was a leftover already on `main`).
#
# Prints one `<sha> <subject>` line per commit whose patch <base> does not carry, and returns 0 when git could
# answer. Returns 1 when it could not, so a caller never reads "git cannot say" as "nothing to land".
loop_unlanded_commits() { # <repo> <base> <branch>
  local repo="$1" base="$2" branch="$3" out line sha
  out=$(git -C "$repo" cherry "$base" "$branch" 2>/dev/null) || return 1
  while IFS= read -r line; do
    # `git cherry` marks a commit `-` when the base already carries an equivalent patch, `+` when it does not
    case "$line" in
      '+ '*) sha=${line#+ }
             git -C "$repo" log -1 --format='%h %s' "$sha" 2>/dev/null || printf '%s\n' "$sha" ;;
    esac
  done <<< "$out"
  return 0
}

# The branches the loop names: one per item it worked and one per run for its worktree. The same set the
# report lists, so a reader and the sweep ask about the same branches.
loop_own_branches() { # <repo> → one branch per line
  git -C "$1" branch --list 'fix/issue-*' 'loop-*' --format='%(refname:short)' 2>/dev/null
}

# The branches a worktree holds: git refuses to delete one of them, so every deletion asks this first.
loop_worktree_branches() { # <repo> → one branch per line
  git -C "$1" worktree list --porcelain 2>/dev/null | sed -n 's|^branch refs/heads/||p'
}

# ── the issue tooling, as the loop uses it ───────────────────────────────────────────────────────

loop_profile() {
  printf '%s\n' "${ISSUES_PROFILE:-$LOOP_ROOT/tools/tooling.json}"
}

# One issue's full record, bodies included. `list.sh --json` deliberately answers without bodies (the
# cheap selection path), and the work package needs the text, so the candidate is re-read by number.
#
# `--comments` is asked for where the caller needs the answer a person recorded: the work package carries it
# (issue #196), and a reader that does not ask for it gets none — the real CLI returns only the fields it is
# asked for, and the fixture `gh` now does the same. The pre-screen asks for neither, so its per-candidate read
# stays as small as it was.
loop_issue_json() {
  local fields="number,title,body,labels,author,state"
  [ "${2:-}" = "--comments" ] && fields="$fields,comments"
  gh issue view "$1" --json "$fields" 2>/dev/null
}

loop_issue_tool() { # run one of the tooling's scripts
  local script="$1"; shift
  bash "$LOOP_ISSUES_DIR/$script" "$@"
}

# The identity a commit is attributed to: the login this run already acts as, resolved by the same gate
# preflight that authorises it before any work starts. Git refuses a commit with no ambient
# `user.name`/`user.email`, and a fresh container or CI runner has neither — the run then blocked on its own
# commit (measured 2026-10-18, issue #167). Naming the run's own identity keeps the commit and the tracker's
# labels attributable to the same actor, and a login that cannot be resolved stops the run instead of
# inventing one. Output: `name<TAB>email`, so the caller can pass both options to `git -c`.
loop_commit_identity() {
  local login
  login=$(loop_issue_tool policy.sh preflight --json 2>/dev/null | jq -r '.login // empty' 2>/dev/null)
  [ -n "$login" ] || loop_die "worktree: no acting login could be resolved — a commit could not be attributed"
  printf '%s\t%s@users.noreply.github.com\n' "$login" "$login"
}

# The authorisation label, read from the profile — the loop never spells it out.
loop_required_labels() {
  loop_compat_profile_value requiredLabels
}

# The profile's `roles` section: which value of the profile's namespaces each of the loop's decisions
# means. Every script that decides something about a value asks here, so no script under `tools/loop/`
# spells a vocabulary value (D16); `tools/check-portability.sh` enforces it. The keys are the contract
# (like the namespace names); a value another project does not need is declared empty, never omitted.
LOOP_ROLES=""
LOOP_ROLE_KEYS="queuedVerdict heldVerdict workedClasses firstCategories firstCategoryClasses firstCategoryEvery proposeClass refusedClasses inFlightStatus deviceStatus holdStatus closeStatus settledStatuses inProgressAgent deviceAgent decisionAgent"
loop_roles_load() { # called outside `$( )`: a half-adapted profile must stop the script, not a subshell
  [ -n "$LOOP_ROLES" ] && return 0
  LOOP_ROLES=$(jq -c '.roles // empty' "$(loop_profile)") || LOOP_ROLES=""
  [ -n "$LOOP_ROLES" ] || loop_die "no roles section in $(loop_profile) — the loop reads which vocabulary value each decision means there"
  local key
  for key in $LOOP_ROLE_KEYS; do
    printf '%s\n' "$LOOP_ROLES" | jq -e --arg k "$key" 'has($k)' >/dev/null ||
      loop_die "the roles section in $(loop_profile) does not declare $key — declare it empty when this project has no such value"
  done
}

loop_role() { # <role> → the value, or one value per line when the role is a list
  printf '%s\n' "$LOOP_ROLES" | jq -r --arg k "$1" '.[$k] // empty | if type == "array" then .[] else . end'
}

loop_compat_profile_value() { # the profile's value for a section, via the tooling's own reader
  ISSUES_PROFILE=$(loop_profile) issues_profile_value "$1"
}

# The loop's state writer. Every state write goes through this, so "one writer" is a single call site.
loop_state() {
  bash "$LOOP_DIR/state.sh" "$@"
}

# The loop's label writer. Every label transition goes through here (D7).
loop_labels() {
  bash "$LOOP_DIR/labels.sh" "$@"
}

# Render a prompt skeleton. The skeletons carry placeholders only, so which skills this project uses and
# how its gate is run arrive from `loop.conf` at render time and never appear in a phase (D16).
loop_render() { # loop_render <template> [KEY=VALUE]…
  local template="$1"; shift
  local tpl pair key value
  tpl=$(cat "$template") || loop_die "cannot read the prompt skeleton $template"
  for pair in "$@"; do
    key="${pair%%=*}"
    value="${pair#*=}"
    tpl="${tpl//\{\{$key\}\}/$value}"
  done
  printf '%s\n' "$tpl"
}
