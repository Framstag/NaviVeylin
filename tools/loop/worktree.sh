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
#   worktree.sh adopt-item <issue> <branch>
#   worktree.sh record-review-failure <issue> <change> <round>
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
    # carried, because the previous run's unfinished work is not this run's to commit — the run's own
    # pre-flight refuses this earlier, under the item the marker names and with the repair flag offered
    # (issue #173), and this refusal stays for `create` used on its own.
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
  # The one submodule path a fresh worktree does not inherit, read from the loop's configuration (D4):
  # the path is the project's, so the loop never names one.
  local submodule_path
  submodule_path=$(loop_conf_value LOOP_SUBMODULE_PATH)
  if [ -n "$submodule_path" ] && [ -f "$LOOP_ROOT/.gitmodules" ]; then
    gitw submodule update --init "$submodule_path" >&2 ||
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
  # The worktree's branch marker names the branch the tree is on, so a tree a killed run left behind still
  # says which item's work it carries — that is what the run's start-time pre-flight reads to attribute a
  # refusal (D7, issue #173).
  printf '%s\n' "$branch" > "$worktree/.pi/loop-branch"
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

# A previous attempt that finished carries three things the run can *check*, and they are what decides its
# branch: one archived change the base does not have (one root cause), and a verification record inside that
# change that is green. A branch a blocked attempt left behind carries neither, so the refusal, its escalation
# and the person who owns the decision stay exactly where they were (issue #176).
carried_change() { # <branch> <base> → the one archived change the branch adds, or nothing
  local branch="$1" base="$2" dirs count
  dirs=$(gitr diff --name-only --diff-filter=A "$base".."$branch" -- openspec/changes/archive 2>/dev/null |
    awk -F/ 'NF > 3 {print $4}' | sort -u)
  count=$(printf '%s' "$dirs" | grep -c . || true)
  [ "$count" -eq 1 ] || return 0
  # A change the base archived itself is not this branch's work, and cannot make it adoptable.
  [ -z "$(gitr ls-tree --name-only "$base" -- "openspec/changes/archive/$dirs")" ] || return 0
  printf '%s\n' "$dirs"
}

# A verification record is a JUnit testsuite that ran at least one test and passed all of them. The evidence
# directory of a *finished* attempt holds the red-on-HEAD XML that reproduced the defect beside the green gate
# XML — the two real branches issue #176 was measured on carry exactly that — so "green" cannot mean "no XML
# records a failure": it means "at least one XML records a passing run". A blocked attempt carries no such
# XML (its only XML is the red one), which is the difference the merge is allowed to act on.
carried_verification() { # <branch> <base> <change> → the evidence it carries, or nothing when it is not green
  local branch="$1" change="$3" files name passed=0
  files=$(gitr ls-tree -r --name-only "$branch" -- "openspec/changes/archive/$change/evidence" 2>/dev/null)
  printf '%s\n' "$files" | grep -q '\.xml$' || return 0
  while IFS= read -r name; do
    [ -n "$name" ] || continue
    case "$name" in *.xml) ;; *) continue ;; esac
    gitr show "$branch:$name" 2>/dev/null | grep -Eq 'tests="[1-9]' || continue
    gitr show "$branch:$name" 2>/dev/null | grep -Eq 'failures="0"' || continue
    gitr show "$branch:$name" 2>/dev/null | grep -Eq 'errors="[1-9]"' && continue
    passed=1
  done <<< "$files"
  [ "$passed" -eq 1 ] || return 0
  printf '%s\n' "$files" | sed "s#^openspec/changes/archive/$change/evidence/##" | paste -sd, -
}

# The branch of a finished attempt is merged onto the run's base and the item is closed by the run's own
# closure path (run.sh calls this, then `labels.sh close`) — no loop path merged one before, so an item whose
# earlier attempt finished but was never merged was permanently ineligible (issue #176). The merge is one
# recorded action: its inputs are printed here and recorded in the run state, and it is never taken on a
# branch that carries no green verification.
cmd_adopt_item() {
  local issue="${1:-}" branch="${2:-}"
  case "$issue" in ''|*[!0-9]*) loop_die "adopt-item: usage: worktree.sh adopt-item <issue> <branch>" ;; esac
  [ -n "$branch" ] || loop_die "adopt-item: usage: worktree.sh adopt-item <issue> <branch>"
  # The merge lands in the project's own tree, where the base branch is checked out — the pre-screen's
  # adoption happens before the run's worktree exists, so the worktree is not required here.
  check_base
  local base change evidence dirty commits who_name who_email
  base=$(state_base)
  gitr rev-parse --verify --quiet "refs/heads/$branch" >/dev/null ||
    loop_refuse "there is no branch $branch to adopt"
  change=$(carried_change "$branch" "$base")
  [ -n "$change" ] ||
    loop_refuse "branch $branch is not adopted — it adds no single archived change of its own"
  evidence=$(carried_verification "$branch" "$base" "$change")
  [ -n "$evidence" ] ||
    loop_refuse "branch $branch is not adopted — the change $change carries no green verification record"
  # The reviewer's refusal is evidence too, and it is the one fact that makes a finished-looking branch the
  # owner's: a branch whose archived change and green XML are all a later run can see was refused by the
  # reviewer that read the diff, so adopting it would close the item on a decision a person had just refused
  # (#185).
  refused_round=$(gitr ls-tree -r --name-only "$branch" -- "openspec/changes/archive/$change/evidence" 2>/dev/null |
    sed -n 's#.*/review-failed-round-\([0-9][0-9]*\)\.md$#\1#p' | sort -n | tail -1)
  [ -z "$refused_round" ] ||
    loop_refuse "branch $branch is not adopted — the reviewer refused $change in round $refused_round (evidence/review-failed-round-$refused_round.md); apply the reviewer's corrections and merge it by hand, or delete that file deliberately if the verdict no longer applies"
  commits=$(gitr rev-list --reverse "$base".."$branch" | tr '\n' ' ' | sed 's/ $//')
  [ -n "$commits" ] || loop_refuse "branch $branch carries no commit the base does not"
  # The merge lands in the project tree, where the base branch is checked out: over uncommitted work it would
  # attribute that work to the merge, which is the rule `create` already refuses for.
  dirty=$(gitr status --porcelain)
  [ -z "$dirty" ] || loop_refuse "the project tree is not clean — refusing to merge $branch onto the base:
$dirty"
  if gitr merge-base --is-ancestor "$base" "$branch"; then
    gitr merge --ff-only "$branch" >&2 || loop_die "adopt-item: the fast-forward failed"
  else
    IFS=$'\t' read -r who_name who_email <<< "$(loop_commit_identity)"
    # shellcheck disable=SC2086
    gitr -c user.name="$who_name" -c user.email="$who_email" cherry-pick $commits >&2 || {
      gitr cherry-pick --abort >&2 2>/dev/null || true
      loop_die "adopt-item: replaying $branch onto the base failed"
    }
  fi
  printf 'adopted: branch %s -> %s · commits %s · change %s · evidence %s\n' \
    "$branch" "$(gitr rev-parse --short HEAD)" "$(printf '%s' "$commits" | sed 's/ /,/g')" "$change" "$evidence"
}

# The reviewer's verdict is evidence, and evidence travels with the change (#185): a branch whose only record of
# the refusal is this run's machine-local item directory looks finished to the next run, which reads one archived
# change and a green XML and adopts exactly the change the reviewer refused. Recording it writes one file into
# the change's own `evidence/` directory and commits it on the item's branch, so the verdict leaves with the run.
cmd_record_review_failure() { # <issue> <change> <round>
  local issue="${1:-}" change="${2:-}" round="${3:-}" dir file who_name who_email
  case "$issue" in ''|*[!0-9]*) loop_die "record-review-failure: usage: worktree.sh record-review-failure <issue> <change> <round>" ;; esac
  [ -n "$change" ] && [ -n "$round" ] ||
    loop_die "record-review-failure: usage: worktree.sh record-review-failure <issue> <change> <round>"
  [ -d "$worktree" ] || loop_die "record-review-failure: no worktree at $worktree"
  [ -d "$worktree/openspec/changes/archive/$change" ] ||
    loop_refuse "record-review-failure: $change is not an archived change in the worktree"
  dir="$worktree/openspec/changes/archive/$change/evidence"
  file="$dir/review-failed-round-$round.md"
  mkdir -p "$dir" || loop_die "record-review-failure: could not create $dir"
  {
    printf 'The reviewer refused this change in review round %s.\n\n' "$round"
    printf 'Recorded by the run for issue #%s at %s, on the item branch, because a branch is what a later run\n' \
      "$issue" "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    printf 'can see: its archived change and its green verification say nothing about this verdict, and adopting\n'
    printf 'it would close the item on a decision the reviewer had just refused.\n'
  } > "$file" || loop_die "record-review-failure: could not write $file"
  gitw add -- "$file" >&2 || loop_die "record-review-failure: could not stage $file"
  IFS=$'\t' read -r who_name who_email <<< "$(loop_commit_identity)"
  gitw -c user.name="$who_name" -c user.email="$who_email" \
    commit -qm "review: $change failed round $round (#$issue)" >&2 ||
    loop_die "record-review-failure: could not commit the verdict"
  printf 'recorded: evidence/review-failed-round-%s.md in %s (%s)\n' "$round" "$change" \
    "$(gitw rev-parse --short HEAD)"
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
  record-review-failure) cmd_record_review_failure "$@" ;;
  adopt-item) cmd_adopt_item "$@" ;;
  head) cmd_head "$@" ;;
  status) cmd_status "$@" ;;
  remove) cmd_remove "$@" ;;
  ""|-h|--help) sed -n '2,25p' "$0" ;;
  *) loop_die "unknown command '$cmd'" ;;
esac
