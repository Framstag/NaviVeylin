#!/usr/bin/env bash
# stale-citations.sh — the residue row's measurement: how many items still carry a citation nothing in the
# repository accounts for.
#
# It does not re-implement the count. It runs `tools/issues/resolve-stale.sh` in **dry-run** mode and reports its
# own counters, so the row that watches the residue and the script that repairs it can never disagree about what
# the residue is (`stale-citation-resolution` D6) — the same reason the pre-screen and the resolver share one
# classifier.
#
# The count is the *whole* residue, not only what a run would park now: `--revisit-parked` makes the resolver
# visit the items it parked itself, so `parked` (new this run) and `stillParked` (held with a citation nothing
# accounts for) together are every item the mechanism cannot settle. Without the flag a parked item is skipped
# and the row reported 0 while items sat unresolvable (#281).
#
# A growing count is the alarm for the whole mechanism: it means either that the ladder stopped reaching
# something it used to repair, or that the scheduled repair is not running.
#
# Inputs (set by `run.sh`): SENSORS_THRESHOLD (the bound), SENSORS_ROOT, SENSORS_NAMESPACE.
set -uo pipefail
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/sensors/lib.sh
source "$here/../lib.sh"

bound="${SENSORS_THRESHOLD:-5}"
report=$(bash "$SENSORS_ROOT/tools/issues/resolve-stale.sh" --json --revisit-parked \
  --max-items "${SENSORS_STALE_MAX_ITEMS:-1000}" 2>/dev/null)
if [ -z "$report" ] || ! jq -e . >/dev/null 2>&1 <<< "$report"; then
  sensors_emit_error '{"kind":"unresolved-citations"}' "unreadable-resolver-report"
  exit 0
fi

count=$(jq -r '((.parked // 0) + (.stillParked // 0)) | tostring' <<< "$report")
fields='{"kind":"unresolved-citations"}'
fingerprint=$(sensors_fingerprint "$fields" "kind")
printf 'unresolved citations: %s (bound %s)\n' "$count" "$bound" >&2
if sensors_compare "$count" "$bound" greater-than; then
  sensors_emit "$fingerprint" "$count" violation "$fields" "" \
    "citations nothing accounts for have piled up" \
    "$count item(s) carry an unresolvable citation, above the bound of $bound"
else
  sensors_emit "$fingerprint" "$count" clear "$fields" "" \
    "the unresolved citations are within their bound" \
    "$count item(s) carry an unresolvable citation, at or below the bound of $bound"
fi
