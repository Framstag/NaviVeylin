#!/usr/bin/env bash
# probe.sh — the five cases this change's delta names, run on the tree the script lives in.
#
# Three cases read `AGENTS.md`'s `## OpenSpec Workflow` section; two drive the `openspec validate --strict`
# diagnostics on this change's own delta spec and restore the file afterwards. Nothing else is read and
# nothing else is written, so the probe is repeatable and leaves the tree as it found it.
#
# Usage: probe.sh [--case <id>]     # no --case runs every case; exit 0 when every case that ran passed
# Case ids: theWorkflowSectionStatesTheScenarioRule theWorkflowSectionStatesTheTextBound
#           theStrictCommandIsNamedBesideTheRules theChangeSetIsValidatedByACommand
#           theCompletedBlockKeepsTheRequirementWhole aRenamedScenarioIsReportedAsAnOmission
#           anOverLongRequirementIsReported
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
root=$(cd "$here/../../../.." && pwd)
change=fix-workflow-section-names-strict-rules
agents="$root/AGENTS.md"
delta="$root/openspec/changes/$change/specs/documentation-ownership/spec.md"

filter=""
while [ $# -gt 0 ]; do
  case "$1" in
    --case) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { echo "probe: --case needs a value" >&2; exit 2; }; filter="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,13p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "probe: unknown argument '$1'" >&2; exit 2 ;;
  esac
done

cases="theWorkflowSectionStatesTheScenarioRule theWorkflowSectionStatesTheTextBound theStrictCommandIsNamedBesideTheRules theChangeSetIsValidatedByACommand theCompletedBlockKeepsTheRequirementWhole aRenamedScenarioIsReportedAsAnOmission anOverLongRequirementIsReported"

[ -f "$agents" ] || { echo "probe: no $agents" >&2; exit 2; }

# the `## OpenSpec Workflow` section, to the next `## ` heading — the one region an author reads before writing
# an artifact. Flattened to one line, so a rule that wraps across two lines still matches as one statement.
section=$(awk '/^## OpenSpec Workflow$/{f=1;next} f && /^## /{exit} f{print}' "$agents" | tr '\n' ' ')

pass=0
failed=0
failed_ids=""

ok()   { printf 'PASS %s: %s\n' "$1" "$2"; pass=$((pass + 1)); }
bad()  { printf 'FAIL %s: %s\n' "$1" "$2"; failed=$((failed + 1)); failed_ids="$failed_ids $1"; }

want_section() { # $1 case id, $2 extended regex, $3 what it asserts
  if [ -z "$section" ]; then bad "$1" "AGENTS.md has no '## OpenSpec Workflow' section"; return; fi
  if printf '%s\n' "$section" | grep -qE -- "$2"; then ok "$1" "$3"; else bad "$1" "$3 — no line of the section matches /$2/"; fi
}

case "$filter" in
  ""|theWorkflowSectionStatesTheScenarioRule)
    want_section theWorkflowSectionStatesTheScenarioRule 'MODIFIED.{0,80}repeats.{0,90}verbatim' \
      "the section states that a MODIFIED requirement repeats every shipped scenario verbatim" ;;
esac
case "$filter" in
  ""|theWorkflowSectionStatesTheTextBound)
    want_section theWorkflowSectionStatesTheTextBound '500 characters' \
      "the section states the validator's 500-character requirement-text bound" ;;
esac
case "$filter" in
  ""|theStrictCommandIsNamedBesideTheRules)
    want_section theStrictCommandIsNamedBesideTheRules 'openspec validate[^|]*--strict' \
      "the section names 'openspec validate <change> --strict' beside the two rules" ;;
esac

# ── the two validator cases: mutate the delta, run the validator, restore ────────────────────────────────
backup=""
restore() { [ -n "$backup" ] && [ -f "$backup" ] && cp -p "$backup" "$delta"; }
trap 'restore' EXIT

run_validator() { ( cd "$root" && openspec validate "$change" --strict 2>&1 ); }
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
  if [ ! -f "$delta" ]; then bad "$1" "no delta spec at openspec/changes/$change/specs/documentation-ownership/spec.md"; return; fi
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
        if [ "$status" -ne 0 ] && printf '%s' "$out" | grep -q "$change" && printf '%s' "$out" | grep -q 'The change set is validated by command'; then
          ok "$1" "validate --changes --strict exits $status and names the change, the requirement and the omitted scenario"
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
      # body's last paragraph, before the requirement's first scenario heading
      awk '{ if ($0 == "#### Scenario: The section states the scenario rule") { print "The validator reports this bound as an error and not as a warning, and the report names the change and the requirement it belongs to, so an author who runs the command before the artifact is finished meets the bound while the text can still be cut rather than in the round that already wrote it, which is the cost this rule exists to remove from the author of a change."; print "" } print }' \
        "$backup" > "$delta"
      if ! cmp -s "$backup" "$delta"; then
        out=$(run_validator); status=$?
        if [ "$status" -ne 0 ] && printf '%s' "$out" | grep -qi 'very long'; then
          ok "$1" "text past the bound makes validate --strict exit $status and report it: $(printf '%s' "$out" | grep -oiE 'Requirement text is very long[^)]*\)' | head -1)"
        else
          bad "$1" "lengthened requirement produced exit=$status without a 'very long' report: $(printf '%s' "$out" | head -3 | tr '\n' ' ')"
        fi
      else
        bad "$1" "the lengthening found no '#### Scenario: The section states the scenario rule' heading to insert before"
      fi
      ;;
  esac
  restore; backup=""
  # and the restored tree must validate
  out=$(run_validator); status=$?
  [ "$status" -eq 0 ] || bad "$1" "the restored tree does not validate (exit $status): $(printf '%s' "$out" | head -3 | tr '\n' ' ')"
}
  case "$filter" in
  "") validator_case theChangeSetIsValidatedByACommand
       validator_case theCompletedBlockKeepsTheRequirementWhole
       validator_case aRenamedScenarioIsReportedAsAnOmission
       validator_case anOverLongRequirementIsReported ;;
  theChangeSetIsValidatedByACommand|theCompletedBlockKeepsTheRequirementWhole|aRenamedScenarioIsReportedAsAnOmission|anOverLongRequirementIsReported) validator_case "$filter" ;;
esac

printf 'probe: %d passed, %d failed\n' "$pass" "$failed"
[ "$failed" -eq 0 ] || { printf 'probe: failed cases:%s\n' "$failed_ids"; exit 1; }
