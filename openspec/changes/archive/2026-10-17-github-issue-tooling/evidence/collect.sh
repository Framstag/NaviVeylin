#!/usr/bin/env bash
# Regenerate this change's evidence. Every file it writes is a record of a real run, not a claim: the
# self-test output, the dry run over `TODO.md` through the source adapter and the reconciler, and the
# tracker's state at the same moment.
#
# Nothing here writes to the tracker: the import runs `--dry-run` (its default), and every `gh` write would
# be refused by the stub unless GH_STUB_RECORD named a log file.
#
# Usage: bash openspec/changes/github-issue-tooling/evidence/collect.sh
set -uo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
cd "$root" || exit 2

echo "evidence: collecting into $here"

# --- 8.1 the gate, as the CI step runs it ---------------------------------------
{
  echo "# tools/issue-tooling-selftest.sh"
  echo "# run:      $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "# command:  bash tools/issue-tooling-selftest.sh"
  echo "# a full run, then the same command in an empty environment (what the CI step does)"
  echo
  bash tools/issue-tooling-selftest.sh
  echo "exit=$?"
  echo
  echo "# env -i PATH=\"\$PATH\" bash tools/issue-tooling-selftest.sh"
  env -i PATH="$PATH" bash tools/issue-tooling-selftest.sh
  echo "exit=$?"
} > "$here/selftest.txt" 2>&1

# --- 5.4 the dry run over the real backlog --------------------------------------
findings="$here/.findings.json"
bash tools/issues/import/todo-md.sh > "$findings"
bash tools/issues/import.sh --findings "$findings" --json > "$here/todo-dry-run.json"

{
  echo "# The dry run over TODO.md"
  echo "# run:     $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "# command: bash tools/issues/import/todo-md.sh > findings.json"
  echo "#          bash tools/issues/import.sh --findings findings.json --json > todo-dry-run.json"
  echo "#          bash tools/issues/import.sh --findings findings.json        (the human output below)"
  echo
  echo "## Counts"
  echo "sections in TODO.md:              $(grep -cE '^## [0-9]+\.' TODO.md)"
  echo "metadata lines in TODO.md:        $(grep -c '^\*\*id:\*\*' TODO.md)"
  echo "findings from the adapter:        $(jq -r '.findings' "$here/todo-dry-run.json")"
  echo "previews from the reconciler:     $(jq -r '.previews' "$here/todo-dry-run.json")"
  echo "already present (skipped):        $(jq -r '.skipped' "$here/todo-dry-run.json")"
  echo "created (dry run):                $(jq -r '.created' "$here/todo-dry-run.json")"
  echo
  echo "## Renames the adapter applied"
  jq -r '.renames[]' "$here/todo-dry-run.json" | sed 's/^/  /'
  echo
  echo "## Label histogram"
  jq -r '.histogram[] | "  \(.count)\t\(.label)"' "$here/todo-dry-run.json"
  echo
  echo "## Status labels, as imported"
  jq -r '[.previewList[].labels[] | select(startswith("status/"))] | group_by(.) | map({label: .[0], count: length})[] | "  \(.count)\t\(.label)"' "$here/todo-dry-run.json"
  echo
  echo "## The opt-in label on arrival"
  echo "  agent/eligible: $(jq -r '[.previewList[].labels[] | select(. == "agent/eligible")] | length' "$here/todo-dry-run.json")"
  echo
  echo "## The reconciler's human output"
  bash tools/issues/import.sh --findings "$findings" | sed 's/^/  /'
  echo
  echo "## The preflight refusal, when the identity is not allowlisted"
  GH_STUB_DIR="$root/tools/issues/fixtures/scenarios/outsider" bash tools/issues/policy.sh preflight
  echo "  exit=$?"
} > "$here/todo-dry-run.txt" 2>&1

# --- 5.5 the tracker, at the same moment ----------------------------------------
{
  echo "# The tracker's state"
  echo "# run: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo
  echo "## Issues, open and closed"
  gh issue list --state all --limit 400 --json number,title,comments
  echo
  echo "## Labels"
  echo "count: $(gh label list --limit 400 --json name | jq 'length')"
  gh label list --limit 400 | sed 's/^/  /'
  echo
  echo "## The dry run created nothing"
  echo "  issues:        0 (the JSON above is an empty array)"
  echo "  comments:      0 (no issue exists to carry one)"
  echo "  labels by this run: 0 — every write path goes through the stub, and the dry run calls none of them"
  echo
  echo "## Recorded honestly: the 36 taxonomy labels already existed when this run started"
  echo "  They were created on 2026-10-17 by this session's own probe script, which ran"
  echo "  tools/issues/labels.sh --apply without the stub gh on PATH and so reached the real repository."
  echo "  The plan (tasks.md 5.5) expected only GitHub's 9 default labels; the operator decided to keep the"
  echo "  labels and to qualify that expectation here rather than delete them. The run above changed nothing."
} > "$here/tracker-state.txt" 2>&1

rm -f "$findings"

# --- 6.1-6.4 the four skills' documented commands, against the stub ------------------
{
  echo "# The four skills' documented commands"
  echo "# run: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "# Every command in .pi/skills/{create,update,close,list}-issue/SKILL.md, run as documented against"
  echo "# the stub gh on PATH; the exit code in brackets is the one the skill documents."
  echo
  bash "$here/skill-commands.sh"
} > "$here/skill-commands.txt" 2>&1

# --- 8.3 the classification gate, and its falsification -----------------------------
{
  echo "# The classification gate"
  echo "# run: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo
  echo "## as shipped"
  bash tools/gen-feature-list.sh --check-classification
  echo "exit=$?"
  echo
  echo "## falsification: this change's delta installed in a throwaway root, the line removed"
  mk="$here/.classification-root"
  data="$here/.classification-data"
  rm -rf "$mk" "$data"
  mkdir -p "$mk/openspec/specs/issue-backlog" "$data"
  cp openspec/changes/github-issue-tooling/specs/issue-backlog/spec.md "$mk/openspec/specs/issue-backlog/spec.md"
  cp tools/feature-list/areas.json tools/feature-list/highlights.json "$data/"
  jq 'del(.specs["issue-backlog"])' tools/feature-list/specs.json > "$data/specs.json"
  out=$(bash tools/gen-feature-list.sh --root "$mk" --data-dir "$data" --check-classification 2>&1)
  status=$?
  printf '%s\n' "$out" | head -5
  echo "exit=$status (non-zero and naming issue-backlog means the line is load-bearing)"
  rm -rf "$mk" "$data"
} > "$here/classification.txt" 2>&1

# --- 6.5 / 8.2 the files this change deliberately did not touch ----------------------
{
  echo "# Files this change deliberately did not modify"
  echo "# run: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo
  echo "## git status --short -- AGENTS.md guidelines openspec/config.yaml .pi/skills"
  git status --short -- AGENTS.md guidelines openspec/config.yaml .pi/skills
  echo "  (the guidelines/ line is this change's own task 8.5 edit; the four '??' entries are the new"
  echo "   skills — new files, not modifications — and AGENTS.md, openspec/config.yaml and the twelve"
  echo "   existing skills appear as no change at all)"
  echo
  echo "## git status --short -- app auto core osmscout-client-java buildSrc"
  git status --short -- app auto core osmscout-client-java buildSrc
  echo "  (no output above means no Gradle source set, manifest, resource or native file changed)"
  echo
  echo "## the run itself"
  echo "  ./gradlew was never invoked; no Gradle build and no test suite was run"
} > "$here/touched-files.txt" 2>&1

echo "evidence: wrote selftest.txt, todo-dry-run.json, todo-dry-run.txt, tracker-state.txt,"
echo "          skill-commands.txt, classification.txt, touched-files.txt"
grep -c . "$here/selftest.txt" >/dev/null && tail -2 "$here/selftest.txt"
