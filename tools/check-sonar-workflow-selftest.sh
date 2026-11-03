#!/usr/bin/env bash
# Self-test for check-sonar-workflow.sh: one case per assertion of the check, each planting a broken
# `sonar.yml` or `sonar-project.properties` in a throwaway tree and requiring the checker to fail *naming file
# and line*. The cases are the ones `openspec/specs/sonar-analysis/spec.md` names:
#
#   sonar-workflow#theNativeTreeIsExcluded            the native tree is an exclusion group
#   sonar-workflow#generatedCodeIsExcluded            the generated-code patterns are exclusion groups
#   sonar-workflow#theTriggerSetIsPushAndDispatch     the landed files pass as they stand
#   sonar-workflow#aPullRequestTriggerIsReported      a pull_request trigger reddens the check
#   sonar-workflow#theDispatchIsGuardedToMain         a missing runtime guard reddens the check
#   sonar-workflow#theVendoredReportIsNotListed       a vendored coverage report reddens the check
#   sonar-workflow#theGateIsNotWaitedFor              a gate-wait property reddens the check
#   sonar-workflow#theScanStepIsNotContinueOnError    an error-swallowing scan step reddens the check
#   sonar-workflow#theCoverageStepCarriesTheJvmOnlyFlag a Gradle test/report step without -PjvmTestsOnly reddens
#                                                    the check
#
# The fixtures are the repository's own two files, copied into a temporary tree per case — no build, no device,
# no network, and no fixture in the repository to keep in step with them.
#
# Usage: check-sonar-workflow-selftest.sh [--root <dir>] [--case <id>] [--list]
# Exit: 0 every selected case passed, 1 a case failed, 2 usage or an unknown case id.
set -uo pipefail

repo=""
only=""
list=0
while [ $# -gt 0 ]; do
  case "$1" in
    --root) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { echo "check-sonar-workflow-selftest: --root needs a value" >&2; exit 2; }; repo="${2:-}"; shift 2 ;;
    --case) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { echo "check-sonar-workflow-selftest: --case needs a value" >&2; exit 2; }; only="${2:-}"; shift 2 ;;
    --list) list=1; shift ;;
    -h|--help) sed -n '2,20p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "check-sonar-workflow-selftest: unknown argument '$1'" >&2; exit 2 ;;
  esac
done
[ -n "$repo" ] || repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
checker="$repo/tools/check-sonar-workflow.sh"
[ -f "$checker" ] || { echo "check-sonar-workflow-selftest: no $checker" >&2; exit 2; }

cases=(
  'sonar-workflow#theNativeTreeIsExcluded'
  'sonar-workflow#generatedCodeIsExcluded'
  'sonar-workflow#theTriggerSetIsPushAndDispatch'
  'sonar-workflow#aPullRequestTriggerIsReported'
  'sonar-workflow#theDispatchIsGuardedToMain'
  'sonar-workflow#theVendoredReportIsNotListed'
  'sonar-workflow#theGateIsNotWaitedFor'
  'sonar-workflow#theScanStepIsNotContinueOnError'
  'sonar-workflow#theCoverageStepCarriesTheJvmOnlyFlag'
)

if [ "$list" -eq 1 ]; then
  printf '%s\n' "${cases[@]}"
  exit 0
fi

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
wf_rel=".github/workflows/sonar.yml"
props_rel="sonar-project.properties"

# A throwaway tree carrying the repository's own scan files, which is the green fixture every case starts from.
fresh() {
  rm -rf "$tmp/.github" "$tmp/sonar-project.properties"
  mkdir -p "$tmp/.github/workflows"
  if [ -f "$repo/$wf_rel" ]; then cp "$repo/$wf_rel" "$tmp/$wf_rel"; fi
  if [ -f "$repo/$props_rel" ]; then cp "$repo/$props_rel" "$tmp/$props_rel"; fi
}

# plant <file> <sed expression>: rewrite one line of the green fixture.
plant() {
  sed -i -E "$2" "$tmp/$1"
}

total=0
failed=0
fail() {
  echo "FAIL: $1"
  printf '%s\n' "${2:-}" | sed 's/^/       /'
  failed=$((failed + 1))
}
check_fails() { # check_fails <case> <needle-file> <needle-message>
  local out rc=0
  out=$(bash "$checker" --root "$tmp" 2>&1) || rc=$?
  if [ "$rc" -ne 1 ]; then
    fail "$1 (expected exit 1, got $rc)" "$out"
    return 1
  fi
  case "$out" in
    *"file=$2,line="*) ;;
    *) fail "$1 (the report does not name $2 and a line)" "$out"; return 1 ;;
  esac
  case "$out" in
    *"$3"*) ;;
    *) fail "$1 (the report does not name '$3')" "$out"; return 1 ;;
  esac
  return 0
}
check_passes() { # check_passes <case>
  local out rc=0
  out=$(bash "$checker" --root "$tmp" 2>&1) || rc=$?
  if [ "$rc" -ne 0 ]; then
    fail "$1 (expected exit 0, got $rc)" "$out"
    return 1
  fi
  case "$out" in
    *'sonar workflow:'*) ;;
    *) fail "$1 (no summary line)" "$out"; return 1 ;;
  esac
  return 0
}

run_case() {
  total=$((total + 1))
  case "$1" in
    'sonar-workflow#theNativeTreeIsExcluded')
      # The whole native tree, and the vendored client API the Java module compiles beside its own sources.
      fresh
      plant "$props_rel" 's|^sonar\.exclusions=app/src/main/cpp/\*\*,|sonar.exclusions=|'
      check_fails "$1" "$props_rel" "the exclusion 'app/src/main/cpp/**' is missing" || return 1
      fresh
      plant "$props_rel" 's|,app/src/main/cpp/libosmscout/libosmscout-client-java/\*\*||'
      check_fails "$1" "$props_rel" "the exclusion 'app/src/main/cpp/libosmscout/libosmscout-client-java/**' is missing" ;;
    'sonar-workflow#generatedCodeIsExcluded')
      # The patterns the coverage reports already filter: a dropped one puts generated code back in the count.
      fresh
      plant "$props_rel" 's#,\*\*/BuildConfig\.\*##'
      check_fails "$1" "$props_rel" "the exclusion '**/BuildConfig.*' is missing" || return 1
      fresh
      plant "$props_rel" 's#,\*\*/Dagger\*Component\*##'
      check_fails "$1" "$props_rel" "the exclusion '**/Dagger*Component*' is missing" ;;
    'sonar-workflow#theTriggerSetIsPushAndDispatch')
      # The landed files, unchanged: the trigger set the capability requires passes as it stands.
      fresh
      check_passes "$1" ;;
    'sonar-workflow#aPullRequestTriggerIsReported')
      fresh
      sed -i 's/^on:$/on:\n  pull_request:\n    branches: [main]/' "$tmp/$wf_rel"
      check_fails "$1" "$wf_rel" "the 'pull_request' trigger" ;;
    'sonar-workflow#theDispatchIsGuardedToMain')
      fresh
      sed -i "/if: github.ref == 'refs\/heads\/main'/d" "$tmp/$wf_rel"
      check_fails "$1" "$wf_rel" "guard" ;;
    'sonar-workflow#theVendoredReportIsNotListed')
      fresh
      plant "$props_rel" 's#^(osmscout-client-java\.sonar\.coverage\.jacoco\.xmlReportPaths=).*#\1build/reports/jacoco/vendored/jacocoVendoredTestReport.xml#'
      check_fails "$1" "$props_rel" "is not imported" ;;
    'sonar-workflow#theGateIsNotWaitedFor')
      fresh
      printf 'sonar.qualitygate.wait=true\n' >> "$tmp/$props_rel"
      check_fails "$1" "$props_rel" "quality-gate wait property is set" || return 1
      fresh
      sed -i 's|^\( *\)uses: SonarSource/sonarqube-scan-action@v7$|&\n\1with:\n\1  args: -Dsonar.qualitygate.wait=true|' "$tmp/$wf_rel"
      check_fails "$1" "$wf_rel" "quality-gate wait property is set" ;;
    'sonar-workflow#theScanStepIsNotContinueOnError')
      fresh
      sed -i 's/^\( *\)uses: SonarSource\/sonarqube-scan-action@v7$/\1uses: SonarSource\/sonarqube-scan-action@v7\n\1continue-on-error: true/' "$tmp/$wf_rel"
      check_fails "$1" "$wf_rel" "'continue-on-error' on the scan step" ;;
    'sonar-workflow#theCoverageStepCarriesTheJvmOnlyFlag')
      # The report step re-configures the graph the test step configured: without the toggle that graph carries
      # the licence inventory's mergeNativeSbom, which reads vcpkg's installed-status file at configuration time
      # and fails a runner that never ran ./setup-vcpkg.sh, before the scanner runs.
      fresh
      plant "$wf_rel" 's/^( *run: .*\.\/gradlew.*) -PjvmTestsOnly(.*)$/\1\2/'
      check_fails "$1" "$wf_rel" "without -PjvmTestsOnly" || return 1
      # …and the assertion is about the flag, not about which report tasks a step names: dropping one leaves the
      # step with the toggle and the check green.
      fresh
      plant "$wf_rel" 's/ :app:koverXmlReport//'
      check_passes "$1" ;;
    *) echo "check-sonar-workflow-selftest: unknown case '$1'" >&2; exit 2 ;;
  esac
  echo "ok: $1"
}

if [ -n "$only" ]; then
  found=0
  for c in "${cases[@]}"; do
    [ "$c" = "$only" ] || continue
    found=1
  done
  [ "$found" -eq 1 ] || { echo "check-sonar-workflow-selftest: unknown case '$only'" >&2; exit 2; }
  run_case "$only"
  [ "$failed" -eq 0 ] || exit 1
  exit 0
fi

for c in "${cases[@]}"; do
  run_case "$c"
done

if [ "$failed" -gt 0 ]; then
  echo "check-sonar-workflow-selftest: $failed of $total cases failed"
  exit 1
fi
echo "check-sonar-workflow-selftest: all $total cases passed (the repository's own scan files, no build, no network)"
