#!/usr/bin/env bash
# red-on-head.sh — run this change's two cases against the check as the base commit carries it, without touching
# the working tree.
#
# The probe drives `tools/check-doc-routes.sh`, so "red on HEAD" means red against the check HEAD holds, not
# against a revert of this change's edit. This script extracts that file from the base commit into a throwaway
# tree beside a copy of the probe — the probe reads no document of the repository, only its own fixtures — and
# runs it there. Nothing in the repository is written; `git show` only reads.
#
# Usage: red-on-head.sh [<base-commit>]        # default: this change's base
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
root=$here
while [ ! -f "$root/AGENTS.md" ]; do
  [ "$root" != "/" ] || { echo "red-on-head: no AGENTS.md above $here" >&2; exit 2; }
  root=$(dirname "$root")
done
base=${1:-2f954a04b3f75e138c529fffe7985ca011375866}

tmp=$(mktemp -d "${TMPDIR:-/tmp}/red-on-head.XXXXXX") || exit 2
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/tools" "$tmp/openspec/probe/evidence"
(cd "$root" && git show "$base:tools/check-doc-routes.sh") > "$tmp/tools/check-doc-routes.sh" || {
  echo "red-on-head: $base:tools/check-doc-routes.sh does not come out of the repository" >&2; exit 2; }
chmod +x "$tmp/tools/check-doc-routes.sh"
cp "$root/AGENTS.md" "$tmp/AGENTS.md"
cp "$here/red-probe.sh" "$tmp/openspec/probe/evidence/red-probe.sh"

printf '# the two cases of this change against the check base %s carries, %s\n' \
  "$base" "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
printf '$ git show %s:tools/check-doc-routes.sh > <tmp>/tools/check-doc-routes.sh\n' "$base"
printf '$ bash openspec/changes/2026-10-30-fix-routing-row-figure-is-measured/evidence/red-probe.sh   # from <tmp>\n'
printf '$ git show %s:tools/check-doc-routes.sh | grep -c "%s"\n' "$base" 'presence and'
(cd "$root" && git show "$base:tools/check-doc-routes.sh") | grep -c 'presence and'
bash "$tmp/openspec/probe/evidence/red-probe.sh" --xml "$here/red-on-head.xml" 2>&1
