#!/usr/bin/env bash
# The scan workflow's own structural check: the triggers it may offer, the guard that refuses any other ref,
# the boundary `sonar-project.properties` declares, and the one property the quality-gate rule forbids.
#
# Nothing else in the tree reads these files. A `pull_request` trigger added to `sonar.yml` parses, the run is
# green, and the only signal would be a contributor's code analysed with `SONAR_TOKEN` in scope — the failure
# this check exists to make red. The same holds for an exclusion a later edit drops: the analysis simply
# starts measuring the `libosmscout` submodule as this repository's code, and the badge a reader trusts
# reports upstream's line count. The rule is `openspec/specs/sonar-analysis/spec.md`.
#
# What it reads, and what each group of assertions is for:
#
#   * the trigger set of `.github/workflows/sonar.yml` is exactly `push` on `main` and `workflow_dispatch`;
#     any other trigger, and a `push` that does not name `main`, is reported where it stands;
#   * the job carries `if: github.ref == 'refs/heads/main'`, because a dispatch can select any ref — the guard
#     is a runtime check, not a line of YAML to be trusted;
#   * the scanner step pins a released major (`SonarSource/sonarqube-scan-action@v<N>`) and is not wrapped in
#     `continue-on-error` — a scan that could not run must fail the run rather than pass quietly;
#   * neither file sets `qualitygate.wait`: the quality gate colours the project and does not gate the build
#     (`guidelines/BuildEvidence.md` §7);
#   * `sonar-project.properties` declares the project, the module set, each module's own coverage report, and
#     the six exclusion path groups with the generated-code patterns — the boundary the capability requires,
#     spelled out rather than inferred.
#
# Reads text files only: no build, no device, no network. Exit 0 when every assertion holds, 1 when one does
# not, 2 on a usage or layout error.
#
# Usage: check-sonar-workflow.sh [--root <dir>]   # default root: the repository this script lives in
set -uo pipefail

root=""
while [ $# -gt 0 ]; do
  case "$1" in
    --root) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; root="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,29p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "check-sonar-workflow: unknown argument '$1'" >&2; exit 2 ;;
  esac
done
[ -n "$root" ] || root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

wf_rel=".github/workflows/sonar.yml"
props_rel="sonar-project.properties"
wf="$root/$wf_rel"
props="$root/$props_rel"
modules="app auto core buildSrc osmscout-client-java"

fail=0
errors=0
error() { # error <file> <line> <message>
  printf '::error file=%s,line=%s::%s\n' "$1" "$2" "$3"
  fail=1
  errors=$((errors + 1))
}
value_of() { # value_of <file> <key> -> the value of the first `key=` line, empty when absent
  sed -n "s/^$2=//p" "$1" | head -1
}
line_of() { # line_of <file> <key> -> the line number of the first `key=` line, 1 when absent
  local n
  n=$(grep -n "^$2=" "$1" | head -1 | cut -d: -f1)
  printf '%s\n' "${n:-1}"
}

# ── the workflow: its triggers and the guard ─────────────────────────────────────────────────────────
if [ ! -f "$wf" ]; then
  error "$wf_rel" 1 "missing — the scan is produced by CI from a workflow in this repository (spec sonar-analysis, 'The scan is produced by CI from the main branch')"
else
  mapfile -t lines < "$wf"
  on_start=0
  jobs_line=1
  for i in "${!lines[@]}"; do
    case "${lines[$i]}" in
      on:) on_start=$((i + 1)) ;;
      jobs:) jobs_line=$((i + 1)) ;;
    esac
  done
  if [ "$on_start" -eq 0 ]; then
    error "$wf_rel" 1 "carries no 'on:' block — the workflow declares no trigger"
  else
    triggers=()
    trigger_lines=()
    for ((i = on_start; i < ${#lines[@]}; i++)); do
      line="${lines[$i]}"
      case "$line" in
        ''|'#'*) continue ;;
      esac
      # A line at column 0 that is not a comment ends the `on:` block.
      if [[ "$line" != [[:space:]]* ]]; then break; fi
      if [[ "$line" =~ ^\ \ ([A-Za-z_]+):[[:space:]]*(.*)$ ]]; then
        triggers+=("${BASH_REMATCH[1]}")
        trigger_lines+=("$((i + 1))")
      fi
    done
    [ "${#triggers[@]}" -gt 0 ] || error "$wf_rel" "$on_start" "declares no trigger key under 'on:'"

    push_line=0
    dispatch_line=0
    for t in "${!triggers[@]}"; do
      case "${triggers[$t]}" in
        push) push_line="${trigger_lines[$t]}" ;;
        workflow_dispatch) dispatch_line="${trigger_lines[$t]}" ;;
        *) error "$wf_rel" "${trigger_lines[$t]}" "the '${triggers[$t]}' trigger — the scan runs from a push to main and an explicit dispatch only (spec sonar-analysis, 'A pull request produces no scan')" ;;
      esac
    done

    if [ "$push_line" -eq 0 ]; then
      error "$wf_rel" "$on_start" "no 'push' trigger — a commit on main must produce a scan (spec sonar-analysis, 'A push to main produces a scan')"
    else
      main_found=0
      for ((i = push_line; i < ${#lines[@]}; i++)); do
        line="${lines[$i]}"
        case "$line" in
          ''|'#'*) continue ;;
        esac
        if [[ "$line" =~ ^\ \ [A-Za-z_]+: ]]; then break; fi
        case "$line" in
          *branches:*main*) main_found=1 ;;
        esac
      done
      [ "$main_found" -eq 1 ] || error "$wf_rel" "$push_line" "the 'push' trigger names no 'branches: [main]' — the scan runs from main, the only branch kind the project analyses"
    fi

    [ "$dispatch_line" -gt 0 ] || error "$wf_rel" "$on_start" "no 'workflow_dispatch' trigger — a person cannot ask for a scan of main (spec sonar-analysis, 'The scan is produced by CI from the main branch')"
  fi

  if ! grep -q "github\.ref == 'refs/heads/main'" "$wf"; then
    error "$wf_rel" "$jobs_line" "the job carries no \"if: github.ref == 'refs/heads/main'\" guard — a dispatch can select any ref, so the refusal has to be a runtime check (spec sonar-analysis, 'A dispatch of another branch is refused')"
  fi

  # ── the scanner step: a released major, no continue-on-error ──────────────────────────────────────
  scan_line=0
  for i in "${!lines[@]}"; do
    case "${lines[$i]}" in
      *uses:*SonarSource/sonarqube-scan-action*)
        case "${lines[$i]}" in
          '#'*) continue ;;
        esac
        scan_line=$((i + 1))
        break ;;
    esac
  done
  if [ "$scan_line" -eq 0 ]; then
    error "$wf_rel" 1 "no 'uses: SonarSource/sonarqube-scan-action@v<N>' step — nothing in this workflow runs a scanner (spec sonar-analysis, 'The scan is produced by CI from the main branch')"
  else
    ref="${lines[$((scan_line - 1))]}"
    case "$ref" in
      *@v[0-9]*) ;;
      *) error "$wf_rel" "$scan_line" "'$(printf '%s' "$ref" | sed 's/^[[:space:]]*//')' does not pin a released major — the scanner action is pinned '@v<major>' (spec sonar-analysis, 'The scanner action's runtime is recorded where every action's is')" ;;
    esac
    # The step's own block: from the `- ` line above the `uses:` to the next `- ` line below it.
    start=$scan_line
    while [ "$start" -gt 1 ]; do
      case "${lines[$((start - 2))]}" in
        [[:space:]]*-[[:space:]]*) break ;;
      esac
      start=$((start - 1))
    done
    end=${#lines[@]}
    for ((i = scan_line; i < ${#lines[@]}; i++)); do
      case "${lines[$i]}" in
        [[:space:]]*-[[:space:]]*) end=$((i + 1)); break ;;
      esac
    done
    while IFS=: read -r n value; do
      [ -n "${n:-}" ] || continue
      case "${value#"${value%%[![:space:]]*}"}" in
        '#'*) continue ;;   # a comment that explains the rule is prose, not a setting
      esac
      case "$value" in
        *continue-on-error*)
          error "$wf_rel" "$((start + n - 1))" "'continue-on-error' on the scan step — a scan that could not run must fail the run, never pass quietly (spec sonar-analysis, 'A scan that could not run fails the workflow')" ;;
      esac
    done < <(sed -n "${start},${end}p" "$wf" | grep -n 'continue-on-error' || true)
  fi
fi

# ── the quality gate is never waited for ───────────────────────────────────────────────────────────
for f in "$wf" "$props"; do
  [ -f "$f" ] || continue
  rel=${f#"$root"/}
  while IFS=: read -r n value; do
    [ -n "${n:-}" ] || continue
    case "${value#"${value%%[![:space:]]*}"}" in
      '#'*) continue ;;   # a comment that explains the rule is prose, not a setting
    esac
    case "$value" in
      *qualitygate.wait*)
        error "$rel" "$n" "the quality-gate wait property is set — the quality gate colours the project and does not gate the build (guidelines/BuildEvidence.md §7, spec sonar-analysis, 'A red gate leaves the workflow green')" ;;
    esac
  done < <(grep -n 'qualitygate\.wait' "$f" || true)
done

# ── the declared boundary ─────────────────────────────────────────────────────────────────────────
if [ ! -f "$props" ]; then
  error "$props_rel" 1 "missing — the scan's source boundary and coverage inputs are declared in this file, not inferred (spec sonar-analysis, 'The analysis measures this repository's sources and nothing else')"
else
  # The project key the badges and the workflow both name.
  while IFS='|' read -r key want; do
    got=$(value_of "$props" "$key")
    if [ "$got" != "$want" ]; then
      error "$props_rel" "$(line_of "$props" "$key")" "$key is '${got:-<unset>}' — the scan publishes to '$want' (spec sonar-analysis, 'The badge the README shows is this project's measure')"
    fi
  done <<'PROJECT'
sonar.projectKey|Framstag_NaviVeylin
sonar.organization|framstag
sonar.host.url|https://sonarcloud.io
PROJECT

  # The module set, and each module's own sources, tests and coverage report.
  declared_modules=$(value_of "$props" 'sonar.modules')
  for m in $modules; do
    case ",$declared_modules," in
      *",$m,"*) ;;
      *) error "$props_rel" "$(line_of "$props" 'sonar.modules')" "the module '$m' is not in sonar.modules ('${declared_modules:-<unset>}') — every module this project owns is analysed (spec sonar-analysis, 'An owned module is inside the analysis')" ;;
    esac
    [ -n "$(value_of "$props" "$m.sonar.sources")" ] \
      || error "$props_rel" "$(line_of "$props" 'sonar.modules')" "no '$m.sonar.sources' — the module is declared but measures nothing"
    case "$m" in
      app|auto|core) want_report='build/reports/kover/report.xml' ;;
      osmscout-client-java) want_report='build/reports/jacoco/test/jacocoTestReport.xml' ;;
      *) want_report='' ;;   # buildSrc's test suite has no coverage task and no report
    esac
    [ -n "$want_report" ] || continue
    got=$(value_of "$props" "$m.sonar.coverage.jacoco.xmlReportPaths")
    if [ "$got" != "$want_report" ]; then
      error "$props_rel" "$(line_of "$props" "$m.sonar.coverage.jacoco.xmlReportPaths")" "'$m' imports '${got:-<unset>}' — it measures its own report ('$want_report'), per module (spec sonar-analysis, 'Coverage reaches the module it was measured in')"
    fi
  done

  # The exclusion list is the boundary: each group has to be declared, or the tree it names is measured as
  # this repository's code.
  excl_line=$(line_of "$props" 'sonar.exclusions')
  excl=$(value_of "$props" 'sonar.exclusions')
  groups=0
  while IFS='|' read -r pattern why; do
    [ -n "${pattern:-}" ] || continue
    groups=$((groups + 1))
    case ",$excl," in
      *",$pattern,"*) ;;
      *) error "$props_rel" "$excl_line" "the exclusion '$pattern' is missing — $why (spec sonar-analysis, 'The analysis measures this repository's sources and nothing else')" ;;
    esac
  done <<'EXCLUSIONS'
app/src/main/cpp/**|the libosmscout submodule's native tree and this repository's own C++ are outside the analysis
app/src/main/cpp/libosmscout/libosmscout-client-java/**|the vendored client API `:osmscout-client-java` compiles is upstream's code
tools/**|the tooling tree is not product code
openspec/**|the specification tree is not product code
guidelines/**|the guideline documents are not product code
licenses/**|the licence inventory is not product code
vcpkg/**|the port overlay tree is not product code
**/build/**|build outputs are generated, never analysed
**/.gradle/**|Gradle's own state is not source
**/.cxx/**|the native build's scratch tree is not source
**/BuildConfig.*|BuildConfig is generated, matching the coverage reports' filter
**/R.java|R is generated, matching the coverage reports' filter
**/R$*.java|R's nested classes are generated, matching the coverage reports' filter
**/dagger/hilt/**|Hilt-generated code is filtered from the coverage reports too
**/Hilt_*|the generated Hilt class name is filtered from the coverage reports too
**/*_Hilt*|the generated Hilt members are filtered from the coverage reports too
**/Dagger*Component*|the generated Dagger component is filtered from the coverage reports too
EXCLUSIONS

  # SonarJava analyses bytecode as well as source, so the Java module names its classes.
  [ -n "$(value_of "$props" 'osmscout-client-java.sonar.java.binaries')" ] \
    || error "$props_rel" "$(line_of "$props" 'osmscout-client-java.sonar.sources')" "no 'osmscout-client-java.sonar.java.binaries' — SonarJava analyses bytecode as well as source, so the one pure-Java module names its classes"

  # The vendored and merged reports are never named as coverage inputs.
  while IFS=: read -r n value; do
    [ -n "${n:-}" ] || continue
    case "$value" in
      *vendored*|*kover/merged*|*koverXmlReport*)
        error "$props_rel" "$n" "the coverage input '$value' is not imported — the vendored report measures the submodule and the merged report's paths do not resolve per module (spec sonar-analysis, 'Vendored coverage is not attributed to this project')" ;;
    esac
  done < <(grep -n 'coverage\.jacoco\.xmlReportPaths=' "$props" || true)

  if [ "$fail" -eq 0 ]; then
    module_count=0
    for m in $modules; do module_count=$((module_count + 1)); done
    echo "sonar workflow: main-only triggers with a runtime guard, and a boundary of $module_count modules ($(printf '%s' "$modules" | tr ' ' ',')) with $groups exclusion groups."
  fi
fi

exit "$fail"
