#!/usr/bin/env bash
# surface.sh — the entry this change was filed from, and the surface the plan is set against, taken from the tree.
#
# Every number in `surface.txt` is this script's output for one command, so a reader can re-take it. The record's
# own owner citation is quoted inside a fence: `tools/check-openspec-hygiene.sh` reads an `owner:` line outside a
# fence as a citation, and a quotation is not one.
#
# Usage: bash surface.sh [<base>]
set -uo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." || exit 2   # the change root
base="${1:-650028bfdf6e652ebd045b4af1be0a90b6c0a481}"
# the commit whose parent still carried the entry document's nine-skill table ("the guideline corpus in three
# layers" removed it); `git log -S'Agent iteration loop' -- AGENTS.md` names it.
diet=4330ce3e
self=openspec/changes/fix-relocation-record-names-its-owners/
record="openspec/changes/archive/2026-10-31-guideline-layers-mechanism/evidence/relocation-entry-document.txt"
gaps="openspec/changes/archive/2026-10-31-guideline-layers-mechanism/evidence/known-gaps.md"

printf '# the entry this change was filed from, verified against the tree — %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
printf '# base %s\n\n' "$base"

printf '## the record'"'"'s owner citation, and the paths under it (quoted, so it is not a citation here)\n'
printf '```\n'
sed -n '19,21p' "$record"
printf '```\n'
printf 'owner paths cited: '
sed -n '/^B2/,/^B3/p' "$record" | grep -c '^\s*\.pi/skills/'

printf '\n## what the block it justifies named ("Nine skills own the tracker", table at %s^:AGENTS.md)\n' "$diet"
printf 'table rows: '
git show "$diet^:AGENTS.md" | sed -n '/^| Skill | Job |/,/^$/p' | grep -c '^| `'
git show "$diet^:AGENTS.md" | sed -n '/^| `create-issue`/,/^$/p' | sed 's/^/  /'

printf '\n## the archived review grade of it\n'
grep -n 'G2' "$gaps"

printf '\n## the surface, before the plan is fixed (the change'"'"'s own directory excluded - it quotes the record)\n'
printf 'owner citations in the tree (open changes): '
grep -rn '^[[:space:]]*owner[s]*:' openspec/changes/ | grep -v "^$self" | wc -l
printf 'files carrying them: '
grep -rln '^[[:space:]]*owner[s]*:' openspec/changes/ | grep -v "^$self" | wc -l
printf 'of those carrying a glob: '
grep -rn '^[[:space:]]*owner[s]*:' openspec/changes/ | grep -v "^$self" | grep -c '\*'
printf 'records under archive/ carrying them: '
grep -rln '^[[:space:]]*owner[s]*:' openspec/changes/archive/ | wc -l
printf 'cases in the hygiene selftest, now / at base: %s / %s\n' \
  "$(grep -c '^case_' tools/check-openspec-hygiene-selftest.sh)" \
  "$(git show "$base:tools/check-openspec-hygiene-selftest.sh" | grep -c '^case_')"
printf 'skills on disk today: '
ls -d .pi/skills/*/SKILL.md | wc -l
printf 'cases that declare tools/loop/ (the suite a tools/loop path would owe): '
grep -c 'files: .*tools/loop/' tools/loop-cases.sh
