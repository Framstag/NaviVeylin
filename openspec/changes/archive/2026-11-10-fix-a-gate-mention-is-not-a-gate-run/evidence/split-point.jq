# Where a session's "after the gate" share was split at, read with the BASE's predicate and with the fixed one.
# The base's `is_gate` matched any word of a command; the fixed one reads the program the command starts.
# Usage: jq -s -f split-point.jq <events.jsonl>
def words: [((. // "") | tostring) | splits("[ \t;|&]+")] | map(select(length > 0));
def assignment: test("^[A-Za-z_][A-Za-z0-9_]*=");
def wrapper: . as $w | (["bash", "sh", "exec", "command", "timeout", "env", "nohup"] | index($w)) != null;
def run_word:
  (if .[0] == "cd" then .[2:] else . end)
  | until((length == 0) or (((.[0] | assignment) or (.[0] | wrapper)) | not);
      (if (.[0] | assignment) then .[1:]
       elif (.[0] == "timeout" and ((.[1] // "") | test("^[0-9]"))) then .[2:]
       else .[1:] end))
  | .[0] // "";
def old_gate: any(words[]; . == "gate.sh" or endswith("/gate.sh"));
def new_gate:
  ((words) as $w
   | (($w | run_word) as $prog
      | (($prog == "gate.sh") or ($prog | endswith("/gate.sh")))
        and ([$w[] | select(. == "--plan")] | length == 0)));
def tok: if . == null then 0
         else ((.input // 0) + (.output // 0) + (.cacheRead // 0) + (.cacheWrite // 0)) end;
def calls_of: [(. // [])[] | select(.type == "toolCall")
               | {cmd: (((.arguments?.command? // "") | tostring))}];
def sessions:
  reduce .[] as $e ({cur:null, done:[]};
    if $e.type == "session" then
      (if .cur != null then .done = (.done + [.cur]) else . end)
      | .cur = {prompt:null, usage:null, turns:[]}
    elif .cur == null then .
    elif $e.type == "message_start" and (($e.message.role? // "") == "user") and (.cur.prompt == null) then
      .cur.prompt = (($e.message.content // "")
        | if type == "array" then (map(.text? // "") | join("")) else tostring end)
    elif $e.type == "message_update" and (($e.usage? // null) | type) == "object" then
      .cur.usage = $e.usage
    elif $e.type == "turn_end" then
      (($e.message // {}) as $m
       | .cur.turns = (.cur.turns + [{usage: (.cur.usage // ($m.usage? // null)),
                                       calls: (($m.content? // []) | calls_of)}])
       | .cur.usage = null)
    else . end)
  | if .cur != null then (.done + [.cur]) else .done end;
sessions
| map({
    role: ((.prompt // "") | ascii_downcase
           | if test("worker-tail") then "tail" elif test("reviewer") then "reviewer" else "worker" end),
    prompt: ((.prompt // "") | split("\n") | .[0]),
    turn_list: .turns,
    split_at: ([.turns | to_entries[]
                | select(any(.value.calls[]; (.cmd | old_gate)))
                | {turn: .key, cmd: ([.value.calls[] | select(.cmd | old_gate) | .cmd][0])}][0] // null)
  })
| map(. + {
    turns: (.turn_list | length),
    tokens_total: ([.turn_list[] | .usage | tok] | add // 0),
    tokens_after_split: (if .split_at == null then null
                         else ([.turn_list[.split_at.turn:][] | .usage | tok] | add // 0) end),
    fixed_predicate_says_gate: (if .split_at == null then null else (.split_at.cmd | new_gate) end),
    split_cmd: (.split_at.cmd // null),
    split_turn: (.split_at.turn // null)
  })
| map(del(.turn_list, .split_at))
