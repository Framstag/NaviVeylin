#!/usr/bin/env bash
# The cases for the build inputs' producer and fetcher (`tools/build-inputs.sh`,
# `tools/fetch-build-inputs.sh`): every case drives the real script against a throwaway tree, with the shared
# stub `gh` first on `PATH`, so the whole lifecycle is exercised with no token, no network and no Gradle.
#
# Sourced by `tools/build-inputs-selftest.sh`, which provides the environment:
#   INPUTS_REPO_ROOT   the repository under test      INPUTS_TMP         a scratch directory
#   INPUTS_FIXTURES    the stub `gh`'s directory (on PATH)
set -uo pipefail

INPUTS_CASES=(
  'workflow#tickMinutesCoverTheRegistryCrons'
  'inputs#manifestIsTheProducerSet'
  'inputs#refusesADeclaredInputWithNoProducer'
  'inputs#writesTheGateTimingRecord'
  'inputs#convertsPerModuleCoverage'
  'inputs#resultRecordShapes'
  'inputs#aRedBuildStillCarriesTheResultRecord'
  'inputs#fetchRestoresTheDeclaredPaths'
  'inputs#reportsTheRunsConclusion'
  'inputs#refusesAnotherCommitsRecord'
  'inputs#noRecordExitsThree'
)

inputs_cases() {
  printf '%s\n' "${INPUTS_CASES[@]}"
}

inputs_run_case() {
  case "$1" in
    'workflow#tickMinutesCoverTheRegistryCrons') case_workflow_tickMinutesCoverTheRegistryCrons ;;
    'inputs#manifestIsTheProducerSet') case_inputs_manifestIsTheProducerSet ;;
    'inputs#refusesADeclaredInputWithNoProducer') case_inputs_refusesADeclaredInputWithNoProducer ;;
    'inputs#writesTheGateTimingRecord') case_inputs_writesTheGateTimingRecord ;;
    'inputs#convertsPerModuleCoverage') case_inputs_convertsPerModuleCoverage ;;
    'inputs#resultRecordShapes') case_inputs_resultRecordShapes ;;
    'inputs#aRedBuildStillCarriesTheResultRecord') case_inputs_aRedBuildStillCarriesTheResultRecord ;;
    'inputs#fetchRestoresTheDeclaredPaths') case_inputs_fetchRestoresTheDeclaredPaths ;;
    'inputs#reportsTheRunsConclusion') case_inputs_reportsTheRunsConclusion ;;
    'inputs#refusesAnotherCommitsRecord') case_inputs_refusesAnotherCommitsRecord ;;
    'inputs#noRecordExitsThree') case_inputs_noRecordExitsThree ;;
    *) printf 'no such case: %s\n' "$1" >&2; exit 2 ;;
  esac
}

# --------------------------------------------------------------------------- helpers

fail() {
  printf '  %s\n' "$*" >&2
  exit 1
}

assert_eq() {
  [ "$1" = "$2" ] || fail "$3: expected '$1', got '$2'"
}

assert_contains() {
  case "$1" in
    *"$2"*) ;;
    *) fail "$3: '$2' not found in: $1" ;;
  esac
}

assert_empty() {
  [ -z "$1" ] || fail "$2: expected nothing, got: $1"
}

scratch() { mktemp -d "$INPUTS_TMP/case.XXXXXX"; }

publisher() { # <args…> — the producer under test
  bash "$INPUTS_REPO_ROOT/tools/build-inputs.sh" "$@"
}

fetcher() { # <args…> — the fetcher under test
  bash "$INPUTS_REPO_ROOT/tools/fetch-build-inputs.sh" "$@"
}

# An empty workspace with the fake build outputs a case wants. `kover_module <tree> <module> <covered>
# <missed>` and `jacoco_module`, `timing_record`, `test_results`, `package` fill it in.
workspace() { mktemp -d "$INPUTS_TMP/workspace.XXXXXX"; }

coverage_xml() { # <file> <covered> <missed> [minified] — a report whose root counter is the module's own
  mkdir -p "$(dirname "$1")"
  if [ "${4:-pretty}" = "minified" ]; then
    # Gradle's JaCoCo report writes the whole document on ONE line, and the first counter on that line is a
    # class's: a line-oriented parse of this shape reports that class, not the module (measured 2026-10-18 on
    # `osmscout-client-java`: 100.0 instead of 25.7). So the fixture keeps the real shape, minified.
    printf '%s' "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><report name=\"fixture\"><package name=\"com/naviveylin/fixture\"><class name=\"com/naviveylin/fixture/A\"/><counter type=\"INSTRUCTION\" missed=\"3\" covered=\"7\"/></package><counter type=\"INSTRUCTION\" missed=\"$3\" covered=\"$2\"/><counter type=\"BRANCH\" missed=\"0\" covered=\"0\"/></report>" > "$1"
    return 0
  fi
  cat > "$1" <<XML
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<report name="fixture">
  <package name="com/naviveylin/fixture">
    <class name="com/naviveylin/fixture/A"/>
    <counter type="INSTRUCTION" missed="3" covered="7"/>
  </package>
  <counter type="INSTRUCTION" missed="$3" covered="$2"/>
  <counter type="BRANCH" missed="0" covered="0"/>
</report>
XML
}

module_reports() { # <tree> — one kover XML per Android module plus the JaCoCo module, both shapes
  coverage_xml "$1/app/build/reports/kover/report.xml" 90 10
  coverage_xml "$1/auto/build/reports/kover/report.xml" 80 20
  coverage_xml "$1/core/build/reports/kover/report.xml" 100 0 minified
  coverage_xml "$1/osmscout-client-java/build/reports/jacoco/test/jacocoTestReport.xml" 70 30 minified
}

timing_record() { # <tree>
  mkdir -p "$1/build"
  printf '{"startMs":1,"endMs":2,"durationMs":1234,"tasks":[]}\n' > "$1/build/gate-timings.json"
}

package_apk() { # <tree>
  mkdir -p "$1/app/build/outputs/apk/mobile/debug"
  printf 'apk-bytes\n' > "$1/app/build/outputs/apk/mobile/debug/app-mobile-debug.apk"
}

test_results() { # <tree> <failures|clean>
  local dir="$1/osmscout-client-java/build/test-results/test"
  mkdir -p "$dir"
  if [ "$2" = "failures" ]; then
    cat > "$dir/TEST-com.naviveylin.FixtureTest.xml" <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.naviveylin.FixtureTest" tests="2" failures="1" errors="0" skipped="0">
  <testcase name="aWork" classname="com.naviveylin.FixtureTest" time="0.01"/>
  <testcase name="bWork" classname="com.naviveylin.FixtureTest" time="0.02">
    <failure message="boom">a trace</failure>
  </testcase>
</testsuite>
XML
  else
    cat > "$dir/TEST-com.naviveylin.FixtureTest.xml" <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.naviveylin.FixtureTest" tests="1" failures="0" errors="0" skipped="0">
  <testcase name="aWork" classname="com.naviveylin.FixtureTest" time="0.01"/>
</testsuite>
XML
  fi
}

registry_with_input() { # <file> <id> <path>
  jq -nc --arg id "$2" --arg path "$3" '{rows: [{id: $id, input: $path}]}' > "$1"
}

stub_runs() { # <stub-dir> <json>
  mkdir -p "$1"
  printf '%s\n' "$2" > "$1/runs.json"
}

stub_artifacts() { # <stub-dir> <names-json>
  printf '%s\n' "$2" > "$1/artifacts.json"
}

# --------------------------------------------------------------------------- cases

case_workflow_tickMinutesCoverTheRegistryCrons() {
  local workflow registry ticks minutes declared missing=""
  workflow="$INPUTS_REPO_ROOT/.github/workflows/sensors.yml"
  registry="$INPUTS_REPO_ROOT/tools/sensors/sensors.json"
  [ -f "$workflow" ] || fail "no sensor pipeline file"
  [ -f "$registry" ] || fail "no sensor registry"

  ticks=$(awk '/^on:/ { inside = 1; next } /^[^[:space:]]/ { inside = 0 }
               inside && /^[[:space:]]*-[[:space:]]*cron:/ { sub(/^[^:]*:[[:space:]]*/, ""); gsub(/["'"'"']/, ""); print }' \
    "$workflow")
  [ -n "$ticks" ] || fail "the pipeline declares no schedule tick, so no row can ever be due"

  minutes=$(printf '%s\n' "$ticks" | awk '{ print $1 }')
  declared=$(jq -r '.rows[].cron | split(" ")[0]' "$registry")
  for minute in $declared; do
    printf '%s\n' "$minutes" | awk -v minute="$minute" '
      function worker(spec, value, n, parts, i, part, sp, step, range) {
        if (spec == "*") return 1
        n = split(spec, parts, ",")
        for (i = 1; i <= n; i++) {
          part = parts[i]; step = 1
          if (index(part, "/") > 0) { split(part, sp, "/"); part = sp[1]; step = sp[2] + 0 }
          if (part == "*") { lo = 0; hi = 100000 } else lo = part + 0
          if (index(part, "-") > 0) { split(part, range, "-"); lo = range[1] + 0; hi = range[2] + 0 }
          else hi = lo
          if (value >= lo && value <= hi && step > 0 && ((value - lo) % step) == 0) return 1
        }
        return 0
      }
      worker($0, minute) { found = 1 }
      END { exit found ? 0 : 1 }
    ' || missing="$missing $minute"
  done
  assert_empty "$missing" "these registry minutes have no schedule tick, so their rows never run"
}

case_inputs_manifestIsTheProducerSet() {
  local out
  out=$(publisher --manifest) || fail "the manifest could not be printed"
  assert_contains "$out" "build/gate-timings.json" "the manifest names the timing record"
  assert_contains "$out" "app/build/reports/jacoco/coverage.json" "the manifest names the coverage summary"
  assert_contains "$out" "app/build/gate-result.json" "the manifest names the result record"
  assert_contains "$out" "app/build/outputs/apk/mobile/debug/app-mobile-debug.apk" "the manifest names the package"

  # The registry the repository ships declares nothing outside that set.
  out=$(publisher --check) || fail "--check refused the repository's own registry: $out"
  assert_contains "$out" "every declared input has a producer" "the check reports what it proved"
}

case_inputs_refusesADeclaredInputWithNoProducer() {
  local dir registry out status
  dir=$(scratch)
  registry="$dir/registry.json"
  registry_with_input "$registry" "invented-row" "app/build/reports/nothing-writes-this.json"
  out=$(publisher --check --registry "$registry" 2>&1); status=$?
  [ "$status" -eq 1 ] || fail "--check exited $status for an input no pipeline publishes, expected 1"
  assert_contains "$out" "invented-row" "the refusal names the row"
  assert_contains "$out" "app/build/reports/nothing-writes-this.json" "the refusal names the path"

  # A row that declares nothing is not refused: half the registry reads the API.
  registry_with_input "$registry" "api-row" ""
  publisher --check --registry "$registry" >/dev/null 2>&1 \
    || fail "a row with no input was refused, though it reads no file"
}

case_inputs_writesTheGateTimingRecord() {
  local dir out
  dir=$(workspace)
  module_reports "$dir"
  timing_record "$dir"
  package_apk "$dir"
  test_results "$dir" clean
  out=$(publisher --root "$dir" --write 2>&1) || fail "--write failed: $out"
  [ -f "$dir/build/gate-timings.json" ] || fail "the timing record is not where the row declares it"
  assert_contains "$(cat "$dir/build/gate-timings.json")" '"durationMs":1234' "the record carries the gate's wall time"

  # The timing record is the gate's output, so a workspace without it is refused rather than uploaded.
  dir=$(workspace)
  module_reports "$dir"
  package_apk "$dir"
  out=$(publisher --root "$dir" --write 2>&1); status=$?
  [ "$status" -eq 1 ] || fail "--write exited $status without a timing record, expected 1"
  assert_contains "$out" "gate-timings.init.gradle.kts" "the refusal names what would have produced it"
}

case_inputs_convertsPerModuleCoverage() {
  local dir out
  dir=$(workspace)
  module_reports "$dir"
  timing_record "$dir"
  package_apk "$dir"
  test_results "$dir" clean
  publisher --root "$dir" --write >/dev/null 2>&1 || fail "--write failed"
  out=$(jq -c '.modules' "$dir/app/build/reports/jacoco/coverage.json") || fail "the coverage summary is not JSON"
  assert_eq '{"app":90.0,"auto":80.0,"core":100.0,"osmscout-client-java":70.0}' "$out" \
    "one percentage per module, minified and pretty-printed reports alike, class counters not mistaken for the module's"
}

case_inputs_resultRecordShapes() {
  local dir out
  # A failing test names its cause.
  dir=$(workspace)
  module_reports "$dir"; timing_record "$dir"; package_apk "$dir"; test_results "$dir" failures
  publisher --root "$dir" --write >/dev/null 2>&1 || fail "--write failed"
  out=$(jq -c . "$dir/app/build/gate-result.json")
  assert_eq '{"job":"test","case":"com.naviveylin.FixtureTest.bWork","result":"failure"}' "$out" "the record names the failing case"

  # A clean tree with the package present is a success.
  dir=$(workspace)
  module_reports "$dir"; timing_record "$dir"; package_apk "$dir"; test_results "$dir" clean
  publisher --root "$dir" --write >/dev/null 2>&1 || fail "--write failed"
  out=$(jq -c . "$dir/app/build/gate-result.json")
  assert_eq '{"job":"gate","case":"the-build","result":"success"}' "$out" "a green tree with a package reads as success"

  # A failure before the test step leaves no failing case: the missing package is what the workspace shows.
  dir=$(workspace)
  module_reports "$dir"; timing_record "$dir"; test_results "$dir" clean
  out=$(publisher --root "$dir" --write 2>&1); status=$?
  [ "$status" -eq 1 ] || fail "--write exited $status without a package, expected 1"
  assert_contains "$out" "app-mobile-debug.apk" "the refusal names the missing package"
  assert_eq '{"job":"build","case":"apk","result":"failure"}' "$(jq -c . "$dir/app/build/gate-result.json")" \
    "a workspace without a package reads as a failed build, even though the step failed"
}

case_inputs_aRedBuildStillCarriesTheResultRecord() {
  # What a red test run leaves behind: no Kover XML (the coverage step is skipped), but the result record —
  # which is exactly what the `broken-main` row exists to read. The step fails, loudly, and still writes it.
  local dir out status
  dir=$(workspace)
  timing_record "$dir"; package_apk "$dir"; test_results "$dir" failures
  out=$(publisher --root "$dir" --write 2>&1); status=$?
  [ "$status" -eq 1 ] || fail "--write exited $status on a red build, expected 1 (an incomplete record)"
  assert_contains "$out" "coverage.json" "the message names the coverage summary it could not write"
  assert_eq '{"job":"test","case":"com.naviveylin.FixtureTest.bWork","result":"failure"}' \
    "$(jq -c . "$dir/app/build/gate-result.json")" "the result record was written before the report"
  [ ! -e "$dir/app/build/reports/jacoco/coverage.json" ] || fail "a coverage summary appeared without its reports"
}

case_inputs_fetchRestoresTheDeclaredPaths() {
  local dir dest out status
  dir=$(scratch); dest="$dir/tree"
  # Another commit's run is newer in the list; only ours may answer.
  stub_runs "$dir" '[{"databaseId":9999,"headSha":"other99","status":"completed","workflowFile":"build.yml"},
                     {"databaseId":4242,"headSha":"abc1234","status":"completed","workflowFile":"build.yml"}]'
  stub_artifacts "$dir" '{"artifacts":[{"name":"sensor-inputs"}]}'
  mkdir -p "$dir/artifact/9999"
  printf 'other\n' > "$dir/artifact/9999/other-commits-file.txt"
  mkdir -p "$dir/artifact/4242/app/build/reports/jacoco"
  printf '{"modules":{"app":90}}\n' > "$dir/artifact/4242/app/build/reports/jacoco/coverage.json"
  printf '{"job":"gate","case":"the-build","result":"success"}\n' > "$dir/artifact/4242/app/build/gate-result.json"

  export GH_STUB_DIR="$dir"
  out=$(fetcher --repo example/repo --commit abc1234 --dest "$dest" 2>&1); status=$?
  unset GH_STUB_DIR
  [ "$status" -eq 0 ] || fail "the fetch exited $status: $out"
  [ -f "$dest/app/build/gate-result.json" ] || fail "the declared path was not restored as declared"
  [ -f "$dest/app/build/reports/jacoco/coverage.json" ] || fail "the second declared path was not restored"
  [ ! -e "$dest/other-commits-file.txt" ] || fail "the newest run's record was used, though it belongs to another commit"
}

case_inputs_reportsTheRunsConclusion() {
  # The record alone does not say whether the build that published it succeeded, and the checks' pipeline
  # selects its rows on exactly that: a red build cannot produce the coverage summary, the package or the
  # timing record, so the rows that read them must not be attempted for it. The fetcher reports the
  # conclusion beside the record, and its exit contract (0 fetched, 3 not ready, 1 cannot tell) is unchanged.
  local dir dest out status
  dir=$(scratch); dest="$dir/tree"
  stub_runs "$dir" '[{"databaseId":4242,"headSha":"abc1234","status":"completed","conclusion":"failure","workflowFile":"build.yml"}]'
  stub_artifacts "$dir" '{"artifacts":[{"name":"sensor-inputs"}]}'
  mkdir -p "$dir/artifact/4242/app/build"
  printf '{"job":"build","case":"apk","result":"failure"}\n' > "$dir/artifact/4242/app/build/gate-result.json"

  export GH_STUB_DIR="$dir"
  out=$(fetcher --repo example/repo --commit abc1234 --dest "$dest" --conclusion-file "$dir/conclusion" 2>&1); status=$?
  unset GH_STUB_DIR
  [ "$status" -eq 0 ] || fail "the fetch of a red build's record exited $status: $out"
  assert_eq "failure" "$(cat "$dir/conclusion")" "the conclusion the build's run ended with is reported"

  # A successful build's record reports its own conclusion.
  stub_runs "$dir" '[{"databaseId":4242,"headSha":"abc1234","status":"completed","conclusion":"success","workflowFile":"build.yml"}]'
  export GH_STUB_DIR="$dir"
  out=$(fetcher --repo example/repo --commit abc1234 --dest "$dest" --conclusion-file "$dir/conclusion" 2>&1); status=$?
  unset GH_STUB_DIR
  [ "$status" -eq 0 ] || fail "the fetch exited $status: $out"
  assert_eq "success" "$(cat "$dir/conclusion")" "a successful build's conclusion is reported"

  # Not ready: no run, so no record and no conclusion — the file is not written, so a caller cannot read a
  # conclusion for a build whose record does not exist (the same exit 3 that makes the run a skip).
  stub_runs "$dir" '[]'
  rm -f "$dir/conclusion"
  export GH_STUB_DIR="$dir"
  out=$(fetcher --repo example/repo --commit abc1234 --dest "$dest" --conclusion-file "$dir/conclusion" 2>&1); status=$?
  unset GH_STUB_DIR
  [ "$status" -eq 3 ] || fail "no run exited $status, expected 3 (not ready): $out"
  [ ! -e "$dir/conclusion" ] || fail "a conclusion was reported for a record that does not exist"
}

case_inputs_refusesAnotherCommitsRecord() {
  local dir dest out status
  dir=$(scratch); dest="$dir/tree"
  stub_runs "$dir" '[{"databaseId":4242,"headSha":"other99","status":"completed","workflowFile":"build.yml"}]'
  stub_artifacts "$dir" '{"artifacts":[{"name":"sensor-inputs"}]}'
  export GH_STUB_DIR="$dir"
  out=$(fetcher --repo example/repo --commit abc1234 --dest "$dest" 2>&1); status=$?
  unset GH_STUB_DIR
  [ "$status" -eq 3 ] || fail "another commit's run was used (exit $status): $out"
  [ ! -e "$dest" ] || fail "a tree was created for a commit whose record does not exist"
}

case_inputs_noRecordExitsThree() {
  local dir dest out status
  dir=$(scratch); dest="$dir/tree"
  stub_runs "$dir" '[]'
  stub_artifacts "$dir" '{"artifacts":[]}'
  export GH_STUB_DIR="$dir"
  out=$(fetcher --repo example/repo --commit abc1234 --dest "$dest" 2>&1); status=$?
  unset GH_STUB_DIR
  [ "$status" -eq 3 ] || fail "no run exited $status, expected 3 (not ready), so a pending build is not a failed check"
  assert_contains "$out" "does not exist" "the message says the record does not exist yet"

  # The run exists but carries no artifact yet: also "not ready".
  stub_runs "$dir" '[{"databaseId":4242,"headSha":"abc1234","status":"completed","workflowFile":"build.yml"}]'
  stub_artifacts "$dir" '{"artifacts":[{"name":"something-else"}]}'
  export GH_STUB_DIR="$dir"
  out=$(fetcher --repo example/repo --commit abc1234 --dest "$dest" 2>&1); status=$?
  unset GH_STUB_DIR
  [ "$status" -eq 3 ] || fail "a run without the artifact exited $status, expected 3: $out"
}
