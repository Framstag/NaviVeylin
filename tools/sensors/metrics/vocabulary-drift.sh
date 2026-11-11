#!/usr/bin/env bash
# vocabulary-drift.sh — the drift row's measurement: every refusal the glossary check prints, grouped by the
# refusal word rather than by the term (`vocabulary-projection`, "The drift measurement files under one identity
# per refusal").
#
# The identity matters as much as the count. A row whose fingerprint were the term would file one issue per
# drifting word; a bad night — a rename that leaves fifty spellings behind — would then be fifty issues, and
# the one thing a person needs to see (one class of refusal, fifty terms in it) would be the one thing the
# tracker could not show. So the fingerprint's one field is `refusal`, and every term of one class is listed in
# that condition's claim.
#
# A row with no measurement on record reports and writes nothing (`SENSORS_NO_RECORD`): the first scheduled run
# is an observation, not a filing. The bound is the registry row's — `threshold: 0`, `greater-than`, `consecutive:
# 2`, `maxFilings: 2` — and a class that keeps returning is handed to a person by the registry's own
# `escalation.maxRecurrences`, which this measurement never overrides.
#
# It does not re-implement the refusals: it runs `tools/check-glossary.sh --json` and groups what that printed,
# so the check and the row can never disagree about what a finding is.
#
# Inputs (set by run.sh): SENSORS_THRESHOLD, SENSORS_ROOT, SENSORS_NAMESPACE.
# Test seam: SENSORS_EMIT_LOG appends every emission it prints, and SENSORS_NO_RECORD is the first-run case.
set -uo pipefail
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/sensors/lib.sh
source "$here/../lib.sh"

bound="${SENSORS_THRESHOLD:-0}"
report=$(bash "$SENSORS_ROOT/tools/check-glossary.sh" --json 2>/dev/null)
if [ -z "$report" ]; then
  sensors_emit_error '{"kind":"vocabulary-drift"}' "unreadable-glossary-report"
  exit 0
fi

# one condition per refusal class: the refusal is the identity, the terms are the claim
classes=$(printf '%s\n' "$report" | jq -r 'select(.refusal != null) | .refusal' | sort -u)
if [ -z "$classes" ]; then
  sensors_emit "$(sensors_fingerprint '{"kind":"vocabulary-drift"}' "kind")" 0 clear \
    '{"kind":"vocabulary-drift"}' "" "the declared vocabulary holds" \
    "no refusal stands over the declaration"
  exit 0
fi

would_file=0
for refusal in $classes; do
  terms=$(printf '%s\n' "$report" | jq -r --arg r "$refusal" 'select(.refusal == $r) | .key' | sort -u)
  count=$(printf '%s\n' "$terms" | grep -c . || true)
  fields=$(jq -cn --arg r "$refusal" '{kind: "vocabulary-drift", refusal: $r}')
  fingerprint=$(sensors_fingerprint "$fields" "refusal")
  claim=$(printf '%s\n' "$terms" | paste -sd, - | sed 's/,/, /g')
  would_file=$((would_file + 1))
  if [ -n "${SENSORS_NO_RECORD:-}" ]; then
    printf 'vocabulary-drift: would file %s: %s term(s) — %s\n' "$refusal" "$count" "$claim" >&2
    continue
  fi
  if sensors_compare "$count" "$bound" greater-than; then
    line=$(sensors_emit "$fingerprint" "$count" violation "$fields" "" \
      "vocabulary drift: $refusal ($count term(s))" \
      "$refusal: $claim")
    printf '%s\n' "$line"
    [ -n "${SENSORS_EMIT_LOG:-}" ] && printf '%s\t%s\t%s\n' "$refusal" "$count" "$claim" >> "$SENSORS_EMIT_LOG"
  else
    line=$(sensors_emit "$fingerprint" "$count" clear "$fields" "" \
      "the declared vocabulary holds" \
      "$refusal: $claim")
    printf '%s\n' "$line"
    [ -n "${SENSORS_EMIT_LOG:-}" ] && printf '%s\t%s\t%s\n' "$refusal" "$count" "$claim" >> "$SENSORS_EMIT_LOG"
  fi
done
if [ -n "${SENSORS_NO_RECORD:-}" ]; then
  printf 'vocabulary-drift: %s condition(s) would be filed; nothing is written on a first run\n' "$would_file" >&2
fi
exit 0
