#!/usr/bin/env bash
# mutation.sh — the change's own invariant, falsified once (the `revert-check` skill).
#
# The invariant: the entry document names `.pi/skills/create-issue` and `.pi/skills/device-check` by their paths.
# The mutation deletes the bullet that names them — the shape the diet of 2026-10-31 left — runs the case, and
# restores the document. A case that stays green under the mutation is not evidence, so this script exits 1 when
# the case passes mutated, and 1 when it fails restored.
#
# Usage: mutation.sh [out-dir]
set -uo pipefail
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
root=$(cd "$here/../../../.." && pwd)
out_dir="${1:-$here}"
case_id='routed-read#theEntryDocumentNamesTheLoopSkillsByPath'
doc="$root/AGENTS.md"

backup=$(mktemp "${TMPDIR:-/tmp}/mutation-AGENTS.XXXXXX") || exit 2
cp "$doc" "$backup"
trap 'cp "$backup" "$doc"; rm -f "$backup"' EXIT

{
  printf '# %s — mutate the entry document once, restore it\n' "$case_id"
  printf '# %s\n\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"

  # the bullet the mutation removes: from `- two skills are the loop's ends` through its last line
  awk 'BEGIN{drop=0} /^- two skills are the loop.s ends/{drop=1} drop && /^$/ && NR>0 && !/^- two skills/{drop=0; next} drop{next} {print}' \
    "$backup" > "$doc"
  printf '# mutated: %s path(s) named in AGENTS.md\n' "$(grep -c '\.pi/skills/\(create-issue\|device-check\)' "$doc")"
  bash "$root/tools/check-doc-routes-selftest.sh" --case "$case_id" 2>&1
  printf 'MUTATION exit=%s (expected 1)\n\n' "$?"

  cp "$backup" "$doc"
  printf '# restored: %s path(s) named in AGENTS.md\n' "$(grep -c '\.pi/skills/\(create-issue\|device-check\)' "$doc")"
  bash "$root/tools/check-doc-routes-selftest.sh" --case "$case_id" 2>&1
  printf 'RESTORED exit=%s (expected 0)\n' "$?"
} > "$out_dir/mutation.log" 2>&1

tail -4 "$out_dir/mutation.log"
grep -q 'MUTATION exit=1' "$out_dir/mutation.log" && grep -q 'RESTORED exit=0' "$out_dir/mutation.log"
