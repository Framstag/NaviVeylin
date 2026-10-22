#!/usr/bin/env bash
# probe.sh — the two cases of this change, over the entry document's `OpenSpec Workflow` section.
#
# The defect is a documentation gap, so the case that exercises it reads the document: the section an author
# takes the artifact rules from must state the two rules `openspec validate --strict` fails on, and must name
# the strict command itself. No build, no device, no network — text only, exactly like `tools/check-doc-routes.sh`.
#
# Exit 0 when both cases pass, 1 when one fails, 2 on a usage error.
#
# Usage: probe.sh [<case>]      # no argument runs both cases; case names are the two below
set -uo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
while [ ! -f "$root/AGENTS.md" ]; do
  [ "$root" != "/" ] || { echo "probe: no AGENTS.md above $(dirname "${BASH_SOURCE[0]}")" >&2; exit 2; }
  root=$(dirname "$root")
done
agents="$root/AGENTS.md"
[ -f "$agents" ] || { echo "probe: no $agents" >&2; exit 2; }

# The section, from its `## ` heading to the next one — the unit a reader takes the artifact rules from.
section=$(awk '/^## OpenSpec Workflow$/{f=1;next} f && /^## /{exit} f{print}' "$agents")

run_case() { # $1 case name, $2.. the fixed strings that must all occur
  local name="$1"; shift
  local missing=""
  for needle in "$@"; do
    case "$section" in
      *"$needle"*) ;;
      *) missing="$missing [$needle]" ;;
    esac
  done
  if [ -n "$missing" ]; then
    printf 'FAIL %s — the OpenSpec Workflow section does not state:%s\n' "$name" "$missing"
    return 1
  fi
  printf 'PASS %s\n' "$name"
  return 0
}

case_a() {
  run_case theEntryDocumentStatesTheValidatorStrictRules \
    'MODIFIED' \
    'repeated verbatim' \
    '500 characters' \
    'the body, not its scenarios'
}

case_b() {
  run_case theEntryDocumentNamesTheStrictValidationCommand \
    'openspec validate <change> --strict'
}

ran=0
failed=0
try_case() { ran=$((ran + 1)); "$1" || failed=$((failed + 1)); }
case "${1:-}" in
  '')                      try_case case_a; try_case case_b ;;
  theEntryDocumentStates*) try_case case_a ;;
  theEntryDocumentNames*)  try_case case_b ;;
  *) echo "probe: unknown case '$1'" >&2; exit 2 ;;
esac
printf 'probe: %s of %s cases failed\n' "$failed" "$ran"
[ "$failed" -eq 0 ] || exit 1
exit 0
