#!/usr/bin/env bash
# fetch-build-inputs.sh — bring a commit's build record into the working tree.
#
# The sensor rows read files (`build/gate-timings.json`, `app/build/reports/jacoco/coverage.json`,
# `app/build/gate-result.json`, the package). The build pipeline publishes them as one artifact,
# `sensor-inputs` (`tools/build-inputs.sh`). This script is how the checks' pipeline gets that artifact: it
# picks the newest *completed* run of the build workflow for the commit under test, downloads the artifact,
# and unpacks it at the repository root, so every declared path is where the row declares it.
#
# A commit whose build has not finished yet is not a failure: this exits 3, and the pipeline that calls it
# selects no row instead of reporting four failed checks (design D5). The conclusion the run *ended* with is
# written beside the record when `--conclusion-file` names a path, because a red build cannot produce the
# coverage summary, the package or the timing record: the caller selects the rows that read those for a
# record a build completed successfully, and lets the condition rows run for any record. Every other error —
# auth, a broken download, an unreadable artifact list — exits 1, so "not ready" and "cannot tell" stay
# distinguishable, and no conclusion is reported when nothing was fetched.
#
# One thing the artifact cannot carry is the wall time of the run that made it: a running job cannot read its own
# step durations, so only GitHub's post-run `actions/runs/<id>/jobs` answers them. `--jobs-record <path>` writes
# that answer for the run this fetch restored — every job and every step, with the run's identifier and the commit
# beside them — so a row can measure the pipeline instead of one invocation inside it (issue #778). The flag is
# opt-in: the scan's record is fetched through this same script, and a record overwritten with another pipeline's
# jobs would measure the wrong pipeline.
#
# Usage: bash tools/fetch-build-inputs.sh --commit <sha> [--repo <owner/name>] [--workflow <file>]
#                                        [--artifact <name>] [--dest <dir>] [--conclusion-file <path>]
#                                        [--jobs-record <path>]
# Env:   GITHUB_REPOSITORY (the default --repo), GH_TOKEN is passed through to `gh` as usual.
# Exit:  0 fetched, 3 no completed run for that commit carries the artifact yet, 1 the fetch failed, 2 usage.
set -uo pipefail

repo="${GITHUB_REPOSITORY:-}"
commit=""
workflow="build.yml"
artifact="sensor-inputs"
dest="."
conclusion_file=""
jobs_record=""

while [ $# -gt 0 ]; do
  case "$1" in
    --commit) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; commit="${2:-}"; shift 2 ;;
    --repo) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; repo="${2:-}"; shift 2 ;;
    --workflow) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; workflow="${2:-}"; shift 2 ;;
    --artifact) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; artifact="${2:-}"; shift 2 ;;
    --dest) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; dest="${2:-}"; shift 2 ;;
    --conclusion-file) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; conclusion_file="${2:-}"; shift 2 ;;
    --jobs-record) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; jobs_record="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,27p' "$0"; exit 0 ;;
    *) printf 'fetch-build-inputs: unknown argument %s\n' "$1" >&2; exit 2 ;;
  esac
done

[ -n "$commit" ] || { printf 'fetch-build-inputs: --commit is required\n' >&2; exit 2; }
[ -n "$repo" ] || { printf 'fetch-build-inputs: --repo (or GITHUB_REPOSITORY) is required\n' >&2; exit 2; }
command -v gh >/dev/null 2>&1 || { printf 'fetch-build-inputs: gh is required\n' >&2; exit 2; }
command -v jq >/dev/null 2>&1 || { printf 'fetch-build-inputs: jq is required\n' >&2; exit 2; }

die() { printf 'fetch-build-inputs: %s\n' "$*" >&2; exit 1; }

# The newest completed run of that workflow. `gh`'s own `--commit` filter is deliberately not used: the
# identity has to hold in *this* script, where a case can watch it, and a run from another ref could
# otherwise answer for a commit that shares a tree — so the head SHA is compared here, and a re-run of the
# same commit (a newer run, same head SHA) is what the first match then names.
runs=$(gh run list --repo "$repo" --workflow "$workflow" --status completed --limit 30 \
  --json databaseId,headSha,conclusion 2>/dev/null) || die "cannot list the $workflow runs of $repo"
run=$(printf '%s' "$runs" | jq -r --arg sha "$commit" \
  '[.[] | select(.headSha == $sha)][0] | "\(.databaseId // "")\t\(.conclusion // "")"')
run_id=${run%%$'\t'*}
conclusion=${run#*$'\t'}
if [ -z "$run_id" ]; then
  printf 'fetch-build-inputs: no completed %s run for %s yet — the record does not exist\n' \
    "$workflow" "$commit" >&2
  exit 3
fi

# The run exists; the artifact may still be an older run's or already expired, so its presence is asked for
# explicitly rather than inferred from a download failure.
if ! names=$(gh api "repos/$repo/actions/runs/$run_id/artifacts" --paginate --jq '.artifacts[].name' 2>/dev/null); then
  die "cannot read the artifact list of $workflow run $run_id"
fi
if ! printf '%s\n' "$names" | grep -qx "$artifact"; then
  printf 'fetch-build-inputs: %s run %s carries no %s artifact yet — the record does not exist\n' \
    "$workflow" "$run_id" "$artifact" >&2
  exit 3
fi

mkdir -p "$dest" || die "cannot create $dest"
gh run download "$run_id" --repo "$repo" --name "$artifact" --dir "$dest" \
  || die "downloading $artifact from $workflow run $run_id failed"

# The run's own job and step wall times, when the caller asked for them. It is written after the artifact, so a
# record in the tree always belongs to a fetch that succeeded, and it dies on an unreadable answer rather than
# writing a partial one: a record missing a job would measure the wrong pipeline silently.
if [ -n "$jobs_record" ]; then
  if ! jobs_json=$(gh api "repos/$repo/actions/runs/$run_id/jobs" --paginate --jq '.jobs[]' 2>/dev/null); then
    die "cannot read the job list of $workflow run $run_id"
  fi
  mkdir -p "$(dirname "$jobs_record")" || die "cannot create the directory of $jobs_record"
  printf '%s\n' "$jobs_json" | jq -s --arg workflow "$workflow" --arg commit "$commit" --argjson run "$run_id" '
    def instant: if . == null or . == "" then "" else sub("\\.[0-9]+Z$"; "Z") end;
    def span($start; $end):
      (($start | instant) as $a | ($end | instant) as $b
       | if $a != "" and $b != "" then ((($b | fromdateiso8601) - ($a | fromdateiso8601)) * 1000) else null end);
    {workflow: $workflow, commit: $commit, runId: $run,
     jobs: [.[] | . as $job
       | {name: ($job.name // ""), conclusion: ($job.conclusion // ""),
          startedAt: ($job.started_at // null), completedAt: ($job.completed_at // null),
          durationMs: span($job.started_at; $job.completed_at),
          steps: [($job.steps // [])[] | {name: (.name // ""), conclusion: (.conclusion // ""),
                     startedAt: (.started_at // null), completedAt: (.completed_at // null),
                     durationMs: span(.started_at; .completed_at)}]}]}' > "$jobs_record" \
    || die "cannot write $jobs_record"
fi
# The conclusion is reported last, so a caller that reads it knows the record it belongs to is in the tree.
if [ -n "$conclusion_file" ]; then
  printf '%s\n' "$conclusion" > "$conclusion_file" || die "cannot write $conclusion_file"
fi
printf 'fetch-build-inputs: %s of %s %s unpacked at %s (the build run concluded %s)\n' \
  "$artifact" "$workflow" "$commit" "$dest" "${conclusion:-nothing}"
