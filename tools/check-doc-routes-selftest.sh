#!/usr/bin/env bash
# Self-test for tools/check-doc-routes.sh: every case runs against a throwaway fixture under a temp
# directory, so the project's own documents are not read and nothing in the repository is written — except the
# `routed-read#*` cases: `#bound` and `#route` read this repository's `AGENTS.md` and `guidelines/*.md` because
# their claim is about this tree's route and not about a fixture's, and `#numbers` reads a fixture whose split
# keeps its parent number and its citations. None of them writes anything.
#
# Cases: a map that resolves (including a duplicate section number resolved by its heading text), a
# dangling number, a bare number that is ambiguous, a heading text that matches nothing, a document the
# table references but that does not exist, an AGENTS.md with no Documentation Map, a guideline document
# that no route names, an extra guideline that is routed and resolves, an extra guideline that is routed
# but whose section does not exist, the same three reference forms written in a guideline *body* rather than
# in the table — a bare duplicated number, a heading text that resolves it, and a section the document does
# not have — a reference inside a fenced code block (an example) and one inside a change artifact (history),
# four skill-path cases: a named skill that exists, one that no longer exists (renamed or deleted), one
# whose directory carries no `SKILL.md`, and one whose name is only a prefix of a skill that does exist — and
# two cases over the project's own tree: `routed-read#route` (a row that routes a lettered sub-section of *any*
# document states its cost, so a row naming sub-sections and pricing nothing fails) and `routed-read#bound`
# (the cost of every section of every routed document, printed with the requirement's ruler and the routed
# pair marked, so the number a row restates is the number the measurement takes) — and three more that read the
# same tree and the same ruler: `routed-read#numbers` (a fixture whose split keeps its parent number, its citations
# and a passing route check), `routed-read#figure` (a figure a row writes after a lettered reference is that
# section's measurement, so `0.1 kB` for a kilobyte section fails, and the project's own eleven figures must be
# their sections') and `routed-read#range` (a range is the span of the lettered sections the row names, while a
# figure the row writes as a past measurement is not compared).
#
# The check derives its documents from the fixture's own `guidelines/` directory, so a fixture carries the
# four base documents plus whichever extra one a case adds — and its table must name every document present.
#
# The layers and named-rule cases: a companion that a route does not name is read and its `## §N` anchor is
# resolved against its base document (`routes#aCompanionIsReadAndNotRouted`,
# `layers#aCompanionAnchorThatNoSectionCarriesIsReported`, `layers#aDroppedSectionIsReportedInItsCompanion`,
# `layers#aRuleIsNormativeAndItsRationaleIsACompanion`), and a rule name is unique in its document and across
# the documents (`rules#aNameTwiceInOneDocumentIsReported`, `rules#aNameTwoDocumentsStateIsReported`) while a
# `see rule <File>.md §N "<name>"` reference resolves or is reported with the names the section carries
# (`rules#aReferenceResolvesToTheNamedRule`, `rules#aStaleRuleReferenceIsReported`).
#
# Every case carries an id (`routes#<name>`, `routed-read#<name>`), so a task item or a mutation set can quote
# one instead of a sentence; `--case <id>` runs one of them.
#
# Usage: check-doc-routes-selftest.sh [--case <id>]
set -uo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
check="$root/tools/check-doc-routes.sh"
case_filter=""
while [ $# -gt 0 ]; do
  case "$1" in
    --case) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { echo "check-doc-routes-selftest: $1 needs a value" >&2; exit 2; }; case_filter="${2:-}"; shift 2 ;;
    *) echo "check-doc-routes-selftest: unknown argument '$1'" >&2; exit 2 ;;
  esac
done
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

add_skill() { # $1 = directory, $2 = skill name
  mkdir -p "$1/.pi/skills/$2"
  printf -- '---\nname: %s\n---\nfixture skill\n' "$2" > "$1/.pi/skills/$2/SKILL.md"
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

expect() { # $1 case id, $2 expected exit, $3 fixture dir, $4 expected substring, $5 description
  local out got ok
  [ -z "$case_filter" ] || [ "$case_filter" = "$1" ] || return 0
  out=$("$check" --root "$3" 2>&1)
  got=$?
  ok=1
  [ "$got" = "$2" ] || ok=0
  if [ -n "${4:-}" ] && ! printf '%s' "$out" | grep -qF -- "$4"; then ok=0; fi
  if [ "$ok" = 1 ]; then
    echo "PASS  $1 — $5 (exit $got)"
    pass=$((pass + 1))
  else
    echo "FAIL  $1 — $5: expected exit $2 containing '$4', got exit $got:"
    printf '        %s\n' "$out"
    failures=$((failures + 1))
  fi
}

expect_all() { # $1 case id, $2 expected exit, $3 fixture dir, $4 description, $5... substrings
  local out got ok s id desc
  [ -z "$case_filter" ] || [ "$case_filter" = "$1" ] || return 0
  id="$1"; desc="$4"
  out=$("$check" --root "$3" 2>&1)
  got=$?
  ok=1
  [ "$got" = "$2" ] || ok=0
  shift 4
  for s in "$@"; do
    printf '%s' "$out" | grep -qF -- "$s" || ok=0
  done
  if [ "$ok" = 1 ]; then
    echo "PASS  $id — $desc (exit $got)"
    pass=$((pass + 1))
  else
    echo "FAIL  $id — $desc: expected exit $2 containing every of '$*', got exit $got:"
    printf '        %s\n' "$out"
    failures=$((failures + 1))
  fi
}

agents_before=$(md5sum "$root/AGENTS.md" | awk '{print $1}')
guidelines_before=$(cat "$root"/guidelines/*.md | md5sum | awk '{print $1}')

for d in good dangling ambiguous mismatch missing noregion extra-unrouted extra-routed extra-dangling \
         skill-ok skill-dangling skill-nofile skill-prefix \
         body-ambiguous body-text body-fenced body-dangling artifacts \
         comp-ok comp-anchor comp-dropped ruledup-onedoc ruledup-twodoc ruleref-ok ruleref-stale rule-empty; do
  mk_fixture "$tmp/$d"
done

write_agents "$tmp/good" '| phone UI | `UI.md` §1 "Cross-variant UI parity" |
| car renderer | `UI.md` §14 "Android Auto renderer" |
| the other §14 | `UI.md` §14 "Rotation Gesture" |'
expect routes#everyReferenceResolves 0 "$tmp/good" "all 7 section references resolve" "a map where every reference resolves"

write_agents "$tmp/dangling" '| gone | `UI.md` §99 "First section" |'
expect routes#aDanglingSectionNumberIsReported 1 "$tmp/dangling" "UI.md §99" "a dangling section number fails naming the reference"

write_agents "$tmp/ambiguous" '| ambiguous | `UI.md` §14 |'
expect routes#aBareDuplicatedNumberIsAmbiguous 1 "$tmp/ambiguous" "ambiguous" "a bare duplicated number is reported as ambiguous"

write_agents "$tmp/mismatch" '| wrong text | `UI.md` §1 "No such heading" |'
expect routes#headingTextThatMatchesNothingIsReported 1 "$tmp/mismatch" "contains that text" "heading text that matches nothing fails"

write_agents "$tmp/missing" '| absent document | `UI.md` §1 "Cross-variant" |'
rm -f "$tmp/missing/guidelines/UI.md"
expect routes#aReferencedDocumentThatDoesNotExistIsReported 1 "$tmp/missing" "does not exist" "a document the table references but that does not exist is reported"

write_agents "$tmp/noregion" '| phone UI | `UI.md` §1 "Cross-variant UI parity" |'
printf '# AGENTS.md\n\n## Project Overview\n\nNo map here.\n' > "$tmp/noregion/AGENTS.md"
expect routes#anAgentsFileWithoutTheMapIsReported 2 "$tmp/noregion" "Documentation Map" "an AGENTS.md without a Documentation Map is reported"

add_doc "$tmp/extra-unrouted" Logging
write_agents "$tmp/extra-unrouted" '| phone UI | `UI.md` §1 "Cross-variant UI parity" |'
expect routes#anUnroutedGuidelineIsReported 1 "$tmp/extra-unrouted" "does not name this document" "an extra guideline that no route names is reported"

add_doc "$tmp/extra-routed" Logging
write_agents "$tmp/extra-routed" '| phone UI | `UI.md` §1 "Cross-variant UI parity" |
| logging | `Logging.md` §99 "Unused" |'
expect routes#anExtraGuidelineIsRoutedWithoutEditingTheCheck 0 "$tmp/extra-routed" "all 6 section references resolve" "an extra guideline that is routed and resolves passes without editing the check"

add_doc "$tmp/extra-dangling" Logging
write_agents "$tmp/extra-dangling" '| phone UI | `UI.md` §1 "Cross-variant UI parity" |
| logging | `Logging.md` §42 "Unused" |'
expect routes#anExtraGuidelineWithADanglingSectionIsReported 1 "$tmp/extra-dangling" "Logging.md §42" "an extra guideline whose section does not exist is reported"

add_skill "$tmp/skill-ok" fixture-skill
write_agents "$tmp/skill-ok" '| phone UI | `UI.md` §1 "Cross-variant UI parity" |
| a fixture skill | `.pi/skills/fixture-skill` |'
expect routes#aSkillTheTreeCarriesResolvesWithoutEditingTheCheck 0 "$tmp/skill-ok" "all 1 skill references resolve" "a skill the tree carries resolves without editing the check"

write_agents "$tmp/skill-dangling" '| phone UI | `UI.md` §1 "Cross-variant UI parity" |
| a screenshot look | `.pi/skills/pixel-check` |'
expect routes#aSkillPathThatNoLongerResolvesIsReported 1 "$tmp/skill-dangling" ".pi/skills/pixel-check" "a skill path that no longer resolves is reported"

mkdir -p "$tmp/skill-nofile/.pi/skills/pixel-check"
write_agents "$tmp/skill-nofile" '| phone UI | `UI.md` §1 "Cross-variant UI parity" |
| a screenshot look | `.pi/skills/pixel-check` |'
expect routes#aSkillDirectoryWithoutItsSkillFileIsReported 1 "$tmp/skill-nofile" ".pi/skills/pixel-check" "a skill directory without a SKILL.md is reported"

add_skill "$tmp/skill-prefix" pixel-check-extra
write_agents "$tmp/skill-prefix" '| phone UI | `UI.md` §1 "Cross-variant UI parity" |
| a screenshot look | `.pi/skills/pixel-check` |'
expect routes#aSkillNameThatOnlyPrefixesAnExistingOneIsReported 1 "$tmp/skill-prefix" ".pi/skills/pixel-check" "a skill name that only prefixes an existing one is reported"

# a reference a guideline body writes is resolved the same way the table's rows are
write_body() { # $1 = directory, $2 = body text of guidelines/Build.md
  cat > "$1/guidelines/Build.md" <<FIXTURE
# Build Specification

## 99. Unused

$2
FIXTURE
}

write_body "$tmp/body-ambiguous" 'The renderer rules live in `UI.md` §14.'
write_agents "$tmp/body-ambiguous" '| phone UI | `UI.md` §1 "Cross-variant UI parity" |'
expect_all routes#aBareDuplicatedNumberInAGuidelineBodyIsReported 1 "$tmp/body-ambiguous" \
  "a bare duplicated number in a guideline body is reported as ambiguous" "file=guidelines/Build.md" "ambiguous"

write_body "$tmp/body-text" 'The renderer rules live in `UI.md` §14 "Android Auto renderer".'
write_agents "$tmp/body-text" '| phone UI | `UI.md` §1 "Cross-variant UI parity" |'
expect routes#aHeadingTextInAGuidelineBodyResolvesItsNumber 0 "$tmp/body-text" "all 6 section references resolve" "a heading text in a guideline body resolves its duplicated number"

write_body "$tmp/body-fenced" 'An example, quoted:

```
The renderer rules live in `UI.md` §14.
```'
write_agents "$tmp/body-fenced" '| phone UI | `UI.md` §1 "Cross-variant UI parity" |'
expect routes#aReferenceInsideAFencedBlockIsNotAReference 0 "$tmp/body-fenced" "all 5 section references resolve" "a reference inside a fenced block is not a reference"

write_body "$tmp/body-dangling" 'The renderer rules live in `UI.md` §42.'
write_agents "$tmp/body-dangling" '| phone UI | `UI.md` §1 "Cross-variant UI parity" |'
expect_all routes#aGuidelineBodyNamingAMissingSectionIsReported 1 "$tmp/body-dangling" \
  "a guideline body naming a section its document lacks is reported" "file=guidelines/Build.md" "UI.md §42"

# an archived change and an open change artifact are history: their references are not resolved
mkdir -p "$tmp/artifacts/openspec/changes/archive/2026-01-01-old" "$tmp/artifacts/openspec/changes/open-thing"
for d in openspec/changes/archive/2026-01-01-old openspec/changes/open-thing; do
  printf 'The renderer rules live in `UI.md` §14.\n' > "$tmp/artifacts/$d/proposal.md"
done
write_agents "$tmp/artifacts" '| phone UI | `UI.md` §1 "Cross-variant UI parity" |'
expect routes#aReferenceInAChangeArtifactIsNotChecked 0 "$tmp/artifacts" "all 5 section references resolve" "a reference in a change artifact is not checked"

# ── the three layers and named rules (`documentation-ownership`, "A guideline document carries one kind of
# text", "A rule is named") ──────────────────────────────────────────────────────────────────────────────
# A concern's normative document holds one named rule; its `.why.md` holds the measurement that proved it. The
# companion is read and resolves its `## §N` anchor against the base document; it is not reported as unrouted.
write_concern() { # $1 dir, $2 rule name
  cat > "$1/guidelines/Build.md" <<FIXTURE
# Build Specification

## 1. The concern

- **Rule: $2.** Refusal path.

## 99. Unused
FIXTURE
}
write_why() { # $1 dir, $2 anchor number, $3 measurement line
  cat > "$1/guidelines/Build.why.md" <<FIXTURE
# Build — why

## §$2 The concern

$3
FIXTURE
}

write_concern "$tmp/comp-ok" "a concern has one normative home"
write_why "$tmp/comp-ok" 1 "Measured 2026-10-31: the statement moved and its number did not."
write_agents "$tmp/comp-ok" '| phone UI | `UI.md` §1 "Cross-variant UI parity" |'
expect_all routes#aCompanionIsReadAndNotRouted 0 "$tmp/comp-ok" \
  "a companion no row names is read and not reported as unrouted" "all "
expect layers#aRuleIsNormativeAndItsRationaleIsACompanion 0 "$tmp/comp-ok" "all " \
  "a fixture concern whose normative document holds a named rule and whose companion holds the measurement resolves"

write_concern "$tmp/comp-anchor" "a concern has one normative home"
write_why "$tmp/comp-anchor" 9 "the anchor names a section the base document does not carry"
write_agents "$tmp/comp-anchor" '| phone UI | `UI.md` §1 "Cross-variant UI parity" |'
expect_all layers#aCompanionAnchorThatNoSectionCarriesIsReported 1 "$tmp/comp-anchor" \
  "a companion anchor that names no section of its base document is reported" "guidelines/Build.why.md" "§9"

# The base document drops the section while the companion still anchors to it.
printf '# Build Specification\n\n## 99. Unused\n' > "$tmp/comp-dropped/guidelines/Build.md"
write_why "$tmp/comp-dropped" 1 "the base document no longer carries the section this anchors to"
write_agents "$tmp/comp-dropped" '| phone UI | `UI.md` §1 "Cross-variant UI parity" |'
expect_all layers#aDroppedSectionIsReportedInItsCompanion 1 "$tmp/comp-dropped" \
  "a companion whose base document dropped its section is reported" "guidelines/Build.why.md" "§1"

# One document states a rule name in two sections — the duplicate the "one normative home" rule forbids.
cat > "$tmp/ruledup-onedoc/guidelines/Build.md" <<'FIXTURE'
# Build Specification

## 1. The concern

- **Rule: a concern has one normative home.** First statement.

## 2. The same name again

- **Rule: a concern has one normative home.** Second statement in the same document.

## 99. Unused
FIXTURE
write_agents "$tmp/ruledup-onedoc" '| phone UI | `UI.md` §1 "Cross-variant UI parity" |'
expect_all rules#aNameTwiceInOneDocumentIsReported 1 "$tmp/ruledup-onedoc" \
  "one document stating a rule name twice is reported" "guidelines/Build.md" "stated more than once in one document"

# Two documents state one rule name.
cat > "$tmp/ruledup-twodoc/guidelines/Build.md" <<'FIXTURE'
# Build Specification

## 1. The concern

- **Rule: a shared name.** First.

## 99. Unused
FIXTURE
cat > "$tmp/ruledup-twodoc/guidelines/Design.md" <<'FIXTURE'
# Design Specification

## 1. Another concern

- **Rule: a shared name.** Second.

## 99. Unused
FIXTURE
write_agents "$tmp/ruledup-twodoc" '| phone UI | `UI.md` §1 "Cross-variant UI parity" |'
expect_all rules#aNameTwoDocumentsStateIsReported 1 "$tmp/ruledup-twodoc" \
  "two documents stating one rule name are reported" "stated in more than one document" "Build" "Design"

# A reference resolves to a named rule.
cat > "$tmp/ruleref-ok/guidelines/Build.md" <<'FIXTURE'
# Build Specification

## 1. The concern

- **Rule: a referenced rule.** Statement.

## 99. Unused
FIXTURE
cat > "$tmp/ruleref-ok/guidelines/Design.md" <<'FIXTURE'
# Design Specification

## 1. Another concern

See rule Build.md §1 "a referenced rule." — a reference, not a restatement.

## 99. Unused
FIXTURE
write_agents "$tmp/ruleref-ok" '| phone UI | `UI.md` §1 "Cross-variant UI parity" |'
expect rules#aReferenceResolvesToTheNamedRule 0 "$tmp/ruleref-ok" "all " "a rule reference that resolves passes"

# A reference whose name the section no longer carries is reported with the names it does carry.
cat > "$tmp/ruleref-stale/guidelines/Build.md" <<'FIXTURE'
# Build Specification

## 1. The concern

- **Rule: the renamed rule.** Statement.

## 99. Unused
FIXTURE
cat > "$tmp/ruleref-stale/guidelines/Design.md" <<'FIXTURE'
# Design Specification

## 1. Another concern

See rule Build.md §1 "the old name." — a stale reference.

## 99. Unused
FIXTURE
write_agents "$tmp/ruleref-stale" '| phone UI | `UI.md` §1 "Cross-variant UI parity" |'
expect_all rules#aStaleRuleReferenceIsReported 1 "$tmp/ruleref-stale" \
  "a reference whose name the section does not carry is reported with the names it carries" "guidelines/Design.md" "carries no rule named" "the renamed rule."

# A rule written `**Rule:**` with no name is refused with its file and line, instead of being skipped in
# silence by an extraction that asked only for `**Rule: `.
cat > "$tmp/rule-empty/guidelines/Build.md" <<'FIXTURE'
# Build Specification

## 1. The concern

- **Rule:** the statement whose rule carries no name.

## 99. Unused
FIXTURE
write_agents "$tmp/rule-empty" '| phone UI | `UI.md` §1 "Cross-variant UI parity" |'
expect_all rules#aRuleWithNoNameIsReported 1 "$tmp/rule-empty" \
  "a rule with no name is reported with its file and line" "guidelines/Build.md" "line 5" "**Rule:**"

# ── this project's own tree ────────────────────────────────────────────────────────────────────────────────
# The three cases `documentation-ownership` names for the cost of a routed read — "A section carrying more than
# one question is split" (`routed-read#bound`), "The route names the sub-section" (`routed-read#route`) and
# "A split section keeps its number and its citations" (`routed-read#numbers`). The first two claim something
# about every routed section of this repository, so they read its `AGENTS.md` and `guidelines/*.md` instead of a
# fixture; `#numbers` reads a fixture. None of them writes anything.

# The routed pairs the table makes, derived here independently of the check: a `Doc.md` mention in backticks names
# the document, and every `§<number>` after it on the same row is one of its sections.
table_pairs() { # $1 = the Documentation Map region
  awk '
    {
      n = split($0, tok, "`"); doc = ""
      for (i = 1; i <= n; i++) {
        t = tok[i]
        if (t ~ /^[A-Za-z0-9_-]+\.md$/) { doc = t; sub(/\.md$/, "", doc); continue }
        while (match(t, /§[0-9]+[a-z]?/)) {
          if (doc != "") { ref = substr(t, RSTART, RLENGTH); sub(/^§/, "", ref); print doc "\t" ref }
          t = substr(t, RSTART + RLENGTH)
        }
      }
    }' "$1" | sort -u
}

# Every section of a document as `document<TAB>number<TAB>first line<TAB>last line`: from its `## ` heading
# through the line before the next one, a heading inside a fenced block being an example.
section_spans() { # $1 = document path, $2 = document name
  # a companion (`*.why.md`, `*.recipes.md`) is read, not a document a route names, so this ruler's count
  # must not carry its `## §N` anchors either: skip it, matching `check-doc-routes.sh`'s document set
  case "$(basename "$1" .md)" in *.why|*.recipes) return 0 ;; esac
  LC_ALL=C awk -v name="$2" '
    /^```/ { fence = !fence }
    !fence && /^## / {
      if (have) print name "\t" num "\t" first "\t" NR - 1
      h = substr($0, 4); num = ""
      if (h ~ /^[0-9]+[a-z]?\./) { p = index(h, "."); num = substr(h, 1, p - 1) }
      first = NR; have = 1; next
    }
    END { if (have) print name "\t" num "\t" first "\t" NR }' "$1"
}

tree_region="$tmp/tree-region"
awk '/^## Documentation Map$/{f=1;next} f && /^## /{exit} f{print}' "$root/AGENTS.md" > "$tree_region"
: > "$tmp/tree-spans.txt"
for f in "$root"/guidelines/*.md; do
  [ -f "$f" ] || continue
  section_spans "$f" "$(basename "$f" .md)" >> "$tmp/tree-spans.txt"
done
table_pairs "$tree_region" > "$tmp/tree-pairs.txt"

echo_case() { # $1 id, $2 body-function, $3 description — runs one case under the filter
  [ -z "$case_filter" ] || [ "$case_filter" = "$1" ] || return 0
  if "$2"; then
    echo "PASS  $1 — $3"
    pass=$((pass + 1))
  else
    echo "FAIL  $1 — $3"
    printf '        %s\n' "${CASE_WHY:-no reason recorded}"
    failures=$((failures + 1))
  fi
}

case_routed_read_bound() {
  local sizes_out rc reported first_bad doc first last f
  sizes_out=$("$check" --root "$root" --sizes 2>&1); rc=$?
  printf '%s\n' "$sizes_out" | grep '^check-doc-routes: section-cost ' \
    | sed 's/^check-doc-routes: section-cost //' > "$tmp/tree-costs.txt"
  if [ ! -s "$tmp/tree-costs.txt" ]; then
    CASE_WHY="the size report is empty — the check exited $rc: $(printf '%s' "$sizes_out" | head -3)"
    return 1
  fi
  # the report is the tree's: one cost line per section of every document, and the ruler's own count for each
  reported=$(wc -l < "$tmp/tree-costs.txt" | tr -d ' ')
  if [ "$reported" != "$(wc -l < "$tmp/tree-spans.txt" | tr -d ' ')" ]; then
    CASE_WHY="the report carries $reported section(s), the documents carry $(wc -l < "$tmp/tree-spans.txt" | tr -d ' ')"
    return 1
  fi
  # every reported cost is the ruler's own count, compared per document as a list so a document that numbers
  # two headings alike (`MapRendering.md` carries two `## 14.`) is compared as the two sections it has, and an
  # unnumbered heading (`## Known Pitfalls`) keeps its bytes in the list although no pair can route it
  awk '{ d = $1; sub(/\.md$/, "", d); b = $3; s = $7; n = $2
         sub(/^§/, "", n)
         if (n == $2) next
         print d "\t" n "\t" b "\t" s }' "$tmp/tree-costs.txt" > "$tmp/tree-costs.tsv"
  local doc first last f first_bad=""
  for f in "$root"/guidelines/*.md; do
    [ -f "$f" ] || continue
    doc=$(basename "$f" .md)
    : > "$tmp/expected-bytes.txt"
    while IFS=$'\t' read -r first last; do
      sed -n "${first},${last}p" "$f" | LC_ALL=C wc -c | tr -d ' ' >> "$tmp/expected-bytes.txt"
    done < <(awk -F'\t' -v d="$doc" '$1 == d { print $3 "\t" $4 }' "$tmp/tree-spans.txt")
    awk -v d="$doc.md" '$1 == d { print $3 }' "$tmp/tree-costs.txt" | sort -n > "$tmp/reported-bytes.txt"
    if [ -z "$first_bad" ] && \
       [ "$(sort -n "$tmp/expected-bytes.txt" | paste -sd' ' -)" != "$(paste -sd' ' - < "$tmp/reported-bytes.txt")" ]; then
      first_bad="$doc.md — reported [$(paste -sd' ' - < "$tmp/reported-bytes.txt")] against the ruler's [$(sort -n "$tmp/expected-bytes.txt" | paste -sd' ' -)]"
    fi
  done
  if [ -n "$first_bad" ]; then
    CASE_WHY="a printed cost is not the requirement's ruler count: $first_bad"
    return 1
  fi
  # and it marks exactly the pairs the table routes
  awk -F'\t' '$4 == "routed" { print $1 "\t" $2 }' "$tmp/tree-costs.tsv" | sort -u > "$tmp/tree-routed.txt"
  if [ -n "$(comm -23 "$tmp/tree-pairs.txt" "$tmp/tree-routed.txt")" ]; then
    CASE_WHY="the table routes a pair the report does not mark as routed: $(comm -23 "$tmp/tree-pairs.txt" "$tmp/tree-routed.txt" | head -3 | paste -sd' ' -)"
    return 1
  fi
  if [ -n "$(comm -13 "$tmp/tree-pairs.txt" "$tmp/tree-routed.txt")" ]; then
    CASE_WHY="the report marks a pair as routed that the table does not name: $(comm -13 "$tmp/tree-pairs.txt" "$tmp/tree-routed.txt" | head -3 | paste -sd' ' -)"
    return 1
  fi
  ROUTED_READ_NOTE="$reported section(s) printed, $(wc -l < "$tmp/tree-routed.txt" | tr -d ' ') routed pair(s), each equal to the ruler's own sed count"
  echo "        $ROUTED_READ_NOTE" >&2
  return 0
}

case_routed_read_route() {
  local out rc d fit fit_rc priced
  out=$("$check" --root "$root" 2>&1); rc=$?
  if [ "$rc" != 0 ]; then
    CASE_WHY="the project's own table does not pass the clause: $out"
    return 1
  fi
  # the claim is per row and per document, so a lettered sub-section of a document that is not `Build.md` is
  # priced as well — and a row that names one and prices nothing is the failure the case is for
  d="$tmp/route-costless"; mk_fixture "$d"
  cat > "$d/guidelines/UI.md" <<'FIXTURE'
# UI Specification

## 1. Cross-variant UI parity (general rule)

## 1a. Parity, measured

A sub-section a split created.
FIXTURE
  write_agents "$d" '| phone UI | `UI.md` §1a "Parity, measured" |'
  fit=$("$check" --root "$d" 2>&1); fit_rc=$?
  if [ "$fit_rc" != 1 ] || ! printf '%s' "$fit" | grep -qF 'states no cost'; then
    CASE_WHY="a fixture row naming a lettered sub-section and pricing nothing was not reported: exit $fit_rc: $fit"
    return 1
  fi
  # the price it states is the measurement, because the accuracy clause convicts a figure that is not one: this
  # case's claim is that a row states *a* cost, and the figure it states must still be the section's own
  priced=$(cost_kb "$(section_bytes "$d/guidelines/UI.md" 1a '')")
  write_agents "$d" "| phone UI | \`UI.md\` §1a \"Parity, measured\" — $priced kB |"
  fit=$("$check" --root "$d" 2>&1); fit_rc=$?
  if [ "$fit_rc" != 0 ]; then
    CASE_WHY="a fixture row naming a lettered sub-section and pricing it did not pass: exit $fit_rc: $fit"
    return 1
  fi
  return 0
}

case_routed_read_numbers() {
  local d out rc
  d="$tmp/numbers"; mk_fixture "$d"
  write_numbers_fixture() {
    cat > "$d/guidelines/UI.md" <<'FIXTURE'
# UI Specification

## 1. Cross-variant UI parity (general rule)

The split's index: `UI.md` §1a "Parity, measured" and `UI.md` §1b "Handoff, measured"; this section stays
`UI.md` §1 "Cross-variant UI parity".

## 1a. Parity, measured

A sub-section a split created.

## 1b. Handoff, measured

A second sub-section the split created.
FIXTURE
  }
  write_numbers_fixture
  write_agents "$d" '| parity, measured | `UI.md` §1a "Parity, measured" — 0.1 kB |
| handoff, measured | `UI.md` §1b "Handoff, measured" — 0.1 kB |'
  out=$("$check" --root "$d" 2>&1); rc=$?
  if [ "$rc" != 0 ]; then
    CASE_WHY="a split that keeps its number and its citations did not pass the route check: exit $rc: $out"
    return 1
  fi
  # the parent number is what the split keeps: without its heading the same citations no longer resolve
  grep -vF '## 1. Cross-variant UI parity (general rule)' "$d/guidelines/UI.md" > "$d/UI.md.next"
  mv "$d/UI.md.next" "$d/guidelines/UI.md"
  out=$("$check" --root "$d" 2>&1); rc=$?
  if [ "$rc" != 1 ] || ! printf '%s' "$out" | grep -qF 'UI.md §1'; then
    CASE_WHY="a split whose parent number is no longer a heading was not reported: exit $rc: $out"
    return 1
  fi
  # and a citation of a sub-section resolves only while that sub-section is still a heading
  write_numbers_fixture
  grep -vF '## 1a. Parity, measured' "$d/guidelines/UI.md" > "$d/UI.md.next"
  mv "$d/UI.md.next" "$d/guidelines/UI.md"
  out=$("$check" --root "$d" 2>&1); rc=$?
  if [ "$rc" != 1 ] || ! printf '%s' "$out" | grep -qF 'UI.md §1a'; then
    CASE_WHY="a citation of a sub-section that is no longer a heading was not reported: exit $rc: $out"
    return 1
  fi
  return 0
}

echo_case routed-read#numbers case_routed_read_numbers \
  "a split keeps its parent number, every citation resolves, and the route check accepts the document set"

# A fixture section's cost under the requirement's ruler — the byte count of its lines from the `## ` heading
# through the line before the next one — and the figure a row states for it, both taken here independently of
# the check. A fixture with no section after the measured one is measured to the end of the document.
section_bytes() { # $1 = document, $2 = section number, $3 = the number of the section after it (empty for the last)
  if [ -n "$3" ]; then
    sed -n "/^## $2\\./,/^## $3\\./p" "$1" | sed '$d' | LC_ALL=C wc -c | tr -d ' '
  else
    sed -n "/^## $2\\./,\$p" "$1" | LC_ALL=C wc -c | tr -d ' '
  fi
}

cost_kb() { # $1 = bytes
  LC_ALL=C awk -v b="$1" 'BEGIN { printf "%.1f", b / 1000 }'
}

filler() { # $1 = how many 78-byte lines to write, so a fixture section costs what a case states it costs
  local i=0
  while [ "$i" -lt "$1" ]; do
    printf 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n'
    i=$((i + 1))
  done
}

write_lettered() { # $1 = directory, $2 = what the priced row states after `§1a "Parity, measured"`
  mk_fixture "$1"
  {
    printf '# UI Specification\n\n## 1. Cross-variant UI parity (general rule)\n\n'
    printf '## 1a. Parity, measured\n\nThe measured half of the parity rule: '
    filler 12
    printf '\n## 1b. Parity, unmeasured\n\nThe half with no case behind it: '
    filler 38
  } > "$1/guidelines/UI.md"
  write_agents "$1" "| phone UI | \`UI.md\` §1a \"Parity, measured\"$2 |"
}

# `routed-read#figure` and `routed-read#range`: a figure and a range a row states are the measurement the ruler
# takes, and a row that writes a figure as a past measurement is not compared (issue #712).
case_routed_read_figure() {
  local d bytes kb out rc
  d="$tmp/figure"
  write_lettered "$d" ' — 0.1 kB'
  bytes=$(section_bytes "$d/guidelines/UI.md" 1a 1b); kb=$(cost_kb "$bytes")
  out=$("$check" --root "$d" 2>&1); rc=$?
  if [ "$rc" != 1 ] || ! printf '%s' "$out" | grep -qF '0.1 kB for UI.md §1a'; then
    CASE_WHY="a row stating 0.1 kB for a $bytes-byte section was not reported: exit $rc: $out"
    return 1
  fi
  if ! printf '%s' "$out" | grep -qF "measures $kb kB"; then
    CASE_WHY="the report does not name the measured $kb kB beside the stated 0.1 kB: $out"
    return 1
  fi
  write_lettered "$d" " — $kb kB"
  out=$("$check" --root "$d" 2>&1); rc=$?
  if [ "$rc" != 0 ]; then
    CASE_WHY="a row stating the section's own $kb kB ($bytes bytes) did not pass: exit $rc: $out"
    return 1
  fi
  out=$("$check" --root "$root" 2>&1); rc=$?
  if [ "$rc" != 0 ]; then
    CASE_WHY="the project's own table states a figure a section does not measure: exit $rc: $out"
    return 1
  fi
  printf '        a row stating 0.1 kB for a %s-byte (%s kB) section is reported with both numbers, the same row stating %s kB passes, and the project\342\200\231s own figures are the measurements\n' \
    "$bytes" "$kb" "$kb" >&2
  return 0
}

case_routed_read_range() {
  local d a b out rc
  d="$tmp/range"
  write_lettered "$d" ', §1b "Parity, unmeasured" — 1.2–2.0 kB each'
  a=$(cost_kb "$(section_bytes "$d/guidelines/UI.md" 1a 1b)")
  b=$(cost_kb "$(section_bytes "$d/guidelines/UI.md" 1b '')")
  out=$("$check" --root "$d" 2>&1); rc=$?
  if [ "$rc" != 1 ] || ! printf '%s' "$out" | grep -qF 'states the range'; then
    CASE_WHY="a row stating the range 1.2–2.0 kB for sections measuring $a–$b kB was not reported: exit $rc: $out"
    return 1
  fi
  if ! printf '%s' "$out" | grep -qF "measure $a-$b kB"; then
    CASE_WHY="the report does not name the measured span $a-$b kB: $out"
    return 1
  fi
  write_lettered "$d" ", §1b \"Parity, unmeasured\" — $a–$b kB each, against 23.2 kB for §1 before the split"
  out=$("$check" --root "$d" 2>&1); rc=$?
  if [ "$rc" != 0 ]; then
    CASE_WHY="a row stating its sections' span $a–$b kB did not pass, or its past measurement was compared: exit $rc: $out"
    return 1
  fi
  printf '        a row stating 1.2–2.0 kB for sections measuring %s–%s kB is reported, the same row stating %s–%s kB passes, and the 23.2 kB it writes as a past measurement is not compared\n' \
    "$a" "$b" "$a" "$b" >&2
  return 0
}

echo_case routed-read#bound case_routed_read_bound \
  "every section of every routed document is printed with its cost, each equal to the requirement's ruler, and the pairs the table routes are the pairs marked routed"
echo_case routed-read#route case_routed_read_route \
  "a row that routes a lettered sub-section of any document states its cost, here and over the project's own table"
echo_case routed-read#figure case_routed_read_figure \
  "a figure a row writes after a lettered reference is that section's measurement, here and over the project's own table"
echo_case routed-read#range case_routed_read_range \
  "a range a row states is the span of the lettered sections it names, and a past measurement in the same row is not compared"

# The two cases above read the tree, so "the repository's documents are untouched" is asserted after them too: a
# case that measured a document by editing it would pass and leave the tree changed.
agents_after=$(md5sum "$root/AGENTS.md" | awk '{print $1}')

guidelines_after=$(cat "$root"/guidelines/*.md | md5sum | awk '{print $1}')

if [ -n "$case_filter" ]; then
  : # the tree-untouched check is not one of the selectable cases
elif [ "$agents_before" = "$agents_after" ] && [ "$guidelines_before" = "$guidelines_after" ]; then
  echo "PASS  routes#theRepositorysAgentsFileIsUntouched — the repository's AGENTS.md and its guidelines are untouched by every case"
  pass=$((pass + 1))
else
  echo "FAIL  routes#theRepositorysAgentsFileIsUntouched: a document a case reads changed (AGENTS.md $agents_before -> $agents_after, guidelines ${guidelines_before:-?} -> ${guidelines_after:-?})"
  failures=$((failures + 1))
fi

if [ -n "$case_filter" ] && [ "$pass" = 0 ] && [ "$failures" = 0 ]; then
  echo "check-doc-routes-selftest: no case with the id '$case_filter'" >&2
  exit 2
fi

if [ "$failures" -ne 0 ]; then
  echo "check-doc-routes-selftest: $failures of $((pass + failures)) cases failed"
  exit 1
fi
echo "check-doc-routes-selftest: all $pass cases passed (throwaway fixtures and this tree, no Gradle, no device, no network)"
exit 0
