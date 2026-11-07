#!/usr/bin/env bash
# mutation.sh — the revert-check for the class this change adds: one mutation per invariant, the named case must
# go red, restore, the case must go green again (`.pi/skills/revert-check`).
#
# Two invariants, two mutations, two named cases:
#
#   glob-test-never-matches   the class's `/\*/` becomes a pattern that matches no citation, so a record that cites
#                             its owners as a directory listing passes again —
#                             `artifact-hygiene#anOwnerCitedAsADirectoryListingIsReported` must fail. It is the
#                             class's own case.
#   fence-is-not-a-citation   `!fence &&` is dropped, so a quotation inside a fenced block counts as a citation —
#                             `artifact-hygiene#aWrittenPurposePasses` must fail, because the clean tree carries the
#                             refused form inside a fence on purpose.
#
# Nothing is mutated in the tree: the checker is copied to a scratch root, the mutation is applied to the copy, and
# the case is run against that root — so a killed run cannot leave a mutation behind.
#
# Usage: bash mutation.sh        writes the transcript beside this script and prints it.
set -uo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." || exit 2   # the change root
checker="tools/check-openspec-hygiene.sh"
selftest="tools/check-openspec-hygiene-selftest.sh"
out="openspec/changes/fix-relocation-record-names-its-owners/evidence/mutation.log"

scratch=$(mktemp -d) || { echo "mutation: no scratch directory" >&2; exit 2; }
trap 'rm -rf "$scratch"' EXIT

mutated_run() { # <mutation> <case id>
  local mut="$1" cid="$2" root="$scratch/root" rc
  rm -rf "$root"; mkdir -p "$root/tools"
  cp "$checker" "$root/tools/check-openspec-hygiene.sh" || return 2
  case "$mut" in
    glob-test-never-matches)
      sed -i 's#/\*/#/NO-CITATION-IS-A-GLOB/#' "$root/tools/check-openspec-hygiene.sh" ;;
    fence-is-not-a-citation)
      sed -i 's#!fence && ##' "$root/tools/check-openspec-hygiene.sh" ;;
    *) printf 'MUTATION-UNKNOWN %s\n' "$mut"; return 2 ;;
  esac
  if cmp -s "$checker" "$root/tools/check-openspec-hygiene.sh"; then
    printf 'MUTATION-DID-NOT-APPLY %s\n' "$mut"
    return 2
  fi
  bash "$selftest" --root "$root" --case "$cid" 2>&1 | tail -3
  rc=${PIPESTATUS[0]}
  printf '  mutated run %s: exit=%s\n' "$mut" "$rc"
  [ "$rc" -eq 1 ]
}

green_run() { # <case id>
  bash "$selftest" --root . --case "$1" 2>&1 | tail -1
  local rc=${PIPESTATUS[0]}
  [ "$rc" -eq 0 ]
}

{
  printf '# revert-check for tools/check-openspec-hygiene.sh class 4 — %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf '# base 650028bfdf6e652ebd045b4af1be0a90b6c0a481\n\n'

  printf '## green before any mutation, so a red below is the mutation'"'"'s\n'
  green_run 'artifact-hygiene#anOwnerCitedAsADirectoryListingIsReported' ||
    { printf 'RED-CHECK the case is not green before the mutation\n'; exit 1; }

  printf '\n## mutation 1: glob-test-never-matches -> artifact-hygiene#anOwnerCitedAsADirectoryListingIsReported\n'
  mutated_run glob-test-never-matches 'artifact-hygiene#anOwnerCitedAsADirectoryListingIsReported' ||
    { printf 'RED-CHECK the mutation did not redden the case\n'; exit 1; }

  printf '\n## mutation 2: fence-is-not-a-citation -> artifact-hygiene#aWrittenPurposePasses\n'
  mutated_run fence-is-not-a-citation 'artifact-hygiene#aWrittenPurposePasses' ||
    { printf 'RED-CHECK the mutation did not redden the case\n'; exit 1; }

  printf '\n## restored: the tree'"'"'s own checker, both cases green again\n'
  green_run 'artifact-hygiene#anOwnerCitedAsADirectoryListingIsReported' ||
    { printf 'RED-CHECK the restore did not turn the case green\n'; exit 1; }
  green_run 'artifact-hygiene#aWrittenPurposePasses' ||
    { printf 'RED-CHECK the restore did not turn the clean case green\n'; exit 1; }

  printf '\nREVERT-CHECK 2 mutation(s) run, 2 went red and restored green, 0 problem(s)\n'
} 2>&1 | tee "$out"

grep -q 'REVERT-CHECK 2 mutation(s) run, 2 went red and restored green, 0 problem(s)' "$out"
