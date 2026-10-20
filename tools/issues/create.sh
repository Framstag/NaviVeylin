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
# Exit: 0 created, 2 usage/malformed, 3 policy, 4 duplicate.
#
# Usage: create.sh [--finding <file>] [--label <label>]… [--status <value>] [--sensor <id>]
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
while [ $# -gt 0 ]; do
  case "$1" in
    --profile) need_value "$@"; profile_arg="${2:-}"; shift 2 ;;
    --finding) need_value "$@"; finding_file="${2:-}"; shift 2 ;;
    --label) need_value "$@"; extra_labels+=("${2:-}"); shift 2 ;;
    --status) need_value "$@"; extra_status="${2:-}"; shift 2 ;;
    --sensor) need_value "$@"; sensor_id="${2:-}"; shift 2 ;;
    --allow-duplicate) allow_duplicate=1; shift ;;
    --json) json=1; shift ;;
    -h|--help) sed -n '2,29p' "$0"; exit 0 ;;
    *) issues_die "create: unknown argument $1" ;;
  esac
done

issues_resolve "$profile_arg"
profile="$ISSUES_PROFILE"
separator=$(issues_profile_value namespaceSeparator)

login=$(issues_login)
issues_preflight "$profile" "$login"

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

# A body the tooling accepts must cite something that resolves. A finding is written once and read many times,
# and a citation the pre-screen cannot resolve costs the item every run — it is excluded as `stale` before
# anyone reads it, which is what happened to #181, #182, #184 and #185 (#189). Only the shape the author can
# repair mechanically is refused here: a token that names its own directory twice, whose collapsed form is a
# tracked path. Everything else stays the repair path's business (`tools/issues/resolve-stale.sh`), because a
# body may legitimately cite something the change itself creates.
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

args=(issue create --title "$title" --body "$new_body")
for label in "${labels[@]}"; do
  args+=(--label "$label")
done

if [ "$json" -eq 1 ]; then
  label_json=$(if [ "${#labels[@]}" -gt 0 ]; then printf '%s\n' "${labels[@]}"; fi | jq -R . | jq -s .)
  jq -n --arg title "$title" --arg fingerprint "$fingerprint" \
        --argjson labels "$label_json" --argjson overridden "$overridden" \
        '{created: true, outcome: "created", title: $title, labels: $labels, fingerprint: $fingerprint, overrideRecorded: $overridden}'
  gh "${args[@]}" >/dev/null
else
  if [ "$overridden" = true ]; then
    printf 'note: --allow-duplicate overrides an existing fingerprint (%s)\n' "$fingerprint"
  fi
  gh "${args[@]}"
fi
exit "$ISSUES_EXIT_OK"
