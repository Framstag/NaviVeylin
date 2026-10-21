#!/usr/bin/env bash
# The reconciler: findings JSON in, issues out. It knows no backlog format — a source adapter produces the
# findings and this script turns them into issues, idempotently on the source id (design D3, D11).
#
# `--dry-run` is the default and prints one preview per finding; `--apply` is required to create anything.
# A finding whose source id already exists in the tracker is skipped and reported; when every finding is
# already present the run exits 4 — "nothing to do", not a failure.
#
# Exit: 0 ok/previews, 2 usage, 3 policy, 4 everything already present.
#
# Usage: import.sh [--findings <file|->] [--dry-run|--apply] [--json] [--profile <path>]
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/issues/lib.sh
source "$here/lib.sh"

profile_arg=""
json=0
apply=0
findings_file="-"
while [ $# -gt 0 ]; do
  case "$1" in
    --profile) need_value "$@"; profile_arg="${2:-}"; shift 2 ;;
    --findings) need_value "$@"; findings_file="${2:-}"; shift 2 ;;
    --dry-run) apply=0; shift ;;
    --apply) apply=1; shift ;;
    --json) json=1; shift ;;
    -h|--help) sed -n '2,13p' "$0"; exit 0 ;;
    *) issues_die "import: unknown argument $1" ;;
  esac
done

issues_resolve "$profile_arg"
profile="$ISSUES_PROFILE"
issues_preflight "$profile"

if [ "$findings_file" = "-" ]; then
  document=$(cat)
else
  [ -f "$findings_file" ] || issues_die "import: no such findings file: $findings_file"
  document=$(cat "$findings_file")
fi
[ -n "$document" ] || issues_die "import: no findings on stdin — pass the findings document or --findings <file>"
printf '%s' "$document" | jq -e '.findings' >/dev/null 2>&1 || issues_die "import: the findings document is not valid JSON with a findings array"

count=$(printf '%s' "$document" | jq '.findings | length')
existing=$(issues_issue_list)

previews=$(mktemp) || issues_die "import: cannot create a temporary file"
skipped=$(mktemp) || issues_die "import: cannot create a temporary file"
trap 'rm -f "$previews" "$skipped"' EXIT
: > "$previews"
: > "$skipped"

created=0
while IFS= read -r finding; do
  source_id=$(jq -r '.sourceId // empty' <<< "$finding")
  title=$(jq -r '.title // empty' <<< "$finding")
  fingerprint=$(jq -r '.fingerprint // empty' <<< "$finding")
  already=""
  if [ -n "$source_id" ]; then
    already=$(printf '%s' "$existing" | jq -r --arg marker "<!-- nvid: ${source_id} -->" \
      '[.[] | select((.body | contains($marker)))][0].number // empty')
  fi
  if [ -n "$already" ]; then
    printf '%s\t%s\n' "$source_id" "$already" >> "$skipped"
    continue
  fi
  printf '%s\t%s\t%s\t%s\n' "$source_id" "$title" "$fingerprint" \
    "$(jq -r '[.labels[]?] | join(",")' <<< "$finding")" >> "$previews"
  if [ "$apply" -eq 1 ]; then
    create_out=$(printf '%s' "$finding" | "$here/create.sh" --profile "$profile" --json 2>&1)
    status=$?
    if [ "$status" -ne 0 ]; then
      # A refusal is reported, not swallowed: the caller must see why nothing was created.
      [ "$status" -eq "$ISSUES_EXIT_DUPLICATE" ] || { printf '%s\n' "$create_out"; exit "$status"; }
    fi
    created=$((created + 1))
  fi
done < <(printf '%s' "$document" | jq -c '.findings[]')

preview_count=$(grep -c . "$previews" || true)
skipped_count=$(grep -c . "$skipped" || true)
histogram=$(printf '%s' "$document" | jq -r '[.findings[].labels[]?] | group_by(.) | map({label: .[0], count: length})')
renames=$(printf '%s' "$document" | jq -c '.renames // []')

if [ "$json" -eq 1 ]; then
  jq -n --argjson count "$count" --argjson previews "$preview_count" --argjson skipped "$skipped_count" \
        --argjson created "$created" --argjson applied "$apply" \
        --argjson histogram "$histogram" --argjson renames "$renames" \
        --argjson previewList "$(jq -R -s 'split("\n") | map(select(length > 0) | split("\t") | {sourceId: .[0], title: .[1], fingerprint: .[2], labels: (.[3] | split(","))})' "$previews")" \
        --argjson skippedList "$(jq -R -s 'split("\n") | map(select(length > 0) | split("\t") | {sourceId: .[0], issue: (.[1] | tonumber)})' "$skipped")" \
        '{findings: $count, previews: $previews, skipped: $skipped, created: $created, applied: ($applied == 1),
          renames: $renames, histogram: $histogram, previewList: $previewList, skippedList: $skippedList}'
else
  printf 'findings: %s\n' "$count"
  while IFS=$'\t' read -r source_id title fingerprint labels; do
    [ -n "$source_id$title" ] || continue
    printf '  preview §%s  fingerprint %s  [%s]\n' "$source_id" "${fingerprint:0:12}" "$labels"
  done < "$previews"
  while IFS=$'\t' read -r source_id issue; do
    [ -n "$source_id$issue" ] || continue
    printf '  already present: source id %s is issue #%s\n' "$source_id" "$issue"
  done < "$skipped"
  printf '%s preview(s), %s already present, %s created%s\n' \
    "$preview_count" "$skipped_count" "$created" "$([ "$apply" -eq 1 ] && echo '' || echo ' (dry run)')"
fi

if [ "$preview_count" -eq 0 ] && [ "$skipped_count" -gt 0 ]; then
  exit "$ISSUES_EXIT_DUPLICATE"
fi
exit "$ISSUES_EXIT_OK"
