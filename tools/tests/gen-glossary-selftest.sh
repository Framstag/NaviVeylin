#!/usr/bin/env bash
# Self-test for tools/gen-glossary.sh and its measurement, tools/sensors/metrics/vocabulary-drift.sh.
#
# Every case builds a throwaway tree under a temp directory and drives the real script against it with
# `--root`, so nothing in the repository is read or written except the two scripts under test — the projection
# cases never touch the tree the repository carries, which is the reason a case can hand-edit a generated block.
#
# The cases are the ones `vocabulary-projection` declares: the two projections (§The definition reaches the
# owning type as KDoc, §A declared concept with no file is emitted as a skeleton), the check mode that fails
# instead of writing, and the drift measurement's identity, first run and escalation floor.
#
# The escalation case reads the registry rather than running the engine: the recurrence behaviour is
# `run.sh`'s and `run#escalatesPastKRecurrences` already drives it, so what this suite asserts is that the
# `vocabulary-drift` row inherits that floor instead of declaring a bound of its own.
#
# Usage: gen-glossary-selftest.sh [--case <id>] [--list]
# Exit:  0 every case passed, 1 a case failed, 2 the environment is unusable.
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
root=$(git -C "$here" rev-parse --show-toplevel 2>/dev/null) || root=$(cd "$here/../.." && pwd)
gen="$root/tools/gen-glossary.sh"
metric="$root/tools/sensors/metrics/vocabulary-drift.sh"
registry="$root/tools/sensors/sensors.json"
case_filter=""
list_only=0
while [ $# -gt 0 ]; do
  case "$1" in
    --case) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { echo "gen-glossary-selftest: $1 needs a value" >&2; exit 2; }; case_filter="${2:-}"; shift 2 ;;
    --list) list_only=1; shift ;;
    *) echo "gen-glossary-selftest: unknown argument '$1'" >&2; exit 2 ;;
  esac
done
[ -f "$gen" ] || { echo "gen-glossary-selftest: no $gen" >&2; exit 2; }
[ -f "$metric" ] || { echo "gen-glossary-selftest: no $metric" >&2; exit 2; }
command -v jq >/dev/null 2>&1 || { echo "gen-glossary-selftest: jq is required" >&2; exit 2; }

tmp=$(mktemp -d "${TMPDIR:-/tmp}/gen-glossary-selftest.XXXXXX") || exit 2
trap 'rm -rf "$tmp"' EXIT

cases="
projection#theDefinitionReachesTheType
projection#anExistingKdocIsNotRewritten
projection#aSkeletonIsEmittedForAMissingConcept
projection#anExistingSkeletonIsNotOverwritten
projection#aModifiedProjectionFailsTheCheck
projection#aCurrentTreeWritesNothing
projection#fiftyTermsAreOneCondition
projection#theFirstRunReports
projection#aThirdRecurrenceGoesToAPerson
"

if [ "$list_only" -eq 1 ]; then
  printf '%s\n' "$cases" | grep -v '^$'
  exit 0
fi

fail() { printf '  %s\n' "$*" >&2; return 1; }

assert_eq() { [ "$1" = "$2" ] || fail "expected [$2], got [$1] — $3"; }
assert_contains() {
  case "$1" in *"$2"*) return 0 ;; esac
  fail "[$1] does not carry [$2] — $3"
}
assert_absent() {
  case "$1" in *"$2"*) fail "[$1] carries [$2] — $3" ;; esac
  return 0
}
assert_file() { [ -f "$1" ] || fail "no file at $1 — $2"; }

# The declaration a fixture carries: two kinds, the projection switches, and one row per case.
write_index() { # $1 dir
  mkdir -p "$1/tools/glossary"
  cat > "$1/tools/glossary/index.json" <<'FIXTURE'
{
  "profile": 1,
  "kinds": ["entity", "value"],
  "roleOfKind": { "entity": "type", "value": "type" },
  "roles": ["type", "property"],
  "booleanPrefixes": ["is", "has", "can"],
  "vacuous": [],
  "projections": {
    "kdoc": true,
    "skeleton": true,
    "skeletonRoot": "app/src/main/java",
    "skeletonPackage": "com.example.concept",
    "skeletonSuffix": ""
  },
  "census": { "roots": ["app/src/main"], "extensions": ["kt"], "splitRatio": 10, "stoplist": [] }
}
FIXTURE
}

write_rows() { # $1 dir, $2 file name, $3 the rows array as JSON
  printf '{"profile": 1, "rows": %s}\n' "$3" > "$1/tools/glossary/$2"
}

# A tree holding one type with no KDoc above it, and the one row that declares it.
fixture_with_a_type() { # $1 dir
  write_index "$1"
  mkdir -p "$1/app/src/main/java/com/example/map"
  printf 'package com.example.map\n\nclass Basemap(val id: String)\n' > "$1/app/src/main/java/com/example/map/Basemap.kt"
  write_rows "$1" map.json '[{"key":"basemap#entity","definition":"The map image the camera looks at.","surfaces":{"type":"Basemap"}}]'
}

# --------------------------------------------------------------------------- the cases

case_theDefinitionReachesTheType() {
  local dir out file
  dir="$tmp/kdoc"
  fixture_with_a_type "$dir"
  file="$dir/app/src/main/java/com/example/map/Basemap.kt"
  out=$(bash "$gen" --root "$dir" 2>&1) || fail "the generator refused a tree it must project: $out"
  assert_contains "$(cat "$file")" "The map image the camera looks at." "the type's KDoc holds the definition text verbatim"
  assert_contains "$(cat "$file")" "declared by basemap#entity" "the block names the row it came from"
  assert_contains "$(cat "$file")" "class Basemap(val id: String)" "the declaration itself is left as it was"
  assert_contains "$out" "projected=1" "the run reports the one projection it wrote"
}

case_anExistingKdocIsNotRewritten() {
  local dir out before file
  dir="$tmp/written"
  write_index "$dir"
  mkdir -p "$dir/app/src/main/java/com/example/map"
  file="$dir/app/src/main/java/com/example/map/Basemap.kt"
  printf 'package com.example.map\n\n/**\n * Hand-written: the image of the map, as a person meant it.\n */\nclass Basemap(val id: String)\n' > "$file"
  write_rows "$dir" map.json '[{"key":"basemap#entity","definition":"The map image the camera looks at.","surfaces":{"type":"Basemap"}}]'
  before=$(cat "$file")
  out=$(bash "$gen" --root "$dir" 2>&1) || fail "the generator failed on a tree holding a hand-written KDoc: $out"
  assert_eq "$before" "$(cat "$file")" "the hand-written block is unchanged, byte for byte"
  assert_contains "$out" "not projected Basemap" "the type is reported as not projected"
  assert_absent "$out" "projected=1" "nothing was written"
}

case_aSkeletonIsEmittedForAMissingConcept() {
  local dir out file
  dir="$tmp/skeleton"
  write_index "$dir"
  mkdir -p "$dir/app/src/main/java/com/example"
  write_rows "$dir" concept.json '[{"key":"theme#entity","definition":"The one set of colours a surface draws its chrome with.","surfaces":{"type":"Theme"}}]'
  out=$(bash "$gen" --root "$dir" 2>&1) || fail "the generator refused to emit a skeleton: $out"
  file="$dir/app/src/main/java/com/example/concept/Theme.kt"
  assert_file "$file" "a concept row with no declaring file emits its type"
  assert_contains "$(cat "$file")" "generated by gen-glossary.sh" "the file carries the generated marker"
  assert_contains "$(cat "$file")" "The one set of colours a surface draws its chrome with." "the KDoc holds the definition"
  assert_contains "$(cat "$file")" "package com.example.concept" "the skeleton sits in the declared package"
  assert_contains "$(cat "$file")" "class Theme" "the skeleton declares the type"
  assert_absent "$(cat "$file")" "val " "the skeleton declares no members"
  assert_contains "$out" "skeletons=1" "the run reports the one skeleton it emitted"
}

case_anExistingSkeletonIsNotOverwritten() {
  local dir out first second file
  dir="$tmp/keep-skeleton"
  write_index "$dir"
  mkdir -p "$dir/app/src/main/java/com/example"
  write_rows "$dir" concept.json '[{"key":"theme#entity","definition":"The one set of colours a surface draws its chrome with.","surfaces":{"type":"Theme"}}]'
  bash "$gen" --root "$dir" >/dev/null 2>&1 || fail "the first run did not emit the skeleton"
  file="$dir/app/src/main/java/com/example/concept/Theme.kt"
  first=$(cat "$file")
  out=$(bash "$gen" --root "$dir" 2>&1) || fail "the second run failed: $out"
  second=$(cat "$file")
  assert_eq "$first" "$second" "an existing file is not overwritten by a later run"
  assert_contains "$out" "not projected Theme" "the existing file is reported rather than rewritten"
  assert_eq "1" "$(grep -c 'declared by theme#entity' "$file")" "the file carries one block, not a second one"
}

case_aModifiedProjectionFailsTheCheck() {
  local dir out status before
  dir="$tmp/modified"
  fixture_with_a_type "$dir"
  bash "$gen" --root "$dir" >/dev/null 2>&1 || fail "the projection was not written"
  # a hand edit of the generated block, in place of the declaration's sentence
  sed -i 's/The map image the camera looks at\./The map image somebody edited by hand./' \
    "$dir/app/src/main/java/com/example/map/Basemap.kt"
  before=$(cat "$dir/app/src/main/java/com/example/map/Basemap.kt")
  out=$(bash "$gen" --root "$dir" --check 2>&1)
  status=$?
  assert_eq "1" "$status" "the check mode exits non-zero on a hand-edited projection"
  assert_contains "$out" "modified Basemap" "the finding names the type"
  assert_contains "$out" "basemap#entity" "the finding names the row"
  assert_contains "$out" "nothing was written" "the check mode reports that it wrote nothing"
  assert_eq "$before" "$(cat "$dir/app/src/main/java/com/example/map/Basemap.kt")" "the edited file is untouched by the check"
}

case_aCurrentTreeWritesNothing() {
  local dir out dir_before dir_after
  dir="$tmp/current"
  fixture_with_a_type "$dir"
  mkdir -p "$dir/app/src/main/java/com/example"
  write_rows "$dir" concept.json '[{"key":"theme#entity","definition":"The one set of colours a surface draws its chrome with.","surfaces":{"type":"Theme"}}]'
  bash "$gen" --root "$dir" >/dev/null 2>&1 || fail "the first run did not project the tree"
  assert_file "$dir/tools/glossary/GLOSSARY.md" "the run writes the generated reading copy"
  dir_before=$(find "$dir" -type f -name '*.kt' -exec md5sum {} + | sort)
  md_before=$(cat "$dir/tools/glossary/GLOSSARY.md")
  out=$(bash "$gen" --root "$dir" --check 2>&1) || fail "the check mode refused a tree whose projections are current: $out"
  assert_contains "$out" "green" "a current tree is reported green"
  dir_after=$(find "$dir" -type f -name '*.kt' -exec md5sum {} + | sort)
  assert_eq "$dir_before" "$dir_after" "the check mode writes no file"
  assert_eq "$md_before" "$(cat "$dir/tools/glossary/GLOSSARY.md")" "the reading copy is the declaration's and is not rewritten either"
}

# The metric's own tree: a check that prints fifty drift findings and nothing else, so the metric's grouping is
# measured rather than the check's judgement.
fixture_with_fifty_terms() { # $1 dir
  mkdir -p "$1/tools"
  cat > "$1/tools/check-glossary.sh" <<'FIXTURE'
#!/usr/bin/env bash
i=1
while [ "$i" -le 50 ]; do
  printf '{"refusal":"drift","key":"term%s#entity","file":"a.kt","line":1}\n' "$i"
  i=$((i + 1))
done
FIXTURE
  chmod +x "$1/tools/check-glossary.sh"
}

case_fiftyTermsAreOneCondition() {
  local dir out lines
  dir="$tmp/fifty"
  fixture_with_fifty_terms "$dir"
  out=$(SENSORS_ROOT="$dir" SENSORS_THRESHOLD=0 bash "$metric" 2>&1) || fail "the measurement failed on a fixture it can read"
  lines=$(printf '%s\n' "$out" | grep -c '"fingerprint"' || true)
  assert_eq "1" "$lines" "fifty terms of one refusal class are one condition, not fifty"
  assert_contains "$out" '"refusal":"drift"' "the condition's identity is the refusal"
  assert_contains "$out" '"measurement":"50"' "the condition's measurement is the class's count"
  assert_contains "$out" "term1#entity" "the claim lists the first term"
  assert_contains "$out" "term50#entity" "the claim lists the last term"
}

case_theFirstRunReports() {
  local dir out status
  dir="$tmp/first-run"
  fixture_with_fifty_terms "$dir"
  out=$(SENSORS_ROOT="$dir" SENSORS_THRESHOLD=0 SENSORS_NO_RECORD=1 bash "$metric" 2>&1)
  status=$?
  assert_eq "0" "$status" "a first run succeeds"
  assert_contains "$out" "would file drift: 50 term(s)" "the first run prints the conditions it would have filed"
  assert_contains "$out" "nothing is written on a first run" "the first run states that it wrote nothing"
  assert_absent "$out" '"fingerprint"' "the first run emits no finding"
}

case_aThirdRecurrenceGoesToAPerson() {
  local row recurrence
  row=$(jq -c '.rows[] | select(.id == "vocabulary-drift")' "$registry")
  [ -n "$row" ] || fail "the registry declares no vocabulary-drift row"
  assert_eq "2" "$(jq -r '.maxFilings' <<< "$row")" "one run files at most two conditions"
  assert_eq "refusal" "$(jq -r '.fingerprint.fields | join(",")' <<< "$row")" "the fingerprint's one field is the refusal"
  assert_eq "2" "$(jq -r '.consecutive' <<< "$row")" "the row files once the refusal stands over two runs"
  assert_eq "null" "$(jq -c '.escalation // null' <<< "$row")" "the row declares no recurrence bound of its own"
  recurrence=$(jq -r '.escalation.maxRecurrences' "$registry")
  assert_eq "3" "$recurrence" "past the registry's three recurrences the class is handed to a person"
  assert_contains "$(jq -r '.escalation.labels | join(",")' "$registry")" "agent/needs-decision" \
    "the escalation carries no eligibility for automated work"
}

# --------------------------------------------------------------------------- the runner

if [ -n "$case_filter" ]; then
  if ! printf '%s\n' "$cases" | grep -qxF "$case_filter"; then
    echo "gen-glossary-selftest: no such case: $case_filter" >&2
    exit 2
  fi
  if ( "case_${case_filter#*#}" ); then
    printf 'ok   %s\n' "$case_filter"
    exit 0
  fi
  printf 'FAIL %s\n' "$case_filter"
  exit 1
fi

total=0
failed=0
for name in $cases; do
  [ -n "$name" ] || continue
  total=$((total + 1))
  if ( "case_${name#*#}" ); then
    printf 'ok   %s\n' "$name"
  else
    printf 'FAIL %s\n' "$name"
    failed=$((failed + 1))
  fi
done
if [ "$total" -eq 0 ]; then
  echo "gen-glossary-selftest: no cases" >/dev/null
  exit 2
fi
if [ "$failed" -gt 0 ]; then
  echo "gen-glossary-selftest: $failed of $total cases failed"
  exit 1
fi
echo "gen-glossary-selftest: $total cases passed (offline, no Gradle, no device)"
