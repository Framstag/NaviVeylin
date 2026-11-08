#!/usr/bin/env bash
# Self-test for tools/ci-vcpkg-cache.sh — five cases, each against a stub client in a throwaway directory, so
# nothing in the repository is written and no network is needed.
#
# The probe decides whether a CI job relies on the binary cache, and its failure path is what turns a 62-minute
# step into a source build with one warning line (issue #761). A probe that reported the wrong verdict would put
# that cost back, so both verdicts, the missing client and the absence of anything sensitive are cases.
#
# Usage: ci-vcpkg-cache-selftest.sh [--case <id>]
set -uo pipefail

root=$(git rev-parse --show-toplevel 2>/dev/null) || root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
probe="$root/tools/ci-vcpkg-cache.sh"
case_filter=""
while [ $# -gt 0 ]; do
  case "$1" in
    --case) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { echo "ci-vcpkg-cache-selftest: $1 needs a value" >&2; exit 2; }; case_filter="${2:-}"; shift 2 ;;
    *) echo "ci-vcpkg-cache-selftest: unknown argument '$1'" >&2; exit 2 ;;
  esac
done
tmp=$(mktemp -d "${TMPDIR:-/tmp}/ci-vcpkg-cache-selftest.XXXXXX") || exit 2
trap 'rm -rf "$tmp"' EXIT
[ -x "$probe" ] || { echo "ci-vcpkg-cache-selftest: $probe is not executable" >&2; exit 2; }

pass=0
failures=0

# a stub client: $1 dir, $2 name, $3 exit status, $4 the message it prints
stub() { # → the stub's path
  mkdir -p "$1"
  {
    printf '#!/usr/bin/env bash\n'
    printf 'printf "%%s\\\\n" %q\n' "$4"
    printf 'exit %s\n' "$3"
  } > "$1/$2"
  chmod +x "$1/$2"
  printf '%s\n' "$1/$2"
}

expect() { # $1 case id, $2 expected exit, $3 expected substring, $4 description, $5... command
  local id="$1" want="$2" sub="$3" desc="$4" out got ok=1
  shift 4
  [ -z "$case_filter" ] || [ "$case_filter" = "$id" ] || return 0
  out=$("$@" 2>&1); got=$?
  [ "$got" = "$want" ] || ok=0
  if [ -n "$sub" ] && ! printf '%s' "$out" | grep -qF -- "$sub"; then ok=0; fi
  if [ "$ok" = 1 ]; then
    echo "PASS  $id — $desc (exit $got)"
    pass=$((pass + 1))
  else
    echo "FAIL  $id — $desc: expected exit $want containing '$sub', got exit $got:"
    printf '        %s\n' "$out"
    failures=$((failures + 1))
  fi
}

ok_client=$(stub "$tmp/ok" good 0 "Service index answered")
dead_client=$(stub "$tmp/dead" dead 1 "The SSL connection could not be established, see inner exception.")

expect vcpkg-cache#anAnsweringFeedIsReported 0 "VCPKG_BINARY_SOURCES=clear;nuget,https://example.test/index.json,readwrite" \
  "a client that answers makes the probe print the source the job should use" \
  bash "$probe" --probe --nuget "$ok_client" --source "https://example.test/index.json"

expect vcpkg-cache#aRefusingFeedIsReportedAndDisablesTheCache 1 "the feed https://example.test/index.json did not answer (1): The SSL connection could not be established" \
  "a refusing client is reported with its own message" \
  bash "$probe" --probe --nuget "$dead_client" --source "https://example.test/index.json"

expect vcpkg-cache#aRefusingFeedIsReportedAndDisablesTheCache 1 "VCPKG_BINARY_SOURCES=clear, so every port builds from source" \
  "…and the same refusal names what the install does instead" \
  bash "$probe" --probe --nuget "$dead_client" --source "https://example.test/index.json"

expect vcpkg-cache#aRefusingFeedIsReportedAndDisablesTheCache 1 "a dead transport cost 62 minutes of failed pushes" \
  "…and names the measured cost of relying on it" \
  bash "$probe" --probe --nuget "$dead_client" --source "https://example.test/index.json"

expect vcpkg-cache#aMissingClientIsReported 1 "the NuGet client $tmp/none/nuget is not there" \
  "a client that does not exist is named rather than surfacing as a shell error" \
  bash "$probe" --probe --nuget "$tmp/none/nuget" --source "https://example.test/index.json"

# A client that rejected the probe's own arguments also exits non-zero, and reading that as a dead feed is how the
# first version of this probe switched the cache off on a runner where it worked (issue #761). The diagnostic is
# the first line that names a cause, not the last: every failure ends in a stack frame.
usage_client=$(stub "$tmp/usage" usage 1 "Unknown option: -Take")
expect vcpkg-cache#aRejectedArgumentIsNotReadAsADeadFeed 1 "did not answer (1): Unknown option: -Take" \
  "a client rejecting the probe's arguments is reported with that line, not as a dead feed" \
  bash "$probe" --probe --nuget "$usage_client" --source "https://example.test/index.json"

# The client's message is quoted, so a token in it would be logged. No case here can invent a real token; this
# asserts the shape the probe prints — no credential, no coordinate pair.
leak_check() {
  local out
  out=$(bash "$probe" --probe --nuget "$dead_client" --source "https://example.test/index.json" 2>&1)
  if printf '%s\n' "$out" | grep -Eqi 'token=|password|authorization:|[0-9]{1,3}\.[0-9]{4,},[[:space:]]*[0-9]{1,3}\.[0-9]{4,}'; then
    printf '        the probe printed a credential or a coordinate: %s\n' "$out"
    return 1
  fi
  return 0
}
expect vcpkg-cache#theProbePrintsNothingSensitive 0 "" \
  "the probe's line carries no credential and no coordinate" \
  leak_check

if [ -n "$case_filter" ] && [ "$pass" = 0 ] && [ "$failures" = 0 ]; then
  echo "ci-vcpkg-cache-selftest: no case with the id '$case_filter'" >&2
  exit 2
fi
if [ "$failures" -ne 0 ]; then
  echo "ci-vcpkg-cache-selftest: $failures of $((pass + failures)) cases failed"
  exit 1
fi
echo "ci-vcpkg-cache-selftest: all $pass cases passed (throwaway stubs, no network)"
exit 0
