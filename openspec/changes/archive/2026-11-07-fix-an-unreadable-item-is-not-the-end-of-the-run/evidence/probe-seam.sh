#!/usr/bin/env bash
# Probe: is the loop's per-item read the retry seam's own call? The stub `gh` fails every `issue view`
# transiently (GH_STUB_TRANSIENT_TIMES=99) and counts the attempts in a file, so the count is the seam's
# retry count and the status is the seam's exit code. Nothing here is inline: the read is `loop_issue_json`
# — the function `run.sh`'s per-item read calls — sourced from `tools/loop/lib.sh`.
set -uo pipefail
root=/home/tim/projects/NaviVeylin/.pi/worktrees/loop
d=/home/tim/projects/NaviVeylin/.pi/issue-loop/items/808/scratch
cd "$root" || exit 2
export GH_STUB_DIR="$root/tools/loop/fixtures/scenarios/two"
# The stub `gh` is the one the suite puts first on PATH; without it the probe reads the real tracker.
export PATH="$root/tools/issues/fixtures:$PATH"
export ISSUES_GH_RETRY_DELAYS="0 0 0"
export GH_STUB_TRANSIENT_FAIL="issue view"
export GH_STUB_TRANSIENT_TIMES=99
export GH_STUB_TRANSIENT_STATE="$d/attempts"
: > "$GH_STUB_TRANSIENT_STATE"

# shellcheck source=tools/loop/lib.sh
source "$root/tools/loop/lib.sh"
printf 'cli='
command -v gh
loop_issue_json 21 --comments >"$d/read.out" 2>"$d/read.err"
status=$?
printf 'status=%s\n' "$status"
printf 'attempts='
sed -n '1p' "$GH_STUB_TRANSIENT_STATE"
printf 'stderr-last='
sed -n '$p' "$d/read.err"
printf 'stdout-bytes='
wc -c < "$d/read.out"
