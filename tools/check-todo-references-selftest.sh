#!/usr/bin/env bash
# Self-test for tools/check-todo-references.sh: every case runs against a throwaway fixture tree under a temp
# directory, so no project document is read and nothing in the repository is written (design D11).
#
# Cases: a line that instructs a reader to use the old record fails and names the file and the line; a line
# that cites an item passes; a citation the recorded table does not carry fails; the archive, `.pi/tmp` and the
# provenance specs are excluded; the allowlist is printed even when the check passes; a citation resolves
# through the tracker under `--api`; and a root that does not exist is a usage error.
#
# The `--api` case builds a throwaway git working tree around the tooling and answers the tracker through the
# repository's stub `gh` and the `nvid` fixture, so it needs no token and no network.
#
# Usage: check-todo-references-selftest.sh
set -uo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
check="$root/tools/check-todo-references.sh"
tmp=$(mktemp -d "${TMPDIR:-/tmp}/todo-refs-selftest.XXXXXX") || exit 2
trap 'rm -rf "$tmp"' EXIT

[ -x "$check" ] || { echo "check-todo-references-selftest: $check is not executable" >&2; exit 2; }

pass=0
failures=0
before=$(md5sum "$root/AGENTS.md" "$root/TODO.md" "$root/guidelines/Build.md" 2>/dev/null)
empty="$tmp/empty.table"
: > "$empty"

expect() { # name expected-exit tree expected-substring [extra args…]
  local name="$1" want="$2" tree="$3" needle="$4"; shift 4
  local out got ok
  out=$("$check" --root "$tree" "$@" 2>&1)
  got=$?
  ok=1
  [ "$got" = "$want" ] || ok=0
  if [ -n "$needle" ] && ! printf '%s' "$out" | grep -qF -- "$needle"; then ok=0; fi
  if [ "$ok" = 1 ]; then
    echo "PASS  $name (exit $got)"
    pass=$((pass + 1))
  else
    echo "FAIL  $name: expected exit $want containing '$needle', got exit $got:"
    printf '        %s\n' "$out"
    failures=$((failures + 1))
  fi
}

tree() { # name → creates a fixture tree with a guideline, an archive entry and its two specs
  local d="$tmp/$1"
  mkdir -p "$d/guidelines" "$d/openspec/changes/archive/2026-01-01-old" "$d/openspec/specs/compass-button" "$d/.pi/tmp"
  printf '# A guideline document\n' > "$d/guidelines/Build.md"
  printf '# AGENTS.md\n\n## Documentation Map\n\n| what | read |\n|---|---|\n| nothing | `Build.md` §1 |\n' > "$d/AGENTS.md"
  printf '# An archived change\n\n- [x] 1.1 file the residual in `TODO.md`\n' > "$d/openspec/changes/archive/2026-01-01-old/tasks.md"
  printf '# A provenance spec\n\nThe item came from `TODO.md` when it was filed.\n' > "$d/openspec/specs/compass-button/spec.md"
  printf '# machine-local\n\nuse `TODO.md`\n' > "$d/.pi/tmp/scratch.md"
  printf '%s\n' "$d"
}

table() { # path, then rows as `N:NUMBER`
  local path="$1"; shift
  {
    printf '| § | issue | state |\n|---|---|---|\n'
    for row in "$@"; do printf '| %s | #%s | imported |\n' "${row%%:*}" "${row##*:}"; done
  } > "$path"
  printf '%s\n' "$path"
}

# 1. an instruction fails, and the message names the file and the line
d=$(tree instruction)
printf '# A guideline document\n\nFile the residual in `TODO.md` and move on.\n' > "$d/guidelines/Build.md"
expect "refcheck#instructionLineFails" 1 "$d" "file=guidelines/Build.md,line=3" --table "$empty"

# 2. a citation resolves through the recorded table, and the run prints the allowlist it excluded
d=$(tree citation)
printf '# A guideline document\n\nThe rule exists for a defect (TODO.md §113).\n' > "$d/guidelines/Build.md"
t=$(table "$tmp/table-113" 113:42)
expect "refcheck#citationLinePasses" 0 "$d" "1 citation(s) resolve through the recorded table" --table "$t"
out=$("$check" --root "$d" --table "$t" 2>&1)
printf '%s' "$out" | grep -qF 'openspec/changes/archive/*' || { echo "FAIL  refcheck#allowlistIsPrinted: the archive glob is not named"; failures=$((failures + 1)); }
printf '%s' "$out" | grep -qF 'openspec/specs/compass-button/spec.md' || { echo "FAIL  refcheck#allowlistIsPrinted: the provenance spec is not named"; failures=$((failures + 1)); }
if printf '%s' "$out" | grep -qF 'openspec/changes/archive/*' && printf '%s' "$out" | grep -qF 'openspec/specs/compass-button/spec.md'; then
  echo "PASS  refcheck#allowlistIsPrinted (exit 0)"
  pass=$((pass + 1))
fi

# 3. a citation the table does not carry fails and names the number
d=$(tree unresolved)
printf '# A guideline document\n\nThe rule exists for a defect (TODO.md §99999).\n' > "$d/guidelines/Build.md"
expect "refcheck#unresolvedCitationFails" 1 "$d" "§99999 resolves to nothing" --table "$t"

# 4. the exclusions hold: an instruction inside the archive, `.pi/tmp` or a provenance spec is not scanned
d=$(tree excluded)
printf '# coded in\n' > "$d/guidelines/Build.md"
expect "refcheck#allowlistedPathsAreNotScanned" 0 "$d" "file(s) excluded by the allowlist" --table "$empty"

# 5. a citation no table row carries fails even when the tree has no instruction
d=$(tree tableless)
printf '# A guideline document\n\nThe rule exists for a defect (TODO.md §113).\n' > "$d/guidelines/Build.md"
expect "refcheck#missingTableRowFails" 1 "$d" "§113 resolves to nothing" --table "$empty"

# 6. `--api` resolves through the tracker: a throwaway working tree around the tooling, the stub `gh` and the
#    `nvid` fixture answer `list.sh --id 11`
d="$tmp/api"
mkdir -p "$d/tools"
cp -a "$root/tools/issues" "$d/tools/issues"
cp "$root/tools/tooling.json" "$d/tools/tooling.json"
mkdir -p "$d/guidelines"
printf '# A guideline document\n\nThe rule exists for a defect (TODO.md §11).\n' > "$d/guidelines/Build.md"
git -C "$d" init -q . >/dev/null 2>&1 || { echo "FAIL  refcheck#apiResolvesThroughTheTracker: could not create the throwaway repository"; failures=$((failures + 1)); }
out=$(GH_STUB_DIR="$root/tools/issues/fixtures/scenarios/nvid" PATH="$root/tools/issues/fixtures:$PATH" \
  "$check" --root "$d" --table "$empty" --api 2>&1)
got=$?
if [ "$got" = 0 ]; then
  echo "PASS  refcheck#apiResolvesThroughTheTracker (exit 0)"
  pass=$((pass + 1))
else
  echo "FAIL  refcheck#apiResolvesThroughTheTracker: expected exit 0, got exit $got:"
  printf '        %s\n' "$out"
  failures=$((failures + 1))
fi
# and a § the tracker does not carry stays a failure under `--api` too
printf '# A guideline document\n\nThe rule exists for a defect (TODO.md §99999).\n' > "$d/guidelines/Build.md"
expect "refcheck#apiLeavesAnUnknownNumberRed" 1 "$d" "§99999 resolves to nothing" --table "$empty" --api

# 7. a root that does not exist is a usage error
out=$("$check" --root "$tmp/nowhere" 2>&1)
got=$?
if [ "$got" = 2 ]; then
  echo "PASS  refcheck#unknownRootIsAUsageError (exit 2)"
  pass=$((pass + 1))
else
  echo "FAIL  refcheck#unknownRootIsAUsageError: expected exit 2, got exit $got:"
  printf '        %s\n' "$out"
  failures=$((failures + 1))
fi

after=$(md5sum "$root/AGENTS.md" "$root/TODO.md" "$root/guidelines/Build.md" 2>/dev/null)
if [ "$after" = "$before" ]; then
  echo "PASS  the repository's documents are untouched by every case"
  pass=$((pass + 1))
else
  echo "FAIL  a project document changed while the cases ran"
  failures=$((failures + 1))
fi

echo "check-todo-references-selftest: $pass passed, $failures failed"
[ "$failures" = 0 ] || exit 1
