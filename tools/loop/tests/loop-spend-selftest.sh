#!/usr/bin/env bash
# loop-spend-selftest.sh — the cases for tools/loop-spend.py, on fixtures it writes itself.
#
# Why: the tool's numbers are the whole point, and a measurement tool that silently stops measuring (a renamed
# event field, a stream shape that changed, a `--since` that filters everything) is worse than no tool — it
# reports a calm zero. The fixture is a minimal event stream in the shape `tools/pi-child-events.sh` keeps:
# two items, a worker session with a repeated read and a gate call, a reviewer session, and known usage on
# every turn — so each expected number below is arithmetic, not a recording of today's answer.
#
# The role split has fixtures of its own (issue #822): `$WORK/roles/items/200` holds one session per role the
# engine starts, each started from the prompt file that role's name gives it (`child_role`, `tools/loop/run.sh`)
# and each carrying its own `cacheRead` — so a session charged to the wrong role moves a number instead of hiding
# inside a total — and `$WORK/prompts/items/300` holds the first user messages that name no role path at all.
#
# Every case carries an id of the shape a spec's `- **Case**:` line names — `spend#<name>` — and `--case <id>`
# runs that one and skips the rest: a change needs the case it broke, not the suite. An id no case carries is a
# refusal (exit 2), never a quiet zero, because nothing run and nothing found must not read alike. The summary
# line is the shape `tools/loop/gate.sh` derives a step's `tests=`/`failures=` from.
#
#   bash tools/loop/tests/loop-spend-selftest.sh              # every case
#   bash tools/loop/tests/loop-spend-selftest.sh --case spend#eachRoleTheEngineStartsIsChargedToItsOwnName
#
# Offline, no device, no token, no network. Exit 0 = every case passed, 1 = a case failed (the case prints
# what it expected and what the tool reported), 2 = usage, or a case id no case carries.
set -uo pipefail

root=$(git rev-parse --show-toplevel 2>/dev/null) || root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)
HERE="$root/tools"
TOOL="$HERE/loop-spend.py"
[ -f "$TOOL" ] || { echo "selftest: no tools/loop-spend.py next to $0" >&2; exit 1; }

case_only=""
while [ $# -gt 0 ]; do
  case "$1" in
    --case)
      [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { echo "loop-spend-selftest: $1 needs a value" >&2; exit 2; }
      case_only="${2:-}"; shift 2 ;;
    *) echo "loop-spend-selftest: unknown argument $1" >&2; exit 2 ;;
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
field() { # field <output> <name>  -> the value of `name=` in the LOOP-SPEND-<name> block, key k
  printf '%s\n' "$1" | sed -n "s/^LOOP-SPEND-$2 .*$3=\([^ ]*\).*/\1/p" | head -1
}

# ── the fixture: the shape a child's stream has, with arithmetic instead of a recording ──────────────
mkdir -p "$WORK/items/100" "$WORK/items/101"
cat > "$WORK/items/100/events.jsonl" <<'JSONL'
{"type":"session","version":3,"id":"s1","timestamp":"2026-10-26T05:00:00.000Z"}
{"type":"agent_start"}
{"type":"message_start","message":{"role":"system","content":""}}
{"type":"message_start","message":{"role":"user","content":[{"type":"text","text":"/p/.pi/issue-loop/items/100/worker-prompt.md"}]}}
{"type":"turn_end","message":{"role":"assistant","usage":{"input":100,"output":200,"cacheRead":17000,"cacheWrite":0,"cost":{"total":0}},"durationMs":1000,"timestamp":1792990800000,"content":[{"type":"toolCall","id":"c1","name":"ctx_read","arguments":{"path":"guidelines/A.md"}}]},"toolResults":[{"toolCallId":"c1","toolName":"ctx_read","content":[{"type":"text","text":"<2800 chars>"}]}]}
{"type":"turn_end","message":{"role":"assistant","usage":{"input":0,"output":100,"cacheRead":20000,"cacheWrite":0,"cost":{"total":0}},"durationMs":2000,"timestamp":1792990801000,"content":[{"type":"toolCall","id":"c2","name":"ctx_read","arguments":{"path":"guidelines/A.md"}}]},"toolResults":[{"toolCallId":"c2","toolName":"ctx_read","content":[{"type":"text","text":"<2800 chars>"}]}]}
{"type":"turn_end","message":{"role":"assistant","usage":{"input":0,"output":50,"cacheRead":22000,"cacheWrite":0,"cost":{"total":0}},"durationMs":3000,"timestamp":1792990802000,"content":[{"type":"toolCall","id":"c3","name":"ctx_shell","arguments":{"command":"bash tools/loop/gate.sh"}}]},"toolResults":[{"toolCallId":"c3","toolName":"ctx_shell","content":[{"type":"text","text":"<100 chars>"}]}]}
JSONL
cat > "$WORK/items/101/events.jsonl" <<'JSONL'
{"type":"session","version":3,"id":"s2","timestamp":"2026-10-27T05:00:00.000Z"}
{"type":"message_start","message":{"role":"user","content":[{"type":"text","text":"# Reviewer — one change, one verdict\nRead the diff."}]}}
{"type":"turn_end","message":{"role":"assistant","usage":{"input":0,"output":60,"cacheRead":17000,"cacheWrite":0,"cost":{"total":0}},"durationMs":500,"timestamp":1793077200000,"content":[]},"toolResults":[]}
JSONL
# The two reads above are "<2800 chars>" placeholders; the tool counts characters, so the fixture carries the
# real length rather than a symbolic size. Written by python3 so the JSONL stays one line per event.
python3 - "$WORK/items/100/events.jsonl" <<'PY'
import json, sys
path = sys.argv[1]
lines = []
for line in open(path):
    ev = json.loads(line)
    for tr in (ev.get('toolResults') or []):
        for block in tr.get('content') or []:
            if block.get('text') == '<2800 chars>':
                block['text'] = 'x' * 2800
            elif block.get('text') == '<100 chars>':
                block['text'] = 'y' * 100
    lines.append(json.dumps(ev))
open(path, 'w').write('\n'.join(lines) + '\n')
PY

# The role fixture (issue #822): one session per role the engine starts, each started from the prompt file
# `child_role` reads that name from, one turn each with its own `cacheRead` — 1 000, 2 000, 4 000, 8 000, 16 000,
# 32 000 — so a session charged to the wrong role moves a number rather than hiding inside a total.
mkdir -p "$WORK/roles/items/200"
cat > "$WORK/roles/items/200/events.jsonl" <<'JSONL'
{"type":"session","version":3,"id":"r1","timestamp":"2026-11-07T05:00:00.000Z"}
{"type":"message_start","message":{"role":"user","content":[{"type":"text","text":"/p/.pi/issue-loop/items/200/worker-prompt.md"}]}}
{"type":"turn_end","message":{"role":"assistant","usage":{"input":0,"output":10,"cacheRead":1000,"cacheWrite":0,"cost":{"total":0}},"durationMs":100,"timestamp":"2026-11-07T05:00:01.000Z","content":[]},"toolResults":[]}
{"type":"session","version":3,"id":"r2","timestamp":"2026-11-07T05:01:00.000Z"}
{"type":"message_start","message":{"role":"user","content":[{"type":"text","text":"/p/.pi/issue-loop/items/200/worker-prompt-round2.md"}]}}
{"type":"turn_end","message":{"role":"assistant","usage":{"input":0,"output":10,"cacheRead":2000,"cacheWrite":0,"cost":{"total":0}},"durationMs":100,"timestamp":"2026-11-07T05:01:01.000Z","content":[]},"toolResults":[]}
{"type":"session","version":3,"id":"r3","timestamp":"2026-11-07T05:02:00.000Z"}
{"type":"message_start","message":{"role":"user","content":[{"type":"text","text":"/p/.pi/issue-loop/items/200/worker-gate-prompt.md"}]}}
{"type":"turn_end","message":{"role":"assistant","usage":{"input":0,"output":10,"cacheRead":4000,"cacheWrite":0,"cost":{"total":0}},"durationMs":100,"timestamp":"2026-11-07T05:02:01.000Z","content":[]},"toolResults":[]}
{"type":"session","version":3,"id":"r4","timestamp":"2026-11-07T05:03:00.000Z"}
{"type":"message_start","message":{"role":"user","content":[{"type":"text","text":"/p/.pi/issue-loop/items/200/worker-tail-prompt.md"}]}}
{"type":"turn_end","message":{"role":"assistant","usage":{"input":0,"output":10,"cacheRead":8000,"cacheWrite":0,"cost":{"total":0}},"durationMs":100,"timestamp":"2026-11-07T05:03:01.000Z","content":[]},"toolResults":[]}
{"type":"session","version":3,"id":"r5","timestamp":"2026-11-07T05:04:00.000Z"}
{"type":"message_start","message":{"role":"user","content":[{"type":"text","text":"/p/.pi/issue-loop/items/200/reviewer-prompt-1.md"}]}}
{"type":"turn_end","message":{"role":"assistant","usage":{"input":0,"output":10,"cacheRead":16000,"cacheWrite":0,"cost":{"total":0}},"durationMs":100,"timestamp":"2026-11-07T05:04:01.000Z","content":[]},"toolResults":[]}
{"type":"session","version":3,"id":"r6","timestamp":"2026-11-07T05:05:00.000Z"}
{"type":"message_start","message":{"role":"user","content":[{"type":"text","text":"/p/.pi/issue-loop/items/200/reviewer-pre-gate-prompt.md"}]}}
{"type":"turn_end","message":{"role":"assistant","usage":{"input":0,"output":10,"cacheRead":32000,"cacheWrite":0,"cost":{"total":0}},"durationMs":100,"timestamp":"2026-11-07T05:05:01.000Z","content":[]},"toolResults":[]}
JSONL

# The prompts that name no role path: prompt *text* with and without the reviewer's word, and a `.md` path whose
# name no arm of `child_role` matches — which the engine falls back to as the name without its extension.
mkdir -p "$WORK/prompts/items/300"
cat > "$WORK/prompts/items/300/events.jsonl" <<'JSONL'
{"type":"session","version":3,"id":"t1","timestamp":"2026-11-07T06:00:00.000Z"}
{"type":"message_start","message":{"role":"user","content":[{"type":"text","text":"# Reviewer — the fixed contract\nRead the diff."}]}}
{"type":"turn_end","message":{"role":"assistant","usage":{"input":0,"output":10,"cacheRead":64000,"cacheWrite":0,"cost":{"total":0}},"durationMs":100,"timestamp":"2026-11-07T06:00:01.000Z","content":[]},"toolResults":[]}
{"type":"session","version":3,"id":"t2","timestamp":"2026-11-07T06:01:00.000Z"}
{"type":"message_start","message":{"role":"user","content":[{"type":"text","text":"# Worker — one item, phases A–D\nRead the package."}]}}
{"type":"turn_end","message":{"role":"assistant","usage":{"input":0,"output":10,"cacheRead":32000,"cacheWrite":0,"cost":{"total":0}},"durationMs":100,"timestamp":"2026-11-07T06:01:01.000Z","content":[]},"toolResults":[]}
{"type":"session","version":3,"id":"t3","timestamp":"2026-11-07T06:02:00.000Z"}
{"type":"message_start","message":{"role":"user","content":[{"type":"text","text":"/p/.pi/issue-loop/items/300/prompt-something-else.md"}]}}
{"type":"turn_end","message":{"role":"assistant","usage":{"input":0,"output":10,"cacheRead":128000,"cacheWrite":0,"cost":{"total":0}},"durationMs":100,"timestamp":"2026-11-07T06:02:01.000Z","content":[]},"toolResults":[]}
JSONL

out="$(python3 "$TOOL" --items "$WORK/items" 2>&1)"
rc=$?
json_all="$(python3 "$TOOL" --items "$WORK/items" --json 2>&1)"
echo "loop-spend-selftest"
expect "spend#exitCode" 0 "$rc"
expect "spend#itemsCounted" 2 "$(field "$out" ITEMS n)"
expect "spend#sessionsCounted" 2 "$(field "$out" SESSIONS n)"
expect "spend#turnsCounted" 4 "$(field "$out" TURNS n)"
expect "spend#inputTokens" 100 "$(field "$out" TOKENS input)"
expect "spend#cacheReadTokens" 76000 "$(field "$out" TOKENS cacheRead)"
expect "spend#outputTokens" 410 "$(field "$out" TOKENS output)"
expect "spend#totalTokens" 76510 "$(field "$out" TOKENS total)"
expect "spend#baselineIsTheMedianFirstTurn" 17050 "$(field "$out" BASELINE tokens)"
expect "spend#baselineIsChargedOncePerTurn" 68300 "$(field "$out" BASELINE charged)"
expect "spend#aRepeatedReadIsADuplicate" 1 "$(field "$out" READS duplicate)"
expect "spend#readsAreCounted" 2 "$(field "$out" READS calls)"
expect "spend#spendAfterTheGateIsCounted" 22000 "$(field "$out" GATE after)"
expect "spend#noCapSavingWithoutALargeResult" 0 "$(field "$out" CAP4K saved)"

# A cost the provider reports is summed; one it does not report reads `-`, never a measured zero.
expect "spend#anUnreportedCostIsADash" - "$(field "$out" COST total)"

# The role split: a session is charged to the role its prompt's own name gives it — the names `child_role`
# (`tools/loop/run.sh`) resolves — and a prompt that names no such file keeps the reading its words give it.
role_field() { # role_field <json> <role> <field> -> that role's value, or 0 where the report does not name it
  printf '%s' "$1" | python3 -c 'import json,sys
d = json.load(sys.stdin)["byRole"]
print(d.get(sys.argv[1], {}).get(sys.argv[2], 0))' "$2" "$3"
}
role_map() { # role_map <json> -> `role=sessions/turns/tokens` for every role charged, sorted by role
  printf '%s' "$1" | python3 -c 'import json,sys
d = json.load(sys.stdin)["byRole"]
print(",".join("%s=%d/%d/%d" % (r, d[r]["sessions"], d[r]["turns"], d[r]["tok"]) for r in sorted(d)))'
}
role_names() { # role_names <json> -> every role charged, one per line, sorted
  printf '%s' "$1" | python3 -c 'import json,sys; print("\n".join(sorted(json.load(sys.stdin)["byRole"])))'
}
role_table_rows() { # role_table_rows <output> -> the per-role table's role names, one per line, sorted
  printf '%s\n' "$1" | sed -n '/^Per session role/,/^$/p' | sed -n 's/^  \([a-z][a-z-]*\)  *[0-9].*/\1/p' | sort -u
}
expect "spend#aReviewerSessionIsChargedToTheReviewer" 1 "$(role_field "$json_all" reviewer turns)"
expect "spend#aWorkerSessionIsChargedToTheWorker" 3 "$(role_field "$json_all" worker turns)"

roles_json="$(python3 "$TOOL" --items "$WORK/roles/items" --json 2>&1)"
roles_out="$(python3 "$TOOL" --items "$WORK/roles/items" 2>&1)"
# Six roles, six keys, each holding its own one session, its own one turn and its own tokens: a session charged to a
# role it did not play leaves the role it did play one short and the role it was charged to one long, and the map is
# compared whole so a seventh role cannot appear unnoticed.
expect "spend#eachRoleTheEngineStartsIsChargedToItsOwnName" \
  'reviewer=1/1/16000,reviewer-pre-gate=1/1/32000,worker=1/1/1000,worker-gate=1/1/4000,worker-rework=1/1/2000,worker-tail=1/1/8000' \
  "$(role_map "$roles_json")"
# The pre-gate round is the one that contains the reviewer's word: charged to the reviewer it inflates the round
# after the gate, charged to the worker it disappears from the diff-reading role a model decision is about.
expect "spend#theRoundBeforeTheGateIsChargedApart" '1/32000 1/16000 1/1000' \
  "$(role_field "$roles_json" reviewer-pre-gate turns)/$(role_field "$roles_json" reviewer-pre-gate tok) \
$(role_field "$roles_json" reviewer turns)/$(role_field "$roles_json" reviewer tok) \
$(role_field "$roles_json" worker turns)/$(role_field "$roles_json" worker tok)"
# The table prints one row per role, and its rows are exactly the JSON's keys: a role named in only one of the
# two is a split a caller reads differently from the one a person reads. Both halves are asserted against the six
# the engine starts, because two roles agreeing with each other is what the split looked like before this case.
expect "spend#thePerRoleTableNamesWhatTheJsonCarries" \
  'reviewer-reviewer-pre-gate-worker-worker-gate-worker-rework-worker-tail|reviewer-reviewer-pre-gate-worker-worker-gate-worker-rework-worker-tail' \
  "$(role_table_rows "$roles_out" | paste -sd'-')|$(role_names "$roles_json" | paste -sd'-')"
# A prompt that is not a prompt path keeps the words' reading — the reviewer's text is the reviewer, the worker's
# text the worker — and a name no arm of `child_role` matches is charged to the name it carries rather than folded
# into the worker.
prompts_json="$(python3 "$TOOL" --items "$WORK/prompts/items" --json 2>&1)"
expect "spend#anUnmatchedPromptKeepsItsOwnReading" 'prompt-something-else=1/1/128000,reviewer=1/1/64000,worker=1/1/32000' \
  "$(role_map "$prompts_json")"

# --since keeps the items whose first turn is on or after the date.
out_since="$(python3 "$TOOL" --items "$WORK/items" --since 2026-10-27 2>&1)"
expect "spend#sinceFiltersEarlierItems" 1 "$(field "$out_since" ITEMS n)"

# --quiet prints the LOOP-SPEND lines and nothing else, which is what a prompt quotes.
out_quiet="$(python3 "$TOOL" --items "$WORK/items" --quiet 2>&1)"
noise="$(printf '%s\n' "$out_quiet" | grep -vc '^LOOP-SPEND-')"
expect "spend#quietPrintsOnlySpanLines" 0 "$noise"

# --json carries the same totals, so a caller does not have to parse the tables.
json="$json_all"
expect "spend#jsonCarriesTheSameTotal" 76510 "$(printf '%s' "$json" | python3 -c 'import json,sys; print(json.load(sys.stdin)["tokens"]["total"])')"
expect "spend#jsonCarriesTheSameDuplicateCount" 1 "$(printf '%s' "$json" | python3 -c 'import json,sys; print(json.load(sys.stdin)["reads"]["duplicate"])')"

# An empty or absent item directory is a refusal with exit 2, not a report of zero spend.
mkdir -p "$WORK/empty"
python3 "$TOOL" --items "$WORK/empty" >/dev/null 2>&1
expect "spend#anEmptyItemDirectoryIsRefused" 2 "$?"
python3 "$TOOL" --items "$WORK/nope" >/dev/null 2>&1
expect "spend#aMissingItemDirectoryIsRefused" 2 "$?"

# A case id no case carries is a refusal, not a suite that ran nothing and passed.
if [ -n "$case_only" ] && [ "$ran" -eq 0 ]; then
  printf 'loop-spend-selftest: no case carries the id %s\n' "$case_only" >&2
  exit 2
fi

echo
if [ "$fail" -ne 0 ]; then
  printf 'loop-spend-selftest: %s of %s cases failed\n' "$fail" "$((pass + fail))"
  exit 1
fi
printf 'loop-spend-selftest: all %s cases passed\n' "$pass"
