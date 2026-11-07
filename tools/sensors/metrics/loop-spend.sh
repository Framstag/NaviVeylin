#!/usr/bin/env bash
# loop-spend.sh — the spend row's measurement: the share of the loop children's file reads that repeated a path
# the session already had.
#
# It does not re-implement the count. It runs `tools/loop-spend.py --quiet --since <window>` and reads the
# `LOOP-SPEND-*` counters that tool publishes, so the row that watches the spend and the skill a person runs
# cannot disagree about the number (`loop-spend`).
#
# The measurement is a **ratio**, never a total. An item's price and the number of items a day carries move for
# unrelated reasons — the archive read 32 items a day at 10.65 M tokens an item and 5 items a day at 18.96, and
# then 31 items at 10.24 — so a total fires on a quiet week and stays silent through a real regression. The share
# of reads that repeated a path is the number that moved while neither of those did: 41 % → 48 % → 54 % over the
# days the archive covers (issue #690). The bound is the row's, in percent.
#
# ## The fallback, stated because the archive is machine-local
#
# The streams live under `.pi/issue-loop/items`, which is not committed: an environment without them (the checks'
# own pipeline, a fresh clone) has nothing to read. That is not "the check determined nothing" — it is the state
# such an environment is in on every run, and the third outcome would keep the run red for a fact that says
# nothing about the loop. It is reported as one `clear` under an identity of its own, `stream-absent`, whose
# measurement token names *which* absence it was:
#
#   no-archive   there is no stream directory here at all
#   no-items     the directory holds no stream whose item falls inside the window
#   no-reads     the window's items read no file, so there is no share to take
#
# A clear under that identity is recorded on the row's record issue (the engine's own path for a clear whose
# identity no condition issue carries) and nothing else: it files no item, and it cannot reach the ratio's
# condition, whose identity is a different value of the row's one field — so an absent stream can neither file a
# false condition nor clear a real one. A stream that exists and cannot be read is a broken check rather than an
# absence: that is still the third outcome, and the run fails.
#
# Inputs (set by `run.sh`): SENSORS_THRESHOLD (the bound, in percent), SENSORS_ROOT, SENSORS_INPUT (the item
# directory, when the caller names one — the runner's own lever), SENSORS_LOOP_SPEND_DAYS (the window in days).
set -uo pipefail
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/sensors/lib.sh
source "$here/../lib.sh"

# The window the ratio is taken over: a week holds enough items for a share to mean something, and short enough
# that a drift lands in the next run rather than a month later.
window_days="${SENSORS_LOOP_SPEND_DAYS:-7}"
bound="${SENSORS_THRESHOLD:-0}"
archive="${SENSORS_INPUT:-$SENSORS_ROOT/.pi/issue-loop/items}"

# The row's one fingerprint field carries the identity: the ratio this row measures, and the absence it states
# instead when it has nothing to measure. The runner finds the ratio's condition issue for the first and nothing
# at all for the second.
kind_measured="duplicate-read-share"
kind_absent="stream-absent"

fields_of() { # <kind>
  jq -nc --arg kind "$1" '{kind: $kind}'
}

# The absence, as the record's entry rather than as a condition: a clear whose identity no condition issue
# carries, and whose measurement token says which absence it was.
emit_absent() { # <token> <what the environment shows>
  local fields fingerprint
  fields=$(fields_of "$kind_absent")
  fingerprint=$(sensors_fingerprint "$fields" kind)
  printf 'loop-spend: %s: %s\n' "$1" "$2" >&2
  sensors_emit "$fingerprint" "$1" clear "$fields" "" \
    "the loop's spend archive could not be measured in this environment"
}

since=$(date -u -d "$window_days days ago" +%Y-%m-%d 2>/dev/null ||
  date -u -v-"${window_days}"d +%Y-%m-%d 2>/dev/null)
if [ -z "$since" ]; then
  sensors_emit_error "$(fields_of "$kind_measured")" "no-window"
  exit 0
fi

if [ ! -d "$archive" ]; then
  emit_absent no-archive "there is no event stream directory at $archive"
  exit 0
fi

# The tool's own counters, and its exit status as the distinction between "nothing to read" and "cannot read":
# 2 is the documented refusal of an empty or absent stream, and anything else is the check itself failing.
report=$(python3 "$SENSORS_ROOT/tools/loop-spend.py" --quiet --items "$archive" --since "$since" 2>&1)
status=$?
if [ "$status" -eq 2 ]; then
  emit_absent no-items "no item under $archive has its first turn on or after $since"
  exit 0
fi
if [ "$status" -ne 0 ]; then
  sensors_emit_error "$(fields_of "$kind_measured")" "unreadable-spend-report"
  exit 0
fi

calls=$(printf '%s\n' "$report" | sed -n 's/^LOOP-SPEND-READS .*calls=\([0-9][0-9]*\).*/\1/p' | head -1)
duplicate=$(printf '%s\n' "$report" | sed -n 's/^LOOP-SPEND-READS .*duplicate=\([0-9][0-9]*\).*/\1/p' | head -1)
# A counter the tool stopped printing is a check that silently stopped measuring, which is worse than no check:
# it is a failure, never an absence and never a zero.
if [ -z "$calls" ] || [ -z "$duplicate" ]; then
  sensors_emit_error "$(fields_of "$kind_measured")" "no-reads-counter"
  exit 0
fi
if [ "$calls" -eq 0 ]; then
  emit_absent no-reads "the items since $since read no file, so there is no share to take"
  exit 0
fi

share=$(awk -v d="$duplicate" -v c="$calls" 'BEGIN { printf "%.1f", 100 * d / c }')
fields=$(fields_of "$kind_measured")
fingerprint=$(sensors_fingerprint "$fields" kind)
printf 'duplicate share of reads: %s%% (%s of %s reads since %s; bound %s%%)\n' \
  "$share" "$duplicate" "$calls" "$since" "$bound" >&2
if sensors_compare "$share" "$bound" greater-than; then
  sensors_emit "$fingerprint" "$share" violation "$fields" "" "" \
    "the loop's children repeated $share% of their file reads since $since, above the row's bound of $bound% ($duplicate of $calls reads)"
else
  sensors_emit "$fingerprint" "$share" clear "$fields" "" "" \
    "the loop's children repeated $share% of their file reads since $since, inside the row's bound of $bound% ($duplicate of $calls reads)"
fi
