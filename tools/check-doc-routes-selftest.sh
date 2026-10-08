#!/usr/bin/env bash
# Self-test for tools/check-doc-routes.sh: every case runs against a throwaway fixture under a temp
# directory, so no project document is read and nothing in the repository is written.
#
# Cases: a map that resolves (including a duplicate section number resolved by its heading text), a
# dangling number, a bare number that is ambiguous, a heading text that matches nothing, a document the
# table references but that does not exist, an AGENTS.md with no Documentation Map, a guideline document
# that no route names, an extra guideline that is routed and resolves, and an extra guideline that is routed
# but whose section does not exist.
#
# The check derives its documents from the fixture's own `guidelines/` directory, so a fixture carries the
# four base documents plus whichever extra one a case adds — and its table must name every document present.
#
# Usage: check-doc-routes-selftest.sh
set -uo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
check="$root/tools/check-doc-routes.sh"
tmp=$(mktemp -d "${TMPDIR:-/tmp}/doc-routes-selftest.XXXXXX") || exit 2
trap 'rm -rf "$tmp"' EXIT

[ -x "$check" ] || { echo "check-doc-routes-selftest: $check is not executable" >&2; exit 2; }

pass=0
failures=0

BASE_ROWS='| the base documents | `Build.md` §99, `Design.md` §99, `MapRendering.md` §99, `Regulatory.md` §99 |'

mk_fixture() { # $1 = directory
  d="$1"
  mkdir -p "$d/guidelines"
  for f in Build Design MapRendering Regulatory; do
    printf '# %s Specification\n\n## 99. Unused\n' "$f" > "$d/guidelines/$f.md"
  done
  cat > "$d/guidelines/UI.md" <<'FIXTURE'
# UI Specification

## 1. Cross-variant UI parity (general rule)

## 14. Rotation Gesture Display-Layer Handoff

## 14. Android Auto renderer — smooth follow (overrun + blit + extrapolation)
FIXTURE
}

add_doc() { # $1 = directory, $2 = document name
  printf '# %s Specification\n\n## 99. Unused\n' "$2" > "$1/guidelines/$2.md"
}

write_agents() { # $1 = directory, $2 = case rows
  cat > "$1/AGENTS.md" <<FIXTURE
# AGENTS.md

## Project Overview

Facts only.

## Documentation Map

Read the section that owns the topic.

| what you are changing | read |
|---|---|
$BASE_ROWS
$2

## Tech Stack

| Layer | Technology |
FIXTURE
}

expect() { # $1 name, $2 expected exit, $3 fixture dir, $4 expected substring
  local out got ok
  out=$("$check" --root "$3" 2>&1)
  got=$?
  ok=1
  [ "$got" = "$2" ] || ok=0
  if [ -n "${4:-}" ] && ! printf '%s' "$out" | grep -qF -- "$4"; then ok=0; fi
  if [ "$ok" = 1 ]; then
    echo "PASS  $1 (exit $got)"
    pass=$((pass + 1))
  else
    echo "FAIL  $1: expected exit $2 containing '$4', got exit $got:"
    printf '        %s\n' "$out"
    failures=$((failures + 1))
  fi
}

agents_before=$(md5sum "$root/AGENTS.md" | awk '{print $1}')

for d in good dangling ambiguous mismatch missing noregion extra-unrouted extra-routed extra-dangling; do
  mk_fixture "$tmp/$d"
done

write_agents "$tmp/good" '| phone UI | `UI.md` §1 "Cross-variant UI parity" |
| car renderer | `UI.md` §14 "Android Auto renderer" |
| the other §14 | `UI.md` §14 "Rotation Gesture" |'
expect "a map where every reference resolves" 0 "$tmp/good" "all 7 section references resolve"

write_agents "$tmp/dangling" '| gone | `UI.md` §99 "First section" |'
expect "a dangling section number fails naming the reference" 1 "$tmp/dangling" "UI.md §99"

write_agents "$tmp/ambiguous" '| ambiguous | `UI.md` §14 |'
expect "a bare duplicated number is reported as ambiguous" 1 "$tmp/ambiguous" "ambiguous"

write_agents "$tmp/mismatch" '| wrong text | `UI.md` §1 "No such heading" |'
expect "heading text that matches nothing fails" 1 "$tmp/mismatch" "contains that text"

write_agents "$tmp/missing" '| absent document | `UI.md` §1 "Cross-variant" |'
rm -f "$tmp/missing/guidelines/UI.md"
expect "a document the table references but that does not exist is reported" 1 "$tmp/missing" "does not exist"

write_agents "$tmp/noregion" '| phone UI | `UI.md` §1 "Cross-variant UI parity" |'
printf '# AGENTS.md\n\n## Project Overview\n\nNo map here.\n' > "$tmp/noregion/AGENTS.md"
expect "an AGENTS.md without a Documentation Map is reported" 2 "$tmp/noregion" "Documentation Map"

add_doc "$tmp/extra-unrouted" Logging
write_agents "$tmp/extra-unrouted" '| phone UI | `UI.md` §1 "Cross-variant UI parity" |'
expect "an extra guideline that no route names is reported" 1 "$tmp/extra-unrouted" "does not name this document"

add_doc "$tmp/extra-routed" Logging
write_agents "$tmp/extra-routed" '| phone UI | `UI.md` §1 "Cross-variant UI parity" |
| logging | `Logging.md` §99 "Unused" |'
expect "an extra guideline that is routed and resolves passes without editing the check" 0 "$tmp/extra-routed" "all 6 section references resolve"

add_doc "$tmp/extra-dangling" Logging
write_agents "$tmp/extra-dangling" '| phone UI | `UI.md` §1 "Cross-variant UI parity" |
| logging | `Logging.md` §42 "Unused" |'
expect "an extra guideline whose section does not exist is reported" 1 "$tmp/extra-dangling" "Logging.md §42"

agents_after=$(md5sum "$root/AGENTS.md" | awk '{print $1}')
if [ "$agents_before" = "$agents_after" ]; then
  echo "PASS  the repository's AGENTS.md is untouched by every case"
  pass=$((pass + 1))
else
  echo "FAIL  AGENTS.md changed: $agents_before -> $agents_after"
  failures=$((failures + 1))
fi

echo "check-doc-routes-selftest: $pass passed, $failures failed"
[ "$failures" = 0 ] || exit 1
