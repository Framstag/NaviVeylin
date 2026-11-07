#!/usr/bin/env bash
# loop-spend-selftest.sh — the cases for tools/loop-spend.py, on fixtures it writes itself.
#
# Why: the tool's numbers are the whole point, and a measurement tool that silently stops measuring (a renamed
# event field, a stream shape that changed, a `--since` that filters everything) is worse than no tool — it
# reports a calm zero. The fixture is a minimal event stream in the shape `tools/pi-child-events.sh` keeps:
# two items, a worker session with a repeated read and a gate call, a reviewer session, and known usage on
# every turn — so each expected number below is arithmetic, not a recording of today's answer.
#
#   bash tools/loop-spend-selftest.sh
#
# Offline, no device, no token, no network. Exit 0 = every case passed, 1 = a case failed (the case prints
# what it expected and what the tool reported).
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOL="$HERE/loop-spend.py"
[ -f "$TOOL" ] || { echo "selftest: no tools/loop-spend.py next to $0" >&2; exit 1; }

WORK="$(mktemp -d)" || exit 1
trap 'rm -rf "$WORK"' EXIT

pass=0
fail=0
case_failed() { # case_failed <case> <expected> <actual>
  printf '  FAIL %s\n         expected: %s\n         actual:   %s\n' "$1" "$2" "$3"
  fail=$((fail + 1))
}
case_passed() { pass=$((pass + 1)); printf '  ok   %s\n' "$1"; }
expect() { # expect <case> <expected> <actual>
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

out="$(python3 "$TOOL" --items "$WORK/items" 2>&1)"
rc=$?
json_all="$(python3 "$TOOL" --items "$WORK/items" --json 2>&1)"
echo "loop-spend-selftest"
expect "exit code" 0 "$rc"
expect "items" 2 "$(field "$out" ITEMS n)"
expect "sessions" 2 "$(field "$out" SESSIONS n)"
expect "turns" 4 "$(field "$out" TURNS n)"
expect "input tokens" 100 "$(field "$out" TOKENS input)"
expect "cacheRead tokens" 76000 "$(field "$out" TOKENS cacheRead)"
expect "output tokens" 410 "$(field "$out" TOKENS output)"
expect "total tokens" 76510 "$(field "$out" TOKENS total)"
expect "prompt baseline is the median first turn" 17050 "$(field "$out" BASELINE tokens)"
expect "baseline charged once per turn" 68300 "$(field "$out" BASELINE charged)"
expect "a repeated read is a duplicate" 1 "$(field "$out" READS duplicate)"
expect "reads counted" 2 "$(field "$out" READS calls)"
expect "spend after the first gate call" 22000 "$(field "$out" GATE after)"
expect "no result exceeds 4k chars, so no cap saving" 0 "$(field "$out" CAP4K saved)"

# A cost the provider reports is summed; one it does not report reads `-`, never a measured zero.
expect "unreported cost is a dash" - "$(field "$out" COST total)"

# The role split: a reviewer is recognised by its prompt text, a worker by the path it is handed.
role_turns() { # role_turns <json> <role> -> that role's turn count
  printf '%s' "$1" | python3 -c 'import json,sys; print(json.load(sys.stdin)["byRole"].get(sys.argv[1],{}).get("turns",0))' "$2"
}
expect "reviewer session recognised" 1 "$(role_turns "$json_all" reviewer)"
expect "worker session recognised" 3 "$(role_turns "$json_all" worker)"

# --since keeps the items whose first turn is on or after the date.
out_since="$(python3 "$TOOL" --items "$WORK/items" --since 2026-10-27 2>&1)"
expect "--since filters earlier items" 1 "$(field "$out_since" ITEMS n)"

# --quiet prints the LOOP-SPEND lines and nothing else, which is what a prompt quotes.
out_quiet="$(python3 "$TOOL" --items "$WORK/items" --quiet 2>&1)"
noise="$(printf '%s\n' "$out_quiet" | grep -vc '^LOOP-SPEND-')"
expect "--quiet prints only LOOP-SPEND lines" 0 "$noise"

# --json carries the same totals, so a caller does not have to parse the tables.
json="$json_all"
expect "json total" 76510 "$(printf '%s' "$json" | python3 -c 'import json,sys; print(json.load(sys.stdin)["tokens"]["total"])')"
expect "json duplicate reads" 1 "$(printf '%s' "$json" | python3 -c 'import json,sys; print(json.load(sys.stdin)["reads"]["duplicate"])')"

# An empty or absent item directory is a refusal with exit 2, not a report of zero spend.
mkdir -p "$WORK/empty"
python3 "$TOOL" --items "$WORK/empty" >/dev/null 2>&1
expect "an empty item directory is refused" 2 "$?"
python3 "$TOOL" --items "$WORK/nope" >/dev/null 2>&1
expect "a missing item directory is refused" 2 "$?"

echo
if [ "$fail" -ne 0 ]; then
  printf 'loop-spend-selftest: %s case(s) failed, %s passed\n' "$fail" "$pass"
  exit 1
fi
printf 'loop-spend-selftest: %s cases passed\n' "$pass"
