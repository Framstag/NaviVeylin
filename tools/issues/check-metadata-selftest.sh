#!/usr/bin/env bash
# check-metadata-selftest.sh — the falsification of `check-metadata.sh`: every check can go **red**, and each
# failure fixture goes **green** again once its defect is removed (`issue-metadata-loop`, D7).
#
# The rule it exists for is the repo's own: a green check proves nothing until it has been shown capable of
# failing, and a failure fixture that stays red after the defect is removed would mean the check is
# over-triggering. Both directions are asserted per case, so neither a blind check nor an over-eager one
# passes.
#
# Usage: bash tools/issues/check-metadata-selftest.sh [--list] [--case <Case#name>]
# Exit:  0 every case passed, 1 a case failed, 2 the environment is unusable.
set -uo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
check="$root/tools/issues/check-metadata.sh"
fixtures="$root/tools/issues/fixtures/metadata"

case "${1:-}" in
  --list) list_only=1; only_case="" ;;
  --case) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; list_only=0; only_case="${2:-}" ;;
  "") list_only=0; only_case="" ;;
  -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
  *) echo "check-metadata-selftest: unknown argument $1" >&2; exit 2 ;;
esac

for tool in bash jq java javac; do
  command -v "$tool" >/dev/null 2>&1 || { echo "check-metadata-selftest: $tool is required" >&2; exit 2; }
done
[ -f "$check" ] || { echo "check-metadata-selftest: $check is missing" >&2; exit 2; }

tmp=$(mktemp -d "${TMPDIR:-/tmp}/check-metadata-selftest.XXXXXX") || {
  echo "check-metadata-selftest: cannot create a scratch directory" >&2
  exit 2
}
trap 'rm -rf "$tmp"' EXIT

CASES=(
  'check#markerLabelMismatchFails'
  'check#outOfRangeBandFails'
  'check#neverWritesEligibility'
  'check#incompleteMarkerFails'
  'check#labelWithoutMarkerFails'
  'check#aQuotationIsNotAMarker'
  'check#aMarkerLineBesideAQuotationIsTheRecord'
  'check#unrankedIsNotAViolation'
  'check#selfCheckIsGreen'
)

if [ "$list_only" -eq 1 ]; then
  printf '%s\n' "${CASES[@]}"
  exit 0
fi

fail_done=0
run_check() { # run_check <args…> → prints the output, returns the exit code in CHECK_STATUS
  CHECK_OUT=$(bash "$check" "$@" 2>&1)
  CHECK_STATUS=$?
}

expect() { # expect <what> <expected> <got>
  if [ "$2" = "$3" ]; then
    echo "  ok   $1"
  else
    echo "  FAIL $1: expected [$2] got [$3]"
    fail_done=1
  fi
}

expect_contains() { # expect_contains <what> <needle> <haystack>
  case "$3" in
    *"$2"*) echo "  ok   $1";;
    *) echo "  FAIL $1: [$2] not found in: $3"; fail_done=1;;
  esac
}

expect_not_contains() {
  case "$3" in
    *"$2"*) echo "  FAIL $1: [$2] should not appear in: $3"; fail_done=1;;
    *) echo "  ok   $1";;
  esac
}

case_markerLabelMismatchFails() {
  run_check --issues "$fixtures/band-mismatch.json"
  expect "a hand-edited band exits non-zero" "1" "$CHECK_STATUS"
  expect_contains "and names the issue" "#21" "$CHECK_OUT"
  expect_contains "and the code" "band-mismatch" "$CHECK_OUT"

  # the same fixture, benigned: the label is brought to the marker's band and the check goes green
  jq '.[0].labels = [{"name": "class/bug"}, {"name": "prio/p1"}]' \
    "$fixtures/band-mismatch.json" > "$tmp/benign.json"
  run_check --issues "$tmp/benign.json"
  expect "a benigned copy exits 0" "0" "$CHECK_STATUS"
}

case_outOfRangeBandFails() {
  run_check --issues "$fixtures/out-of-range.json"
  expect "a band outside the vocabulary exits non-zero" "1" "$CHECK_STATUS"
  expect_contains "and names the issue" "#22" "$CHECK_OUT"
  expect_contains "and the out-of-vocabulary label" "band-out-of-vocabulary" "$CHECK_OUT"
  expect_contains "and the score outside the model" "score-out-of-range" "$CHECK_OUT"

  jq '.[0].labels = [{"name": "class/bug"}, {"name": "prio/p2"}]
      | .[0].body = "<!-- prio: band=p2 score=9 sev=4 reach=2 conf=1 occ=1 basis=0123456789abcdef at=2026-10-17 -->"' \
    "$fixtures/out-of-range.json" > "$tmp/benign.json"
  run_check --issues "$tmp/benign.json"
  expect "a benigned copy exits 0" "0" "$CHECK_STATUS"
}

case_neverWritesEligibility() {
  run_check --write-log "$fixtures/write-log-optin.txt"
  expect "a log carrying the opt-in exits non-zero" "1" "$CHECK_STATUS"
  expect_contains "and names the write" "wrote-the-opt-in" "$CHECK_OUT"
  expect_contains "and quotes the line" "agent/eligible" "$CHECK_OUT"

  # the same log with that one write removed goes green
  grep -v 'agent/eligible' "$fixtures/write-log-optin.txt" > "$tmp/clean.log"
  run_check --write-log "$tmp/clean.log"
  expect "the same log without it exits 0" "0" "$CHECK_STATUS"
}

case_incompleteMarkerFails() {
  run_check --issues "$fixtures/marker-incomplete.json"
  expect "a marker missing fields exits non-zero" "1" "$CHECK_STATUS"
  expect_contains "and names the missing ones" "marker-incomplete" "$CHECK_OUT"
  expect_contains "naming basis and at" "basis at" "$CHECK_OUT"

  jq '.[0].body = "<!-- prio: band=p1 score=13 sev=4 reach=3 conf=1 occ=1 basis=0123456789abcdef at=2026-10-17 -->"' \
    "$fixtures/marker-incomplete.json" > "$tmp/benign.json"
  run_check --issues "$tmp/benign.json"
  expect "a complete marker exits 0" "0" "$CHECK_STATUS"
}

case_labelWithoutMarkerFails() {
  run_check --issues "$fixtures/label-without-marker.json"
  expect "a band with no marker exits non-zero" "1" "$CHECK_STATUS"
  expect_contains "and names the code" "label-without-marker" "$CHECK_OUT"

  jq '.[0].body = "<!-- prio: band=p1 score=13 sev=4 reach=3 conf=1 occ=1 basis=0123456789abcdef at=2026-10-17 -->"' \
    "$fixtures/label-without-marker.json" > "$tmp/benign.json"
  run_check --issues "$tmp/benign.json"
  expect "the marker written beside it exits 0" "0" "$CHECK_STATUS"
}

# Item #292 was refused with three violations naming itself because its body *quoted* the marker grammar — an
# inline code span and a fenced excerpt — to describe the violation it reported, and the reader took the first
# span anywhere in the body as the item's own marker (issue #293). The two cases below are the pair that fixes
# the shape: a quotation is prose, and the marker's own line is still the record beside one.
case_aQuotationIsNotAMarker() {
  local body marker
  run_check --issues "$fixtures/quoted-grammar.json"
  expect "a body that only quotes the grammar exits non-zero" "1" "$CHECK_STATUS"
  expect_contains "and the missing record is what is reported" "label-without-marker" "$CHECK_OUT"
  expect_contains "naming the item" "#31" "$CHECK_OUT"
  # the quoted band says p3 and the label says p2: on HEAD that disagreement was reported as the item's own
  expect_not_contains "the quotation is not matched against the label" "band-mismatch" "$CHECK_OUT"
  expect_not_contains "and the quotation is not read as an incomplete record" "marker-incomplete" "$CHECK_OUT"

  # The shape's two anchors, one body line each, asserted through the reader itself so neither clause can be
  # dropped without this case going red: the fixture carries a prose line that *ends* at the close (excluded by
  # the column-0 clause) and a line that *starts* at the open but continues past the close (excluded by the end
  # anchor). `revert-check-293.sh` mutates each of the two and expects this case to fail. A reader that is not
  # there at all answers `missing` rather than nothing, so a tree without the predicate cannot read as green.
  body=$(jq -r '.[0].body' "$fixtures/quoted-grammar.json")
  expect "the prose line that ends at the close is not the record" "" \
    "$( ( source "$root/tools/issues/lib.sh"
          declare -F issues_prio_marker_line >/dev/null 2>&1 || { printf 'missing'; exit 0; }
          issues_prio_marker_line "$body" ) )"
  marker=$( ( source "$root/tools/issues/lib.sh"; issues_prio_marker "$body" ) )
  expect "and the line that starts at the open but does not close is not one either" "" "$marker"
}

case_aMarkerLineBesideAQuotationIsTheRecord() {
  local body read_band
  jq --arg marker '<!-- prio: band=p2 score=9 sev=4 reach=2 conf=1 occ=1 basis=0123456789abcdef at=2026-10-17 -->' \
    '.[0].body += "\n\n" + $marker' "$fixtures/quoted-grammar.json" > "$tmp/marker-beside.json"
  run_check --issues "$tmp/marker-beside.json"
  expect "a marker line beside a quotation exits 0" "0" "$CHECK_STATUS"
  expect_contains "and nothing is reported" "0 violation(s)" "$CHECK_OUT"

  # the reader itself, not just the check's verdict: the record is the line the pass writes, not the band the
  # quotation above it names
  body=$(jq -r '.[0].body' "$tmp/marker-beside.json")
  read_band=$( ( source "$root/tools/issues/lib.sh"; issues_prio_field "$(issues_prio_marker "$body")" band ) )
  expect "the reader takes the marker's own line" "p2" "$read_band"
}

case_unrankedIsNotAViolation() {
  # An issue with no band and no marker is *unranked*, not broken: the check must not fire on the state the
  # whole backlog starts in, or the pass could never be run incrementally.
  run_check --issues "$fixtures/ok.json"
  expect "the committed fixture exits 0" "0" "$CHECK_STATUS"
  expect_contains "and says how many it checked" "3 issue(s) checked" "$CHECK_OUT"
  run_check --issues "$fixtures/ok.json" --json
  expect "the JSON form is valid" "0" "$(printf '%s' "$CHECK_OUT" | jq -e . >/dev/null 2>&1; echo $?)"
  expect "and reports no violations" "0" "$(printf '%s' "$CHECK_OUT" | jq -r '.violations | length')"
}

case_selfCheckIsGreen() {
  # The token-free mode CI runs: the profile's bands, the marker grammar's single home, and the two write
  # paths' refusal to name the opt-in — all on the committed tree, with no tracker.
  run_check
  expect "the self-check exits 0" "0" "$CHECK_STATUS"
  expect_contains "and reports no violation" "· 0 violation(s)" "$CHECK_OUT"
}

if [ -n "$only_case" ]; then
  case "$only_case" in
    'check#markerLabelMismatchFails') case_markerLabelMismatchFails ;;
    'check#outOfRangeBandFails') case_outOfRangeBandFails ;;
    'check#neverWritesEligibility') case_neverWritesEligibility ;;
    'check#incompleteMarkerFails') case_incompleteMarkerFails ;;
    'check#labelWithoutMarkerFails') case_labelWithoutMarkerFails ;;
    'check#aQuotationIsNotAMarker') case_aQuotationIsNotAMarker ;;
    'check#aMarkerLineBesideAQuotationIsTheRecord') case_aMarkerLineBesideAQuotationIsTheRecord ;;
    'check#unrankedIsNotAViolation') case_unrankedIsNotAViolation ;;
    'check#selfCheckIsGreen') case_selfCheckIsGreen ;;
    *) echo "check-metadata-selftest: no such case: $only_case" >&2; exit 2 ;;
  esac
  [ "$fail_done" -eq 0 ] || exit 1
  echo "PASS $only_case"
  exit 0
fi

for name in "${CASES[@]}"; do
  fail_before=$fail_done
  case "$name" in
    'check#markerLabelMismatchFails') case_markerLabelMismatchFails ;;
    'check#outOfRangeBandFails') case_outOfRangeBandFails ;;
    'check#neverWritesEligibility') case_neverWritesEligibility ;;
    'check#incompleteMarkerFails') case_incompleteMarkerFails ;;
    'check#labelWithoutMarkerFails') case_labelWithoutMarkerFails ;;
    'check#aQuotationIsNotAMarker') case_aQuotationIsNotAMarker ;;
    'check#aMarkerLineBesideAQuotationIsTheRecord') case_aMarkerLineBesideAQuotationIsTheRecord ;;
    'check#unrankedIsNotAViolation') case_unrankedIsNotAViolation ;;
    'check#selfCheckIsGreen') case_selfCheckIsGreen ;;
  esac
  if [ "$fail_done" -eq "$fail_before" ]; then
    echo "PASS $name"
  else
    echo "FAIL $name"
  fi
done

if [ "$fail_done" -ne 0 ]; then
  echo "check-metadata-selftest: a check cannot be shown to fail, or over-triggers"
  exit 1
fi
echo "check-metadata-selftest: all ${#CASES[@]} cases passed (offline, no token, no device)"
exit 0
