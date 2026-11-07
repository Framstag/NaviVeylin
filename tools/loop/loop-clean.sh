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
# The loop's own trees are leftovers too. The run keeps one worktree, so a tree beside it — under the directory that
# one lives in — is named by nothing at all, and this report had no arm for it (measured 2026-11-06, issue #803:
# `.pi/worktrees/` held ten directories and one of them was the run's). A tree is judged by what it *holds*, the
# reading a branch gets one level down: uncommitted changes, or a HEAD the base does not carry, keep it and are named
# as the reason; a base or a HEAD that cannot be read leaves the same question unanswered and keeps it too; anything
# else is named with its size and removed with `--apply`.
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
    -h|--help) sed -n '2,45p' "$0"; exit 0 ;;
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
base_commit=$(git -C "$LOOP_ROOT" rev-parse --verify --quiet "$(loop_base_ref)" 2>/dev/null || true)
if [ -z "$base_commit" ]; then
  say "  kept every branch — the base $(loop_base_ref) cannot be read"
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

# ── the worktrees no run keeps ─────────────────────────────────────────────────────────────────────
# The run keeps one tree (`LOOP_WORKTREE`), so a worktree under the directory that one lives in is named by nothing
# at all — not the configuration, not a state field, not a branch marker — and no arm reported it: the per-item trees
# of the runs that kept one tree per item accumulated unmeasured (issue #803; measured 2026-11-06, `.pi/worktrees/`
# held ten directories and the run's tree was one of them). A tree is a leftover by what it *holds*, the same
# reading a branch gets one level down: uncommitted changes, or a HEAD the base does not carry, are somebody's work
# and the tree is kept and named with that reason; anything else is what `--apply` removes. This arm runs after the
# branches, so a branch a tree nobody keeps holds is judged by the arm above first — the tree is released here and
# the branch is the next run's to remove, not this one's read of a tree it just deleted.
wt_root=$(dirname "$LOOP_WORKTREE")
say "loop-clean: worktrees under ${wt_root#"$LOOP_ROOT"/} that the run does not keep"
wt_base=$(git -C "$LOOP_ROOT" rev-parse --verify --quiet "$(loop_base_ref)" 2>/dev/null || true)
while IFS= read -r wt_path; do
  [ -n "$wt_path" ] || continue
  case "$wt_path" in
    "$wt_root"/?*) ;;
    *) continue ;;
  esac
  loop_same_dir "$wt_path" "$LOOP_WORKTREE" && continue
  loop_same_dir "$wt_path" "$tree" && continue
  wt_rel="${wt_path#"$LOOP_ROOT"/}"
  wt_dirty=$(git -C "$wt_path" status --porcelain 2>/dev/null)
  if [ -n "$wt_dirty" ]; then
    say "  kept $wt_rel — $(printf '%s\n' "$wt_dirty" | grep -c .) uncommitted path(s), somebody is working in it"
    kept=$((kept + 1))
    continue
  fi
  wt_head=$(git -C "$wt_path" rev-parse --verify --quiet HEAD 2>/dev/null || true)
  # An unreadable base or HEAD is the unknown the branch arm one screen above keeps every branch on — deleting a
  # tree because a reading failed is how work is thrown away on a question nobody answered (issue #803, round 1).
  if [ -z "$wt_base" ] || [ -z "$wt_head" ]; then
    say "  kept $wt_rel — the base $(loop_base_ref) or the tree's HEAD cannot be read"
    kept=$((kept + 1))
    continue
  fi
  if ! git -C "$LOOP_ROOT" merge-base --is-ancestor "$wt_head" "$wt_base" 2>/dev/null; then
    wt_ahead=$(git -C "$LOOP_ROOT" rev-list --count "$wt_base..$wt_head" 2>/dev/null || true)
    case "$wt_ahead" in ''|*[!0-9]*) wt_ahead=1 ;; esac
    say "  kept $wt_rel — $wt_ahead commit(s) the base does not have"
    kept=$((kept + 1))
    continue
  fi
  wt_size=$(loop_bytes_of "$wt_path")
  if [ "$apply" -eq 1 ]; then
    git -C "$LOOP_ROOT" worktree remove --force "$wt_path" >/dev/null 2>&1 ||
      { say "  could not remove $wt_rel"; continue; }
    say "  removed $wt_rel ($wt_size B) — no run keeps it"
  else
    say "  would remove $wt_rel ($wt_size B) — no run keeps it"
  fi
  paths=$((paths + 1)); bytes=$((bytes + wt_size))
done <<< "$(git -C "$LOOP_ROOT" worktree list --porcelain 2>/dev/null | sed -n 's|^worktree ||p')"

# ── the change an item consumed, in the project's own tree ─────────────────────────────────────────
# The run writes nothing in the project's tree, so what remains of a prepared change after its item landed is the
# source directory its worktree copy was taken from (`worktree.sh start-item --change`). It is reported only for an
# item the state records as done: a source an unfinished item left is that item's unfinished work, and this command
# is not a second discard path. It removes nothing but the path it named, never a path that leaves the project.
say "loop-clean: change sources the landed items consumed"
if [ ! -f "$LOOP_STATE_FILE" ]; then
  say "  none — no run state at ${LOOP_STATE_FILE#"$LOOP_ROOT"/}"
  kept=$((kept + 1))
else
  while IFS=$'\t' read -r consumed_issue consumed_source; do
    [ -n "$consumed_source" ] || continue
    case "$consumed_source" in
      /*|..|*/../*|*/..|*..)
        say "  kept $consumed_source — not a path inside the project"
        kept=$((kept + 1))
        continue ;;
    esac
    consumed_dir="$LOOP_ROOT/$consumed_source"
    [ -e "$consumed_dir" ] || [ -L "$consumed_dir" ] || continue
    size=$(loop_bytes_of "$consumed_dir")
    if [ "$apply" -eq 1 ]; then
      rm -rf -- "$consumed_dir" || { say "  could not remove $consumed_source"; continue; }
      say "  removed $consumed_source ($size B) — the change #$consumed_issue consumed"
    else
      say "  would remove $consumed_source ($size B) — the change #$consumed_issue consumed"
    fi
    paths=$((paths + 1)); bytes=$((bytes + size))
  done < <(jq -r '
    . as $s
    | (($s.items // []) | map(select((.result // "") == "done") | (.issue | tostring))) as $landed
    | (($s.changeCopies // {}) | to_entries[])
    | select(.key as $k | $landed | index($k) != null)
    | [.key, (.value.source // "")] | @tsv' "$LOOP_STATE_FILE" 2>/dev/null)
fi

# ── the item directories the run kept ─────────────────────────────────────────────────────────────
# The largest thing the loop keeps is the item directories beside its state: one per item, holding that item's event
# stream, its package and its reports, and nothing reported them (issue #802; measured 2026-11-06, `.pi/issue-loop/`
# held 3.6 GB over 264 item directories, one event file 63 MiB). They are a leftover like the others, and `--apply` is
# still the only path that removes one — but they are also the loop's evidence, so a directory is released only when
# two readings agree: nothing inside it has been written for `LOOP_ITEM_ARCHIVE_DAYS`, and no open issue still cites
# it. Whether an issue is open is read from the run's own record, offline: an item whose result is `done` and whose
# closure happened (`metrics.closed: yes`) is one the tracker closed, and anything else — no record, another result,
# a landing the run was not allowed to close — is kept and named with that reason. A state file that cannot be read
# keeps every directory, the same answer an unreadable base gets from the branch arm.
items_root="$(dirname "$LOOP_STATE_FILE")/items"
archive_days=$(loop_conf_value LOOP_ITEM_ARCHIVE_DAYS)
case "$archive_days" in ''|*[!0-9]*) archive_days=0 ;; esac
say "loop-clean: item directories under ${items_root#"$LOOP_ROOT"/} that no open issue cites, older than ${archive_days} days"
if [ "$archive_days" -eq 0 ]; then
  say "  kept every item — LOOP_ITEM_ARCHIVE_DAYS records no retention, so no item directory is released"
  kept=$((kept + 1))
elif [ ! -d "$items_root" ]; then
  say "  none — no item directory at ${items_root#"$LOOP_ROOT"/}"
else
  while IFS= read -r item_path; do
    [ -n "$item_path" ] || continue
    item_n=${item_path##*/}
    case "$item_n" in ''|*[!0-9]*) continue ;; esac
    if [ -n "$(find "$item_path" -mmin -$(( archive_days * 1440 )) 2>/dev/null)" ]; then
      say "  kept items/$item_n — written within ${archive_days} days, an open issue may still cite it"
      kept=$((kept + 1))
      continue
    fi
    cited=$(jq -r --arg n "$item_n" '
      [ .items[]? | select((.issue | tostring) == $n)
        | ((.result // "") == "done" and ((.metrics.closed // "no") == "yes")) ]
      | if length == 0 then "unreadable" else (if any then "landed" else "cited" end) end' \
      "$LOOP_STATE_FILE" 2>/dev/null)
    case "$cited" in
      landed) ;;
      cited)
        say "  kept items/$item_n — the issue it belongs to is not closed, so it still cites the directory"
        kept=$((kept + 1))
        continue ;;
      *)
        say "  kept items/$item_n — the run state does not record what became of its issue"
        kept=$((kept + 1))
        continue ;;
    esac
    size=$(loop_bytes_of "$item_path")
    if [ "$apply" -eq 1 ]; then
      rm -rf -- "$item_path" || { say "  could not remove items/$item_n"; continue; }
      say "  removed items/$item_n ($size B) — landed and closed, and older than any open issue cites"
    else
      say "  would remove items/$item_n ($size B) — landed and closed, and older than any open issue cites"
    fi
    paths=$((paths + 1)); bytes=$((bytes + size))
  done <<< "$(find "$items_root" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort)"
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
