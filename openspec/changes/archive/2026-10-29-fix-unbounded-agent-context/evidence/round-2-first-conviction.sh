#!/usr/bin/env bash
# round-2-first-conviction.sh — reproduce the moment the cost clause first ran: the table as it stood at
# `7b5893fa` (six rows routing a lettered sub-section of a document and pricing nothing) against the check that
# now reads that clause. The tree is a throwaway: today's `guidelines/` (their sections' numbers and headings are
# the same for the rows' references), the entry document from that commit, and a link to this tree's skills.
#
# `round-2-first-conviction.log` is its output. Nothing in the repository is written.
set -uo pipefail
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
root=$(cd "$here/../../../../.." && pwd)
base="${1:-7b5893fa}"

tmp=$(mktemp -d "${TMPDIR:-/tmp}/issue197-head.XXXXXX") || { echo "round-2-first-conviction: no scratch directory" >&2; exit 2; }
trap 'rm -rf "$tmp"' EXIT

mkdir -p "$tmp/guidelines" "$tmp/.pi"
cp "$root"/guidelines/*.md "$tmp/guidelines/" || exit 2
ln -s "$root/.pi/skills" "$tmp/.pi/skills"
git -C "$root" show "$base:AGENTS.md" > "$tmp/AGENTS.md" || exit 2

echo "THE FIRST CONVICTION — the cost clause run against AGENTS.md as it stood at $base (six rows routing a"
echo "lettered sub-section of a document and pricing nothing), with the current check and this tree's guidelines,"
echo "in a throwaway tree. Recorded $(date -u +%Y-%m-%dT%H:%M:%SZ)."
echo
echo "\$ bash tools/check-doc-routes.sh --root <throwaway tree: $base:AGENTS.md + this tree's guidelines>"
bash "$root/tools/check-doc-routes.sh" --root "$tmp" 2>&1
echo "exit=$?"
