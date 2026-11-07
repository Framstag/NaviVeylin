#!/usr/bin/env bash
# The checks the archived delta owes, each with its own exit status recorded — the list is the gate's own:
# `bash tools/loop/gate.sh --plan --base 0f366176` prints these commands for the archived paths.
set -uo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
cd "$root" || exit 2
change=openspec/changes/archive/2026-10-24-fix-loop-gate-case-reads-the-stream

printf '# The checks the *archived* delta owes, taken %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
printf '# The delta now carries openspec/** and *.md paths, so the profile gates them with the classification\n'
printf '# check, the doc routes and the todo-reference check — and with nothing compiled, no Gradle suite.\n'
printf '# The loop suite is *not* re-run for this commit: `tools/loop-cases.sh` is byte-identical to the file\n'
printf '# gate.log measured (%s) and that run cost 1111 s. The exclusion is named here, not left implicit.\n' \
  "$(git hash-object tools/loop-cases.sh)"
printf '# The list is the gate'"'"'s own: `bash tools/loop/gate.sh --plan --base 0f366176` prints these six commands.\n'

run() {
  printf '\n$ %s\n' "$*"
  "$@" 2>&1
  printf 'exit=%s\n' "$?"
}

run bash tools/gen-feature-list.sh --check-classification
run tools/check-doc-routes.sh
run tools/check-doc-routes-selftest.sh
run tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list
run tools/check-todo-references.sh
run openspec validate --changes --strict
run openspec validate agent-issue-loop --type spec
run bash .pi/skills/fix-loop/scripts/evidence-check.sh "$change" --root .
