#!/usr/bin/env bash
# build-inputs.sh — the publisher of the files the sensor rows declare as their inputs.
#
# `tools/sensors/sensors.json` declares four file inputs — `build-duration`, `package-size`, `coverage` and
# `broken-main` — and until this script existed nothing wrote them, so every scheduled sensor run reported
# four `error` rows and exited 8. The schedule was therefore off (issue #161).
#
# This script is the one place that knows which paths those rows declare. `--write` materialises them inside
# the build pipeline; `--check` refuses a registry whose row declares an input this script does not publish,
# which is the bound that keeps a row from naming a file no pipeline produces; `--manifest` prints the set,
# so the bound and the producer cannot drift apart.
#
# The publisher is project-side on purpose: the paths and the module names below are this project's, and the
# sensor engine (`tools/sensors/**`) declares them nowhere (design D1, D2).
#
# Usage: bash tools/build-inputs.sh --manifest
#        bash tools/build-inputs.sh --write
#        bash tools/build-inputs.sh --check [--registry <file>]
#        [--root <dir>]  the workspace it reads and writes (default: the repository; a case passes a
#                        throwaway tree, so the producer is testable without touching the build output)
# Exit:  0 ok, 1 a declared input has no producer or a producer's file is missing, 2 usage/environment.
set -uo pipefail

root="${BUILD_INPUTS_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
registry="tools/sensors/sensors.json"
mode=""

while [ $# -gt 0 ]; do
  case "$1" in
    --manifest) mode=manifest; shift ;;
    --write) mode=write; shift ;;
    --check) mode=check; shift ;;
    --registry) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; registry="${2:-}"; shift 2 ;;
    --root) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; root="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,21p' "$0"; exit 0 ;;
    *) printf 'build-inputs: unknown argument %s\n' "$1" >&2; exit 2 ;;
  esac
done
[ -d "$root" ] || { printf 'build-inputs: no such root: %s\n' "$root" >&2; exit 2; }
cd "$root" || exit 2
[ -n "$mode" ] || { printf 'build-inputs: one of --manifest, --write, --check is required\n' >&2; exit 2; }
command -v jq >/dev/null 2>&1 || { printf 'build-inputs: jq is required\n' >&2; exit 2; }

# --------------------------------------------------------------------------- the declared paths

# The record the gate writes when Gradle runs with `-I tools/gate-timings.init.gradle.kts` (invocation
# directory = repository root).
timing_record="build/gate-timings.json"
# The coverage summary the `coverage` row reads: module → percentage, the shape
# `tools/sensors/metrics/coverage.sh` documents.
coverage_summary="app/build/reports/jacoco/coverage.json"
# The build result record the `broken-main` row reads: job, case, result.
result_record="app/build/gate-result.json"
# The package the `package-size` row measures. The build pipeline assembles the debug APK; the shipped
# release artifact is produced by the release pipeline only (design D4).
package_apk="app/build/outputs/apk/mobile/debug/app-mobile-debug.apk"

# The per-module Kover XML reports the coverage summary is derived from, and the pure-Java module's two JaCoCo
# reports. `:koverXmlReport` merges the three Android modules, so each module is measured from its own.
# The Java module compiles two provenances — its own local overrides and the vendored libosmscout Java client API
# beside them — and each report measures one of them, so the row carries one key per provenance instead of one
# number for both.
kover_modules="app auto core"
jacoco_xml="osmscout-client-java/build/reports/jacoco/test/jacocoTestReport.xml"
jacoco_vendored_xml="osmscout-client-java/build/reports/jacoco/vendored/jacocoVendoredTestReport.xml"

# The test-result directories the result record's failing case is read from — the same directories the build
# pipeline names in its "Report executed test suites" step.
test_result_dirs="app/build/test-results/testMobileDebugUnitTest
app/build/test-results/testAutomotiveDebugUnitTest
auto/build/test-results/testDebugUnitTest
core/build/test-results/testDebugUnitTest
osmscout-client-java/build/test-results/test"

# The manifest: every path a sensor row may declare.
# The paths this script writes itself, and the paths the pipeline's other steps produce. The manifest is the
# union (design D6): a row may declare either, and `--check` compares the registry against both.
written_paths() {
  printf '%s\n' "$coverage_summary" "$result_record"
}

produced_paths() {
  printf '%s\n' "$timing_record" "$package_apk"
}

published_paths() {
  written_paths
  produced_paths
}

die() { printf 'build-inputs: %s\n' "$*" >&2; exit 1; }

# --------------------------------------------------------------------------- the measurements

# The instruction coverage of one JaCoCo/Kover XML report, in percent with one decimal.
#
# The report's own counters come last (a JaCoCo report is `<package>`…, then its own `<counter>`s), so the
# percentage is the *last* instruction counter in the document. Taking the last one, rather than a line's
# first, is what makes this independent of how the generator formatted the file: Gradle's JaCoCo report is a
# single line, Kover's is pretty-printed, and a line-oriented parse of the minified form reads a class
# counter and reports 100 % (measured 2026-10-18 on `osmscout-client-java`'s report: 100.0 instead of 25.7).
xml_instruction_percentage() { # <xml> → percentage, or die
  local xml="$1" counter missed covered value
  [ -f "$xml" ] || die "no coverage report at $xml — the step that generates it did not run"
  counter=$(tr -d '\n' < "$xml" | grep -o '<counter type="INSTRUCTION"[^>]*>' | tail -n 1)
  [ -n "$counter" ] || die "$xml carries no instruction counter — the report proves nothing"
  missed=$(printf '%s' "$counter" | sed -n 's/.*missed="\([0-9]*\)".*/\1/p')
  covered=$(printf '%s' "$counter" | sed -n 's/.*covered="\([0-9]*\)".*/\1/p')
  value=$(awk -v missed="${missed:-0}" -v covered="${covered:-0}" 'BEGIN {
    total = missed + covered
    if (total <= 0) { print "none"; exit 0 }
    printf "%.1f", covered * 100 / total
  }')
  [ "$value" != "none" ] || die "$xml carries no instruction counter — the report proves nothing"
  printf '%s\n' "$value"
}

# The first failing test case the test-result XMLs carry, as `FQCN.method`, or nothing. File order is
# sorted so two runs of the same tree name the same cause.
first_failing_case() {
  local dir file
  while IFS= read -r dir; do
    [ -d "$dir" ] || continue
    for file in $(ls "$dir"/TEST-*.xml 2>/dev/null | LC_ALL=C sort); do
      awk '
        /<testcase / {
          class_name = ""; test_name = ""
          if (match($0, /classname="[^"]*"/)) class_name = substr($0, RSTART + 11, RLENGTH - 12)
          if (match($0, /name="[^"]*"/)) test_name = substr($0, RSTART + 6, RLENGTH - 7)
          pending = 1
          next
        }
        /<failure|<error/ { if (pending == 1) { print class_name "." test_name; exit } }
        /<\/testcase>/ { pending = 0 }
      ' "$file"
    done
  done <<< "$test_result_dirs"
}

# --------------------------------------------------------------------------- the writers

write_coverage_summary() {
  local module xml lines
  lines=""
  for module in $kover_modules; do
    xml="$module/build/reports/kover/report.xml"
    lines="$lines$module	$(xml_instruction_percentage "$xml")"$'\n'
  done
  lines="${lines}osmscout-client-java	$(xml_instruction_percentage "$jacoco_xml")"$'\n'
  lines="${lines}osmscout-client-java-vendored	$(xml_instruction_percentage "$jacoco_vendored_xml")"$'\n'
  mkdir -p "$(dirname "$coverage_summary")"
  printf '%s' "$lines" | jq -Rn \
    '[inputs | split("\t")] | map({key: .[0], value: (.[1] | tonumber)}) | from_entries | {modules: .}' \
    > "$coverage_summary" || die "cannot write $coverage_summary"
}

write_result_record() {
  local job case_name result="success" failing
  failing=$(first_failing_case)
  if [ -n "$failing" ]; then
    job="test"; case_name="$failing"; result="failure"
  elif [ ! -f "$package_apk" ]; then
    # A failure before the test step leaves no failing case: what the workspace shows is the missing package.
    job="build"; case_name="apk"; result="failure"
  else
    job="gate"; case_name="the-build"
  fi
  mkdir -p "$(dirname "$result_record")"
  jq -nc --arg job "$job" --arg case "$case_name" --arg result "$result" \
    '{job: $job, case: $case, result: $result}' > "$result_record" \
    || die "cannot write $result_record"
}

# The per-module reports the coverage summary needs; a red test run leaves them absent, so the publisher
# reports that instead of dying before the result record is written.
coverage_inputs_present() {
  local module
  for module in $kover_modules; do
    [ -f "$module/build/reports/kover/report.xml" ] || return 1
  done
  [ -f "$jacoco_xml" ] || return 1
  [ -f "$jacoco_vendored_xml" ] || return 1
  return 0
}

# --------------------------------------------------------------------------- the verbs

case "$mode" in
  manifest)
    published_paths
    ;;

  write)
    # The result record is written first and unconditionally: a red build is exactly when the checks' pipeline
    # needs it, and it is the one input the publisher can always produce. Everything else is named as missing
    # rather than silently absent — this step fails, and the upload step still runs (`if: always()`), so the
    # record reaches the checks' run either way.
    missing=""
    add_missing() { missing="${missing}${missing:+; }$1"; }
    write_result_record
    coverage_inputs_present || add_missing "$coverage_summary (the per-module coverage XMLs are missing — the coverage step did not run, which a red test run explains)"
    [ -f "$jacoco_vendored_xml" ] || add_missing "$jacoco_vendored_xml (the JaCoCo step that measures the vendored client API did not run)"
    [ -f "$timing_record" ] || add_missing "$timing_record (the Gradle steps must run with -I tools/gate-timings.init.gradle.kts)"
    [ -f "$package_apk" ] || add_missing "$package_apk (the step that assembles :app:assembleDebug did not run)"
    if [ -n "$missing" ]; then
      printf 'build-inputs: the record is incomplete — nothing publishes: %s\n' "$missing" >&2
      exit 1
    fi
    write_coverage_summary
    jq -e '.durationMs' "$timing_record" >/dev/null 2>&1 \
      || die "$timing_record carries no durationMs — the build-duration row would report an error"
    for path in $(published_paths); do
      [ -f "$path" ] || die "$path is missing after the write — the uploaded record would be incomplete"
    done
    printf 'build-inputs: wrote the record for %s\n' "$root"
    published_paths | sed 's/^/build-inputs:   /'
    ;;

  check)
    [ -f "$registry" ] || die "no registry at $registry"
    jq -e . "$registry" >/dev/null 2>&1 || die "$registry is not readable JSON"
    manifest=$(published_paths | LC_ALL=C sort)
    refused=0
    while IFS=$'\t' read -r id path; do
      [ -n "$id" ] || continue
      if ! printf '%s\n' "$manifest" | grep -Fxq -- "$path"; then
        printf 'build-inputs: refused — row %s declares %s, which no pipeline publishes\n' "$id" "$path" >&2
        refused=1
      fi
    done < <(jq -r '.rows[] | select((.input // "") != "") | "\(.id)\t\(.input)"' "$registry")
    [ "$refused" -eq 0 ] || exit 1
    printf 'build-inputs: every declared input has a producer (%s rows checked)\n' \
      "$(jq -r '[.rows[] | select((.input // "") != "")] | length' "$registry")"
    ;;
esac
