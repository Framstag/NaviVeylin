#!/usr/bin/env bash
# prescreen.sh — the cheap, mechanical half of the eligibility gate, run before any agent process.
#
# The loop's gate has two halves and the split is the point (design D6): what a script can decide
# cheaply — the item still cites something that exists, its class is one this loop works, its status is
# not already in flight, its evidence needs a device rather than a judgement — is decided here, with no
# agent process; what only judgement can decide (one root cause, exactly one fix, a falsifiable
# WHEN/THEN, one capability) is carried into the queue as `pending` and decided by the worker in Phase B.
#
# Claiming the whole gate as deterministic would be false, and a script that pretended otherwise would
# turn "this needs a decision" into "silently skipped" — the failure the run's report exists to prevent.
# The pre-screen is measured at 10-15 min per rejection saved: without it, 5 of 8 candidates were
# ineligible (`.pi/skills/fix-loop/REFERENCE.md`).
#
# The candidate's text is read here (it is data, and the sensor reads it as data): the queue and the
# exclusions are the run's own output, so a rejected candidate is reported with the clause that
# rejected it rather than dropped silently.
#
# The refusals an item cannot escape by itself leave a trace in the tracker, not only in this run's
# state: the state is machine-local and the next `init` replaces it wholesale, so an authorised item the
# loop will never work was indistinguishable from one it has not reached yet (#163). The verdict label is
# a refusal — it states why the pre-screen declined and leaves the authorisation label untouched — and it
# is cleared on the first run whose verdict is no longer a refusal, so a repair is not silently frozen.
#
# Exit: 0 ok, 2 usage, 3 stop (nothing eligible), 5 refuse.
#
# Usage: prescreen.sh [--json] [--no-sensor] [--limit N] [--dry-run]
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/loop/lib.sh
source "$here/lib.sh"

json=0
sensor=1
write=1
limit=400
while [ $# -gt 0 ]; do
  case "$1" in
    --json) json=1; shift ;;
    --no-sensor) sensor=0; shift ;;
    --dry-run) write=0; shift ;;
    --limit) need_value "$@"; limit="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,31p' "$0"; exit 0 ;;
    *) loop_die "prescreen: unknown argument $1" ;;
  esac
done

loop_resolve
loop_roles_load
profile=$(loop_profile)
separator=$(loop_compat_profile_value namespaceSeparator)
separator="${separator:-/}"

# One citation index for the whole pre-screen (`stale-citation-resolution` D1): the classifier's suffix test
# needs the file list, and a `git ls-files` per citation would fork per token.
ISSUES_CITATION_ROOT="$LOOP_ROOT"
export ISSUES_CITATION_ROOT
ISSUES_CITATION_INDEX=$(mktemp) || ISSUES_CITATION_INDEX=""
export ISSUES_CITATION_INDEX
if [ -n "$ISSUES_CITATION_INDEX" ]; then
  issues_citation_index_build "$LOOP_ROOT" "$ISSUES_CITATION_INDEX" || true
  trap 'rm -f "$ISSUES_CITATION_INDEX"' EXIT
fi

# The loop's rules are stated over the tracker's vocabulary, and every value is read from the profile's
# `roles` section — a second project renames its vocabulary in the profile, not in this script (D16).
vocabulary() { loop_compat_profile_value "$1"; }
queued_verdict=$(loop_role queuedVerdict)
held_verdict=$(loop_role heldVerdict)
decision_verdict=$(loop_role decisionAgent)
worked_classes="$(loop_role workedClasses)"
# The categories whose items lead the queue, in the profile's order (this project: the tooling, the process and
# the proof rather than the shipped app). Empty is a project that wants no category priority, and the queue then
# keeps the class order alone — an absent key is a half-adapted profile, refused by `loop_roles_load`.
priority_categories="$(loop_role firstCategories)"
refused_classes="$(loop_role refusedClasses)"
inflight_status=$(loop_role inFlightStatus)
device_status=$(loop_role deviceStatus)
device_agent=$(loop_role deviceAgent)
hold_status=$(loop_role holdStatus)
# The label an escalation writes, and the state it leaves beside it: an item carrying either has already been
# decided about, by this loop, and is its owner's until a person answers it (#183).
decision_label="agent${separator}${decision_verdict}"
settled_statuses="$(loop_role settledStatuses)"
is_refused_class() { [ -n "$refused_classes" ] && printf '%s\n' "$refused_classes" | grep -qxF "$1"; }
is_settled_status() { [ -n "$settled_statuses" ] && printf '%s\n' "$settled_statuses" | grep -qxF "$1"; }
allowed_authors=$(vocabulary allowedAuthors)
# The refusal labels: the verdicts an item cannot escape by itself, and therefore the ones that must be
# readable from the tracker rather than from the run. A project renames them here, not in this script (D16).
refusal_stale=$(loop_role excludedStaleAgent)
refusal_feature=$(loop_role excludedFeatureAgent)
refusal_settled=$(loop_role excludedSettledAgent)
for refusal in "$refusal_stale" "$refusal_feature" "$refusal_settled"; do
  [ -z "$refusal" ] || refusal_labels="${refusal_labels:-}agent${separator}${refusal} "
done
refusal_labels="${refusal_labels:-}"

label_value() { # label_value <labels-json> <namespace> -> the value, or empty
  printf '%s' "$1" | jq -r --arg n "$2" --arg s "$separator" \
    '[.[] | select(.name | startswith($n + $s)) | (.name | ltrimstr($n + $s))] | .[0] // ""'
}

# The filesystem sensor: does the item still cite something that exists? A memory is not a fact, and a
# path that no longer resolves is the one thing a script can refute without reading the code (Phase A
# step 1 / Phase B condition 1, decided cheaply here — design D6).
# The citation rule is `tools/issues/lib.sh`'s (`stale-citation-resolution` D1): **one** definition of what a
# citation is — asked here and by `tools/issues/resolve-stale.sh`, so the two cannot disagree about a body —
# and `stale` means the classifier's verdict (a citation no file, no submodule and no generated artefact
# accounts for) rather than "this token is not at the repository root". That crude extraction was wrong about
# 48 of 49 verdicts on 2026-10-18 (#164).
stale_citations_of() {
  issues_stale_citations_of "$1
$2"
}

cited_symbols_of() {
  printf '%s\n%s\n' "$1" "$2" |
    grep -oE '[A-Za-z0-9_][A-Za-z0-9_./-]*\.[A-Za-z0-9]+:[A-Za-z_][A-Za-z0-9_]*' |
    sort -u || true
}

# The base every item of this run descends from: the branch question below is asked against it, exactly as
# `worktree.sh start-item`'s own check asks it.
run_base=$(loop_state get run.base 2>/dev/null || true)

# The repository sensor: does the item already have an attempt behind it? Whether an item was attempted is
# held by git and not by the tracker — a run that died before it could label anything leaves its branch and
# nothing else (#175) — so the pre-screen asks the same question the start asks: a commit on the item's
# branch that the run's base does not have *is* the previous attempt's work. It is asked on the branch name's
# *prefix*, because the slug is not known before the title is read, and only the start creates the branch, so
# the start's own check stays the authoritative one.
previous_attempt_branch_of() { # <issue> → the branch that carries work the run's base does not, or nothing
  [ -n "$run_base" ] || return 0
  local branch
  while IFS= read -r branch; do
    [ -n "$branch" ] || continue
    if ! git -C "$LOOP_ROOT" merge-base --is-ancestor "$branch" "$run_base" 2>/dev/null; then
      printf '%s\n' "$branch"
      return 0
    fi
  done < <(git -C "$LOOP_ROOT" for-each-ref --format='%(refname:short)' "refs/heads/fix/issue-$1-*" 2>/dev/null)
}

# ── selection ────────────────────────────────────────────────────────────────────────────────────

candidates_json=$(loop_issue_tool list.sh --eligible-only --json --limit "$limit" 2>/dev/null) ||
  loop_die "prescreen: the issue tooling could not list eligible items"
[ -n "$candidates_json" ] || candidates_json='[]'

# The tracker writes the refusals need are collected here and applied once, after the queue is written, so
# a pre-screen that dies mid-selection changes no item (the labels are the run script's, never a child's).
label_writes=$(mktemp) || loop_die "prescreen: could not create the label write list"
trap 'rm -f "$label_writes"' EXIT

records='[]'
count=$(printf '%s' "$candidates_json" | jq 'length')
i=0
while [ "$i" -lt "$count" ]; do
  number=$(printf '%s' "$candidates_json" | jq -r ".[$i].number")
  i=$((i + 1))
  issue=$(loop_issue_json "$number") || loop_die "prescreen: gh could not read issue #$number"
  labels=$(printf '%s' "$issue" | jq -c '[.labels[]? | if type == "string" then {name: .} else . end]')
  title=$(printf '%s' "$issue" | jq -r '.title // ""')
  body=$(printf '%s' "$issue" | jq -r '.body // ""')
  author=$(printf '%s' "$issue" | jq -r '.author.login // ""')
  class=$(label_value "$labels" class)
  category=$(label_value "$labels" category)
  status=$(label_value "$labels" status)
  gh_state=$(printf '%s' "$issue" | jq -r '.state // "OPEN"')

  verdict="$queued_verdict"
  reason=""
  evidence=""
  already_escalated=0
  # What makes this item its owner's rather than the run's, named as it actually is: the label an earlier
  # escalation wrote, the hold it left, or both. An item that merely carries a person's hold must not be
  # described as carrying a loop escalation (#183).
  awaiting_decision=0
  awaiting_why=""
  awaiting_label=0
  if [ -n "$decision_verdict" ] &&
     printf '%s' "$labels" | jq -e --arg l "$decision_label" 'any(.[]; .name == $l)' >/dev/null; then
    awaiting_decision=1
    awaiting_label=1
    awaiting_why="$decision_label"
  fi
  if [ -n "$hold_status" ] && [ "$status" = "$hold_status" ]; then
    awaiting_decision=1
    awaiting_why="${awaiting_why:+$awaiting_why / }status${separator}${status}"
  fi
  if ! printf '%s\n' "$allowed_authors" | grep -qxF "$author"; then
    # The label alone would be self-served: the author term of the gate is the other half (change 1, D2).
    verdict="author-not-allowlisted"
    reason="author '$author' is not on the profile's allowlist"
  elif is_refused_class "$class"; then
    verdict="class-feature"
    reason="class${separator}${class} is not worked by this loop"
  elif [ "$status" = "$inflight_status" ] && [ -n "$inflight_status" ]; then
    verdict="$inflight_status"
    reason="status${separator}${status} — another change already holds it"
  elif [ "$gh_state" = "CLOSED" ] || is_settled_status "$status"; then
    condition="${status:-closed}"
    verdict="settled"
    reason="the item is already ${condition} — nothing is left for this loop to work"
  elif [ "$status" = "$device_status" ] && [ -n "$device_status" ]; then
    # A device is a fact, not a judgement: the run records the verdict and produces no host work
    # (design D15 property 4).
    verdict="$device_agent"
    reason="status${separator}${status} — the remaining evidence needs a device attached to the host"
  fi

  # The opt-in is the author's, so it is read and never written: a candidate without it is excluded rather
  # than authorised by the loop (change 1, D2; D6). This is the one read-side mention of the authorisation
  # label in `tools/loop/`. The branch is a guard, not a filter: today `list.sh --eligible-only` already
  # drops an unauthorised item, so it cannot fire — and it stays because the loop must not depend on the
  # tooling's filter for its own invariant that it never works what nobody authorised. If that filter ever
  # widens, this is where the loop refuses instead of working an item.
  if [ "$verdict" = "$queued_verdict" ] &&
     ! printf '%s' "$labels" | jq -e --arg l "$LOOP_AUTHORISATION_LABEL" 'any(.[]; .name == $l)' >/dev/null; then
    verdict="missing-authorisation"
    reason="the item does not carry ${LOOP_AUTHORISATION_LABEL} — the opt-in is the author's, not the run's"
  fi

  # A previous attempt's branch is the one rejection the tracker cannot state, so it is asked of git — and
  # asked before the citation sensor, because it names a decision for a person rather than a repair. The
  # verdict is a value of the profile's vocabulary, read here as the device verdict is (D16).
  #
  # It is asked before the awaiting-decision clause as well, and that ordering is load-bearing: an item the run
  # escalated *and* whose branch carries a finished attempt is the one #176 exists to adopt, and the branch is
  # the evidence `adopt_finished_exclusions` reads. An escalated item whose branch is unadoptable records the
  # branch too — `adopt-item` refuses it on its own inputs and the refusal is printed.
  if [ "$verdict" = "$queued_verdict" ]; then
    attempt_branch=$(previous_attempt_branch_of "$number")
    if [ -n "$attempt_branch" ]; then
      verdict="$decision_verdict"
      reason="branch $attempt_branch carries a commit the run's base does not — a previous attempt's work is on it"
      evidence="$attempt_branch"
      # The label still suppresses a second escalation comment: the branch is the new *evidence*, not a new
      # decision, and the item was already escalated when the label was written.
      [ "$awaiting_label" -eq 1 ] && already_escalated=1
    fi
  fi

  # The sensor runs on every candidate, before any judgement: it is the cheap refutation, and `stale` is the
  # classifier's verdict — a citation no file, no submodule and no generated artefact accounts for.
  if [ "$verdict" = "$queued_verdict" ] && [ "$sensor" -eq 1 ]; then
    stale_path=$(stale_citations_of "$title" "$body" | head -1)
    if [ -n "$stale_path" ]; then
      verdict="stale"
      reason="cited path '$stale_path' resolves to no file in the repository or its submodules"
      evidence="$stale_path"
    fi
    if [ "$verdict" = "$queued_verdict" ]; then
      for symbol in $(cited_symbols_of "$title" "$body"); do
        p="${symbol%%:*}"
        s="${symbol#*:}"
        if ! git -C "$LOOP_ROOT" grep -qn -- "$s" -- "$p" 2>/dev/null; then
          evidence="'$symbol' names no declaration in $p"
          break
        fi
        evidence="$symbol resolves"
      done
    fi
  fi

  # An item that already awaits a decision is its owner's until a person answers it: re-deriving the same
  # conclusion spends the very budget the decision cap protects, and #110 was escalated three times with one
  # conclusion (#183). The sensor and the branch rule above speak first, and that ordering is the point — a
  # stale citation is a repair and a branch carrying a finished attempt is an adoption, both cheaper than a
  # decision, and the spec's refusal label for `stale` must still be written on a held item.
  #
  # A hold is **not** a decision (#193): the state is written by the worker's skip verdict, by the device
  # verdict, by the stale-citation repair parking an item and by a person by hand, and three of those four
  # state no question at all. Only the decision label — the one an escalation writes — makes an item a
  # decision; a hold without it is reported as what it is, so it is visible on the tracker and stops consuming
  # the decision cap, which counts the items a person can actually answer.
  #
  # `alreadyEscalated` is the *label*'s fact, not the hold's: an item escalated by a worker keeps its comment
  # and is not escalated a second time (#183).
  if [ "$verdict" = "$queued_verdict" ] && [ "$awaiting_decision" -eq 1 ]; then
    if [ "$awaiting_label" -eq 1 ]; then
      verdict="$decision_verdict"
      reason="the item awaits a decision (${awaiting_why}) — left to its owner until a person answers it"
      evidence="$decision_label"
      already_escalated=1
    else
      verdict="$held_verdict"
      reason="the item is on hold with no recorded decision (status${separator}${hold_status}) — nobody has stated a question for it, so it is neither work nor a decision this run may take"
      evidence="status${separator}${hold_status}"
      already_escalated=0
    fi
  fi

  # The refusal leaves its trace in the tracker (expectation of #163). Only the verdicts the item cannot
  # escape by itself are written: `in-flight` clears when the other change closes, `needs-device` and
  # `needs-decision` already have a label write of their own (`labels.sh`), and the author's own label is
  # read and never written, so an item excluded for it is already visible as un-authorised.
  if [ "$write" -eq 1 ] && [ -n "$refusal_labels" ]; then
    refusal_label=""
    case "$verdict" in
      stale) refusal_label="agent${separator}${refusal_stale}" ;;
      class-feature) refusal_label="agent${separator}${refusal_feature}" ;;
      settled) refusal_label="agent${separator}${refusal_settled}" ;;
    esac
    if [ "$verdict" = "$queued_verdict" ]; then
      # The verdict changed: the refusal labels a previous run wrote are no longer true of this item.
      for stale_label in $refusal_labels; do
        if printf '%s' "$labels" | jq -e --arg l "$stale_label" 'any(.[]; .name == $l)' >/dev/null; then
          printf '%s\t%s\n' "$number" "clear-${stale_label##*${separator}excluded-}" >> "$label_writes"
        fi
      done
    elif [ -n "$refusal_label" ] &&
         ! printf '%s' "$labels" | jq -e --arg l "$refusal_label" 'any(.[]; .name == $l)' >/dev/null; then
      printf '%s\t%s\n' "$number" "exclude-${refusal_label##*${separator}excluded-}" >> "$label_writes"
    fi
  fi

  # The gate's judgement half is NOT decided here, and saying so is part of the output (D6).
  record=$(jq -n --argjson issue "$number" --arg title "$title" --arg class "$class" \
    --arg category "$category" --arg status "$status" --arg author "$author" \
    --arg verdict "$verdict" --arg reason "$reason" --arg evidence "$evidence" \
    --argjson escalated "$([ "$already_escalated" -eq 1 ] && printf true || printf false)" '
    {issue:$issue, title:$title, class:$class, category:$category, status:$status, author:$author,
     verdict:$verdict, reason:$reason, evidence:$evidence, rank:null, alreadyEscalated:$escalated,
     gates:{oneRootCause:"pending", oneFix:"pending", falsifiable:"pending", oneCapability:"pending"}}')
  records=$(printf '%s' "$records" | jq -c --argjson r "$record" '. + [$r]')
done

ranked=$(printf '%s' "$records" | jq -c --argjson worked "$(printf '%s\n' "$worked_classes" | jq -R . | jq -sc .)" \
  --argjson leading "$(printf '%s' "$priority_categories" | jq -R . | jq -sc .)" '
  (map(. + {_rank: (. as $r | (($worked | index($r.class)) // ($worked | length))),
             _lead: (. as $r | (($leading | index($r.category)) // ($leading | length)))})
   | sort_by([._lead, ._rank, .issue]) | map(del(._lead, ._rank))) as $sorted
  | ($sorted | map(select(.verdict == $queued))) as $q
  | reduce range(0; ($q | length)) as $i ($sorted;
      . as $all | ($q[$i].issue) as $id
      | map(if .issue == $id then .rank = ($i + 1) else . end))
' --arg queued "$queued_verdict") || loop_die "prescreen: could not rank the candidates"
records="$ranked"

eligible_count=$(printf '%s' "$records" | jq --arg queued "$queued_verdict" '[.[] | select(.verdict == $queued)] | length')
excluded_count=$(printf '%s' "$records" | jq --arg queued "$queued_verdict" '[.[] | select(.verdict != $queued)] | length')

# The queue, the exclusions and the per-item gate fields are one write, because they are one answer.
printf '%s' "$records" | loop_state queue >/dev/null

# The refusal labels, applied after the queue: a tracker write that fails is reported and does not stop a
# run, because the verdict, its reason and its evidence are already in the run's own state and report.
while IFS=$'\t' read -r write_issue write_label; do
  [ -n "$write_issue" ] && [ -n "$write_label" ] || continue
  loop_labels "$write_label" "$write_issue" >&2 ||
    printf 'pre-screen: could not write %s on #%s — the refusal is in the report and the run continues\n' \
      "$write_label" "$write_issue" >&2
done < "$label_writes"

if [ "$json" -eq 1 ]; then
  printf '%s\n' "$records"
else
  printf 'pre-screen: %s eligible, %s excluded — gates 2-5 stay the worker'"'"'s (design D6)\n' "$eligible_count" "$excluded_count"
  printf '%s' "$records" | jq -r --arg queued "$queued_verdict" '.[] |
    if .verdict == $queued
    then "  #\(.issue) [\(.class)/\(.category)] rank \(.rank) — \(.title | .[0:80])"
    else "  excluded #\(.issue) \(.verdict): \(.reason)" end'
fi
[ "$eligible_count" -gt 0 ] || exit "$LOOP_STOP"
exit "$LOOP_OK"
