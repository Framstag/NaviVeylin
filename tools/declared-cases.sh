#!/usr/bin/env bash
# Declared-case selection — change `speed-up-test-iteration`, spec `build-test-gate`
# ("Iteration pre-gate runs the change's declared cases first").
#
# Usage:
#   bash tools/declared-cases.sh <change-name>                    report selection + measured cost
#   bash tools/declared-cases.sh <change-name> --command          also print the Gradle command
#   bash tools/declared-cases.sh <change-name> --diff <file>      select from that file list instead
#                                                                 of the whole working tree
#   bash tools/declared-cases.sh <change-name> --rules declared,touched
#                                                                 run/report only those rules as the
#                                                                 union (all three by default)
#
# The selection is the union of three rules, each derived from the change and its diff — nothing is
# maintained by hand:
#   declared  a `*Test` class the change's own artifacts name
#   touched   a `*Test` file the change's diff touches
#   mention   a test whose source mentions a type that a changed production file declares
#
# The report prints per-rule counts, so an inflated rule is visible instead of hidden: in a shared
# working tree `touched` and `mention` also see a peer's dirty files, and the change's own file list
# (`--diff`) is then the honest input. `mention` is the widest rule by measurement — a change to the
# app's central types reaches most of the suite through it — so `--rules declared,touched` is the
# cheap first stage of a two-stage pass, and the full union is the second. Nothing is executed here —
# a pre-gate run is not gate evidence, and the unfiltered module suite is still owed before the change
# is complete (spec `build-test-gate`; design D3/D4 of `speed-up-test-iteration`).
#
# Overrides for the self-test, which drives a synthetic tree without git:
#   DECLARED_CASES_ROOT _CHANGES_DIR _TEST_ROOTS _RESULT_DIRS _DIFF
#
# Exit codes: 0 = reported a selection (including an empty one, with its reason); 2 = usage,
# missing change or unreadable diff file; 3 = no test source root found.
set -uo pipefail

usage() {
  sed -n '2,15p' "$0" | sed 's#^#  #'
}

fail() { echo "declared-cases: $*" >&2; exit 2; }

CHANGE=""
WANT_COMMAND=0
DIFF_FILE="${DECLARED_CASES_DIFF:-}"
RULES="declared,touched,mention"

while [ $# -gt 0 ]; do
  case "$1" in
    --command) WANT_COMMAND=1 ;;
    --diff)    shift; [ $# -gt 0 ] || fail "--diff needs a file"; DIFF_FILE="$1" ;;
    --rules)   shift; [ $# -gt 0 ] || fail "--rules needs a comma-separated list"; RULES="$1" ;;
    -h|--help) usage; exit 0 ;;
    -*)        fail "unknown option '$1'" ;;
    *)         [ -z "$CHANGE" ] || fail "more than one change name given"; CHANGE="$1" ;;
  esac
  shift
done
[ -n "$CHANGE" ] || { usage; exit 2; }

rule_enabled() { case ",$RULES," in *",$1,"*) return 0 ;; *) return 1 ;; esac; }
for r in ${RULES//,/ }; do
  case "$r" in declared|touched|mention) ;; *) fail "unknown rule '$r' (declared, touched, mention)" ;; esac
done

ROOT="${DECLARED_CASES_ROOT:-$(git rev-parse --show-toplevel 2>/dev/null)}"
[ -n "$ROOT" ] || fail "not inside a git work tree — set DECLARED_CASES_ROOT"

CHANGES_DIR="${DECLARED_CASES_CHANGES_DIR:-$ROOT/openspec/changes}"
TEST_ROOTS="${DECLARED_CASES_TEST_ROOTS:-$ROOT/app/src/test/java $ROOT/auto/src/test/java $ROOT/core/src/test/java}"
RESULT_DIRS="${DECLARED_CASES_RESULT_DIRS:-$ROOT/app/build/test-results/testMobileDebugUnitTest
$ROOT/app/build/test-results/testAutomotiveDebugUnitTest
$ROOT/auto/build/test-results/testDebugUnitTest
$ROOT/core/build/test-results/testDebugUnitTest}"

CHANGE_DIR="$CHANGES_DIR/$CHANGE"

TMP="$(mktemp -d)" || fail "cannot create a temporary directory"
trap 'rm -rf "$TMP"' EXIT

# `fqn <TAB> module <TAB> path` — the module is the test root's own prefix when it has one, else the
# first segment of a repo-relative path, so an absolute and a relative input agree.
module_of_path() {
  case "$1" in
    */app/src/test/*)  echo app;  return ;;
    */auto/src/test/*) echo auto; return ;;
    */core/src/test/*) echo core; return ;;
  esac
  printf '%s' "$1" | sed -E 's#^([a-z]+)/src/test/java/.*#\1#'
}
class_of_path() { printf '%s' "$1" | sed -E 's#^.*/src/test/java/##; s#\.kt$##; s#/#.#g'; }

# ── index: every test class in the modules ─────────────────────────────────────────────────────
test_root_count=0
for r in $TEST_ROOTS; do [ -d "$r" ] && test_root_count=$((test_root_count + 1)); done
[ "$test_root_count" -gt 0 ] || { echo "declared-cases: none of these test roots exists: $TEST_ROOTS" >&2; exit 3; }

find $TEST_ROOTS -name '*Test.kt' 2>/dev/null | sort > "$TMP/test-paths.txt"
while IFS= read -r p; do
  [ -n "$p" ] || continue
  printf '%s\t%s\t%s\n' "$(class_of_path "$p")" "$(module_of_path "$p")" "$p"
done < "$TMP/test-paths.txt" > "$TMP/index.tsv"

# ── the change's diff ─────────────────────────────────────────────────────────────────────────
if [ -n "$DIFF_FILE" ]; then
  [ -f "$DIFF_FILE" ] || fail "no diff file at $DIFF_FILE"
  sed -E 's/^\s+//; s/\s+$//' "$DIFF_FILE" | grep -v '^$' > "$TMP/diff-paths.txt"
  DIFF_SOURCE="file list $DIFF_FILE"
else
  # Working tree: modified, added, deleted and untracked paths; a rename contributes its new name.
  git -C "$ROOT" status --porcelain | awk '
    { path = substr($0, 4)
      if (path ~ / -> /) { split(path, parts, / -> /); path = parts[2] }
      gsub(/^"|"$/, "", path)
      print path }' | sort -u > "$TMP/diff-paths.txt"
  DIFF_SOURCE="working tree of $ROOT"
fi

grep -E '(^|/)(app|auto|core)/src/main/java/.*\.(kt|java)$' "$TMP/diff-paths.txt" \
  | sort -u > "$TMP/prod-changed.txt"
grep -E '^.*/src/test/java/.*Test\.kt$' "$TMP/diff-paths.txt" | sort -u > "$TMP/test-changed.txt"

# ── rule 1: declared by the change's own artifacts ────────────────────────────────────────────
: > "$TMP/r1.txt"
if [ -d "$CHANGE_DIR" ]; then
  grep -rhoE '\b[A-Z][A-Za-z0-9]*Test\b' "$CHANGE_DIR" 2>/dev/null | sort -u > "$TMP/declared-names.txt"
  while IFS= read -r name; do
    awk -F'\t' -v n="$name" '$1 ~ "(^|\\.)"n"$" { print $0 }' "$TMP/index.tsv" | head -1
  done < "$TMP/declared-names.txt" > "$TMP/r1.txt"
fi

# ── rule 2: touched by the change's diff ──────────────────────────────────────────────────────
: > "$TMP/r2.txt"
while IFS= read -r p; do
  [ -n "$p" ] || continue
  awk -F'\t' -v path="$p" '$3 == path || $3 ~ "/"path"$" { print $0 }' "$TMP/index.tsv"
done < "$TMP/test-changed.txt" > "$TMP/r2.txt"

# ── rule 3: a test mentions a type a changed production file declares ─────────────────────────
: > "$TMP/r3.txt"
while IFS= read -r p; do
  [ -n "$p" ] || continue
  sym="$(basename "$p")"; sym="${sym%.kt}"; sym="${sym%.java}"
  [ -n "$sym" ] || continue
  grep -rlE "\b${sym}\b" $TEST_ROOTS --include='*Test.kt' 2>/dev/null
done < "$TMP/prod-changed.txt" | sort -u > "$TMP/r3-paths.txt"
while IFS= read -r p; do
  [ -n "$p" ] || continue
  awk -F'\t' -v path="$p" '$3 == path { print $0 }' "$TMP/index.tsv"
done < "$TMP/r3-paths.txt" > "$TMP/r3.txt"

# ── union: one row per class, its label the strongest rule that selected it ────────────────────
{
  rule_enabled declared && awk -F'\t' '{ print $0"\tdeclared" }' "$TMP/r1.txt"
  rule_enabled touched  && awk -F'\t' '{ print $0"\ttouched"  }' "$TMP/r2.txt"
  rule_enabled mention  && awk -F'\t' '{ print $0"\tmention"  }' "$TMP/r3.txt"
} | awk -F'\t' '!seen[$1]++' | sort -t$'\t' -k1,1 > "$TMP/selected.tsv"

count_of() { [ -f "$1" ] && awk 'NF { n++ } END { print n+0 }' "$1" || echo 0; }
R1_N="$(count_of "$TMP/r1.txt")"
R2_N="$(count_of "$TMP/r2.txt")"
R3_N="$(count_of "$TMP/r3.txt")"
SELECTED_COUNT="$(count_of "$TMP/selected.tsv")"

measured_time() {
  for d in $RESULT_DIRS; do
    f="$d/TEST-$1.xml"
    if [ -f "$f" ]; then
      grep -o 'time="[0-9.]*"' "$f" | head -1 | tr -dc '0-9.'
      return
    fi
  done
  printf '-'
}

echo "declared-cases: change '$CHANGE'"
echo "  diff      $DIFF_SOURCE — $(count_of "$TMP/diff-paths.txt") path(s), $(count_of "$TMP/prod-changed.txt") production file(s), $(count_of "$TMP/test-changed.txt") test file(s)"
printf '  rules     declared %s · touched %s · mention %s · union of %s\n' "$R1_N" "$R2_N" "$R3_N" "$RULES"

if [ "$SELECTED_COUNT" -eq 0 ]; then
  echo
  echo "Nothing selected. Reasons, rule by rule:"
  if [ ! -d "$CHANGE_DIR" ]; then
    echo "  declared  no change artifacts at $CHANGE_DIR, so no class can be declared by them"
  elif [ "$(count_of "$TMP/declared-names.txt")" -eq 0 ]; then
    echo "  declared  the change's artifacts name no '*Test' token at all"
  else
    echo "  declared  the artifacts name token(s) that match no existing test class"
  fi
  echo "  touched   the diff touches no test source (or the rule is not in --rules)"
  echo "  mention   the diff changes no production file (or the rule is not in --rules)"
  echo
  echo "An empty selection is not a failure: there is nothing to iterate, and the module suite is"
  echo "still owed before the change is called complete."
  exit 0
fi

printf '\n%3s %-8s %-7s %-9s %s\n' "#" "rule" "module" "last time" "test class"
printf '%s\n' "──────────────────────────────────────────────────────────────────────────────"
i=0
TOTAL=0
while IFS=$'\t' read -r fqn module path rule; do
  i=$((i + 1))
  t="$(measured_time "$fqn")"
  case "$t" in ''|-) t='-' ;; *) TOTAL="$(awk -v a="$TOTAL" -v b="$t" 'BEGIN { printf "%.3f", a + b }')" ;; esac
  [ -n "$module" ] || module='?'
  printf '%3d %-8s %-7s %-9s %s\n' "$i" "$rule" "$module" "$t" "$fqn"
done < "$TMP/selected.tsv"

printf '%s\n' "──────────────────────────────────────────────────────────────────────────────"
printf 'selected %s of %s test classes; measured class time %ss\n' \
  "$SELECTED_COUNT" "$(count_of "$TMP/index.tsv")" \
  "$(awk -v t="$TOTAL" 'BEGIN { printf "%.1f", t }')"
cat <<'NOTE'
The suite's wall time is not that sum: it runs at the declared fork count, every invocation pays the
Robolectric fork bootstrap plus the daemon and (when test sources changed) the test compile, so the
floor is ~30s, not 10s. And a pre-gate run is NOT gate evidence: run the unfiltered module suite
before marking the change complete.
NOTE
if [ "$R2_N" -gt "$R1_N" ] && [ -z "$DIFF_FILE" ]; then
  cat <<'NOTE'
The touched/mention counts come from the whole working tree, which also carries other changes' files
here — pass --diff <file list of this change's own paths> for an honest selection.
NOTE
fi

if [ "$WANT_COMMAND" -eq 1 ]; then
  echo
  echo "# Ready-to-run iteration command (no coverage agent; the selection is the table above):"
  for mod in app auto core; do
    task=''
    case "$mod" in
      app)  task=':app:testMobileDebugUnitTest' ;;
      auto) task=':auto:testDebugUnitTest' ;;
      core) task=':core:testDebugUnitTest' ;;
    esac
    filters="$(awk -F'\t' -v m="$mod" '$2 == m { printf "--tests \"%s\" ", $1 }' "$TMP/selected.tsv")"
    [ -n "$filters" ] && printf './gradlew %s -PforceTests --no-build-cache -PnoCoverage %s\n' "$task" "$filters"
  done
fi
