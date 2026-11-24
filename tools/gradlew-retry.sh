#!/usr/bin/env bash
# gradlew-retry.sh — run a Gradle invocation, retrying only a Maven Central 429.
#
# Gradle's dependency resolver does not retry HTTP 429. A rate-limited artifact download is treated as a
# repository error: the repository is blacklisted for the rest of the build and the invocation fails
# (gradle/gradle#39083, "Could not HEAD ... Received status code 429 from server: Too Many Requests").
# A runner's IP is shared, so a transient throttle on Maven Central's side becomes a red build — run
# 45361986255 failed that way on `org.robolectric:nativeruntime-dist-compat:1.0.18` after its cache
# restored without the jar (issue #1048).
#
# This wrapper re-runs the command when — and only when — the failed attempt's log names a 429. Any other
# failure is reported once, with its own exit status and no retry, so a real defect is never masked by a
# second attempt. The retry count and the first delay are bounded; the delay doubles each time.
#
# Usage: bash tools/gradlew-retry.sh <command> [args...]
#   GRADLEW_RETRY_ATTEMPTS  total attempts, default 3
#   GRADLEW_RETRY_DELAY     seconds before the first retry, default 90
#
# Exit status: the command's own on success, the last attempt's on a 429 that outlives the attempts, or the
# first attempt's when the failure is not a 429.
set -uo pipefail

attempts="${GRADLEW_RETRY_ATTEMPTS:-3}"
delay="${GRADLEW_RETRY_DELAY:-90}"

if [ "$#" -eq 0 ]; then
  echo "usage: $0 <command> [args...]" >&2
  exit 2
fi

log="$(mktemp)"
trap 'rm -f "$log"' EXIT

attempt=1
while :; do
  "$@" 2>&1 | tee "$log"
  status="${PIPESTATUS[0]}"
  if [ "$status" -eq 0 ]; then
    exit 0
  fi
  if ! grep -q 'status code 429' "$log"; then
    echo "gradlew-retry: attempt $attempt failed (exit $status) for a reason other than a 429; not retrying" >&2
    exit "$status"
  fi
  if [ "$attempt" -ge "$attempts" ]; then
    echo "gradlew-retry: Maven Central still rate limiting after $attempt attempt(s); giving up" >&2
    exit "$status"
  fi
  echo "::warning::Maven Central rate limited a dependency download (429); retrying in ${delay}s (attempt $attempt of $attempts)" >&2
  sleep "$delay"
  delay=$((delay * 2))
  attempt=$((attempt + 1))
done
