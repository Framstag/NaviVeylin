#!/usr/bin/env bash
# Red-on-HEAD reading for issue #831, change `fix-the-repeat-rule-names-what-it-withheld`.
#
# The case `report#theRepeatRuleNamesWhatItWithheld` asserts behaviour the base commit does not have, so the reading
# is that case run against the base production files: a hardlink copy of the change root, its three production files
# replaced by `git show <base>:<path>`, and the case driven by the tree's own suite. Nothing is reverted in the change
# root — the copy is what is read — because `git checkout` and `git stash` are refused to an unattended child.
#
# The copy carries the case and its registry lines (the red test is what is being read), and only
# `tools/pi-trim-result.mjs`, `tools/loop/state.sh` and `tools/loop/report.sh` are the base versions.
#
# Usage: bash /home/tim/projects/NaviVeylin/.pi/issue-loop/items/831/red-on-HEAD-probe.sh
set -uo pipefail

root=/home/tim/projects/NaviVeylin/.pi/worktrees/loop
base=c0ddb97cadaefe10cbf126ba7d19cfb41fb02b97
here=$(cd "$(dirname "$0")" && pwd)
tree="$here/scratch/base-tree"

rm -rf "$tree"
cp -al "$root" "$tree" || { echo "cannot copy the change root" >&2; exit 2; }
rm -f "$tree/.git"
git init -q "$tree" || { echo "cannot make the copy a root of its own" >&2; exit 2; }
for f in tools/pi-trim-result.mjs tools/loop/state.sh tools/loop/report.sh; do
  rm -f "$tree/$f"
  git -C "$root" show "$base:$f" > "$tree/$f" || { echo "cannot read $f at $base" >&2; exit 2; }
done

cd "$tree" && bash tools/loop/tests/loop-selftest.sh --case report#theRepeatRuleNamesWhatItWithheld
