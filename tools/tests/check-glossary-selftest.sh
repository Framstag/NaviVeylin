#!/usr/bin/env bash
# Self-test for tools/check-glossary.sh. Every case runs against a throwaway fixture under a temp directory:
# the check reads the fixture's declaration, structure declaration, spec and guideline, and the engine keeps
# reading its own census program beside this suite's parent — so nothing in the repository is read or written.
#
# The fixture is green to begin with (one declared term, one source, one case, one rule), and each case breaks
# exactly one thing and states the line the check must print. The case ids are the ones the change's two
# capability deltas declare, so a scenario and the case named after it are one thing.
#
# Usage: check-glossary-selftest.sh [--case <id>] [--list]
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
root=$(git -C "$here" rev-parse --show-toplevel 2>/dev/null) || root=$(cd "$here/../.." && pwd)
check="$root/tools/check-glossary.sh"
case_filter=""
list_only=0
while [ $# -gt 0 ]; do
  case "$1" in
    --case) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { echo "check-glossary-selftest: $1 needs a value" >&2; exit 2; }; case_filter="${2:-}"; shift 2 ;;
    --list) list_only=1; shift ;;
    *) echo "check-glossary-selftest: unknown argument '$1'" >&2; exit 2 ;;
  esac
done
[ -x "$check" ] || { echo "check-glossary-selftest: $check is not executable" >&2; exit 2; }
command -v jq >/dev/null 2>&1 || { echo "check-glossary-selftest: jq is required" >&2; exit 2; }

tmp=$(mktemp -d "${TMPDIR:-/tmp}/glossary-selftest.XXXXXX") || exit 2
trap 'rm -rf "$tmp"' EXIT

pass=0
failures=0

write_index() { # $1 dir
  mkdir -p "$1/tools/glossary"
  cat > "$1/tools/glossary/index.json" <<'FIXTURE'
{
  "profile": 1,
  "kinds": ["entity", "value", "operation", "attribute", "boolean", "event", "constant"],
  "roleOfKind": { "entity": "type", "value": "type", "operation": "operation", "attribute": "property", "boolean": "property", "event": "event", "constant": "constant" },
  "roles": ["type", "property", "operation", "event", "constant"],
  "booleanPrefixes": ["is", "has", "can"],
  "vacuous": [
    { "suffix": "Manager", "reason": "names who calls a thing, not what it does", "refusedKinds": ["concept", "feature"] },
    { "suffix": "Provider", "reason": "a provider is a port role", "refusedKinds": ["concept", "feature"] }
  ],
  "projections": { "kdoc": true, "skeleton": true },
  "census": { "roots": ["app/src/main"], "extensions": ["kt"], "splitRatio": 10, "stoplist": ["val", "var", "class", "fun"] }
}
FIXTURE
}

write_structure() { # $1 dir
  mkdir -p "$1/architecture"
  printf '%s\n' \
    '# columns: module, package, kind, feature, may_import' \
    'app	com.example.map	feature	map	concept,port' \
    'app	com.example.route	feature	route	concept,port' \
    'app	com.example.data	platform	-	concept,port' \
    'core	com.example.port	port	-	concept' \
    'core	com.example	concept	-	-' > "$1/architecture/structure.tsv"
}

case_source() { # $1 dir, $2 relative path, $3 body
  mkdir -p "$(dirname "$1/$2")"
  printf '%s\n' "$3" > "$1/$2"
}

write_rows() { # $1 dir, $2 feature file stem, $3 rows JSON array
  mkdir -p "$1/tools/glossary"
  printf '{"profile": 1, "rows": %s}\n' "$3" > "$1/tools/glossary/$2.json"
}

base_row='{"key":"basemap#entity","definition":"One installed set of map data.","not":"not a stylesheet","surfaces":{"type":"Basemap"},"anchor":["case: map#aBasemapIsDrawn"]}'

mk_fixture() { # $1 dir
  write_index "$1"
  write_structure "$1"
  write_rows "$1" map "[$base_row]"
  case_source "$1" app/src/main/java/com/example/map/Basemap.kt 'package com.example.map

class Basemap
'
  mkdir -p "$1/openspec/specs/map" "$1/guidelines"
  printf '%s\n' '- **Case**: `map#aBasemapIsDrawn`' > "$1/openspec/specs/map/spec.md"
  printf '%s\n' '- **Rule: a basemap is drawn once** The rule text.' > "$1/guidelines/Map.md"
}

declare -a case_ids=()
expect() { # $1 case id, $2 expected exit, $3 dir, $4 required substrings separated by '|', $5 description
  case_ids+=("$1")
  [ -z "$case_filter" ] || [ "$case_filter" = "$1" ] || [ "$case_filter" = "${1#glossary#}" ] || return 0
  local out got ok want
  out=$("$check" --root "$3" 2>&1)
  got=$?
  ok=1
  [ "$got" = "$2" ] || ok=0
  local IFS='|'
  for want in $4; do
    [ -z "$want" ] && continue
    printf '%s\n' "$out" | grep -Fq -- "$want" || { ok=0; echo "    missing: $want" >&2; }
  done
  unset IFS
  if [ "$ok" = 1 ]; then
    pass=$((pass + 1))
    printf 'ok   %s — %s\n' "$1" "$5"
  else
    failures=$((failures + 1))
    printf 'FAIL %s — %s (expected exit %s, got %s)\n' "$1" "$5" "$2" "$got"
    printf '%s\n' "$out" | sed 's/^/    /'
  fi
}

# --- a row is one term in one grammatical kind ------------------------------------------------------------
d="$tmp/polysemous"; mk_fixture "$d"
write_rows "$d" map '[{"key":"map/session#entity","definition":"A map view kept across a screen change.","not":"not a route/session#entity","surfaces":{"type":"MapSession"},"anchor":["case: map#aBasemapIsDrawn"]}]'
write_rows "$d" route '[{"key":"route/session#entity","definition":"A route plan kept while a screen is redrawn.","not":"not a map/session#entity","surfaces":{"type":"RouteSession"},"anchor":["case: map#aBasemapIsDrawn"]}]'
case_source "$d" app/src/main/java/com/example/map/MapSession.kt 'package com.example.map

class MapSession
'
case_source "$d" app/src/main/java/com/example/map/RouteSession.kt 'package com.example.map

class RouteSession
'
expect 'glossary#aPolysemousWordCarriesItsScope' 0 "$d" 'rows=2 ' 'two rows of one word, each scoped and excluding the other, are accepted'

d="$tmp/duplicate"; mk_fixture "$d"
write_rows "$d" map "[$base_row,$base_row]"
expect 'glossary#aDuplicateKeyIsRefused' 1 "$d" 'bad-name|basemap#entity' 'a second row carrying the same key fails and names the key'

# --- every row carries an anchor ---------------------------------------------------------------------------
d="$tmp/unanchored"; mk_fixture "$d"
write_rows "$d" map '[{"key":"basemap#entity","definition":"One installed set of map data.","not":"not a stylesheet","surfaces":{"type":"Basemap"},"anchor":[]}]'
expect 'glossary#anUnanchoredRowIsRefused' 1 "$d" 'unanchored|basemap#entity|openspec/specs' 'a row with no anchor fails, naming what was looked for'

d="$tmp/anchor-dangling"; mk_fixture "$d"
write_rows "$d" map '[{"key":"basemap#entity","definition":"One installed set of map data.","not":"not a stylesheet","surfaces":{"type":"Basemap"},"anchor":["case: map#aCaseNoSpecDeclares"]}]'
expect 'glossary#anAnchorThatDoesNotResolveIsRefused' 1 "$d" 'unanchored|aCaseNoSpecDeclares' 'an anchor naming nothing fails with the anchor text'

d="$tmp/anchor-rule"; mk_fixture "$d"
write_rows "$d" map '[{"key":"basemap#entity","definition":"One installed set of map data.","not":"not a stylesheet","surfaces":{"type":"Basemap"},"anchor":["rule: a basemap is drawn once"]}]'
expect 'glossary#anAnchorThatDoesNotResolveIsRefused' 0 "$d" 'rows=1 ' 'a named rule is an anchor, and it resolves'

# --- a definition excludes its neighbours ------------------------------------------------------------------
d="$tmp/lopsided"; mk_fixture "$d"
write_rows "$d" map '[{"key":"basemap#entity","definition":"One installed set of map data.","not":"not tile#entity","surfaces":{"type":"Basemap"},"anchor":["case: map#aBasemapIsDrawn"]},{"key":"tile#entity","definition":"One square of rendered map.","not":"not a frame","surfaces":{"type":"Tile"},"anchor":["case: map#aBasemapIsDrawn"]}]'
case_source "$d" app/src/main/java/com/example/map/Tile.kt 'package com.example.map

class Tile
'
expect 'glossary#aOneSidedExclusionIsRefused' 1 "$d" 'lopsided-exclusion|tile#entity' 'an exclusion one row declares and the other does not is refused'

d="$tmp/twin"; mk_fixture "$d"
write_rows "$d" map '[{"key":"basemap#entity","definition":"One installed set of map data.","not":"not a tree","surfaces":{"type":"Basemap"},"anchor":["case: map#aBasemapIsDrawn"]},{"key":"tile#entity","definition":"One square of rendered map.","not":"not a tree","surfaces":{"type":"Tile"},"anchor":["case: map#aBasemapIsDrawn"]}]'
case_source "$d" app/src/main/java/com/example/map/Tile.kt 'package com.example.map

class Tile
'
expect 'glossary#twoRowsWithoutAnExclusionAreRefused' 1 "$d" 'twin-term' 'two rows of one kind that exclude the same nothing are refused'

# --- the declaration settles a split spelling --------------------------------------------------------------
split_source='package com.example.map

class Basemap
class Basemap
class Basemap
class Basemap
class Basemap
class Basemap
class Basemap
class Basemap
class Basemap
class Basemap
class Basemap
class BaseMap
'


d="$tmp/confirmed"; mk_fixture "$d"
case_source "$d" app/src/main/java/com/example/map/Basemap.kt "$split_source"
write_rows "$d" map '[{"key":"basemap#entity","definition":"One installed set of map data.","not":"not a stylesheet","surfaces":{"type":"Basemap"},"canonical":"Basemap","anchor":["case: map#aBasemapIsDrawn"]}]'
expect 'glossary#aDeclarationTheCodeConfirmsPasses' 0 "$d" 'rows=1 ' 'a declared spelling the code agrees with passes'

d="$tmp/overruled"; mk_fixture "$d"
case_source "$d" app/src/main/java/com/example/map/Basemap.kt "$split_source"
write_rows "$d" map '[{"key":"basemap#entity","definition":"One installed set of map data.","not":"not a stylesheet","surfaces":{"type":"BaseMap"},"canonical":"BaseMap","anchor":["case: map#aBasemapIsDrawn"]}]'
expect 'glossary#anOverruledDeclarationIsRefused' 1 "$d" 'overruled|declared=BaseMap' 'a declared spelling a ten-to-one majority contradicts is refused, both counts named'

d="$tmp/split-undecided"; mk_fixture "$d"
case_source "$d" app/src/main/java/com/example/map/Speed.kt 'package com.example.map

val speedKmH: Int = 1
val speedKmH2: Int = speedKmH
val speedKmh: Int = 2
val speedKmh2: Int = speedKmh
class Speed
'
write_rows "$d" map '[{"key":"speed#attribute","definition":"How fast the position moves.","not":"not a clock","surfaces":{"property":"speedKmH"},"unit":"km/h","anchor":["case: map#aBasemapIsDrawn"]}]'
expect 'glossary#aSplitWithNoDeclarationIsRefused' 1 "$d" 'undecided|speedKmH|speedKmh' 'a split spelling with no declared form is refused, both counts named'

d="$tmp/split-declared"; mk_fixture "$d"
case_source "$d" app/src/main/java/com/example/map/Speed.kt 'package com.example.map

val speedKmH: Int = 1
val speedKmH2: Int = speedKmH
val speedKmh: Int = 2
val speedKmh2: Int = speedKmh
class Speed
'
write_rows "$d" map '[{"key":"speed#attribute","definition":"How fast the position moves.","not":"not a clock","surfaces":{"property":"speedKmH"},"unit":"km/h","canonical":"speedKmH","anchor":["case: map#aBasemapIsDrawn"]}]'
expect 'glossary#aDeclaredSplitPasses' 0 "$d" 'drift|speedKmh' 'a declared split passes and the other spelling is reported as drift'

# --- surfaces are declared per grammatical role ------------------------------------------------------------
d="$tmp/forbidden"; mk_fixture "$d"
case_source "$d" app/src/main/java/com/example/map/Basemap.kt 'package com.example.map

class BaseMap
class Basemap
'
write_rows "$d" map '[{"key":"basemap#entity","definition":"One installed set of map data.","not":"not a stylesheet","surfaces":{"type":"Basemap"},"forbidden":{"type":["BaseMap"]},"anchor":["case: map#aBasemapIsDrawn"]}]'
expect 'glossary#aForbiddenFormInItsRoleIsDrift' 1 "$d" 'drift|basemap#entity|Basemap.kt:3' 'a forbidden form declared in the type role fails, naming the file and line'

d="$tmp/phantom"; mk_fixture "$d"
write_rows "$d" map '[{"key":"basemap#entity","definition":"One installed set of map data.","not":"not a stylesheet","surfaces":{"type":"Basemap","property":"absentScale"},"anchor":["case: map#aBasemapIsDrawn"]}]'
expect 'glossary#aSurfaceTheCensusNeverObservesIsRefused' 1 "$d" 'phantom|basemap#entity|absentScale' 'a declared surface the census never observes is refused, naming the surface'

d="$tmp/kind-mismatch"; mk_fixture "$d"
write_rows "$d" map '[{"key":"basemap#value","definition":"One installed set of map data.","not":"not a stylesheet","surfaces":{"operation":"drawBasemap"},"anchor":["case: map#aBasemapIsDrawn"]}]'
expect 'glossary#aDeclaredKindWithNoSurfaceIsARefusal' 1 "$d" 'kind-mismatch|type' 'a row whose kind demands a type but declares an operation is refused'

d="$tmp/bool"; mk_fixture "$d"
case_source "$d" app/src/main/java/com/example/map/Follow.kt 'package com.example.map

val followMode: Boolean = true
class Follow
'
write_rows "$d" map '[{"key":"follow#boolean","definition":"Whether the map follows the position.","not":"not a one-off jump","surfaces":{"property":"followMode"},"anchor":["case: map#aBasemapIsDrawn"]}]'
expect 'glossary#aBareBooleanIsRefused' 1 "$d" 'bool-prefix|followMode|is,has,can' 'a boolean surface with no prefix is refused, the prefixes named'

d="$tmp/unit"; mk_fixture "$d"
write_rows "$d" map '[{"key":"speed#attribute","definition":"How fast the position moves.","not":"not a clock","surfaces":{"property":"zoom"},"anchor":["case: map#aBasemapIsDrawn"]}]'
expect 'glossary#aPhysicalAttributeWithoutUnitIsRefused' 1 "$d" 'unit-missing|speed#attribute' 'an attribute with no unit is refused'

# --- the concept kind is the only global scope -------------------------------------------------------------
d="$tmp/global"; mk_fixture "$d"
write_rows "$d" concept '[{"key":"theme#entity","definition":"The colours a surface draws its chrome with.","not":"not a map palette","surfaces":{"type":"Theme"},"home":"feature/map","anchor":["case: map#aBasemapIsDrawn"]}]'
case_source "$d" app/src/main/java/com/example/map/Theme.kt 'package com.example.map

class Theme
'
expect 'glossary#aGlobalTermIsHomedInConcept' 1 "$d" 'unowned-term|theme#entity|feature/map' 'a global row homed in a feature is refused'

d="$tmp/scope"; mk_fixture "$d"
write_rows "$d" nowhere '[{"key":"route#entity","definition":"The ordered plan from one place to another.","not":"not a recorded trail","surfaces":{"type":"Route"},"anchor":["case: map#aBasemapIsDrawn"]}]'
case_source "$d" app/src/main/java/com/example/map/Route.kt 'package com.example.map

class Route
'
expect 'glossary#anUndeclaredScopeIsRefused' 1 "$d" 'undeclared-context|nowhere' 'a scope the structure declaration does not carry is refused'

# --- only a coining kind declares a term ------------------------------------------------------------------
d="$tmp/misplaced"; mk_fixture "$d"
write_rows "$d" map '[{"key":"basemap#entity","definition":"One installed set of map data.","not":"not a stylesheet","surfaces":{"type":"Basemap"},"anchor":["case: map#aBasemapIsDrawn"]}]'
case_source "$d" app/src/main/java/com/example/data/Basemap.kt 'package com.example.data

class Basemap
'
expect 'glossary#aTermInANonCoiningKindIsRefused' 1 "$d" 'misplaced-term|platform' 'a declared term declared in a platform package is refused'

d="$tmp/vacuous"; mk_fixture "$d"
write_rows "$d" map '[{"key":"basemap#entity","definition":"One installed set of map data.","not":"not a stylesheet","surfaces":{"type":"BasemapManager"},"anchor":["case: map#aBasemapIsDrawn"]}]'
case_source "$d" app/src/main/java/com/example/map/BasemapManager.kt 'package com.example.map

class BasemapManager
'
expect 'glossary#aVacuousSuffixIsRefusedInACoiningKind' 1 "$d" 'bad-name|BasemapManager|Manager' 'a declared type with a vacuous suffix is refused, with the lexicon reason'

d="$tmp/port"; mk_fixture "$d"
case_source "$d" core/src/main/java/com/example/port/LocationProvider.kt 'package com.example.port

class LocationProvider
'
expect 'glossary#aPortRoleIsNotAVacuousName' 0 "$d" 'rows=1 ' 'a port role carrying a refused suffix is legal, because a port does not coin a term'

# --- the vocabulary reads the structure declaration -------------------------------------------------------
d="$tmp/schema"; mk_fixture "$d"
write_rows "$d" map '[{"key":"basemap#entity","definition":"One installed set of map data.","not":"not a stylesheet","surfaces":{"type":"Basemap"},"imports":[],"anchor":["case: map#aBasemapIsDrawn"]}]'
expect 'glossary#aRowWithAnUnknownFieldIsRefused' 1 "$d" 'bad-name|imports|closed schema' 'a row carrying a field the schema does not declare is refused'

d="$tmp/missing-structure"; mk_fixture "$d"
rm -f "$d/architecture/structure.tsv"
expect 'glossary#aMissingDeclarationFailsClosed' 1 "$d" 'undeclared-context|architecture/structure.tsv' 'a missing structure declaration fails closed, naming the file'

# --- a refusal names the row and the artefact -------------------------------------------------------------
d="$tmp/one-line"; mk_fixture "$d"
case_source "$d" app/src/main/java/com/example/map/Basemap.kt 'package com.example.map

class Basemap
class BaseMap
'
write_rows "$d" map '[{"key":"basemap#entity","definition":"One installed set of map data.","not":"not a stylesheet","surfaces":{"type":"Basemap"},"canonical":"Basemap","forbidden":{"type":["BaseMap"]},"anchor":["case: map#aBasemapIsDrawn"]}]'
if [ -z "$case_filter" ] || [ "$case_filter" = 'glossary#aRefusalNamesItsRowAndItsArtefact' ]; then
  case_ids+=('glossary#aRefusalNamesItsRowAndItsArtefact')
  out=$("$check" --root "$d" 2>&1)
  n=$(printf '%s\n' "$out" | grep -c 'drift ' || true)
  if [ "$n" = 1 ] && printf '%s\n' "$out" | grep -q 'basemap#entity' \
     && printf '%s\n' "$out" | grep -q 'Basemap.kt:4' && printf '%s\n' "$out" | grep -q 'declared=Basemap'; then
    pass=$((pass + 1)); printf 'ok   %s — %s\n' 'glossary#aRefusalNamesItsRowAndItsArtefact' 'one finding is one line, carrying key, file:line, counts and the word'
  else
    failures=$((failures + 1)); printf 'FAIL %s\n%s\n' 'glossary#aRefusalNamesItsRowAndItsArtefact' "$out"
  fi
fi

d="$tmp/green"; mk_fixture "$d"
expect 'glossary#aGreenRunPrintsFigures' 0 "$d" 'green rows=1 anchored=1 kinds=entity files=1' 'a green run prints the rows, anchored rows, kinds and files it verified'

if [ "$list_only" -eq 1 ]; then
  printf '%s\n' "${case_ids[@]}"
  exit 0
fi

printf '\n%d passed, %d failed\n' "$pass" "$failures"
[ "$failures" -eq 0 ]
# ignored files under openspec/changes (the unarchived changes of this project):
