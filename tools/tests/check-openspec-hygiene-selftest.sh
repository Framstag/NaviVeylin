#!/usr/bin/env bash
# The cases for `tools/check-openspec-hygiene.sh` (issue #45, TODO.md §17): each case builds a throwaway
# OpenSpec tree, breaks exactly one of the artifact classes, and runs the real checker over it — so the
# script the pipeline runs is exercised against the trees the CLI accepts silently.
#
# The classes and why nothing else reports them are the script's own header. What this file adds is
# the red half: a checker whose classes are never violated is a checker nobody has seen fail.
#
# Three case ids are the ones the shipped requirement "A capability spec states what the capability is for"
# already names — `artifact-hygiene#theArchivePlaceholderIsReported`, `#aWrittenPurposePasses` and
# `#aNestedCapabilitySpecIsReported` — and until this file existed no case answered to any of them. The three the
# issue-field requirement names (`#theIssueFieldIsReported`, `#twoOpenChangesNamingOneIssueAreRefused`,
# `#aChangeNamingNoIssueIsRefused`) arrive with it, in the two passes at the end.
#
# Usage: check-openspec-hygiene-selftest.sh [--root <dir>] [--case <name>]
#        --case <name>  run one case by its id (`artifact-hygiene#…`, or the bare suffix) — what a
#                       revert-check names, so one mutation costs one run instead of the whole set
# Exit: 0 every case passed, 1 a case failed, 2 usage/environment.
set -uo pipefail

root=""
only_case=""
while [ $# -gt 0 ]; do
  case "$1" in
    --root) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; root="${2:-}"; shift 2 ;;
    --case) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; only_case="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,18p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "$(basename -- "$0" .sh): unknown argument '$1'" >&2; exit 2 ;;
  esac
done
[ -n "$root" ] || root=$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel 2>/dev/null) || root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
checker="$root/tools/check-openspec-hygiene.sh"
[ -f "$checker" ] || { echo "check-openspec-hygiene-selftest: no $checker" >&2; exit 2; }

# selected <case id> — the case runs, either because nothing was selected or because it was.
selected() {
  [ -z "$only_case" ] && return 0
  [ "$only_case" = "$1" ] || [ "artifact-hygiene#${only_case}" = "$1" ]
}

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# clean_tree <dir> — the smallest OpenSpec tree the six classes accept: one open change whose tasks
# carry `- [x]` markers, a config whose guidance entries are quoted and whose `context:` block is the pointer
# the sixth class reads, and a capability spec whose Purpose is a sentence its author wrote.
clean_tree() {
  local d="$1"
  mkdir -p "$d/openspec/changes/demo" "$d/openspec/specs/demo"
  cat > "$d/openspec/changes/demo/tasks.md" <<'EOF'
## 1. The first section

- [x] 1.1 A done task
- [ ] 1.2 An open task
EOF
  # The issue field the fifth class reads: a change whose finding is owed by no tracker item declares `none`, which
  # is a declaration rather than an absence — so the clean tree is a tree the fifth pass reports and does not refuse.
  # The key carries the CLI's `x-` prefix, its own form for tool metadata: a bare key is a WARNING that `--strict`
  # counts as a failure, and this field is read by the check rather than by the CLI.
  cat > "$d/openspec/changes/demo/.openspec.yaml" <<'EOF'
schema: spec-driven
created: 2026-01-01
x-issue: none
EOF
  cat > "$d/openspec/config.yaml" <<'EOF'
schema: spec-driven
context: |
  Project facts are `AGENTS.md`, loaded for every session: read it, and read the section of
  `guidelines/<concern>.md` its "Documentation Map" routes your topic to. This block is a pointer.
operations:
  apply:
    guidance:
      - "A quoted entry that contains ': ' on purpose"
  archive:
    guidance:
      - "Another quoted entry"
EOF
  mkdir -p "$d/openspec/changes/demo/evidence"
  cat > "$d/openspec/changes/demo/evidence/relocation.txt" <<'EOF'
B1  Backlog maintenance — the tracker paragraph.
    owner: guidelines/Backlog.md (the owning section states it)

The refused form, quoted rather than made — a fence is not a citation:

```
    owner: .pi/skills/*/SKILL.md (the skill's own contract)
```
EOF
  cat > "$d/openspec/specs/demo/spec.md" <<'EOF'
# demo Specification

## Purpose

What this capability is for, written by its author.

## Requirements

### Requirement: A requirement

The capability SHALL do the thing.

#### Scenario: The thing happens

- **WHEN** the thing is asked for
- **THEN** it happens
EOF
}

# placeholder_spec <file> — a spec whose `## Purpose` is the sentence `openspec archive` writes, on line 4
# so a case can assert the `path:line` the checker prints.
placeholder_spec() {
  cat > "$1" <<'EOF'
# demo Specification

## Purpose
TBD - created by archiving change demo. Update Purpose after archive.

## Requirements

### Requirement: A requirement

The capability SHALL do the thing.
EOF
}

# run <name> <tree> — runs the checker over the tree; output in $tmp/<name>.out, exit status in CHECK_RC.
CHECK_RC=0
run() {
  local name="$1" tree="$2"
  bash "$checker" --root "$tree" > "$tmp/$name.out" 2>&1
  CHECK_RC=$?
}

total=0
failed=0
FAILED_CASES=""

# fail_case <name> <reason> — prints the case's reason and the checker's own output, and counts it failed.
fail_case() {
  local name="$1" reason="$2"
  echo "FAIL $name: $reason"
  printf '%s\n' "$(cat "$tmp/$name.out" 2>/dev/null | tail -8)" | sed 's/^/       /'
  failed=$((failed + 1))
  FAILED_CASES="$FAILED_CASES $name"
}

# ── case 1 ────────────────────────────────────────────────────────────────────────────────────────────────
# `artifact-hygiene#aWrittenPurposePasses`: the clean tree, and the sentence the requirement names. A red
# case below therefore means its own finding, not a checker that reports everything.
case_a=aWrittenPurposePasses
if selected "artifact-hygiene#$case_a"; then
total=$((total + 1))
tree="$tmp/tree-clean"; clean_tree "$tree"; run "$case_a" "$tree"
if [ "$CHECK_RC" != 0 ]; then
  fail_case "artifact-hygiene#$case_a" "the clean tree was reported (exit $CHECK_RC)"
elif ! grep -q 'Every capability spec states its Purpose\.' "$tmp/$case_a.out"; then
  fail_case "artifact-hygiene#$case_a" "the clean tree passed without the verdict sentence the requirement names"
fi
fi

# ── case 2 ────────────────────────────────────────────────────────────────────────────────────────────────
# The class issue #45 was filed from: `1. [x] …` items, which the parser reads as no tasks at all. The
# fixture keeps one well-formed marker beside the bare ones, so only this class can redden the case.
case_b=bareNumberedMarkersAreReported
if selected "artifact-hygiene#$case_b"; then
total=$((total + 1))
tree="$tmp/tree-bare"; clean_tree "$tree"
cat > "$tree/openspec/changes/demo/tasks.md" <<'EOF'
## 1. The first section

- [x] 1.1 A well-formed task
1. [x] A bare numbered task
2. [ ] Another one
EOF
run "$case_b" "$tree"
if [ "$CHECK_RC" = 0 ]; then
  fail_case "artifact-hygiene#$case_b" "bare numbered markers passed (exit 0)"
elif ! grep -q 'bare numbered task marker' "$tmp/$case_b.out"; then
  fail_case "artifact-hygiene#$case_b" "the finding does not name the refused form"
fi
fi

# ── case 3 ────────────────────────────────────────────────────────────────────────────────────────────────
# The same class one shape over: no marker at all, which the parser also reads as zero tasks.
case_c=aTasksFileWithNoMarkersIsReported
if selected "artifact-hygiene#$case_c"; then
total=$((total + 1))
tree="$tmp/tree-nomarkers"; clean_tree "$tree"
cat > "$tree/openspec/changes/demo/tasks.md" <<'EOF'
## 1. The first section

1.1 A task with no marker at all
EOF
run "$case_c" "$tree"
if [ "$CHECK_RC" = 0 ]; then
  fail_case "artifact-hygiene#$case_c" "a tasks.md with no marker passed (exit 0)"
elif ! grep -q "no '- \[x\]' / '- \[ \]' task markers" "$tmp/$case_c.out"; then
  fail_case "artifact-hygiene#$case_c" "the finding does not name the missing markers"
fi
fi

# ── case 4 ────────────────────────────────────────────────────────────────────────────────────────────────
# The guidance entry that contains ': ' and is not quoted: the CLI drops the whole operation's guidance.
case_d=unquotedGuidanceIsReported
if selected "artifact-hygiene#$case_d"; then
total=$((total + 1))
tree="$tmp/tree-guidance"; clean_tree "$tree"
cat > "$tree/openspec/config.yaml" <<'EOF'
schema: spec-driven
operations:
  apply:
    guidance:
      - A bare entry that contains ': ' and parses as a mapping
EOF
run "$case_d" "$tree"
if [ "$CHECK_RC" = 0 ]; then
  fail_case "artifact-hygiene#$case_d" "an unquoted guidance entry passed (exit 0)"
elif ! grep -q "unquoted guidance entry containing" "$tmp/$case_d.out"; then
  fail_case "artifact-hygiene#$case_d" "the finding does not name the refused form"
fi
fi

# ── case 5 ────────────────────────────────────────────────────────────────────────────────────────────────
# `artifact-hygiene#theArchivePlaceholderIsReported`: the Purpose `openspec archive` writes for a capability
# it creates, which no delta can repair. The finding names the file and the line.
case_e=theArchivePlaceholderIsReported
if selected "artifact-hygiene#$case_e"; then
total=$((total + 1))
tree="$tmp/tree-purpose"; clean_tree "$tree"
placeholder_spec "$tree/openspec/specs/demo/spec.md"
run "$case_e" "$tree"
if [ "$CHECK_RC" = 0 ]; then
  fail_case "artifact-hygiene#$case_e" "the archive placeholder Purpose passed (exit 0)"
elif ! grep -q "archive placeholder" "$tmp/$case_e.out"; then
  fail_case "artifact-hygiene#$case_e" "the finding does not name the placeholder"
elif ! grep -q 'openspec/specs/demo/spec.md:4' "$tmp/$case_e.out"; then
  fail_case "artifact-hygiene#$case_e" "the finding does not name the file and line it read"
fi
fi

# ── case 6 ────────────────────────────────────────────────────────────────────────────────────────────────
# `artifact-hygiene#aNestedCapabilitySpecIsReported`: the same pass over a capability one level deeper, as
# the requirement's own example spells it (`openspec/specs/share/location-receiving/spec.md:4`). A flat
# glob over `openspec/specs/*/spec.md` would miss it, which is why the checker uses `find`.
case_f=aNestedCapabilitySpecIsReported
if selected "artifact-hygiene#$case_f"; then
total=$((total + 1))
tree="$tmp/tree-nested"; clean_tree "$tree"
mkdir -p "$tree/openspec/specs/share/location-receiving"
placeholder_spec "$tree/openspec/specs/share/location-receiving/spec.md"
run "$case_f" "$tree"
if [ "$CHECK_RC" = 0 ]; then
  fail_case "artifact-hygiene#$case_f" "a nested spec's placeholder Purpose passed (exit 0)"
elif ! grep -q 'openspec/specs/share/location-receiving/spec.md:4' "$tmp/$case_f.out"; then
  fail_case "artifact-hygiene#$case_f" "the finding does not name the nested spec and its line"
fi
fi

# ── case 7 ────────────────────────────────────────────
# `artifact-hygiene#anOwnerCitedAsADirectoryListingIsReported` (issue #732): a relocation record that proves its
# claim with `owner: .pi/skills/*/SKILL.md` names a directory, so its reader cannot tell which owners the removed
# block named — 35 paths were cited for a table that named eight skills. The same lines under `archive/` are
# history, and the clean tree carries the offending line inside a fence, where it is a quotation: the case carries
# all three, so a later guard cannot start inspecting history and neither the fence nor the open half can pass by
# reading nothing.
case_g=anOwnerCitedAsADirectoryListingIsReported
if selected "artifact-hygiene#$case_g"; then
total=$((total + 1))
tree="$tmp/tree-owner"; clean_tree "$tree"
mkdir -p "$tree/openspec/changes/archive/2026-01-01-demo/evidence"
cat > "$tree/openspec/changes/demo/evidence/relocation.txt" <<'EOF'
B2  Backlog maintenance — the nine-skill table (a skill's job).
    owner: .pi/skills/*/SKILL.md (the skill's own contract)
EOF
cp "$tree/openspec/changes/demo/evidence/relocation.txt" \
   "$tree/openspec/changes/archive/2026-01-01-demo/evidence/relocation.txt"
run "$case_g" "$tree"
if [ "$CHECK_RC" = 0 ]; then
  fail_case "artifact-hygiene#$case_g" "an owner cited as a directory listing passed (exit 0)"
elif ! grep -q 'directory listing' "$tmp/$case_g.out"; then
  fail_case "artifact-hygiene#$case_g" "the finding does not name the refused form"
elif ! grep -q 'openspec/changes/demo/evidence/relocation.txt:2' "$tmp/$case_g.out"; then
  fail_case "artifact-hygiene#$case_g" "the finding does not name the open record's file and its line"
elif grep -q 'archive/' "$tmp/$case_g.out"; then
  fail_case "artifact-hygiene#$case_g" "the archived record was inspected, and it is history"
fi
fi

# ── case 8 ────────────────────────────────────────────────────────────────────────────────────────────────
# `artifact-hygiene#theIssueFieldIsReported` (issue #931): the field is the one thing read. The tree's change is
# named for its claim and its proposal names a different item, so a pass that read the directory name or the prose
# would report #111 — the field says #928, and #928 is what the check reports.
case_i=theIssueFieldIsReported
if selected "artifact-hygiene#$case_i"; then
total=$((total + 1))
tree="$tmp/tree-issue-field"; clean_tree "$tree"
cat > "$tree/openspec/changes/demo/.openspec.yaml" <<'EOF'
schema: spec-driven
created: 2026-01-01
x-issue: 928
EOF
cat > "$tree/openspec/changes/demo/proposal.md" <<'EOF'
# A case waits on the state the run writes

Filed for #111, which is not the item this change answers.
EOF
run "$case_i" "$tree"
if [ "$CHECK_RC" != 0 ]; then
  fail_case "artifact-hygiene#$case_i" "a tree whose change declares its issue did not pass (exit $CHECK_RC)"
elif ! grep -q 'openspec/changes/demo=#928' "$tmp/$case_i.out"; then
  fail_case "artifact-hygiene#$case_i" "the check does not report the change under the issue its field declares"
elif grep -q '#111' "$tmp/$case_i.out"; then
  fail_case "artifact-hygiene#$case_i" "the check reported the proposal's prose instead of the field"
fi
fi

# ── case 9 ────────────────────────────────────────────────────────────────────────────────────────────────
# `artifact-hygiene#twoOpenChangesNamingOneIssueAreRefused` (issue #931): the collision that is silent until an
# archive exposes it. The pair is refused before either archives, both directories are named, and the same number
# under `archive/` is history — a shipped change and this one may both answer #928, and only the open pair is a
# second session writing the same item.
case_j=twoOpenChangesNamingOneIssueAreRefused
if selected "artifact-hygiene#$case_j"; then
total=$((total + 1))
tree="$tmp/tree-one-issue"; clean_tree "$tree"
for pair in "first 928" "second 928"; do
  set -- $pair
  mkdir -p "$tree/openspec/changes/$1"
  printf 'schema: spec-driven\ncreated: 2026-01-01\nx-issue: %s\n' "$2" > "$tree/openspec/changes/$1/.openspec.yaml"
done
mkdir -p "$tree/openspec/changes/archive/2026-01-01-shipped"
printf 'schema: spec-driven\ncreated: 2025-12-01\nx-issue: 928\n' > "$tree/openspec/changes/archive/2026-01-01-shipped/.openspec.yaml"
run "$case_j" "$tree"
if [ "$CHECK_RC" = 0 ]; then
  fail_case "artifact-hygiene#$case_j" "two open changes naming one issue passed (exit 0)"
elif ! grep -q 'name one issue (#928)' "$tmp/$case_j.out"; then
  fail_case "artifact-hygiene#$case_j" "the finding does not state the collision and the number"
elif ! grep -q 'openspec/changes/first' "$tmp/$case_j.out" || ! grep -q 'openspec/changes/second' "$tmp/$case_j.out"; then
  fail_case "artifact-hygiene#$case_j" "the finding does not name both directories"
elif grep -q 'archive/2026-01-01-shipped' "$tmp/$case_j.out"; then
  fail_case "artifact-hygiene#$case_j" "the archived change was counted, and it is history"
fi
fi

# ── case 10 ───────────────────────────────────────────────────────────────────────────────────────────────
# `artifact-hygiene#aChangeNamingNoIssueIsRefused` (issue #931): the three states of the field. A change with
# neither the field nor an issue-prefixed name is refused; `x-issue: none` is a declaration and passes; and a name
# written before the field existed (`fix-issue-<N>-…`) supplies the number, so a directory from that era is
# reported rather than refused forever.
case_k=aChangeNamingNoIssueIsRefused
if selected "artifact-hygiene#$case_k"; then
total=$((total + 1))
# the refusal: no field, and a name that carries no number
nofield="$tmp/tree-no-issue"; clean_tree "$nofield"
printf 'schema: spec-driven\ncreated: 2026-01-01\n' > "$nofield/openspec/changes/demo/.openspec.yaml"
run "$case_k" "$nofield"
if [ "$CHECK_RC" = 0 ]; then
  fail_case "artifact-hygiene#$case_k" "a change with no issue field and no issue-prefixed name passed (exit 0)"
elif ! grep -q "no 'x-issue:' field" "$tmp/$case_k.out"; then
  fail_case "artifact-hygiene#$case_k" "the finding does not name the missing field"
elif ! grep -q 'openspec/changes/demo' "$tmp/$case_k.out"; then
  fail_case "artifact-hygiene#$case_k" "the finding does not name the directory"
fi
# `x-issue: none` is a declaration: the clean tree carries it and the fifth pass reports it
none_arm="$tmp/tree-issue-none"; clean_tree "$none_arm"
run "$case_k-none" "$none_arm"
if [ "$CHECK_RC" != 0 ]; then
  fail_case "artifact-hygiene#$case_k" "a change declaring 'x-issue: none' was refused (exit $CHECK_RC)"
elif ! grep -q 'openspec/changes/demo=none' "$tmp/$case_k-none.out"; then
  fail_case "artifact-hygiene#$case_k" "the declared 'none' is not reported as the change's declaration"
fi
# a name from before the field: the number it carries is the one read
prefix="$tmp/tree-issue-prefix"; clean_tree "$prefix"
mv "$prefix/openspec/changes/demo" "$prefix/openspec/changes/fix-issue-898-a-case-waits"
printf 'schema: spec-driven\ncreated: 2026-01-01\n' > "$prefix/openspec/changes/fix-issue-898-a-case-waits/.openspec.yaml"
run "$case_k-prefix" "$prefix"
if [ "$CHECK_RC" != 0 ]; then
  fail_case "artifact-hygiene#$case_k" "a 'fix-issue-<N>-' name with no field was refused (exit $CHECK_RC)"
elif ! grep -q 'fix-issue-898-a-case-waits=#898' "$tmp/$case_k-prefix.out"; then
  fail_case "artifact-hygiene#$case_k" "the number in the directory name is not the one reported"
fi
fi

# ── case 11 ───────────────────────────────────────────────────────────────────────────────────────────────
# `artifact-hygiene#aContextBlockThatCopiesTheFactsIsRefused`: the `context:` block is injected verbatim into
# `openspec instructions … --json`, and nothing compares it with the file that owns the facts, so a copy drifts
# in silence (the shipped one priced whole-document reads at figures `AGENTS.md`'s split measurement stopped
# giving). Both arms are the refused shapes — a long copy, and a short one that names no owner — while the
# clean tree carries the pointer that passes.
case_l=aContextBlockThatCopiesTheFactsIsRefused
if selected "artifact-hygiene#$case_l"; then
total=$((total + 1))
# arm 1: a copy long enough to be the facts themselves
copy="$tmp/tree-context-copy"; clean_tree "$copy"
cat > "$copy/openspec/config.yaml" <<'EOF'
schema: spec-driven
context: |
  Project: NaviVeylin — Android navigation app built on libosmscout
  Tech Stack: Kotlin, Java, C++20 (NDK)
  UI: Jetpack Compose + Material 3
  DI: Hilt
  Build: Gradle (Kotlin DSL), AGP 8.7+, CMake 3.22+
  Min SDK: 29, Target SDK: 36
operations:
  apply:
    guidance:
      - "A quoted entry that contains ': ' on purpose"
EOF
run "$case_l-copy" "$copy"
if [ "$CHECK_RC" = 0 ]; then
  fail_case "artifact-hygiene#$case_l" "a copied context block passed (exit 0)"
elif ! grep -q "the 'context:' block is 6 lines" "$tmp/$case_l-copy.out"; then
  fail_case "artifact-hygiene#$case_l" "the finding does not state how many lines the block carries"
fi
# arm 2: short enough, but it names no owner — a reader cannot tell where the facts live
unowned="$tmp/tree-context-unowned"; clean_tree "$unowned"
cat > "$unowned/openspec/config.yaml" <<'EOF'
schema: spec-driven
context: |
  Android navigation app; Kotlin and C++; Hilt; Compose.
operations:
  apply:
    guidance:
      - "A quoted entry that contains ': ' on purpose"
EOF
run "$case_l-unowned" "$unowned"
if [ "$CHECK_RC" = 0 ]; then
  fail_case "artifact-hygiene#$case_l" "a short context block that names no owner passed (exit 0)"
elif ! grep -q 'does not name AGENTS.md' "$tmp/$case_l-unowned.out"; then
  fail_case "artifact-hygiene#$case_l" "the finding does not name the missing pointer"
fi
fi

if [ "$total" -eq 0 ]; then
  echo "$(basename -- "$0" .sh): no case named '$only_case'" >&2
  exit 2
fi

if [ "$failed" -gt 0 ]; then
  echo "$(basename -- "$0" .sh): $failed of $total cases failed —${FAILED_CASES}"
  exit 1
fi
if [ "$total" -eq 1 ]; then
  echo "$(basename -- "$0" .sh): all 1 case passed (throwaway OpenSpec trees, no Gradle)"
else
  echo "$(basename -- "$0" .sh): all $total cases passed (throwaway OpenSpec trees, no Gradle)"
fi
