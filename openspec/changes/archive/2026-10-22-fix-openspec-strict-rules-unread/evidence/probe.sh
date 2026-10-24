#!/usr/bin/env bash
# probe.sh — the two cases of this change, over the entry document's `OpenSpec Workflow` section.
#
# The defect is a documentation gap, so the case that exercises it reads the document: the passage an author
# takes the validator's rules from must state both rules, name the requirement that owns them, and name the
# strict command (both invocations). No build, no device, no network — text only, exactly like
# `tools/check-doc-routes.sh`.
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

# The strict-rules passage inside that section: its lead-in sentence to the end of the section. The cases read
# this passage rather than the whole section, because the section's CI-gated bullets already name a spec path —
# a needle satisfied by a sibling bullet would not show that *these* rules name their owner.
passage=$(printf '%s\n' "$section" | awk '/^Two more rules are the validator/{f=1} f' | tr '\n' ' ' | tr -s ' ')

run_case() { # $1 case name, $2.. the fixed strings that must all occur
  local name="$1"; shift
  local missing=""
  for needle in "$@"; do
    case "$passage" in
      *"$needle"*) ;;
      *) missing="$missing [$needle]" ;;
    esac
  done
  if [ -n "$missing" ]; then
    printf 'FAIL %s — the OpenSpec Workflow strict-rules passage does not state:%s\n' "$name" "$missing"
    return 1
  fi
  printf 'PASS %s\n' "$name"
  return 0
}

case_a() {
  run_case theEntryDocumentStatesTheValidatorStrictRules \
    'openspec/specs/documentation-ownership/spec.md' \
    'The validator'\''s own strict rules are read before the first write' \
    'MODIFIED' \
    'repeated verbatim' \
    '500 characters' \
    'the body, not its scenarios'
}

case_b() {
  run_case theEntryDocumentNamesTheStrictValidationCommand \
    'openspec validate <change> --strict' \
    'openspec validate --changes --strict'
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
