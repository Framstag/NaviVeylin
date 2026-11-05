#!/usr/bin/env bash
# The base-tree measurement behind `gate-note.txt`: does the one failing gate step pass on the run's base commit?
#
# The step is `bash tools/gen-feature-list.sh --check-classification`, and the two delta spec ids it reports enter
# the working tree with other open changes' artifacts (gitignored directories under `openspec/changes/`), not with
# any commit. Extracted from the base commit — where those directories do not exist — the same check is the
# measurement below.
#
# Usage: bash openspec/changes/archive/<change>/evidence/gate-base-green.sh [<base-sha>]
set -uo pipefail
repo="$(cd "$(dirname "$0")/../../../../.." && pwd)"
base="${1:-05ed983517719cbe1f1fbbd3c6ba9776662ce4fe}"
tmp=/tmp/nv-sonar-base-tree
rm -rf "$tmp"; mkdir -p "$tmp"
git -C "$repo" archive "$base" openspec tools/gen-feature-list.sh tools/feature-list FEATURES.md | tar -x -C "$tmp"
echo "# base $base, extracted to $tmp"
echo '$ bash tools/gen-feature-list.sh --check-classification'
( cd "$tmp" && bash tools/gen-feature-list.sh --check-classification )
echo "exit=$?"
rm -rf "$tmp"
