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
# Usage: bash tools/fetch-build-inputs.sh --commit <sha> [--repo <owner/name>] [--workflow <file>]
#                                        [--artifact <name>] [--dest <dir>] [--conclusion-file <path>]
# Env:   GITHUB_REPOSITORY (the default --repo), GH_TOKEN is passed through to `gh` as usual.
# Exit:  0 fetched, 3 no completed run for that commit carries the artifact yet, 1 the fetch failed, 2 usage.
set -uo pipefail

repo="${GITHUB_REPOSITORY:-}"
commit=""
workflow="build.yml"
artifact="sensor-inputs"
dest="."
conclusion_file=""

while [ $# -gt 0 ]; do
  case "$1" in
    --commit) commit="${2:-}"; shift 2 ;;
    --repo) repo="${2:-}"; shift 2 ;;
    --workflow) workflow="${2:-}"; shift 2 ;;
    --artifact) artifact="${2:-}"; shift 2 ;;
    --dest) dest="${2:-}"; shift 2 ;;
    --conclusion-file) conclusion_file="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
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
# The conclusion is reported last, so a caller that reads it knows the record it belongs to is in the tree.
if [ -n "$conclusion_file" ]; then
  printf '%s\n' "$conclusion" > "$conclusion_file" || die "cannot write $conclusion_file"
fi
printf 'fetch-build-inputs: %s of %s %s unpacked at %s (the build run concluded %s)\n' \
  "$artifact" "$workflow" "$commit" "$dest" "${conclusion:-nothing}"
