#!/usr/bin/env bash
# probe.sh — the seven cases this change's delta names, run on the tree the script lives in.
#
# Three cases read `AGENTS.md`'s `## OpenSpec Workflow` section; four drive the `openspec validate` diagnostics
# on this change's own delta and restore it afterwards. Nothing else is read and nothing else is written, so
# the probe is repeatable and leaves the tree as it found it.
#
# The change may be open (`openspec/changes/<name>`) or archived (`openspec/changes/archive/<date>-<name>`), and
# an archived change is not in the open change set, so the four validator cases drive a temporary copy of it —
# `openspec/changes/probe-<name>`, removed by the exit trap. That is what keeps the probe runnable after
# `openspec archive`, which is when the delta no longer sits under `openspec/changes/`.
#
# Usage: probe.sh [--case <id>]     # no --case runs every case; exit 0 when every case that ran passed
# Case ids: theWorkflowSectionStatesTheScenarioRule theWorkflowSectionStatesTheTextBound
#           theStrictCommandIsNamedBesideTheRules theChangeSetIsValidatedByACommand
#           theCompletedBlockKeepsTheRequirementWhole aRenamedScenarioIsReportedAsAnOmission
#           anOverLongRequirementIsReported
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# the project root, whichever of the two places the probe sits in: opened (`openspec/changes/<name>/evidence`)
# or archived (`openspec/changes/archive/<date>-<name>/evidence`), one level deeper
root=$here
while [ ! -f "$root/AGENTS.md" ] && [ "$root" != "/" ]; do root=$(dirname "$root"); done
[ -f "$root/AGENTS.md" ] || { echo "probe: no AGENTS.md above $here" >&2; exit 2; }
change=fix-workflow-section-names-strict-rules
agents="$root/AGENTS.md"

filter=""
while [ $# -gt 0 ]; do
  case "$1" in
    --case) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { echo "probe: --case needs a value" >&2; exit 2; }; filter="${2:-}"; shift 2 ;;
    -h|--help) awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "probe: unknown argument '$1'" >&2; exit 2 ;;
  esac
done

cases="theWorkflowSectionStatesTheScenarioRule theWorkflowSectionStatesTheTextBound theStrictCommandIsNamedBesideTheRules theChangeSetIsValidatedByACommand theCompletedBlockKeepsTheRequirementWhole aRenamedScenarioIsReportedAsAnOmission anOverLongRequirementIsReported"

[ -f "$agents" ] || { echo "probe: no $agents" >&2; exit 2; }

# the change directory, open or archived, and the name the validator is asked about: an archived change is
# copied into the open change set under `probe-` so the four validator cases can drive it
open_dir="$root/openspec/changes/$change"
archived_dir=$(ls -d "$root"/openspec/changes/archive/*-"$change" 2>/dev/null | head -1)
if [ -d "$open_dir" ]; then
  work_change=$change
elif [ -n "$archived_dir" ] && [ -d "$archived_dir" ]; then
  work_change="probe-$change"
  rm -rf "$root/openspec/changes/$work_change"
  cp -r "$archived_dir" "$root/openspec/changes/$work_change" || { echo "probe: cannot copy $archived_dir" >&2; exit 2; }
else
  echo "probe: no change directory at openspec/changes/$change or under openspec/changes/archive/" >&2
  exit 2
fi
delta="$root/openspec/changes/$work_change/specs/documentation-ownership/spec.md"

backup=""
cleanup() {
  [ -n "$backup" ] && [ -f "$backup" ] && cp -p "$backup" "$delta"
  [ "$work_change" != "$change" ] && rm -rf "$root/openspec/changes/$work_change"
  return 0
}
trap cleanup EXIT

# the `## OpenSpec Workflow` section, to the next `## ` heading — the one region an author reads before writing
# an artifact. Flattened to one line, so a rule that wraps across two lines still matches as one statement.
section=$(awk '/^## OpenSpec Workflow$/{f=1;next} f && /^## /{exit} f{print}' "$agents" | tr '\n' ' ')

pass=0
failed=0
failed_ids=""

ok()   { printf 'PASS %s: %s\n' "$1" "$2"; pass=$((pass + 1)); }
bad()  { printf 'FAIL %s: %s\n' "$1" "$2"; failed=$((failed + 1)); failed_ids="$failed_ids $1"; }

want_section() { # $1 case id, $2 what it asserts, then one extended regex per clause the assertion is made of
  local id=$1 what=$2 re
  shift 2
  if [ -z "$section" ]; then bad "$id" "AGENTS.md has no '## OpenSpec Workflow' section"; return; fi
  for re in "$@"; do
    if ! printf '%s\n' "$section" | grep -qE -- "$re"; then
      bad "$id" "$what — no line of the section matches /$re/"
      return
    fi
  done
  ok "$id" "$what"
}

case "$filter" in
  ""|theWorkflowSectionStatesTheScenarioRule)
    want_section theWorkflowSectionStatesTheScenarioRule \
      "the section states that a MODIFIED requirement repeats every shipped scenario verbatim and under their own titles" \
      'MODIFIED.{0,80}repeats.{0,140}verbatim' \
      'verbatim.{0,140}under[[:space:]]+their[[:space:]]+own[[:space:]]+titles' ;;
esac
case "$filter" in
  ""|theWorkflowSectionStatesTheTextBound)
    want_section theWorkflowSectionStatesTheTextBound \
      "the section states the validator's 500-character bound, that it is the requirement body and not its scenarios, and that an example belongs in a scenario" \
      '500 characters' \
      'requirement[[:space:]]+body,[[:space:]]+not[[:space:]]+its[[:space:]]+scenarios' \
      'example[[:space:]]+belongs[[:space:]]+in[[:space:]]+a[[:space:]]+scenario' ;;
esac
case "$filter" in
  ""|theStrictCommandIsNamedBesideTheRules)
    want_section theStrictCommandIsNamedBesideTheRules \
      "the section names 'openspec validate <change> --strict' beside the two rules, as the command that fails on them where a plain validate only warns" \
      'openspec validate[^|]*--strict' \
      'plain[[:space:]]+`openspec[[:space:]]+validate`[[:space:]]+reports[[:space:]]+both[[:space:]]+as[[:space:]]+warnings' ;;
esac

# ── the four validator cases: mutate the delta, run the validator, restore ───────────────────────────────
run_validator() { ( cd "$root" && openspec validate "$work_change" --strict 2>&1 ); }
run_plain() { ( cd "$root" && openspec validate "$work_change" 2>&1 ); }
run_change_set() { ( cd "$root" && openspec validate --changes --strict 2>&1 ); }

# the shipped requirement this change modifies, and the block this change's delta carries instead
shipped_req() {
  awk '/^### Requirement: A modified requirement carries the scenarios/{f=1;next} f && /^### /{exit} f{print}' \
    "$root/openspec/specs/documentation-ownership/spec.md"
}
block_req() {
  awk '/^### Requirement: A modified requirement carries the scenarios/{f=1;next} f && /^### /{exit} f{print}' "$delta"
}

validator_case() { # $1 case id
  if [ ! -f "$delta" ]; then bad "$1" "no delta spec at openspec/changes/$work_change/specs/documentation-ownership/spec.md"; return; fi
  backup=$(mktemp "${TMPDIR:-/tmp}/probe-delta.XXXXXX") || { bad "$1" "cannot make a backup"; return; }
  cp -p "$delta" "$backup"
  case "$1" in
    theChangeSetIsValidatedByACommand)
      # the whole open change set validates, and a block that omits a shipped scenario makes it fail: the same
      # rename as below, read through `--changes`
      awk '{ print ($0 == "#### Scenario: The change set is validated by command" ? "#### Scenario: The change set is validated by the strict command" : $0) }' \
        "$backup" > "$delta"
      if ! cmp -s "$backup" "$delta"; then
        out=$(run_change_set); status=$?
        if [ "$status" -ne 0 ] && printf '%s' "$out" | grep -q "$work_change" &&
          printf '%s' "$out" | grep -q 'The change set is validated by command' &&
          printf '%s' "$out" | grep -q "A modified requirement carries the scenarios its spec still has"; then
          ok "$1" "validate --changes --strict exits $status and its error names the change, the requirement and the omitted scenario"
        else
          bad "$1" "the omitted scenario did not make --changes exit non-zero naming all three: exit=$status $(printf '%s' "$out" | head -3 | tr '\n' ' ')"
        fi
      else
        bad "$1" "the rename found no '#### Scenario: The change set is validated by command' heading to change"
      fi
      ;;
    theCompletedBlockKeepsTheRequirementWhole)
      # the restored block validates, and carries every shipped scenario's title and WHEN/THEN text
      out=$(run_validator); status=$?
      lost=""
      while IFS= read -r line; do
        case "$line" in
          "#### Scenario:"*|'- **WHEN**'*|'- **THEN**'*|'- **AND**'*|'  '*)
            grep -qxF -- "$line" "$delta" || lost="$lost / $line"
            ;;
        esac
      done <<EOF
$(shipped_req)
EOF
      if [ "$status" -eq 0 ] && [ -z "$lost" ]; then
        ok "$1" "the block validates and carries every shipped scenario title and WHEN/THEN line verbatim"
      else
        bad "$1" "exit=$status, shipped text the block does not carry:${lost:- none}"
      fi
      ;;
    aRenamedScenarioIsReportedAsAnOmission)
      # rename a *shipped* scenario's title in the MODIFIED block and change nothing else: the block now names a
      # scenario the shipped requirement does not have, and omits the one it does
      awk '{ print ($0 == "#### Scenario: The change set is validated by command" ? "#### Scenario: The change set is validated by the strict command" : $0) }' \
        "$backup" > "$delta"
      if ! cmp -s "$backup" "$delta"; then
        out=$(run_validator); status=$?
        if [ "$status" -ne 0 ] && printf '%s' "$out" | grep -qi 'omit'; then
          ok "$1" "a renamed scenario title makes validate --strict exit $status and report an omission: $(printf '%s' "$out" | grep -oiE '[^.]*omits?[^.]*' | head -1)"
        else
          bad "$1" "rename produced exit=$status and no omission report: $(printf '%s' "$out" | head -3 | tr '\n' ' ')"
        fi
      else
        bad "$1" "the rename found no '#### Scenario: The change set is validated by command' heading to change"
      fi
      ;;
    anOverLongRequirementIsReported)
      # lengthen a requirement body past the bound, leaving its scenarios alone: one paragraph is inserted as the
      # body's last paragraph, before the requirement's first scenario heading. A plain validate must report it as
      # a warning and exit 0; --strict must fail on the same finding, which is the sentence the entry document and
      # the requirement now carry.
      awk '{ if ($0 == "#### Scenario: The section states the scenario rule") { print "The validator reports this bound as a warning that the strict command fails on, and the report names the change and the requirement it belongs to, so an author who runs the command before the artifact is finished meets the bound while the text can still be cut rather than in the round that already wrote it, which is the cost this rule exists to remove from the author of a change."; print "" } print }' \
        "$backup" > "$delta"
      if ! cmp -s "$backup" "$delta"; then
        strict_out=$(run_validator); strict_status=$?
        plain_out=$(run_plain); plain_status=$?
        strict_says=$(printf '%s' "$strict_out" | grep -oiE 'Requirement text is very long[^)]*\)' | head -1)
        if [ "$strict_status" -ne 0 ] && [ -n "$strict_says" ] &&
          [ "$plain_status" -eq 0 ] && printf '%s' "$plain_out" | grep -qiE '\[WARNING\].*Requirement text is very long'; then
          ok "$1" "text past the bound makes --strict exit $strict_status reporting '$strict_says', and a plain validate report the same finding as a warning and exit $plain_status"
        else
          bad "$1" "lengthened requirement produced strict exit=$strict_status plain exit=$plain_status: $(printf '%s' "$strict_out" | head -3 | tr '\n' ' ')"
        fi
      else
        bad "$1" "the lengthening found no '#### Scenario: The section states the scenario rule' heading to insert before"
      fi
      ;;
  esac
  cleanup_backup
  # and the restored tree must validate
  out=$(run_validator); status=$?
  [ "$status" -eq 0 ] || bad "$1" "the restored tree does not validate (exit $status): $(printf '%s' "$out" | head -3 | tr '\n' ' ')"
}
cleanup_backup() { [ -n "$backup" ] && cp -p "$backup" "$delta"; backup=""; }
  case "$filter" in
  "") validator_case theChangeSetIsValidatedByACommand
       validator_case theCompletedBlockKeepsTheRequirementWhole
       validator_case aRenamedScenarioIsReportedAsAnOmission
       validator_case anOverLongRequirementIsReported ;;
  theChangeSetIsValidatedByACommand|theCompletedBlockKeepsTheRequirementWhole|aRenamedScenarioIsReportedAsAnOmission|anOverLongRequirementIsReported) validator_case "$filter" ;;
esac

printf 'probe: %d passed, %d failed\n' "$pass" "$failed"
[ "$failed" -eq 0 ] || { printf 'probe: failed cases:%s\n' "$failed_ids"; exit 1; }
