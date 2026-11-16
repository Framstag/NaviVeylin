#!/usr/bin/env bash
# Self-test for `tools/sonar-findings.sh`: one case per invariant the analysis's findings record owes, each
# offline — a stub `curl` answers the three API calls from fixtures in a scratch tree, so there is no network,
# no token and no Gradle. The cases are the ones `openspec/specs/sonar-analysis/spec.md` names:
#
#   sonar-findings#theRecordNamesTheCommitItMeasured   the record carries the commit and the analysis's date
#   sonar-findings#aStaleAnalysisIsRefused             a poll that gives up fails and writes nothing
#   sonar-findings#aRedScanPublishesNoFindings         a failed scan publishes no findings record
#   sonar-findings#severityComesFromTheIssuesImpacts   the impact's severity, never the legacy field
#   sonar-findings#completenessIsStated                a bounded read says so; an empty one keeps its keys
#
# The fixtures are inputs, never stand-ins for the code under test: the workflow assertions read the
# repository's own `.github/workflows/sonar.yml`.
#
# Usage: tools/tests/sonar-findings-selftest.sh [--root <dir>] [--case <id>] [--list]
# Exit: 0 every selected case passed, 1 a case failed, 2 usage or an unknown case id.
set -uo pipefail

repo=""
only=""
list=0
while [ $# -gt 0 ]; do
  case "$1" in
    --root) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { echo "sonar-findings-selftest: --root needs a value" >&2; exit 2; }; repo="${2:-}"; shift 2 ;;
    --case) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { echo "sonar-findings-selftest: --case needs a value" >&2; exit 2; }; only="${2:-}"; shift 2 ;;
    --list) list=1; shift ;;
    -h|--help) sed -n '2,19p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "sonar-findings-selftest: unknown argument '$1'" >&2; exit 2 ;;
  esac
done
[ -n "$repo" ] || repo=$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel 2>/dev/null) || repo=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
tool="$repo/tools/sonar-findings.sh"
[ -f "$tool" ] || { echo "sonar-findings-selftest: no $tool" >&2; exit 2; }
command -v jq >/dev/null 2>&1 || { echo "sonar-findings-selftest: jq is required" >&2; exit 2; }

cases=(
  'sonar-findings#theRecordNamesTheCommitItMeasured'
  'sonar-findings#aStaleAnalysisIsRefused'
  'sonar-findings#aRedScanPublishesNoFindings'
  'sonar-findings#severityComesFromTheIssuesImpacts'
  'sonar-findings#completenessIsStated'
)

if [ "$list" -eq 1 ]; then
  printf '%s\n' "${cases[@]}"
  exit 0
fi

tmp=$(mktemp -d "${TMPDIR:-/tmp}/sonar-findings-selftest.XXXXXX") || { echo "sonar-findings-selftest: cannot create a scratch directory" >&2; exit 2; }
trap 'rm -rf "$tmp"' EXIT
stub="$tmp/stub"
mkdir -p "$stub"
cp "$repo/tools/tests/fixtures/sonar-findings/curl" "$stub/curl"
chmod +x "$stub/curl"
fixtures="$repo/tools/tests/fixtures/sonar-findings"
commit="0123456789abcdef0123456789abcdef01234567"
out="$tmp/sonar-findings.json"

total=0
failed=0
fail() {
  echo "FAIL: $1"
  printf '%s\n' "${2:-}" | sed 's/^/       /'
  failed=$((failed + 1))
}
assert_eq() { [ "$1" = "$2" ] || fail "$3 (expected '$1', got '$2')"; }
assert_contains() { case "$1" in *"$2"*) ;; *) fail "$3 (no '$2' in the output)" "$1" ;; esac; }
assert_not_contains() { case "$1" in *"$2"*) fail "$3 (found '$2')" "$1" ;; *) ;; esac; }
assert_absent() { [ ! -e "$1" ] || fail "$2 ($1 exists)"; }

# The stub's answers for one run: the fresh analysis of the fixture commit, its issues and its measures.
fresh() {
  rm -rf "$tmp/answers"
  mkdir -p "$tmp/answers"
  sed "s/REVISION/$commit/" "$fixtures/analyses.json" > "$tmp/answers/analyses.json"
  cp "$fixtures/issues.json" "$tmp/answers/issues.json"
  cp "$fixtures/measures.json" "$tmp/answers/measures.json"
  rm -f "$out"
}

# Run the dump against the stub. DUMP_SHA overrides the commit, DUMP_ATTEMPTS the poll bound, and
# CURL_STUB_STATUS the transport's answer; the record is written to $out and nothing else is touched.
dump() {
  response=$(cd "$tmp" && PATH="$stub:$PATH" CURL_STUB_DIR="$tmp/answers" \
    GITHUB_SHA="${DUMP_SHA:-$commit}" SONAR_TOKEN=fixture-token SONAR_HOST_URL=https://sonar.example \
    SONAR_PROJECT_KEY=fixture-project SONAR_FINDINGS_OUT="$out" \
    SONAR_POLL_ATTEMPTS="${DUMP_ATTEMPTS:-2}" SONAR_POLL_INTERVAL=0 \
    bash "$tool" 2>&1)
  rc=$?
}

run_case() {
  total=$((total + 1))
  case "$1" in
    'sonar-findings#theRecordNamesTheCommitItMeasured')
      fresh
      dump
      assert_eq "0" "$rc" "the dump succeeds on a fresh analysis: $response"
      assert_eq "$commit" "$(jq -r '.revision' "$out" 2>/dev/null)" "the record carries the commit it measured"
      assert_eq "2026-11-04T08:12:00+0000" "$(jq -r '.analysisDate' "$out")" "the record carries the analysis's own date"
      # The whole key set is present, so a row reads a measure the scan took rather than a missing key's zero.
      assert_eq "analysisDate,issues,measures,revision,truncated" "$(jq -r 'keys | sort | join(",")' "$out")" \
        "the record is the design's D8 shape and nothing else"
      assert_eq "0" "$(jq -r '.truncated' "$out" | sed 's/false/0/;s/true/1/')" "a complete read is not truncated"
      ;;
    'sonar-findings#aStaleAnalysisIsRefused')
      # The server's newest analysis is another commit's: the poll gives up and no record is written for it.
      fresh
      sed "s/REVISION/ffffffffffffffffffffffffffffffffffffffff/" "$fixtures/analyses.json" > "$tmp/answers/analyses.json"
      DUMP_ATTEMPTS=2 dump
      [ "$rc" -ne 0 ] || fail "a stale analysis must fail the step (exit $rc)"
      assert_absent "$out" "a stale analysis publishes no record"
      assert_contains "$response" "$commit" "the refusal names the commit it could not find"
      ;;
    'sonar-findings#aRedScanPublishesNoFindings')
      # The dump's half: an unreachable server writes nothing rather than an empty record.
      fresh
      CURL_STUB_STATUS=500 dump
      [ "$rc" -ne 0 ] || fail "an HTTP failure must fail the step (exit $rc)"
      assert_absent "$out" "an unreachable server publishes no record"

      # The workflow's half: neither findings step runs for a scan that failed, while the result record still
      # does — a red scan publishes its outcome and never a record that reads as zero findings.
      wf="$repo/.github/workflows/sonar.yml"
      assert_eq "1" "$(grep -c 'name: sonar-findings' "$wf")" "the findings artifact is named once"
      dump_block=$(awk '/- name: Dump the findings record/ { inside = 1; next }
                        inside && /^[[:space:]]*- name:/ { inside = 0 } inside' "$wf")
      assert_contains "$dump_block" "bash tools/sonar-findings.sh" "the dump step runs the tool"
      assert_contains "$(printf '%s\n' "$dump_block" | grep -E '^[[:space:]]*if:' | head -1)" "success()" \
        "the dump runs for a successful scan only"
      upload_block=$(awk '/- name: Publish the findings record/ { inside = 1; next }
                           inside && /^[[:space:]]*- name:/ { inside = 0 } inside' "$wf")
      assert_contains "$upload_block" "sonar-findings.json" "the upload carries the findings record"
      assert_contains "$(printf '%s\n' "$upload_block" | grep -E '^[[:space:]]*if:' | head -1)" "success()" \
        "the findings artifact is published for a successful scan only"
      assert_eq "1" "$(grep -c 'name: scan-inputs' "$wf")" "the result record is still published beside it"
      ;;
    'sonar-findings#severityComesFromTheIssuesImpacts')
      fresh
      dump
      assert_eq "0" "$rc" "the dump succeeds: $response"
      # i1 reports `severity: MAJOR` with a reliability impact of MEDIUM: the record carries MEDIUM, and the
      # legacy field's value appears nowhere.
      medium=$(jq -r '[.issues[] | select(.rule == "java:S2095" and (.path | test("BasemapManager"))) | .severity] | join(",")' "$out")
      assert_eq "MEDIUM" "$medium" "the severity is the impact's severity for its quality"
      assert_not_contains "$(jq -c '.issues' "$out")" "MAJOR" "the legacy severity field is not recorded"
      # An issue the server reports for two qualities is two entries, each with its own quality's band.
      assert_eq "LOW,MEDIUM" "$(jq -r '[.issues[] | select(.rule == "java:S1192") | .severity] | join(",")' "$out")" \
        "each quality's impact is its own entry"
      ;;
    'sonar-findings#completenessIsStated')
      # A read that stops at the ceiling while the server reports more says so: 500 per page, four pages,
      # 2 500 findings on the server.
      fresh
      for page in 1 2 3 4; do
        jq -n '[range(500) | {key: "k\(.)", rule: "java:S1192",
                              component: "app/src/main/java/com/naviveylin/ui/Settings.java",
                              message: "define a constant", severity: "LOW",
                              impacts: [{softwareQuality: "RELIABILITY", severity: "LOW"}]}]
               | {total: 2500, issues: .}' > "$tmp/answers/issues-page$page.json"
      done
      dump
      assert_eq "0" "$rc" "the bounded read still succeeds: $response"
      assert_eq "true" "$(jq -r '.truncated' "$out")" "the record states its bound"
      assert_eq "2000" "$(jq -r '.issues | length' "$out")" "the read stops at its ceiling"

      # An empty project keeps every key with an empty value: no row reads a missing key as zero.
      fresh
      printf '{"total":0,"issues":[]}\n' > "$tmp/answers/issues.json"
      printf '{"component":{}}\n' > "$tmp/answers/measures.json"
      dump
      assert_eq "0" "$rc" "an empty project still writes a record: $response"
      assert_eq "false" "$(jq -r '.truncated' "$out")" "no issues at all is not a truncated read"
      assert_eq "0" "$(jq -r '.issues | length' "$out")" "the issues list is empty"
      assert_eq "3" "$(jq -r '[.measures[] | select(. == "")] | length' "$out")" \
        "every measure key is present and empty when the scan measured none"
      assert_eq "duplicated_lines,duplicated_lines_density,ncloc" "$(jq -r '.measures | keys | join(",")' "$out")" \
        "the measures carry the three keys the rows read"
      ;;
    *) echo "sonar-findings-selftest: unknown case '$1'" >&2; exit 2 ;;
  esac
  echo "ok: $1"
}

if [ -n "$only" ]; then
  found=0
  for c in "${cases[@]}"; do
    [ "$c" = "$only" ] && found=1
  done
  [ "$found" -eq 1 ] || { echo "sonar-findings-selftest: unknown case '$only'" >&2; exit 2; }
  run_case "$only"
  [ "$failed" -eq 0 ] || exit 1
  exit 0
fi

for c in "${cases[@]}"; do
  run_case "$c"
done

if [ "$failed" -gt 0 ]; then
  echo "sonar-findings-selftest: $failed case(s) failed"
  exit 1
fi
echo "sonar-findings-selftest: all $total cases passed (stub curl, no network, no token)"
