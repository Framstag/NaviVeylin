#!/usr/bin/env bash
# check-metadata.sh — the priority invariant, as a command.
#
# Four things can be true of an issue's priority, and a hand edit can break any of them: the band label is
# inside the vocabulary, an issue carries at most one band, the marker and the label are both there, and
# they agree with each other. This script asserts all four (`issue-metadata-loop`, D1/D7), plus the two
# things that make the pass safe: the marker's score is inside the model's range, and the pass never wrote
# the automation opt-in.
#
# Three modes:
#
#   (no argument)        the token-free self-check, and the one CI runs: the profile's bands are total and
#                        well formed, the marker grammar has one home, and neither write path can name or
#                        pass a reserved label. No network, no token, no tracker.
#   --live               read the tracker through `gh issue list --state all` (needs a token) and check the
#                        per-issue invariants on every issue.
#   --issues <file>      the same per-issue checks over a JSON array — what the offline cases and this
#                        script's own selftest drive.
#
# Exit: 0 every check passed, 1 a violation was found (each named on stdout), 2 usage or unusable input.
#
# Usage: check-metadata.sh [--live | --issues <file>] [--write-log <file>] [--json] [--profile <path>]
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/issues/lib.sh
source "$here/lib.sh"

mode="self"
issues_file=""
write_log=""
json=0
profile_arg=""
while [ $# -gt 0 ]; do
  case "$1" in
    --live) mode="live"; shift ;;
    --issues) need_value "$@"; mode="issues"; issues_file="${2:-}"; shift 2 ;;
    --write-log) need_value "$@"; write_log="${2:-}"; shift 2 ;;
    --json) json=1; shift ;;
    --profile) need_value "$@"; profile_arg="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,25p' "$0"; exit 0 ;;
    *) issues_die "check-metadata: unknown argument $1" ;;
  esac
done

issues_resolve "$profile_arg"
profile="$ISSUES_PROFILE"
root="$ISSUES_ROOT"

violations=""
found=0
checked=0
checked_labels=0

violate() { # violate <issue> <code> <detail>
  local issue="$1" code="$2" detail="$3"
  violations+="${issue}	${code}	${detail}"$'\n'
  found=$((found + 1))
}

report() {
  if [ "$json" -eq 1 ]; then
    local rows
    rows=$(printf '%s' "$violations" | grep -v '^$' | awk -F'\t' \
      '{printf "%s{\"issue\":\"%s\",\"code\":\"%s\",\"detail\":\"%s\"}", (NR > 1 ? "," : ""), $1, $2, $3}')
    printf '{"checked":%s,"violations":[%s]}\n' "$checked" "$rows"
  else
    printf '%s' "$violations" | grep -v '^$' | awk -F'\t' '{printf "#%s %s: %s\n", $1, $2, $3}'
    printf 'check-metadata: %s issue(s) checked · %s violation(s)\n' "$checked" "$found"
  fi
  [ "$found" -eq 0 ] || exit 1
  exit "$ISSUES_EXIT_OK"
}

# ── the mechanical half: the bands are total and well formed, and one script owns their spelling ──────

band_edges=$(issues_core Bands edges --profile "$profile") || issues_die "check-metadata: the profile's bands do not parse"
band_range=$(issues_core Bands range --profile "$profile") || issues_die "check-metadata: the profile's scoring table does not parse"
band_ids=$(printf '%s\n' "$band_edges" | awk '{print $1}')
range_min=${band_range%% *}
range_max=${band_range##* }
namespace=$(jq -r '.bands.namespace' "$profile")
separator=$(issues_profile_value namespaceSeparator)

profile_problems=$(issues_core Profile "$profile" problems 2>&1)
[ -z "$profile_problems" ] || violate "profile" "bands-malformed" "$(printf '%s' "$profile_problems" | paste -sd'; ' -)"

# The pass must never write the automation opt-in, and must not even name it (the label is written by the
# reserved-label refusal in update.sh, so naming it in a pass would be the first step to passing it).
for script in prioritise.sh classify.sh; do
  [ -f "$root/tools/issues/$script" ] || continue
  if grep -q "$(issues_core Profile "$profile" reservedLabels | head -1)" "$root/tools/issues/$script"; then
    violate "$script" "names-the-reserved-label" "$(issues_core Profile "$profile" reservedLabels | head -1)"
  fi
done

# The marker grammar has one home. The positive half: the writer reads and writes it through lib.sh's
# helpers. The negative half: no engine script spells the opening delimiter out in code — the scan searches
# for the constant's *value* (so this checker does not spell it either), a comment may name it, and the
# self-tests and the fixtures carry it as data, so only the engine's code lines count.
if [ -f "$root/tools/issues/prioritise.sh" ] &&
   ! grep -q 'issues_prio_marker\|issues_prio_field\|ISSUES_PRIO_MARKER' "$root/tools/issues/prioritise.sh"; then
  violate "prioritise.sh" "marker-grammar-not-shared" "does not write the marker through lib.sh"
fi
spelled=$(grep -n -- "$ISSUES_PRIO_MARKER_OPEN" "$root"/tools/issues/*.sh 2>/dev/null |
  grep -v 'lib\.sh:' | grep -v -- '-selftest\.sh:' | grep -vE ':[0-9]+:[[:space:]]*#' || true)
[ -z "$spelled" ] || violate "engine" "marker-grammar-duplicated" "$(printf '%s' "$spelled" | paste -sd'; ' -)"

if [ -n "$write_log" ]; then
  [ -f "$write_log" ] || issues_die "check-metadata: no such write log: $write_log"
  opt_in=$(issues_core Profile "$profile" reservedLabels | head -1)
  if grep -q -- "$opt_in" "$write_log"; then
    violate "write-log" "wrote-the-opt-in" "$(grep -m1 -- "$opt_in" "$write_log")"
  fi
  checked_labels=$((checked_labels + 1))
fi

if [ "$mode" = "self" ]; then
  report
fi

# ── the per-issue half: the label and the marker, and whether they agree ──────────────────────────────

if [ "$mode" = "live" ]; then
  issues_json=$(gh issue list --state all --limit 400 --json number,title,labels,body 2>/dev/null) ||
    issues_die "check-metadata: gh could not read the tracker (--live needs a token)"
elif [ "$mode" = "issues" ]; then
  [ -f "$issues_file" ] || issues_die "check-metadata: no such issue file: $issues_file"
  issues_json=$(cat "$issues_file")
  printf '%s' "$issues_json" | jq -e . >/dev/null 2>&1 ||
    issues_die "check-metadata: $issues_file is not valid JSON"
fi

issue_numbers=$(printf '%s' "$issues_json" | jq -r '.[].number')
for number in $issue_numbers; do
  checked=$((checked + 1))
  labels=$(printf '%s' "$issues_json" | jq -r --argjson n "$number" \
    '[.[] | select(.number == $n) | .labels[]? | if type == "string" then . else .name end] | .[]')
  body=$(printf '%s' "$issues_json" | jq -r --argjson n "$number" '[.[] | select(.number == $n) | .body // ""] | .[0]')

  bands_found=0
  band_label=""
  while IFS= read -r label; do
    [ -n "$label" ] || continue
    case "$label" in
      "$namespace${separator}"*)
        value="${label#"$namespace"$separator}"
        bands_found=$((bands_found + 1))
        band_label="$value"
        if ! printf '%s\n' "$band_ids" | grep -qxF "$value"; then
          violate "$number" "band-out-of-vocabulary" "$label"
        fi
        ;;
    esac
  done <<< "$labels"
  if [ "$bands_found" -gt 1 ]; then
    violate "$number" "multiple-bands" "$bands_found priority labels"
  fi

  marker=$(issues_prio_marker "$body")
  if [ -n "$marker" ]; then
    missing=""
    for field in $ISSUES_PRIO_FIELDS; do
      [ -n "$(issues_prio_field "$marker" "$field")" ] || missing="$missing $field"
    done
    [ -z "$missing" ] || violate "$number" "marker-incomplete" "missing:$missing"

    marker_band=$(issues_prio_field "$marker" band)
    marker_score=$(issues_prio_field "$marker" score)
    if [ -z "$band_label" ]; then
      violate "$number" "marker-without-label" "$marker_band"
    elif [ "$marker_band" != "$band_label" ]; then
      violate "$number" "band-mismatch" "marker=$marker_band label=$namespace$separator$band_label"
    fi
    case "$marker_score" in
      ''|*[!0-9]*) violate "$number" "score-not-a-number" "$marker_score" ;;
      *) if [ "$marker_score" -lt "$range_min" ] || [ "$marker_score" -gt "$range_max" ]; then
           violate "$number" "score-out-of-range" "$marker_score not in $range_min..$range_max"
         fi ;;
    esac
  elif [ -n "$band_label" ]; then
    violate "$number" "label-without-marker" "$namespace$separator$band_label"
  fi
done

report
