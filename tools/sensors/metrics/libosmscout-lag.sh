#!/usr/bin/env bash
# libosmscout-lag.sh — whether upstream has moved past the commit the submodule is pinned to.
#
# The pin is the parent repository's gitlink, read with `git ls-tree` and never by initialising the submodule: a
# submodule checkout would clone hundreds of megabytes into every run for one number. Upstream's answer is one
# unauthenticated `compare` call, and its `ahead_by` is exactly "commits upstream carries that the pin does not":
# the pin is a merge commit, so after an update lands it is deliberately not upstream's head, and the count is zero
# without the condition having stopped looking.
#
# The project facts — the upstream repository, its branch and the submodule path — are the row's, never this
# script's (`tools/check-portability.sh` forbids a project value in an engine). A missing one is an `error` with a
# named reason, never a quiet zero that reads as a clear. The same holds for every unreadable answer: a failed
# request, a refusal, a server error, or a pin upstream does not carry is the third outcome, which fails the run
# and writes nothing.
#
# Inputs (set by `run.sh`): SENSORS_ROW (the row's JSON), SENSORS_ROOT, SENSORS_THRESHOLD.
set -uo pipefail
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/sensors/lib.sh
source "$here/../lib.sh"

row="${SENSORS_ROW:-}"
repo=$(jq -r '.upstream.repo // empty' <<< "$row")
branch=$(jq -r '.upstream.branch // empty' <<< "$row")
submodule=$(jq -r '.upstream.submodule // empty' <<< "$row")

# The condition's identity is the upstream repository and branch the row watches, so an item filed for one branch
# is not cleared by a read of another.
fields=$(jq -nc --arg repo "${repo:-unknown}" --arg branch "${branch:-unknown}" \
  '{repo: $repo, branch: $branch}')

emit_error() { # <reason-token>
  sensors_emit_error "$fields" "$1"
  exit 0
}

if [ -z "$repo" ] || [ -z "$branch" ] || [ -z "$submodule" ]; then
  emit_error "row-upstream-incomplete"
fi

pin=$(git -C "$SENSORS_ROOT" ls-tree HEAD -- "$submodule" 2>/dev/null | awk '$2 == "commit" { print $3 }')
[ -n "$pin" ] || emit_error "pin-unreadable"

# The branch is URL-encoded by jq, not by this script, so a branch name with a slash is still one path segment.
branch_encoded=$(jq -rn --arg branch "$branch" '$branch | @uri')
url="https://api.github.com/repos/${repo}/compare/${pin}...${branch_encoded}"
response=$(curl -sS -w '\n%{http_code}' -H 'Accept: application/vnd.github+json' "$url" 2>/dev/null)
transport=$?
[ "$transport" -eq 0 ] || emit_error "upstream-unreachable"

code=${response##*$'\n'}
body=${response%$'\n'*}
case "$code" in
  200) : ;;
  403) emit_error "upstream-rate-limited" ;;
  404) emit_error "pin-or-branch-unknown" ;;
  *) emit_error "upstream-http-${code}" ;;
esac

ahead=$(jq -r '.ahead_by // empty' <<< "$body")
case "$ahead" in
  ''|*[!0-9]*) emit_error "unreadable-upstream-compare" ;;
esac
head=$(jq -r '[.commits[]?.sha] | last // empty' <<< "$body")

fingerprint=$(sensors_fingerprint "$fields" "repo branch")
if [ "$ahead" -gt 0 ]; then
  sensors_emit "$fingerprint" "$ahead" violation "$fields" "" \
    "upstream carries commits the pin does not" \
    "upstream ${repo} ${branch} carries ${ahead} commit(s) the pinned ${pin:0:9} does not${head:+ (head ${head:0:9})}"
else
  sensors_emit "$fingerprint" "0" clear "$fields" "" \
    "the pin carries every commit upstream has" \
    "upstream ${repo} ${branch} carries nothing the pinned ${pin:0:9} does not, so the update has landed"
fi
