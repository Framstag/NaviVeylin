#!/usr/bin/env bash
# The cases for `tools/check-openspec-hygiene.sh` (issue #45, TODO.md §17): each case builds a throwaway
# OpenSpec tree, breaks exactly one of the artifact classes, and runs the real checker over it — so the
# script the pipeline runs is exercised against the trees the CLI accepts silently.
#
# The three classes and why nothing else reports them are the script's own header. What this file adds is
# the red half: a checker whose classes are never violated is a checker nobody has seen fail.
#
# Three case ids are the ones the shipped requirement already names —
# `documentation-ownership` "A capability spec states what the capability is for" promises
# `artifact-hygiene#theArchivePlaceholderIsReported`, `#aWrittenPurposePasses` and
# `#aNestedCapabilitySpecIsReported`, and until this file existed no case answered to any of them.
#
# Usage: check-openspec-hygiene-selftest.sh [--root <dir>] [--case <name>]
#        --case <name>  run one case by its id (`artifact-hygiene#…`, or the bare suffix) — what a
#                       revert-check names, so one mutation costs one run instead of six
# Exit: 0 every case passed, 1 a case failed, 2 usage/environment.
set -uo pipefail

root=""
only_case=""
while [ $# -gt 0 ]; do
  case "$1" in
    --root) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; root="${2:-}"; shift 2 ;;
    --case) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; only_case="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,15p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "$(basename -- "$0" .sh): unknown argument '$1'" >&2; exit 2 ;;
  esac
done
[ -n "$root" ] || root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
checker="$root/tools/check-openspec-hygiene.sh"
[ -f "$checker" ] || { echo "check-openspec-hygiene-selftest: no $checker" >&2; exit 2; }

# selected <case id> — the case runs, either because nothing was selected or because it was.
selected() {
  [ -z "$only_case" ] && return 0
  [ "$only_case" = "$1" ] || [ "artifact-hygiene#${only_case}" = "$1" ]
}

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# clean_tree <dir> — the smallest OpenSpec tree the three classes accept: one open change whose tasks
# carry `- [x]` markers, a config whose guidance entries are quoted, and a capability spec whose Purpose
# is a sentence its author wrote.
clean_tree() {
  local d="$1"
  mkdir -p "$d/openspec/changes/demo" "$d/openspec/specs/demo"
  cat > "$d/openspec/changes/demo/tasks.md" <<'EOF'
## 1. The first section

- [x] 1.1 A done task
- [ ] 1.2 An open task
EOF
  cat > "$d/openspec/config.yaml" <<'EOF'
schema: spec-driven
operations:
  apply:
    guidance:
      - "A quoted entry that contains ': ' on purpose"
  archive:
    guidance:
      - "Another quoted entry"
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
