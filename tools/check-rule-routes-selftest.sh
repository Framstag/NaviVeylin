#!/usr/bin/env bash
# Self-test for tools/check-rule-routes.sh — almost every case runs against a throwaway fixture under a temp
# directory, so nothing in the repository is written. One case reads this tree's own map: the granularity of a
# glob is a fact about *this* map, and a fixture cannot carry it — so that case copies the map and the guideline
# corpus into a throwaway git tree and derives from there.
#
# Cases: a map whose globs match the tree and whose rules are all reached (pass); a glob that matches no
# tracked path; a named rule no glob reaches; a normative document no glob names; a `guidelines/` route
# carrying a document its glob does not match; `--diff` over a fixture range — the reading set follows the
# paths a change touched, and a change that names fewer sections than the map derives is reported while a
# change that names them all (or adds one) passes; and five edits to five guideline documents of this tree
# (one document, one companion layer, one concern family, one of the five `Map*` documents that had been
# orphaned, and the tooling document that shares the backlog concern), each of which must derive that
# document's own entries and no other document's.
#
# Usage: check-rule-routes-selftest.sh [--case <id>]
set -uo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
check="$root/tools/check-rule-routes.sh"
case_filter=""
while [ $# -gt 0 ]; do
  case "$1" in
    --case) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { echo "check-rule-routes-selftest: $1 needs a value" >&2; exit 2; }; case_filter="${2:-}"; shift 2 ;;
    *) echo "check-rule-routes-selftest: unknown argument '$1'" >&2; exit 2 ;;
  esac
done
tmp=$(mktemp -d "${TMPDIR:-/tmp}/check-rule-routes-selftest.XXXXXX") || exit 2
trap 'rm -rf "$tmp"' EXIT

[ -x "$check" ] || { echo "check-rule-routes-selftest: $check is not executable" >&2; exit 2; }

pass=0
failures=0

mk_fixture() { # $1 = directory
  mkdir -p "$1/tools" "$1/guidelines" "$1/src"
  printf '{"profile":1,"docRoutes":"tools/doc-routes.json"}\n' > "$1/tools/tooling.json"
  printf '# Doc Specification\n\n## 1. A rule home\n\n- **Rule: nothing yet.** The refusal path.\n' > "$1/guidelines/Doc.md"
  printf 'fun main() {}\n' > "$1/src/Thing.kt"
}

# Every fixture's map names `guidelines/Doc.*` with no entries, so the fixture already satisfies the third
# invariant (a normative document no glob names is reported) and the case under test is not answered by it.
# A case that is *about* one of the two document invariants writes its map without that route, through
# `write_map_bare`.
doc_route='{ "glob": "guidelines/Doc.*", "sections": [], "rules": [] }'

write_map() { # $1 = directory, $2 = the routes array body, with the document's own route appended
  printf '{ "routes": [ %s, %s ] }\n' "$2" "$doc_route" > "$1/tools/doc-routes.json"
}

write_map_bare() { # $1 = directory, $2 = the routes array body, nothing appended
  printf '{ "routes": [ %s ] }\n' "$2" > "$1/tools/doc-routes.json"
}

expect() { # $1 case id, $2 expected exit, $3 expected substring, $4 description, $5... command
  local id="$1" want="$2" sub="$3" desc="$4" out got ok=1
  shift 4
  [ -z "$case_filter" ] || [ "$case_filter" = "$id" ] || return 0
  out=$("$@" 2>&1); got=$?
  [ "$got" = "$want" ] || ok=0
  if [ -n "$sub" ] && ! printf '%s' "$out" | grep -qF -- "$sub"; then ok=0; fi
  if [ "$ok" = 1 ]; then
    echo "PASS  $id — $desc (exit $got)"
    pass=$((pass + 1))
  else
    echo "FAIL  $id — $desc: expected exit $want containing '$sub', got exit $got:"
    printf '        %s\n' "$out"
    failures=$((failures + 1))
  fi
}

# ── the map resolves and every rule is reached ─────────────────────────────────────────────────────────────
mk_fixture "$tmp/ok"
write_map "$tmp/ok" '{ "glob": "src/**", "sections": ["Doc.md §1"], "rules": ["Doc.md §1 \"nothing yet.\""] }'
expect routes#theMapResolvesAndEveryRuleIsReached 0 "all 1 named rule(s) are reached" \
  "a map whose glob matches the tree and whose rule is reached passes" \
  bash "$check" --root "$tmp/ok"

# ── a normative document no glob names ─────────────────────────────────────────────────────────────────────
mk_fixture "$tmp/doc-orphan"
write_map_bare "$tmp/doc-orphan" '{ "glob": "src/**", "sections": ["Doc.md §1"], "rules": ["Doc.md §1 \"nothing yet.\""] }'
expect routes#aDocumentNoGlobNamesIsReported 1 "no glob matches guidelines/Doc.md" \
  "a normative document no glob matches is reported, even though its rule is reached by another path's route" \
  bash "$check" --root "$tmp/doc-orphan"

# ── a guideline route carrying a document its glob does not match ─────────────────────────────────────────
mk_fixture "$tmp/doc-mismatch"
printf '# Other Specification\n\n## 1. Another home\n\n- **Rule: another.** Its own body.\n' > "$tmp/doc-mismatch/guidelines/Other.md"
write_map_bare "$tmp/doc-mismatch" '{ "glob": "guidelines/Doc.*", "sections": ["Doc.md §1", "Other.md §1"], "rules": ["Doc.md §1 \"nothing yet.\""] }, { "glob": "guidelines/Other.*", "sections": ["Other.md §1"], "rules": ["Other.md §1 \"another.\""] }'
expect routes#aGuidelineRouteCarryingAnotherDocumentIsReported 1 "carries a Other.md entry it does not match" \
  'a route whose glob sits under `guidelines/` and names a document it never reaches is reported' \
  bash "$check" --root "$tmp/doc-mismatch"

# ── a glob that matches no tracked path ────────────────────────────────────────────────────────────────────
mk_fixture "$tmp/glob-dead"
write_map "$tmp/glob-dead" '{ "glob": "src/**", "sections": ["Doc.md §1"], "rules": ["Doc.md §1 \"nothing yet.\""] }, { "glob": "no/such/path/**", "sections": [], "rules": [] }'
expect routes#aGlobMatchingNoTrackedPathIsReported 1 "matches no tracked path" \
  "a glob under which the tree holds no file is reported" \
  bash "$check" --root "$tmp/glob-dead"

# ── a named rule no glob reaches ───────────────────────────────────────────────────────────────────────────
mk_fixture "$tmp/rule-orphan"
printf '# Doc Specification\n\n## 1. A rule home\n\n- **Rule: a lonely rule.** The refusal path.\n' > "$tmp/rule-orphan/guidelines/Doc.md"
write_map "$tmp/rule-orphan" '{ "glob": "src/**", "sections": ["Doc.md §1"], "rules": [] }'
expect routes#aRuleNoGlobReachesIsReported 1 'the rule "a lonely rule."' \
  "a named rule no glob reaches is reported with its name and document" \
  bash "$check" --root "$tmp/rule-orphan"

# ── the reading set follows the diff ───────────────────────────────────────────────────────────────────────
mk_git_fixture() { # $1 directory
  mk_fixture "$1"
  write_map "$1" '{ "glob": "src/**", "sections": ["Doc.md §1"], "rules": ["Doc.md §1 \"nothing yet.\""] }'
  git -C "$1" init -q -b main >/dev/null 2>&1 || { echo "check-rule-routes-selftest: git init failed" >&2; exit 2; }
  git -C "$1" add -A >/dev/null 2>&1
  git -C "$1" -c user.email=selftest@example.invalid -c user.name=selftest commit -qm one >/dev/null 2>&1
  printf 'fun main() { println("changed") }\n' > "$1/src/Thing.kt"
  git -C "$1" add -A >/dev/null 2>&1
  git -C "$1" -c user.email=selftest@example.invalid -c user.name=selftest commit -qm two >/dev/null 2>&1
}

mk_git_fixture "$tmp/diff"
mkdir -p "$tmp/diff/openspec/changes/thing"
printf '# a change\n\nIt touches `src/Thing.kt`.\n' > "$tmp/diff/openspec/changes/thing/proposal.md"
expect routes#theReadingSetFollowsTheDiff 1 "the change does not name it" \
  "a change naming fewer sections than the map derives for its paths is reported" \
  bash "$check" --root "$tmp/diff" --diff HEAD~1 --change "$tmp/diff/openspec/changes/thing"

printf '# a change\n\nIt is graded against `Doc.md` §1.\n' > "$tmp/diff/openspec/changes/thing/proposal.md"
expect routes#aChangeNamingItsDerivedSectionsPasses 0 "" \
  "a change that names every derived section passes" \
  bash "$check" --root "$tmp/diff" --diff HEAD~1 --change "$tmp/diff/openspec/changes/thing"

printf '# a change\n\nIt is graded against `Doc.md` §1, and it adds `Build.md` §3.\n' > "$tmp/diff/openspec/changes/thing/proposal.md"
expect routes#aChangeMayAddASection 0 "" \
  "a change may add a section it names beyond the derived set" \
  bash "$check" --root "$tmp/diff" --diff HEAD~1 --change "$tmp/diff/openspec/changes/thing"

# ── one guideline edit derives its own document, and no other document's ───────────────────────────────────
#
# A glob's granularity is a fact about this tree's map, so the fixture below is the map and the guideline corpus
# copied from this tree, not a synthetic one. Each document is committed on its own, so `--diff HEAD~1` derives the
# set for exactly that edit: the entries must name the edited document's own concern and no other — a coarse
# `guidelines/**` derives the whole corpus for any one of them (issue #733). Five documents are asserted: one
# document, one companion layer, one concern family, one of the five `Map*` documents that a route split had left
# with no glob at all, and the tooling document that shares the backlog concern.
guideline_fixture() { # $1 = directory
  mkdir -p "$1/tools"
  cp "$root/tools/tooling.json" "$root/tools/doc-routes.json" "$1/tools/"
  cp -R "$root/guidelines" "$1/guidelines"
  git -C "$1" init -q -b main >/dev/null 2>&1 || { echo "check-rule-routes-selftest: git init failed" >&2; exit 2; }
  git -C "$1" add -A >/dev/null 2>&1
  git -C "$1" -c user.email=selftest@example.invalid -c user.name=selftest commit -qm base >/dev/null 2>&1
}

guideline_edit_derives_only() { # $1 = directory, $2 = path it edits, $3 = the documents it may derive, sorted
  local out entries docs
  printf '\nOne more sentence, appended by the case (issue #733).\n' >> "$1/$2" || return 1
  git -C "$1" add -A >/dev/null 2>&1
  git -C "$1" -c user.email=selftest@example.invalid -c user.name=selftest commit -qm "edit $2" >/dev/null 2>&1
  out=$(bash "$check" --root "$1" --diff HEAD~1 2>&1)
  entries=$(printf '%s\n' "$out" | grep -E '^  [A-Za-z0-9_-]+\.md §')
  docs=$(printf '%s\n' "$entries" | sed -E 's/^  ([A-Za-z0-9_-]+)\.md.*/\1/' | sort -u | tr '\n' ' ')
  if [ "$(printf '%s' "$entries" | grep -c .)" -eq 0 ]; then
    printf '        %s derives nothing — the map reaches no document for it\n' "$2"
    return 1
  fi
  if [ "$docs" != "$3 " ]; then
    printf '        %s derives %s entries naming [%s] — it is one concern, so it must derive [%s] alone\n' \
      "$2" "$(printf '%s' "$entries" | grep -c .)" "$docs" "$3"
    return 1
  fi
}

guideline_fixture "$tmp/guideline"
expect routes#aGuidelineEditDerivesItsOwnDocument 0 "" \
  "one edit to one guideline document derives that document's entries alone" \
  guideline_edit_derives_only "$tmp/guideline" guidelines/Logging.md Logging
expect routes#aGuidelineEditDerivesItsOwnDocument 0 "" \
  "…for a companion layer too — a procedure edit derives its document" \
  guideline_edit_derives_only "$tmp/guideline" guidelines/BuildGate.recipes.md BuildGate
expect routes#aGuidelineEditDerivesItsOwnDocument 0 "" \
  "…and a concern family derives its four siblings, not the corpus" \
  guideline_edit_derives_only "$tmp/guideline" guidelines/DesignStack.md "Design DesignCrossVariant DesignNative DesignStack"
expect routes#aGuidelineEditDerivesItsOwnDocument 0 "" \
  "…and a document a route split had left with no glob derives its own entries, not nothing" \
  guideline_edit_derives_only "$tmp/guideline" guidelines/MapCar.md MapCar
expect routes#aGuidelineEditDerivesItsOwnDocument 0 "" \
  "…and so does the document that shares the backlog concern with a sibling of its own" \
  guideline_edit_derives_only "$tmp/guideline" guidelines/BacklogTooling.md BacklogTooling

if [ -n "$case_filter" ] && [ "$pass" = 0 ] && [ "$failures" = 0 ]; then
  echo "check-rule-routes-selftest: no case with the id '$case_filter'" >&2
  exit 2
fi
if [ "$failures" -ne 0 ]; then
  echo "check-rule-routes-selftest: $failures of $((pass + failures)) cases failed"
  exit 1
fi
echo "check-rule-routes-selftest: all $pass cases passed (throwaway fixtures, no Gradle, no device, no network)"
exit 0
