#!/usr/bin/env bash
# The case-name traceability check's cases (`tools/check-case-names.sh`): one per rule the check states, each
# driven over a throwaway tree, so no repository spec and no repository suite is used as a fixture.
#
#   traceability#aNameTheLoopSuiteRunsResolves              a command row's `group#case` resolves, exit 0
#   traceability#aNameAnotherHarnessRunsResolves            a second suite's name and a Gradle `module: Class#m`
#   traceability#aNameNoHarnessRunsIsRefused                a name no row lists — refused with file, line, scenario
#   traceability#aSharedGroupIsDeclared                     two rows claim `run` — declared, printed once, no refusal
#   traceability#aDuplicateFullNameIsRefused                one full name in two rows — refused naming both
#   traceability#aRowWhoseListIsAbsentIsRefused             a row whose list is missing — refused naming the row
#   traceability#aRowThatEmitsAnUnclaimedGroupIsRefused     a hash name outside the row's declared groups
#   traceability#anExclusionThatHidesANameIsRefused         an `exclude` no other row covers — a hiding place
#   traceability#theUnclaimedCasesAreCounted                a case no scenario names — counted, exit 0
#   traceability#anUnclaimedCaseNeverRefuses                a whole harness unclaimed — still exit 0
#   traceability#aNameInProseIsNotAClaim                    a case-shaped name outside a `**Case**:` line — ignored
#   traceability#aScenarioWithoutACaseLineIsNotThisChecksSubject   no `**Case**:` line — counted, no reading
#
# Every case builds its own tree — a registry, two command suites, one `classes` root and a spec — under the
# loop's recorded temporary root (`LOOP_TMP_DIR` in `tools/loop/loop.conf`), so a killed run leaves nothing a
# cleaner cannot find, and a trap removes it on every signal. No git, no build, no network, no tracker.
#
# Usage: check-case-names-selftest.sh [--list] [--case <traceability#name>]
# Exit:  0 every case passed, 1 a case failed, 2 a usage error or an unknown case id.
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
root=$(git -C "$here" rev-parse --show-toplevel 2>/dev/null) || root=$(cd "$here/../.." && pwd)
check="$root/tools/check-case-names.sh"
list_only=0
case_filter=""
while [ $# -gt 0 ]; do
  case "$1" in
    --list) list_only=1; shift ;;
    --case) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { echo "check-case-names-selftest: --case needs a value" >&2; exit 2; }; case_filter="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,25p' "$0"; exit 0 ;;
    *) echo "check-case-names-selftest: unknown argument '$1'" >&2; exit 2 ;;
  esac
done

CASES=(
  'traceability#aNameTheLoopSuiteRunsResolves'
  'traceability#aNameAnotherHarnessRunsResolves'
  'traceability#aNameNoHarnessRunsIsRefused'
  'traceability#aSharedGroupIsDeclared'
  'traceability#aDuplicateFullNameIsRefused'
  'traceability#aRowWhoseListIsAbsentIsRefused'
  'traceability#aRowThatEmitsAnUnclaimedGroupIsRefused'
  'traceability#anExclusionThatHidesANameIsRefused'
  'traceability#theUnclaimedCasesAreCounted'
  'traceability#anUnclaimedCaseNeverRefuses'
  'traceability#aNameInProseIsNotAClaim'
  'traceability#aScenarioWithoutACaseLineIsNotThisChecksSubject'
)

if [ "$list_only" -eq 1 ]; then
  printf '%s\n' "${CASES[@]}"
  exit 0
fi

[ -x "$check" ] || { echo "check-case-names-selftest: $check is not executable" >&2; exit 2; }
command -v jq >/dev/null 2>&1 || { echo "check-case-names-selftest: jq is required" >&2; exit 2; }

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
mkdir -p "$tmp_root" 2>/dev/null || { echo "check-case-names-selftest: cannot create $tmp_root" >&2; exit 2; }
tmp=$(mktemp -d "$tmp_root/check-case-names-selftest.XXXXXX") || {
  echo "check-case-names-selftest: cannot create a scratch directory under $tmp_root" >&2; exit 2
}
cleanup() { rm -rf "$tmp"; }
trap cleanup EXIT
for sig in INT TERM HUP QUIT; do trap 'cleanup; exit 2' "$sig"; done

pass=0
failures=0

# ── the fixture tree ─────────────────────────────────────────────────────────────────────────────────────────
# Two command suites (one prints the loop-shaped names, one a second suite's) and one `classes` root, so each case
# can break the registry in exactly one way without touching the harnesses it reads.
mk_tree() { # <directory>
  mkdir -p "$1/tools" "$1/openspec/specs/fixture" "$1/logic/src/test/kotlin/fixture"
  cat > "$1/tools/fixture-loop.sh" <<'SH'
#!/usr/bin/env bash
printf '%s\n' 'run#theStreakIsTheRecordedCount' 'run#dryRunTouchesNothing' 'report#oneLine'
SH
  cat > "$1/tools/fixture-other.sh" <<'SH'
#!/usr/bin/env bash
printf '%s\n' 'hotspot#aLayerIsDeclared'
SH
  cat > "$1/logic/src/test/kotlin/fixture/FixtureTest.kt" <<'KT'
package fixture

class FixtureTest {
    @Test
    fun oneCase() {
        assertEquals(1, 1)
    }
}
KT
}

# The three rows every case starts from; a case rewrites one of them.
registry() { # <directory> [<extra row json>]
  cat > "$1/tools/case-harnesses.json" <<'JSON'
{
  "harnesses": [
    {
      "id": "fixture-loop",
      "kind": "command",
      "command": ["bash", "tools/fixture-loop.sh"],
      "groups": ["report", "run"]
    },
    {
      "id": "fixture-other",
      "kind": "command",
      "command": ["bash", "tools/fixture-other.sh"],
      "groups": ["hotspot"]
    },
    {
      "id": "fixture-classes",
      "kind": "classes",
      "roots": [{"path": "logic/src/test", "module": "core"}],
      "groups": ["core"]
    }
  ]
}
JSON
}

spec() { # <directory> <case-line> [<second case-line>]
  {
    printf '# fixture Specification\n\n## ADDED Requirements\n\n### Requirement: the fixture does one thing\n\n'
    printf 'The fixture SHALL do one thing.\n\n'
    printf '#### Scenario: the fixture names its case\n\n'
    printf -- '- **WHEN** the fixture runs\n- **THEN** it names its case\n'
    printf -- '%s\n' "$2"
    if [ -n "${3:-}" ]; then
      printf '\n#### Scenario: the fixture names a second case\n\n'
      printf -- '- **WHEN** the fixture runs again\n- **THEN** it names that case too\n'
      printf -- '%s\n' "$3"
    fi
  } > "$1/openspec/specs/fixture/spec.md"
}

run_check() { # <directory> — OUT and RC are set; stdout and stderr are merged so one assertion reads both
  OUT=$(bash "$check" --root "$1" 2>&1)
  RC=$?
}

expect() { # <id> <description> <expected exit> <expected substring> <directory>
  local id="$1" desc="$2" want_rc="$3" want="$4" dir="$5"
  [ -z "$case_filter" ] || [ "$case_filter" = "$id" ] || return 0
  run_check "$dir"
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

# ── the forward direction: a name resolves, or it is refused ─────────────────────────────────────────────────
tree="$tmp/loop-case"
mk_tree "$tree"; registry "$tree"
spec "$tree" '- **Case**: `run#theStreakIsTheRecordedCount`'
expect traceability#aNameTheLoopSuiteRunsResolves \
  "a command row's 'group#case' resolves and the verdict carries no refusal" 0 \
  "CASE-NAMES: ok harnesses=3" "$tree"

tree="$tmp/another-harness"
mk_tree "$tree"; registry "$tree"
spec "$tree" '- **Case**: `hotspot#aLayerIsDeclared`' '- **Case**: `core: FixtureTest#oneCase`'
expect traceability#aNameAnotherHarnessRunsResolves \
  "a second suite's name and a Gradle 'module: Class#method' both resolve, so one suite alone reports no false dead reference" 0 \
  "CASE-NAMES: ok" "$tree"

tree="$tmp/unresolved"
mk_tree "$tree"; registry "$tree"
spec "$tree" '- **Case**: `run#noSuiteRunsThisCase`'
run_check "$tree"
if [ -z "$case_filter" ] || [ "$case_filter" = 'traceability#aNameNoHarnessRunsIsRefused' ]; then
  problems=""
  [ "$RC" = 1 ] || problems="$problems\n        expected exit 1, got $RC"
  printf '%s' "$OUT" | grep -qF 'run#noSuiteRunsThisCase' || problems="$problems\n        the refusal does not name the case"
  printf '%s' "$OUT" | grep -qF 'openspec/specs/fixture/spec.md:' || problems="$problems\n        the refusal does not name the spec file and its line"
  printf '%s' "$OUT" | grep -qF 'the fixture names its case' || problems="$problems\n        the refusal does not name the scenario"
  printf '%s' "$OUT" | grep -qF 'CASE-NAMES: refused' || problems="$problems\n        the verdict line is not the refusal one"
  if [ -z "$problems" ]; then
    echo "PASS  traceability#aNameNoHarnessRunsIsRefused — an unresolved name is refused with the file, the line, the scenario and the name (exit $RC)"
    pass=$((pass + 1))
  else
    printf 'FAIL  traceability#aNameNoHarnessRunsIsRefused — the refusal is incomplete:%b\n' "$problems"
    failures=$((failures + 1))
  fi
fi

# ── the declared set: a shared group, a duplicate, an absent list, an unclaimed group, an exclusion ──────────
tree="$tmp/shared-group"
mk_tree "$tree"
cat > "$tree/tools/case-harnesses.json" <<'JSON'
{
  "harnesses": [
    {
      "id": "fixture-loop",
      "kind": "command",
      "command": ["bash", "tools/fixture-loop.sh"],
      "groups": ["report", "run"]
    },
    {
      "id": "fixture-other",
      "kind": "command",
      "command": ["bash", "tools/fixture-other.sh"],
      "groups": ["hotspot", "run"]
    }
  ]
}
JSON
spec "$tree" '- **Case**: `run#theStreakIsTheRecordedCount`' '- **Case**: `run#dryRunTouchesNothing`'
expect traceability#aSharedGroupIsDeclared \
  "two rows claim the group 'run' and different full names, so the group is a declared fact and refuses nothing" 0 \
  "CASE-NAMES: ok" "$tree"

tree="$tmp/duplicate"
mk_tree "$tree"
cat > "$tree/tools/fixture-other.sh" <<'SH'
#!/usr/bin/env bash
printf '%s\n' 'run#theStreakIsTheRecordedCount'
SH
registry "$tree"
spec "$tree" '- **Case**: `run#theStreakIsTheRecordedCount`'
run_check "$tree"
if [ -z "$case_filter" ] || [ "$case_filter" = 'traceability#aDuplicateFullNameIsRefused' ]; then
  problems=""
  [ "$RC" = 1 ] || problems="$problems\n        expected exit 1, got $RC"
  printf '%s' "$OUT" | grep -qF 'duplicates' || problems="$problems\n        the verdict does not count a duplicate"
  printf '%s' "$OUT" | grep -qF 'fixture-loop' || problems="$problems\n        the refusal does not name the first row"
  printf '%s' "$OUT" | grep -qF 'fixture-other' || problems="$problems\n        the refusal does not name the second row"
  if [ -z "$problems" ]; then
    echo "PASS  traceability#aDuplicateFullNameIsRefused — one full name in two rows is refused, naming both (exit $RC)"
    pass=$((pass + 1))
  else
    printf 'FAIL  traceability#aDuplicateFullNameIsRefused — the refusal is incomplete:%b\n' "$problems"
    failures=$((failures + 1))
  fi
fi

tree="$tmp/absent-list"
mk_tree "$tree"
cat > "$tree/tools/case-harnesses.json" <<'JSON'
{
  "harnesses": [
    {
      "id": "fixture-loop",
      "kind": "file",
      "path": "tools/fixture-absent.sh",
      "pattern": "run#[A-Za-z0-9_]+",
      "groups": ["run"]
    }
  ]
}
JSON
spec "$tree" '- **Case**: `run#theStreakIsTheRecordedCount`'
expect traceability#aRowWhoseListIsAbsentIsRefused \
  "a row whose case list is not in the tree is refused by name, not read as an empty harness" 1 \
  'rows=1' "$tree"

tree="$tmp/unclaimed-group"
mk_tree "$tree"
cat > "$tree/tools/case-harnesses.json" <<'JSON'
{
  "harnesses": [
    {
      "id": "fixture-loop",
      "kind": "command",
      "command": ["bash", "tools/fixture-loop.sh"],
      "groups": ["report"]
    }
  ]
}
JSON
spec "$tree" '- **Case**: `report#oneLine`'
expect traceability#aRowThatEmitsAnUnclaimedGroupIsRefused \
  "a harness that prints a name in a group it did not claim is refused rather than trusted" 1 \
  'undeclared=2' "$tree"

tree="$tmp/exclusion"
mk_tree "$tree"
cat > "$tree/tools/case-harnesses.json" <<'JSON'
{
  "harnesses": [
    {
      "id": "fixture-loop",
      "kind": "command",
      "command": ["bash", "tools/fixture-loop.sh"],
      "groups": ["report", "run"],
      "exclude": "^run#"
    }
  ]
}
JSON
spec "$tree" '- **Case**: `report#oneLine`'
expect traceability#anExclusionThatHidesANameIsRefused \
  "an excluded name no other row declares is a hiding place, and is refused as one" 1 \
  'hinges=' "$tree"

# ── the backward direction: a count, never a refusal ─────────────────────────────────────────────────────────
tree="$tmp/unclaimed-count"
mk_tree "$tree"; registry "$tree"
spec "$tree" '- **Case**: `run#theStreakIsTheRecordedCount`'
run_check "$tree"
if [ -z "$case_filter" ] || [ "$case_filter" = 'traceability#theUnclaimedCasesAreCounted' ]; then
  problems=""
  [ "$RC" = 0 ] || problems="$problems\n        expected exit 0, got $RC"
  printf '%s' "$OUT" | grep -qE 'unclaimed=[1-9]' || problems="$problems\n        the verdict carries no unclaimed count"
  json=$(bash "$check" --root "$tree" --json 2>/dev/null)
  printf '%s' "$json" | jq -e '.unclaimed | type == "array" and length > 0' >/dev/null 2>&1 \
    || problems="$problems\n        --json carries no unclaimed list"
  if [ -z "$problems" ]; then
    echo "PASS  traceability#theUnclaimedCasesAreCounted — the verdict carries the count and --json the list, exit $RC"
    pass=$((pass + 1))
  else
    printf 'FAIL  traceability#theUnclaimedCasesAreCounted — the count is not reported:%b\n' "$problems"
    failures=$((failures + 1))
  fi
fi

tree="$tmp/whole-harness-unclaimed"
mk_tree "$tree"
cat > "$tree/tools/case-harnesses.json" <<'JSON'
{
  "harnesses": [
    {
      "id": "fixture-other",
      "kind": "command",
      "command": ["bash", "tools/fixture-other.sh"],
      "groups": ["hotspot"]
    }
  ]
}
JSON
spec "$tree" '- **Case**: none — the fixture names no case, so the only harness is unclaimed'
expect traceability#anUnclaimedCaseNeverRefuses \
  "a harness every one of whose cases is unclaimed still exits 0 with the count printed" 0 \
  'unclaimed=1' "$tree"

# ── the check reads names, never meaning ────────────────────────────────────────────────────────────────────
tree="$tmp/prose"
mk_tree "$tree"; registry "$tree"
spec "$tree" '- **Case**: `run#theStreakIsTheRecordedCount`'
cat >> "$tree/openspec/specs/fixture/spec.md" <<'MD'

The section's body mentions `run#noSuiteRunsThisCase` and `hotspot#notDeclaredAnywhere` as prose, which is not a
claim about a case: only a `**Case**:` line is.
MD
expect traceability#aNameInProseIsNotAClaim \
  "a case-shaped name inside prose is ignored, and only a '**Case**:' line is read" 0 \
  "CASE-NAMES: ok" "$tree"

tree="$tmp/no-case-line"
mk_tree "$tree"; registry "$tree"
cat > "$tree/openspec/specs/fixture/spec.md" <<'MD'
# fixture Specification

## ADDED Requirements

### Requirement: the fixture leaves one scenario unclaimed

The fixture SHALL do one thing.

#### Scenario: a scenario the readiness checker owns

- **WHEN** the fixture runs with no case line
- **THEN** this check says nothing about it
MD
run_check "$tree"
if [ -z "$case_filter" ] || [ "$case_filter" = 'traceability#aScenarioWithoutACaseLineIsNotThisChecksSubject' ]; then
  problems=""
  [ "$RC" = 0 ] || problems="$problems\n        expected exit 0, got $RC"
  printf '%s' "$OUT" | grep -qF 'lines=0' || problems="$problems\n        the scenario with no case line was read as a line"
  printf '%s' "$OUT" | grep -qE 'CASE-NAMES: ok' || problems="$problems\n        the verdict is not the ok one"
  if [ -z "$problems" ]; then
    echo "PASS  traceability#aScenarioWithoutACaseLineIsNotThisChecksSubject — no case line is read at all (lines=0), and no refusal is raised (exit $RC)"
    pass=$((pass + 1))
  else
    printf 'FAIL  traceability#aScenarioWithoutACaseLineIsNotThisChecksSubject — the line is not counted:%b\n' "$problems"
    failures=$((failures + 1))
  fi
fi

if [ -n "$case_filter" ] && [ "$pass" -eq 0 ] && [ "$failures" -eq 0 ]; then
  echo "check-case-names-selftest: no case with the id '$case_filter'" >&2
  exit 2
fi

if [ "$failures" -ne 0 ]; then
  echo "check-case-names-selftest: $failures of $((pass + failures)) cases failed"
  exit 1
fi
echo "check-case-names-selftest: all $pass cases passed (throwaway trees under the loop's temporary root; no git, no build, no network)"
exit 0