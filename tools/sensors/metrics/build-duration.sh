#!/usr/bin/env bash
# build-duration.sh — the build duration row's measurement.
#
# It measures the pipeline, not one Gradle invocation inside it (issue #778). The record it reads is the one the
# checks' fetcher writes — `tools/fetch-build-inputs.sh --jobs-record`, from GitHub's own
# `actions/runs/<id>/jobs` — and it carries every job of the build workflow with its wall time and every step's.
# A running job cannot read its own step durations, which is why the record is a post-run read rather than
# something the build publishes about itself.
#
# The measurement is the wall time of the record's **longest job**, and the finding's identity is that job's own
# name. An absolute ceiling applied to every job is the same condition as one applied to the longest — a job past
# the ceiling *is* the longest one — so one finding carries it, rather than one per job commenting on the row's
# record every tick. The claim names that job's longest step, so a step outside the build that grew is named
# without this script knowing any step's name.
#
# The bound is absolute: the row's `threshold`, in milliseconds, under `comparison: greater-than`. It is not a
# multiple of the previous measurement, because such a bound needs one run to be worse than the run before it and
# drift never is — the owner's recorded decision on the item (issue #778). No baseline is read for the comparison;
# the number the run reports is still the row's measurement, so the record issue's history carries it.
#
# Inputs (set by `run.sh`, never by the caller): SENSORS_THRESHOLD, SENSORS_INPUT. Output: one finding per line on
# stdout; the human-readable report on stderr.
set -uo pipefail
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/sensors/lib.sh
source "$here/../lib.sh"

# The identity an error finding carries: no job could be measured, so nothing about the pipeline is claimed.
fields='{"job":"pipeline"}'
input="${SENSORS_INPUT:-}"

if [ -z "$input" ] || [ ! -f "$input" ]; then
  sensors_emit_error "$fields" "no-job-timings-record"
  exit 0
fi

if ! jobs=$(jq -c '[.jobs[]? | select((.durationMs // null) != null)]' "$input" 2>/dev/null); then
  sensors_emit_error "$fields" "the-record-is-unreadable"
  exit 0
fi

job=$(jq -c 'max_by(.durationMs) // empty' <<< "$jobs")
if [ -z "$job" ]; then
  sensors_emit_error "$fields" "the-record-carries-no-job-wall-time"
  exit 0
fi

duration=$(jq -r '.durationMs' <<< "$job")
name=$(jq -r '.name // ""' <<< "$job")
fields=$(jq -nc --arg job "$name" '{job: $job}')
fingerprint=$(sensors_fingerprint "$fields" "job")

# The step that accounts for most of the job's own wall time — what the claim names when a job grows. A step with
# no wall time (skipped, or never started) is not a candidate: it took no time, so it explains none.
step=$(jq -r '[(.steps // [])[] | select((.durationMs // null) != null)]
  | if length == 0 then "" else (max_by(.durationMs) | "\(.name // "unnamed") at \(.durationMs)ms") end' <<< "$job")
[ -n "$step" ] || step="no step of it carries a wall time"

bound=$(sensors_number "${SENSORS_THRESHOLD:-}")
if [ -z "$bound" ]; then
  sensors_emit_error "$fields" "no-bound-declared"
  exit 0
fi

printf 'job=%s duration=%sms bound=%sms step=%s\n' "$name" "$duration" "$bound" "$step" >&2

if sensors_compare "$duration" "$bound" greater-than; then
  sensors_emit "$fingerprint" "$duration" violation "$fields" "" \
    "a job of the build pipeline is past its ceiling" \
    "the job '${name}' took ${duration}ms, past the ${bound}ms ceiling; its longest step is ${step}"
else
  sensors_emit "$fingerprint" "$duration" clear "$fields" "" \
    "every job of the build pipeline is inside its ceiling" \
    "the longest job '${name}' took ${duration}ms, inside the ${bound}ms ceiling; its longest step is ${step}"
fi
