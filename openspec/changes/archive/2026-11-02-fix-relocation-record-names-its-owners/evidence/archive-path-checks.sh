#!/usr/bin/env bash
# archive-path-checks.sh — the checks the archived delta's own path owes, run after the archive moved the delta
# into `openspec/specs/documentation-ownership/spec.md`.
#
# The project's gate was run once, before the archive, as `tools/loop/prompts/worker.md` requires: at that moment the
# diff was the two `tools/check-openspec-hygiene*` scripts, so the gate owed those two suites and no `openspec/*`
# one. The archive then merges the delta into a path the profile maps to seven checks
# (`./tools/loop/gate.sh --plan` names them), so they are run here, focused, and quoted — not by re-running the
# gate, which is what would destroy its record.
#
# Usage: bash archive-path-checks.sh        writes the transcript beside this script and prints it.
set -uo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." || exit 2   # the change root (this one is archived: one level deeper)
out="openspec/changes/archive/2026-11-02-fix-relocation-record-names-its-owners/evidence/archive-path-checks.log"

{
  printf '# the checks the archived delta'"'"'s own path owes — %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf '# command: ./tools/loop/gate.sh --plan, run after the archive moved the delta into openspec/specs/\n'
  printf '# (the gate itself was run once, before the archive, over the two paths the delta edited)\n\n'

  while IFS= read -r cmd; do
    [ -n "$cmd" ] || continue
    printf '## %s\n' "$cmd"
    bash -c "$cmd" 2>&1 | tail -3
    rc=${PIPESTATUS[0]}
    printf 'exit=%s\n\n' "$rc"
  done <<'CMDS'
bash tools/gen-feature-list.sh --check-classification
tools/check-doc-routes.sh
tools/check-doc-routes-selftest.sh
bash tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list
tools/check-todo-references.sh
tools/check-openspec-hygiene.sh
tools/check-openspec-hygiene-selftest.sh
CMDS
} 2>&1 | tee "$out"
