#!/usr/bin/env bash
# The gate as a command.
#
#   policy.sh preflight                 — is the identity the tooling runs as on the allowlist?
#   policy.sh check <issue.json|->      — may this issue be acted on? (allowlisted author and opt-in label)
#
# Exit: 0 allow, 2 usage, 3 refusal with the reason code on stdout (design D8), 75 the tracker did not
# answer so no verdict exists (issue #231).
#
# Usage: policy.sh <preflight|check> [<issue.json|->] [--json] [--profile <path>] [--login <login>]
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/issues/lib.sh
source "$here/lib.sh"

mode="${1:-}"
[ -n "$mode" ] || issues_die "policy: usage: policy.sh <preflight|check> [<issue.json|->] [--json] [--profile <path>]"
shift
case "$mode" in
  preflight|check) ;;
  *) issues_die "policy: unknown mode '$mode' — expected preflight or check" ;;
esac

json=0
profile_arg=""
login=""
issue_file="-"
while [ $# -gt 0 ]; do
  case "$1" in
    --json) json=1; shift ;;
    --profile) need_value "$@"; profile_arg="${2:-}"; shift 2 ;;
    --login) need_value "$@"; login="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,9p' "$0"; exit 0 ;;
    -) issue_file="-"; shift ;;
    -*) issues_die "policy: unknown argument $1" ;;
    *) issue_file="$1"; shift ;;
  esac
done

issues_resolve "$profile_arg"
profile="$ISSUES_PROFILE"
# An unreadable identity is not a refusal (#231). `issues_login` answers `ISSUES_EXIT_UNREACHABLE` when the
# tracker did not answer at all; this script then says exactly that, instead of handing the policy core an
# empty login, which the core can only read as "nobody is authorised" — the verdict that turned a 20-minute
# network outage into `author-not-allowlisted` on the tracker.
if [ -z "$login" ] && ! login=$(issues_login); then
  if [ "$json" -eq 1 ]; then
    jq -n --arg login "" --arg reason "tracker-unreachable" \
      --arg detail "(the tracker API did not answer — no identity could be read)" \
      '{login: $login, decision: "unknown", reason: $reason, detail: $detail}'
  else
    printf 'tracker-unreachable: the tracker API did not answer — no identity could be read, so no policy verdict is available\n'
  fi
  exit "$ISSUES_EXIT_UNREACHABLE"
fi

if [ "$mode" = "preflight" ]; then
  out=$(issues_core Policy preflight --profile "$profile" --login "$login" 2>&1)
  status=$?
  if [ "$json" -eq 1 ]; then
    jq -n --arg login "$login" --argjson ok "$([ "$status" -eq 0 ] && echo true || echo false)" \
      --arg reason "${out%%:*}" --arg detail "${out#*: }" \
      '{login: $login, decision: (if $ok then "allow" else "refuse" end), reason: $reason, detail: $detail}'
  else
    printf '%s\n' "$out"
  fi
  [ "$status" -eq 0 ] || exit "$ISSUES_EXIT_POLICY"
  exit "$ISSUES_EXIT_OK"
fi

if [ "$issue_file" = "-" ]; then
  tmp=$(mktemp) || issues_die "policy: cannot create a temporary file"
  trap 'rm -f "$tmp"' EXIT
  cat > "$tmp"
  issue_file="$tmp"
fi
[ -f "$issue_file" ] || issues_die "policy: no such issue file: $issue_file"

if [ "$json" -eq 1 ]; then
  issues_core Policy check "$issue_file" --profile "$profile" --json
  exit $?
fi
issues_core Policy check "$issue_file" --profile "$profile"
exit $?
