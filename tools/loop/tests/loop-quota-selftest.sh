#!/usr/bin/env bash
# loop-quota-selftest.sh — the cases for tools/loop-quota.sh, on fixtures it writes itself.
#
# Why: the reader is the one thing that decides whether a run may start another item, and nothing exercised its
# arithmetic — `LOOP_QUOTA_JSON` is documented in the reader as "the self-test's seam" and no case read it, while
# the engine's four quota cases answer through a stub written into a throwaway project. A wrong floor, a wrong
# pacing line or a wrong resume instant was caught by nobody (issue #878: a run recorded eight holds totalling
# 17 278 s of an 11.1 h run, each bought with 0.1–0.5 points of a line nobody had ever compared with a balance).
#
# The seam is the reader's own: a fixture is a balance file named by `LOOP_QUOTA_JSON`, so a case states the
# reading and the arithmetic, never a recording of today's answer. `resets_at` is written relative to the clock at
# the moment the fixture is built — a reset instant four days out puts three of the week's seven days behind the
# window — so the elapsed share below is 3/7 and the pacing line is `elapsed × (100 − reserve) / 100` computed
# here, not read back from the reader.
#
# Every case carries an id of the shape a spec's `- **Case**:` line names — `quota#<name>` — and `--case <id>`
# runs that one and skips the rest: a change needs the case it broke, not the suite. An id no case carries is a
# refusal (exit 2), never a quiet zero, because nothing run and nothing found must not read alike. The summary
# line is the shape `tools/loop/gate.sh` derives a step's `tests=`/`failures=` from.
#
#   bash tools/loop/tests/loop-quota-selftest.sh             # every case
#   bash tools/loop/tests/loop-quota-selftest.sh --case quota#thePacingLineIsTheElapsedShareLessTheReserve
#
# Offline, no device, no token, no network. Exit 0 = every case passed, 1 = a case failed (the case prints what it
# expected and what the reader reported), 2 = usage, or a case id no case carries.
#
# The thresholds every case pins are the ones `tools/loop/loop.conf` records, and the case
# `quota#theRecordedValuesAreTheOnesTheseCasesAssume` is what keeps the two equal: a profile that moved a bound is
# a red case here and not a suite quietly asserting the number it found.
set -uo pipefail

root=$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel 2>/dev/null) || root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)
HERE="$root/tools"
TOOL="$HERE/loop-quota.sh"
CONF="$root/tools/loop/loop.conf"
[ -f "$TOOL" ] || { echo "selftest: no tools/loop-quota.sh next to $0" >&2; exit 1; }

case_only=""
while [ $# -gt 0 ]; do
  case "$1" in
    --case)
      [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { echo "loop-quota-selftest: $1 needs a value" >&2; exit 2; }
      case_only="${2:-}"; shift 2 ;;
    *) echo "loop-quota-selftest: unknown argument $1" >&2; exit 2 ;;
  esac
done

WORK="$(mktemp -d)" || exit 1
trap 'rm -rf "$WORK"' EXIT

pass=0
fail=0
ran=0
case_failed() { # case_failed <case> <expected> <actual>
  printf '  FAIL %s\n         expected: %s\n         actual:   %s\n' "$1" "$2" "$3"
  fail=$((fail + 1))
}
case_passed() { pass=$((pass + 1)); printf '  ok   %s\n' "$1"; }
expect() { # expect <id> <expected> <actual>
  [ -z "$case_only" ] || [ "$case_only" = "$1" ] || return 0
  ran=$((ran + 1))
  if [ "$2" = "$3" ]; then case_passed "$1"; else case_failed "$1" "$2" "$3"; fi
}

WEEK=604800
MARGIN=15
now=$(date +%s)
iso() { date -u -d "@$1" +%Y-%m-%dT%H:%M:%SZ; }

# The thresholds the cases assume: the reserve and the two floors are the profile's own (`loop.conf`), passed
# the way `state.sh cmd_next` passes them, so the reader is never asked what its compiled-in defaults are.
QUOTA_SESSION_MIN=5
QUOTA_WEEKLY_MIN=5
QUOTA_RESERVE=5

out=""
rc=0
read_quota() { # read_quota <fixture-or-> [reader args…] — the reader's output and status, verbatim
  local fixture="$1"; shift
  out=$(env \
    LOOP_QUOTA_JSON="$fixture" \
    LOOP_QUOTA_SESSION_MIN_PERCENT="$QUOTA_SESSION_MIN" \
    LOOP_QUOTA_WEEKLY_MIN_PERCENT="$QUOTA_WEEKLY_MIN" \
    LOOP_QUOTA_PACING_RESERVE_PERCENT="$QUOTA_RESERVE" \
    LOOP_QUOTA_WEEKLY_SECONDS="$WEEK" \
    LOOP_QUOTA_MARGIN_SECONDS="$MARGIN" \
    bash "$TOOL" "$@" 2>&1)
  rc=$?
}

# A balance in the provider's own shape: two windows, each a remaining percentage and the instant it resets.
fixture() { # fixture <name> <session%> <session resets_at (JSON)> <weekly%> <weekly resets_at (JSON)> → path
  local f="$WORK/$1.json"
  printf '{"included":{"session":{"remaining_percent":%s,"resets_at":%s},"weekly":{"remaining_percent":%s,"resets_at":%s}},"purchased":{"balance_usd":0}}\n' \
    "$2" "$3" "$4" "$5" > "$f"
  printf '%s' "$f"
}

lines_of() { if [ -z "$1" ]; then printf '0'; else printf '%s\n' "$1" | wc -l | tr -d ' '; fi; }
resume_of() { printf '%s\n' "$1" | sed -n 's/^resume-at //p' | tail -1; }
reason_of() { printf '%s\n' "$1" | tail -2 | head -1 | sed -e 's/ — resumes at .*$//' -e 's/ — the balance names no reset instant.*$//'; }
# A hold's reason is the last-but-one line and `resume-at` the last one, which is the protocol `state.sh` reads;
# `reason_of` strips the tail that names the instant, so a case asserts the words that carry the numbers.

# ── the two floors: a window under its floor is a hold until that window's own reset instant ──────────────────
# 4 % against a 5 % floor, four days out on the long window and an hour out on the short one: the floor that fires
# is the one the percentage belongs to, and the instant offered is that window's reset plus the reader's margin —
# the sleep a run can pay, not the other window's.
session_under=$(fixture floors-session 4 "\"$(iso $((now + 3600)))\"" 50 "\"$(iso $((now + 345600)))\"")
weekly_under=$(fixture floors-weekly 50 "\"$(iso $((now + 3600)))\"" 4 "\"$(iso $((now + 345600)))\"")
read_quota "$session_under"; s_rc=$rc; s_why=$(reason_of "$out"); s_at=$(resume_of "$out")
read_quota "$weekly_under"; w_rc=$rc; w_why=$(reason_of "$out"); w_at=$(resume_of "$out")
expect "quota#aWindowUnderItsFloorHoldsUntilItResets" \
  "3|quota: the session quota has 4% left, below the 5% floor|$((now + 3600 + MARGIN))|3|quota: the weekly quota has 4% left, below the 5% floor|$((now + 345600 + MARGIN))" \
  "$s_rc|$s_why|$s_at|$w_rc|$w_why|$w_at"

# ── the pacing line: `elapsed × (100 − reserve) / 100`, on both sides of itself ───────────────────────────────
# Three of the week's seven days elapsed and a 5 % reserve, so the line stands at 3/7 × 95 = 40.7 % burned; the
# fixtures burn 40 % and 41 %. The reserve is what the two differ by: with the reserve dropped from the line
# (a mutation this case exists for) the line is the elapsed share alone, 42.8 %, and 41 % is then under it.
week_reset=$((now + 345600))
below=$(fixture line-below 90 "\"$(iso $((now + 3600)))\"" 60 "\"$(iso $week_reset)\"")
above=$(fixture line-above 90 "\"$(iso $((now + 3600)))\"" 59 "\"$(iso $week_reset)\"")
# The two bounds meet where the arithmetic says they do: 95 % burned is the line's own end point and is also the
# weekly floor's 5 % remaining, so the hold it takes offers the window's reset (the line cannot arrive later than
# that) rather than a solved instant — and 4.9 % remaining is the floor's hold, one tenth lower. A reserve that did
# not end on the floor would answer one of these two differently, which is why both are asserted.
cap=$(fixture line-cap 90 "\"$(iso $((now + 3600)))\"" 5 "\"$(iso $week_reset)\"")
overcap=$(fixture line-cap-over 90 "\"$(iso $((now + 3600)))\"" 4.9 "\"$(iso $week_reset)\"")
read_quota "$below"; b_rc=$rc; b_out=$out
read_quota "$above"; a_rc=$rc; a_why=$(reason_of "$out")
read_quota "$cap"; c_rc=$rc; c_why=$(reason_of "$out"); c_at=$(resume_of "$out")
read_quota "$overcap"; oc_rc=$rc; oc_why=$(reason_of "$out")
expect "quota#thePacingLineIsTheElapsedShareLessTheReserve" \
  "0||3|quota: the weekly quota is 41% burned against a pacing line of 40.7% (42.8% of the week elapsed, 5% kept back for other consumers)|3|quota: the weekly quota is 95% burned against a pacing line of 40.7% (42.8% of the week elapsed, 5% kept back for other consumers)|$((week_reset + MARGIN))|3|quota: the weekly quota has 4.9% left, below the 5% floor" \
  "$b_rc|$b_out|$a_rc|$a_why|$c_rc|$c_why|$c_at|$oc_rc|$oc_why"

# ── a hold on the line names when the line arrives, not the window's reset ───────────────────────────────────
# 60 % burned against the same line: the instant the line reaches 60 % is `week_start + ceil(60 × week / 95)`,
# which is 222 821 s before the window resets — the difference is the arithmetic spelled here, and a reader that
# offered the reset instead would name an instant a third of a week too late.
arrival=$(fixture line-arrival 90 "\"$(iso $((now + 3600)))\"" 40 "\"$(iso $week_reset)\"")
read_quota "$arrival"; arr_rc=$rc; arr_at=$(resume_of "$out")
arrive_by=$(( week_reset - WEEK + (60 * WEEK + 94) / 95 ))
expect "quota#aHoldOnTheLineNamesWhenTheLineArrives" \
  "3|$((arrive_by + MARGIN))|$((WEEK - (60 * WEEK + 94) / 95))" \
  "$arr_rc|$arr_at|$((week_reset - arrive_by))"

# ── an instant it cannot parse is named, not dropped ─────────────────────────────────────────────────────────
# `fromdateiso8601` accepts exactly `%Y-%m-%dT%H:%M:%SZ`, so an offset, fractional seconds or an absent field
# all become 0 — and a 0 there skipped the pacing comparison in silence, leaving the floors and no line. Each
# fixture here burns past the line, so a reader that dropped the comparison would answer a silent go.
offset=$(fixture instant-offset 90 "\"$(iso $((now + 3600)))\"" 40 "\"2026-11-14T00:00:00+00:00\"")
fractional=$(fixture instant-fractional 90 "\"$(iso $((now + 3600)))\"" 40 "\"2026-11-14T00:00:00.500Z\"")
absent=$(fixture instant-absent 90 "\"$(iso $((now + 3600)))\"" 40 'null')
read_quota "$offset"; o_rc=$rc; o_out=$out
read_quota "$fractional"; f_rc=$rc; f_out=$out
read_quota "$absent"; n_rc=$rc; n_out=$out
# The floors are still compared when an instant cannot be read: the same unparsable long window, under its floor.
floors_blind=$(fixture instant-floors 90 "\"$(iso $((now + 3600)))\"" 4 "\"2026-11-14T00:00:00+00:00\"")
read_quota "$floors_blind"; fb_rc=$rc; fb_out=$out; fb_why=$(reason_of "$out")
# `--json` is the self-test's own reading and stays the decision object it was: the note is the run's, printed on
# the exit-0 path, and a hold's stdout is its reason and `resume-at` alone — the pair `run.sh` reads.
read_quota "$offset" --json; oj_rc=$rc; oj_out=$out
read_quota "$floors_blind" --json; hj_rc=$rc; hj_out=$out
json_shape() { printf '%s' "$1" | jq -r '"\(has("notes"))|\(.go)|\(.window // "-")"' 2>/dev/null || printf 'not-json'; }
expect "quota#anInstantItCannotParseIsNamedNotDropped" \
  "0|1|quota: the weekly reset instant \"2026-11-14T00:00:00+00:00\" is not the shape this reader parses (YYYY-MM-DDTHH:MM:SSZ) — the pacing line is not compared|0|1|quota: the weekly reset instant \"2026-11-14T00:00:00.500Z\" is not the shape this reader parses (YYYY-MM-DDTHH:MM:SSZ) — the pacing line is not compared|0|1|quota: the weekly reset instant is absent — the pacing line is not compared|3|2|quota: the weekly quota has 4% left, below the 5% floor|0|false|true|none|3|false|false|weekly" \
  "$o_rc|$(lines_of "$o_out")|$o_out|$f_rc|$(lines_of "$f_out")|$f_out|$n_rc|$(lines_of "$n_out")|$n_out|$fb_rc|$(lines_of "$fb_out")|$fb_why|$oj_rc|$(json_shape "$oj_out")|$hj_rc|$(json_shape "$hj_out")"

# ── a balance it cannot read is a go with a note ─────────────────────────────────────────────────────────────
# Four ways a reading cannot be made: no file, a file that is not a file, bytes that are not JSON, and JSON of
# another shape. Each is a note and a `go` — a check that cannot be made is not a refusal — and each says which
# of the four it was, because the run relays the line into its own notes.
mkdir -p "$WORK/not-a-file.json"
printf 'not json at all\n' > "$WORK/garbage.json"
printf '{"included":{}}\n' > "$WORK/other-shape.json"
read_quota "$WORK/absent.json"; ab_rc=$rc; ab_out=$out
read_quota "$WORK/not-a-file.json"; na_rc=$rc; na_out=$out
read_quota "$WORK/garbage.json"; ga_rc=$rc; ga_out=$out
read_quota "$WORK/other-shape.json"; os_rc=$rc; os_out=$out
expect "quota#aBalanceItCannotReadIsAGoWithANote" \
  "0|1|quota: the reading at $WORK/absent.json is empty or absent — the check is not a refusal|0|1|quota: the reading at $WORK/not-a-file.json is empty or absent — the check is not a refusal|0|1|quota: the balance is not the JSON this reader knows — the check is not a refusal|0|1|quota: the balance carries no session and weekly percentage — nothing was compared" \
  "$ab_rc|$(lines_of "$ab_out")|$ab_out|$na_rc|$(lines_of "$na_out")|$na_out|$ga_rc|$(lines_of "$ga_out")|$ga_out|$os_rc|$(lines_of "$os_out")|$os_out"

# ── the values the cases assume are the ones the profile records ─────────────────────────────────────────────
# Every case above pins 5 / 5 / 5 the way `state.sh` passes them; this one is what keeps the pinned numbers and
# `tools/loop/loop.conf` from drifting apart, and it names the key that moved rather than the arithmetic.
conf_value() { sed -n "s/^$1=\"\(.*\)\"$/\1/p" "$CONF" | head -1; }
expect "quota#theRecordedValuesAreTheOnesTheseCasesAssume" \
  "session=5 weekly=5 reserve=5" \
  "session=$(conf_value LOOP_QUOTA_SESSION_MIN_PERCENT) weekly=$(conf_value LOOP_QUOTA_WEEKLY_MIN_PERCENT) reserve=$(conf_value LOOP_QUOTA_PACING_RESERVE_PERCENT)"

# A case id no case carries is a refusal, not a suite that ran nothing and passed.
if [ -n "$case_only" ] && [ "$ran" -eq 0 ]; then
  printf 'loop-quota-selftest: no case carries the id %s\n' "$case_only" >&2
  exit 2
fi

echo
if [ "$fail" -ne 0 ]; then
  printf 'loop-quota-selftest: %s of %s cases failed\n' "$fail" "$((pass + fail))"
  exit 1
fi
printf 'loop-quota-selftest: all %s cases passed\n' "$pass"
