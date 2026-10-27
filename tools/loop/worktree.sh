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
# an item not started (D4). The worktree is kept at the end of a run — it is the next run's warm checkout —
# and removed only when the run asked for it. A kept tree keeps its sources and its git state and loses the
# module build directories the project records (`strip-scratch`): the next build regenerates them and no
# item reads them, and carrying them cost 1.9 GB per kept worktree (issue #210).
#
# Exit: 0 ok, 2 usage, 3 stop, 5 refuse.
#
# Usage:
#   worktree.sh create [--base <sha>] [--branch <name>]
#   worktree.sh start-item <issue> <slug>
#   worktree.sh end-item <issue> <slug>
#   worktree.sh adopt-item <issue> <branch>
#   worktree.sh branch-state <issue> <branch>
#   worktree.sh merge-item <issue> <branch>
#   worktree.sh release-branch <branch>
#   worktree.sh prune-landed [--apply]
#   worktree.sh record-review-round <issue> <change> <round> requested|passed
#   worktree.sh record-review-failure <issue> <change> <round>
#   worktree.sh head
#   worktree.sh status
#   worktree.sh strip-scratch
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

# The one submodule path a worktree does not inherit, read from the loop's configuration (D4): the path is the
# project's, so the loop never names one. Both paths of `create` owe it — the fresh one inherits no module at all,
# and the reused one inherits the previous branch's (issue #309) — so it is one step with one note. Best-effort,
# because a project with no submodule, no local properties or no vendored dependency is a project the loop still
# has to serve (D16).
sync_submodule() { # <the name this step prints itself under>
  local path
  path=$(loop_conf_value LOOP_SUBMODULE_PATH)
  [ -n "$path" ] && [ -f "$worktree/.gitmodules" ] || return 0
  gitw submodule update --init --checkout "$path" >&2 ||
    loop_note "$1: the submodule could not be checked out — the build may not be able to run"
}

cmd_create() {
  local base="" branch="" dirty
  while [ $# -gt 0 ]; do
    case "$1" in
      --base) need_value "$@"; base="${2:-}"; shift 2 ;;
      --branch) need_value "$@"; branch="${2:-}"; shift 2 ;;
      *) loop_die "create: unknown argument $1" ;;
    esac
  done
  [ -n "$base" ] || base=$(state_base) || loop_die "create: no base commit — run 'state.sh init' first"
  [ -n "$branch" ] || branch="loop-$(date -u +%Y%m%d-%H%M%S)"

  if [ -e "$worktree" ]; then
    # The worktree is kept at the end of a run so the next run inherits its warm checkout (D4): a second
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
    # The switch moves the superproject's gitlink to the commit the new base records and leaves the module's own
    # worktree on the pin the previous branch carried, so the tree is dirty the moment it is switched: `start-item`
    # refuses it mid-run, under no item, and the run ends with every queued item unworked (issue #309). The tree is
    # clean before the switch — that is what the refusal above asserts — so the checkout below discards nothing of a
    # previous run's work; it places the module on the pin this base records, as the fresh path does.
    sync_submodule worktree
    printf '%s\n' "$branch" > "$worktree/.pi/loop-branch"
    printf 'worktree %s reused (branch %s from %s)\n' "$worktree" "$branch" "${base:0:12}"
    return 0
  fi
  gitr worktree add "$worktree" -b "$branch" "$base" >&2 || loop_die "create: git worktree add failed"

  # Four inputs a fresh worktree does not inherit (REFERENCE.md, "Running the loop in a worktree").
  # Each is best-effort with a note, because a project with no submodule, no local properties or no
  # vendored dependency is a project the loop still has to serve (D16).
  sync_submodule worktree
  local local_props=0
  for f in "$LOOP_ROOT"/local.prop*; do
    [ -e "$f" ] || continue
    cp "$f" "$worktree/" && local_props=$((local_props + 1))
  done
  mkdir -p "$worktree/.pi/skills"
  cp -a "$LOOP_ROOT"/.pi/skills/openspec-* "$worktree/.pi/skills/" 2>/dev/null || true
  # Only the *generated* skills are seeded: `.pi/skills/openspec-*` is gitignored, so a fresh worktree does not
  # carry it, while the hand-written skills are versioned and arrive with the checkout. A copied one that is
  # versioned was a second copy of the tree's own file and nothing read it (issue #255).

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
base_ref() { loop_base_ref; }

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

# The landing step merges into the project's **own tree**, so what a merge there moves is the branch that tree has
# checked out — never "the base" in the abstract. `check_base` compares the base *ref* with the base this run
# recorded and answers nothing about the checkout, so the precondition the two merge commands' comments assert is
# verified here instead of being relied on: a tree switched to another branch mid-run — the start's own guard
# (#301) covers the tree a run is opened on, not one a person switches while it is open — either fails the
# fast-forward after an item's whole budget, or, when the checkout merely fast-forwards to the item's branch,
# absorbs the merge while the base stays where it was. The run then re-reads the unchanged base, records it, and
# closes the item with its fix on neither (issue #302, from the read-only review of #301). Both branches are named,
# because the repair is a `switch` to the second one.
check_checkout() {
  local branch
  branch=$(gitr symbolic-ref --quiet --short HEAD 2>/dev/null) || branch=""
  if [ "$branch" != "$(base_ref)" ]; then
    loop_refuse "the project tree is on ${branch:-a detached HEAD}, not the base branch $(base_ref) — the merge lands in this tree, so switch it to $(base_ref) first"
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
  # An earlier run's branch for the same item, decided by what it *carries* (#228, #232):
  #   · nothing of its own — every patch is already in the base's history (`loop_unlanded_commits` is empty,
  #     which is how a branch whose work landed as a commit of the loop's own reads) — so the item starts on it
  #     again, because a blocked run that committed nothing leaves exactly that;
  #   · a blocked attempt (commits, no archived change of its own) — nothing on it states a question, and its
  #     commits are kept by renaming the branch aside, so the item is *worked* instead of being parked forever;
  #   · a finished attempt (an archived change of its own) — its change and evidence are the attempt's verdict
  #     and starting over would discard them, so it is refused with the branch and its commits named: `run.sh`
  #     adopts what is adoptable and escalates the rest (D4, D7, D9).
  # Patch identity, never ancestry alone: the loop lands work as `fix: … (#N)`, so a landed branch is not an
  # ancestor of the base and ancestry would refuse a leftover for ever (issue #228).
  local branch="fix/issue-$issue-$slug" base state set_aside carried
  base=$(state_base)
  if gitw rev-parse --verify --quiet "refs/heads/$branch" >/dev/null; then
    carried=$(loop_unlanded_commits "$LOOP_ROOT" "$base" "$branch") ||
      loop_refuse "start-item: git cannot say what branch $branch carries — refusing to start #$issue on a branch nobody could read"
    state=$(branch_state_of "$issue" "$branch" "$base")
    if [ -z "$carried" ] || [ "$state" = "landed" ]; then
      gitw switch -C "$branch" "$base" >&2 || loop_die "start-item: git switch failed"
      printf 'item #%s on branch %s (reused — the branch carries no commit of its own)\n' "$issue" "$branch"
    elif [ "$state" = "unfinished" ]; then
      set_aside="loop/leftover/issue-$issue-$slug-$(date -u +%Y%m%dT%H%M%SZ)"
      gitw branch -m "$branch" "$set_aside" >&2 ||
        loop_die "start-item: could not set the blocked attempt's branch aside"
      printf 'set-aside: branch %s carries a blocked attempt (no archived change of its own) — renamed to %s, its commits are kept\n' \
        "$branch" "$set_aside"
      gitw switch -c "$branch" "$base" >&2 || loop_die "start-item: git switch failed"
      printf 'item #%s on branch %s (fresh — the blocked attempt was set aside)\n' "$issue" "$branch"
    else
      loop_refuse "branch $branch already exists and carries commits the base does not — a previous attempt's work is on it:
$(printf '%s\n' "$carried" | head -5)"
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
#
# The change must be this branch's **own** work, and that is what the commit subject decides: the run lands work
# as `fix: <slug> (#<issue>)` (`end-item` below), so a change whose adding commit names another issue came in on
# history the branch shares with somebody else's attempt. Counting it credits the branch with work it never did —
# measured on #230, whose four-commit branch held #126's commit and was refused for
# `2026-10-19-fix-i18n-gate-state-field-text`, a change that has nothing to do with the item (issue #232).
carried_change() { # <issue> <branch> <base> → the one archived change the branch is credited with, or nothing
  local issue="$1" branch="$2" base="$3" dirs count added
  dirs=$(gitr diff --name-only --diff-filter=A "$base".."$branch" -- openspec/changes/archive 2>/dev/null |
    awk -F/ 'NF > 3 {print $4}' | sort -u)
  count=$(printf '%s' "$dirs" | grep -c . || true)
  [ "$count" -eq 1 ] || return 0
  # A change the base archived itself is not this branch's work, and cannot make it adoptable.
  [ -z "$(gitr ls-tree --name-only "$base" -- "openspec/changes/archive/$dirs")" ] || return 0
  added=$(gitr log --reverse --format=%H --diff-filter=A "$base".."$branch" -- "openspec/changes/archive/$dirs" | head -1)
  [ -n "$added" ] || return 0
  gitr log -1 --format=%s "$added" 2>/dev/null | grep -qF "(#$issue)" || return 0
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

# The reviewer's verdict is evidence, and the *round* is evidence too (#262): the answer alone cannot be told
# apart from a reviewer that never wrote one, so a branch whose round was announced and never answered looked
# exactly like a branch whose round passed. Measured 2026-10-22: the round-2 reviewer of #249 was killed
# mid-answer, its report was 0 bytes, and the next run adopted the branch and closed the item although nobody had
# graded the change. A round is therefore written on the branch twice — announced before the reviewer starts, and
# answered when it arrives — and the highest round the branch names is its last.
#
#   review-round-<n>-requested.md   the run announced the round; no answer arrived yet
#   review-round-<n>-passed.md      the round answered, and the change passed it
#   review-failed-round-<n>.md      the round answered, and it refused the change (#185)
#
# A round that carries an answer reads as that answer, and a refusal outranks a pass if a branch ever carries
# both: the reading is the conservative one, as an old-shaped review never passes.
carried_review_state() { # <branch> <change> → "<round> <passed|failed|requested>", or nothing
  local branch="$1" change="$2" files round
  files=$(gitr ls-tree -r --name-only "$branch" -- "openspec/changes/archive/$change/evidence" 2>/dev/null) || return 0
  round=$(printf '%s\n' "$files" |
    sed -n -e 's#.*/review-failed-round-\([0-9][0-9]*\)\.md$#\1#p' \
      -e 's#.*/review-round-\([0-9][0-9]*\)-requested\.md$#\1#p' \
      -e 's#.*/review-round-\([0-9][0-9]*\)-passed\.md$#\1#p' |
    sort -n | tail -1)
  [ -n "$round" ] || return 0
  case "$files" in *"/review-failed-round-$round.md"*) printf '%s failed\n' "$round"; return 0 ;; esac
  case "$files" in *"/review-round-$round-passed.md"*) printf '%s passed\n' "$round"; return 0 ;; esac
  printf '%s requested\n' "$round"
}

# What a branch is, as one value, because three callers ask the same question and must not answer it
# differently: `adopt-item` merges what finished, the pre-screen records what a person must decide, and
# `start-item` may only set aside what carries nothing of its own (#232).
#
#   adoptable          one archived change of its own, a green verification, no reviewer's refusal, and no
#                      review round left unanswered
#   review-refused     the same, and the reviewer refused it — the owner's (#185)
#   review-unanswered  the same, and the last review round was announced and never answered — the owner's (#262)
#   archived-not-green the change is there and never verified — the owner's (#176)
#   unfinished         commits and no archived change of its own: a blocked attempt, so work rather than a question
#   landed             every commit is already in the base — the item is startable on that branch again
#   absent             there is no such branch
branch_state_of() { # <issue> <branch> <base> → the state, one word
  local issue="$1" branch="$2" base="$3" change evidence review_state
  gitr rev-parse --verify --quiet "refs/heads/$branch" >/dev/null || { printf 'absent\n'; return 0; }
  if gitr merge-base --is-ancestor "$branch" "$base" 2>/dev/null; then
    printf 'landed\n'
    return 0
  fi
  change=$(carried_change "$issue" "$branch" "$base")
  [ -n "$change" ] || { printf 'unfinished\n'; return 0; }
  evidence=$(carried_verification "$branch" "$base" "$change")
  [ -n "$evidence" ] || { printf 'archived-not-green\n'; return 0; }
  review_state=$(carried_review_state "$branch" "$change")
  case "$review_state" in
    *' failed') printf 'review-refused\n'; return 0 ;;
    *' requested') printf 'review-unanswered\n'; return 0 ;;
  esac
  printf 'adoptable\n'
}

cmd_branch_state() { # <issue> <branch> → <state> TAB <change> TAB <evidence>
  local issue="${1:-}" branch="${2:-}" base="" state="" change="" evidence=""
  case "$issue" in ''|*[!0-9]*) loop_die "branch-state: usage: worktree.sh branch-state <issue> <branch>" ;; esac
  [ -n "$branch" ] || loop_die "branch-state: usage: worktree.sh branch-state <issue> <branch>"
  base=$(state_base)
  state=$(branch_state_of "$issue" "$branch" "$base")
  case "$state" in
    adoptable|review-refused|review-unanswered|archived-not-green)
      change=$(carried_change "$issue" "$branch" "$base")
      evidence=$(carried_verification "$branch" "$base" "$change")
      ;;
  esac
  printf '%s\t%s\t%s\n' "$state" "$change" "$evidence"
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
  check_checkout
  local base change evidence dirty commits who_name who_email review_state
  base=$(state_base)
  gitr rev-parse --verify --quiet "refs/heads/$branch" >/dev/null ||
    loop_refuse "there is no branch $branch to adopt"
  change=$(carried_change "$issue" "$branch" "$base")
  [ -n "$change" ] ||
    loop_refuse "branch $branch is not adopted — it adds no single archived change of its own"
  evidence=$(carried_verification "$branch" "$base" "$change")
  [ -n "$evidence" ] ||
    loop_refuse "branch $branch is not adopted — the change $change carries no green verification record"
  review_state=$(carried_review_state "$branch" "$change")
  case "$review_state" in
    *' failed')
      loop_refuse "branch $branch is not adopted — the reviewer refused $change in round ${review_state%% *} (evidence/review-failed-round-${review_state%% *}.md); apply the reviewer's corrections and merge it by hand, or delete that file deliberately if the verdict no longer applies" ;;
    *' requested')
      loop_refuse "branch $branch is not adopted — review round ${review_state%% *} of $change was announced and never answered (evidence/review-round-${review_state%% *}-requested.md): no reviewer graded that change, so it is reviewed and merged by hand, or that announcement is deleted deliberately when the round is not to be waited for" ;;
  esac
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

# The run's own item, merged onto the base before the run closes it (#229). `end-item` commits on the item's
# branch and nothing merged it, so a closed item read `status/fixed-by` while its fix lived only on
# `fix/issue-N-…`: measured 2026-10-20 on #201, whose two commits a person fast-forwarded onto the base by hand
# 35 minutes after the run reported the closure. The branch descends from the base the run recorded —
# `start-item` cut it there and `end-item` only added commits — so this is a fast-forward or it is nothing. A
# branch the base cannot fast-forward is *not* replayed here: that is the adoption of a previous attempt
# (`cmd_adopt_item`), which decides on evidence a stranger to the run can see, and its decision is not this one.
cmd_merge_item() { # <issue> <branch>
  local issue="${1:-}" branch="${2:-}" base commits dirty
  case "$issue" in ''|*[!0-9]*) loop_die "merge-item: usage: worktree.sh merge-item <issue> <branch>" ;; esac
  [ -n "$branch" ] || loop_die "merge-item: usage: worktree.sh merge-item <issue> <branch>"
  # The merge lands in the project's own tree, where the base branch is checked out, and that is verified: over a
  # checkout off the base branch the merge has no tree to land in (issue #302).
  check_base
  check_checkout
  base=$(state_base)
  gitr rev-parse --verify --quiet "refs/heads/$branch" >/dev/null ||
    loop_refuse "there is no branch $branch to merge"
  commits=$(gitr rev-list --reverse "$base".."$branch" | tr '\n' ' ' | sed 's/ $//')
  [ -n "$commits" ] || loop_refuse "branch $branch carries no commit the base does not"
  gitr merge-base --is-ancestor "$base" "$branch" ||
    loop_refuse "branch $branch does not descend from the base ${base:0:12} — refusing to replay it onto a tree nobody reviewed"
  # Over uncommitted work the merge would attribute that work to itself, which `create` already refuses for.
  dirty=$(gitr status --porcelain)
  [ -z "$dirty" ] || loop_refuse "the project tree is not clean — refusing to merge $branch onto the base:
$dirty"
  gitr merge --ff-only "$branch" >&2 || loop_die "merge-item: the fast-forward failed"
  printf 'merged: branch %s -> %s · commits %s\n' \
    "$branch" "$(gitr rev-parse --short HEAD)" "$(printf '%s' "$commits" | sed 's/ /,/g')"
}

# The run's record of a review round, written onto the item's branch: the *announcement* before the reviewer
# starts, and the answer when it arrives (#262). The branch is what a later run reads — the item's own directory
# (`.pi/issue-loop/items/<n>/`) is machine-local and is gone or unreadable to it — so a reviewer that is killed
# mid-answer has to leave a round that says "never answered" rather than no trace at all.
cmd_record_review_round() { # <issue> <change> <round> <requested|passed>
  local issue="$1" change="$2" round="$3" what="${4:-}" dir file who_name who_email
  case "$issue" in ''|*[!0-9]*) loop_die "record-review-round: usage: worktree.sh record-review-round <issue> <change> <round> requested|passed" ;; esac
  [ -n "$change" ] && [ -n "$round" ] ||
    loop_die "record-review-round: usage: worktree.sh record-review-round <issue> <change> <round> requested|passed"
  case "$what" in requested|passed) ;; *) loop_die "record-review-round: usage: worktree.sh record-review-round <issue> <change> <round> requested|passed" ;; esac
  [ -d "$worktree" ] || loop_die "record-review-round: no worktree at $worktree"
  [ -d "$worktree/openspec/changes/archive/$change" ] ||
    loop_refuse "record-review-round: $change is not an archived change in the worktree"
  dir="$worktree/openspec/changes/archive/$change/evidence"
  file="$dir/review-round-$round-$what.md"
  mkdir -p "$dir" || loop_die "record-review-round: could not create $dir"
  if [ "$what" = requested ]; then
    {
      printf 'The run announced review round %s of this change before its reviewer started.\n\n' "$round"
      printf 'Recorded by the run for issue #%s at %s, on the item branch, because a branch is what a later run\n' \
        "$issue" "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
      printf 'can see: a round whose answer never arrives must not read like a round that passed, and this file\n'
      printf 'is the only record that the round existed at all.\n'
    } > "$file" || loop_die "record-review-round: could not write $file"
  else
    {
      printf 'The reviewer passed this change in review round %s.\n\n' "$round"
      printf 'Recorded by the run for issue #%s at %s, on the item branch, beside the announcement of the same\n' \
        "$issue" "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
      printf 'round: a later run adopting this branch reads the answer here instead of trusting a silence.\n'
    } > "$file" || loop_die "record-review-round: could not write $file"
  fi
  gitw add -- "$file" >&2 || loop_die "record-review-round: could not stage $file"
  IFS=$'\t' read -r who_name who_email <<< "$(loop_commit_identity)"
  gitw -c user.name="$who_name" -c user.email="$who_email" \
    commit -qm "review: $change round $round $what (#$issue)" >&2 ||
    loop_die "record-review-round: could not commit the round"
  printf 'recorded: evidence/review-round-%s-%s.md in %s (%s)\n' "$round" "$what" "$change" \
    "$(gitw rev-parse --short HEAD)"
}

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

# The base a sweep compares against: the base the run recorded, or the base branch when no state exists. It is
# read here rather than passed by the caller because both the run's end and a person's `prune-landed` ask the same
# question, and a sweep against a base that is not the one the tree lands on would delete the wrong refs.
prune_base() {
  local base
  base=$(state_base 2>/dev/null) || base=""
  [ -n "$base" ] || base=$(gitr rev-parse --verify --quiet "$(loop_base_ref)" 2>/dev/null) || return 1
  printf '%s\n' "$base"
}

# The archived changes a branch adds, one directory name per line. The branch's own change is what the refusal for
# a refused review reads, and what must be on the base before a branch can be called landed on that reading.
branch_added_changes() { # <branch> <base> → one change name per line
  local branch="$1" base="$2" merge_base
  merge_base=$(gitr merge-base "$base" "$branch" 2>/dev/null) || return 0
  gitr diff --name-only --diff-filter=A "$merge_base".."$branch" -- openspec/changes/archive 2>/dev/null |
    awk -F/ 'NF > 3 { print $4 }' | sort -u
}

# A reviewer's refusal is its owner's verdict (#185), not a leftover: the branch stays even when its patch is in the
# base. The marker is read from the change the branch's tip commit adds: `record-review-failure` writes it last, so
# it is the tip's own diff even after a fast-forward put the branch in the base, where a diff against the base can
# no longer say what the branch added.
branch_carries_refused_review() { # <branch>
  local branch="$1" issue subject change
  case "$branch" in fix/issue-*) ;; *) return 1 ;; esac
  issue=${branch#fix/issue-}
  issue=${issue%%-*}
  case "$issue" in ''|*[!0-9]*) return 1 ;; esac
  subject=$(gitr log -1 --format=%s "$branch" 2>/dev/null)
  case "$subject" in *"(#$issue)"*) ;; *) return 1 ;; esac
  while IFS= read -r change; do
    [ -n "$change" ] || continue
    gitr ls-tree -r --name-only "$branch" -- "openspec/changes/archive/$change/evidence" 2>/dev/null |
      grep -Eq 'review-failed-round-[0-9][0-9]*\.md$' && return 0
  done <<< "$(gitr diff-tree --no-commit-id --name-only -r --diff-filter=A "$branch" -- openspec/changes/archive 2>/dev/null |
    awk -F/ 'NF > 3 { print $4 }' | sort -u)"
  return 1
}

# Landed by any reading that says the base already carries the work. The four readings are not one test written
# four ways: ancestry sees a fast-forward; patch identity (`git cherry`) sees a branch the loop re-landed as a
# fresh `fix: … (#N)` commit; identical content sees a branch whose net change is already there; and an archived
# change the branch adds must be on the base, so a branch that adds a change the base does not hold is never read
# as landed by one of the first three. The fourth is a veto as well as a reading, which is why it is checked first.
branch_is_landed() { # <branch> <base> → 0 when the base carries every patch the branch has
  local branch="$1" base="$2" merge_base paths change changes unlanded
  changes=$(branch_added_changes "$branch" "$base")
  while IFS= read -r change; do
    [ -n "$change" ] || continue
    gitr cat-file -e "$base:openspec/changes/archive/$change" 2>/dev/null || return 1
  done <<< "$changes"
  gitr merge-base --is-ancestor "$branch" "$base" 2>/dev/null && return 0
  if unlanded=$(loop_unlanded_commits "$LOOP_ROOT" "$base" "$branch"); then
    [ -n "$unlanded" ] || return 0
  fi
  merge_base=$(gitr merge-base "$base" "$branch" 2>/dev/null) || return 1
  paths=$(gitr diff --name-only "$merge_base" "$branch" 2>/dev/null)
  [ -n "$paths" ] || return 0
  # shellcheck disable=SC2086 # the paths are the line-separated output of git, and this tree has no path with whitespace
  gitr diff --quiet "$base" "$branch" -- $paths 2>/dev/null && return 0
  return 1
}

# The ref sweep at the run's end, and the command a person can repeat. It is deliberately narrower than
# `loop-clean.sh`: it touches refs only, never a tree, a scratch path or a process. `--apply` deletes exactly the
# branches it named, and every refusal is a branch that stays.
cmd_prune_landed() {
  local apply=0 base held branch kept_work=0 kept_checked=0 kept_refused=0 removed=0
  while [ $# -gt 0 ]; do
    case "$1" in
      --apply) apply=1; shift ;;
      *) loop_die "prune-landed: unknown argument $1" ;;
    esac
  done
  base=$(prune_base) || loop_die "prune-landed: no base to compare against — run 'state.sh init' or name the base branch"
  held=$(loop_worktree_branches "$LOOP_ROOT" | tr '\n' ' ')
  while IFS= read -r branch; do
    [ -n "$branch" ] || continue
    case " $held " in
      *" $branch "*)
        printf 'kept %s — checked out\n' "$branch"
        kept_checked=$((kept_checked + 1))
        continue ;;
    esac
    if branch_carries_refused_review "$branch"; then
      printf 'kept %s — the reviewer refused it\n' "$branch"
      kept_refused=$((kept_refused + 1))
      continue
    fi
    if ! branch_is_landed "$branch" "$base"; then
      printf 'kept %s — carries work the base does not have\n' "$branch"
      kept_work=$((kept_work + 1))
      continue
    fi
    if [ "$apply" -eq 1 ]; then
      # The force is the price of patch identity: a branch whose work was re-landed as a different commit is not
      # an ancestor of the base, and git's own guard would refuse a branch that carries nothing.
      gitr branch -D "$branch" >/dev/null 2>&1 || { printf 'kept %s — could not be removed\n' "$branch"; kept_work=$((kept_work + 1)); continue; }
      printf 'removed %s (its patches are in the base)\n' "$branch"
    else
      printf 'would remove %s (its patches are in the base)\n' "$branch"
    fi
    removed=$((removed + 1))
  done <<< "$(loop_own_branches "$LOOP_ROOT")"
  if [ "$removed" -eq 0 ] && [ "$kept_work" -eq 0 ] && [ "$kept_checked" -eq 0 ] && [ "$kept_refused" -eq 0 ]; then
    printf 'worktree: no branch of the loop to prune\n'
  elif [ "$apply" -eq 1 ]; then
    printf 'worktree: %s landed branch(es) removed · %s kept (carries work the base does not) · %s kept (checked out) · %s kept (the reviewer refused it)\n' \
      "$removed" "$kept_work" "$kept_checked" "$kept_refused"
  else
    printf 'worktree: would remove %s landed branch(es) · %s kept (carries work the base does not) · %s kept (checked out) · %s kept (the reviewer refused it)\n' \
      "$removed" "$kept_work" "$kept_checked" "$kept_refused"
  fi
}

# A branch the run committed for an item and merged is done with: the work is in the base, and the branch
# itself is a leftover. Git refuses to delete a branch a worktree holds, and the kept worktree is on it — so
# the worktree is released first (detached at the branch's own HEAD, which is where the work is) and the
# branch is then removed with git's *safe* delete, which refuses a branch whose work is not in the base. The
# worktree's branch marker stays: it names the item whose work the tree carries, and a tree that is dirty
# when the next run starts is attributed by it.
cmd_release_branch() { # <branch>
  local branch="${1:-}"
  [ -n "$branch" ] || loop_die "release-branch: usage: worktree.sh release-branch <branch>"
  gitr rev-parse --verify --quiet "refs/heads/$branch" >/dev/null || {
    printf 'no branch %s to release\n' "$branch"
    return 0
  }
  if [ -d "$worktree" ] && [ "$(gitw rev-parse --abbrev-ref HEAD 2>/dev/null)" = "$branch" ]; then
    gitw switch --detach >&2 || loop_die "release-branch: could not release the worktree from $branch"
  fi
  gitr branch -d "$branch" >&2 || {
    printf 'branch %s was not removed — it carries work the base does not have\n' "$branch" >&2
    return 1
  }
  printf 'released: branch %s removed (its work is in the base)\n' "$branch"
}

cmd_head() { [ -d "$worktree" ] || loop_die "head: no worktree at $worktree"; gitw rev-parse HEAD; }
cmd_status() { [ -d "$worktree" ] || loop_die "status: no worktree at $worktree"; gitw status --porcelain; }

# The build output a kept worktree does not need (issue #210). Which directories those are is the project's
# (`LOOP_WORKTREE_SCRATCH_PATHS`), and the safety of removing them is `lib.sh`'s `loop_scratch_remove`, so
# this command and `loop-clean.sh` remove the same paths under the same four refusals. The total is printed
# because the point of the removal is a number a reader can check against `du`.
cmd_strip_scratch() {
  local rel removed=0 kept=0 bytes=0
  [ -d "$worktree" ] || loop_die "strip-scratch: no worktree at $worktree"
  while IFS= read -r rel; do
    [ -n "$rel" ] || continue
    if loop_scratch_remove "$worktree" "$rel" --apply; then
      removed=$((removed + 1)); bytes=$((bytes + LOOP_SCRATCH_BYTES))
      printf 'worktree: %s\n' "$LOOP_SCRATCH_LINE"
    else
      [ -z "$LOOP_SCRATCH_LINE" ] && continue
      kept=$((kept + 1))
      printf 'worktree: %s\n' "$LOOP_SCRATCH_LINE"
    fi
  done <<< "$(loop_scratch_paths)"
  if [ "$removed" -eq 0 ] && [ "$kept" -eq 0 ]; then
    printf 'worktree: no recorded build output in the kept worktree (0 B)\n'
    return 0
  fi
  printf 'worktree: %s build path(s) removed from the kept worktree: %s B — its sources and its git state stay\n' \
    "$removed" "$bytes"
}

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
  record-review-round) cmd_record_review_round "$@" ;;
  record-review-failure) cmd_record_review_failure "$@" ;;
  adopt-item) cmd_adopt_item "$@" ;;
  branch-state) cmd_branch_state "$@" ;;
  merge-item) cmd_merge_item "$@" ;;
  release-branch) cmd_release_branch "$@" ;;
  prune-landed) cmd_prune_landed "$@" ;;
  head) cmd_head "$@" ;;
  status) cmd_status "$@" ;;
  strip-scratch) cmd_strip_scratch "$@" ;;
  remove) cmd_remove "$@" ;;
  ""|-h|--help) sed -n '2,33p' "$0" ;;
  *) loop_die "unknown command '$cmd'" ;;
esac
