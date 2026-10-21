#!/usr/bin/env bash
# Probe: is `worktree#keepsByDefaultAndRemovesOnRequest` failing on HEAD too, when the checkout's own path
# contains `worktrees/loop`? (The case asserts `git worktree list` does not name `worktrees/loop`, and in the
# loop's own worktree the throwaway project's absolute path carries exactly that substring.)
#
#   usage: probe-case-path-collision.sh <tools source> <label>
#     HEAD   — this repository's HEAD, exported with `git archive`
#     <rev>  — any commit of this repository, exported the same way (the run's base, e.g.)
#     work   — the working tree's tools/, as this change left them
set -uo pipefail
root=$(cd "$(dirname "$0")" && pwd)
while [ ! -e "$root/.git" ]; do root=$(dirname "$root"); [ "$root" != "/" ] || exit 2; done
src="${1:?usage: probe-case-path-collision.sh HEAD|work <label>}"
label="${2:-$src}"
case_name=worktree#keepsByDefaultAndRemovesOnRequest
# a root whose path contains `worktrees/loop`, which is what the case's assertion trips over
dest="$root/.pi/worktrees/loop/.pi/tmp/case-path-$label"
rm -rf "$dest"
mkdir -p "$dest"
if git -C "$root" rev-parse --verify -q "$src^{commit}" >/dev/null; then
  git -C "$root" archive "$src" tools | tar -x -C "$dest" || exit 2
else
  cp -a "$root/tools" "$dest/tools" || exit 2
fi
mkdir -p "$dest/.pi/skills"
cp -a "$root/.pi/skills/fix-loop" "$root/.pi/skills/openspec-apply-change" "$dest/.pi/skills/" 2>/dev/null || true
printf 'run %s · engine %s · HEAD %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$src" "$(git -C "$root" rev-parse HEAD)"
printf 'path of the checkout: %s\n' "$dest"
LOOP_CONF="$dest/tools/loop/loop.conf" bash "$dest/tools/loop-selftest.sh" --case "$case_name" 2>&1 | tail -3
