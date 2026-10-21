#!/usr/bin/env bash
# Probe: are mutations 12.7 and 18.2 already stale on HEAD? The harness is pointed at a throwaway copy of HEAD's
# engine (`--engine`), which is what its own self-test does.
set -uo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
dest="$root/.pi/tmp/head-engine"
rm -rf "$dest"
mkdir -p "$dest"
git -C "$root" archive HEAD tools | tar -x -C "$dest" || exit 2
bash "$root/tools/loop-mutations.sh" --only "12.7 18.2" --engine "$dest/tools/loop" --out "$dest/mutations.txt" 2>&1 | tail -8
