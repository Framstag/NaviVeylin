#!/usr/bin/env bash
# The change-readiness checker's cases: one per refusal it can print, and the passes that prove it judges a
# declaration and never the meaning of a section.
#
# Every case builds its own change directory in a scratch tree and drives the real checker
# (`tools/check-change-readiness.sh`) over it, so no repository file is read as a fixture and nothing is written
# into the tree: no tracker, no git, no network, no build.
#
# The scratch tree lives under the loop's recorded temporary root — `LOOP_TMP_DIR` in `tools/loop/loop.conf`,
# which is the project's `.pi/tmp` when it is empty — and a trap on every signal removes it, so a killed run
# leaves nothing behind and `tools/loop/loop-clean.sh` finds nothing to report.
#
# The cases:
#
#   readiness#declaredNone              a change that leaves nothing open declares `none` — no refusal
#   readiness#openDecision             the declaration names a decision — `open-decision` with its line
#   readiness#missingDeclaration       no `## Open decisions` section at all — `declaration-missing`
#   readiness#deferralIsNotAFork       a real in-flight `## Open Questions` deferral is not read — no refusal
#   readiness#scenarioWithoutCase      a `#### Scenario:` with no `**Case**:` line — `scenario-without-case`
#   readiness#caseNoneWithReason       `**Case**: none — <reason>` is a claim, not an omission — no refusal
#   readiness#scenarioWithCase         a named case passes — no refusal
#   readiness#surfaceMissing           a proposal with no `surface:` line — `surface-missing`
#   readiness#surfaceCommandMissing    a `surface:` line quoting no command — `surface-command-missing`
#   readiness#surfacePresent           a counted surface with its command passes — no refusal
#   readiness#oneVerdictLine           exactly one `LOOP-CHANGE-READY:` line, exit 0 only for `ok`
#   readiness#wordingIsNotAVerdict     prose that reads like a fork is not a refusal
#   readiness#changeUnreadable         a change directory with no `design.md` — `change-unreadable`
#
# Every case carries an id, so a task item or a mutation record can quote one instead of a sentence;
# `--case <id>` runs exactly one of them (that is what `tools/check-change-readiness-mutations.json` uses).
#
# Usage: check-change-readiness-selftest.sh [--list] [--case <readiness#name>]
# Exit:  0 every case passed, 1 a case failed, 2 a usage error or an unknown case id.
set -uo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
check="$root/tools/check-change-readiness.sh"
list_only=0
case_filter=""
while [ $# -gt 0 ]; do
  case "$1" in
    --list) list_only=1; shift ;;
    --case) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { echo "check-change-readiness-selftest: --case needs a value" >&2; exit 2; }; case_filter="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,32p' "$0"; exit 0 ;;
    *) echo "check-change-readiness-selftest: unknown argument '$1'" >&2; exit 2 ;;
  esac
done

CASES=(
  'readiness#declaredNone'
  'readiness#openDecision'
  'readiness#missingDeclaration'
  'readiness#deferralIsNotAFork'
  'readiness#scenarioWithoutCase'
  'readiness#caseNoneWithReason'
  'readiness#scenarioWithCase'
  'readiness#surfaceMissing'
  'readiness#surfaceCommandMissing'
  'readiness#surfacePresent'
  'readiness#oneVerdictLine'
  'readiness#wordingIsNotAVerdict'
  'readiness#changeUnreadable'
)

if [ "$list_only" -eq 1 ]; then
  printf '%s\n' "${CASES[@]}"
  exit 0
fi

[ -x "$check" ] || { echo "check-change-readiness-selftest: $check is not executable" >&2; exit 2; }

# The scratch tree goes under the loop's recorded temporary root, so a failed run leaves nothing in the diff and
# a killed one leaves nothing the loop's cleaner cannot find.
tmp_root=""
conf="$root/tools/loop/loop.conf"
if [ -f "$conf" ]; then
  tmp_root=$(sed -n 's/^LOOP_TMP_DIR="\(.*\)"/\1/p' "$conf" | tail -1)
fi
case "$tmp_root" in
  '') tmp_root="$root/.pi/tmp" ;;
  /*) : ;;
  *)  tmp_root="$root/$tmp_root" ;;
esac
mkdir -p "$tmp_root" 2>/dev/null || { echo "check-change-readiness-selftest: cannot create $tmp_root" >&2; exit 2; }
tmp=$(mktemp -d "$tmp_root/check-change-readiness-selftest.XXXXXX") || {
  echo "check-change-readiness-selftest: cannot create a scratch directory under $tmp_root" >&2; exit 2
}
cleanup() { rm -rf "$tmp"; }
trap cleanup EXIT
for sig in INT TERM HUP QUIT; do trap 'cleanup; exit 2' "$sig"; done

pass=0
failures=0

# ── the fixture ──────────────────────────────────────────────────────────────────────────────────────────
# A change that satisfies every requirement, which each case then breaks in exactly one place.
mk_change() { # <directory>
  mkdir -p "$1/specs/fixture"
  cat > "$1/design.md" <<'FIXTURE'
# Design — fixture

## Decisions

The fixture decides nothing that a worker would have to decide.

## Open decisions

none
FIXTURE
  cat > "$1/proposal.md" <<'FIXTURE'
# Proposal — fixture

## What Changes

- one file.

surface: 0 cases added, 1 code file created — `grep -rn 'fixture' tools/`
FIXTURE
  cat > "$1/specs/fixture/spec.md" <<'FIXTURE'
# fixture Specification

## ADDED Requirements

### Requirement: the fixture does something

The fixture SHALL do one thing.

#### Scenario: the case names itself

- **WHEN** the fixture runs
- **THEN** it reports its case
- **Case**: `readiness#scenarioWithCase`
FIXTURE
}

run_check() { # <args...> — OUT and RC are set
  OUT=$("$check" "$@" 2>&1)
  RC=$?
}

expect() { # <id> <description> <expected exit> <expected substring> <args...>
  local id="$1" desc="$2" want_rc="$3" want="$4"
  shift 4
  [ -z "$case_filter" ] || [ "$case_filter" = "$id" ] || return 0
  run_check "$@"
  local ok=1
  [ "$RC" = "$want_rc" ] || ok=0
  [ -z "$want" ] || printf '%s' "$OUT" | grep -qF -- "$want" || ok=0
  if [ "$ok" -eq 1 ]; then
    echo "PASS  $id — $desc (exit $RC)"
    pass=$((pass + 1))
  else
    echo "FAIL  $id — $desc: expected exit $want_rc containing '$want', got exit $RC:"
    printf '        %s\n' "$OUT"
    failures=$((failures + 1))
  fi
}

# ── the six refusals ──────────────────────────────────────────────────────────────────────────────────────
mk_change "$tmp/declared-none"
expect readiness#declaredNone \
  "a change that leaves nothing open declares none, and nothing is refused" 0 \
  "LOOP-CHANGE-READY: ok" "$tmp/declared-none"

mkdir -p "$tmp/open-decision/specs/fixture"
cat > "$tmp/open-decision/design.md" <<'FIXTURE'
# Design — fixture

## Open decisions

Should the store be rebuilt per write or patched in place? Both work; a person has to pick one.
FIXTURE
cp "$tmp/declared-none/proposal.md" "$tmp/open-decision/proposal.md"
cp "$tmp/declared-none/specs/fixture/spec.md" "$tmp/open-decision/specs/fixture/spec.md"
expect readiness#openDecision \
  "a declared decision is refused with its own line and the line number" 1 \
  "open-decision=design.md:5" "$tmp/open-decision"

mkdir -p "$tmp/missing-declaration/specs/fixture"
printf '# Design — fixture\n\n## Decisions\n\nThe fixture decides nothing.\n' > "$tmp/missing-declaration/design.md"
cp "$tmp/declared-none/proposal.md" "$tmp/missing-declaration/proposal.md"
cp "$tmp/declared-none/specs/fixture/spec.md" "$tmp/missing-declaration/specs/fixture/spec.md"
expect readiness#missingDeclaration \
  "a design.md with no ## Open decisions is refused, not passed" 1 \
  "declaration-missing=design.md" "$tmp/missing-declaration"

# The deferral fixture is a copy of a real in-flight design.md section: `fix-car-follow-reengage-render` carried
# this entry on 2026-10-30, and it defers without naming a choice a worker would have to make. It is read from
# the tree when the change is still in flight, and the frozen copy below stands in once it archives — the case is
# about the tree's own shape, not about a string invented for it.
deferral=""
if [ -f "$root/openspec/changes/fix-car-follow-reengage-render/design.md" ]; then
  deferral=$(awk '/^## Open Questions/{p=1;next} p&&/^## /{exit} p{print}' \
    "$root/openspec/changes/fix-car-follow-reengage-render/design.md")
fi
if [ -z "${deferral//[[:space:]]/}" ]; then
  deferral='- Should the locale rule later be enforced by a build gate (the shape `checkNoCoordinatesInLogs` uses
  for coordinates), instead of review? Deferrable: the rule is one requirement and one site today, and
  a scanner for default-locale format calls is its own change.'
fi
mkdir -p "$tmp/deferral/specs/fixture"
cat > "$tmp/deferral/design.md" <<FIXTURE
# Design — fixture

## Open decisions

none

## Open Questions

$deferral
FIXTURE
cp "$tmp/declared-none/proposal.md" "$tmp/deferral/proposal.md"
cp "$tmp/declared-none/specs/fixture/spec.md" "$tmp/deferral/specs/fixture/spec.md"
expect readiness#deferralIsNotAFork \
  "a deferrable ## Open Questions entry is not read, so a legitimate deferral is not a refusal" 0 \
  "LOOP-CHANGE-READY: ok" "$tmp/deferral"

mkdir -p "$tmp/scenario-without-case/specs/fixture"
cp "$tmp/declared-none/design.md" "$tmp/scenario-without-case/design.md"
cp "$tmp/declared-none/proposal.md" "$tmp/scenario-without-case/proposal.md"
cat > "$tmp/scenario-without-case/specs/fixture/spec.md" <<'FIXTURE'
# fixture Specification

## ADDED Requirements

### Requirement: the fixture does something

The fixture SHALL do one thing.

#### Scenario: the case is missing

- **WHEN** the fixture runs
- **THEN** nothing names a case
FIXTURE
expect readiness#scenarioWithoutCase \
  "a scenario with no **Case**: line is refused at its own line" 1 \
  "scenario-without-case=specs/fixture/spec.md:9" "$tmp/scenario-without-case"

mkdir -p "$tmp/case-none-with-reason/specs/fixture"
cp "$tmp/declared-none/design.md" "$tmp/case-none-with-reason/design.md"
cp "$tmp/declared-none/proposal.md" "$tmp/case-none-with-reason/proposal.md"
cat > "$tmp/case-none-with-reason/specs/fixture/spec.md" <<'FIXTURE'
# fixture Specification

## ADDED Requirements

### Requirement: the fixture does something

The fixture SHALL do one thing.

#### Scenario: the separation is structural

- **WHEN** the fixture runs
- **THEN** the case cannot exist
- **Case**: none — the separation is structural
FIXTURE
expect readiness#caseNoneWithReason \
  "none with a reason is a claim the checker accepts" 0 \
  "LOOP-CHANGE-READY: ok" "$tmp/case-none-with-reason"

mk_change "$tmp/scenario-with-case"
expect readiness#scenarioWithCase \
  "a scenario that names its case passes" 0 \
  "LOOP-CHANGE-READY: ok" "$tmp/scenario-with-case"

# ── the surface ───────────────────────────────────────────────────────────────────────────────────────────
mkdir -p "$tmp/surface-missing/specs/fixture"
cp "$tmp/declared-none/design.md" "$tmp/surface-missing/design.md"
cp "$tmp/declared-none/specs/fixture/spec.md" "$tmp/surface-missing/specs/fixture/spec.md"
printf '# Proposal — fixture\n\n## What Changes\n\n- one file.\n' > "$tmp/surface-missing/proposal.md"
expect readiness#surfaceMissing \
  "a proposal with no surface: line is refused" 1 \
  "surface-missing=proposal.md" "$tmp/surface-missing"

mkdir -p "$tmp/surface-command-missing/specs/fixture"
cp "$tmp/declared-none/design.md" "$tmp/surface-command-missing/design.md"
cp "$tmp/declared-none/specs/fixture/spec.md" "$tmp/surface-command-missing/specs/fixture/spec.md"
printf '# Proposal — fixture\n\nsurface: 2 cases added, 0 code files\n' > "$tmp/surface-command-missing/proposal.md"
expect readiness#surfaceCommandMissing \
  "a surface line that quotes no command is refused, because its count cannot be re-taken" 1 \
  "surface-command-missing=proposal.md:3" "$tmp/surface-command-missing"

mk_change "$tmp/surface-present"
expect readiness#surfacePresent \
  "a counted surface with the command that produced it passes" 0 \
  "LOOP-CHANGE-READY: ok" "$tmp/surface-present"

# ── the verdict, the prose rule and the unreadable path ───────────────────────────────────────────────────
mk_change "$tmp/one-verdict-line"
if [ -z "$case_filter" ] || [ "$case_filter" = 'readiness#oneVerdictLine' ]; then
  run_check "$tmp/one-verdict-line"
  count=$(printf '%s\n' "$OUT" | grep -c '^LOOP-CHANGE-READY:')
  if [ "$RC" = 0 ] && [ "$count" = 1 ]; then
    echo "PASS  readiness#oneVerdictLine — the verdict is exactly one machine-readable line and exit 0 is ok (exit $RC)"
    pass=$((pass + 1))
  else
    echo "FAIL  readiness#oneVerdictLine: expected one LOOP-CHANGE-READY line and exit 0, got $count line(s), exit $RC:"
    printf '        %s\n' "$OUT"
    failures=$((failures + 1))
  fi
fi

mkdir -p "$tmp/wording/specs/fixture"
cat > "$tmp/wording/design.md" <<'FIXTURE'
# Design — fixture

## Decisions

Option A rebuilds the store on every write; option B patches it in place. Which one should we take? The
question is settled below, and the answer is B — or A, if the measurement disagrees.

## Open decisions

none
FIXTURE
cp "$tmp/declared-none/proposal.md" "$tmp/wording/proposal.md"
cp "$tmp/declared-none/specs/fixture/spec.md" "$tmp/wording/specs/fixture/spec.md"
expect readiness#wordingIsNotAVerdict \
  "prose that reads like a fork is not a refusal while the declaration says none" 0 \
  "LOOP-CHANGE-READY: ok" "$tmp/wording"

mkdir -p "$tmp/unreadable"
cp "$tmp/declared-none/proposal.md" "$tmp/unreadable/proposal.md"
expect readiness#changeUnreadable \
  "a change directory with no design.md is refused by name, not crashed on" 1 \
  "change-unreadable=design.md" "$tmp/unreadable"

if [ -n "$case_filter" ] && [ "$pass" -eq 0 ] && [ "$failures" -eq 0 ]; then
  echo "check-change-readiness-selftest: no case with the id '$case_filter'" >&2
  exit 2
fi

if [ "$failures" -ne 0 ]; then
  echo "check-change-readiness-selftest: $failures of $((pass + failures)) cases failed"
  exit 1
fi
echo "check-change-readiness-selftest: all $pass cases passed (throwaway fixtures under the loop's temporary root; no git, no build, no network)"
exit 0
