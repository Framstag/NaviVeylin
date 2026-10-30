#!/usr/bin/env bash
# measure-the-rows.sh — the numbers this change states, each printed by the command that takes it.
#
# Three questions, one block per question:
#   1. the eleven figures the six priced rows of `AGENTS.md` state, each beside the section's measured cost with
#      the requirement's ruler (`tools/check-doc-routes.sh --sizes`);
#   2. the three ranges the three remaining rows state, each beside the span its lettered sections measure — the
#      two the tree does not measure today are the defect's own proof;
#   3. the figure the clause convicts on a throwaway fixture (a row pricing a 1 065-byte section at 0.1 kB), so
#      the "presence-only" reading is shown and not only asserted.
#
# Reads text files and a temp directory only: no build, no device, no network, nothing in the repository written.
#
# Usage: measure-the-rows.sh            # prints the blocks, one command above each
set -uo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
while [ ! -f "$root/AGENTS.md" ]; do
  [ "$root" != "/" ] || { echo "measure-the-rows: no AGENTS.md above $(dirname "${BASH_SOURCE[0]}")" >&2; exit 2; }
  root=$(dirname "$root")
done
sizes() { bash "$root/tools/check-doc-routes.sh" --sizes 2>&1; }

echo "THE FIGURES THE SIX PRICED ROWS STATE — the row's figure, the section, and the measurement, $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo '$ bash tools/check-doc-routes.sh --sizes | grep -E '"'"'section-cost (MapRendering\.md §(15a|16a|16b)|UI\.md §(3a|3b|3c|6a|6b|7a|8a|10a)) '"'"''
sizes | grep -E 'section-cost (MapRendering\.md §(15a|16a|16b)|UI\.md §(3a|3b|3c|6a|6b|7a|8a|10a)) '
echo
echo "# the eleven figures the six rows state, in the rows' own order (bytes/1000, one decimal):"
echo "#   MapRendering §15a 3.1  §16a 2.4  §16b 0.8 | UI §7a 13.1  §8a 2.5 | UI §3b 1.8  §3c 2.8  §6b 4.9"
echo "#   UI §3a 4.4 | UI §10a 2.2 | UI §6a 6.0   — all eleven equal the measurement above"
echo

echo "THE RANGES THE THREE ROWS STATE, AND THE SPAN THEY MEASURE — the cheapest and the costliest of the row's lettered sections"
echo
echo '$ bash tools/check-doc-routes.sh --sizes | grep -E '"'"'section-cost Build\.md §(2a|2b|2c|2d|2e|3|4a|4b|4d|4f) '"'"''
sizes | grep -E 'section-cost Build\.md §(2a|2b|2c|2d|2e|3|4a|4b|4d|4f) ' | LC_ALL=C sort -t' ' -k5 -n
echo '   the row states "1.7–5.3 kB per section"; the sections the row names measure 1.9 kB (§2e) to 7.0 kB (§4f)'
echo
echo '$ bash tools/check-doc-routes.sh --sizes | grep -E '"'"'section-cost Build\.md §(6a|6b|6c|6d|6e|6f|6g|6h) '"'"''
sizes | grep -E 'section-cost Build\.md §(6a|6b|6c|6d|6e|6f|6g|6h) ' | LC_ALL=C sort -t' ' -k5 -n
echo '   the row states "1.1–5.3 kB each"; the sections the row names measure 1.3 kB (§6g) to 5.3 kB (§6b)'
echo
echo '$ bash tools/check-doc-routes.sh --sizes | grep -E '"'"'section-cost Build\.md §(10a|10b|10c|10d|10e|10f|10g|10h|10i|10j|10k) '"'"''
sizes | grep -E 'section-cost Build\.md §(10a|10b|10c|10d|10e|10f|10g|10h|10i|10j|10k) ' | LC_ALL=C sort -t' ' -k5 -n
echo '   the row states "0.9–6.8 kB each"; the sections the row names measure 0.9 kB (§10f) to 6.8 kB (§10i) — this one is true'
echo

echo "THE FIFTH PICTURE — a routing row that prices a kilobyte of section at 0.1 kB, on a throwaway fixture"
echo
echo '$ <fixture root>/AGENTS.md: | phone UI | `UI.md` §1a "Parity, measured" — 0.1 kB |'
echo '$ bash tools/check-doc-routes.sh --root <fixture>'
tmp=$(mktemp -d "${TMPDIR:-/tmp}/measure-the-rows.XXXXXX") || exit 2
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/guidelines"
{
  printf '# UI Specification\n\n## 1. Cross-variant UI parity (general rule)\n\n'
  printf '## 1a. Parity, measured\n\n'
  printf 'The measured half of the parity rule: '
  i=0; while [ "$i" -lt 12 ]; do printf 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n'; i=$((i + 1)); done
  printf '\n## 1b. Parity, unmeasured\n\n'
  printf 'The half with no case behind it yet: '
  i=0; while [ "$i" -lt 38 ]; do printf 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n'; i=$((i + 1)); done
} > "$tmp/guidelines/UI.md"
printf '# AGENTS\n\n## Documentation Map\n\n| what you are changing | read |\n|---|---|\n| phone UI | `UI.md` §1a "Parity, measured" — 0.1 kB |\n' > "$tmp/AGENTS.md"
echo "\$ sed -n '/^## 1a\\./,/^## 1b\\./p' <fixture>/guidelines/UI.md | sed '\$d' | wc -c   # the ruler's own count"
sed -n '/^## 1a\./,/^## 1b\./p' "$tmp/guidelines/UI.md" | sed '$d' | LC_ALL=C wc -c
bash "$root/tools/check-doc-routes.sh" --root "$tmp" 2>&1
rc=$?
echo "   exit $rc — 1 is the conviction this clause adds; before it the same row passed with exit 0, because the figure was read as presence"
