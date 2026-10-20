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
# `worktree.sh strip-scratch` uses) and only scratch trees under the loop's temporary root that are older
# than `LOOP_TMP_STALE_MINUTES` and carry the self-test's own name — a suite writing into its tree right now
# is younger than that and is left alone.
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
    -h|--help) sed -n '2,32p' "$0"; exit 0 ;;
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

# ── the scratch trees a killed suite left ────────────────────────────────────────────────────────
say "loop-clean: self-test scratch trees under ${LOOP_TMP_DIR#"$LOOP_ROOT"/} older than ${stale_minutes} min"
for dir in "$LOOP_TMP_DIR/$LOOP_SELFTEST_PREFIX"*; do
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
  if [ "$apply" -eq 1 ]; then
    rm -rf -- "$dir" || { say "could not remove ${dir##*/}"; continue; }
    say "  removed ${dir##*/} ($size B)"
  else
    say "  would remove ${dir##*/} ($size B)"
  fi
  paths=$((paths + 1)); bytes=$((bytes + size))
done

if [ "$paths" -eq 0 ]; then
  printf 'loop-clean: nothing to remove — %s path(s) kept\n' "$kept"
  exit 0
fi
if [ "$apply" -eq 1 ]; then
  printf 'loop-clean: removed %s path(s), %s B\n' "$paths" "$bytes"
else
  printf 'loop-clean: would remove %s path(s), %s B — nothing removed (pass --apply)\n' "$paths" "$bytes"
fi
exit 0
