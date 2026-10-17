#!/usr/bin/env bash
# Create or refresh the label taxonomy from the profile and the check registry.
#
# `--dry-run` is the default and prints the diff it would apply; `--apply` creates the missing labels with
# `--force`, so re-running is a no-op. The taxonomy is the project's own vocabulary, from both of its
# homes: the profile's namespace values, and the `sensor` namespace's values, which are the rows of the
# check registry (`sensor-registry-and-metric-issues`, D9/D12). This script names no label and no project
# value (design D5, D11).
#
# Usage: labels.sh [--dry-run|--apply] [--json] [--profile <path>]
# Exit:  0 ok, 2 usage, 3 policy refusal
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/issues/lib.sh
source "$here/lib.sh"

apply=0
json=0
profile_arg=""
while [ $# -gt 0 ]; do
  case "$1" in
    --dry-run) apply=0; shift ;;
    --apply) apply=1; shift ;;
    --json) json=1; shift ;;
    --profile) profile_arg="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,11p' "$0"; exit 0 ;;
    *) issues_die "labels: unknown argument $1" ;;
  esac
done

issues_resolve "$profile_arg"
profile="$ISSUES_PROFILE"
separator=$(issues_profile_value namespaceSeparator)
taxonomy=$(
  {
    issues_profile_value namespaces | while read -r namespace; do
      issues_profile_value "namespaces.$namespace" | while read -r value; do
        printf '%s%s%s\n' "$namespace" "$separator" "$value"
      done
    done
    # The check registry declares the `sensor` namespace's values, so a check's own label is created from
    # the same declaration that files under it — one owner, and no second list to keep in step.
    while IFS= read -r id; do
      [ -n "$id" ] || continue
      label=$(issues_sensor_label "$id")
      [ -n "$label" ] || continue
      printf '%s\n' "$label"
    done < <(issues_sensor_ids)
  } | sort -u
)

existing=$(gh label list --limit 400 --json name | jq -r '.[].name' | sort -u)
to_create=$(comm -23 <(printf '%s\n' "$taxonomy") <(printf '%s\n' "$existing"))

label_color() {
  printf '%s' "$1" | cksum | awk '{printf "%06x", $1 % 16777216}'
}

applied=0
if [ "$apply" -eq 1 ]; then
  applied=1
  while IFS= read -r label; do
    [ -n "$label" ] || continue
    namespace=${label%%"$separator"*}
    gh label create "$label" --force --color "$(label_color "$label")" \
      --description "$namespace — from the project profile" >/dev/null
  done <<< "$to_create"
fi

if [ "$json" -eq 1 ]; then
  # Only non-empty lines become array entries: an empty diff must be `[]`, not `[""]`.
  jq -n --argjson applied "$applied" \
        --argjson taxonomy "$(printf '%s\n' "$taxonomy" | grep -v '^$' | jq -R . | jq -s .)" \
        --argjson existing "$(printf '%s\n' "$existing" | grep -v '^$' | jq -R . | jq -s .)" \
        --argjson toCreate "$(printf '%s\n' "$to_create" | grep -v '^$' | jq -R . | jq -s .)" \
        '{applied: ($applied == 1), taxonomy: $taxonomy, existing: $existing, toCreate: $toCreate}'
else
  while IFS= read -r label; do
    [ -n "$label" ] || continue
    printf '+ %s\n' "$label"
  done <<< "$to_create"
  printf '%s to create, %s already present\n' \
    "$(printf '%s\n' "$to_create" | grep -c . || true)" \
    "$(printf '%s\n' "$existing" | grep -c . || true)"
fi
exit "$ISSUES_EXIT_OK"
