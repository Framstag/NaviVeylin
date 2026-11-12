#!/usr/bin/env bash
# sonar-findings.sh — the scan's findings record: what the analysis measured for the commit this run
# analysed, in one file the checks' pipeline can fetch.
#
# The scan workflow runs this after the scanner and uploads what it wrote as the artifact `sonar-findings`
# (`design.md` D1). It is the second record the scan publishes: `scan-result.json` carries the run's outcome,
# this one carries the analysis's findings.
#
# The record belongs to the commit's own analysis, and that is proven rather than assumed (`design.md` D2):
# the Compute Engine reports asynchronously, so the script polls `api/project_analyses/search` until the
# analysis carries `$GITHUB_SHA` and fails the step when the poll gives up. Reading the previous analysis
# instead has two silent failure modes — a fixed finding never closes, and a new one is invisible for a cycle.
#
# Severity is the impact's severity for its software quality, never the legacy `severity` field, which
# reports the same finding on another scale (`design.md` D3). The record is normalised in one `jq` program
# with a fixture per shape, and an issue whose analysis reports no `impacts` is an error the step reports
# rather than a silently empty severity.
#
# Env: SONAR_HOST_URL (default https://sonarcloud.io), SONAR_TOKEN, GITHUB_SHA, SONAR_PROJECT_KEY (default:
#      `sonar.projectKey` from `sonar-project.properties`), SONAR_FINDINGS_OUT (default: the repository root's
#      `sonar-findings.json`), SONAR_POLL_ATTEMPTS (default 30), SONAR_POLL_INTERVAL (default 20 seconds).
# Exit: 0 the record is written, 1 anything else — an unreachable server, a stale analysis, an unreadable
#      issue. Nothing is written on a failure, so no consumer reads a partial record as a measurement.
set -uo pipefail

root=$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel 2>/dev/null) ||
  root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)

die() { printf 'sonar-findings: %s\n' "$*" >&2; exit 1; }

host="${SONAR_HOST_URL:-https://sonarcloud.io}"
token="${SONAR_TOKEN:-}"
revision="${GITHUB_SHA:-}"
out="${SONAR_FINDINGS_OUT:-$root/sonar-findings.json}"
attempts="${SONAR_POLL_ATTEMPTS:-30}"
interval="${SONAR_POLL_INTERVAL:-20}"
page_size=500
# The ceiling the read stops at, and the reason the record states a bound at all: a consumer that read a
# bounded count as the complete one would report a smaller condition than the analysis holds.
ceiling=2000

project="${SONAR_PROJECT_KEY:-}"
if [ -z "$project" ] && [ -f "$root/sonar-project.properties" ]; then
  project=$(sed -n 's/^sonar\.projectKey=//p' "$root/sonar-project.properties" | head -1)
fi
[ -n "$project" ] || die "no project key — sonar.projectKey is absent from sonar-project.properties and SONAR_PROJECT_KEY is unset"
[ -n "$revision" ] || die "no commit — GITHUB_SHA is unset, and a findings record is the analysis of one commit"
[ -n "$token" ] || die "no token — SONAR_TOKEN is unset"

body=""
status=""

# One API call. The body and the HTTP status are separated the way the server separates them, and anything
# but a 200 with a JSON body is a failure: a record read from an error page would measure nothing and say so
# as if it had measured zero findings.
api() { # <path>
  local response
  response=$(curl -sS -u "$token:" -w '\n%{http_code}' "$host$1") || {
    printf 'sonar-findings: %s did not answer\n' "$1" >&2
    return 1
  }
  status=$(printf '%s\n' "$response" | tail -1)
  body=$(printf '%s\n' "$response" | sed '$d')
  if [ "$status" != "200" ]; then
    printf 'sonar-findings: %s answered %s\n' "$1" "${status:-no status}" >&2
    return 1
  fi
  printf '%s' "$body" | jq -e . >/dev/null 2>&1 || {
    printf 'sonar-findings: %s answered no JSON\n' "$1" >&2
    return 1
  }
}

# ── the analysis of this commit ────────────────────────────────────────────────────────────────────
analysis_date=""
found=0
attempt=1
while [ "$attempt" -le "$attempts" ]; do
  api "/api/project_analyses/search?project=$project&ps=1" || exit 1
  match=$(printf '%s' "$body" | jq -r --arg rev "$revision" \
    '[.analyses[]? | select(.revision == $rev)][0] | if . == null then "" else "\(.date // "")" end')
  if [ -n "$match" ]; then
    analysis_date="$match"
    found=1
    break
  fi
  if [ "$attempt" -lt "$attempts" ] && [ "$interval" -gt 0 ]; then
    sleep "$interval"
  fi
  attempt=$((attempt + 1))
done
if [ "$found" -ne 1 ]; then
  die "no analysis of $revision after $attempts attempt(s) — the record is not published for another analysis's findings"
fi

# ── the issues, paged to the ceiling ──────────────────────────────────────────────────────────────
issues='[]'
read_issues=0
total=0
page=1
# The pages are accumulated in a scratch directory rather than in one shell variable: the ceiling's worth of
# findings is larger than a command line carries, and a record that cannot be shaped would be a record nobody
# could write.
work=$(mktemp -d "${TMPDIR:-/tmp}/sonar-findings.XXXXXX") || die "cannot create a scratch directory"
trap 'rm -rf "$work"' EXIT
while [ $((page * page_size)) -le "$ceiling" ]; do
  api "/api/issues/search?componentKeys=$project&resolved=false&ps=$page_size&p=$page" || exit 1
  # One entry per software quality the issue carries: an issue's severity is the impact's severity for that
  # quality, so an issue the server reports for two qualities is two entries and neither loses its own band.
  page_issues=$(printf '%s' "$body" | jq -c '
    [.issues[]? | . as $issue |
      if ((.impacts // []) | length) == 0
      then error("issue \($issue.key) carries no per-quality impacts")
      else (.impacts[] | {rule: $issue.rule, path: $issue.component,
                          quality: .softwareQuality, severity: .severity,
                          message: ($issue.message // "")})
      end]') || die "the issue read carries an issue whose analysis reports no impacts entry"
  total=$(printf '%s' "$body" | jq -r '.total // 0')
  count=$(printf '%s' "$body" | jq -r '(.issues // []) | length')
  printf '%s\n' "$page_issues" > "$work/page.$page.json" || die "cannot keep the read's page $page"
  read_issues=$((read_issues + count))
  if [ "$count" -lt "$page_size" ]; then
    break
  fi
  page=$((page + 1))
done

truncated=false
if [ "$read_issues" -lt "$total" ]; then
  truncated=true
fi

# ── the duplication measures ──────────────────────────────────────────────────────────────────────
api "/api/measures/component?component=$project&metricKeys=duplicated_lines_density,duplicated_lines,ncloc" || exit 1
measures=$(printf '%s' "$body" | jq -c '
  {duplicated_lines_density: ([.component.measures[]? | select(.metric == "duplicated_lines_density") | .value][0] // ""),
   duplicated_lines: ([.component.measures[]? | select(.metric == "duplicated_lines") | .value][0] // ""),
   ncloc: ([.component.measures[]? | select(.metric == "ncloc") | .value][0] // "")}')

# The fixed key set: a row that reads a missing key as zero would report a measure the scan never took, so
# every section is present even when it is empty.
jq -cs 'add // []' "$work"/page.*.json > "$work/issues.json" || die "cannot join the issue pages"
record=$(jq -nc --arg revision "$revision" --arg date "$analysis_date" --argjson truncated "$truncated" \
  --slurpfile read "$work/issues.json" --argjson measures "$measures" \
  '{revision: $revision, analysisDate: $date, truncated: $truncated, issues: ($read[0] // []), measures: $measures}') ||
  die "cannot shape the findings record"
printf '%s\n' "$record" > "$out" || die "cannot write $out"
printf 'sonar-findings: wrote %s (revision=%s issues=%s truncated=%s)\n' \
  "$out" "$revision" "$(jq -r '.issues | length' <<< "$record")" "$truncated"
