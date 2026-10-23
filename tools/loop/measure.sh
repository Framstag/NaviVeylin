#!/usr/bin/env bash
# measure.sh — the change's own numbers, in one block, for one call.
#
# The worker's task tells it what to verify and hands it the gate, and that is all: which paths the change
# touches, which checks that pathspec owes, what the change's own evidence already tallies and how many of its
# spec scenarios name a case were facts it collected one call at a time. Measured over five items of this loop
# (`.pi/issue-loop/items/{69,133,11,13,98}/events.jsonl`, `tool_execution_end`): 73 to 244 shell calls and 22 to
# 79 file reads per item, against 2 s to 575 s of tool time inside 291 s to 1 644 s of wall clock — nine tenths
# of the item is the wait between one model turn and the next (issue #246). So the facts a worker needs before
# it plans are one command's output, and every line of it is prefixed `LOOP-MEASURE` so a reader can see the
# block's end.
#
# It is a **reading** command: it runs nothing the pathspec owes, because the gate is run once per item by
# `LOOP_GATE_CMD` and a second execution answers a question the first one already answered. The checks it names
# come from `gate.sh --plan`, the same call the gate itself makes, so the profile's globs stay the one place
# the mapping lives and this script names no project value of its own (D16).
#
# Exit: 0 the block was printed; 2 usage, or a base this tree cannot resolve.
#
# Usage: measure.sh [--base <sha>] [--change <name>]

set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/loop/lib.sh
source "$here/lib.sh"

base=""
change=""
while [ $# -gt 0 ]; do
  case "$1" in
    --base) need_value "$@"; base="${2:-}"; shift 2 ;;
    --change) need_value "$@"; change="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,22p' "$0"; exit 0 ;;
    *) loop_die "measure: unknown argument $1" ;;
  esac
done

loop_resolve

# The block: every line carries the prefix, so "one block" is a property a case can count instead of a promise.
say() { printf 'LOOP-MEASURE %s\n' "$*"; }
item() { printf 'LOOP-MEASURE-%s %s\n' "$1" "$2"; }

# The profile this block was read from, beside the change: the checks below are the profile's answer, and a change
# whose delta edits that file must be measured by the file it edits, not by the one the run was started from
# (issue #277). The gate names the same profile in the log of the run it makes.
item PROFILE "$LOOP_CONF_PATH"

# The same base the gate resolves, and the same refusal: a range that cannot be resolved is named, never read
# as "no change" — an unresolvable base is the one thing a measurement must not report as a measurement.
if [ -z "$base" ]; then
  base=$(git merge-base HEAD origin/main 2>/dev/null || git rev-parse --verify --quiet 'HEAD~1' 2>/dev/null || true)
fi
[ -n "$base" ] || loop_die "measure: cannot resolve a base to compare against — pass --base <sha>"
git rev-parse --verify --quiet "$base" >/dev/null || loop_die "measure: '$base' is not a commit this tree knows"

# The change's pathspec, exactly as the gate reads it: what was committed since the base plus what is still
# uncommitted, so the block answers for the work in hand and not only for the work already committed.
paths=$( { git diff --name-only "$base" 2>/dev/null; git status --porcelain 2>/dev/null | sed 's/^...//'; } |
  sort -u | sed '/^$/d')
path_count=$(printf '%s' "$paths" | grep -c . || true)
numstat=$(git diff --numstat "$base" 2>/dev/null)
insertions=$(printf '%s\n' "$numstat" | awk '{ if ($1 ~ /^[0-9]+$/) s += $1 } END { print s + 0 }')
deletions=$(printf '%s\n' "$numstat" | awk '{ if ($2 ~ /^[0-9]+$/) s += $2 } END { print s + 0 }')

say "base=$base paths=$path_count insertions=$insertions deletions=$deletions"
while IFS= read -r p; do
  [ -n "$p" ] || continue
  item PATH "$p"
done <<< "$paths"

# What the pathspec owes, from the one place that decides it. `--plan` names the commands and runs none of
# them; the block then holds the gate's own answer without a second execution of anything.
plan=$(bash "$here/gate.sh" --plan --base "$base" 2>&1)
if [ -n "$(printf '%s' "$plan" | tr -d '[:space:]')" ]; then
  while IFS= read -r cmd; do
    [ -n "$cmd" ] || continue
    item CHECK "$cmd"
  done <<< "$plan"
else
  item CHECK "none — no path this profile gates changed since $base"
fi

# The tallies the tree already carries: the gate's own directory and the change's evidence. A tally is read,
# never produced — `<path> tests=<n> failures=<n>`, one line per file, with its own numbers.
tally_of() { # <xml file> → `tests=<n> failures=<n>` or nothing when the file carries no suite line
  sed -n 's/.*<testsuite[^>]*tests="\([0-9][0-9]*\)"[^>]*failures="\([0-9][0-9]*\)".*/tests=\1 failures=\2/p' "$1" 2>/dev/null |
    head -1
}
# A directory of tallies, capped — and the count it withheld, because a block that stopped silently would read as
# "the tree carries this many" (measured 2026-10-22, review round 1 of issue #246: `head -20` truncated both
# directories and said nothing). The cap is a reading limit, not a claim about the tree.
tally_limit=20
tally_dir() { # <directory>
  local dir="$1" all total shown withheld f n
  [ -n "$dir" ] && [ -d "$dir" ] || return 0
  all=$(find "$dir" -name '*.xml' 2>/dev/null | sort)
  [ -n "$all" ] || return 0
  total=$(printf '%s\n' "$all" | grep -c . || true)
  shown=0
  withheld=0
  while IFS= read -r f; do
    [ -n "$f" ] || continue
    n=$(tally_of "$f")
    [ -n "$n" ] || continue
    if [ "$shown" -lt "$tally_limit" ]; then
      item TALLY "$f $n"
      shown=$((shown + 1))
    else
      withheld=$((withheld + 1))
    fi
  done <<< "$all"
  [ "$withheld" -eq 0 ] ||
    item TALLY-MORE "$withheld of $total tally files not shown — at most $tally_limit per directory ($dir)"
}
tally_dir "$(loop_conf_value LOOP_GATE_TALLY_DIR)"

# The change: its delta's scenarios against the cases they name. A scenario with no case is a plan defect, so
# the count that says so is part of the block rather than something a reviewer has to add up by hand.
#
# Which change is measured is not the block's to guess (review round 1, issue #246): it measured the
# alphabetically last open change, so a correction round on another one read a stranger's numbers. A name the
# worker passes resolves under the open changes and then under the archive, where a correction round works; no
# name and two open changes is named as ambiguous, never silently resolved.
change_dir=""
change_said=0
say_change() { item CHANGE "$*"; change_said=1; }
if [ -n "$change" ]; then
  for cand in "$change" "openspec/changes/$change" "openspec/changes/archive/$change"; do
    [ -n "$cand" ] && [ -d "$cand" ] && { change_dir="$cand"; break; }
  done
  if [ -z "$change_dir" ]; then
    cand=$(find openspec/changes/archive -maxdepth 1 -mindepth 1 -type d -name "*$change*" 2>/dev/null | sort | tail -1)
    [ -n "$cand" ] && change_dir="$cand"
  fi
  [ -n "$change_dir" ] || say_change "not-found name=$change — no change of that name is open or archived"
else
  open_changes=$(find openspec/changes -maxdepth 1 -mindepth 1 -type d ! -name archive 2>/dev/null | sort)
  open_count=$(printf '%s' "$open_changes" | grep -c . || true)
  case "$open_count" in
    1) change_dir="$open_changes" ;;
    0) change_dir=$(find openspec/changes/archive -maxdepth 1 -mindepth 1 -type d 2>/dev/null | sort | tail -1) ;;
    *) say_change "ambiguous open=$(printf '%s\n' "$open_changes" | xargs -n1 basename | paste -sd, -) — $open_count changes are open, so the block names them and measures none: pass --change <name>" ;;
  esac
fi
if [ -n "$change_dir" ] && [ -d "$change_dir" ]; then
  change_name=$(basename "$change_dir")
  specs=$(find "$change_dir/specs" -name 'spec.md' 2>/dev/null | sort)
  scenarios=0
  cases=0
  while IFS= read -r s; do
    [ -n "$s" ] || continue
    scenarios=$((scenarios + $(grep -c '^#### Scenario:' "$s" 2>/dev/null || true)))
    cases=$((cases + $(grep -c '^- \*\*Case\*\*:' "$s" 2>/dev/null || true)))
  done <<< "$specs"
  unnamed=$((scenarios - cases))
  [ "$unnamed" -ge 0 ] || unnamed=0
  item CHANGE "$change_name scenarios=$scenarios cases=$cases unnamed=$unnamed"
  # The evidence the change carries, beside the gate's own tallies: the half a reviewer reads by path.
  tally_dir "$change_dir/evidence"
  surface=$(sed -n 's/^surface:/surface:/p' "$change_dir/proposal.md" 2>/dev/null | head -1)
  item SURFACE "${surface:-none — the proposal carries no surface: line}"
else
  [ "$change_said" -eq 1 ] || say_change "none — no change is open and none is archived"
fi

printf 'LOOP-MEASURE-END\n'
exit 0
