#!/usr/bin/env bash
# Create one issue from a structured finding.
#
# The finding arrives as JSON on stdin (or via `--finding <file>`):
#
#   {"sourceId": "129", "title": "§129 …", "class": "bug", "category": "route-and-navigation",
#    "claim": "the first claim, hashed", "anchor": "app/src/main/X.kt:Symbol",
#    "labels": ["class/bug", "category/route-and-navigation", "status/fixed-by", "source/todo-import"],
#    "body": "the text, verbatim"}
#
# The fingerprint is computed over the structured fields and checked against the live tracker; a match
# refuses with exit 4 and names the issue that already exists, unless `--allow-duplicate` says the caller
# means it (the override is recorded in the output). A reserved label in any input is refused with exit 3 —
# no script here can authorise work (design D2, D4).
#
# `class` and `category` are required, and each is filed as a label of its namespace (`class/<value>`,
# `category/<value>`): a finding whose `.labels[]` spells them too is filed once per value, and one whose
# `.labels[]` contradicts them is refused rather than quietly overruled (issue #201).
#
# `--sensor <id>` is the check registry's path into this writer (`sensor-registry-and-metric-issues`,
# D4): the row's id is validated against the registry — the one owner of the `sensor` namespace's values —
# and the label is added; the finding's own `fingerprint` is then the dedup anchor, because for a sensor
# the identity is the *condition* (the failing case, the path, the module) and not the check that reported
# it. Such a finding's body record also carries `occurrence: 1`, the count the reopen path increments.
#
# The shape (`backlog-source-of-truth`, "A finding is filed in one of two shapes"). A finding carries exactly
# one of:
#
#   "cause":     {"file": "app/src/main/X.kt", "line": 84, "case": "create#...", "tests": 1, "failures": 1}
#   "change":    "some-change",
#   "questions": [{"question": "the fork", "decision": "the answer"}]
#
# A cause makes it tier A: the pointer must resolve in the tree and the case is required, because a cause
# without one is a hypothesis. A change makes it tier B: the change directory must exist, the profile's
# `checks.changeReady` must report `ok` for it, and every question must carry its decision — the answers are
# commented on the item, in the form `answer.sh` writes, before its state label is set. Neither is refused
# with `shape-missing`, both with `shape-ambiguous`. A `--sensor` finding with neither is exempt and says so in
# its body record.
#
# Exit: 0 created, 2 usage/malformed, 3 policy, 4 duplicate.
#
# Usage: create.sh [--finding <file>] [--label <label>]… [--status <value>] [--sensor <id>] [--import]
#                  [--allow-duplicate] [--json] [--profile <path>]
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/issues/lib.sh
source "$here/lib.sh"

profile_arg=""
json=0
allow_duplicate=0
finding_file="-"
extra_labels=()
extra_status=""
sensor_id=""
import_path=0
while [ $# -gt 0 ]; do
  case "$1" in
    --profile) need_value "$@"; profile_arg="${2:-}"; shift 2 ;;
    --finding) need_value "$@"; finding_file="${2:-}"; shift 2 ;;
    --label) need_value "$@"; extra_labels+=("${2:-}"); shift 2 ;;
    --status) need_value "$@"; extra_status="${2:-}"; shift 2 ;;
    --sensor) need_value "$@"; sensor_id="${2:-}"; shift 2 ;;
    --import) import_path=1; shift ;;
    --allow-duplicate) allow_duplicate=1; shift ;;
    --json) json=1; shift ;;
    -h|--help) sed -n '2,50p' "$0"; exit 0 ;;
    *) issues_die "create: unknown argument $1" ;;
  esac
done

issues_resolve "$profile_arg"
profile="$ISSUES_PROFILE"
separator=$(issues_profile_value namespaceSeparator)

issues_preflight "$profile"

if [ "$finding_file" = "-" ]; then
  finding=$(cat)
else
  [ -f "$finding_file" ] || issues_die "create: no such finding file: $finding_file"
  finding=$(cat "$finding_file")
fi
[ -n "$finding" ] || issues_die "create: no finding on stdin — pass the JSON finding or --finding <file>"
printf '%s' "$finding" | jq -e . >/dev/null 2>&1 || issues_die "create: the finding is not valid JSON"

title=$(jq -r '.title // empty' <<< "$finding")
clazz=$(jq -r '.class // empty' <<< "$finding")
category=$(jq -r '.category // empty' <<< "$finding")
claim=$(jq -r '.claim // .title // empty' <<< "$finding")
anchor=$(jq -r '.anchor // ""' <<< "$finding")
body=$(jq -r '.body // ""' <<< "$finding")
source_id=$(jq -r '.sourceId // ""' <<< "$finding")

[ -n "$title" ] || issues_die "create: the finding has no title"
[ -n "$clazz" ] || issues_die "create: the finding has no class"
[ -n "$category" ] || issues_die "create: the finding has no category"

# ── the shape (`backlog-source-of-truth`, "A finding is filed in one of two shapes") ──────────────────────────
#
# A worker that may not ask can only be handed an item that leaves it nothing to decide, so the filing path
# refuses a finding that states neither of the two ways work arrives here:
#
#   tier A  a `cause` — the file:line of the mechanism, the case that exercises it, and the tallies it reported;
#   tier B  a `change` — the OpenSpec change the work will be done as, which must pass the project's readiness
#           check and whose answered questions travel onto the item.
#
# Both is a refusal too: a proven fix and a planned change are different work, and which one the item is worked as
# is not a decision this writer may take silently. The check registry's path (`--sensor <id>`) is the one caller
# whose cause is the failing case the registry already names, and it records that exemption on the item rather
# than leaving the shape question unanswered (design D4).
cause=$(jq -c 'if has("cause") then .cause else empty end' <<< "$finding")
change=$(jq -r 'if has("change") then (.change // "") else "" end' <<< "$finding")
questions=$(jq -c 'if has("questions") then .questions else empty end' <<< "$finding")

cause_type=$(jq -r 'if has("cause") then (.cause | type) else "absent" end' <<< "$finding")
change_type=$(jq -r 'if has("change") then (.change | type) else "absent" end' <<< "$finding")
[ "$cause_type" = "absent" ] || [ "$cause_type" = "object" ] ||
  issues_die "create: the finding's cause is a $cause_type — it must be an object with file, line and case"
[ "$change_type" = "absent" ] || [ "$change_type" = "string" ] ||
  issues_die "create: the finding's change is a $change_type — it must be the change's name"

shape=""
exempt_reason=""
if [ -n "$sensor_id" ] && [ "$cause_type" = "absent" ] && [ -z "$change" ]; then
  shape="exempt"; exempt_reason="sensor ${sensor_id}"
elif [ "$import_path" -eq 1 ] && [ "$cause_type" = "absent" ] && [ -z "$change" ]; then
  # The reconciler's findings are historical records — a source adapter's reading of a record that predates the
  # shapes — so the exemption is declared here too, and the item says who filed it (design D4).
  [ -n "$source_id" ] || issues_die "create: --import needs the finding's sourceId — an imported item with no source identity is not reconcilable"
  shape="exempt"; exempt_reason="import ${source_id}"
elif [ "$cause_type" != "absent" ] && [ -n "$change" ]; then
  issues_refuse "shape-ambiguous" \
    "the finding carries both a cause and a change — a proven fix and a planned change are different work, and the item may carry only the one it will be worked as; drop one of them"
elif [ "$cause_type" != "absent" ]; then
  shape="a"
elif [ -n "$change" ]; then
  shape="b"
else
  issues_refuse "shape-missing" \
    "the finding names no shape — carry a cause (file, line and the case that exercises it) for work whose fix is proven, or a change (the OpenSpec change it will be worked as, with its answered questions) for work that needs a plan; nothing was written"
fi

# A body the tooling accepts must cite something that resolves. A finding is written once and read many times,
# and an unresolvable citation costs the item a reader before anyone decides whether it is work — which is what
# happened to #181, #182, #184 and #185 (#189). Since #281 the pre-screen no longer excludes an item for a
# citation: it reports the token in the record and queues the item, so this is the check's *only* remaining
# citation refusal. Only the shape the author can repair mechanically is refused here: a token that names its
# own directory twice, whose collapsed form is a tracked path. Everything else stays the repair path's business
# (`tools/issues/resolve-stale.sh`), because a body may legitimately cite something the change itself creates.
ISSUES_CITATION_ROOT="${ISSUES_CITATION_ROOT:-$ISSUES_ROOT}"
export ISSUES_CITATION_ROOT
if [ -z "${ISSUES_CITATION_INDEX:-}" ]; then
  ISSUES_CITATION_INDEX=$(mktemp 2>/dev/null) || ISSUES_CITATION_INDEX=""
  export ISSUES_CITATION_INDEX
  if [ -n "$ISSUES_CITATION_INDEX" ]; then
    trap 'rm -f "$ISSUES_CITATION_INDEX"' EXIT
    issues_citation_index_build "$ISSUES_CITATION_ROOT" "$ISSUES_CITATION_INDEX" || true
  fi
fi
while IFS= read -r cited; do
  [ -n "$cited" ] || continue
  [ "$(issues_citation_class "$cited")" = "absent" ] || continue
  collapsed=$(issues_citation_collapsed "$cited")
  [ -n "$collapsed" ] || continue
  [ "$(issues_citation_class "$collapsed")" != "absent" ] || continue
  issues_die "create: the finding cites '$cited' — the repository's path is '$collapsed' (the citation repeats its own directory); fix the payload and file again, nothing was written"
done < <(issues_citations_of "$title
$body")

# ── the shape's own validation, now that the citation index exists ──────────────────────────────────────
#
# A cause is a pointer and a case: the pointer resolves through the same rule the body above passes, so this adds
# no second definition of "a path that resolves", and the line it names is inside the file the tree holds. The
# tallies are recorded rather than required — a cause whose red case has not run is still a cause, but a cause
# with no case is a hypothesis (design D3).
cause_file=""
if [ "$shape" = "a" ]; then
  cause_file=$(jq -r '.file // empty' <<< "$cause")
  cause_line=$(jq -r '.line // empty' <<< "$cause")
  cause_case=$(jq -r '.case // empty' <<< "$cause")
  if [ -z "$cause_file" ] || [ -z "$cause_line" ]; then
    issues_refuse "cause-unresolved" \
      "the cause names no file:line — a cause without its pointer cannot be checked in the tree today, which is the loop's first gate"
  fi
  case "$cause_line" in
    ''|*[!0-9]*) issues_refuse "cause-unresolved" "the cause's line '$cause_line' is not a line number";;
  esac
  if [ -z "$cause_case" ]; then
    issues_refuse "cause-without-case" \
      "the cause names no case — a cause with no red case that exercises it is a hypothesis, and the case is the only thing that tells the two apart"
  fi
  resolved=$(issues_citation_resolve "$cause_file")
  if [ -z "$resolved" ]; then
    issues_refuse "cause-unresolved" \
      "the cause cites '$cause_file' and no path in the tree resolves it; fix the pointer and file again, nothing was written"
  fi
  cause_lines=$(wc -l < "${ISSUES_CITATION_ROOT:-$ISSUES_ROOT}/$resolved" 2>/dev/null | tr -d ' ')
  if [ -n "$cause_lines" ] && [ "$cause_line" -gt "$cause_lines" ]; then
    issues_refuse "cause-unresolved" \
      "the cause cites '$resolved:$cause_line' but the tree's file has $cause_lines line(s)"
  fi
fi

# A change is a directory the tree holds, a readiness check the profile names, and the answers to the forks the
# change leaves: a name nobody can open is a plan nobody wrote, and an item that arrives with `recorded: none`
# hands the worker back the fork the shape exists to settle.
change_dir=""
if [ "$shape" = "b" ]; then
  change_dir="$(issues_changes_dir)/$change"
  if [ ! -d "$change_dir" ]; then
    issues_refuse "change-not-found" \
      "no change directory at $change_dir — a change name no tree holds is a plan nobody wrote; create it with 'openspec new change $change'"
  fi
  gate_key="checks.changeReady"
  gate=$(issues_profile_value "$gate_key" 2>/dev/null | head -1)
  if [ -z "$gate" ]; then
    issues_refuse "gate-not-configured" \
      "the profile declares no $gate_key — an empty value says this project has no readiness check, so a tier-B finding cannot be filed here; file it with a cause instead"
  fi
  case "$gate" in
    /*) gate_path="$gate" ;;
    *)  gate_path="$ISSUES_ROOT/$gate" ;;
  esac
  if [ ! -f "$gate_path" ]; then
    issues_refuse "gate-not-configured" \
      "$gate_key names $gate, and no file is there — a gate this writer cannot reach is a gate it must not pretend to have run"
  fi
  gate_out=$(bash "$gate_path" "$change_dir" 2>&1)
  gate_status=$?
  verdict=$(printf '%s\n' "$gate_out" | grep -m1 '^LOOP-CHANGE-READY:' || true)
  if [ "$gate_status" -ne 0 ] || [ -z "$verdict" ] || [ "${verdict##*: }" != "ok" ]; then
    issues_refuse "change-not-ready" \
      "the change '$change' is not ready to be worked unattended: ${verdict:-the check at $gate printed no verdict line} — shape the change first (openspec/changes/$change), then file it"
  fi
  question_count=$(jq -r '(.questions // []) | length' <<< "$finding" 2>/dev/null || printf '0')
  case "$question_count" in ''|*[!0-9]*) question_count=0 ;; esac
  if [ "$question_count" -eq 0 ]; then
    issues_refuse "decision-missing" \
      "the tier-B finding carries no questions — a worker that reads 'recorded: none' re-derives the fork the item was supposed to hand it; carry the questions the change left open with their answers"
  fi
  missing=$(jq -r '[.questions[]? | select((.decision // "") == "") | (.question // "(unnamed)")] | join("; ")' <<< "$finding")
  if [ -n "$missing" ]; then
    issues_refuse "decision-missing" \
      "the tier-B finding carries a question with no decision: $missing"
  fi
fi

# Every route a label can arrive through — the finding's own array, --label and --status — lands in
# `labels` before the single guarded check below, so guarding one flag is never the invariant (design D2).
labels=()
while IFS= read -r label; do
  [ -n "$label" ] && labels+=("$label")
done < <(jq -r '.labels[]?' <<< "$finding")
for label in "${extra_labels[@]}"; do
  labels+=("$label")
done
if [ -n "$extra_status" ]; then
  labels+=("status${separator}${extra_status}")
fi

# The finding's `class` and `category` are not decoration: this script refuses a finding that omits either, so
# both reach the tracker as labels (#201 — #199 and #200 were filed from findings that carried both fields and
# arrived with `labels: []`, invisible to every selector). They join the set after every other route and an
# exact duplicate is dropped, so a caller that already spells them in `.labels[]` (the sensor path does) files
# one label per value, while a contradiction survives the dedup and is the namespace-uniqueness refusal below
# rather than a silent override of what the caller wrote. They go through the same vocabulary gate as any
# other label, so a misspelt class is refused instead of dropped.
labels+=("class${separator}${clazz}" "category${separator}${category}")
declare -A seen_label=()
merged_labels=()
for label in "${labels[@]}"; do
  [ -n "$label" ] || continue
  [ -z "${seen_label[$label]:-}" ] || continue
  seen_label[$label]=1
  merged_labels+=("$label")
done
labels=("${merged_labels[@]}")

# The ownership label a sensor files under. Its vocabulary is the registry's, not the profile's list: the
# namespace is *declared* in the profile and its values are the rows (design D9/D12), so an id the registry
# does not declare is refused with the same reason a value outside any vocabulary gets, and no label is
# created by a typo.
sensor_label=""
if [ -n "$sensor_id" ]; then
  sensor_label=$(issues_sensor_label "$sensor_id")
  [ -n "$sensor_label" ] || issues_refuse "label-opaque" \
    "sensor/$sensor_id is not a declared check — the registry declares: $(issues_sensor_ids | tr '\n' ' ')"
  labels+=("$sensor_label")
fi

if [ "${#labels[@]}" -gt 0 ]; then
  gate_labels=()
  for label in "${labels[@]}"; do
    # the ownership label is validated above, against its own vocabulary, so it is not handed to the
    # profile's gate — which knows the namespace but not the registry's values
    [ -n "$sensor_label" ] && [ "$label" = "$sensor_label" ] && continue
    gate_labels+=("$label")
  done
  if [ "${#gate_labels[@]}" -gt 0 ]; then
    issues_check_labels "$profile" "${gate_labels[@]}"
  fi
fi

# A created item carries no band: the pair is the label *and* the body's marker, and the body a create writes
# is the finding's — it holds no marker for the pass to agree with, so any band label named here would be the
# drift by construction. The metadata pass writes the pair, and nothing else may write half of it
# (`lib.sh`, "the band pair"; issue #671).
issues_band_pair_gate "$profile" "" "${labels[@]}" --

# One namespace, one value. A merged set names two values of one namespace exactly when two labels share the
# prefix before the separator — and every label here has passed the vocabulary gate, so that prefix is a
# declared namespace. At filing time there is nothing to displace, so the rule is a refusal, not the
# replacement `update.sh` performs for the state namespace; naming two values is a contradictory request and
# exits 2 with a reason naming both, before any `gh issue create` (`issue-backlog`, "Labels carry the item's
# kind, area, state and provenance").
declare -A seen_namespace=()
for label in "${labels[@]}"; do
  namespace="${label%%${separator}*}"
  previous="${seen_namespace[$namespace]:-}"
  if [ -n "$previous" ] && [ "$previous" != "$label" ]; then
    issues_die "create: $previous and $label name two values of one namespace — an item carries one value per namespace"
  fi
  seen_namespace[$namespace]="$label"
done

# The dedup anchor: a sensor's condition fingerprint when the finding carries one, and the digest of the
# structured fields otherwise (`sensor-registry-and-metric-issues`, D4).
fingerprint=$(jq -r '.fingerprint // empty' <<< "$finding")
if [ -z "$fingerprint" ]; then
  fingerprint=$(issues_core Fingerprint "class=$clazz" "category=$category" "claim=$claim" "anchor=$anchor")
fi
[ -n "$fingerprint" ] || issues_die "create: could not compute a fingerprint"

existing=$(issues_issue_list)
match=$(printf '%s' "$existing" | issues_core Fingerprint --match "$fingerprint")
if [ -n "$match" ] && [ "$allow_duplicate" -eq 0 ]; then
  if [ "$json" -eq 1 ]; then
    jq -n --argjson issue "$match" --arg fingerprint "$fingerprint" \
      '{created: false, outcome: "duplicate", issue: $issue, fingerprint: $fingerprint, overridden: false}'
  else
    printf 'duplicate of #%s — fingerprint %s already exists; pass --allow-duplicate to file anyway\n' \
      "$match" "$fingerprint"
  fi
  exit "$ISSUES_EXIT_DUPLICATE"
fi
overridden=false
[ -n "$match" ] && overridden=true

new_body="$body"
if [ -n "$source_id" ]; then
  new_body="${new_body}"$'\n'"<!-- nvid: ${source_id} -->"
fi
new_body="${new_body}"$'\n'"<!-- fingerprint: ${fingerprint} -->"
if [ -n "$sensor_label" ]; then
  # The recurrence count starts here, and the reopen path increments it: one writer, one home.
  new_body="${new_body}"$'\n'"<!-- occurrence: 1 -->"
fi
# The shape, recorded through the one writer the body record has (`issues_body_set`), so the key a reader looks
# for cannot be spelled differently here. A sensor finding carries neither tier: it names what exempted it,
# beside the occurrence count, rather than leaving the shape question unanswered (design D4).
case "$shape" in
  a|b) new_body=$(issues_body_set "$new_body" tier "$shape") ;;
  exempt) new_body=$(issues_body_set "$new_body" exempt "$exempt_reason") ;;
esac
if [ "$shape" = "b" ]; then
  new_body=$(issues_body_set "$new_body" change "$change")
fi

# The state label is the item's mode, and a tier-B item's answers are recorded before the mode moves
# (`decision-queue`, "the answer is recorded before the state changes"): the create call carries every other
# label, the answers are commented next, and only then is the state set. An item whose state said `open` before
# its answers were on it would be a worker's package claiming a decision nobody had written yet.
create_labels=()
held_labels=()
for label in "${labels[@]}"; do
  if [ "$shape" = "b" ] && [ "${label%%${separator}*}" = "status" ]; then
    held_labels+=("$label")
  else
    create_labels+=("$label")
  fi
done

args=(issue create --title "$title" --body "$new_body")
for label in "${create_labels[@]}"; do
  args+=(--label "$label")
done

url=$(gh "${args[@]}") || exit $?
number="${url##*/}"

if [ "$shape" = "b" ]; then
  decisions=0
  while IFS=$'\t' read -r asked answered; do
    [ -n "$asked" ] || continue
    # The two halves of the form `tools/issues/answer.sh` writes and `WorkPackage.java` reads, read from the one home
    # `tools/issues/lib.sh` declares for them (issue #859): the `Decision:` line is the marker that makes a comment an
    # answer, and the marker under it names the question it settles (#196). #845 gave the shape that home and this path
    # went on spelling both halves out, so a mutation of either constant moved nothing here.
    asked=${asked//>/}
    comment="${ISSUES_ANSWER_PREFIX}${answered}"$'\n'"${ISSUES_ANSWER_MARKER}${asked} -->"
    gh issue comment "$number" --body "$comment" >/dev/null || exit $?
    decisions=$((decisions + 1))
  done < <(jq -r '.questions[]? | [(.question // ""), (.decision // "")] | @tsv' <<< "$finding")
  for label in "${held_labels[@]}"; do
    gh issue edit "$number" --add-label "$label" >/dev/null || exit $?
  done
fi

case "$shape" in
  a) shape_line="filed as tier a — the cause ${cause_file}:${cause_line} and the case '$cause_case'" ;;
  b) shape_line="filed as tier b — the change '$change', ${decisions} recorded decision(s)" ;;
  *) shape_line="filed as an exempt finding — exempt from the shape: ${exempt_reason} is its cause" ;;
esac

if [ "$json" -eq 1 ]; then
  label_json=$(if [ "${#labels[@]}" -gt 0 ]; then printf '%s\n' "${labels[@]}"; fi | jq -R . | jq -s .)
  jq -n --arg title "$title" --arg fingerprint "$fingerprint" \
        --argjson labels "$label_json" --argjson overridden "$overridden" \
        --arg number "$number" --arg shape "$shape" --arg change "$change" \
        --arg cause "${cause_file}${cause_file:+:}${cause_line:-}" \
        --argjson decisions "${decisions:-0}" \
        '{created: true, outcome: "created", number: ($number | tonumber? // $number), title: $title,
          labels: $labels, fingerprint: $fingerprint, overrideRecorded: $overridden,
          shape: $shape, change: (if $change == "" then null else $change end),
          cause: (if $cause == "" then null else $cause end), decisions: $decisions}'
else
  if [ "$overridden" = true ]; then
    printf 'note: --allow-duplicate overrides an existing fingerprint (%s)\n' "$fingerprint"
  fi
  printf '%s\n' "$url"
  printf '%s\n' "$shape_line"
fi
exit "$ISSUES_EXIT_OK"
