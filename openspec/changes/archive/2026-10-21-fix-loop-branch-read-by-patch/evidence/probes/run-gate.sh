#!/usr/bin/env bash
# The gate, run from the loop's own worktree. One case (`worktree#keepsByDefaultAndRemovesOnRequest`) asserts
# that `git worktree list` does not name `worktrees/loop`, and this checkout's own path carries that substring,
# so a scratch tree under it makes the throwaway project's absolute path match — on HEAD as well as here
# (`probe-case-path-collision.sh HEAD`). The run therefore keeps its scratch trees one level out, which is the
# only thing this file changes about the configuration.
set -uo pipefail
root=$(cd "$(dirname "$0")" && pwd)
while [ ! -e "$root/.git" ]; do root=$(dirname "$root"); [ "$root" != "/" ] || exit 2; done
conf="$root/.pi/tmp/gate-loop.conf"
sed "s|^LOOP_TMP_DIR=\"\"$|LOOP_TMP_DIR=\"$(cd "$root/../.." && pwd)/tmp\"|" \
  "$root/tools/loop/loop.conf" > "$conf"
mkdir -p "$(cd "$root/../.." && pwd)/tmp"
LOOP_CONF="$conf" bash "$root/tools/loop-selftest.sh" "$@"
