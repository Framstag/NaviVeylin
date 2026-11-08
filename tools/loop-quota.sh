#!/usr/bin/env bash
# loop-quota.sh — the one question the loop asks the provider's own balance: may the run start another item now?
#
# The question is the **provider's**, so the answer is read by the provider's script: this file lives outside
# `tools/loop/` beside the project's other adapters (`tools/pi-child-events.sh`), and `loop.conf` names it through
# `LOOP_QUOTA_CMD`, so no script under `tools/loop/` names a URL, a provider or a threshold — the rule
# `tools/check-portability.sh` enforces (D1, D16). The engine owns *when* the question is asked (a boundary between
# two items, `state.sh cmd_next`) and this script owns *how* it is answered.
#
# Protocol — read by `state.sh cmd_next`:
#   exit 0  go. Output, when there is any, is a note the run relays.
#   exit 3  hold. The reason is the last-but-one line on stdout and `resume-at <epoch-seconds>` the last one.
#           `resume-at 0` is an instant this reader does not know, and the run then waits its own poll interval.
#   exit 2  usage.
#   Any other status, and an unreadable balance, are **go**: a check that cannot be made is not a refusal — the
#   rule the run already follows for its memory floor and for a tracker that answers nothing.
#
# **The evidence this reader was written against** (measured 2026-11-08, issue #811's neighbourhood):
#   `GET https://ollama.com/api/balance` with `Authorization: Bearer $OLLAMA_API_KEY` answers
#   `{"included":{"session":{"remaining_percent":100,"resets_at":"2026-11-08T20:00:00Z"},
#                 "weekly":{"remaining_percent":0,"resets_at":"2026-11-09T00:00:00Z"}},"purchased":{"balance_usd":0}}`
#   — percentages, not counts: `/api/usage` answers request counts of the retired subscription shape and was
#   discarded for exactly that reason. The endpoint is rate limited to **10 requests a minute per user, shared**,
#   which is why nothing here polls: the loop calls it once per item boundary and once per hold, and a hold is
#   minutes to hours long.
#
# **What the three numbers mean.** A window is a burst allowance, not a pot: the short one refills every few hours,
# the long one at the top of its week, so a run that spends the long window flat out on day one buys nothing for
# the rest of the week. Hence two bounds, not one:
#
#   * a **floor** per window — the reserve below which the run stops borrowing from the future. The long window's is
#     the smaller because its time constant is the longer: a short window that runs out returns within hours.
#   * a **pacing line** for the long window — the share of it that may have been burned by now, which is the linear
#     line between the window's start and its reset, less a reserve for every *other* consumer of the same quota.
#     At the top of the week the line is near zero and the run takes what an item costs; as the week fills the line
#     arrives, and the run sleeps instead of overdrawing. This is what "burn in relation to the week, never
#     overburn" is as arithmetic.
#
# An **item's cost in percent is not known** when it starts — the run measures wall time, not quota — so the line
# is the whole guard: an item that overshoots it makes the *next* hold longer, never a window exhausted mid-item.
#
# Usage: tools/loop-quota.sh [--json] [--verbose]
#   --json     print the decision object and exit with the status it implies (the self-test's reading)
#   --verbose  print the go line as well, so a hand reading shows the numbers it compared
#
# Environment:
#   OLLAMA_API_KEY                     the bearer token
#   LOOP_QUOTA_URL                     the balance endpoint (default https://ollama.com/api/balance)
#   LOOP_QUOTA_JSON                    read this file instead of the endpoint (the self-test's seam)
#   LOOP_QUOTA_SESSION_MIN_PERCENT     the short window's floor (default 15)
#   LOOP_QUOTA_WEEKLY_MIN_PERCENT      the long window's floor (default 5)
#   LOOP_QUOTA_PACING_RESERVE_PERCENT  the share of the long window kept back for other consumers (default 10)
#   LOOP_QUOTA_WEEKLY_SECONDS          the long window's length (default 604800)
#   LOOP_QUOTA_MARGIN_SECONDS          added to a resume instant, so the run wakes after the reset (default 15)
#   LOOP_QUOTA_TIMEOUT_SECONDS         the endpoint's own timeout (default 10)
set -uo pipefail

url="${LOOP_QUOTA_URL:-https://ollama.com/api/balance}"
fixture="${LOOP_QUOTA_JSON:-}"
json_out=0
verbose=0

while [ $# -gt 0 ]; do
  case "$1" in
    --json) json_out=1; shift ;;
    --verbose) verbose=1; shift ;;
    -h|--help) sed -n '2,55p' "$0"; exit 0 ;;
    *) printf 'loop-quota: unknown argument %s\n' "$1" >&2; exit 2 ;;
  esac
done

# A bound that is absent, empty or not a whole number is the default: a reader that fails closed on a typo would
# stop the loop for a reason no log line explains, and the numbers below are the documented ones either way.
num() { # num <value> <default> → the value when it is a non-negative integer, the default otherwise
  case "${1:-}" in ''|*[!0-9]*) printf '%s' "$2" ;; *) printf '%s' "$1" ;; esac
}
session_min=$(num "${LOOP_QUOTA_SESSION_MIN_PERCENT:-}" 15)
weekly_min=$(num "${LOOP_QUOTA_WEEKLY_MIN_PERCENT:-}" 5)
reserve=$(num "${LOOP_QUOTA_PACING_RESERVE_PERCENT:-}" 10)
week_seconds=$(num "${LOOP_QUOTA_WEEKLY_SECONDS:-}" 604800)
margin=$(num "${LOOP_QUOTA_MARGIN_SECONDS:-}" 15)
timeout=$(num "${LOOP_QUOTA_TIMEOUT_SECONDS:-}" 10)
# A reserve of 100 keeps everything back and a window of no seconds has no line on it; both are the defaults.
[ "$reserve" -le 100 ] || reserve=10
[ "$week_seconds" -gt 0 ] || week_seconds=604800

read_balance() {
  if [ -n "$fixture" ]; then
    cat "$fixture" 2>/dev/null
    return
  fi
  [ -n "${OLLAMA_API_KEY:-}" ] || return 1
  curl -fsS --max-time "$timeout" -H "Authorization: Bearer $OLLAMA_API_KEY" "$url" 2>/dev/null
}

# The decision, as one jq program: the arithmetic a threshold turns into belongs beside the thresholds, and jq is
# the one language both halves of this repository already use for it (`state.sh`'s floor, `fetch-build-inputs.sh`).
program='
  .included as $in
  | (($in.session.resets_at // "") | if . == "" then 0 else (try fromdateiso8601 catch 0) end) as $sr
  | (($in.weekly.resets_at  // "") | if . == "" then 0 else (try fromdateiso8601 catch 0) end) as $wr
  | ($in.session.remaining_percent // null) as $sl
  | ($in.weekly.remaining_percent  // null) as $wl
  | if ($sl | type) != "number" or ($wl | type) != "number" then
      {go: true, why: "the balance carries no session and weekly percentage — nothing was compared"}
    else
      ($wr - $weekSeconds) as $weekStart
      | ([($now - $weekStart), 0] | max) as $elapsed0
      | ([$elapsed0, $weekSeconds] | min) as $elapsed
      | ($elapsed / $weekSeconds * (100 - $reserve)) as $allow
      | (100 - $wl) as $used
      | if $sl < $sessionMin then
          {go: false, window: "session", resume: $sr,
           why: "the session quota has \($sl)% left, below the \($sessionMin)% floor"}
        elif $wl < $weeklyMin then
          {go: false, window: "weekly", resume: $wr,
           why: "the weekly quota has \($wl)% left, below the \($weeklyMin)% floor"}
        elif ($wr > 0) and ($used > $allow) then
          {go: false, window: "pacing",
           resume: (if $used >= (100 - $reserve) then $wr
                    else ($weekStart + ($used * $weekSeconds / (100 - $reserve) | ceil)) end),
           why: "the weekly quota is \(($used * 10 | floor) / 10)% burned against a pacing line of "
                + "\(($allow * 10 | floor) / 10)% (\(($elapsed / $weekSeconds * 1000 | floor) / 10)% of the week "
                + "elapsed, \($reserve)% kept back for other consumers)"}
        else
          {go: true, window: "none",
           why: "session \($sl)% (floor \($sessionMin)%) · weekly \($wl)% (floor \($weeklyMin)%) · "
                + "\(($used * 10 | floor) / 10)% burned against a pacing line of \(($allow * 10 | floor) / 10)%"}
        end
    end'

body=$(read_balance) || body=""
if [ -z "$body" ]; then
  if [ -n "$fixture" ]; then
    printf 'quota: the reading at %s is empty or absent — the check is not a refusal\n' "$fixture"
  elif [ -z "${OLLAMA_API_KEY:-}" ]; then
    printf 'quota: OLLAMA_API_KEY is not in the environment — the check is not a refusal\n'
  else
    printf 'quota: the balance at %s could not be fetched — the check is not a refusal\n' "$url"
  fi
  exit 0
fi

now=$(date +%s)
decision=$(printf '%s' "$body" | jq -c --argjson now "$now" --argjson sessionMin "$session_min" \
  --argjson weeklyMin "$weekly_min" --argjson reserve "$reserve" --argjson weekSeconds "$week_seconds" \
  "$program" 2>/dev/null)
if [ -z "$decision" ]; then
  printf 'quota: the balance is not the JSON this reader knows — the check is not a refusal\n'
  exit 0
fi

if [ "$(printf '%s' "$decision" | jq -r '.go')" = "true" ]; then
  if [ "$json_out" -eq 1 ]; then
    printf '%s\n' "$decision"
    exit 0
  fi
  [ "$verbose" -eq 1 ] && printf 'quota: go — %s\n' "$(printf '%s' "$decision" | jq -r '.why')"
  exit 0
fi

why=$(printf '%s' "$decision" | jq -r '.why')
resume=$(printf '%s' "$decision" | jq -r '.resume // 0')
case "$resume" in ''|*[!0-9]*) resume=0 ;; esac
if [ "$resume" -gt 0 ]; then
  # The margin is what keeps the run from waking on the boundary it just read: an instant that has already passed
  # is still an instant, and the naive `resume < now` sleep of zero seconds would spin.
  resume=$(( resume + margin ))
  [ "$resume" -gt "$now" ] || resume=$(( now + 1 ))
fi
if [ "$json_out" -eq 1 ]; then
  printf '%s\n' "$decision"
else
  if [ "$resume" -gt 0 ]; then
    printf 'quota: %s — resumes at %s\n' "$why" "$(date -u -d "@$resume" +%Y-%m-%dT%H:%M:%SZ)"
  else
    printf 'quota: %s — the balance names no reset instant, so the run waits its poll interval\n' "$why"
  fi
  printf 'resume-at %s\n' "$resume"
fi
exit 3
