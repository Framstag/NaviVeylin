#!/usr/bin/env bash
# loop-clean.sh — what the loop has left behind, in one report, and one way to remove it.
#
# The run keeps its worktree on purpose (the next run inherits a warm checkout), and a suite killed with
# `SIGKILL` leaves its scratch tree behind because nothing can trap that signal. Both are the loop's own
# leftovers, and until now both accumulated unmeasured: measured 2026-10-20 (issue #210), a root filesystem at
# 88 % with one kept worktree at 3.2 GB — of which 1.9 GB was module build output no item ever reads — and
# 90 MB of `loop-selftest.*` trees from four killed suites.
#
# This is the one command that answers "what did the loop leave, and how much is it": without `--apply` it
# prints every path it would remove with its size and removes nothing, and with `--apply` it removes exactly
# those paths. It is idempotent — a second run finds nothing and says so.
#
# What it never touches: a kept worktree's tracked or untracked *source*. It removes only the project's
# recorded build paths (`LOOP_WORKTREE_SCRATCH_PATHS`, the same list and the same four refusals the run's own
# `worktree.sh strip-scratch` uses) and only scratch trees older than `LOOP_TMP_STALE_MINUTES` that carry the
# self-test's own name — a suite writing into its tree right now is younger than that and is left alone. It scans
# **two** scratch roots, because a suite creates its trees under the root of the tree it runs in: the root this
# invocation resolved and the one the kept worktree resolves, where a gate that runs the suite inside the worktree
# writes them (measured 2026-10-23, issue #274: four trees, 305 MB, and four poll loops under
# `.pi/worktrees/loop/.pi/tmp`, and this report printed no entry at all).
#
# A tree is not all a killed suite leaves: the processes that were working in it keep running, and nothing in
# the tooling used to notice them (measured 2026-10-20, issue #213 — five `run.sh` processes, 55 to 100 min
# after their suites ended, at a working directory that had already been deleted). Their working directory is
# the evidence: a process standing inside a scratch tree this command is about to remove is reported with the
# tree and ended with `--apply`. A process inside a tree that is kept belongs to a live suite and is never
# signalled.
#
# Exit: 0 nothing to do, or done; 2 usage or an unusable configuration.
#
# Usage:
#   tools/loop/loop-clean.sh [--worktree <dir>] [--apply] [--quiet]
#
#   --worktree <dir>  the tree to look in (default: the loop's worktree; a relative name is relative to the
#                     project root)
#   --apply           remove what the report names; without it nothing is removed
#   --quiet           print the summary only, not one line per path
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/loop/lib.sh
source "$here/lib.sh"

apply=0
quiet=0
tree_arg=""
while [ $# -gt 0 ]; do
  case "$1" in
    --worktree) need_value "$@"; tree_arg="${2:-}"; shift 2 ;;
    --apply) apply=1; shift ;;
    --quiet) quiet=1; shift ;;
    -h|--help) sed -n '2,38p' "$0"; exit 0 ;;
    *) loop_die "loop-clean: unknown argument $1" ;;
  esac
done

loop_resolve
stale_minutes=$(loop_conf_value LOOP_TMP_STALE_MINUTES)
case "$stale_minutes" in ''|*[!0-9]*) stale_minutes=120 ;; esac

tree="${tree_arg:-$LOOP_WORKTREE}"
case "$tree" in /*) ;; *) tree="$LOOP_ROOT/$tree" ;; esac
apply_arg=""
[ "$apply" -eq 1 ] && apply_arg="--apply"

paths=0
bytes=0
kept=0
procs=0
branches=0
say() { [ "$quiet" -eq 1 ] && return 0; printf '%s\n' "$*"; }

# ── the kept worktree's build output ──────────────────────────────────────────────────────────────
if [ -d "$tree" ]; then
  say "loop-clean: build output in ${tree#"$LOOP_ROOT"/}"
  while IFS= read -r rel; do
    [ -n "$rel" ] || continue
    if loop_scratch_remove "$tree" "$rel" "$apply_arg"; then
      paths=$((paths + 1)); bytes=$((bytes + LOOP_SCRATCH_BYTES))
    else
      [ -z "$LOOP_SCRATCH_LINE" ] && continue
      kept=$((kept + 1))
    fi
    say "  $LOOP_SCRATCH_LINE"
  done <<< "$(loop_scratch_paths)"
else
  say "loop-clean: no worktree at $tree — no build output of the loop's to remove"
fi

# ── the scratch trees a killed suite left, and the processes still standing in them ─────────────
# Two roots, reported as two lines: this process resolved the first, and the kept worktree resolves the second —
# the trees a gate run inside the worktree leaves are made there, so a report that looked only at its own root
# answered "nothing to do" while they accumulated (issue #274). `--worktree .` names this tree, and then the two
# roots are one directory written two ways (`$LOOP_ROOT/.` against `$LOOP_ROOT`): it is scanned once, so a tree is
# never listed or counted twice — which is a comparison of the directories, not of the strings (round-1 review).
worktree_tmp=""
if [ -d "$tree" ]; then
  worktree_tmp=$(loop_tmp_dir_for "$tree")
  loop_same_dir "$worktree_tmp" "$LOOP_TMP_DIR" && worktree_tmp=""
fi
say "loop-clean: self-test scratch trees under ${LOOP_TMP_DIR#"$LOOP_ROOT"/} older than ${stale_minutes} min"
[ -z "$worktree_tmp" ] ||
  say "loop-clean: self-test scratch trees under ${worktree_tmp#"$LOOP_ROOT"/} older than ${stale_minutes} min"

# The processes a killed suite left. A tree is not all of it: a case's `run.sh` and the child it waited for keep
# polling a tree that no longer exists — measured 2026-10-20 (issue #213), five such processes 55 to 100 min
# after their suites had ended, every one of them at a working directory `readlink /proc/<pid>/cwd` reported
# inside a `loop-selftest.*` tree that had already been deleted. Their working directory is the evidence, and it
# is the same evidence the removal acts on: a process standing inside a tree this command is about to remove.
# `--apply` ends exactly those, and a process inside a *kept* tree is a live suite's and is never signalled.
# `$$` and its parent are skipped, so a report never proposes to end the caller itself. Linux's `/proc` is the
# only reader of a process's working directory there is; without it the report says so instead of guessing.
leftover_pids() { # <dir> → one pid per line
  local dir="$1" entry pid cwd
  [ -d /proc/self ] || return 0
  for entry in /proc/[0-9]*; do
    pid="${entry#/proc/}"
    case "$pid" in ''|*[!0-9]*) continue ;; esac
    [ "$pid" = "$$" ] && continue
    [ "$pid" = "$PPID" ] && continue
    cwd=$(readlink "$entry/cwd" 2>/dev/null) || continue
    [ -n "$cwd" ] || continue
    case "$cwd" in
      "$dir"|"$dir"/*) printf '%s\n' "$pid" ;;
    esac
  done
}

for dir in "$LOOP_TMP_DIR/$LOOP_SELFTEST_PREFIX"* \
           ${worktree_tmp:+"$worktree_tmp/$LOOP_SELFTEST_PREFIX"*}; do
  { [ -e "$dir" ] || [ -L "$dir" ]; } || continue
  if [ -L "$dir" ] || [ ! -d "$dir" ]; then
    say "  kept ${dir##*/} — a symlink, never followed"
    kept=$((kept + 1))
    continue
  fi
  # Age is the only signal a killed suite leaves: it holds no lock that outlives it, and a tree a live suite
  # is writing into has a fresh mtime.
  if [ -z "$(find "$dir" -maxdepth 0 -mmin +"$stale_minutes" 2>/dev/null)" ]; then
    say "  kept ${dir##*/} ($(loop_bytes_of "$dir") B) — younger than ${stale_minutes} min, a suite may be writing into it"
    kept=$((kept + 1))
    continue
  fi
  size=$(loop_bytes_of "$dir")
  if [ ! -d /proc/self ]; then
    say "  note: no /proc — the processes inside ${dir##*/} cannot be listed"
  fi
  # The processes first: one standing in a tree that is about to be removed is the leftover, and ending it before
  # the removal is what stops it polling a path that no longer exists.
  while IFS= read -r left_pid; do
    [ -n "$left_pid" ] || continue
    procs=$((procs + 1))
    if [ "$apply" -eq 1 ]; then
      loop_end_pid "$left_pid"
      say "  ended process $left_pid — its working directory is inside ${dir##*/}"
    else
      say "  would end process $left_pid — its working directory is inside ${dir##*/}"
    fi
  done <<< "$(leftover_pids "$dir")"
  if [ "$apply" -eq 1 ]; then
    rm -rf -- "$dir" || { say "could not remove ${dir##*/}"; continue; }
    say "  removed ${dir##*/} ($size B)"
  else
    say "  would remove ${dir##*/} ($size B)"
  fi
  paths=$((paths + 1)); bytes=$((bytes + size))
done

# ── the branches the loop made ───────────────────────────────────────────────────────────────────
# One branch per item the loop worked and one per run for its worktree: they are leftovers too, and
# nothing removed them — measured 2026-10-22, 43 of the 53 local branches were already merged into the
# base. A branch is a leftover by what it *carries*: the patches the base does not have, asked by patch
# identity (`git cherry`), never by ancestry — the loop lands work as `fix: … (#N)`, so a landed branch is
# not an ancestor of the base and ancestry named a leftover as work for ever (issue #228). A branch a
# worktree holds is a tree in use: git refuses to delete it, and the loop's own worktree is on one.
say "loop-clean: branches of the loop whose patches are in the base"
base_commit=$(git -C "$LOOP_ROOT" rev-parse --verify --quiet "${LOOP_BASE_REF:-main}" 2>/dev/null || true)
if [ -z "$base_commit" ]; then
  say "  kept every branch — the base ${LOOP_BASE_REF:-main} cannot be read"
  kept=$((kept + 1))
else
  held=$(loop_worktree_branches "$LOOP_ROOT" | tr '\n' ' ')
  while IFS= read -r branch; do
    [ -n "$branch" ] || continue
    case " $held " in
      *" $branch "*)
        say "  kept $branch — a worktree holds it"
        kept=$((kept + 1))
        continue ;;
    esac
    if unlanded=$(loop_unlanded_commits "$LOOP_ROOT" "$base_commit" "$branch"); then
      ahead=$(printf '%s\n' "$unlanded" | grep -c . || true)
    else
      ahead=1
    fi
    case "$ahead" in ''|*[!0-9]*) ahead=1 ;; esac
    if [ "$ahead" -gt 0 ]; then
      say "  kept $branch — $ahead commit(s) the base does not have"
      kept=$((kept + 1))
      continue
    fi
    if [ "$apply" -eq 1 ]; then
      # The force is the price of patch identity: a branch whose work was re-landed as a different commit is
      # not an ancestor of the base, and git's own guard would refuse a branch that carries nothing.
      git -C "$LOOP_ROOT" branch -D "$branch" >/dev/null 2>&1 || { say "  could not remove $branch"; continue; }
      say "  removed $branch (its patches are in the base)"
    else
      say "  would remove $branch (its patches are in the base)"
    fi
    branches=$((branches + 1))
  done <<< "$(loop_own_branches "$LOOP_ROOT")"
fi

if [ "$paths" -eq 0 ] && [ "$procs" -eq 0 ] && [ "$branches" -eq 0 ]; then
  printf 'loop-clean: nothing to remove — %s path(s) kept\n' "$kept"
  exit 0
fi
if [ "$apply" -eq 1 ]; then
  proc_note=""
  [ "$procs" -gt 0 ] && proc_note=" and ended $procs process(es)"
  branch_note=""
  [ "$branches" -gt 0 ] && branch_note=" and removed $branches branch(es)"
  printf 'loop-clean: removed %s path(s), %s B%s%s\n' "$paths" "$bytes" "$proc_note" "$branch_note"
else
  proc_note=""
  [ "$procs" -gt 0 ] && proc_note=" and end $procs process(es)"
  branch_note=""
  [ "$branches" -gt 0 ] && branch_note=" and remove $branches branch(es)"
  printf 'loop-clean: would remove %s path(s), %s B%s%s — nothing removed (pass --apply)\n' \
    "$paths" "$bytes" "$proc_note" "$branch_note"
fi
exit 0
