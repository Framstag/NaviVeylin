#!/usr/bin/env bash
# worktree.sh — the working tree and the branches, owned by the run and never by an agent.
#
# Git is the part of the landing step that is mechanical, and this repository's most fragile one
# (design D4, proposal "Why"): one worktree per run so the measured 11-minute cold build is paid once,
# one branch per item off the run's recorded base so items can land independently, and a commit per
# step with the shape this repository already uses. No phase of an item invokes version control on its
# own behalf, and nothing pushes unless the run asked to publish.
#
# A stale base is refused rather than worked on: an item built on a tree nobody reviewed is worse than
# an item not started (D4). The worktree is kept at the end of a run — it is the next run's warm build —
# and removed only when the run asked for it.
#
# Exit: 0 ok, 2 usage, 3 stop, 5 refuse.
#
# Usage:
#   worktree.sh create [--base <sha>] [--branch <name>]
#   worktree.sh start-item <issue> <slug>
#   worktree.sh end-item <issue> <slug>
#   worktree.sh head
#   worktree.sh status
#   worktree.sh remove
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/loop/lib.sh
source "$here/lib.sh"

loop_resolve
worktree="$LOOP_WORKTREE"

gitw() { git -C "$worktree" "$@"; }
gitr() { git -C "$LOOP_ROOT" "$@"; }

state_base() {
  bash "$LOOP_DIR/state.sh" get run.base
}

cmd_create() {
  local base="" branch="" dirty
  while [ $# -gt 0 ]; do
    case "$1" in
      --base) base="${2:-}"; shift 2 ;;
      --branch) branch="${2:-}"; shift 2 ;;
      *) loop_die "create: unknown argument $1" ;;
    esac
  done
  [ -n "$base" ] || base=$(state_base) || loop_die "create: no base commit — run 'state.sh init' first"
  [ -n "$branch" ] || branch="loop-$(date -u +%Y%m%d-%H%M%S)"

  if [ -e "$worktree" ]; then
    # The worktree is kept at the end of a run so the next run inherits its warm build (D4): a second
    # run reuses it, on a branch of its own off its own base. A dirty tree is refused rather than
    # carried, because the previous run's unfinished work is not this run's to commit.
    gitr worktree list --porcelain | grep -qx "worktree $worktree" ||
      loop_refuse "$worktree exists but is not this repository's worktree — remove it and run again"
    dirty=$(git -C "$worktree" status --porcelain)
    [ -z "$dirty" ] || loop_refuse "the worktree at $worktree is not clean — the previous run left work behind:
$dirty"
    git -C "$worktree" switch -c "$branch" "$base" >&2 || loop_die "create: could not branch from the base in the kept worktree"
    printf '%s\n' "$branch" > "$worktree/.pi/loop-branch"
    printf 'worktree %s reused (branch %s from %s)\n' "$worktree" "$branch" "${base:0:12}"
    return 0
  fi
  gitr worktree add "$worktree" -b "$branch" "$base" >&2 || loop_die "create: git worktree add failed"

  # Four inputs a fresh worktree does not inherit (REFERENCE.md, "Running the loop in a worktree").
  # Each is best-effort with a note, because a project with no submodule, no local properties or no
  # vendored dependency is a project the loop still has to serve (D16).
  if [ -f "$LOOP_ROOT/.gitmodules" ]; then
    gitw submodule update --init app/src/main/cpp/libosmscout >&2 ||
      loop_note "worktree: the submodule could not be checked out — the build may not be able to run"
  fi
  local local_props=0
  for f in "$LOOP_ROOT"/local.prop*; do
    [ -e "$f" ] || continue
    cp "$f" "$worktree/" && local_props=$((local_props + 1))
  done
  mkdir -p "$worktree/.pi/skills"
  cp -a "$LOOP_ROOT"/.pi/skills/openspec-* "$worktree/.pi/skills/" 2>/dev/null || true
  cp -a "$LOOP_ROOT/.pi/skills/fix-loop" "$worktree/.pi/skills/" 2>/dev/null || true

  # `VCPKG_ROOT` is exported for the children from a file inside the worktree, not as a symlink: a
  # symlink named `vcpkg` is untracked and pollutes `git status`, which the run reads for evidence.
  {
    printf '# Written by tools/loop/worktree.sh — the machine-local environment a child needs.\n'
    printf 'export VCPKG_ROOT=%q\n' "$LOOP_ROOT/vcpkg"
  } > "$worktree/.pi/loop-env.sh"

  printf 'worktree %s on branch %s from %s (%s local.prop* copied)\n' \
    "$worktree" "$branch" "${base:0:12}" "$local_props"
  printf '%s\n' "$branch" > "$worktree/.pi/loop-branch"
}

# The base the run recorded is the commit every item descends from. If the branch the run started on
# has moved, the next item would be built on a tree nobody reviewed, so it is refused with both
# commits named (D4).
base_ref() { printf '%s\n' "${LOOP_BASE_REF:-main}"; }

check_base() {
  local base ref current
  base=$(state_base)
  ref=$(base_ref)
  current=$(gitr rev-parse --verify --quiet "$ref") || { loop_note "worktree: no '$ref' to compare the base against"; return 0; }
  if [ "$current" != "$base" ]; then
    printf 'base moved: %s is %s but this run recorded %s — refusing to build on a stale base\n' \
      "$ref" "${current:0:12}" "${base:0:12}"
    printf '  %s\n  %s\n' "$(gitr log -1 --format='%h %s' "$base")" "$(gitr log -1 --format='%h %s' "$current")"
    exit "$LOOP_REFUSE"
  fi
}

cmd_start_item() {
  local issue="${1:-}" slug="${2:-}"
  case "$issue" in ''|*[!0-9]*) loop_die "start-item: usage: worktree.sh start-item <issue> <slug>" ;; esac
  [ -n "$slug" ] || loop_die "start-item: usage: worktree.sh start-item <issue> <slug>"
  [ -d "$worktree" ] || loop_die "start-item: no worktree at $worktree — run 'create' first"
  check_base
  # One writer per tree: a dirty tree is another item's unfinished work, and committing over it would
  # attribute two changes to one branch.
  local dirty
  dirty=$(gitw status --porcelain)
  [ -z "$dirty" ] || loop_refuse "the worktree is not clean — refusing to start #$issue:
$dirty"
  # An earlier run's branch for the same item. When every commit on it is already in the base's history it
  # carries no work of its own — a blocked run that committed nothing leaves exactly that — so the item
  # starts on it again. When it carries a commit the base does not have, that commit *is* the previous
  # attempt's work and the only place the run recorded it, so starting over would throw it away: refused
  # with the branch and its commits named, and a person decides (D4, D7, D9).
  local branch="fix/issue-$issue-$slug" base
  base=$(state_base)
  if gitw rev-parse --verify --quiet "refs/heads/$branch" >/dev/null; then
    if gitw merge-base --is-ancestor "$branch" "$base"; then
      gitw switch -C "$branch" "$base" >&2 || loop_die "start-item: git switch failed"
      printf 'item #%s on branch %s (reused — the branch carries no commit of its own)\n' "$issue" "$branch"
    else
      loop_refuse "branch $branch already exists and carries commits the base does not — a previous attempt's work is on it:
$(gitw log --oneline "$base".."$branch" | head -5)"
    fi
  else
    gitw switch -c "$branch" "$base" >&2 || loop_die "start-item: git switch failed"
    printf 'item #%s on branch %s\n' "$issue" "$branch"
  fi
}

cmd_end_item() {
  local issue="${1:-}" slug="${2:-}"
  case "$issue" in ''|*[!0-9]*) loop_die "end-item: usage: worktree.sh end-item <issue> <slug>" ;; esac
  [ -n "$slug" ] || loop_die "end-item: usage: worktree.sh end-item <issue> <slug>"
  # Two commits, in the order the archive requires: the fix first, then the archive with its evidence.
  # The archive travels with the fix, but it is not the fix — a later revert of the fix must not have to
  # revert the record of why it was made.
  gitw add -A -- . ':(exclude)openspec/changes/archive' >&2
  # The identity is the run's own (design D1): git refuses a commit with nothing configured, and a fresh
  # runner or container has nothing, so relying on the machine would block the item on its own commit.
  local who_name who_email
  IFS=$'\t' read -r who_name who_email <<< "$(loop_commit_identity)"
  local committed=""
  if ! gitw diff --cached --quiet; then
    gitw -c user.name="$who_name" -c user.email="$who_email" commit -q -m "fix: $slug (#$issue)" >&2 ||
      loop_die "end-item: could not commit the fix"
    committed="fix"
  fi
  gitw add -A >&2
  if ! gitw diff --cached --quiet; then
    gitw -c user.name="$who_name" -c user.email="$who_email" commit -q \
      -m "fix: $slug (#$issue) — archive with its evidence" >&2 ||
      loop_die "end-item: could not commit the archive"
    committed="$committed archive"
  fi
  printf 'committed:%s\n' "${committed:- nothing}"
}

cmd_head() { [ -d "$worktree" ] || loop_die "head: no worktree at $worktree"; gitw rev-parse HEAD; }
cmd_status() { [ -d "$worktree" ] || loop_die "status: no worktree at $worktree"; gitw status --porcelain; }

cmd_remove() {
  local branch=""
  [ -e "$worktree/.pi/loop-branch" ] && branch=$(cat "$worktree/.pi/loop-branch")
  if [ -d "$worktree" ]; then
    gitr worktree remove --force "$worktree" >&2 || loop_die "remove: git worktree remove failed"
  fi
  printf 'worktree removed%s\n' "${branch:+ (branch $branch kept)}"
}

cmd="${1:-}"; shift || true
case "$cmd" in
  create) cmd_create "$@" ;;
  start-item) cmd_start_item "$@" ;;
  end-item) cmd_end_item "$@" ;;
  head) cmd_head "$@" ;;
  status) cmd_status "$@" ;;
  remove) cmd_remove "$@" ;;
  ""|-h|--help) sed -n '2,25p' "$0" ;;
  *) loop_die "unknown command '$cmd'" ;;
esac
